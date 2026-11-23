package com.wince.fincore.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Corps de {@code POST /api/v1/auth/login}.
 *
 * <p>Le format de l'e-mail est validé (400 clair pour l'utilisateur), mais
 * <strong>l'existence</strong> du compte n'est jamais révélée : un e-mail
 * inconnu et un mauvais mot de passe produisent exactement la même
 * réponse 401 (RG-AUTH-05).</p>
 */
public record LoginRequest(
        @NotBlank(message = "l'adresse e-mail est obligatoire")
        @Email(message = "l'adresse e-mail n'est pas valide")
        String email,
        @NotBlank(message = "le mot de passe est obligatoire")
        String password) {
}
