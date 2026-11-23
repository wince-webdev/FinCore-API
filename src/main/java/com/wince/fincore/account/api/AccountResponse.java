package com.wince.fincore.account.api;

import com.wince.fincore.account.domain.Account;
import com.wince.fincore.account.domain.AccountKind;
import com.wince.fincore.account.domain.AccountStatus;
import com.wince.fincore.account.domain.SystemCode;
import com.wince.fincore.shared.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de réponse — {@code POST /accounts}, {@code GET /accounts},
 * {@code GET /accounts/{id}} — F-03 (§8, cahier §9 — RFC 9457).
 *
 * <p>Miroir du {@code account} (§6.1) — « exact projection » — le service
 * projette l'entité sans positionner de logique ici
 * ({@code AccountResponse.from(Account)}) — String vs UUID, énumérés →
 * Java string dans le JSON, {@code systemCode} NULL pour les comptes
 * clients — {@code spring.jackson.default-property-inclusion=non_null}.
 */
public record AccountResponse(
        UUID id, String accountNumber, UUID customerId, AccountKind kind, SystemCode systemCode,
        Currency currency, AccountStatus status, BigDecimal balance, Instant openedAt, Instant closedAt) {

    /** Projet de l'entité — jamais de donnée sensible — voir classe. */
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(), account.getAccountNumber(), account.getCustomerId(),
                account.getKind(), account.getSystemCode(), account.getCurrency(),
                account.getStatus(), account.getBalance(), account.getOpenedAt(), account.getClosedAt());
    }
}
