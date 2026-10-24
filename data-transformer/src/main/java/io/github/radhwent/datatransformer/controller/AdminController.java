package io.github.radhwent.datatransformer.controller;

import io.github.radhwent.datatransformer.repository.DataProcessRepository;
import io.github.radhwent.datatransformer.repository.ExecutionRepository;
import io.github.radhwent.datatransformer.repository.ProcessRuleRepository;
import io.github.radhwent.datatransformer.service.AdminService;
import io.github.radhwent.datatransformer.service.AdminService.CreatedUser;
import io.github.radhwent.datatransformer.service.UnauthorizedException;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final DataProcessRepository processRepository;
    private final ProcessRuleRepository processRuleRepository;
    private final ExecutionRepository executionRepository;

    public AdminController(AdminService adminService,
                           DataProcessRepository processRepository,
                           ProcessRuleRepository processRuleRepository,
                           ExecutionRepository executionRepository) {
        this.adminService = adminService;
        this.processRepository = processRepository;
        this.processRuleRepository = processRuleRepository;
        this.executionRepository = executionRepository;
    }

    @PostMapping("/users")
    public ResponseEntity<CreatedUserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                          HttpSession session) {
        adminService.requireAdmin(currentUserId(session));

        CreatedUser created = adminService.createUser(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.role());

        return ResponseEntity.status(HttpStatus.CREATED).body(CreatedUserResponse.from(created));
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> listUsers(HttpSession session) {
        adminService.requireAdmin(currentUserId(session));

        List<AdminUserResponse> users = adminService.findAllUsers()
                .stream()
                .map(AdminUserResponse::from)
                .toList();

        return ResponseEntity.ok(users);
    }

    /**
     * Vue admin agregee : tous les traitements actifs, toutes utilisateurs
     * confondus (dashboard admin, filtrable ensuite par utilisateur cote front).
     */
    @GetMapping("/processes")
    public ResponseEntity<List<ProcessResponse>> listAllProcesses(HttpSession session) {
        adminService.requireAdmin(currentUserId(session));

        List<ProcessResponse> processes = processRepository.findByActiveTrue()
                .stream()
                .map(p -> ProcessResponse.from(
                        p,
                        processRuleRepository.countByProcessId(p.getId()),
                        executionRepository.findFirstByProcessIdOrderByStartedAtDesc(p.getId()).orElse(null)))
                .toList();

        return ResponseEntity.ok(processes);
    }

    /**
     * Vue admin agregee : toutes les executions, toutes utilisateurs
     * confondus (dashboard admin : KPIs, panneau "alertes", filtre par utilisateur).
     */
    @GetMapping("/executions")
    public ResponseEntity<List<ExecutionResponse>> listAllExecutions(HttpSession session) {
        adminService.requireAdmin(currentUserId(session));

        List<ExecutionResponse> executions = executionRepository.findAllOrderByStartedAtDesc()
                .stream()
                .map(ExecutionResponse::from)
                .toList();

        return ResponseEntity.ok(executions);
    }

    private Long currentUserId(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            throw new UnauthorizedException("Vous devez etre connecte");
        }
        return (Long) userId;
    }

        @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id, HttpSession session) {
        Long adminId = currentUserId(session);
        adminService.requireAdmin(adminId);
        adminService.deactivateUser(adminId, id);
        return ResponseEntity.noContent().build();
    }

        @PutMapping("/users/{id}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable Long id, HttpSession session) {
        adminService.requireAdmin(currentUserId(session));
        adminService.activateUser(id);
        return ResponseEntity.noContent().build();
    }
    

}