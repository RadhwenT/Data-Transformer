package io.github.radhwent.datatransformer.service;

/** Connecte, mais sans les droits necessaires -> 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}