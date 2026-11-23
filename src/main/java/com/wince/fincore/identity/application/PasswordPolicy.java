package com.wince.fincore.identity.application;

import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Politique de mot de passe RG-AUTH-01 : au moins 12 caractères, avec
 * une majuscule, une minuscule, un chiffre et un caractère spécial.
 *
 * <p><strong>Pourquoi une classe dédiée et pas des annotations
 * {@code @Size}/{@code @Pattern} ?</strong> Parce que CA-AUTH-01 exige de
 * renvoyer la <em>liste complète</em> des règles violées en un seul 400 —
 * une contrainte Bean Validation s'arrête à la première erreur du champ.
 * Ici on collecte <strong>toutes</strong> les violations pour que le client
 * corrige en une seule passe.</p>
 *
 * <p>Équivalent Symfony : un validateur custom ou la contrainte
 * {@code PasswordRequirements} — avec le même souci de « tout lister ».</p>
 */
@Component
public class PasswordPolicy {

    /** Longueur minimale (RG-AUTH-01). */
    public static final int MIN_LENGTH = 12;

    /**
     * Valide un mot de passe et lève {@code VALIDATION_FAILED} (400) avec
     * la liste des règles violées si une seule manque.
     *
     * <p>La propriété {@code errors} est un objet indexé par champ — même
     * forme que celle produite par Bean Validation dans
     * {@code GlobalExceptionHandler} — pour que le client traite toujours
     * un {@code errors} de type objet, jamais tantôt un tableau tantôt un
     * objet.</p>
     *
     * @throws FinCoreException {@code VALIDATION_FAILED}, la propriété
     *         {@code errors} contient {@code password} → règles manquantes
     */
    public void validate(String rawPassword) {
        List<String> violations = violations(rawPassword);
        if (!violations.isEmpty()) {
            throw new FinCoreException(
                    ErrorCode.VALIDATION_FAILED,
                    "Le mot de passe ne respecte pas " + violations.size()
                            + " règle(s) de sécurité.",
                    Map.of("errors", Map.of("password", violations)));
        }
    }

    /** Liste des règles violées (vide = mot de passe acceptable). */
    List<String> violations(String rawPassword) {
        List<String> violations = new ArrayList<>();
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            violations.add("au moins " + MIN_LENGTH + " caractères");
        }
        if (rawPassword == null || !rawPassword.matches(".*[A-Z].*")) {
            violations.add("au moins une lettre majuscule");
        }
        if (rawPassword == null || !rawPassword.matches(".*[a-z].*")) {
            violations.add("au moins une lettre minuscule");
        }
        if (rawPassword == null || !rawPassword.matches(".*\\d.*")) {
            violations.add("au moins un chiffre");
        }
        if (rawPassword == null || !rawPassword.matches(".*[^A-Za-z0-9].*")) {
            violations.add("au moins un caractère spécial");
        }
        return violations;
    }
}
