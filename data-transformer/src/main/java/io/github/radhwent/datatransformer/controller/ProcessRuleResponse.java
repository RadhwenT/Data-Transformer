package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.entity.ProcessRule;

public record ProcessRuleResponse(
        Long id,
        int executionOrder,
        String ruleName,
        String ruleType,
        String sourceField,
        String sourceField2,
        String targetField) {

    public static ProcessRuleResponse from(ProcessRule processRule) {
        return new ProcessRuleResponse(
                processRule.getId(),
                processRule.getExecutionOrder(),
                processRule.getRule().getName(),
                processRule.getRule().getType().name(),
                processRule.getSourceField(),
                processRule.getSourceField2(),
                processRule.getTargetField());
    }
}
