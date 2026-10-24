package io.github.radhwent.datatransformer.service;

/** Ressource en conflit avec l'etat actuel (nom deja pris, ordre deja utilise...) -> 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
