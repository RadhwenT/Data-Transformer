package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.config.TestcontainersConfiguration;
import io.github.radhwent.datatransformer.entity.DataProcess;
import io.github.radhwent.datatransformer.entity.FileFormat;
import io.github.radhwent.datatransformer.entity.ProcessRule;
import io.github.radhwent.datatransformer.entity.Role;
import io.github.radhwent.datatransformer.entity.Rule;
import io.github.radhwent.datatransformer.entity.RuleType;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PreviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private DataProcessRepository processRepository;
    @Autowired private ProcessRuleRepository processRuleRepository;
    @Autowired private RuleRepository ruleRepository;
    @Autowired private ExecutionRepository executionRepository;
    @Autowired private DataFileRepository dataFileRepository;

    private User proprietaire;
    private User autreUtilisateur;
    private DataProcess process;
    private MockHttpSession sessionProprietaire;

    @BeforeEach
    void setUp() {
        dataFileRepository.deleteAll();
        executionRepository.deleteAll();
        processRuleRepository.deleteAll();
        processRepository.deleteAll();
        ruleRepository.deleteAll();
        userRepository.deleteAll();

        proprietaire = creerUtilisateur("sarah@example.com");
        autreUtilisateur = creerUtilisateur("intrus@example.com");

        process = new DataProcess();
        process.setName("Nettoyage clients");
        process.setInputFormat(FileFormat.JSON);
        process.setOutputFormat(FileFormat.JSON);
        process.setUser(proprietaire);
        process = processRepository.save(process);

        ajouterRegle(RuleType.CONCATENATE, 1, "prenom", "nom", "nomComplet");
        ajouterRegle(RuleType.UPPERCASE, 2, "nomComplet", null, null);
        ajouterRegle(RuleType.FORMAT_DATE, 3, "date_naissance", null, null);

        sessionProprietaire = new MockHttpSession();
        sessionProprietaire.setAttribute("userId", proprietaire.getId());
    }

    private User creerUtilisateur(String email) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private void ajouterRegle(RuleType type, int ordre, String source, String source2, String cible) {
        Rule rule = new Rule();
        rule.setType(type);
        rule.setName(type.name());
        rule = ruleRepository.save(rule);

        ProcessRule pr = new ProcessRule();
        pr.setProcess(process);
        pr.setRule(rule);
        pr.setExecutionOrder(ordre);
        pr.setSourceField(source);
        pr.setSourceField2(source2);
        pr.setTargetField(cible);
        processRuleRepository.save(pr);
    }

    private MockMultipartFile fichierJson(String contenu) {
        return new MockMultipartFile("file", "clients.json", "application/json",
                contenu.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile fichierValide() {
        return fichierJson("""
                { "prenom": "sarah", "nom": "martin", "date_naissance": "14/03/1995" }
                """);
    }

    @Test
    void previewSansSessionRenvoie401() throws Exception {
        mockMvc.perform(multipart("/api/processes/" + process.getId() + "/preview")
                        .file(fichierValide()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void previewSurTraitementDUnAutreUtilisateurRenvoie404() throws Exception {
        MockHttpSession sessionIntrus = new MockHttpSession();
        sessionIntrus.setAttribute("userId", autreUtilisateur.getId());

        mockMvc.perform(multipart("/api/processes/" + process.getId() + "/preview")
                        .file(fichierValide())
                        .session(sessionIntrus))
                .andExpect(status().isNotFound());
    }

    @Test
    void previewFichierCorrompuRenvoie400() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("file", "notes.txt", "text/plain",
                "ceci n'est pas du json".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/processes/" + process.getId() + "/preview")
                        .file(fichier)
                        .session(sessionProprietaire))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewFichierTropVolumineuxRenvoie400() throws Exception {
        byte[] contenu = new byte[2 * 1024 * 1024]; // 2 Mo, au-dela de app.preview.max-file-size (1 Mo)
        java.util.Arrays.fill(contenu, (byte) ' ');
        MockMultipartFile fichier = new MockMultipartFile("file", "gros.json", "application/json", contenu);

        mockMvc.perform(multipart("/api/processes/" + process.getId() + "/preview")
                        .file(fichier)
                        .session(sessionProprietaire))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewValideRenvoie200AvecStatusOk() throws Exception {
        mockMvc.perform(multipart("/api/processes/" + process.getId() + "/preview")
                        .file(fichierValide())
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.processName").value("Nettoyage clients"))
                .andExpect(jsonPath("$.recordCount").value(1))
                .andExpect(jsonPath("$.after[0].nomComplet").value("SARAH MARTIN"))
                .andExpect(jsonPath("$.after[0].date_naissance").value("1995-03-14"))
                .andExpect(jsonPath("$.steps.length()").value(3));
    }

    @Test
    void previewChampManquantRenvoie200AvecStatusErrorEtFailedRuleOrder() throws Exception {
        MockMultipartFile fichier = fichierJson("""
                { "prenom": "sarah", "date_naissance": "14/03/1995" }
                """);

        mockMvc.perform(multipart("/api/processes/" + process.getId() + "/preview")
                        .file(fichier)
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.failedRuleOrder").value(1))
                .andExpect(jsonPath("$.after").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").exists());
    }

    @Test
    void previewNeCreeAucuneExecutionNiAucunFichier() throws Exception {
        long executionsAvant = executionRepository.count();
        long fichiersAvant = dataFileRepository.count();

        mockMvc.perform(multipart("/api/processes/" + process.getId() + "/preview")
                        .file(fichierValide())
                        .session(sessionProprietaire))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(executionsAvant, executionRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(fichiersAvant, dataFileRepository.count());
    }
}
