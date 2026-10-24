package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.service.AdminService.CreatedUser;

public record CreatedUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String role,
        String generatedPassword,
        String avertissement) {

    public static CreatedUserResponse from(CreatedUser created) {
        return new CreatedUserResponse(
                created.user().getId(),
                created.user().getFirstName(),
                created.user().getLastName(),
                created.user().getEmail(),
                created.user().getRole().name(),
                created.rawPassword(),
                "Ce mot de passe ne sera plus jamais affiche. Transmettez-le a l'utilisateur maintenant.");
    }
}
