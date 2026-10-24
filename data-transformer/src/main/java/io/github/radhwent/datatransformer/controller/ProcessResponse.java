package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.entity.DataProcess;
import io.github.radhwent.datatransformer.entity.Execution;

import java.time.LocalDateTime;

public record ProcessResponse(
        Long id,
        String name,
        String description,
        String inputFormat,
        String outputFormat,
        LocalDateTime createdAt,
        long ruleCount,
        String lastRunStatus,
        LocalDateTime lastRunAt,
        Long lastRunExecutionId,
        Long ownerId) {

    /** Version simple, sans les informations agregees. */
    public static ProcessResponse from(DataProcess process) {
        return from(process, 0, null);
    }

    /** Version complete, pour la liste des traitements. */
    public static ProcessResponse from(DataProcess process, long ruleCount, Execution lastRun) {
        return new ProcessResponse(
                process.getId(),
                process.getName(),
                process.getDescription(),
                process.getInputFormat().name(),
                process.getOutputFormat().name(),
                process.getCreatedAt(),
                ruleCount,
                lastRun != null ? lastRun.getStatus().name() : null,
                lastRun != null ? lastRun.getStartedAt() : null,
                lastRun != null ? lastRun.getId() : null,
                process.getUser().getId());
    }
}