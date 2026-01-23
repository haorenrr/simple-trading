package org.example.mylearn.tradingengine.clearing;

import org.example.mylearn.common.Result;
import org.example.mylearn.tradingengine.asset.AssetEntity;
import org.example.mylearn.tradingengine.asset.AssetService;
import org.example.mylearn.tradingengine.asset.AssetType;
import org.example.mylearn.tradingengine.order.OrderEntity;
import org.example.mylearn.tradingengine.order.TradeType;
import org.example.mylearn.tradingengine.rpcclient.SequenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ClearingServiceTest {

    @Mock
    private SequenceService sequenceService;

    @InjectMocks
    private ClearingServiceImpl clearingService;

    private AssetService assetService; // Manually managed

    // Test Users
    private final String BUYER_ID = "buyer_1";
    private final String SELLER_ID = "seller_1";

    @BeforeEach
    void setUp() {
        // Initialize AssetService manually and inject dependencies
        assetService = new AssetService();
        org.springframework.test.util.ReflectionTestUtils.setField(assetService, "sequenceService", sequenceService);
        
        // Inject the manual assetService into clearingService
        org.springframework.test.util.ReflectionTestUtils.setField(clearingService, "assetService", assetService);

        // Mock ID generation for AssetService
        lenient().when(sequenceService.newSequence()).thenReturn(Result.ok(100));

        // Initial Funds Setup
        // Buyer: 1000 USD, 0 APPL
        assetService.recharge(BUYER_ID, AssetType.USD, new BigDecimal("1000"));
        assetService.recharge(BUYER_ID, AssetType.APPL, BigDecimal.ZERO);

        // Seller: 0 USD, 10 APPL
        assetService.recharge(SELLER_ID, AssetType.USD, BigDecimal.ZERO);
        assetService.recharge(SELLER_ID, AssetType.APPL, new BigDecimal("10"));
    }

    private OrderEntity createOrder(String uid, TradeType type, String price, String amount) {
        OrderEntity order = new OrderEntity();
        order.setUid(uid);
        order.setTradeType(type);
        order.setPrice(new BigDecimal(price));
        order.setAmount(new BigDecimal(amount));
        order.setProcessingAmount(new BigDecimal(amount)); // Assume full match for simplicity
        return order;
    }

    @Test
    void testPrepareTrading_FreezeSuccess() {
        // Buyer creates order: Buy 2 APPL @ 100 USD = Need 200 USD frozen
        OrderEntity buyOrder = createOrder(BUYER_ID, TradeType.BUY, "100", "2");

        Result<OrderEntity> result = clearingService.prepareTrading(buyOrder);

        assertThat(result.isSuccess()).isTrue();
        
        // Verify Asset: USD Available 800 (1000-200), Frozen 200
        AssetEntity buyerUsd = assetService.getAssetByUidAndType(BUYER_ID, AssetType.USD).getData();
        assertThat(buyerUsd.getAvailable()).isEqualByComparingTo("800");
        assertThat(buyerUsd.getFrozen()).isEqualByComparingTo("200");
    }

    @Test
    void testFinishTrading_StandardMatch() {
        // Scenario: Buyer takes Seller's order.
        // Price: 100, Amount: 2.
        
        // 1. Prepare (Freeze)
        OrderEntity buyOrder = createOrder(BUYER_ID, TradeType.BUY, "100", "2");
        OrderEntity sellOrder = createOrder(SELLER_ID, TradeType.SELL, "100", "2");
        
        clearingService.prepareTrading(buyOrder);  // Buyer freezes 200 USD
        clearingService.prepareTrading(sellOrder); // Seller freezes 2 APPL

        // 2. Finish Trading (Buyer is Taker)
        // Note: finishTrading expects the Taker as the first arg, and Makers as list
        Result<Void> result = clearingService.finishTrading(buyOrder, List.of(sellOrder));

        assertThat(result.isSuccess()).isTrue();

        // 3. Verify Balances
        
        // Buyer: -200 USD, +2 APPL
        AssetEntity buyerUsd = assetService.getAssetByUidAndType(BUYER_ID, AssetType.USD).getData();
        AssetEntity buyerAppl = assetService.getAssetByUidAndType(BUYER_ID, AssetType.APPL).getData();
        assertThat(buyerUsd.getAvailable()).isEqualByComparingTo("800"); // 1000 - 200
        assertThat(buyerUsd.getFrozen()).isEqualByComparingTo("0");      // Consumed
        assertThat(buyerAppl.getAvailable()).isEqualByComparingTo("2");  // Received
        
        // Seller: +200 USD, -2 APPL
        AssetEntity sellerUsd = assetService.getAssetByUidAndType(SELLER_ID, AssetType.USD).getData();
        AssetEntity sellerAppl = assetService.getAssetByUidAndType(SELLER_ID, AssetType.APPL).getData();
        assertThat(sellerUsd.getAvailable()).isEqualByComparingTo("200"); // Received
        assertThat(sellerAppl.getAvailable()).isEqualByComparingTo("8");  // 10 - 2
        assertThat(sellerAppl.getFrozen()).isEqualByComparingTo("0");     // Consumed
    }

    /**
     * Test Case: Buyer buys at a CHEAPER price than they offered.
     * Buyer Offer: 100 USD.
     * Seller Ask: 90 USD.
     * Expected: Match at 90 USD. Buyer gets refund of 10 USD.
     */
    @Test
    void testFinishTrading_BuyTaker_BetterPrice_Refund() {
        // Buyer wants to buy 1 @ 100
        OrderEntity buyOrder = createOrder(BUYER_ID, TradeType.BUY, "100", "1");
        // Seller selling 1 @ 90
        OrderEntity sellOrder = createOrder(SELLER_ID, TradeType.SELL, "90", "1");

        // 1. Freeze
        clearingService.prepareTrading(buyOrder);  // Freeze 100 USD
        clearingService.prepareTrading(sellOrder); // Freeze 1 APPL

        // 2. Finish (Buyer Taker eats Seller Maker)
        clearingService.finishTrading(buyOrder, List.of(sellOrder));

        // 3. Verify Buyer Refund
        // Cost: 90 USD. Refund: 10 USD.
        AssetEntity buyerUsd = assetService.getAssetByUidAndType(BUYER_ID, AssetType.USD).getData();
        
        // Available: Initial(1000) - Frozen(100) + Refund(10) = 910
        // Wait, logic is: Transfer Frozen(90) -> Seller. Remaining Frozen(10) -> Unfreeze -> Available.
        // So Available should be 900 + 10 = 910.
        assertThat(buyerUsd.getAvailable()).isEqualByComparingTo("910");
        assertThat(buyerUsd.getFrozen()).isEqualByComparingTo("0");
        
        // Seller gets 90
        AssetEntity sellerUsd = assetService.getAssetByUidAndType(SELLER_ID, AssetType.USD).getData();
        assertThat(sellerUsd.getAvailable()).isEqualByComparingTo("90");
    }

    /**
     * Test Case: Seller sell at a Higher price than it offered.
     * seller Offer: 90 USD.
     * buyer Ask: 100 USD.
     * Expected: Match at 100 USD. deal with buyer's price
     */
    @Test
    void testFinishTrading_SellerTaker_BetterPrice_Refund() {
        // Buyer wants to buy 1 @ 100
        OrderEntity buyOrder = createOrder(BUYER_ID, TradeType.BUY, "100", "1");
        // Seller selling 1 @ 90
        OrderEntity sellOrder = createOrder(SELLER_ID, TradeType.SELL, "90", "1");

        // 1. Freeze
        clearingService.prepareTrading(buyOrder);  // Freeze 100 USD
        clearingService.prepareTrading(sellOrder); // Freeze 1 APPL

        // 2. Finish (seller Taker eats buyer Maker)
        clearingService.finishTrading(sellOrder, List.of(buyOrder));

        // 3. Verify Buyer Refund
        // Cost: 100 USD.
        AssetEntity buyerUsd = assetService.getAssetByUidAndType(BUYER_ID, AssetType.USD).getData();

        // Available: Initial(1000) - Frozen(100) = 900
        // So Available should be 900
        assertThat(buyerUsd.getAvailable()).isEqualByComparingTo("900");
        assertThat(buyerUsd.getFrozen()).isEqualByComparingTo("0");

        // Seller gets 100
        AssetEntity sellerUsd = assetService.getAssetByUidAndType(SELLER_ID, AssetType.USD).getData();
        assertThat(sellerUsd.getAvailable()).isEqualByComparingTo("100");
    }
}
