package io.github.radhwent.datatransformer.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class PasswordGenerator {

    private static final String MAJUSCULES = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULES = "abcdefghijkmnpqrstuvwxyz";
    private static final String CHIFFRES = "23456789";
    private static final String SPECIAUX = "!@#$%&*";

    private static final String TOUS = MAJUSCULES + MINUSCULES + CHIFFRES + SPECIAUX;
    private static final int LONGUEUR = 12;

    private final SecureRandom random = new SecureRandom();

    /**
     * Genere un mot de passe aleatoire de 12 caracteres, contenant au moins
     * une majuscule, une minuscule, un chiffre et un caractere special.
     */
    public String generate() {
        StringBuilder password = new StringBuilder();

        // Garantit au moins un caractere de chaque categorie
        password.append(pick(MAJUSCULES));
        password.append(pick(MINUSCULES));
        password.append(pick(CHIFFRES));
        password.append(pick(SPECIAUX));

        for (int i = password.length(); i < LONGUEUR; i++) {
            password.append(pick(TOUS));
        }

        return shuffle(password.toString());
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }

    private String shuffle(String input) {
        char[] chars = input.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}