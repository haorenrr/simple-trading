package org.example.mylearn.tradingengine.config;

import org.example.mylearn.tradingengine.asset.AssetService;
import org.example.mylearn.tradingengine.clearing.ClearingServiceImpl;
import org.example.mylearn.tradingengine.engine.TradingServiceImpl;
import org.example.mylearn.tradingengine.match.MatcherServiceImpl;
import org.example.mylearn.tradingengine.order.OrderService;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({TradingServiceImpl.class,
        OrderService.class,
        AssetService.class,
        MatcherServiceImpl.class,
        ClearingServiceImpl.class,
        RpcClientConfiguration.class
})
public class TradingEngineAutoConfiguration {
    /**
     * 其他module依赖本module的package时，要想初始化本module下的bean，只需要在对应Application类上@Import本类即可.
     * 要是不想明确的@Import本类，同时也实现对本module下bean的初始化加载，也有办法，就是采用springboot的starter机制，
     * 参见：org.example.mylearn.common.config.SwaggerUIConfig 内注释说明
     */
}
