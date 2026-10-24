package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.entity.DataProcess;
import io.github.radhwent.datatransformer.entity.FileFormat;
import io.github.radhwent.datatransformer.entity.ProcessRule;
import io.github.radhwent.datatransformer.entity.Rule;
import io.github.radhwent.datatransformer.entity.RuleType;
import io.github.radhwent.datatransformer.entity.User;
import io.github.radhwent.datatransformer.repository.DataProcessRepository;
import io.github.radhwent.datatransformer.repository.ExecutionRepository;
import io.github.radhwent.datatransformer.repository.ProcessRuleRepository;
import io.github.radhwent.datatransformer.repository.RuleRepository;
import io.github.radhwent.datatransformer.repository.UserRepository;
import io.github.radhwent.datatransformer.service.ConflictException;
import io.github.radhwent.datatransformer.service.UnauthorizedException;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/processes")
public class ProcessController {

    private final DataProcessRepository processRepository;
    private final ProcessRuleRepository processRuleRepository;
    private final RuleRepository ruleRepository;
    private final UserRepository userRepository;
    private final ExecutionRepository executionRepository;

    public ProcessController(DataProcessRepository processRepository,
                             ProcessRuleRepository processRuleRepository,
                             RuleRepository ruleRepository,
                             UserRepository userRepository,
                             ExecutionRepository executionRepository) {
        this.processRepository = processRepository;
        this.processRuleRepository = processRuleRepository;
        this.ruleRepository = ruleRepository;
        this.userRepository = userRepository;
        this.executionRepository = executionRepository;
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<List<ProcessResponse>> getAll(HttpSession session) {
        Long userId = currentUserId(session);
        List<ProcessResponse> processes = processRepository.findByUserIdAndActiveTrue(userId)
                .stream()
                .map(p -> ProcessResponse.from(
                        p,
                        processRuleRepository.countByProcessId(p.getId()),
                        executionRepository.findFirstByProcessIdOrderByStartedAtDesc(p.getId()).orElse(null)))
                .toList();
        return ResponseEntity.ok(processes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessDetailResponse> getById(@PathVariable Long id, HttpSession session) {
        DataProcess process = requireOwnedProcess(id, currentUserId(session));
        List<ProcessRule> rules = processRuleRepository.findByProcessIdOrderByExecutionOrderAsc(id);
        return ResponseEntity.ok(ProcessDetailResponse.from(process, rules));
    }

    // ------------------------------------------------------------------
    // Creation
    // ------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<ProcessResponse> create(@Valid @RequestBody CreateProcessRequest request,
                                                  HttpSession session) {
        Long userId = currentUserId(session);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User introuvable : " + userId));

        if (processRepository.findByUserIdAndNameAndActiveTrue(userId, request.name()).isPresent()) {
            throw new ConflictException("Un traitement nomme '" + request.name() + "' existe deja");
        }

        DataProcess process = new DataProcess();
        process.setName(request.name());
        process.setDescription(request.description());
        process.setInputFormat(parseFormat(request.inputFormat()));
        process.setOutputFormat(parseFormat(request.outputFormat()));
        process.setUser(user);
        process = processRepository.save(process);

        return ResponseEntity.status(HttpStatus.CREATED).body(ProcessResponse.from(process));
    }

    @PostMapping("/{id}/rules")
    public ResponseEntity<ProcessRuleResponse> addRule(@PathVariable Long id,
                                                       @Valid @RequestBody CreateProcessRuleRequest request,
                                                       HttpSession session) {
        DataProcess process = requireOwnedProcess(id, currentUserId(session));

        Rule rule = ruleRepository.findById(request.ruleId())
                .orElseThrow(() -> new IllegalArgumentException("Regle introuvable : " + request.ruleId()));

        validateFieldsForRuleType(rule.getType(), request.sourceField2(), request.targetField());

        if (processRuleRepository.findByProcessIdAndExecutionOrder(id, request.executionOrder()).isPresent()) {
            throw new ConflictException(
                    "L'ordre d'execution " + request.executionOrder() + " est deja utilise pour ce traitement");
        }

        ProcessRule processRule = new ProcessRule();
        processRule.setProcess(process);
        processRule.setRule(rule);
        processRule.setExecutionOrder(request.executionOrder());
        processRule.setSourceField(request.sourceField());
        processRule.setSourceField2(request.sourceField2());
        processRule.setTargetField(request.targetField());
        processRule = processRuleRepository.save(processRule);

        return ResponseEntity.status(HttpStatus.CREATED).body(ProcessRuleResponse.from(processRule));
    }

    // ------------------------------------------------------------------
    // Modification
    // ------------------------------------------------------------------

    @PutMapping("/{id}")
    public ResponseEntity<ProcessResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateProcessRequest request,
                                                  HttpSession session) {
        Long userId = currentUserId(session);
        DataProcess process = requireOwnedProcess(id, userId);

        // Le nom doit rester unique par utilisateur, sauf s'il ne change pas
        processRepository.findByUserIdAndNameAndActiveTrue(userId, request.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Un traitement nomme '" + request.name() + "' existe deja");
                });

        process.setName(request.name());
        process.setDescription(request.description());
        process.setInputFormat(parseFormat(request.inputFormat()));
        process.setOutputFormat(parseFormat(request.outputFormat()));
        process = processRepository.save(process);

        return ResponseEntity.ok(ProcessResponse.from(process));
    }

