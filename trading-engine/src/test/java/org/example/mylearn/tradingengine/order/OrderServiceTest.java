package org.example.mylearn.tradingengine.order;

import org.example.mylearn.common.ErrorCode;
import org.example.mylearn.common.Result;
import org.example.mylearn.tradingengine.asset.AssetService;
import org.example.mylearn.tradingengine.asset.AssetType;
import org.example.mylearn.tradingengine.rpcclient.SequenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private AssetService assetService;

    @Mock
    private SequenceService sequenceService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void testCreateOrder_Buy_Success() {
        String userId = "user1";
        BigDecimal price = new BigDecimal("100");
        BigDecimal amount = new BigDecimal("2");
        
        // Mock sequence service
        when(sequenceService.newSequence()).thenReturn(Result.ok(1001));
        
        // Mock asset service freeze
        when(assetService.tryFreeze(eq(userId), eq(AssetType.USD), any(BigDecimal.class)))
                .thenReturn(Result.ok(null));

        Result<OrderEntity> result = orderService.createOrder(
                1, userId, TradeType.BUY, price, amount, OrderStatus.INIT, true);

        assertThat(result.isSuccess()).isTrue();
        OrderEntity order = result.getData();
        assertThat(order.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(order.getPrice()).isEqualTo(price);
        assertThat(order.getAmount()).isEqualTo(amount);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.INIT);
    }

    @Test
    void testCreateOrder_AssetFreezeFailed() {
        String userId = "user1";

        // Mock sequenceService failure
        when(sequenceService.newSequence()).thenReturn(Result.fail(null, ErrorCode.FLOW_CONTROL, "failed"));

        Result<OrderEntity> result = orderService.createOrder(
                1, userId, TradeType.BUY, new BigDecimal("100"), new BigDecimal("2"), OrderStatus.INIT, false);

        assertThat(result.isSuccess()).isFalse();
    }
}
