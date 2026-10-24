package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.config.TestcontainersConfiguration;
import io.github.radhwent.datatransformer.entity.Role;
import io.github.radhwent.datatransformer.entity.User;
import io.github.radhwent.datatransformer.repository.DataFileRepository;
import io.github.radhwent.datatransformer.repository.DataProcessRepository;
import io.github.radhwent.datatransformer.repository.ExecutionRepository;
import io.github.radhwent.datatransformer.repository.ProcessRuleRepository;
import io.github.radhwent.datatransformer.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataFileRepository dataFileRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ProcessRuleRepository processRuleRepository;

    @Autowired
    private DataProcessRepository processRepository;

    private User utilisateur;

    @BeforeEach
    void setUp() {
        // Ordre impose par les contraintes de cle etrangere : les executions/traitements
        // d'un utilisateur crees par d'autres tests doivent partir avant l'utilisateur lui-meme.
        dataFileRepository.deleteAll();
        executionRepository.deleteAll();
        processRuleRepository.deleteAll();
        processRepository.deleteAll();
        userRepository.deleteAll();

        utilisateur = new User();
        utilisateur.setFirstName("Sarah");
        utilisateur.setLastName("Martin");
        utilisateur.setEmail("sarah@example.com");
        utilisateur.setPasswordHash(passwordEncoder.encode("motdepasse123"));
        utilisateur.setRole(Role.USER);
        utilisateur = userRepository.save(utilisateur);
    }

    @Test
    void loginAvecBonsIdentifiantsRenvoie200EtPoseLaSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "sarah@example.com", "password": "motdepasse123" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sarah@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertNotNull(session.getAttribute("userId"));
    }

    @Test
    void loginAvecMauvaisMotDePasseRenvoie401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "sarah@example.com", "password": "mauvais" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginAvecEmailInvalideRenvoie400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "pas-un-email", "password": "motdepasse123" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginAvecCompteDesactiveRenvoie401() throws Exception {
        utilisateur.setActive(false);
        userRepository.save(utilisateur);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "sarah@example.com", "password": "motdepasse123" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordSansSessionRenvoie401() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currentPassword": "motdepasse123", "newPassword": "nouveau456" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordAvecSessionRemplaceLeMotDePasse() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", utilisateur.getId());

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currentPassword": "motdepasse123", "newPassword": "nouveau456" }
                                """))
                .andExpect(status().isNoContent());

        User rechargé = userRepository.findById(utilisateur.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("nouveau456", rechargé.getPasswordHash()));
    }

    @Test
    void changePasswordSansChiffreRenvoie400() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", utilisateur.getId());

        mockMvc.perform(post("/api/auth/change-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currentPassword": "motdepasse123", "newPassword": "aucunchiffre" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfileSansSessionRenvoie401() throws Exception {
        mockMvc.perform(put("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Sarah", "lastName": "Martin", "email": "sarah2@example.com" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfileAvecSessionMetAJourLIdentite() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", utilisateur.getId());

        mockMvc.perform(put("/api/auth/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Sarah-Jane", "lastName": "Martin", "email": "sarah.jane@example.com" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Sarah-Jane"))
                .andExpect(jsonPath("$.email").value("sarah.jane@example.com"));

        User rechargé = userRepository.findById(utilisateur.getId()).orElseThrow();
        assertTrue(rechargé.getEmail().equals("sarah.jane@example.com"));
    }

    @Test
    void updateProfileVersUnEmailDejaUtiliseRenvoie409() throws Exception {
        User autre = new User();
        autre.setFirstName("Youssef");
        autre.setLastName("Trabelsi");
        autre.setEmail("youssef@example.com");
        autre.setPasswordHash(passwordEncoder.encode("motdepasse123"));
        autre.setRole(Role.USER);
        userRepository.save(autre);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", utilisateur.getId());

        mockMvc.perform(put("/api/auth/me")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Sarah", "lastName": "Martin", "email": "youssef@example.com" }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void logoutInvalideLaSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", utilisateur.getId());

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());

        assertTrue(session.isInvalid());
    }

    @Test
    void registerNExistePlus() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "X", "lastName": "Y", "email": "x@y.com", "password": "motdepasse" }
                                """))
                .andExpect(status().isNotFound());
    }
}