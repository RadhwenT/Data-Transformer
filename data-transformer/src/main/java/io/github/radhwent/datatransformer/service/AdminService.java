package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.entity.Role;
import io.github.radhwent.datatransformer.entity.User;
import io.github.radhwent.datatransformer.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;

    public AdminService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        PasswordGenerator passwordGenerator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
    }

    /**
     * Cree un utilisateur avec un mot de passe genere aleatoirement.
     * Le mot de passe en clair est renvoye UNE SEULE FOIS a l'admin :
     * seul son hash est stocke, il est impossible de le retrouver ensuite.
     */
    @Transactional
    public CreatedUser createUser(String firstName, String lastName, String email, Role role) {

        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyUsedException("Un compte existe deja avec cet email : " + email);
        }

        String rawPassword = passwordGenerator.generate();

        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role != null ? role : Role.USER);

        User saved = userRepository.save(user);
        return new CreatedUser(saved, rawPassword);
    }

    @Transactional(readOnly = true)
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Verifie que l'utilisateur connecte est bien administrateur.
     */
    @Transactional(readOnly = true)
    public void requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Vous devez etre connecte"));

        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Acces reserve aux administrateurs");
        }
    }

    /** Porteur temporaire du mot de passe en clair, jamais persiste. */
    public record CreatedUser(User user, String rawPassword) {}

        @Transactional
    public void deactivateUser(Long adminId, Long targetUserId) {
        if (adminId.equals(targetUserId)) {
            throw new IllegalStateException("Vous ne pouvez pas desactiver votre propre compte");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + targetUserId));

        user.setActive(false);
        userRepository.save(user);
    }

        @Transactional
    public void activateUser(Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + targetUserId));

        user.setActive(true);
        userRepository.save(user);
    }
    
}