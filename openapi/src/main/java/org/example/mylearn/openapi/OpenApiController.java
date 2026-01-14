package org.example.mylearn.openapi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.mylearn.common.ErrorCode;
import org.example.mylearn.common.Result;
import org.example.mylearn.common.UserContext;
import org.example.mylearn.tradingengine.asset.AssetEntity;
import org.example.mylearn.tradingengine.asset.AssetService;
import org.example.mylearn.tradingengine.asset.AssetType;
import org.example.mylearn.tradingengine.engine.TradingService;
import org.example.mylearn.tradingengine.match.QuotationInfo;
import org.example.mylearn.tradingengine.match.RealTimeTick;
import org.example.mylearn.tradingengine.match.TradingDetail;
import org.example.mylearn.tradingengine.order.OrderEntity;
import org.example.mylearn.tradingengine.order.OrderService;
import org.example.mylearn.tradingengine.order.TradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Tag(name = "openapi", description = "open api desc") // For Swagger UI(SpringDOC)
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class OpenApiController {
    @Autowired
    TradingService tradingService;
    @Autowired
    OrderService orderService;
    @Autowired
    AssetService assetService;

    Logger logger = LoggerFactory.getLogger(OpenApiController.class);

    @Operation(summary = "HELLO", description = "保活测试") // For Swagger UI(SpringDOC)
    @GetMapping("/hello")
    public Result<Long> hello() { //可用于保活测试
        Date now = new Date();
        return  Result.ok(now.getTime());
    }

    @GetMapping("/trade/buy")
    public Result<OrderEntity> tradeBuy(@RequestParam String price, @RequestParam String amount) {
        String uid = UserContext.getUserId();
        if(uid == null) {
            String msg = "Unauthorized user?! Can't get userid.";
            logger.warn(msg);
            return Result.fail(null, ErrorCode.UNAUTHORIZED, msg);
        }
        Result<OrderEntity> result = tradingService.buy(uid, new BigDecimal(price), new BigDecimal(amount));
        return result;
    }

    @GetMapping("/trade/sell")
    public Result<OrderEntity> tradeSell(@RequestParam String price, @RequestParam String amount) {
        String uid = UserContext.getUserId();
        if(uid == null) {
            String msg = "Unauthorized user?! Can't get userid.";
            logger.warn(msg);
            return Result.fail(null, ErrorCode.UNAUTHORIZED, msg);
        }
        var rlt = tradingService.sell(uid, new BigDecimal(price), new BigDecimal(amount));
        return rlt;
    }

    @GetMapping("/trade/cancel")
    public Result<OrderEntity> tradeCancel(
            @RequestParam(name = "order_id") Integer orderId
    ){
        String uid = UserContext.getUserId();
        if(uid == null) {
            String msg = "Unauthorized user?! Can't get userid.";
            logger.warn(msg);
            return Result.fail(null, ErrorCode.UNAUTHORIZED, msg);
        }
        var rlt = tradingService.cancel(uid, orderId);
        return rlt;
    }

    @GetMapping("/trade/finishedDetails")
    public Result<List<TradingDetail>> finishedDetails(
            @RequestParam(name = "num_items", required = false) Integer numItems
    ){
        int nItems = (numItems == null)? 10 : numItems;
        return tradingService.getTradingDetails(nItems);
    }

    @GetMapping("/trade/realtime-ticks")
    public Result<List<RealTimeTick>> tradeRealTimeTicks(
            @RequestParam(name="start", required = false) Timestamp start,
            @RequestParam(name="num_items", required = false) Integer numItems
    ){
        int nItems = (numItems == null)? 10 : numItems;
        return tradingService.getRealTimeTicks(start, nItems);
    }

    @GetMapping("/trade/quotation")
    public Result<Map<TradeType, TreeSet<QuotationInfo>>> quotationList() {
        return tradingService.getQuotations();
    }

    @GetMapping("/order/get")
    public Result<OrderEntity> orderGet(@RequestParam(name="order_id") Integer orderId) {
        String uid = UserContext.getUserId();
        if(uid == null) {
            String msg = "Unauthorized user?! Can't get userid.";
            logger.warn(msg);
            return Result.fail(null, ErrorCode.UNAUTHORIZED, msg);
        }
        return orderService.getOrderByUserAndId(uid, orderId);
    }

    @GetMapping("/order/list")
    public Result<List<OrderEntity>> orderList() {
        String uid = UserContext.getUserId();
        if(uid == null) {
            String msg = "Unauthorized user?! Can't get userid.";
            logger.warn(msg);
            return Result.fail(null, ErrorCode.UNAUTHORIZED, msg);
        }
        return orderService.getOrderByUser(uid);
    }

    @GetMapping("/asset/get")
    public Result<AssetEntity> assetGet(@RequestParam AssetType type) {

        String uid = UserContext.getUserId();
        if(uid == null) {
            String msg = "Unauthorized user?! Can't get userid!";
            logger.warn(msg);
            return Result.fail(null, ErrorCode.UNAUTHORIZED, msg);
        }
        return assetService.getAssetByUidAndType(uid, type);
    }

    @GetMapping("/asset/list")
    public Result<List<AssetEntity>> assetList() {
        String uid = UserContext.getUserId();
        if(uid == null) {
            String msg = "Unauthorized user?! Can't get userid.";
            logger.warn(msg);
            return Result.fail(null, ErrorCode.UNAUTHORIZED, msg);
        }
        return assetService.getAssetByUid(uid);
    }

    @GetMapping("/asset/new")
    public Result<AssetEntity> assetNew(@RequestParam AssetType type) {
        String uid = UserContext.getUserId();
        if(uid == null) {
            String msg = "Unauthorized user?! Can't get userid.";
            logger.warn(msg);
            return Result.fail(null, ErrorCode.UNAUTHORIZED, msg);
        }
        return assetService.addNewAsset(uid, type);
    }

    @GetMapping("/asset/recharge")
    public Result<Void> assetRecharge(@RequestParam AssetType type, @RequestParam BigDecimal amount) {
        String uid = UserContext.getUserId();
        if(uid == null) {
            String msg = "Unauthorized user?! Can't get userid.";
            logger.warn(msg);
            return Result.fail(null, ErrorCode.UNAUTHORIZED, msg);
        }
        return assetService.recharge(uid, type, amount);
    }
}
