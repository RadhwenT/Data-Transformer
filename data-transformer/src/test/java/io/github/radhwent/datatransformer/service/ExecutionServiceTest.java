package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.config.TestcontainersConfiguration;
import io.github.radhwent.datatransformer.entity.DataProcess;
import io.github.radhwent.datatransformer.entity.Execution;
import io.github.radhwent.datatransformer.entity.FileFormat;
import io.github.radhwent.datatransformer.entity.FileType;
import io.github.radhwent.datatransformer.entity.ProcessRule;
import io.github.radhwent.datatransformer.entity.Rule;
import io.github.radhwent.datatransformer.entity.RuleType;
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
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @BeforeEach cree ses propres fixtures (User, DataProcess + 3 regles) plutot
 * que de supposer que id=1 existe : DataLoader est desactive en test
 * (@Profile("!test")) et la base Testcontainers demarre vide a chaque run.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExecutionServiceTest {

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private DataFileRepository dataFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataProcessRepository processRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private ProcessRuleRepository processRuleRepository;

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
        process.setName("Nettoyage clients (test)");
        process.setInputFormat(FileFormat.JSON);
        process.setOutputFormat(FileFormat.JSON);
        process.setUser(user);
        process = processRepository.save(process);
        processId = process.getId();

        Rule concatenate = findOrCreateRule(RuleType.CONCATENATE, "Concatener deux valeurs");
        Rule uppercase = findOrCreateRule(RuleType.UPPERCASE, "Mettre en majuscules");
        Rule formatDate = findOrCreateRule(RuleType.FORMAT_DATE, "Reformater une date");

        saveProcessRule(process, concatenate, 1, "prenom", "nom", "nomComplet");
        saveProcessRule(process, uppercase, 2, "nomComplet", null, null);
        saveProcessRule(process, formatDate, 3, "date_naissance", null, null);
    }

    private Rule findOrCreateRule(RuleType type, String name) {
        return ruleRepository.findByType(type).orElseGet(() -> {
            Rule r = new Rule();
            r.setType(type);
            r.setName(name);
            r.setDescription(name);
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
        return new MockMultipartFile(
                "file",
                "clients.json",
                "application/json",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void scenarioNominal_produitUnSucces() {
        MockMultipartFile file = jsonFile("""
                {
                  "prenom": "sarah",
                  "nom": "martin",
                  "date_naissance": "14/03/1995"
                }
                """);

        Execution execution = executionService.execute(processId, userId, file);

        assertEquals(Status.SUCCESS, execution.getStatus());
        assertNull(execution.getErrorMessage());
        assertNotNull(execution.getFinishedAt());

        // Les deux fichiers doivent etre traces : INPUT et OUTPUT
        var files = dataFileRepository.findByExecutionId(execution.getId());
        assertEquals(2, files.size());
        assertTrue(files.stream().anyMatch(f -> f.getType() == FileType.INPUT));
        assertTrue(files.stream().anyMatch(f -> f.getType() == FileType.OUTPUT));
    }

    @Test
    void champManquant_produitUnEchecTraceEnBase() {
        // "nom" absent -> la regle CONCATENATE va echouer
        MockMultipartFile file = jsonFile("""
                {
                  "prenom": "sarah",
                  "date_naissance": "14/03/1995"
                }
                """);

        Execution execution = executionService.execute(processId, userId, file);

        assertEquals(Status.FAILED, execution.getStatus());
        assertNotNull(execution.getErrorMessage());
        assertTrue(execution.getErrorMessage().contains("nom"));

        // Le fichier INPUT doit exister malgre l'echec (cardinalite 1..*)
        var files = dataFileRepository.findByExecutionId(execution.getId());
        assertEquals(1, files.size());
        assertEquals(FileType.INPUT, files.get(0).getType());
    }

    @Test
    void dateAuMauvaisFormat_produitUnEchec() {
        MockMultipartFile file = jsonFile("""
                {
                  "prenom": "sarah",
                  "nom": "martin",
                  "date_naissance": "1995-03-14"
                }
                """);

        Execution execution = executionService.execute(processId, userId, file);

        assertEquals(Status.FAILED, execution.getStatus());
        assertTrue(execution.getErrorMessage().contains("date"));
    }

    @Test
    void fichierNonJson_neCreeAucuneLigneEnBase() {
        long avant = executionRepository.count();

        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain",
                "ceci n'est pas du json".getBytes(StandardCharsets.UTF_8));

        assertThrows(InvalidFileFormatException.class,
                () -> executionService.execute(processId, userId, file));

        // Aucune execution ne doit avoir ete creee
        assertEquals(avant, executionRepository.count());
    }

    @Test
    void traitementInexistant_leveUneErreur() {
        MockMultipartFile file = jsonFile("{\"prenom\": \"sarah\"}");

        assertThrows(IllegalArgumentException.class,
                () -> executionService.execute(999999L, userId, file));
    }
}
