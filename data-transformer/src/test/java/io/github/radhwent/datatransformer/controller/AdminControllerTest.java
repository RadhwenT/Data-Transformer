package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.config.TestcontainersConfiguration;
import io.github.radhwent.datatransformer.entity.DataProcess;
import io.github.radhwent.datatransformer.entity.Execution;
import io.github.radhwent.datatransformer.entity.FileFormat;
import io.github.radhwent.datatransformer.entity.Role;
import io.github.radhwent.datatransformer.entity.Status;
import io.github.radhwent.datatransformer.entity.User;
import io.github.radhwent.datatransformer.repository.DataFileRepository;
import io.github.radhwent.datatransformer.repository.DataProcessRepository;
import io.github.radhwent.datatransformer.repository.ExecutionRepository;
import io.github.radhwent.datatransformer.repository.ProcessRuleRepository;
import io.github.radhwent.datatransformer.repository.RuleRepository;
import io.github.radhwent.datatransformer.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vue admin agregee (dashboard admin) : /api/admin/processes et /api/admin/executions
 * doivent renvoyer les donnees de tous les utilisateurs, avec ownerId, et rester
 * reservees aux administrateurs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private DataProcessRepository processRepository;
    @Autowired private ProcessRuleRepository processRuleRepository;
    @Autowired private RuleRepository ruleRepository;
    @Autowired private ExecutionRepository executionRepository;
    @Autowired private DataFileRepository dataFileRepository;

    private User admin;
    private User utilisateurA;
    private User utilisateurB;
    private DataProcess processA;
    private DataProcess processB;
    private MockHttpSession sessionAdmin;
    private MockHttpSession sessionUtilisateurA;

    @BeforeEach
    void setUp() {
        dataFileRepository.deleteAll();
        executionRepository.deleteAll();
        processRuleRepository.deleteAll();
        processRepository.deleteAll();
        ruleRepository.deleteAll();
        userRepository.deleteAll();

        admin = creerUtilisateur("admin@example.com", Role.ADMIN);
        utilisateurA = creerUtilisateur("a@example.com", Role.USER);
        utilisateurB = creerUtilisateur("b@example.com", Role.USER);

        processA = creerProcess("Traitement A", utilisateurA);
        processB = creerProcess("Traitement B", utilisateurB);

        creerExecution(processA, utilisateurA, Status.SUCCESS);
        creerExecution(processB, utilisateurB, Status.FAILED);

        sessionAdmin = new MockHttpSession();
        sessionAdmin.setAttribute("userId", admin.getId());

        sessionUtilisateurA = new MockHttpSession();
        sessionUtilisateurA.setAttribute("userId", utilisateurA.getId());
    }

    private User creerUtilisateur(String email, Role role) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        return userRepository.save(user);
    }

    private DataProcess creerProcess(String name, User owner) {
        DataProcess process = new DataProcess();
        process.setName(name);
        process.setInputFormat(FileFormat.JSON);
        process.setOutputFormat(FileFormat.JSON);
        process.setUser(owner);
        return processRepository.save(process);
    }

    private Execution creerExecution(DataProcess process, User owner, Status status) {
        Execution execution = new Execution();
        execution.setProcess(process);
        execution.setUser(owner);
        execution.setStatus(status);
        execution.setStartedAt(LocalDateTime.now());
        execution.setFinishedAt(LocalDateTime.now());
        return executionRepository.save(execution);
    }

    // --- /api/admin/processes ---

    @Test
    void listAllProcessesSansSessionRenvoie401() throws Exception {
        mockMvc.perform(get("/api/admin/processes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAllProcessesRefuseUnUtilisateurNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/processes").session(sessionUtilisateurA))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllProcessesRenvoieLesTraitementsDeTousLesUtilisateursAvecOwnerId() throws Exception {
        mockMvc.perform(get("/api/admin/processes").session(sessionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name == 'Traitement A')].ownerId").value(utilisateurA.getId().intValue()))
                .andExpect(jsonPath("$[?(@.name == 'Traitement B')].ownerId").value(utilisateurB.getId().intValue()));
    }

    // --- /api/admin/executions ---

    @Test
    void listAllExecutionsSansSessionRenvoie401() throws Exception {
        mockMvc.perform(get("/api/admin/executions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAllExecutionsRefuseUnUtilisateurNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/executions").session(sessionUtilisateurA))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllExecutionsRenvoieLesExecutionsDeTousLesUtilisateursAvecOwnerId() throws Exception {
        mockMvc.perform(get("/api/admin/executions").session(sessionAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.processName == 'Traitement A')].status").value("SUCCESS"))
                .andExpect(jsonPath("$[?(@.processName == 'Traitement A')].ownerId").value(utilisateurA.getId().intValue()))
                .andExpect(jsonPath("$[?(@.processName == 'Traitement B')].status").value("FAILED"));
    }
}
