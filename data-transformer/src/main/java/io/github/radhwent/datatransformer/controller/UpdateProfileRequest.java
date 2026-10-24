package io.github.radhwent.datatransformer.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "firstName est obligatoire") String firstName,
        @NotBlank(message = "lastName est obligatoire") String lastName,
        @NotBlank(message = "email est obligatoire")
        @Email(message = "email invalide") String email) {
}
