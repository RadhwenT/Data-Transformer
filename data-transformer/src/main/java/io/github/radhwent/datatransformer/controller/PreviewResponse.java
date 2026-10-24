package io.github.radhwent.datatransformer.controller;

import java.util.List;
import java.util.Map;

public record PreviewResponse(
        String status,
        String processName,
        String inputFormat,
        String outputFormat,
        int recordCount,
        int previewedCount,
        boolean truncated,
        List<Map<String, Object>> before,
        List<Map<String, Object>> after,
        List<PreviewStep> steps,
        Integer failedRuleOrder,
        String errorMessage) {
}
