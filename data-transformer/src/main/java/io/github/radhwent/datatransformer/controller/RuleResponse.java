package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.entity.Rule;

public record RuleResponse(
        Long id,
        String name,
        String type,
        String description) {

    public static RuleResponse from(Rule rule) {
        return new RuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getType().name(),
                rule.getDescription());
                
    }
}
