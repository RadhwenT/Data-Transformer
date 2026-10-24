package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.entity.Execution;

import java.time.LocalDateTime;

public record ExecutionResponse(
        Long id,
        String status,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String processName,
        Long ownerId) {

    public static ExecutionResponse from(Execution execution) {
        return new ExecutionResponse(
                execution.getId(),
                execution.getStatus().name(),
                execution.getErrorMessage(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getProcess().getName(),
                execution.getUser().getId());
    }
}