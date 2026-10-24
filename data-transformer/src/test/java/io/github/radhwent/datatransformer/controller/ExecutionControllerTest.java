package io.github.radhwent.datatransformer.controller;

import com.jayway.jsonpath.JsonPath;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExecutionControllerTest {

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

    // --- execution ---

    @Test
    void executeSansSessionRenvoie401() throws Exception {
        mockMvc.perform(multipart("/api/executions")
                        .file(fichierValide())
                        .param("processId", process.getId().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void executeAvecSessionRenvoie200EtSucces() throws Exception {
        mockMvc.perform(multipart("/api/executions")
                        .file(fichierValide())
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void executeAvecChampManquantRenvoie200EtFailed() throws Exception {
        MockMultipartFile fichier = fichierJson("""
                { "prenom": "sarah", "date_naissance": "14/03/1995" }
                """);

        mockMvc.perform(multipart("/api/executions")
                        .file(fichier)
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").exists());
    }

    @Test
    void executeAvecFichierNonJsonRenvoie400() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("file", "notes.txt", "text/plain",
                "ceci n'est pas du json".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/executions")
                        .file(fichier)
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire))
                .andExpect(status().isBadRequest());
    }

    // Note : MaxUploadSizeExceededException (413, handleTooLarge) n'est pas testable ici via
    // MockMvc — @AutoConfigureMockMvc ne passe pas par un vrai conteneur servlet, donc
    // spring.servlet.multipart.max-file-size n'est jamais applique par le mock. Voir le test
    // unitaire dedie GlobalExceptionHandlerTest.handleTooLargeRenvoie413.

    @Test
    void executeLeTraitementDUnAutreRenvoie404() throws Exception {
        MockHttpSession sessionIntrus = new MockHttpSession();
        sessionIntrus.setAttribute("userId", autreUtilisateur.getId());

        mockMvc.perform(multipart("/api/executions")
                        .file(fichierValide())
                        .param("processId", process.getId().toString())
                        .session(sessionIntrus))
                .andExpect(status().isNotFound());
    }

    // --- historique ---

    @Test
    void getAllSansSessionRenvoie401() throws Exception {
        mockMvc.perform(get("/api/executions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllRenvoieLHistoriqueDeLUtilisateurConnecte() throws Exception {
        mockMvc.perform(multipart("/api/executions")
                .file(fichierValide())
                .param("processId", process.getId().toString())
                .session(sessionProprietaire));

        // Verifie aussi le contenu (pas seulement la longueur) : processName
        // vient d'un accès lazy sur DataProcess ; sans JOIN FETCH cote
        // repository, la serialisation plante en LazyInitializationException
        // (500) malgre un statut HTTP correct sur l'appel precedent.
        mockMvc.perform(get("/api/executions").session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].processName").value("Nettoyage clients"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    void getByIdRenvoieLeDetailAvecLeNomDuTraitement() throws Exception {
        String reponse = mockMvc.perform(multipart("/api/executions")
                        .file(fichierValide())
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire))
                .andReturn().getResponse().getContentAsString();

        Number executionId = JsonPath.read(reponse, "$.id");

        mockMvc.perform(get("/api/executions/" + executionId.longValue()).session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processName").value("Nettoyage clients"));
    }

    // --- telechargement ---

    @Test
    void downloadApresSuccesRenvoieLeFichierTransforme() throws Exception {
        String reponse = mockMvc.perform(multipart("/api/executions")
                        .file(fichierValide())
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire))
                .andReturn().getResponse().getContentAsString();

        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/executions/" + executionId + "/download")
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SARAH MARTIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1995-03-14")));
    }

    /**
     * Regression : le fichier de sortie d'un traitement JSON -> XML doit
     * s'appeler "output_....xml" (pas "....json", extension du fichier
     * d'entree uploade) et se telecharger en application/xml.
     */
    @Test
    void downloadDunTraitementJsonVersXmlRenvoieUneExtensionEtUnContentTypeXml() throws Exception {
        DataProcess processXml = new DataProcess();
        processXml.setName("Export XML");
        processXml.setInputFormat(FileFormat.JSON);
        processXml.setOutputFormat(FileFormat.XML);
        processXml.setUser(proprietaire);
        processXml = processRepository.save(processXml);

        Rule uppercase = new Rule();
        uppercase.setType(RuleType.UPPERCASE);
        uppercase.setName("UPPERCASE");
        uppercase = ruleRepository.save(uppercase);

        ProcessRule pr = new ProcessRule();
        pr.setProcess(processXml);
        pr.setRule(uppercase);
        pr.setExecutionOrder(1);
        pr.setSourceField("prenom");
        processRuleRepository.save(pr);

        mockMvc.perform(multipart("/api/executions")
                .file(fichierValide())
                .param("processId", processXml.getId().toString())
                .session(sessionProprietaire));

        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/executions/" + executionId + "/download")
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xml\"")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(".json"))))
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_XML));
    }

    @Test
    void downloadSansSessionRenvoie401() throws Exception {
        mockMvc.perform(multipart("/api/executions")
                .file(fichierValide())
                .param("processId", process.getId().toString())
                .session(sessionProprietaire));

        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/executions/" + executionId + "/download"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadParUnAutreUtilisateurRenvoie404() throws Exception {
        mockMvc.perform(multipart("/api/executions")
                .file(fichierValide())
                .param("processId", process.getId().toString())
                .session(sessionProprietaire));

        Long executionId = executionRepository.findAll().get(0).getId();

        MockHttpSession sessionIntrus = new MockHttpSession();
        sessionIntrus.setAttribute("userId", autreUtilisateur.getId());

        mockMvc.perform(get("/api/executions/" + executionId + "/download")
                        .session(sessionIntrus))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadApresEchecRenvoie409() throws Exception {
        MockMultipartFile fichier = fichierJson("""
                { "prenom": "sarah", "date_naissance": "14/03/1995" }
                """);

        mockMvc.perform(multipart("/api/executions")
                .file(fichier)
                .param("processId", process.getId().toString())
                .session(sessionProprietaire));

        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/executions/" + executionId + "/download")
                        .session(sessionProprietaire))
                .andExpect(status().isConflict());
    }

    // --- telechargement du fichier d'entree ---

    @Test
    void downloadInputRenvoieLeFichierEnvoyeApresSucces() throws Exception {
        mockMvc.perform(multipart("/api/executions")
                        .file(fichierValide())
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire));

        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/executions/" + executionId + "/download-input")
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sarah")));
    }

    /** A la difference de /download, le fichier d'entree reste disponible meme si l'execution a echoue. */
    @Test
    void downloadInputResteDisponibleApresEchec() throws Exception {
        MockMultipartFile fichier = fichierJson("""
                { "prenom": "sarah", "date_naissance": "14/03/1995" }
                """);

        mockMvc.perform(multipart("/api/executions")
                .file(fichier)
                .param("processId", process.getId().toString())
                .session(sessionProprietaire));

        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/executions/" + executionId + "/download-input")
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sarah")));
    }

    @Test
    void downloadInputSansSessionRenvoie401() throws Exception {
        mockMvc.perform(multipart("/api/executions")
                .file(fichierValide())
                .param("processId", process.getId().toString())
                .session(sessionProprietaire));

        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/executions/" + executionId + "/download-input"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadInputParUnAutreUtilisateurRenvoie404() throws Exception {
        mockMvc.perform(multipart("/api/executions")
                .file(fichierValide())
                .param("processId", process.getId().toString())
                .session(sessionProprietaire));

        Long executionId = executionRepository.findAll().get(0).getId();

        MockHttpSession sessionIntrus = new MockHttpSession();
        sessionIntrus.setAttribute("userId", autreUtilisateur.getId());

        mockMvc.perform(get("/api/executions/" + executionId + "/download-input")
                        .session(sessionIntrus))
                .andExpect(status().isNotFound());
    }

    // --- filtre par traitement ---

    @Test
    void getAllFiltreParProcessIdNeRenvoieQueCeTraitement() throws Exception {
        DataProcess autreProcess = new DataProcess();
        autreProcess.setName("Autre traitement");
        autreProcess.setInputFormat(FileFormat.JSON);
        autreProcess.setOutputFormat(FileFormat.JSON);
        autreProcess.setUser(proprietaire);
        autreProcess = processRepository.save(autreProcess);

        mockMvc.perform(multipart("/api/executions")
                .file(fichierValide())
                .param("processId", process.getId().toString())
                .session(sessionProprietaire));
        mockMvc.perform(multipart("/api/executions")
                .file(fichierValide())
                .param("processId", autreProcess.getId().toString())
                .session(sessionProprietaire));

        mockMvc.perform(get("/api/executions")
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].processName").value("Nettoyage clients"));
    }

    @Test
    void getAllFiltreParProcessIdDUnAutreUtilisateurRenvoie404() throws Exception {
        MockHttpSession sessionIntrus = new MockHttpSession();
        sessionIntrus.setAttribute("userId", autreUtilisateur.getId());

        mockMvc.perform(get("/api/executions")
                        .param("processId", process.getId().toString())
                        .session(sessionIntrus))
                .andExpect(status().isNotFound());
    }

    // --- detail enrichi ---

    @Test
    void getByIdRenvoieLesDetailsEnrichis() throws Exception {
        String reponse = mockMvc.perform(multipart("/api/executions")
                        .file(fichierValide())
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire))
                .andReturn().getResponse().getContentAsString();
        Number executionId = JsonPath.read(reponse, "$.id");

        mockMvc.perform(get("/api/executions/" + executionId.longValue()).session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processId").value(process.getId()))
                .andExpect(jsonPath("$.inputFormat").value("JSON"))
                .andExpect(jsonPath("$.outputFormat").value("JSON"))
                .andExpect(jsonPath("$.durationMs").isNumber())
                .andExpect(jsonPath("$.inputFileName").value("clients.json"))
                .andExpect(jsonPath("$.hasOutput").value(true))
                .andExpect(jsonPath("$.rules.length()").value(3))
                .andExpect(jsonPath("$.rules[0].ruleType").value("CONCATENATE"));
    }

    // --- contenu (input/output affiches a l'ecran) ---

    @Test
    void contentApresSuccesRenvoieInputEtOutput() throws Exception {
        String reponse = mockMvc.perform(multipart("/api/executions")
                        .file(fichierValide())
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire))
                .andReturn().getResponse().getContentAsString();
        Number executionId = JsonPath.read(reponse, "$.id");

        mockMvc.perform(get("/api/executions/" + executionId.longValue() + "/content")
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString("sarah")))
                .andExpect(jsonPath("$.output").value(org.hamcrest.Matchers.containsString("SARAH MARTIN")))
                .andExpect(jsonPath("$.inputTruncated").value(false))
                .andExpect(jsonPath("$.outputTruncated").value(false));
    }

    @Test
    void contentApresEchecRenvoieOutputNull() throws Exception {
        MockMultipartFile fichier = fichierJson("""
                { "prenom": "sarah", "date_naissance": "14/03/1995" }
                """);

        String reponse = mockMvc.perform(multipart("/api/executions")
                        .file(fichier)
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire))
                .andReturn().getResponse().getContentAsString();
        Number executionId = JsonPath.read(reponse, "$.id");

        mockMvc.perform(get("/api/executions/" + executionId.longValue() + "/content")
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input").exists())
                .andExpect(jsonPath("$.output").doesNotExist());
    }

    @Test
    void contentParUnAutreUtilisateurRenvoie404() throws Exception {
        String reponse = mockMvc.perform(multipart("/api/executions")
                        .file(fichierValide())
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire))
                .andReturn().getResponse().getContentAsString();
        Number executionId = JsonPath.read(reponse, "$.id");

        MockHttpSession sessionIntrus = new MockHttpSession();
        sessionIntrus.setAttribute("userId", autreUtilisateur.getId());

        mockMvc.perform(get("/api/executions/" + executionId.longValue() + "/content")
                        .session(sessionIntrus))
                .andExpect(status().isNotFound());
    }

    @Test
    void contentTronqueAuDelaDe100Ko() throws Exception {
        // Un champ non utilise par les regles reste tel quel dans l'INPUT
        // sauvegarde : suffisant pour depasser MAX_CONTENT_BYTES (100 Ko) et
        // declencher la troncature sans faire echouer les regles existantes.
        String grosChamp = "x".repeat(200 * 1024);

        mockMvc.perform(multipart("/api/executions")
                        .file(new MockMultipartFile("file", "gros.json", "application/json",
                                ("{ \"prenom\": \"sarah\", \"nom\": \"martin\", "
                                        + "\"date_naissance\": \"14/03/1995\", "
                                        + "\"filler\": \"" + grosChamp + "\" }")
                                        .getBytes(StandardCharsets.UTF_8)))
                        .param("processId", process.getId().toString())
                        .session(sessionProprietaire))
                .andExpect(status().isOk());

        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/executions/" + executionId + "/content")
                        .session(sessionProprietaire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputTruncated").value(true));
    }
}