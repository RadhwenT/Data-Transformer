package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.service.ConflictException;
import io.github.radhwent.datatransformer.service.EmailAlreadyUsedException;
import io.github.radhwent.datatransformer.service.ForbiddenException;
import io.github.radhwent.datatransformer.service.InvalidFileFormatException;
import io.github.radhwent.datatransformer.service.UnauthorizedException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;


/**
 * Gestion d'erreurs centralisee, partagee par tous les @RestController
 * (avant : chaque controller dupliquait ses propres @ExceptionHandler).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Fichier vide ou JSON invalide -> 400 */
    @ExceptionHandler(InvalidFileFormatException.class)
    public ResponseEntity<String> handleInvalidFormat(InvalidFileFormatException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /** Traitement ou utilisateur introuvable -> 404 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    /** Traitement mal configure, ex. aucune regle -> 400 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleBadRequest(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /** Inscription avec un email deja utilise -> 409 */
    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<String> handleEmailAlreadyUsed(EmailAlreadyUsedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    /** Nom de traitement ou ordre d'execution deja pris -> 409 */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<String> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    /** Pas connecte, ou identifiants de connexion invalides -> 401 */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<String> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    /** Fichier au-dela de spring.servlet.multipart.max-file-size -> 413 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body("Fichier trop volumineux");
    }

    /** Corps de requete JSON invalide au sens de @Valid (ex. AuthController) -> 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
    }

    /**
     * Filet de securite pour les erreurs metier imprevues.
     * Volontairement limite a RuntimeException : les exceptions du framework
     * (parametre ou partie multipart manquants) restent gerees par Spring,
     * qui renvoie deja un 400 approprie.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleUnexpected(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Erreur inattendue : " + e.getMessage());
    }

        /** Connecte mais sans les droits -> 403 */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<String> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
}
