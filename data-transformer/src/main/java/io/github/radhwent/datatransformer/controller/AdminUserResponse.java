package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.entity.User;

public record AdminUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String role,
        boolean active) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive());
    }
}