package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.entity.DataProcess;
import io.github.radhwent.datatransformer.entity.ProcessRule;

import java.util.List;

public record ProcessDetailResponse(
        Long id,
        String name,
        String description,
        String inputFormat,
        String outputFormat,
        List<ProcessRuleResponse> rules) {

    public static ProcessDetailResponse from(DataProcess process, List<ProcessRule> processRules) {
        return new ProcessDetailResponse(
                process.getId(),
                process.getName(),
                process.getDescription(),
                process.getInputFormat().name(),
                process.getOutputFormat().name(),
                processRules.stream().map(ProcessRuleResponse::from).toList());
    }
}
