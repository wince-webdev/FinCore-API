package com.wince.fincore.account.api;

import com.wince.fincore.account.domain.AccountKind;
import com.wince.fincore.shared.domain.Currency;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Corps de {@code POST /api/v1/accounts} — F-03 (§8, RG-ACC-01 à 07).
 *
 * <p>{@code currency} NULL → XOF — « Devises : XOF (défaut), EUR, USD »
 * (RG-ACC-01) — {@code AccountService.open} applique le défaut (le record
 * ne force pas la valeur au constructeur — évite le défaut de variable
 * statique, pire que l'absence).
 *
 * <p>{@code customerId} = le client pour lequel le compte est ouvert
 * (TELLER/ADMIN — §5 — le client désigné est dans le DTO — vérification
 * RG-ACC-02 sur son statut KYC — Phase 3/F-02).
 */
public record OpenAccountRequest(
        @NotNull(message = "le client est obligatoire") UUID customerId,
        @NotNull(message = "le type de compte est obligatoire") AccountKind kind,
        Currency currency) {
}
