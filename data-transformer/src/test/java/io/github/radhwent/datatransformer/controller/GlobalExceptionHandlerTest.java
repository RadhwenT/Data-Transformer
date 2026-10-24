package io.github.radhwent.datatransformer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires dedies (pas de contexte Spring) pour les deux mappings
 * qu'aucun test d'integration MockMvc ne peut declencher :
 * - handleUnexpected(RuntimeException) : aucun endpoint reel ne declenche
 *   naturellement une RuntimeException imprevue (toutes les erreurs metier
 *   sont deja des exceptions dediees, mappees ailleurs dans cette classe).
 * - handleTooLarge(MaxUploadSizeExceededException) : @AutoConfigureMockMvc
 *   ne passe pas par un vrai conteneur servlet, donc
 *   spring.servlet.multipart.max-file-size n'est jamais applique par le mock
 *   (voir ExecutionControllerTest, section telechargement).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleUnexpectedRenvoie500AvecLeMessageDeLException() {
        ResponseEntity<String> response = handler.handleUnexpected(new RuntimeException("panne imprevue"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().contains("panne imprevue"));
    }

    @Test
    void handleTooLargeRenvoie413() {
        ResponseEntity<String> response = handler.handleTooLarge(
                new MaxUploadSizeExceededException(20 * 1024 * 1024));

        assertEquals(413, response.getStatusCode().value());
        assertEquals("Fichier trop volumineux", response.getBody());
    }
}
