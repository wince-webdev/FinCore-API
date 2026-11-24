package com.wince.fincore.ledger.application;

import com.wince.fincore.shared.infrastructure.ClockConfig;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Génération du numéro externe — {@code financial_transaction.reference UNIQUE}
 * (§6.1) — format {@code TXN-YYYYMMDD-NNNNNN} — par exemple {@code TXN-20261130-000042} —
 * {@code yyyyMMdd} — fuseau {@code Africa/Porto-Novo} ({@code ClockConfig.BANKING_ZONE}).
 *
 * <p>{@code AtomicLong} — {@code %06d} — arbitre local — l'arbitre final reste
 * le {@code UNIQUE} en base — d'où le {@code orElseThrow(LEDGER_UNBALANCED)}-like
 * {@code DB constraint violation} géré par {@code GlobalExceptionHandler}.
 *
 * <p>Équivalent PHP/Symfony — {@code generateUniqueReference()} d'un service —
 * avec un suffixe séquentiel — voir V5 (calcul en SQL pour le seed — parité ICI).
 */
public final class TransactionReferenceGenerator {

    private static final AtomicLong COUNTER = new AtomicLong(1);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private TransactionReferenceGenerator() {
    }

    public static String generate() {
        String date = LocalDate.now(ClockConfig.BANKING_ZONE).format(DATE_FORMAT);
        return "TXN-" + date + "-" + String.format("%06d", COUNTER.getAndIncrement());
    }
}
