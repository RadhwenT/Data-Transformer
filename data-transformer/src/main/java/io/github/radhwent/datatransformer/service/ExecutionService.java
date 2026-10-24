package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.entity.*;
import io.github.radhwent.datatransformer.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final DataProcessRepository processRepository;
    private final ProcessRuleRepository processRuleRepository;
    private final DataFileRepository dataFileRepository;
    private final UserRepository userRepository;
    private final RuleChainRunner ruleChainRunner;
    private final FileStorageService fileStorageService;

    public ExecutionService(ExecutionRepository executionRepository,
                            DataProcessRepository processRepository,
                            ProcessRuleRepository processRuleRepository,
                            DataFileRepository dataFileRepository,
                            UserRepository userRepository,
                            RuleChainRunner ruleChainRunner,
                            FileStorageService fileStorageService) {
        this.executionRepository = executionRepository;
        this.processRepository = processRepository;
        this.processRuleRepository = processRuleRepository;
        this.dataFileRepository = dataFileRepository;
        this.userRepository = userRepository;
        this.ruleChainRunner = ruleChainRunner;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public Execution execute(Long processId, Long userId, MultipartFile inputFile) {

        DataProcess process = processRepository.findByIdAndActiveTrue(processId)
                .orElseThrow(() -> new IllegalArgumentException("DataProcess introuvable : " + processId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User introuvable : " + userId));

        if (!process.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("DataProcess introuvable : " + processId);
        }

        if (inputFile == null || inputFile.isEmpty()) {
            throw new InvalidFileFormatException("Le fichier d'entree est vide ou manquant");
        }

        List<ProcessRule> rules = processRuleRepository.findByProcessIdOrderByExecutionOrderAsc(processId);
        if (rules.isEmpty()) {
            throw new IllegalStateException("Le traitement '" + process.getName() + "' n'a aucune regle configuree");
        }

        // Le fichier doit etre lisible AVANT de creer une ligne Execution :
        // si le format est invalide, aucune trace n'est enregistree en base.
        List<Map<String, Object>> records;
        try {
            records = fileStorageService.readRecords(inputFile, process.getInputFormat());
        } catch (InvalidFileFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidFileFormatException(
                    "Format invalide, " + process.getInputFormat() + " attendu");
        }

        if (records.isEmpty()) {
            throw new InvalidFileFormatException("Le fichier ne contient aucun enregistrement");
        }

        // --- Creation de l'execution : PENDING puis RUNNING ---
        Execution execution = new Execution();
        execution.setProcess(process);
        execution.setUser(user);
        execution.setStatus(Status.PENDING);
        execution = executionRepository.save(execution);

        execution.setStatus(Status.RUNNING);
        execution = executionRepository.save(execution);

        try {
            String inputPath = fileStorageService.saveInputFile(inputFile);

            DataFile inputDataFile = new DataFile();
            inputDataFile.setExecution(execution);
            inputDataFile.setName(safeFileName(inputFile.getOriginalFilename()));
            inputDataFile.setPath(inputPath);
            inputDataFile.setType(FileType.INPUT);
            dataFileRepository.save(inputDataFile);

            List<Map<String, Object>> transformed = ruleChainRunner.applyAll(records, rules);

            String outputPath = fileStorageService.writeOutputFile(
                    transformed, process.getOutputFormat(), inputFile.getOriginalFilename());

            DataFile outputDataFile = new DataFile();
            outputDataFile.setExecution(execution);
            outputDataFile.setName(outputFileName(inputFile.getOriginalFilename(), process.getOutputFormat()));
            outputDataFile.setPath(outputPath);
            outputDataFile.setType(FileType.OUTPUT);
            dataFileRepository.save(outputDataFile);

            execution.setStatus(Status.SUCCESS);
            execution.setFinishedAt(LocalDateTime.now());
            return executionRepository.save(execution);

        } catch (Exception e) {
            return markFailed(execution, e.getMessage());
        }
    }

    private Execution markFailed(Execution execution, String errorMessage) {
        execution.setStatus(Status.FAILED);
        execution.setErrorMessage(errorMessage);
        execution.setFinishedAt(LocalDateTime.now());
        return executionRepository.save(execution);
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "fichier";
        }
        return originalName;
    }

    /**
     * Le nom affiche/telecharge pour le fichier de sortie doit porter
     * l'extension du format de SORTIE, pas celle du fichier d'entree
     * uploade (ex. entree "clients.json" + sortie XML -> "output_clients.xml",
     * jamais "output_clients.json") : le contenu ecrit sur disque
     * (FileStorageService.writeOutputFile) suit deja cette regle, seul ce
     * nom affiche/telecharge trainait encore l'extension d'origine.
     */
    private String outputFileName(String originalName, FileFormat outputFormat) {
        String base = safeFileName(originalName);
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String extension = outputFormat == FileFormat.XML ? ".xml" : ".json";
        return "output_" + base + extension;
    }

    @Transactional(readOnly = true)
    public DataFile findOutputFile(Long executionId) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution introuvable : " + executionId));

        if (execution.getStatus() != Status.SUCCESS) {
            throw new ConflictException(
                    "Aucun fichier de sortie : l'execution est en statut " + execution.getStatus());
        }

        return dataFileRepository.findByExecutionId(executionId)
                .stream()
                .filter(f -> f.getType() == FileType.OUTPUT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fichier de sortie introuvable"));
    }

    /**
     * A la difference du fichier de sortie, le fichier d'entree est ecrit
     * avant l'application des regles (voir execute()) : il existe donc quel
     * que soit le statut final de l'execution, y compris FAILED.
     */
    @Transactional(readOnly = true)
    public DataFile findInputFile(Long executionId) {
        if (!executionRepository.existsById(executionId)) {
            throw new IllegalArgumentException("Execution introuvable : " + executionId);
        }

        return dataFileRepository.findByExecutionId(executionId)
                .stream()
                .filter(f -> f.getType() == FileType.INPUT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fichier d'entree introuvable"));
    }
}