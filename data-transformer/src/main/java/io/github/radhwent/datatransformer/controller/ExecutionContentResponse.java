package io.github.radhwent.datatransformer.controller;

public record ExecutionContentResponse(
        String input,
        String output,
        boolean inputTruncated,
        boolean outputTruncated) {
}
