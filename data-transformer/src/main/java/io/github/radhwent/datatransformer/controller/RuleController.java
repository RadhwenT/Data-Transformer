package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.repository.RuleRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleRepository ruleRepository;

    public RuleController(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @GetMapping
    public ResponseEntity<List<RuleResponse>> getAll() {
        List<RuleResponse> rules = ruleRepository.findAll()
                .stream()
                .map(RuleResponse::from)
                .toList();
        return ResponseEntity.ok(rules);
    }
}
