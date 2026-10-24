package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.config.TestcontainersConfiguration;
import io.github.radhwent.datatransformer.controller.PreviewResponse;
import io.github.radhwent.datatransformer.entity.DataProcess;
import io.github.radhwent.datatransformer.entity.Execution;
import io.github.radhwent.datatransformer.entity.FileFormat;
import io.github.radhwent.datatransformer.entity.ProcessRule;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @BeforeEach cree ses propres fixtures, comme ExecutionServiceTest : la
 * base Testcontainers demarre vide a chaque run.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PreviewServiceTest {

    @Autowired private PreviewService previewService;
    @Autowired private ExecutionService executionService;
    @Autowired private ExecutionRepository executionRepository;
    @Autowired private DataFileRepository dataFileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DataProcessRepository processRepository;
    @Autowired private RuleRepository ruleRepository;
    @Autowired private ProcessRuleRepository processRuleRepository;
    @Autowired private FileStorageService fileStorageService;

    @Value("${app.storage.dir}")
    private String storageDir;

    private Long processId;
    private Long userId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail("test-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hash");
        user = userRepository.save(user);
        userId = user.getId();

        DataProcess process = new DataProcess();
        process.setName("Nettoyage clients (preview test)");
        process.setInputFormat(FileFormat.JSON);
        process.setOutputFormat(FileFormat.JSON);
        process.setUser(user);
        process = processRepository.save(process);
        processId = process.getId();

        Rule concatenate = findOrCreateRule(RuleType.CONCATENATE);
        Rule uppercase = findOrCreateRule(RuleType.UPPERCASE);
        Rule formatDate = findOrCreateRule(RuleType.FORMAT_DATE);

        saveProcessRule(process, concatenate, 1, "prenom", "nom", "nomComplet");
        saveProcessRule(process, uppercase, 2, "nomComplet", null, null);
        saveProcessRule(process, formatDate, 3, "date_naissance", null, null);
    }

    private Rule findOrCreateRule(RuleType type) {
        return ruleRepository.findByType(type).orElseGet(() -> {
            Rule r = new Rule();
            r.setType(type);
            r.setName(type.name());
            r.setDescription(type.name());
            return ruleRepository.save(r);
        });
    }

    private void saveProcessRule(DataProcess process, Rule rule, int order,
                                  String sourceField, String sourceField2, String targetField) {
        ProcessRule pr = new ProcessRule();
        pr.setProcess(process);
        pr.setRule(rule);
        pr.setExecutionOrder(order);
        pr.setSourceField(sourceField);
        pr.setSourceField2(sourceField2);
        pr.setTargetField(targetField);
        processRuleRepository.save(pr);
    }

    private MockMultipartFile jsonFile(String content) {
        return new MockMultipartFile("file", "clients.json", "application/json",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile fichierValide() {
        return jsonFile("""
                { "prenom": "sarah", "nom": "martin", "date_naissance": "14/03/1995" }
                """);
    }

    @Test
    void scenarioNominal_produitUnApercuOk() {
        PreviewResponse response = previewService.preview(processId, userId, fichierValide());

        assertEquals("OK", response.status());
        assertEquals(1, response.recordCount());
        assertEquals(1, response.previewedCount());
        assertFalse(response.truncated());
        assertEquals("SARAH MARTIN", response.after().get(0).get("nomComplet"));
        assertEquals("1995-03-14", response.after().get(0).get("date_naissance"));
        assertEquals(3, response.steps().size());
        assertNull(response.failedRuleOrder());
        assertNull(response.errorMessage());
    }

    @Test
    void tableauJson_previsualiseTousLesEnregistrements() {
        MockMultipartFile fichier = jsonFile("""
                [
                  { "prenom": "sarah", "nom": "martin", "date_naissance": "14/03/1995" },
                  { "prenom": "adam", "nom": "durand", "date_naissance": "01/01/2000" }
                ]
                """);

        PreviewResponse response = previewService.preview(processId, userId, fichier);

        assertEquals("OK", response.status());
        assertEquals(2, response.recordCount());
        assertEquals(2, response.previewedCount());
        assertFalse(response.truncated());
        assertEquals("SARAH MARTIN", response.after().get(0).get("nomComplet"));
        assertEquals("ADAM DURAND", response.after().get(1).get("nomComplet"));
    }

    @Test
    void xml_estPriseEnCharge() {
        User user = userRepository.findById(userId).orElseThrow();
        DataProcess processXml = new DataProcess();
        processXml.setName("Nettoyage clients XML");
        processXml.setInputFormat(FileFormat.XML);
        processXml.setOutputFormat(FileFormat.JSON);
        processXml.setUser(user);
        processXml = processRepository.save(processXml);

        Rule concatenate = findOrCreateRule(RuleType.CONCATENATE);
        Rule uppercase = findOrCreateRule(RuleType.UPPERCASE);
        Rule formatDate = findOrCreateRule(RuleType.FORMAT_DATE);
        saveProcessRule(processXml, concatenate, 1, "prenom", "nom", "nomComplet");
        saveProcessRule(processXml, uppercase, 2, "nomComplet", null, null);
        saveProcessRule(processXml, formatDate, 3, "date_naissance", null, null);

        MockMultipartFile fichier = new MockMultipartFile("file", "client.xml", "application/xml",
                ("<client><prenom>sarah</prenom><nom>martin</nom>"
                        + "<date_naissance>14/03/1995</date_naissance></client>")
                        .getBytes(StandardCharsets.UTF_8));

        PreviewResponse response = previewService.preview(processXml.getId(), userId, fichier);

        assertEquals("OK", response.status());
        assertEquals("SARAH MARTIN", response.after().get(0).get("nomComplet"));
    }

    @Test
    void champManquant_produitStatusErrorEtFailedRuleOrder() {
        MockMultipartFile fichier = jsonFile("""
                { "prenom": "sarah", "date_naissance": "14/03/1995" }
                """);

        PreviewResponse response = previewService.preview(processId, userId, fichier);

        assertEquals("ERROR", response.status());
        assertEquals(1, response.failedRuleOrder()); // regle CONCATENATE, ordre 1
        assertNull(response.after());
        assertNotNull(response.errorMessage());
        assertTrue(response.errorMessage().contains("nom"));

        // Les etapes reussies avant l'echec ne sont pas tracees ici (echec des
        // la 1ere regle) mais la regle fautive doit apparaitre, sans record.
        assertEquals(1, response.steps().size());
        assertNull(response.steps().get(0).record());
    }

    @Test
    void traitementSansRegle_produitNoRulesEtApresEgalAvant() {
        // Nouveau traitement, sans aucune regle associee.
        User user = userRepository.findById(userId).orElseThrow();
        DataProcess sansRegle = new DataProcess();
        sansRegle.setName("Traitement sans regle");
        sansRegle.setInputFormat(FileFormat.JSON);
        sansRegle.setOutputFormat(FileFormat.JSON);
        sansRegle.setUser(user);
        sansRegle = processRepository.save(sansRegle);

        PreviewResponse response = previewService.preview(sansRegle.getId(), userId, fichierValide());

        assertEquals("NO_RULES", response.status());
        assertEquals(response.before(), response.after());
        assertTrue(response.steps().isEmpty());
    }

    @Test
    void fichierCorrompu_leveInvalidFileFormatException() {
        MockMultipartFile fichier = new MockMultipartFile("file", "notes.txt", "text/plain",
                "ceci n'est pas du json".getBytes(StandardCharsets.UTF_8));

        assertThrows(InvalidFileFormatException.class,
                () -> previewService.preview(processId, userId, fichier));
    }

    @Test
    void fichierTropVolumineux_leveInvalidFileFormatException() {
        byte[] contenu = new byte[2 * 1024 * 1024];
        MockMultipartFile fichier = new MockMultipartFile("file", "gros.json", "application/json", contenu);

        assertThrows(InvalidFileFormatException.class,
                () -> previewService.preview(processId, userId, fichier));
    }

    @Test
    void traitementDUnAutreUtilisateur_leveUneErreur() {
        assertThrows(IllegalArgumentException.class,
                () -> previewService.preview(processId, 999999L, fichierValide()));
    }

    /**
     * Test anti-regression capital (D2) : le preview ne doit jamais persister
     * quoi que ce soit, ni ecrire de fichier sur le disque de stockage.
     */
    @Test
    void previewNePersisteRienEtNecritAucunFichier() {
        long executionsAvant = executionRepository.count();
        long fichiersAvant = dataFileRepository.count();
        File dir = new File(storageDir);
        int fichiersSurDisqueAvant = dir.exists() ? countFiles(dir) : 0;

        previewService.preview(processId, userId, fichierValide());

        assertEquals(executionsAvant, executionRepository.count());
        assertEquals(fichiersAvant, dataFileRepository.count());
        int fichiersSurDisqueApres = dir.exists() ? countFiles(dir) : 0;
        assertEquals(fichiersSurDisqueAvant, fichiersSurDisqueApres);
    }

    private int countFiles(File dir) {
        File[] files = dir.listFiles();
        return files == null ? 0 : files.length;
    }

    /**
     * Test d'equivalence (D1) : pour un meme fichier et un meme traitement,
     * le "after" du preview doit etre egal a la sortie ecrite par le vrai
     * execute() -- garantie que les deux partagent le meme RuleChainRunner.
     */
    @Test
    void equivalencePreviewEtExecute() throws Exception {
        PreviewResponse preview = previewService.preview(processId, userId, fichierValide());

        Execution execution = executionService.execute(processId, userId, fichierValide());
        assertEquals(io.github.radhwent.datatransformer.entity.Status.SUCCESS, execution.getStatus());

        var outputFile = executionService.findOutputFile(execution.getId());
        byte[] contenu = fileStorageService.readFileContent(outputFile.getPath());

        @SuppressWarnings("unchecked")
        var reel = new tools.jackson.databind.ObjectMapper()
                .readValue(contenu, java.util.Map.class);

        assertEquals(reel.get("nomComplet"), preview.after().get(0).get("nomComplet"));
        assertEquals(reel.get("date_naissance"), preview.after().get(0).get("date_naissance"));
    }
}
