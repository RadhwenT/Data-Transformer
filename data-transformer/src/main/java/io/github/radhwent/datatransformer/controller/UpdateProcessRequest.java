package io.github.radhwent.datatransformer.controller;

import jakarta.validation.constraints.NotBlank;

public record UpdateProcessRequest(
        @NotBlank(message = "name est obligatoire") String name,
        String description,
        @NotBlank(message = "inputFormat est obligatoire") String inputFormat,
        @NotBlank(message = "outputFormat est obligatoire") String outputFormat) {
}