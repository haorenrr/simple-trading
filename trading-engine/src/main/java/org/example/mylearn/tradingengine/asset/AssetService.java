package org.example.mylearn.tradingengine.asset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.RandomStringUtils;
import org.example.mylearn.common.ErrorCode;
import org.example.mylearn.common.Result;
import org.example.mylearn.tradingengine.rpcclient.SequenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AssetService {
    @Autowired
    private SequenceService sequenceService;

    Logger logger = LoggerFactory.getLogger(AssetService.class);
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static String SYSTEM_ASSET_ID = "0";
    // 用户ID -> (资产类型ID -> Asset)
    ConcurrentMap<String, ConcurrentMap<AssetType, AssetEntity>> userAssertsDB = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {

    }

    public Result<AssetEntity> getAssetByUidAndType(String uid, AssetType type){
        ConcurrentMap<AssetType, AssetEntity> userAsset = userAssertsDB.get(uid);
        if(userAsset == null){
            var msg = "AssetEntity for user %s not found.".formatted(uid);
            logger.debug(msg);
            return Result.fail(null, ErrorCode.DEFAULT, msg);
        }
        AssetEntity assetEntity = userAsset.get(type);
        if(assetEntity == null){
            var msg = "AssetEntity for user %s, type %s not found.".formatted(uid, type);
            logger.debug(msg);
            return Result.fail(null, ErrorCode.DEFAULT, msg);
        }
        return Result.ok(assetEntity);
    }

    public Result<List<AssetEntity>> getAssetByUid(String uid){
        ConcurrentMap<AssetType, AssetEntity> userAsset = userAssertsDB.get(uid);
        if(userAsset == null){
            String msg = "Assets for user %s not found.".formatted(uid);
            logger.debug(msg);
            return Result.fail(null, ErrorCode.DEFAULT, msg);
        }
        List<AssetEntity> assetEntities = new ArrayList<>(userAsset.values());
        return Result.ok(assetEntities);
    }

    public Result<List<AssetEntity>> getAllAssets(){
        List<AssetEntity> assetEntities = new ArrayList<>();
        for(var v : userAssertsDB.values()){
            assetEntities.addAll(v.values());
        }
        return Result.ok(assetEntities);
    }

    public Result<AssetEntity> addNewAsset(AssetEntity assetEntity){
        String uid = assetEntity.getUid();
        var assetType =  assetEntity.getAssetType();

        // 模拟数据库的自增ID列
        if(assetEntity.getId() == 0) {
            Result<Integer> result = sequenceService.newSequence();
            if(!result.isSuccess()) {
                return Result.fail(assetEntity, result.getErrorCode(), result.getMessage());
            }
            assetEntity.setId(result.getData());
        }

        if(userAssertsDB.get(uid) == null){
            userAssertsDB.put(uid, new ConcurrentHashMap<>(Map.of(assetType, assetEntity)));
            return Result.ok(assetEntity);
        }

        if(userAssertsDB.get(uid).get(assetType) == null){
            userAssertsDB.get(uid).put(assetType, assetEntity);
            return Result.ok(assetEntity);
        }
        String msg = "AssetEntity for user %s, type %s already exist! will not add the new one".formatted(uid, assetType);
        logger.debug(msg);
        return Result.fail(assetEntity, ErrorCode.ASSET_ALREADY_EXISTS, msg);
    }

    public Result<AssetEntity> addNewAsset(String uid, AssetType assetType){
        var assetEntity = new AssetEntity();
        assetEntity.setUid(uid);
        assetEntity.setAssetType(assetType);
        return addNewAsset(assetEntity);
    }

    public Result<Void> tryTransfer(
            AssetTransferType type,
            String fromUserId,
            String toUserId,
            AssetType assetType,
            BigDecimal amount,
            boolean check
    ){
        if(amount.signum()<0) {
            var msg = "amount(%s) is less than zero".formatted(amount);
            logger.debug(msg);
            return Result.fail(null, ErrorCode.INVALID_PARAM, msg);
        }
        if(amount.signum() == 0) return Result.ok(null); // do nothing for zero transfer

        var fromAssetResult = getAssetByUidAndType(fromUserId, assetType);
        AssetEntity fromAsset = fromAssetResult.getData();
        if(!fromAssetResult.isSuccess()){
            // 用户资产不存在，初始化一个
            Result<AssetEntity> rlt = addNewAsset(fromUserId, assetType);
            if(!rlt.isSuccess()){
                var msg = "Adding new Asset for %s failed. detial msg: %s".formatted(fromUserId, rlt.getMessage());
                logger.warn(msg);
                return Result.fail(null, ErrorCode.INVALID_PARAM, msg);
            }
            fromAsset = rlt.getData();
        }

        var toAssetResult = getAssetByUidAndType(toUserId, assetType);
        AssetEntity toAsset = toAssetResult.getData();
        if(!toAssetResult.isSuccess()){
            // 用户资产不存在，初始化一个
            Result<AssetEntity> rlt = addNewAsset(toUserId, assetType);
            if(!rlt.isSuccess()){
                var msg = "Adding new Asset for %s failed. detial msg: %s".formatted(fromUserId, rlt.getMessage());
                logger.warn(msg);
                return Result.fail(null, ErrorCode.INVALID_PARAM, msg);
            }
            toAsset = rlt.getData();
        }

        switch(type){
            case AVAILABLE_TO_AVAILABLE:{
                if(check && fromAsset.getAvailable().compareTo(amount) < 0){
                    String msg = String.format("Aailable asset %s, lower than required amount %s", GSON.toJson(fromAsset), amount);
                    logger.debug(msg);
                    return Result.fail(null, ErrorCode.ASSET_NOT_ENOUGH, msg);
                }
                fromAsset.setAvailable(fromAsset.getAvailable().subtract(amount));
                toAsset.setAvailable(toAsset.getAvailable().add(amount));
                return Result.ok(null);
            }
            case AVAILABLE_TO_FROZEN:{
                if(check && fromAsset.getAvailable().compareTo(amount) < 0){
                    String msg = String.format("Aailable asset %s, lower than required amount %s", GSON.toJson(fromAsset), amount);
                    logger.debug(msg);
                    return Result.fail(null, ErrorCode.ASSET_NOT_ENOUGH, msg);
                }
                fromAsset.setAvailable(fromAsset.getAvailable().subtract(amount));
                toAsset.setFrozen(toAsset.getFrozen().add(amount));
                return Result.ok(null);
            }
            case FROZEN_TO_AVAILABLE:{
                if(check && fromAsset.getFrozen().compareTo(amount) < 0){
                    String msg = String.format("Frozen asset %s, lower than required amount %s", GSON.toJson(fromAsset), amount);
                    logger.debug(msg);
                    return Result.fail(null, ErrorCode.ASSET_NOT_ENOUGH, msg);
                }
                fromAsset.setFrozen(fromAsset.getFrozen().subtract(amount));
                toAsset.setAvailable(toAsset.getAvailable().add(amount));
                return Result.ok(null);
            }
            default:{
                String msg = String.format("should NOT run here! Invalid ENUM type: %s ?!", type);
                throw new IllegalStateException(msg);
            }
        }
    }

    //用户存入资金，从系统默认负债账户0划账, 无需做资金校验,账户0的资金一定是负的
    public Result<Void> recharge(String userId, AssetType assetId, BigDecimal amount) {
        return tryTransfer(AssetTransferType.AVAILABLE_TO_AVAILABLE, SYSTEM_ASSET_ID, userId, assetId, amount, false);
    }

    public Result<Void> transferBetweenUsers(AssetTransferType type, String fromUser, String toUser, AssetType assetId, BigDecimal amount) {
        return tryTransfer(type, fromUser, toUser, assetId, amount, true);
    }

    public Result<Void> tryFreeze(String userId, AssetType assetId, BigDecimal amount) {
        return tryTransfer(AssetTransferType.AVAILABLE_TO_FROZEN, userId, userId, assetId, amount, true);
    }

    public Result<Void> unfreeze(String userId, AssetType assetId, BigDecimal amount) {
        return tryTransfer(AssetTransferType.FROZEN_TO_AVAILABLE, userId, userId, assetId, amount, true);
    }


    public void initAssetDB(){
        String[] jsonArray = {
                "{\"uid\":\"0\",\"assetType\":\"USD\", \"available\":0, \"frozen\":0}",
                "{\"uid\":\"0\",\"assetType\":\"APPL\", \"available\":0, \"frozen\":0}",
                "{\"uid\":\"1\",\"assetType\":\"USD\", \"available\":100, \"frozen\":0}",
                "{\"uid\":\"1\",\"assetType\":\"APPL\", \"available\":10, \"frozen\":0}",
                "{\"uid\":\"2\",\"assetType\":\"APPL\", \"available\":20, \"frozen\":0}",
                "{\"uid\":\"3\",\"assetType\":\"USD\", \"available\":200, \"frozen\":0}",
                "{\"uid\":\"10\",\"assetType\":\"APPL\", \"available\":100, \"frozen\":0}"
        };

        Gson gson = new Gson();
        List<AssetEntity> list1 = Arrays.stream(jsonArray)
                .map(json -> gson.fromJson(json, AssetEntity.class))
                .toList();
        list1.forEach(e->{
            var r = addNewAsset(e);
            if(!r.isSuccess()) {
                logger.info("Failed to add new asset, reason: {}", r.getMessage());
                return;
            }
            logger.info("Add assetEntity: {}", gson.toJson(e));
        });
    }
}
