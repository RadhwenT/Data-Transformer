package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.entity.ProcessRule;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Point d'entree unique pour appliquer une chaine de regles : utilise a la
 * fois par l'execution reelle (ExecutionService) et par le preview
 * (PreviewService), pour garantir que les deux produisent exactement le
 * meme resultat (un seul moteur, pas deux implementations qui divergent).
 */
@Service
public class RuleChainRunner {

    private final TransformationEngine transformationEngine;

    public RuleChainRunner(TransformationEngine transformationEngine) {
        this.transformationEngine = transformationEngine;
    }

    /**
     * Applique les regles a chaque enregistrement, dans l'ordre.
     * Strategie "tout ou rien" : la premiere erreur interrompt le traitement,
     * en precisant quel enregistrement a echoue (choix documente).
     */
    public List<Map<String, Object>> applyAll(List<Map<String, Object>> records, List<ProcessRule> rules) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> record = records.get(i);

            try {
                for (ProcessRule processRule : rules) {
                    record = transformationEngine.applyRule(
                            record,
                            processRule.getRule().getType(),
                            processRule.getSourceField(),
                            processRule.getSourceField2(),
                            processRule.getTargetField());
                }
                result.add(record);

            } catch (Exception e) {
                String position = records.size() > 1
                        ? "Enregistrement " + (i + 1) + "/" + records.size() + " : "
                        : "";
                throw new IllegalArgumentException(position + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Applique la chaine a UN enregistrement en conservant l'etat apres
     * chaque regle reussie : utilise par le preview pour tracer la
     * transformation etape par etape. S'arrete a la premiere regle en echec
     * et leve RuleChainTraceException, qui transporte les etapes reussies
     * jusque-la (sans le prefixe "Enregistrement i/n" : ce prefixe est
     * specifique a applyAll(), pas a cette methode).
     */
    public List<Map<String, Object>> applyWithTrace(Map<String, Object> record, List<ProcessRule> rules) {
        List<Map<String, Object>> trace = new ArrayList<>();
        Map<String, Object> current = record;

        for (ProcessRule processRule : rules) {
            try {
                current = transformationEngine.applyRule(
                        current,
                        processRule.getRule().getType(),
                        processRule.getSourceField(),
                        processRule.getSourceField2(),
                        processRule.getTargetField());
            } catch (Exception e) {
                throw new RuleChainTraceException(e.getMessage(), trace);
            }
            trace.add(current);
        }

        return trace;
    }
}
