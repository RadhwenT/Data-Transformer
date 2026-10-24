package io.github.radhwent.datatransformer.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email est obligatoire") @Email(message = "email invalide") String email,
        @NotBlank(message = "password est obligatoire") String password) {
}
