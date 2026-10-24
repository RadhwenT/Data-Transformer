package io.github.radhwent.datatransformer.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "currentPassword est obligatoire") String currentPassword,
        @NotBlank(message = "newPassword est obligatoire")
        @Size(min = 8, message = "newPassword doit contenir au moins 8 caracteres")
        @Pattern(regexp = ".*\\d.*", message = "newPassword doit contenir au moins un chiffre") String newPassword) {
}
