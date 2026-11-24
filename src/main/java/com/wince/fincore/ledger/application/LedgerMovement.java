package com.wince.fincore.ledger.application;

import com.wince.fincore.ledger.domain.Direction;
import com.wince.fincore.shared.domain.Currency;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mouvement élémentaire — entrée d'un {@link LedgerService#apply(FinancialTransaction, List)} —
 * un mouvement = {@code (compte, direction, montant, devise)} — montant strictement
 * positif — {@code §6.2} — {@code CHECK (amount > 0)}.
 *
 * <p>Équivalent Domain-Driven — {@code Movement} dans les bons de gestion bancaire.
 */
public record LedgerMovement(UUID accountId, Direction direction, BigDecimal amount, Currency currency) {

    public LedgerMovement {
        if (accountId == null || direction == null || currency == null) {
            throw new IllegalArgumentException("compte, direction et devise obligatoires (§6.2)");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("le montant d'un mouvement doit être strictement positif (§6.2)");
        }
    }
}
