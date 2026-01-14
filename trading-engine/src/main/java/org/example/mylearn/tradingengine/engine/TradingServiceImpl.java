package org.example.mylearn.tradingengine.engine;

import org.example.mylearn.common.Result;
import org.example.mylearn.tradingengine.match.MatcherService;
import org.example.mylearn.tradingengine.match.QuotationInfo;
import org.example.mylearn.tradingengine.match.RealTimeTick;
import org.example.mylearn.tradingengine.match.TradingDetail;
import org.example.mylearn.tradingengine.order.OrderEntity;
import org.example.mylearn.tradingengine.order.OrderService;
import org.example.mylearn.tradingengine.order.TradeType;
import org.example.mylearn.tradingengine.rpcclient.SequenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class TradingServiceImpl implements TradingService {

    @Autowired
    OrderService orderService;
    @Autowired
    MatcherService matcherService;
    /**
     * 这是个跨package的bean依赖，对应bean不会被默认初始化
     * 要么在TradingEngineApplication类前用@Import引入对应类，要么把被依赖的类做成starter模式
     */
    @Autowired
    SequenceService sequenceService;
    @Override
    public Result<OrderEntity> buy(String uid, BigDecimal price, BigDecimal amont) {

        Integer seqId = sequenceService.newSequence();
        Result<OrderEntity> result = orderService.createNewOrder(seqId, uid, TradeType.BUY, price, amont);
        if(!result.isSuccess()) {
            return result;
        }
        return matcherService.submitOrder(result.getData());
    }

    @Override
    public Result<OrderEntity> sell(String uid, BigDecimal price, BigDecimal amont) {
        Integer seqId = sequenceService.newSequence();
        var result = orderService.createNewOrder(seqId, uid, TradeType.SELL, price, amont);
        if(!result.isSuccess()) {
            return result;
        }
        return matcherService.submitOrder(result.getData());
    }

    @Override
    public Result<OrderEntity> cancel(String uid, Integer orderId) {
        // Check and get order
        var result = orderService.getOrderByUserAndId(uid, orderId);
        if (!result.isSuccess()) {
            return result;
        }
        return matcherService.cancelOrder(result.getData());
    }

    @Override
    public Result<OrderEntity> orderStatus(String uid, Integer orderId) {
        return orderService.getOrderByUserAndId(uid, orderId);
    }

    @Override
    public Result<Map<TradeType, TreeSet<QuotationInfo>>> getQuotations() {
        Map<TradeType, TreeSet<QuotationInfo>> quoInfo = matcherService.getQuotationInfo();
        return Result.ok(quoInfo);
    }

    @Override
    public Result<List<TradingDetail>> getTradingDetails(int lastNumItems) {
        return matcherService.getTradingDetails(lastNumItems);
    }

    @Override
    public Result<List<RealTimeTick>> getRealTimeTicks(Timestamp start, int numItems){
        return matcherService.getRealTimeTicks(start, numItems);
    }

}
