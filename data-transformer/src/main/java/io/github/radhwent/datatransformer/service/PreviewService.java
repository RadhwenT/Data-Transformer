package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.controller.PreviewResponse;
import io.github.radhwent.datatransformer.controller.PreviewStep;
import io.github.radhwent.datatransformer.entity.DataProcess;
import io.github.radhwent.datatransformer.entity.ProcessRule;
import io.github.radhwent.datatransformer.repository.DataProcessRepository;
import io.github.radhwent.datatransformer.repository.ProcessRuleRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dry-run : rejoue la meme chaine de regles que ExecutionService (via
 * RuleChainRunner) mais ne persiste rien (aucune Execution, aucun DataFile,
 * aucune ecriture sur disque). Sert a detecter un nom de champ mal saisi
 * avant de lancer une vraie execution.
 */
@Service
public class PreviewService {

    private static final int MAX_PREVIEWED_RECORDS = 3;

    private final DataProcessRepository processRepository;
    private final ProcessRuleRepository processRuleRepository;
    private final FileStorageService fileStorageService;
    private final RuleChainRunner ruleChainRunner;

    @Value("${app.preview.max-file-size}")
    private long maxFileSize;

    @Value("${app.preview.max-records}")
    private int maxRecords;

    public PreviewService(DataProcessRepository processRepository,
                          ProcessRuleRepository processRuleRepository,
                          FileStorageService fileStorageService,
                          RuleChainRunner ruleChainRunner) {
        this.processRepository = processRepository;
        this.processRuleRepository = processRuleRepository;
        this.fileStorageService = fileStorageService;
        this.ruleChainRunner = ruleChainRunner;
    }

    public PreviewResponse preview(Long processId, Long userId, MultipartFile file) {
        DataProcess process = processRepository.findByIdAndActiveTrue(processId)
                .orElseThrow(() -> new IllegalArgumentException("DataProcess introuvable : " + processId));

        // Meme logique que ExecutionService.execute() : ne pas distinguer
        // "n'existe pas" de "appartient a quelqu'un d'autre".
        if (!process.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("DataProcess introuvable : " + processId);
        }

        if (file == null || file.isEmpty()) {
            throw new InvalidFileFormatException("Le fichier d'entree est vide ou manquant");
        }

        if (file.getSize() > maxFileSize) {
            throw new InvalidFileFormatException(
                    "Fichier trop volumineux pour l'apercu (max " + (maxFileSize / 1024) + " Ko)");
        }

        List<Map<String, Object>> records;
        try {
            records = fileStorageService.readRecords(file, process.getInputFormat());
        } catch (InvalidFileFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidFileFormatException("Format invalide, " + process.getInputFormat() + " attendu");
        }

        if (records.isEmpty()) {
            throw new InvalidFileFormatException("Le fichier ne contient aucun enregistrement");
        }

        if (records.size() > maxRecords) {
            records = records.subList(0, maxRecords);
        }

        int recordCount = records.size();
        int previewedCount = Math.min(MAX_PREVIEWED_RECORDS, recordCount);
        boolean truncated = previewedCount < recordCount;
        List<Map<String, Object>> before = new ArrayList<>(records.subList(0, previewedCount));

        List<ProcessRule> rules = processRuleRepository.findByProcessIdOrderByExecutionOrderAsc(processId);

        if (rules.isEmpty()) {
            return new PreviewResponse("NO_RULES", process.getName(),
                    process.getInputFormat().name(), process.getOutputFormat().name(),
                    recordCount, previewedCount, truncated,
                    before, before, List.of(), null, null);
        }

        return runPreview(process, rules, before, recordCount, previewedCount, truncated);
    }

    /**
     * Rejoue la chaine sur chaque enregistrement previsualise, dans l'ordre.
     * Le premier qui echoue determine le statut ERROR de toute la reponse
     * (le panneau de trace detaillee ne montre que ce premier enregistrement
     * en echec, ou le premier de la liste si tout reussit).
     */
    private PreviewResponse runPreview(DataProcess process, List<ProcessRule> rules,
                                       List<Map<String, Object>> before,
                                       int recordCount, int previewedCount, boolean truncated) {

        List<Map<String, Object>> after = new ArrayList<>();

        List<Map<String, Object>> firstTrace;
        try {
            firstTrace = ruleChainRunner.applyWithTrace(before.get(0), rules);
        } catch (RuleChainTraceException e) {
            return buildErrorResponse(process, rules, e, 0, recordCount, previewedCount, truncated, before);
        }
        after.add(firstTrace.get(firstTrace.size() - 1));

        for (int i = 1; i < before.size(); i++) {
            try {
                List<Map<String, Object>> trace = ruleChainRunner.applyWithTrace(before.get(i), rules);
                after.add(trace.get(trace.size() - 1));
            } catch (RuleChainTraceException e) {
                return buildErrorResponse(process, rules, e, i, recordCount, previewedCount, truncated, before);
            }
        }

        List<PreviewStep> steps = new ArrayList<>();
        for (int j = 0; j < rules.size(); j++) {
            ProcessRule rule = rules.get(j);
            steps.add(new PreviewStep(rule.getExecutionOrder(), rule.getRule().getType().name(),
                    buildLabel(rule), firstTrace.get(j)));
        }

        return new PreviewResponse("OK", process.getName(),
                process.getInputFormat().name(), process.getOutputFormat().name(),
                recordCount, previewedCount, truncated,
                before, after, steps, null, null);
    }

    private PreviewResponse buildErrorResponse(DataProcess process, List<ProcessRule> rules,
                                               RuleChainTraceException e, int failingIndex,
                                               int recordCount, int previewedCount, boolean truncated,
                                               List<Map<String, Object>> before) {

        List<Map<String, Object>> partialTrace = e.getPartialTrace();
        ProcessRule failedRule = rules.get(partialTrace.size());

        List<PreviewStep> steps = new ArrayList<>();
        for (int j = 0; j < partialTrace.size(); j++) {
            ProcessRule rule = rules.get(j);
            steps.add(new PreviewStep(rule.getExecutionOrder(), rule.getRule().getType().name(),
                    buildLabel(rule), partialTrace.get(j)));
        }
        steps.add(new PreviewStep(failedRule.getExecutionOrder(), failedRule.getRule().getType().name(),
                buildLabel(failedRule), null));

        // Meme formule que RuleChainRunner.applyAll() : le message doit rester
        // identique a celui qu'un vrai run produirait sur ce fichier.
        String position = recordCount > 1
                ? "Enregistrement " + (failingIndex + 1) + "/" + recordCount + " : "
                : "";

        return new PreviewResponse("ERROR", process.getName(),
                process.getInputFormat().name(), process.getOutputFormat().name(),
                recordCount, previewedCount, truncated,
                before, null, steps, failedRule.getExecutionOrder(), position + e.getMessage());
    }

    /** Meme information que la colonne "fields" de process-detail.html, avec le detail du format pour FORMAT_DATE. */
    private String buildLabel(ProcessRule processRule) {
        String source = processRule.getSourceField();
        String source2 = processRule.getSourceField2();
        String target = processRule.getTargetField();

        return switch (processRule.getRule().getType()) {
            case CONCATENATE -> source + " + " + source2 + " → " + target;
            case RENAME -> source + " → " + target;
            case UPPERCASE -> source;
            case FORMAT_DATE -> source + " · dd/MM/yyyy → yyyy-MM-dd";
        };
    }
}
