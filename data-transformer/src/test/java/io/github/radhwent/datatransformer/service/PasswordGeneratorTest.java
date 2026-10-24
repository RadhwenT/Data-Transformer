package io.github.radhwent.datatransformer.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordGeneratorTest {

    private final PasswordGenerator generator = new PasswordGenerator();

    @Test
    void genereUnMotDePasseDeDouzeCaracteres() {
        assertEquals(12, generator.generate().length());
    }

    @Test
    void contientAuMoinsUneMajusculeUneMinusculeUnChiffreEtUnSpecial() {
        // Repete plusieurs fois : le tirage est aleatoire, un seul essai
        // ne prouverait rien.
        for (int i = 0; i < 100; i++) {
            String password = generator.generate();

            assertTrue(password.chars().anyMatch(Character::isUpperCase),
                    "Aucune majuscule dans : " + password);
            assertTrue(password.chars().anyMatch(Character::isLowerCase),
                    "Aucune minuscule dans : " + password);
            assertTrue(password.chars().anyMatch(Character::isDigit),
                    "Aucun chiffre dans : " + password);
            assertTrue(password.chars().anyMatch(c -> "!@#$%&*".indexOf(c) >= 0),
                    "Aucun caractere special dans : " + password);
        }
    }

    @Test
    void neContientAucunCaractereAmbigu() {
        // I, O, l, o, 0, 1 sont exclus volontairement : le mot de passe est
        // transmis oralement ou par ecrit par l'admin.
        String ambigus = "IOlo01";

        for (int i = 0; i < 100; i++) {
            String password = generator.generate();
            for (char c : ambigus.toCharArray()) {
                assertTrue(password.indexOf(c) < 0,
                        "Caractere ambigu '" + c + "' trouve dans : " + password);
            }
        }
    }

    @Test
    void genereUnMotDePasseDifferentAChaqueAppel() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            passwords.add(generator.generate());
        }

        // Un Set elimine les doublons : si les 1000 sont differents,
        // le generateur n'est pas deterministe.
        assertEquals(1000, passwords.size());
    }
}