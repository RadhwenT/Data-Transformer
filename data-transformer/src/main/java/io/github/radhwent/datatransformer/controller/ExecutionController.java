package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.entity.DataFile;
import io.github.radhwent.datatransformer.entity.DataProcess;
import io.github.radhwent.datatransformer.entity.Execution;
import io.github.radhwent.datatransformer.entity.FileType;
import io.github.radhwent.datatransformer.entity.ProcessRule;
import io.github.radhwent.datatransformer.repository.DataFileRepository;
import io.github.radhwent.datatransformer.repository.DataProcessRepository;
import io.github.radhwent.datatransformer.repository.ExecutionRepository;
import io.github.radhwent.datatransformer.repository.ProcessRuleRepository;
import io.github.radhwent.datatransformer.service.ExecutionService;
import io.github.radhwent.datatransformer.service.FileStorageService;
import io.github.radhwent.datatransformer.service.UnauthorizedException;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private static final int MAX_CONTENT_BYTES = 100 * 1024;

    private final ExecutionService executionService;
    private final ExecutionRepository executionRepository;
    private final DataFileRepository dataFileRepository;
    private final DataProcessRepository processRepository;
    private final ProcessRuleRepository processRuleRepository;
    private final FileStorageService fileStorageService;

    public ExecutionController(ExecutionService executionService,
                               ExecutionRepository executionRepository,
                               DataFileRepository dataFileRepository,
                               DataProcessRepository processRepository,
                               ProcessRuleRepository processRuleRepository,
                               FileStorageService fileStorageService) {
        this.executionService = executionService;
        this.executionRepository = executionRepository;
        this.dataFileRepository = dataFileRepository;
        this.processRepository = processRepository;
        this.processRuleRepository = processRuleRepository;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ExecutionResponse> execute(
            @RequestParam("processId") Long processId,
            @RequestParam("file") MultipartFile file,
            HttpSession session) {

        Long userId = currentUserId(session);
        Execution execution = executionService.execute(processId, userId, file);
        return ResponseEntity.ok(ExecutionResponse.from(execution));
    }

    @GetMapping
    public ResponseEntity<List<ExecutionResponse>> getAll(
            @RequestParam(value = "processId", required = false) Long processId,
            HttpSession session) {
        Long userId = currentUserId(session);

        List<Execution> executions;
        if (processId != null) {
            requireOwnedProcess(processId, userId);
            executions = executionRepository.findByUserIdAndProcessIdOrderByStartedAtDesc(userId, processId);
        } else {
            executions = executionRepository.findByUserIdOrderByStartedAtDesc(userId);
        }

        return ResponseEntity.ok(executions.stream().map(ExecutionResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExecutionDetailResponse> getById(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        Execution execution = requireOwnedExecution(id, userId);

        List<DataFile> files = dataFileRepository.findByExecutionId(id);
        List<ProcessRule> rules = processRuleRepository
                .findByProcessIdOrderByExecutionOrderAsc(execution.getProcess().getId());

        return ResponseEntity.ok(ExecutionDetailResponse.from(execution, files, rules));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<ExecutionContentResponse> content(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        requireOwnedExecution(id, userId);

        List<DataFile> files = dataFileRepository.findByExecutionId(id);
        DataFile inputFile = files.stream().filter(f -> f.getType() == FileType.INPUT).findFirst().orElse(null);
        DataFile outputFile = files.stream().filter(f -> f.getType() == FileType.OUTPUT).findFirst().orElse(null);

        TruncatedContent input = readTruncated(inputFile);
        TruncatedContent output = readTruncated(outputFile);

        return ResponseEntity.ok(new ExecutionContentResponse(
                input.text(), output.text(), input.truncated(), output.truncated()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        requireOwnedExecution(id, userId);

        return downloadFile(executionService.findOutputFile(id));
    }

    /**
     * Contrairement a /download (sortie, exige un SUCCESS), le fichier
     * d'entree est disponible quel que soit le statut de l'execution.
     */
    @GetMapping("/{id}/download-input")
    public ResponseEntity<byte[]> downloadInput(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        requireOwnedExecution(id, userId);

        return downloadFile(executionService.findInputFile(id));
    }

    private ResponseEntity<byte[]> downloadFile(DataFile file) {
        byte[] content;
        try {
            content = fileStorageService.readFileContent(file.getPath());
        } catch (IOException e) {
            throw new IllegalStateException("Fichier introuvable sur le disque : " + file.getPath());
        }

        // Le nom porte deja la bonne extension (voir ExecutionService.outputFileName
        // / le nom original uploade pour l'entree) : s'y fier evite de re-derailler
        // le Content-Type d'un fichier de sortie XML en le renvoyant en JSON.
        MediaType contentType = file.getName().toLowerCase().endsWith(".xml")
                ? MediaType.APPLICATION_XML
                : MediaType.APPLICATION_JSON;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .contentType(contentType)
                .body(content);
    }

    /**
     * userId vient desormais de la session (posee par AuthController.login),
     * plus jamais du client -> ferme la faille qui permettait d'executer le
     * traitement de n'importe quel utilisateur en passant son id en parametre.
     */
    private Long currentUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            throw new UnauthorizedException("Vous devez etre connecte");
        }
        return (Long) userId;
    }

    /**
     * Meme logique partout : ne pas distinguer "n'existe pas" de "appartient
     * a quelqu'un d'autre", pour ne pas confirmer l'id a un intrus.
     */
    private Execution requireOwnedExecution(Long id, Long userId) {
        Execution execution = executionRepository.findByIdWithProcess(id)
                .orElseThrow(() -> new IllegalArgumentException("Execution introuvable : " + id));

        if (!execution.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Execution introuvable : " + id);
        }
        return execution;
    }

    private void requireOwnedProcess(Long processId, Long userId) {
        DataProcess process = processRepository.findByIdAndActiveTrue(processId)
                .orElseThrow(() -> new IllegalArgumentException("DataProcess introuvable : " + processId));

        if (!process.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("DataProcess introuvable : " + processId);
        }
    }

    private TruncatedContent readTruncated(DataFile file) {
        if (file == null) {
            return new TruncatedContent(null, false);
        }

        byte[] content;
        try {
            content = fileStorageService.readFileContent(file.getPath());
        } catch (IOException e) {
            throw new IllegalStateException("Fichier introuvable sur le disque : " + file.getPath());
        }

        boolean truncated = content.length > MAX_CONTENT_BYTES;
        byte[] slice = truncated ? Arrays.copyOf(content, MAX_CONTENT_BYTES) : content;
        return new TruncatedContent(new String(slice, StandardCharsets.UTF_8), truncated);
    }

    private record TruncatedContent(String text, boolean truncated) {
    }
}
