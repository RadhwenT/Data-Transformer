package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.entity.RuleType;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Moteur des 4 regles de transformation du catalogue (CONCATENATE, UPPERCASE,
 * FORMAT_DATE, RENAME). Chaque methode privee est pure : elle recoit
 * l'enregistrement courant et renvoie une COPIE modifiee, jamais l'original
 * (LinkedHashMap pour preserver l'ordre des cles a l'affichage). Un champ
 * source absent ou une date mal formee leve IllegalArgumentException, qui
 * remonte tel quel jusqu'a RuleChainRunner pour construire le message
 * "Enregistrement i/n : ..." (tout-ou-rien, voir ExecutionService).
 */
@Service
public class TransformationEngine {

    private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter OUTPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Map<String, Object> applyRule(
            Map<String, Object> data,
            RuleType type,
            String sourceField,
            String sourceField2,
            String targetField) {

        switch (type) {
            case CONCATENATE:
                return concatenate(data, sourceField, sourceField2, targetField);
            case UPPERCASE:
                return uppercase(data, sourceField);
            case FORMAT_DATE:
                return formatDate(data, sourceField);
            case RENAME:
                return rename(data, sourceField, targetField);
            default:
                throw new IllegalArgumentException("Type de regle non supporte : " + type);
        }
    }

    /** Deplace la valeur : le champ source disparait du resultat, seul le champ cible reste. */
    private Map<String, Object> rename(Map<String, Object> data, String sourceField, String targetField) {
        Map<String, Object> result = new LinkedHashMap<>(data);

        if (!result.containsKey(sourceField)) {
            throw new IllegalArgumentException("Champ absent dans le fichier : " + sourceField);
        }

        Object value = result.remove(sourceField);
        result.put(targetField, value);
        return result;
    }

    /**
     * Joint les deux champs sources dans le champ cible, separes par un
     * espace. Regle produit (voir docs/scenario-nominal.md) : les deux champs
     * sources sont retires du resultat, pas seulement lus ; ce n'est pas
     * configurable pour le MVP.
     */
    private Map<String, Object> concatenate(Map<String, Object> data,
                                            String sourceField,
                                            String sourceField2,
                                            String targetField) {
        Map<String, Object> result = new LinkedHashMap<>(data);

        Object value1 = result.remove(sourceField);
        Object value2 = result.remove(sourceField2);

        if (value1 == null) {
            throw new IllegalArgumentException("Champ absent dans le fichier : " + sourceField);
        }
        if (value2 == null) {
            throw new IllegalArgumentException("Champ absent dans le fichier : " + sourceField2);
        }

        result.put(targetField, value1 + " " + value2);
        return result;
    }

    /** Transformation sur place : le champ garde son nom, seule sa valeur change. */
    private Map<String, Object> uppercase(Map<String, Object> data, String sourceField) {
        Map<String, Object> result = new LinkedHashMap<>(data);

        Object value = result.get(sourceField);
        if (value == null) {
            throw new IllegalArgumentException("Champ absent dans le fichier : " + sourceField);
        }

        result.put(sourceField, value.toString().toUpperCase());
        return result;
    }

    /**
     * Transformation sur place, format source et cible fixes pour le MVP
     * (JJ/MM/AAAA -> AAAA-MM-JJ, non configurable) — voir docs/scenario-nominal.md.
     */
    private Map<String, Object> formatDate(Map<String, Object> data, String sourceField) {
        Map<String, Object> result = new LinkedHashMap<>(data);

        Object value = result.get(sourceField);
        if (value == null) {
            throw new IllegalArgumentException("Champ absent dans le fichier : " + sourceField);
        }

        try {
            LocalDate date = LocalDate.parse(value.toString(), INPUT_DATE_FORMAT);
            result.put(sourceField, date.format(OUTPUT_DATE_FORMAT));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Format de date invalide pour le champ '" + sourceField
                            + "' : attendu JJ/MM/AAAA, recu '" + value + "'");
        }

        return result;
    }
}