package org.example.mylearn.tradingengine.config;

import feign.Logger;
import org.example.mylearn.tradingengine.rpcclient.SequenceService;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@EnableFeignClients(basePackages = "org.example.mylearn.tradingengine.rpcclient")
@Configuration
@EnableDiscoveryClient //貌似写不写都可以
class RpcClientConfiguration {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RpcClientConfiguration.class);
    @Bean
    Logger.Level feignLoggerLevel(){
        return Logger.Level.BASIC;
    }

    // 尝试注入自动装配好的 Client
    @Bean
    public CommandLineRunner checkFeignClient(feign.Client feignClient) {
        return args -> {
            logger.debug("Feign's active Http client is:  " + feignClient.getClass().getName());
            // 如果输出是 HttpClient5FeignClient，你就可以安心去睡觉了
        };
    }
    public RpcClientConfiguration(){
        logger.info("Feign config class '{}' is loaded.", this.getClass().getName());
    }
}
