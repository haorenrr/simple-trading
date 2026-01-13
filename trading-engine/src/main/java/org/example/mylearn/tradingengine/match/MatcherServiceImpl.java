package org.example.mylearn.tradingengine.match;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.RandomStringUtils;
import org.example.mylearn.common.ErrorCode;
import org.example.mylearn.common.Result;
import org.example.mylearn.common.util.SnapshotList;
import org.example.mylearn.sequenceengine.SequenceService;
import org.example.mylearn.tradingengine.clearing.ClearingService;
import org.example.mylearn.tradingengine.order.OrderEntity;
import org.example.mylearn.tradingengine.order.OrderStatus;
import org.example.mylearn.tradingengine.order.TradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class MatcherServiceImpl implements MatcherService {
    @Autowired
    ClearingService  clearingService;
    @Autowired
    SequenceService sequenceService;
    
    private final Logger logger = LoggerFactory.getLogger(MatcherServiceImpl.class);

    private final ConcurrentSkipListSet<QuotationItem> buyQuotations =
            new ConcurrentSkipListSet<>(Comparator.comparing(QuotationItem::getPrice).reversed()); //降序
    private final ConcurrentSkipListSet<QuotationItem> sellQuotations =
            new ConcurrentSkipListSet<>(Comparator.comparing(QuotationItem::getPrice)); // 升序

    private final SnapshotList<TradingDetail> tradingDetailList = new SnapshotList<>(new LinkedList<>()) ;
    private final LinkedBlockingQueue<QuotationReq> waitingOrderQueue = new LinkedBlockingQueue<>();
    private final List<Thread> threads = new ArrayList<>();

    private final static Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final static RandomStringUtils RANDOM = RandomStringUtils.secure();

    @Override
    public Result<OrderEntity> submitOrder(OrderEntity order) {
        QuotationReq req = new QuotationReq();
        order.setStatus(OrderStatus.PREPARING);
        req.setOrder(order);
        req.setAdd();// 提交
        waitingOrderQueue.add(req);
        return Result.ok(order);
    }

    @Override
    public Result<OrderEntity> cancelOrder(OrderEntity order) {
        QuotationReq req = new QuotationReq();
        order.setStatus(OrderStatus.CANCELLING);
        req.setOrder(order);
        req.setRmove();
        waitingOrderQueue.add(req);
        return Result.ok(order);
    }

    @Override
    public HashMap<TradeType, TreeSet<QuotationInfo>> getQuotationInfo() {

        HashMap<TradeType, TreeSet<QuotationInfo>> map = new HashMap<>();
        TreeSet<QuotationInfo> sellSet =  new TreeSet<>(Comparator.comparing(QuotationInfo::getPrice));
        TreeSet<QuotationInfo> buySet =  new TreeSet<>((o1, o2) -> o2.getPrice().compareTo(o1.getPrice()));
        map.put(TradeType.BUY, buySet);
        map.put(TradeType.SELL,sellSet);
        sellQuotations.forEach( q -> {
            QuotationInfo info = dumpQuotationItemToQuotationInfo(q);
            map.get(q.getTradeType()).add(info);
        });
        buyQuotations.forEach( q -> {
            QuotationInfo info = dumpQuotationItemToQuotationInfo(q);
            map.get(q.getTradeType()).add(info);
        });
        return map;
    }

    @Override
    public Result<List<TradingDetail>> getTradingDetails(int lastNumItems){
        if(lastNumItems <=0){
            return Result.fail(null, ErrorCode.INVALID_PARAM,
                    "lastNumItems %s is null or lower than 0".formatted(lastNumItems));
        }
        int start = Math.max(0, tradingDetailList.size() - lastNumItems);
        int end = tradingDetailList.size();
        List<TradingDetail> list = tradingDetailList.subList(start, end);
        return Result.ok(list);
    }

    @Override
    public Result<List<RealTimeTick>> getRealTimeTicks(Timestamp start, int numTicks){
        var ticks = new ArrayList<RealTimeTick>();
        if(numTicks == 0){ // do nothing!
            return Result.ok(ticks);
        }
        if(numTicks < 0) numTicks = Integer.MAX_VALUE;
        if(start == null) start = new Timestamp(0); //最早时间， 1970..
        int pickedTicks = 0;

        var ssTradingDetailList = tradingDetailList.snapshot();
        ListIterator<TradingDetail> it = ssTradingDetailList.listIterator(ssTradingDetailList.size());
        //for(var item : ssTradingDetailList){
        while (it.hasPrevious()){
            TradingDetail item = it.previous();
            if(item.getUpdatedAt().before(start) || pickedTicks >= numTicks){
                return Result.ok(ticks);
            }
            var tick = new RealTimeTick();
            tick.setId(sequenceService.newSequence());
            tick.setTime(item.getUpdatedAt());
            tick.setPrice(item.getPrice());
            tick.setAmount(item.getAmount());
            ticks.add(tick);
            pickedTicks++;
        }
        return Result.ok(ticks);
    }

    private QuotationInfo dumpQuotationItemToQuotationInfo(QuotationItem item) {
        var info = new QuotationInfo();
        info.setPrice(item.getPrice());
        info.setVolume(item.getVolume());
        info.setTradeType(item.getTradeType());
        return info;
    }

    @PostConstruct
    public void init(){
        Thread t = new Thread(()->{
            logger.warn("Thread {} started.", Thread.currentThread().getName());
            matchExecutor();
        },"match-executor-thread");

        t.start();
        threads.add(t);
    }
    @PreDestroy
    public void destroy(){
        for (Thread t : threads){
            t.interrupt();
            logger.warn("Thread {} terminated.", t.getName());
        }
    }

    private void matchExecutor() {
        logger.warn("matchExecutor start...");

        while (true) {
            try {
                QuotationReq req = waitingOrderQueue.take();
                switch (req.reqType) {
                    case ADD: {
                        var order = req.getOrder();
                        var result = addOrderToTrading(order);
                        if (!result.isSuccess()) {
                            order.setMessge(result.getMessage());
                        }
                        break;
                    }
                    case REMOVE: {
                        var order = req.getOrder();
                        var result = removeOrderFromTrading(order);
                        if (!result.isSuccess()) {
                            order.setMessge(result.getMessage());
                        }
                        break;
                    }
                    default: {
                        throw new IllegalStateException("unexpected request type: " + req.reqType);
                    }
                }
            } catch (InterruptedException e) {
                logger.debug("Thread {} Interrupted, exit now!", Thread.currentThread().getName());
                // 关键：重新设置中断状态，让上层调用者知道线程该停止了
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.warn("Thread get a unexpected Exception, SKIPPED it! ", e);
            }
        }
    }

    private Result<OrderEntity> removeOrderFromTrading(OrderEntity order) {
        return new Result<>(true, order, ErrorCode.DEFAULT, "{ok}");
    }

    private Result<OrderEntity> addOrderToTradingInternal(
            OrderEntity order,
            ConcurrentSkipListSet<QuotationItem> thisDirQ,  // if BUY, this is buyQuotations, if SELL, this sellQuotations
            ConcurrentSkipListSet<QuotationItem> otherDirQ) {
        {
            // 冻结资金
            Result<OrderEntity> result = clearingService.prepareTrading(order);
            if (!result.isSuccess()){
                order.setStatus(OrderStatus.FAILED);
                logger.debug(result.getMessage());
                return result;
            }

            QuotationItem newItem = new QuotationItem(order.getPrice(), order.getAmount(), order.getTradeType(),new ArrayList<>());
            if(!otherDirQ.headSet(newItem, true).isEmpty()){
                processMatch(order);
                if(order.getStatus() == OrderStatus.FINISHED) {
                    Assert.isTrue(
                            order.getFinishedAmount().compareTo(order.getAmount()) == 0,
                            "OrderStatus.FINISHED, but order amount not consistent, amount=%s, and finished=%s"
                                    .formatted(order.getAmount(), order.getFinishedAmount()));
                    return Result.ok(order);
                }
                if(order.getStatus() != OrderStatus.TRADING){
                    var msg = "Get strange error, MAY be internal issues need to check, %s".formatted(GSON.toJson(order));
                    logger.warn(msg);
                    return Result.fail(order, ErrorCode.INTERNAL_ERROR, "msg");
                }
            }
            logger.trace("add order to trading Q, order = {}", GSON.toJson(order));

            order.setStatus(OrderStatus.TRADING);
            BigDecimal remainingVol = order.getAmount().subtract(order.getFinishedAmount());
            newItem.setVolume(remainingVol);
            var exist = thisDirQ.subSet(newItem, true, newItem,  true);
            if(exist.isEmpty()){
                newItem.getOrders().addLast(order);
                thisDirQ.add(newItem);
            }else{
                QuotationItem item = exist.first(); //不要使用poll,pollFirst类,因为poll()会从queue中删除元素!
                item.getOrders().addLast(order);
                // 本价格下的总挂单额 = 原挂单额 + （订单总额-订单已成交额）
                item.setVolume(item.getVolume().add(remainingVol));
            }
            return new Result<>(true, order, ErrorCode.DEFAULT, "{ok}");
        }
    }
    private Result<OrderEntity> addOrderToTrading(OrderEntity order){
        logger.trace("addOrderToQ(), get an order: {}", GSON.toJson(order));
        switch (order.getTradeType()){
            case BUY: {
                return addOrderToTradingInternal(order, buyQuotations, sellQuotations);
            }
            case SELL: {
                return addOrderToTradingInternal(order, sellQuotations, buyQuotations);
            }
            default:{
                var msg = String.format("invalid request TYPE {%s}", order.getTradeType());
                throw new IllegalStateException(msg);
            }
        }
    }

    private void processMatch(OrderEntity order) {

        List<OrderEntity> matchedOrders = new ArrayList<>();
        final ConcurrentSkipListSet<QuotationItem> workingQ = order.getTradeType().compareTo(TradeType.BUY) == 0 ? sellQuotations : buyQuotations;
        // 寻找可撮合的交易
        findMatchedOrders(order, matchedOrders, workingQ);
        if(matchedOrders.isEmpty()) return;
        //清算，资金划拨、解冻
        if(!clearOrders(order, matchedOrders).isSuccess()){
            logger.warn("clearOrders failed, call rollback()");
            rollbackOrders(matchedOrders);
        }
    }

    private void rollbackOrders(List<OrderEntity> matchedOrders) {
        // TODO: 把单子重新加到交易池中
    }

    private void logTradingDetail(OrderEntity order, List<OrderEntity> finishedOrders){

        finishedOrders.forEach((orderTo) -> {
            var detail = new TradingDetail();

            detail.setId(sequenceService.newSequence());
            detail.setFromOrderId(order.getId());
            detail.setToOrderId(orderTo.getId());

            BigDecimal price = (order.getTradeType()==TradeType.SELL)?order.getPrice():orderTo.getPrice();
            detail.setPrice(price);
            detail.setAmount(orderTo.getProcessingAmount());

            detail.setTradeType(order.getTradeType());
            var status = (order.getStatus()==OrderStatus.FAILED)? OrderStatus.FAILED:OrderStatus.FINISHED;
            detail.setOrderStatus(status);

            Timestamp now = new Timestamp(System.currentTimeMillis());
            detail.setCreatedAt(now);
            detail.setUpdatedAt(now);

            tradingDetailList.addLast(detail);
        });
    }
    private Result<OrderEntity> clearOrders(OrderEntity order, List<OrderEntity> finishedOrders) {
        var now = System.currentTimeMillis();
        // 清算资金，解冻、转账
        var result = clearingService.finishTrading(order, finishedOrders);
        if(result.isSuccess()){
            logTradingDetail(order, finishedOrders); //记录交易明细
            finishedOrders.forEach(orderTo -> updateOrder(orderTo, now));
            updateOrder(order, now);
            return Result.ok(order);
        }
        // 清算服务失败，理论上不应该运行到这里，因为资金都是冻结过的
        // 在清算服务中，对失败订单，只修改订单状态
        order.setStatus(OrderStatus.FAILED);
        order.getUpdatedAt().setTime(now);
        finishedOrders.forEach(o ->{
            //o.setProcessingAmount(BigDecimal.ZERO); // 暂不修改中间变量，留给上层处理，看具体是什么问题，决定后续流程
            o.setStatus(OrderStatus.FAILED);
            o.getUpdatedAt().setTime(now);
        });
        logger.warn(result.getMessage());
        return Result.fail(order, ErrorCode.INTERNAL_ERROR, result.getMessage());
    }

    private static void updateOrder(OrderEntity order, long now) {
        order.setFinishedAmount(order.getFinishedAmount().add(order.getProcessingAmount()));
        order.setProcessingAmount(BigDecimal.ZERO);
        var status = (order.getAmount().compareTo(order.getFinishedAmount()) == 0) ? OrderStatus.FINISHED : OrderStatus.TRADING;
        order.setStatus(status);
        order.getUpdatedAt().setTime(now);
    }

    private void findMatchedOrders(OrderEntity order, List<OrderEntity> finishedOrders, ConcurrentSkipListSet<QuotationItem> quotationQ) {

        List<QuotationItem> matchedQuo = new ArrayList<>();
        BigDecimal tobeFinished = new BigDecimal(order.getAmount().toString());
        List<OrderEntity> partialOrders = new ArrayList<>();
        long now = System.currentTimeMillis();

        var parameter = new QuotationItem(order.getPrice(), null, null, null);
        NavigableSet<QuotationItem> matchedQutation = quotationQ.headSet(parameter, true);
        for(QuotationItem quotationItem : matchedQutation){
            if(tobeFinished.compareTo(BigDecimal.ZERO)<=0) break;
            if(quotationItem.getVolume().compareTo(tobeFinished) <= 0 ) {
                // 完全吃掉了此价格的卖单
                finishedOrders.addAll(quotationItem.getOrders());
                tobeFinished = tobeFinished.subtract(quotationItem.getVolume());
                matchedQuo.add(quotationItem);
            } else{
                //只能吃掉此价格的部分卖单
                var orders = quotationItem.getOrders();
                var matchOrders = new ArrayList<OrderEntity>();
                for(OrderEntity orderEntity : orders){
                    if(tobeFinished.compareTo(BigDecimal.ZERO)<=0) break;
                    BigDecimal orderRemainingVol = orderEntity.getAmount().subtract(orderEntity.getFinishedAmount()); //减掉已经完成的部分
                    if(orderRemainingVol.compareTo(tobeFinished) <= 0){
                        finishedOrders.add(orderEntity);
                        matchOrders.add(orderEntity);
                        quotationItem.setVolume(quotationItem.getVolume().subtract(orderRemainingVol));
                        tobeFinished = tobeFinished.subtract(orderRemainingVol);
                    }else{
                        // 处理partial order，即只满足了部分成交的订单，可能用子订单会更好一些
                        orderEntity.setStatus(OrderStatus.CLEARING);
                        orderEntity.setProcessingAmount(tobeFinished);
                        orderEntity.getUpdatedAt().setTime(now);
                        partialOrders.add(orderEntity);

                        quotationItem.setVolume(quotationItem.getVolume().subtract(tobeFinished));
                        tobeFinished = BigDecimal.ZERO;
                    }
                }
                orders.removeAll(matchOrders);//删除被吃掉的卖单，部分成交的订单不能被删除！
            }
        }
        /**
         * quotationQ.removeAll(matchedQuo)的坑, 可以实现相同的功能，但效率可能非常低:
         * 因为，removeAll()可能会内部实现为“Set做外循环，List做内循环”，但List中查找元素那可就慢了--O（N)，而Set一般是Hash查找-O(1)
         */
        matchedQuo.forEach(quotationQ::remove);//删除被吃掉的卖单
        finishedOrders.forEach(e -> {
            e.setStatus(OrderStatus.CLEARING);
            e.setProcessingAmount(e.getAmount().subtract(e.getFinishedAmount()));
            e.getUpdatedAt().setTime(now);
        });

        finishedOrders.addAll(partialOrders);
        // SETUP original order properly
        order.setStatus(OrderStatus.CLEARING);
        order.setProcessingAmount(order.getAmount().subtract(tobeFinished)); // 考虑到还有未满足的数量
        order.getUpdatedAt().setTime(now);
    }
}
