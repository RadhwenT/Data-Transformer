package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.entity.User;
import io.github.radhwent.datatransformer.service.AuthService;
import io.github.radhwent.datatransformer.service.UnauthorizedException;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentification par session classique. Les comptes ne sont plus crees
 * librement : seul un administrateur peut en creer via /api/admin/users.
 * L'utilisateur peut en revanche changer son propre mot de passe.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        User user = authService.login(request.email(), request.password());
        session.setAttribute("userId", user.getId());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               HttpSession session) {
        Long userId = currentUserId(session);

        authService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                       HttpSession session) {
        Long userId = currentUserId(session);

        User user = authService.updateProfile(userId, request.firstName(), request.lastName(), request.email());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    private Long currentUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            throw new UnauthorizedException("Vous devez etre connecte");
        }
        return (Long) userId;
    }
}