    /**
     * @Transactional : sans ca, requireRuleOfProcess() et le save() plus bas
     * s'executent dans deux sessions Hibernate distinctes, et remplacer la
     * regle detachee declenche "Could not initialize proxy [Rule] - no session".
     */
    @PutMapping("/{processId}/rules/{ruleId}")
    @Transactional
    public ResponseEntity<ProcessRuleResponse> updateRule(@PathVariable Long processId,
                                                          @PathVariable Long ruleId,
                                                          @Valid @RequestBody CreateProcessRuleRequest request,
                                                          HttpSession session) {
        requireOwnedProcess(processId, currentUserId(session));
        ProcessRule processRule = requireRuleOfProcess(ruleId, processId);

        Rule rule = ruleRepository.findById(request.ruleId())
                .orElseThrow(() -> new IllegalArgumentException("Regle introuvable : " + request.ruleId()));

        validateFieldsForRuleType(rule.getType(), request.sourceField2(), request.targetField());

        // L'ordre doit rester unique, sauf s'il ne change pas
        processRuleRepository.findByProcessIdAndExecutionOrder(processId, request.executionOrder())
                .filter(existing -> !existing.getId().equals(ruleId))
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "L'ordre d'execution " + request.executionOrder() + " est deja utilise");
                });

        processRule.setRule(rule);
        processRule.setExecutionOrder(request.executionOrder());
        processRule.setSourceField(request.sourceField());
        processRule.setSourceField2(request.sourceField2());
        processRule.setTargetField(request.targetField());
        processRule = processRuleRepository.save(processRule);

        return ResponseEntity.ok(ProcessRuleResponse.from(processRule));
    }

    // ------------------------------------------------------------------
    // Suppression
    // ------------------------------------------------------------------

    /** Suppression logique : l'historique des executions doit rester coherent. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpSession session) {
        DataProcess process = requireOwnedProcess(id, currentUserId(session));

        process.setActive(false);
        processRepository.save(process);

        return ResponseEntity.noContent().build();
    }

    /** Suppression physique : une regle parametree n'a pas d'historique propre. */
    @DeleteMapping("/{processId}/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long processId,
                                           @PathVariable Long ruleId,
                                           HttpSession session) {
        requireOwnedProcess(processId, currentUserId(session));
        ProcessRule processRule = requireRuleOfProcess(ruleId, processId);

        processRuleRepository.delete(processRule);

        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    /**
     * userId vient de la session (posee par AuthController.login), jamais du
     * client -> evite qu'un utilisateur agisse au nom d'un autre.
     */
    private Long currentUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            throw new UnauthorizedException("Vous devez etre connecte");
        }
        return (Long) userId;
    }

    /**
     * Renvoie le traitement s'il existe, est actif, et appartient a l'utilisateur.
     * Le meme message dans les trois cas : ne pas confirmer l'existence d'un id
     * a quelqu'un qui n'y a pas droit.
     */
    private DataProcess requireOwnedProcess(Long processId, Long userId) {
        DataProcess process = processRepository.findByIdAndActiveTrue(processId)
                .orElseThrow(() -> new IllegalArgumentException("Traitement introuvable : " + processId));

        if (!process.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Traitement introuvable : " + processId);
        }

        return process;
    }

    /** Empeche d'agir sur la regle d'un autre traitement en devinant son id. */
    private ProcessRule requireRuleOfProcess(Long ruleId, Long processId) {
        ProcessRule processRule = processRuleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Regle introuvable : " + ruleId));

        if (!processRule.getProcess().getId().equals(processId)) {
            throw new IllegalArgumentException("Regle introuvable : " + ruleId);
        }

        return processRule;
    }

    private FileFormat parseFormat(String value) {
        try {
            return FileFormat.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Format invalide : attendu JSON ou XML");
        }
    }

    /**
     * CONCATENATE a besoin de sourceField2 ET targetField ; RENAME a besoin
     * de targetField seul ; UPPERCASE et FORMAT_DATE n'ont besoin que de
     * sourceField (deja garanti non vide par @NotBlank sur le DTO).
     */
    private void validateFieldsForRuleType(RuleType type, String sourceField2, String targetField) {
        switch (type) {
            case CONCATENATE -> {
                if (sourceField2 == null || sourceField2.isBlank()) {
                    throw new IllegalStateException("sourceField2 est obligatoire pour la regle CONCATENATE");
                }
                if (targetField == null || targetField.isBlank()) {
                    throw new IllegalStateException("targetField est obligatoire pour la regle CONCATENATE");
                }
            }
            case RENAME -> {
                if (targetField == null || targetField.isBlank()) {
                    throw new IllegalStateException("targetField est obligatoire pour la regle RENAME");
                }
            }
            case UPPERCASE, FORMAT_DATE -> {
                // seul sourceField est requis, deja valide par le DTO
            }
        }
    }
}