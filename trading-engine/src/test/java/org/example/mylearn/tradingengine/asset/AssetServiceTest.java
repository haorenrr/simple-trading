package org.example.mylearn.tradingengine.asset;

import org.example.mylearn.common.ErrorCode;
import org.example.mylearn.common.Result;
import org.example.mylearn.tradingengine.rpcclient.SequenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private SequenceService sequenceService;

    @InjectMocks
    private AssetService assetService;

    private final String userId = "user1";

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(sequenceService.newSequence()).thenReturn(org.example.mylearn.common.Result.ok(100));
    }

    @Test
    void testRecharge_Success() {
        BigDecimal amount = new BigDecimal("1000");
        Result<Void> result = assetService.recharge(userId, AssetType.USD, amount);

        assertThat(result.isSuccess()).isTrue();
        
        Result<AssetEntity> assetResult = assetService.getAssetByUidAndType(userId, AssetType.USD);
        assertThat(assetResult.isSuccess()).isTrue();
        assertThat(assetResult.getData().getAvailable()).isEqualByComparingTo(amount);
    }

    @Test
    void testTryFreeze_Success() {
        // First recharge
        assetService.recharge(userId, AssetType.USD, new BigDecimal("1000"));

        // Then try freeze
        BigDecimal freezeAmount = new BigDecimal("400");
        Result<Void> result = assetService.tryFreeze(userId, AssetType.USD, freezeAmount);

        assertThat(result.isSuccess()).isTrue();
        
        AssetEntity asset = assetService.getAssetByUidAndType(userId, AssetType.USD).getData();
        assertThat(asset.getAvailable()).isEqualByComparingTo("600");
        assertThat(asset.getFrozen()).isEqualByComparingTo("400");
    }

    @Test
    void testTryFreeze_InsufficientBalance() {
        assetService.recharge(userId, AssetType.USD, new BigDecimal("100"));

        Result<Void> result = assetService.tryFreeze(userId, AssetType.USD, new BigDecimal("200"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.ASSET_NOT_ENOUGH);
        
        AssetEntity asset = assetService.getAssetByUidAndType(userId, AssetType.USD).getData();
        assertThat(asset.getAvailable()).isEqualByComparingTo("100");
        assertThat(asset.getFrozen()).isEqualByComparingTo("0");
    }

    @Test
    void testUnfreeze_Success() {
        assetService.recharge(userId, AssetType.USD, new BigDecimal("1000"));
        assetService.tryFreeze(userId, AssetType.USD, new BigDecimal("400"));

        Result<Void> result = assetService.unfreeze(userId, AssetType.USD, new BigDecimal("150"));

        assertThat(result.isSuccess()).isTrue();
        AssetEntity asset = assetService.getAssetByUidAndType(userId, AssetType.USD).getData();
        assertThat(asset.getAvailable()).isEqualByComparingTo("750");
        assertThat(asset.getFrozen()).isEqualByComparingTo("250");
    }

    @Test
    void testTransferBetweenUsers_Success() {
        String fromUser = "user1";
        String toUser = "user2";
        assetService.recharge(fromUser, AssetType.USD, new BigDecimal("1000"));
        
        Result<Void> result = assetService.transferBetweenUsers(
                AssetTransferType.AVAILABLE_TO_AVAILABLE, 
                fromUser, toUser, AssetType.USD, new BigDecimal("300"));

        assertThat(result.isSuccess()).isTrue();
        
        assertThat(assetService.getAssetByUidAndType(fromUser, AssetType.USD).getData().getAvailable())
                .isEqualByComparingTo("700");
        assertThat(assetService.getAssetByUidAndType(toUser, AssetType.USD).getData().getAvailable())
                .isEqualByComparingTo("300");
    }
}
