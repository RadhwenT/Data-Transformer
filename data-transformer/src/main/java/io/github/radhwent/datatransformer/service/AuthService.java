package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.entity.User;
import io.github.radhwent.datatransformer.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Meme message d'erreur que l'email soit inconnu ou le mot de passe faux,
     * pour ne pas laisser un appelant deviner quels emails sont enregistres.
     */
    public User login(String email, String rawPassword) {
        User user = userRepository.findByEmailAndActiveTrue(email)
        .orElseThrow(() -> new UnauthorizedException("Email ou mot de passe incorrect"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Email ou mot de passe incorrect");
        }

        return user;
    }

    /**
     * Permet a l'utilisateur de remplacer le mot de passe genere par l'admin.
     * L'ancien mot de passe est exige pour eviter qu'une session laissee
     * ouverte permette a un tiers de prendre le controle du compte.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Utilisateur introuvable"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Mot de passe actuel incorrect");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalStateException("Le nouveau mot de passe doit etre different de l'ancien");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Un utilisateur peut modifier sa propre identite (prenom, nom, email),
     * mais jamais son role : ca reste un privilege admin (voir AdminService).
     */
    @Transactional
    public User updateProfile(Long userId, String firstName, String lastName, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Utilisateur introuvable"));

        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new EmailAlreadyUsedException("Un compte existe deja avec cet email : " + email);
                });

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        return userRepository.save(user);
    }
}