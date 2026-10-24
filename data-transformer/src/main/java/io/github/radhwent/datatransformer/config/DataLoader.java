package io.github.radhwent.datatransformer.config;

import io.github.radhwent.datatransformer.entity.*;
import io.github.radhwent.datatransformer.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Desactive en test (@Profile) : src/test/resources/application.properties
 * active le profil "test", pour que les tests n'inserent pas ces donnees de
 * demo dans la base jetable Testcontainers.
 */
@Component
@Profile("!test")
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RuleRepository ruleRepository;
    private final DataProcessRepository processRepository;
    private final ProcessRuleRepository processRuleRepository;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(UserRepository userRepository,
                      RuleRepository ruleRepository,
                      DataProcessRepository processRepository,
                      ProcessRuleRepository processRuleRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.ruleRepository = ruleRepository;
        this.processRepository = processRepository;
        this.processRuleRepository = processRuleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        // 1. Utilisateur de demo (cree une seule fois)
        User demoUser = userRepository.findByEmail("demo@example.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setFirstName("Demo");
                    u.setLastName("User");
                    u.setEmail("demo@example.com");
                    u.setPasswordHash(passwordEncoder.encode("demo123"));
                    u.setRole(Role.USER);
                    return userRepository.save(u);
                });

                        // Utilisateur administrateur
        userRepository.findByEmail("admin@example.com")
                .orElseGet(() -> {
                    User admin = new User();
                    admin.setFirstName("Admin");
                    admin.setLastName("Principal");
                    admin.setEmail("admin@example.com");
                    admin.setPasswordHash(passwordEncoder.encode("admin123"));
                    admin.setRole(Role.ADMIN);
                    return userRepository.save(admin);
                });

        // 2. Catalogue des regles
        Rule concatenate = findOrCreateRule(RuleType.CONCATENATE,
                "Concatener deux valeurs",
                "Joint deux champs en un seul, separes par un espace");

        Rule uppercase = findOrCreateRule(RuleType.UPPERCASE,
                "Mettre en majuscules",
                "Convertit la valeur d'un champ en majuscules");

        Rule formatDate = findOrCreateRule(RuleType.FORMAT_DATE,
                "Reformater une date",
                "Convertit une date de JJ/MM/AAAA vers AAAA-MM-JJ");

        findOrCreateRule(RuleType.RENAME,
                "Renommer un champ",
                "Deplace la valeur d'un champ source vers un champ cible");

        // 3. Traitement "Nettoyage clients" (cree une seule fois)
        if (processRepository.findByName("Nettoyage clients").isEmpty()) {

            DataProcess process = new DataProcess();
            process.setName("Nettoyage clients");
            process.setDescription("Scenario nominal : concatenation, majuscules, reformatage de date");
            process.setInputFormat(FileFormat.JSON);
            process.setOutputFormat(FileFormat.JSON);
            process.setUser(demoUser);
            processRepository.save(process);

            // Regle 1 : prenom + nom -> nomComplet
            ProcessRule pr1 = new ProcessRule();
            pr1.setProcess(process);
            pr1.setRule(concatenate);
            pr1.setExecutionOrder(1);
            pr1.setSourceField("prenom");
            pr1.setSourceField2("nom");
            pr1.setTargetField("nomComplet");
            processRuleRepository.save(pr1);

            // Regle 2 : nomComplet en majuscules
            ProcessRule pr2 = new ProcessRule();
            pr2.setProcess(process);
            pr2.setRule(uppercase);
            pr2.setExecutionOrder(2);
            pr2.setSourceField("nomComplet");
            processRuleRepository.save(pr2);

            // Regle 3 : date_naissance reformatee
            ProcessRule pr3 = new ProcessRule();
            pr3.setProcess(process);
            pr3.setRule(formatDate);
            pr3.setExecutionOrder(3);
            pr3.setSourceField("date_naissance");
            processRuleRepository.save(pr3);

            System.out.println(">>> Donnees de demarrage inserees : traitement 'Nettoyage clients' avec 3 regles");
        }
    }

    private Rule findOrCreateRule(RuleType type, String name, String description) {
        return ruleRepository.findByType(type)
                .orElseGet(() -> {
                    Rule r = new Rule();
                    r.setType(type);
                    r.setName(name);
                    r.setDescription(description);
                    return ruleRepository.save(r);
                });
    }
}