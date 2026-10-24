package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.entity.DataFile;
import io.github.radhwent.datatransformer.entity.Execution;
import io.github.radhwent.datatransformer.entity.FileType;
import io.github.radhwent.datatransformer.entity.ProcessRule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record ExecutionDetailResponse(
        Long id,
        String status,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String processName,
        Long processId,
        String inputFormat,
        String outputFormat,
        Long durationMs,
        String inputFileName,
        boolean hasOutput,
        List<ProcessRuleResponse> rules) {

    public static ExecutionDetailResponse from(Execution execution, List<DataFile> files, List<ProcessRule> rules) {
        Long durationMs = execution.getFinishedAt() != null
                ? Duration.between(execution.getStartedAt(), execution.getFinishedAt()).toMillis()
                : null;

        String inputFileName = files.stream()
                .filter(f -> f.getType() == FileType.INPUT)
                .findFirst()
                .map(DataFile::getName)
                .orElse(null);

        boolean hasOutput = files.stream().anyMatch(f -> f.getType() == FileType.OUTPUT);

        return new ExecutionDetailResponse(
                execution.getId(),
                execution.getStatus().name(),
                execution.getErrorMessage(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getProcess().getName(),
                execution.getProcess().getId(),
                execution.getProcess().getInputFormat().name(),
                execution.getProcess().getOutputFormat().name(),
                durationMs,
                inputFileName,
                hasOutput,
                rules.stream().map(ProcessRuleResponse::from).toList());
    }
}
