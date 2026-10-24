package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.entity.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank(message = "firstName est obligatoire") String firstName,
        @NotBlank(message = "lastName est obligatoire") String lastName,
        @NotBlank(message = "email est obligatoire") @Email(message = "email invalide") String email,
        Role role) {
}
