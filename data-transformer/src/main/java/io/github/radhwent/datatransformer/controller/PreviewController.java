package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.service.PreviewService;
import io.github.radhwent.datatransformer.service.UnauthorizedException;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Separe de ProcessController (deja volumineux) : le preview est un dry-run
 * sur fichier uploade, plus proche dans l'esprit de ExecutionController que
 * du CRUD des traitements.
 */
@RestController
@RequestMapping("/api/processes")
public class PreviewController {

    private final PreviewService previewService;

    public PreviewController(PreviewService previewService) {
        this.previewService = previewService;
    }

    @PostMapping(value = "/{id}/preview", consumes = "multipart/form-data")
    public ResponseEntity<PreviewResponse> preview(@PathVariable Long id,
                                                    @RequestParam("file") MultipartFile file,
                                                    HttpSession session) {
        Long userId = currentUserId(session);
        return ResponseEntity.ok(previewService.preview(id, userId, file));
    }

    private Long currentUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            throw new UnauthorizedException("Vous devez etre connecte");
        }
        return (Long) userId;
    }
}
