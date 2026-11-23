package com.wince.fincore.identity.api;

import java.util.UUID;

/**
 * Réponse commune aux endpoints de session : les deux jetons et ce qu'il
 * faut pour les employer immédiatement.
 *
 * @param userId      identifiant UUID de l'utilisateur ({@code sub} du JWT)
 * @param email       adresse normalisée en minuscules
 * @param role        {@code CUSTOMER} / {@code TELLER} / {@code ADMIN} / {@code AUDITOR}
 * @param tokenType   toujours {@code Bearer} (précédé de {@code Authorization: })
 * @param accessToken JWT court (15 min) — à envoyer dans chaque requête protégée
 * @param expiresIn   durée de vie de l'access token <strong>en secondes</strong>
 * @param refreshToken jeton opaque (7 jours) — à conserver côté client et
 *                    à envoyer uniquement sur {@code /auth/refresh} et
 *                    {@code /auth/logout}
 */
public record AuthResponse(
        UUID userId,
        String email,
        String role,
        String tokenType,
        String accessToken,
        long expiresIn,
        String refreshToken) {
}
