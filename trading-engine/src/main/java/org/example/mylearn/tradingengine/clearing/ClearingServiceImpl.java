package org.example.mylearn.tradingengine.clearing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.example.mylearn.common.ErrorCode;
import org.example.mylearn.common.Result;
import org.example.mylearn.tradingengine.asset.AssetService;
import org.example.mylearn.tradingengine.asset.AssetTransferType;
import org.example.mylearn.tradingengine.asset.AssetType;
import org.example.mylearn.tradingengine.order.OrderEntity;
import org.example.mylearn.tradingengine.order.TradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class ClearingServiceImpl implements ClearingService {
    @Autowired
    AssetService assetService;
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    Logger logger = LoggerFactory.getLogger(ClearingServiceImpl.class);

    @Override
    public Result<OrderEntity> prepareTrading(OrderEntity orderEntity) {
        // 检查并并冻结对应账户的USD、或APPL
        String uid = orderEntity.getUid();
        switch (orderEntity.getTradeType()){
            case BUY -> {
                var result = assetService.tryFreeze(uid, AssetType.USD, orderEntity.getAmount().multiply(orderEntity.getPrice()) );
                return new Result<>(result.isSuccess(), orderEntity, result.getErrorCode(), result.getMessage());
            }
            case SELL ->{
                var result = assetService.tryFreeze(uid, AssetType.APPL, orderEntity.getAmount());
                return new Result<>(result.isSuccess(), orderEntity, result.getErrorCode(), result.getMessage());
            }
            default -> {
                var msg = String.format("invald TradeType: %s ?!", orderEntity.getTradeType());
                throw new IllegalStateException(msg);
            }
        }
    }

    @Override
    public Result<Void> finishTrading(OrderEntity orderFrom, List<OrderEntity> matchedOrders) {
        // for the compilcated of the logic before and afer, we do some consistence check here!
        matchedOrders.forEach(order -> Assert.notNull(order, "order is null in matchedOrders"));
        BigDecimal tradingvalue = matchedOrders.stream().map(OrderEntity::getProcessingAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        Assert.isTrue(tradingvalue.compareTo(orderFrom.getProcessingAmount()) == 0,
            "Trading value not consistent! 'from' side {%s}, 'to' side {%s}. Detailed info: orderFrom=%s, matchedOrders=%s"
                .formatted(orderFrom.getProcessingAmount(), tradingvalue, GSON.toJson(orderFrom), GSON.toJson(matchedOrders)));
        TradeType takerType = orderFrom.getTradeType();
        Assert.isTrue(takerType == TradeType.BUY || takerType == TradeType.SELL,
                "invald TradeType: %s, order=%s".formatted(orderFrom.getTradeType(), GSON.toJson(orderFrom)));

        matchedOrders.forEach(orderTo -> {
            var dealPrice = orderTo.getPrice();
            var dealAmount = orderTo.getProcessingAmount();
            var buyer = (takerType == TradeType.BUY) ? orderFrom : orderTo;
            var seller = (takerType == TradeType.SELL) ? orderFrom : orderTo;
            // always transfer USD from buyer to seller, and vice versa for goods(APPL)
            assetService.transferBetweenUsers(AssetTransferType.FROZEN_TO_AVAILABLE, buyer.getUid(), seller.getUid(),
                    AssetType.USD, dealPrice.multiply(dealAmount));
            assetService.transferBetweenUsers(AssetTransferType.FROZEN_TO_AVAILABLE, seller.getUid(), buyer.getUid(),
                    AssetType.APPL, dealAmount);
            if(orderFrom.getTradeType() == TradeType.BUY){
                // deal with seller's price, it may frize too much ealier,give it back
                Assert.isTrue(orderFrom.getPrice().compareTo(orderTo.getPrice()) >= 0,
                        "BUY price must be greater than sell price. buyOrder=%s, sellOrder=%s".formatted(GSON.toJson(orderFrom), GSON.toJson(orderTo)));
                assetService.unfreeze(orderFrom.getUid(), AssetType.USD,
                        orderTo.getProcessingAmount().multiply(orderFrom.getPrice().subtract(orderTo.getPrice())));

            }
        });
        return Result.ok(null);
    }
}
