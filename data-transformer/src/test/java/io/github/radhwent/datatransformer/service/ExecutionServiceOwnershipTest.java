package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.entity.DataProcess;
import io.github.radhwent.datatransformer.entity.User;
import io.github.radhwent.datatransformer.repository.DataFileRepository;
import io.github.radhwent.datatransformer.repository.DataProcessRepository;
import io.github.radhwent.datatransformer.repository.ExecutionRepository;
import io.github.radhwent.datatransformer.repository.ProcessRuleRepository;
import io.github.radhwent.datatransformer.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifie le correctif de la faille IDOR : executer un processId qui
 * n'appartient pas au userId fourni doit echouer avant tout effet de bord
 * (aucune Execution creee, aucun fichier ecrit).
 */
@ExtendWith(MockitoExtension.class)
class ExecutionServiceOwnershipTest {

    @Mock
    private ExecutionRepository executionRepository;
    @Mock
    private DataProcessRepository processRepository;
    @Mock
    private ProcessRuleRepository processRuleRepository;
    @Mock
    private DataFileRepository dataFileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RuleChainRunner ruleChainRunner;
    @Mock
    private FileStorageService fileStorageService;

    @Test
    void executerLeTraitementDeQuelquUnDautreLeveUneException() throws Exception {
        ExecutionService executionService = new ExecutionService(
                executionRepository, processRepository, processRuleRepository,
                dataFileRepository, userRepository, ruleChainRunner, fileStorageService);

        User owner = new User();
        owner.setId(1L);
        User intrus = new User();
        intrus.setId(2L);

        DataProcess process = new DataProcess();
        process.setId(10L);
        process.setUser(owner);

        when(processRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(process));
        when(userRepository.findById(2L)).thenReturn(Optional.of(intrus));

        MockMultipartFile file = new MockMultipartFile("file", "clients.json",
                "application/json", "{}".getBytes());

        assertThrows(IllegalArgumentException.class, () ->
                executionService.execute(10L, 2L, file));

        verify(executionRepository, never()).save(any());
        verify(fileStorageService, never()).saveInputFile(any());
    }

        @Test
    void executerUnTraitementDesactiveLeveUneException() {
        ExecutionService executionService = new ExecutionService(
                executionRepository, processRepository, processRuleRepository,
                dataFileRepository, userRepository, ruleChainRunner, fileStorageService);

        // Un traitement desactive n'est pas renvoye par findByIdAndActiveTrue
        when(processRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "clients.json",
                "application/json", "{}".getBytes());

        assertThrows(IllegalArgumentException.class, () ->
                executionService.execute(10L, 1L, file));

        verify(executionRepository, never()).save(any());
    }
}
