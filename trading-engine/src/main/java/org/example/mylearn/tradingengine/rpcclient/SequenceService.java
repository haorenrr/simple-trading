package org.example.mylearn.tradingengine.rpcclient;

import org.example.mylearn.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(value = "sequenceClient", path="/seq", url="http://localhost:8081/")
public interface SequenceService {

    @GetMapping("/next")
    Integer newSequence();
}
