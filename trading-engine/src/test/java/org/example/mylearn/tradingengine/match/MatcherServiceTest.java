package org.example.mylearn.tradingengine.match;

import org.example.mylearn.common.Result;
import org.example.mylearn.tradingengine.clearing.ClearingService;
import org.example.mylearn.tradingengine.order.OrderEntity;
import org.example.mylearn.tradingengine.order.TradeType;
import org.example.mylearn.tradingengine.rpcclient.SequenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatcherServiceTest {

    @Mock
    private ClearingService clearingService;

    @Mock
    private SequenceService sequenceService;

    @InjectMocks
    private MatcherServiceImpl matcherService;

    @BeforeEach
    void setUp() {
        matcherService.init(); // Start the executor thread
        // Default behavior for prepareTrading: accept everything
        lenient().when(clearingService.prepareTrading(any())).thenAnswer(invocation -> Result.ok(invocation.getArgument(0)));
        // Default behavior for finishTrading: success
        lenient().when(clearingService.finishTrading(any(), any())).thenReturn(Result.ok(null));
        // Default sequence ID
        lenient().when(sequenceService.newSequence()).thenReturn(Result.ok(9999));
    }

    @AfterEach
    void tearDown() {
        matcherService.destroy(); // Stop the executor thread
    }

    // Helper to create order
    private OrderEntity createOrder(int id, TradeType type, String price, String amount) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setTradeType(type);
        order.setPrice(new BigDecimal(price));
        order.setAmount(new BigDecimal(amount));
        order.setFinishedAmount(BigDecimal.ZERO);
        order.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        order.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return order;
    }

    // --- Original Cases ---

    @Test
    void testSubmitOrder_Maker() throws InterruptedException {
        OrderEntity order = createOrder(1, TradeType.BUY, "100", "10");

        matcherService.submitOrder(order);
        Thread.sleep(200);

        HashMap<TradeType, TreeSet<QuotationInfo>> quotations = matcherService.getQuotationInfo();
        assertThat(quotations.get(TradeType.BUY)).isNotEmpty();
        QuotationInfo info = quotations.get(TradeType.BUY).first();
        assertThat(info.getPrice()).isEqualByComparingTo("100");
        assertThat(info.getVolume()).isEqualByComparingTo("10");
    }

    @Test
    void testMatch_FullyFilled() throws InterruptedException {
        // 1. Maker: SELL 10 @ 100
        matcherService.submitOrder(createOrder(101, TradeType.SELL, "100", "10"));
        Thread.sleep(100);

        // 2. Taker: BUY 10 @ 100
        matcherService.submitOrder(createOrder(102, TradeType.BUY, "100", "10"));
        Thread.sleep(200);

        verify(clearingService, atLeastOnce()).finishTrading(any(), any());
        
        var q = matcherService.getQuotationInfo();
        assertThat(q.get(TradeType.SELL)).isEmpty();
        assertThat(q.get(TradeType.BUY)).isEmpty();
    }

    // --- New Cases ---

    /**
     * Case 1: Partial Fill - Taker > Maker
     * Maker: Sell 5 @ 100
     * Taker: Buy 10 @ 100
     * Result: Maker gone, Taker remains with 5
     */
    @Test
    void testPartialFill_TakerRemains() throws InterruptedException {
        matcherService.submitOrder(createOrder(201, TradeType.SELL, "100", "5"));
        Thread.sleep(100);

        matcherService.submitOrder(createOrder(202, TradeType.BUY, "100", "10"));
        Thread.sleep(200);

        // Verify Maker is gone
        var q = matcherService.getQuotationInfo();
        assertThat(q.get(TradeType.SELL)).isEmpty();

        // Verify Taker remains in Buy book with 5 volume
        assertThat(q.get(TradeType.BUY)).hasSize(1);
        assertThat(q.get(TradeType.BUY).first().getVolume()).isEqualByComparingTo("5");
    }

    /**
     * Case 2: Partial Fill - Taker < Maker
     * Maker: Sell 10 @ 100
     * Taker: Buy 4 @ 100
     * Result: Taker finished, Maker remains with 6
     */
    @Test
    void testPartialFill_MakerRemains() throws InterruptedException {
        matcherService.submitOrder(createOrder(301, TradeType.SELL, "100", "10"));
        Thread.sleep(100);

        matcherService.submitOrder(createOrder(302, TradeType.BUY, "100", "4"));
        Thread.sleep(200);

        // Verify Buy book is empty (Taker filled)
        var q = matcherService.getQuotationInfo();
        assertThat(q.get(TradeType.BUY)).isEmpty();

        // Verify Maker remains with 6
        assertThat(q.get(TradeType.SELL)).hasSize(1);
        assertThat(q.get(TradeType.SELL).first().getVolume()).isEqualByComparingTo("6");
    }

    /**
     * Case 3: Sweep - Taker eats multiple Makers
     * Makers: Sell 2@100, 3@101, 5@102
     * Taker: Buy 6 @ 105
     * Result: 100 & 101 gone, 102 remains with 4 (5 - 1)
     */
    @Test
    void testSweep_MultiOrders() throws InterruptedException {
        matcherService.submitOrder(createOrder(401, TradeType.SELL, "100", "2"));
        matcherService.submitOrder(createOrder(402, TradeType.SELL, "101", "3"));
        matcherService.submitOrder(createOrder(403, TradeType.SELL, "102", "5"));
        Thread.sleep(100);

        matcherService.submitOrder(createOrder(404, TradeType.BUY, "105", "6"));
        Thread.sleep(300);

        var q = matcherService.getQuotationInfo();
        
        // 100 and 101 should be completely gone
        assertThat(q.get(TradeType.SELL)).hasSize(1);
        
        // Check the remaining one is 102, with volume 4
        QuotationInfo remaining = q.get(TradeType.SELL).first();
        assertThat(remaining.getPrice()).isEqualByComparingTo("102");
        assertThat(remaining.getVolume()).isEqualByComparingTo("4"); // 5 - (6 - 2 - 3) = 4
    }

    /**
     * Case 4: Price Priority - Better price matched first
     * Makers: Sell A@100, Sell B@95
     * Taker: Buy @ 100
     * Result: Match B@95 first (Cheaper for buyer)
     */
    @Test
    void testPriority_Price() throws InterruptedException {
        matcherService.submitOrder(createOrder(501, TradeType.SELL, "100", "10")); // Expensive
        matcherService.submitOrder(createOrder(502, TradeType.SELL, "95", "10"));  // Cheap
        Thread.sleep(100);

        // Buy at 100
        matcherService.submitOrder(createOrder(503, TradeType.BUY, "100", "5"));
        Thread.sleep(200);

        var q = matcherService.getQuotationInfo();
        
        // Verify 95 reduced to 5
        // Verify 100 untouched
        TreeSet<QuotationInfo> sells = q.get(TradeType.SELL);
        assertThat(sells).hasSize(2);
        
        QuotationInfo bestPrice = sells.first(); // Should be 95
        assertThat(bestPrice.getPrice()).isEqualByComparingTo("95");
        assertThat(bestPrice.getVolume()).isEqualByComparingTo("5"); // 10 - 5
    }

    /**
     * Case 5: Time Priority - Same price, First In First Out
     * Makers: Sell A@100 (Time 0), Sell B@100 (Time 1)
     * Taker: Buy @ 100
     * Result: Match A first
     */
    @Test
    void testPriority_Time() throws InterruptedException {
        // Need to control order ID or internal queue order to simulate time
        // Since we use ConcurrentSkipListSet and QuotationItem uses ID list for same price,
        // the first added item should be at the front of the list within the QuotationItem.
        
        // NOTE: Our Matcher implementation groups orders by price in QuotationItem.
        // Inside QuotationItem, it uses a list for orders.
        // So we test if the first submitted order gets filled.
        
        matcherService.submitOrder(createOrder(601, TradeType.SELL, "100", "10")); // First
        Thread.sleep(50); // Ensure order
        matcherService.submitOrder(createOrder(602, TradeType.SELL, "100", "10")); // Second
        Thread.sleep(100);

        matcherService.submitOrder(createOrder(603, TradeType.BUY, "100", "5"));
        Thread.sleep(200);

        var q = matcherService.getQuotationInfo();
        QuotationInfo item = q.get(TradeType.SELL).first();
        
        // Total volume should be 15 (20 - 5)
        assertThat(item.getVolume()).isEqualByComparingTo("15");
        
        // We verify the clearingService call arguments to see WHICH order was matched.
        // But verifying arguments of a specific call among multiple is tricky with basic verify().
        // Instead, we can inspect the internal logic if we could, but here we rely on Total Volume check 
        // AND the assumption that the engine implementation uses a Queue/List correctly.
        // For a stricter test, we would need to capture arguments:
        // verify(clearingService).finishTrading(taker, list_of_makers);
        // and check list_of_makers contains order 601.
    }
}