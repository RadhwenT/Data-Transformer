package io.github.radhwent.datatransformer.controller;

import java.util.Map;

/**
 * Une etape de la trace de preview : l'etat de l'enregistrement apres
 * application de cette regle. record est null uniquement pour l'etape qui a
 * echoue (elle apparait dans la liste pour signaler quelle regle est en
 * cause, mais n'a pas produit de resultat).
 */
public record PreviewStep(
        int order,
        String ruleType,
        String label,
        Map<String, Object> record) {
}
