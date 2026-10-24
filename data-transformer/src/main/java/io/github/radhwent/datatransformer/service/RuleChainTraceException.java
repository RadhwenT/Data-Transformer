package io.github.radhwent.datatransformer.service;

import java.util.List;
import java.util.Map;

/**
 * Levee par RuleChainRunner.applyWithTrace() quand une regle echoue :
 * transporte l'etat de l'enregistrement apres chaque regle reussie, pour que
 * l'appelant (PreviewService) sache exactement ou la chaine s'est arretee.
 */
public class RuleChainTraceException extends RuntimeException {

    private final List<Map<String, Object>> partialTrace;

    public RuleChainTraceException(String message, List<Map<String, Object>> partialTrace) {
        super(message);
        this.partialTrace = partialTrace;
    }

    public List<Map<String, Object>> getPartialTrace() {
        return partialTrace;
    }
}
