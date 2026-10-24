package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.entity.Role;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final PasswordGenerator passwordGenerator = new PasswordGenerator();

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, passwordEncoder, passwordGenerator);
    }

    private User utilisateur(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    // --- createUser ---

    @Test
    void createUserGenereUnMotDePasseEtStockeSonHash() {
        when(userRepository.findByEmail("sarah@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminService.CreatedUser created =
                adminService.createUser("Sarah", "Martin", "sarah@example.com", Role.USER);

        assertEquals("sarah@example.com", created.user().getEmail());
        assertEquals(12, created.rawPassword().length());

        // Le hash stocke ne doit jamais etre le mot de passe en clair
        assertNotEquals(created.rawPassword(), created.user().getPasswordHash());
        assertTrue(passwordEncoder.matches(created.rawPassword(), created.user().getPasswordHash()));
    }

    @Test
    void createUserSansRoleUtiliseUserParDefaut() {
        when(userRepository.findByEmail("sarah@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminService.CreatedUser created =
                adminService.createUser("Sarah", "Martin", "sarah@example.com", null);

        assertEquals(Role.USER, created.user().getRole());
    }

    @Test
    void createUserAvecEmailDejaUtiliseLeveUneException() {
        when(userRepository.findByEmail("sarah@example.com")).thenReturn(Optional.of(new User()));

        assertThrows(EmailAlreadyUsedException.class, () ->
                adminService.createUser("Sarah", "Martin", "sarah@example.com", Role.USER));

        verify(userRepository, never()).save(any(User.class));
    }

    // --- requireAdmin ---

    @Test
    void requireAdminPasseSiLUtilisateurEstAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(utilisateur(1L, Role.ADMIN)));

        adminService.requireAdmin(1L);   // ne doit pas lever d'exception
    }

    @Test
    void requireAdminLeveForbiddenSiLUtilisateurEstSimpleUser() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(utilisateur(2L, Role.USER)));

        assertThrows(ForbiddenException.class, () -> adminService.requireAdmin(2L));
    }

    @Test
    void requireAdminLeveUnauthorizedSiLUtilisateurNExistePas() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> adminService.requireAdmin(999L));
    }

    // --- deactivateUser ---

    @Test
    void deactivateUserDesactiveLeCompte() {
        User cible = utilisateur(2L, Role.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(cible));

        adminService.deactivateUser(1L, 2L);

        assertFalse(cible.isActive());
        verify(userRepository).save(cible);
    }

    @Test
    void unAdminNePeutPasSeDesactiverLuiMeme() {
        assertThrows(IllegalStateException.class, () -> adminService.deactivateUser(1L, 1L));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deactivateUserInexistantLeveUneException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> adminService.deactivateUser(1L, 999L));
    }
}