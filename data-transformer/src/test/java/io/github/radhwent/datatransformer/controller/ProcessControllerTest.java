package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.config.TestcontainersConfiguration;
import io.github.radhwent.datatransformer.entity.DataProcess;
import io.github.radhwent.datatransformer.entity.FileFormat;
import io.github.radhwent.datatransformer.entity.Role;
import io.github.radhwent.datatransformer.entity.Rule;
import io.github.radhwent.datatransformer.entity.RuleType;
import io.github.radhwent.datatransformer.entity.User;
import io.github.radhwent.datatransformer.repository.DataProcessRepository;
import io.github.radhwent.datatransformer.repository.ProcessRuleRepository;
import io.github.radhwent.datatransformer.repository.RuleRepository;
import io.github.radhwent.datatransformer.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.radhwent.datatransformer.repository.DataFileRepository;
import io.github.radhwent.datatransformer.repository.ExecutionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProcessControllerTest {

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
    private Rule regleConcatenate;
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

        regleConcatenate = new Rule();
        regleConcatenate.setType(RuleType.CONCATENATE);
        regleConcatenate.setName("Concatener deux valeurs");
        regleConcatenate = ruleRepository.save(regleConcatenate);

        process = new DataProcess();
        process.setName("Nettoyage clients");
        process.setInputFormat(FileFormat.JSON);
        process.setOutputFormat(FileFormat.JSON);
        process.setUser(proprietaire);
        process = processRepository.save(process);

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

    // --- lecture ---

    @Test
    void getAllSansSessionRenvoie401() throws Exception {
        mockMvc.perform(get("/api/processes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllRenvoieLesTraitementsActifsDeLUtilisateurConnecte() throws Exception {
        mockMvc.perform(get("/api/processes").session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Nettoyage clients"));
    }

    /** Regression : getAll() ne doit jamais renvoyer les traitements d'un autre utilisateur. */
    @Test
    void getAllNeRenvoiePasLesTraitementsDUnAutreUtilisateur() throws Exception {
        DataProcess processAutreUtilisateur = new DataProcess();
        processAutreUtilisateur.setName("Traitement de l'intrus");
        processAutreUtilisateur.setInputFormat(FileFormat.JSON);
        processAutreUtilisateur.setOutputFormat(FileFormat.JSON);
        processAutreUtilisateur.setUser(autreUtilisateur);
        processRepository.save(processAutreUtilisateur);

        mockMvc.perform(get("/api/processes").session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Nettoyage clients"));
    }

    @Test
    void getAllNeRenvoiePasLesTraitementsDesactives() throws Exception {
        process.setActive(false);
        processRepository.save(process);

        mockMvc.perform(get("/api/processes").session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getByIdInexistantRenvoie404() throws Exception {
        // getById verifie desormais la session avant l'existence : il faut
        // etre connecte pour que ce test exerce reellement le cas "404".
        mockMvc.perform(get("/api/processes/999999").session(sessionProprietaire))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByIdSansSessionRenvoie401() throws Exception {
        mockMvc.perform(get("/api/processes/999999"))
                .andExpect(status().isUnauthorized());
    }

    // --- creation ---

    @Test
    void createSansSessionRenvoie401() throws Exception {
        mockMvc.perform(post("/api/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Nouveau", "inputFormat": "JSON", "outputFormat": "JSON" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAvecSessionRenvoie201() throws Exception {
        mockMvc.perform(post("/api/processes")
                        .session(sessionProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Nouveau traitement", "inputFormat": "JSON", "outputFormat": "JSON" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nouveau traitement"));
    }

    @Test
    void createAvecNomDejaPrisRenvoie409() throws Exception {
        mockMvc.perform(post("/api/processes")
                        .session(sessionProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Nettoyage clients", "inputFormat": "JSON", "outputFormat": "JSON" }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void createSansNomRenvoie400() throws Exception {
        mockMvc.perform(post("/api/processes")
                        .session(sessionProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "", "inputFormat": "JSON", "outputFormat": "JSON" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAvecFormatInvalideRenvoie400() throws Exception {
        mockMvc.perform(post("/api/processes")
                        .session(sessionProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Autre", "inputFormat": "PDF", "outputFormat": "JSON" }
                                """))
                .andExpect(status().isBadRequest());
    }

    // --- ajout de regle ---

    @Test
    void addRuleConcatenateSansSourceField2Renvoie400() throws Exception {
        mockMvc.perform(post("/api/processes/" + process.getId() + "/rules")
                        .session(sessionProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ruleId": %d,
                                  "executionOrder": 1,
                                  "sourceField": "prenom",
                                  "targetField": "nomComplet"
                                }
                                """.formatted(regleConcatenate.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addRuleConcatenateCompleteRenvoie201() throws Exception {
        mockMvc.perform(post("/api/processes/" + process.getId() + "/rules")
                        .session(sessionProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ruleId": %d,
                                  "executionOrder": 1,
                                  "sourceField": "prenom",
                                  "sourceField2": "nom",
                                  "targetField": "nomComplet"
                                }
                                """.formatted(regleConcatenate.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleType").value("CONCATENATE"));
    }

    @Test
    void addRuleAvecOrdreDejaPrisRenvoie409() throws Exception {
        String body = """
                {
                  "ruleId": %d,
                  "executionOrder": 1,
                  "sourceField": "prenom",
                  "sourceField2": "nom",
                  "targetField": "nomComplet"
                }
                """.formatted(regleConcatenate.getId());

        mockMvc.perform(post("/api/processes/" + process.getId() + "/rules")
                .session(sessionProprietaire)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        mockMvc.perform(post("/api/processes/" + process.getId() + "/rules")
                        .session(sessionProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // --- modification de regle ---

    /**
     * Regression : mettre a jour une regle existante declenchait
     * "Could not initialize proxy [Rule] - no session" car la regle
     * chargee par requireRuleOfProcess() et celle sauvegardee ensuite
     * appartenaient a deux sessions Hibernate distinctes.
     */
    @Test
    void updateRuleExistanteRenvoie200EtLesNouveauxChamps() throws Exception {
        String reponseCreation = mockMvc.perform(post("/api/processes/" + process.getId() + "/rules")
                        .session(sessionProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ruleId": %d,
                                  "executionOrder": 1,
                                  "sourceField": "prenom",
                                  "sourceField2": "nom",
                                  "targetField": "nomComplet"
                                }
                                """.formatted(regleConcatenate.getId())))
                .andReturn().getResponse().getContentAsString();
        Number ruleId = com.jayway.jsonpath.JsonPath.read(reponseCreation, "$.id");

        mockMvc.perform(put("/api/processes/" + process.getId() + "/rules/" + ruleId.longValue())
                        .session(sessionProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ruleId": %d,
                                  "executionOrder": 1,
                                  "sourceField": "firstname",
                                  "sourceField2": "lastname",
                                  "targetField": "fullName"
                                }
                                """.formatted(regleConcatenate.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceField").value("firstname"))
                .andExpect(jsonPath("$.sourceField2").value("lastname"))
                .andExpect(jsonPath("$.targetField").value("fullName"));
    }

    // --- suppression ---

    @Test
    void deleteParLeProprietaireDesactiveLeTraitement() throws Exception {
        mockMvc.perform(delete("/api/processes/" + process.getId())
                        .session(sessionProprietaire))
                .andExpect(status().isNoContent());

        DataProcess rechargé = processRepository.findById(process.getId()).orElseThrow();
        assertFalse(rechargé.isActive());
    }

    @Test
    void deleteParUnAutreUtilisateurRenvoie404() throws Exception {
        MockHttpSession sessionIntrus = new MockHttpSession();
        sessionIntrus.setAttribute("userId", autreUtilisateur.getId());

        mockMvc.perform(delete("/api/processes/" + process.getId())
                        .session(sessionIntrus))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSansSessionRenvoie401() throws Exception {
        mockMvc.perform(delete("/api/processes/" + process.getId()))
                .andExpect(status().isUnauthorized());
    }
}