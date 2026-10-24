package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.config.TestcontainersConfiguration;
import io.github.radhwent.datatransformer.entity.Rule;
import io.github.radhwent.datatransformer.entity.RuleType;
import io.github.radhwent.datatransformer.repository.DataFileRepository;
import io.github.radhwent.datatransformer.repository.DataProcessRepository;
import io.github.radhwent.datatransformer.repository.ExecutionRepository;
import io.github.radhwent.datatransformer.repository.ProcessRuleRepository;
import io.github.radhwent.datatransformer.repository.RuleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Jusqu'ici seul endpoint de tout le backend sans aucun test, direct ou
 * indirect (les autres tests peuplent le catalogue via RuleRepository mais
 * n'appellent jamais GET /api/rules).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RuleControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RuleRepository ruleRepository;
    @Autowired private ProcessRuleRepository processRuleRepository;
    @Autowired private DataProcessRepository processRepository;
    @Autowired private ExecutionRepository executionRepository;
    @Autowired private DataFileRepository dataFileRepository;

    @BeforeEach
    void setUp() {
        // Ordre impose par les contraintes de cle etrangere, meme motif que
        // les autres tests de controller (voir AuthControllerTest).
        dataFileRepository.deleteAll();
        executionRepository.deleteAll();
        processRuleRepository.deleteAll();
        processRepository.deleteAll();
        ruleRepository.deleteAll();

        creerRegle(RuleType.CONCATENATE, "Concatener deux valeurs", "Joint deux champs");
        creerRegle(RuleType.UPPERCASE, "Mettre en majuscules", "Majuscules");
    }

    private void creerRegle(RuleType type, String name, String description) {
        Rule rule = new Rule();
        rule.setType(type);
        rule.setName(name);
        rule.setDescription(description);
        ruleRepository.save(rule);
    }

    @Test
    void getAllRenvoieLeCatalogueComplet() throws Exception {
        mockMvc.perform(get("/api/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.type == 'CONCATENATE')].name").value("Concatener deux valeurs"))
                .andExpect(jsonPath("$[?(@.type == 'UPPERCASE')].description").value("Majuscules"));
    }

    @Test
    void getAllNeRequiertPasDeSession() throws Exception {
        // Contrairement au reste de l'API : RuleController ne verifie aucune
        // session (catalogue en lecture seule, aucune donnee sensible).
        mockMvc.perform(get("/api/rules"))
                .andExpect(status().isOk());
    }
}
