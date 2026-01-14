package org.example.mylearn.tradingengine.order;

import com.google.gson.Gson;
import org.apache.commons.lang3.RandomStringUtils;
import org.example.mylearn.common.ErrorCode;
import org.example.mylearn.common.Result;
import org.example.mylearn.tradingengine.asset.AssetService;
import org.example.mylearn.tradingengine.asset.AssetType;
import org.example.mylearn.tradingengine.rpcclient.SequenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class OrderService {

    @Autowired
    AssetService assetService;
    @Autowired
    SequenceService sequenceService;
    RandomStringUtils RANDOM = RandomStringUtils.secure();
    Logger logger = LoggerFactory.getLogger(OrderService.class);

    // 跟踪所有活动订单: Order ID => OrderEntity
    final ConcurrentMap<Integer, OrderEntity> allOrdersDB = new ConcurrentHashMap<>();
    // 跟踪用户活动订单: User ID => Map(Order ID => OrderEntity)
    final ConcurrentMap<String, ConcurrentMap<Integer, OrderEntity>> userOrdersDB = new ConcurrentHashMap<>();

    public Result<OrderEntity> createNewOrder(Integer sequenceId, String userId,
                           TradeType type, BigDecimal price, BigDecimal amont){
        return createOrder(sequenceId, userId, type, price, amont, OrderStatus.INIT, false);
    }

    public Result<OrderEntity> createOrder(Integer sequenceId, String userId,
                                   TradeType type, BigDecimal price, BigDecimal amont,
                                   OrderStatus status, boolean checkAsset) {
        if(checkAsset) {
            switch (type) {
                case BUY -> {
                    // 买入，需冻结USD：
                    if (!assetService.tryFreeze(userId, AssetType.USD, price.multiply(amont)).isSuccess()) {
                        var msg = String.format("Failed to freeze USD, uid= %s, amont= %s, price =%s", userId, amont, price);
                        return new Result<>(false, null, ErrorCode.DEFAULT, msg);
                    }
                }
                case SELL -> {
                    // 卖出，需冻结APPL：
                    if (!assetService.tryFreeze(userId, AssetType.APPL, amont).isSuccess()) {
                        var msg = String.format("Failed to freeze APPL, uid= %s, amont= %s", userId, amont);
                        return new Result<>(false, null, ErrorCode.DEFAULT, msg);
                    }
                }
                default -> throw new IllegalStateException("Invalid TradeType: " + type);
            }
        }
        // 实例化Order:
        OrderEntity order = new OrderEntity();
        order.setId(sequenceService.newSequence());
        order.setSeqId(sequenceId);
        order.setUid(userId);
        order.setTradeType(type);
        order.setPrice(price);
        order.setAmount(amont);
        order.setFinishedAmount(BigDecimal.ZERO);
        order.setStatus(status);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        return addOrderInternal(order);
    }

    public Result<List<OrderEntity>> getOrderByUser(String userId) {
        var userorder  = userOrdersDB.get(userId);
        if(userorder == null) {
            String msg = "order not found for userId='%s'".formatted(userId);
            return Result.fail(null, ErrorCode.ORDER_NOT_FOUND, msg);
        }
        List<OrderEntity> list = new ArrayList<>(userorder.values());
        return Result.ok(list);
    }

    public Result<OrderEntity> getOrderById(Integer orderId) {
        var order = allOrdersDB.get(orderId);
        if(order == null) {
            return Result.fail(null, ErrorCode.INVALID_PARAM, "{invalid order id %d}".formatted(orderId));
        }
        return Result.ok(order);
    }

    public Result<OrderEntity> getOrderByUserAndId(String uid, Integer orderId) {
        if(!userOrdersDB.containsKey(uid) || !userOrdersDB.get(uid).containsKey(orderId)) {
            String msg =  String.format("uid '%s' or order for id '%s' not found", uid, orderId);
            logger.debug(msg);
            return Result.fail(null, ErrorCode.ERROR, msg);
        }
        // we garantee order is exist now, get it directly!
        OrderEntity order = userOrdersDB.get(uid).get(orderId);
        return Result.ok(order);
    }

    public List<OrderEntity> getAllOrder() {
        return new ArrayList<>(allOrdersDB.values());
    }

    public OrderEntity remove(Integer orderId){
        // TODO：更好的办法是在OrderEntity中加一个链表，指向所有引用此Object的Map，就不用零散的遍历了
        // Delete from allorderDB
        OrderEntity order = allOrdersDB.remove(orderId);
        if(order == null)return null;
        // Delete data from user order DB
        userOrdersDB.get(order.getUid()).remove(order.getId());
        return order;
    }

    public OrderEntity updateOrder(Integer orderId, BigDecimal finishedAmt, OrderStatus status) {
        OrderEntity order = allOrdersDB.get(orderId);
        boolean modified = false;
        if(order == null){
            logger.info("order not found for {}",  orderId);
            return null;
        }
        if (finishedAmt != null) {
            if (order.getAmount().subtract(order.getFinishedAmount()).compareTo(finishedAmt) <= 0) {
                logger.info("order's amont {} < finished amont {}!", order.getFinishedAmount(), finishedAmt);
                return null;
            }
            order.setFinishedAmount(order.getFinishedAmount().add(finishedAmt));// 原finished基础上，再增加
            modified = true;
        }
        if(status != null) {
            order.setStatus(status);
            modified = true;
        }
        if(modified) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            order.setUpdatedAt(now);
        }
        // TODO：理论上修改了all order db的object，暗含同时修改user order db，因为他们引用的是同一个对象，观察下  ✓
        return order;
    }

    // internal method, it will NOT check and froze Asset!!
    private Result<OrderEntity> addOrderInternal(OrderEntity order) {
        // 添加到OrdersDB:
        if(allOrdersDB.containsKey(order.getId())) {
            order.setStatus(OrderStatus.FAILED);
            var msg = String.format("Internal Error, duplicate order id {%d}", order.getId());
            return Result.fail(order, ErrorCode.INTERNAL_ERROR, msg);
        }
        allOrdersDB.put(order.getId(), order);

        // 添加到UserOrdersDB:
        userOrdersDB.computeIfAbsent(order.getUid(), k -> new ConcurrentHashMap<>()).put(order.getId(), order);

        return Result.ok(order);
    }

    // Uncomment to test creat order api
    //@PostConstruct
    private void init(){
        String[] jsonArray = {
            "{\"uid\":\"1\",\"seqId\":2345, \"price\":10, \"tradeType\":BUY, \"amount\":2, \"FinishedAmont\":0, \"status\":TRADING}",
            "{\"uid\":\"2\",\"seqId\":123, \"price\":15, \"tradeType\":SELL, \"amount\":4, \"FinishedAmont\":0, \"status\":INIT}",
            "{\"uid\":\"3\",\"seqId\":2445, \"price\":20, \"tradeType\":BUY, \"amount\":5, \"FinishedAmont\":0, \"status\":TRADING}",
            "{\"uid\":\"1\",\"seqId\":232, \"price\":34, \"tradeType\":SELL, \"amount\":2, \"FinishedAmont\":0, \"status\":CANCELED}",
            "{\"uid\":\"2\",\"seqId\":4212, \"price\":24, \"tradeType\":BUY, \"amount\":7, \"FinishedAmont\":0, \"status\":TRADING}"
        };
        Gson gson = new Gson();
        List<OrderEntity> list1 = Arrays.stream(jsonArray)
                .map(json -> gson.fromJson(json, OrderEntity.class))
                .toList();
            list1.forEach(e->{
                Result<OrderEntity> result = createOrder(e.getSeqId(), e.getUid(), e.getTradeType(),
                        e.getPrice(), e.getAmount(), e.getStatus(), false);
            logger.info("create order: {}", gson.toJson(result));
        });
    }
}
