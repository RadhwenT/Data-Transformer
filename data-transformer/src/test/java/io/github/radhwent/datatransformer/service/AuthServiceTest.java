package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.entity.User;
import io.github.radhwent.datatransformer.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    private User utilisateurAvecMotDePasse(String rawPassword) {
        User user = new User();
        user.setEmail("sarah@example.com");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return user;
    }

    // --- login ---

    @Test
    void loginAvecBonMotDePasseRenvoieLUtilisateur() {
        when(userRepository.findByEmailAndActiveTrue("sarah@example.com"))
                .thenReturn(Optional.of(utilisateurAvecMotDePasse("motdepasse123")));

        User result = authService.login("sarah@example.com", "motdepasse123");

        assertEquals("sarah@example.com", result.getEmail());
    }

    @Test
    void loginAvecMauvaisMotDePasseLeveUnauthorized() {
        when(userRepository.findByEmailAndActiveTrue("sarah@example.com"))
                .thenReturn(Optional.of(utilisateurAvecMotDePasse("motdepasse123")));

        assertThrows(UnauthorizedException.class, () ->
                authService.login("sarah@example.com", "mauvais-mot-de-passe"));
    }

    @Test
    void loginAvecEmailInconnuLeveUnauthorized() {
        when(userRepository.findByEmailAndActiveTrue("inconnu@example.com")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () ->
                authService.login("inconnu@example.com", "peu-importe"));
    }

    // --- changePassword ---

    @Test
    void changePasswordRemplaceLeHashQuandLAncienEstCorrect() {
        User user = utilisateurAvecMotDePasse("ancien123");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.changePassword(1L, "ancien123", "nouveau456");

        assertTrue(passwordEncoder.matches("nouveau456", user.getPasswordHash()));
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordAvecMauvaisAncienMotDePasseLeveUnauthorized() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(utilisateurAvecMotDePasse("ancien123")));

        assertThrows(UnauthorizedException.class, () ->
                authService.changePassword(1L, "mauvais", "nouveau456"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePasswordAvecLeMemeMotDePasseLeveUneErreur() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(utilisateurAvecMotDePasse("ancien123")));

        assertThrows(IllegalStateException.class, () ->
                authService.changePassword(1L, "ancien123", "ancien123"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePasswordAvecUtilisateurInexistantLeveUnauthorized() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () ->
                authService.changePassword(999L, "ancien123", "nouveau456"));
    }

        @Test
    void loginAvecCompteDesactiveLeveUnauthorized() {
        // findByEmailAndActiveTrue ne renvoie rien pour un compte inactif
        when(userRepository.findByEmailAndActiveTrue("desactive@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () ->
                authService.login("desactive@example.com", "motdepasse123"));
    }
}