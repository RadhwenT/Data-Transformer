package io.github.radhwent.datatransformer.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProcessRuleRequest(
        @NotNull(message = "ruleId est obligatoire") Long ruleId,
        @Min(value = 1, message = "executionOrder doit etre >= 1") int executionOrder,
        @NotBlank(message = "sourceField est obligatoire") String sourceField,
        String sourceField2,
        String targetField) {
}
