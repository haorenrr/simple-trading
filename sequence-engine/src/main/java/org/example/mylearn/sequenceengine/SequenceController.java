package org.example.mylearn.sequenceengine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/seq")
class SequenceController {

    @Autowired
    private SequenceService sequenceService;

    @GetMapping("/next")
    public Integer next() {
        return sequenceService.newSequence();
    }
}
