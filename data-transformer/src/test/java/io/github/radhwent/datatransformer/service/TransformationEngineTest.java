package io.github.radhwent.datatransformer.service;
import io.github.radhwent.datatransformer.entity.RuleType;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;



class TransformationEngineTest {

    @Test
    void champManquantPourConcatenateLeveUneException() {
        TransformationEngine engine = new TransformationEngine();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("prenom", "sarah");
        // "nom" absent volontairement

        assertThrows(IllegalArgumentException.class, () ->
                engine.applyRule(data, RuleType.CONCATENATE, "prenom", "nom", "nomComplet"));
    }

    @Test
    void champManquantPourUppercaseLeveUneException() {
        TransformationEngine engine = new TransformationEngine();

        Map<String, Object> data = new LinkedHashMap<>();
        // "nomComplet" absent volontairement

        assertThrows(IllegalArgumentException.class, () ->
                engine.applyRule(data, RuleType.UPPERCASE, "nomComplet", null, null));
    }

    @Test
    void dateMalformeeLeveUneException() {
        TransformationEngine engine = new TransformationEngine();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date_naissance", "1995-03-14"); // mauvais format : attendu JJ/MM/AAAA

        assertThrows(IllegalArgumentException.class, () ->
                engine.applyRule(data, RuleType.FORMAT_DATE, "date_naissance", null, null));
    }

    @Test
    void champManquantPourRenameLeveUneException() {
        TransformationEngine engine = new TransformationEngine();

        Map<String, Object> data = new LinkedHashMap<>();
        // "email" absent volontairement

        assertThrows(IllegalArgumentException.class, () ->
                engine.applyRule(data, RuleType.RENAME, "email", null, "adresseEmail"));
    }

    @Test
    void renameDeplaceLaValeurVersLeChampCible() {
        TransformationEngine engine = new TransformationEngine();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", "sarah@example.com");

        data = engine.applyRule(data, RuleType.RENAME, "email", null, "adresseEmail");

        assertEquals("sarah@example.com", data.get("adresseEmail"));
        assertEquals(1, data.size());
    }

    @Test
    void appliqueLes3ReglesDansLOrdre() {
        TransformationEngine engine = new TransformationEngine();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("prenom", "sarah");
        data.put("nom", "martin");
        data.put("date_naissance", "14/03/1995");

        data = engine.applyRule(data, RuleType.CONCATENATE, "prenom", "nom", "nomComplet");
        data = engine.applyRule(data, RuleType.UPPERCASE, "nomComplet", null, null);
        data = engine.applyRule(data, RuleType.FORMAT_DATE, "date_naissance", null, null);

        assertEquals("SARAH MARTIN", data.get("nomComplet"));
        assertEquals("1995-03-14", data.get("date_naissance"));
        assertEquals(2, data.size());
    }
}
