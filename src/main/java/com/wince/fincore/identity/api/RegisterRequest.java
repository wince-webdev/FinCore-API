package com.wince.fincore.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Corps de {@code POST /api/v1/auth/register}.
 *
 * <p>Email, profil client minimal et mot de passe — le cahier (RG-AUTH-02 +
 * F-02) impose que l'inscription publique ne crée qu'un utilisateur
 * {@code CUSTOMER} (email), et que le client arrive en {@code LEVEL_1}/
 * {@code PENDING} avec son profil minimal (nom, prénom, date de naissance,
 * téléphone — RG-KYC-01). Le package charge l'injection par constructeur :
 * {@code AuthService.register} écrit ces champs dans {@code Customer}.</p>
 *
 * <p>La <strong>politique de mot de passe</strong> (12 caractères, 4
 * classes de caractères) n'est <em>pas</em> exprimée en annotations :
 * {@code PasswordPolicy} la rassemble toutes en une seule réponse 400
 * (CA-AUTH-01), alors que Bean Validation s'arrête à la première erreur de
 * champ.</p>
 */
public record RegisterRequest(
        @NotBlank(message = "l'adresse e-mail est obligatoire")
        @Email(message = "l'adresse e-mail n'est pas valide")
        @Size(max = 320, message = "l'adresse e-mail ne peut pas dépasser 320 caractères")
        String email,
        @NotBlank(message = "le prénom est obligatoire")
        @Size(max = 100, message = "le prénom ne peut pas dépasser 100 caractères")
        String firstName,
        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 100, message = "le nom ne peut pas dépasser 100 caractères")
        String lastName,
        @NotNull(message = "la date de naissance est obligatoire")
        LocalDate birthDate,
        @NotBlank(message = "le téléphone est obligatoire")
        @Size(max = 30, message = "le téléphone ne peut pas dépasser 30 caractères")
        String phone,
        @NotBlank(message = "le mot de passe est obligatoire")
        String password) {
}
