# Phase 5 — F-04 : Grand livre (Ledger) + F-05 : Dépôts / retraits — ⏳ PRÉVUE

> **Statut : PRÉVUE** — sera **FAIT** à la fin de cette phase.
> Phase 5 = **F-04 Grand livre** (MUST) + **F-05 Dépôts/retraits** (MUST) — cahier §7, Phase 5.
> Plusieurs critères d'acceptance (CA-LED-01 à 04, CA-DEP-01 à 02) et RG-LED-01 à 07.

## 5.1 Objectif de la phase (3 lignes)

1. Implémenter le **grand livre en partie double** : chaque transaction = `financial_transaction` + `ledger_entry` (+ soldes + outbox) dans **une seule transaction DB** — immuable (triggers PostgreSQL) — avec **chasse de hachages par compte** et verrouillage ordonné des comptes pour éviter les deadlocks.
2. Implémenter les **dépôts et retraits** : débit compte client / crédit compte(s) système — refus si solde insuffisant — plafonds de Phase 6 déjà référentiels.
3. Prouver la cohérence : un test dédié vérifie `Σ DEBIT = Σ CREDIT` pour chaque devise et que le solde `account.balance` correspond à `Σ crédits − Σ débits` depuis le grand livre.

## 5.2 Fonctionnalités prévues (F-04 Grand livre — RG-LED-01 à 07)

| Règle | Prévision |
|---|---|
| **RG-LED-01** | `LedgerService.post(transaction)` : crée `financial_transaction` + `ledger_entry` + met à jour `account.balance` — dans `@Transactional` du service. Soit tout, soit rien. |
| **RG-LED-02** | `LedgerBalancer` : assert `Σ DEBIT = Σ CREDIT` par devise — violation → `FinCoreException(LEDGER_UNBALANCED)` (500) + rollback — vérifier également dans un test dédié (`doit_refuser_le_virement_quand_le_solde_est_insuffisant` pattern). |
| **RG-LED-03** | Triggers PostgreSQL dans migration V6/V7 : `UPDATE` / `DELETE` sur `ledger_entry` → exception explicite — vérifier dans `LedgerIntegrationTest`. |
| **RG-LED-04** | `LedgerEntry.balanceAfter` — solde du compte juste après l'écriture (nécessite `SELECT … FOR UPDATE` sur le compte avant de mettre à jour). |
| **RG-LED-05** | Chaînage de hachages : `entry_hash = SHA-256(prevHash ‖ transactionId ‖ accountId ‖ direction ‖ amount ‖ currency ‖ createdAt)` — initial `prevHash` = 64 zéros — mis à jour après commit par un refresh. |
| **RG-LED-06** | Verrouillage **dans l'ordre croissant de l'identifiant** (`ORDER BY id`) — anti-deadlock — + `version` (verrou optimiste JPA). |
| **RG-LED-07** | `account.balance` dénormalisé mis à jour dans la même transaction — doit toujours correspondre à `Σ crédits − Σ débits` — vérifier dans `LedgerConsistencyTest`. |
| **CA-LED-01** | Test : insertion d'une transaction déséquilibrée → rejet — aucune ligne persistante. |
| **CA-LED-02** | Test : `UPDATE` / `DELETE` direct sur `ledger_entry` (via SQL) → exception du trigger. |
| **CA-LED-03** | `GET /admin/ledger/verify` recalcule la chaîne de hachages — test avec écriture **altérée artificiellement** (UPDATE via SQL après trigger désactivé ou utiliser le `prevHash` simulé). |
| **CA-LED-04** | Test concurrence : N opérations aléatoires concurrencies — `Σ balance` = 0 après — ExecutorService + CountDownLatch. |

**Entité `FinancialTransaction`** (cahier §6.1) : `id UUID PK`, `reference UNIQUE`, `type` (enum `DEPOSIT|WITHDRAWAL|TRANSFER|FX_TRANSFER|EXTERNAL_TRANSFER|FEE|INTEREST|REVERSAL`), `status` (enum `PENDING|COMPLETED|FAILED|REVERSED`), `amount`, `feeAmount`, `currency`, `sourceAccountId`, `destinationAccountId`, `reversalOf`, `initiatedBy FK app_user`, `description`, `failureReason`, `createdAt`, `completedAt`.

**Entité `LedgerEntry`** : `id BIGSERIAL PK` (pas de UUID — grand livre est LIN), `transactionId FK`, `accountId FK`, `direction` (enum `DEBIT|CREDIT`), `amount > 0` (CHECK), `currency`, `balanceAfter`, `prevHash` (64 zéros initial), `entryHash`, `createdAt`.

## 5.3 Fonctionnalités prévues (F-05 Dépôts/retraits — RG-DEP-01 à 05, RG-TRF-02, RG-IDEM-01 à 05)

**Virement interne prévu (Phase 6 — préparatoire)** : `POST /api/v1/transfers` — source → destination — débit source + frais, crédit destination, crédit `FEE_REVENUE` (RG-TRF-01) — pre-checks (`source != destination` — RG-TRF-02; `RULE-IDEMPOTENCE` — RG-IDEM-01).

**Dépôt** (par TELLER — RG-DEP-01) : débit `CASH_VAULT`, crédit compte client, aucun frais par défaut, vérif plafond solde (F-06).

**Retrait** (par TELLER — RG-DEP-02) : débit compte client (montant + frais), crédit `CASH_VAULT` (montant) + `FEE_REVENUE` (frais) — refus si solde insuffisant (`INSUFFICIENT_FUNDS`) — vérif plafonds F-06.

**Checks prévus (RG-DEP-03 à 05, RG-TRF-02, RG-TRF-04, RG-TRF-05, RG-TRF-07, RG-IDEM-01 à 05, CA-DEP-01 à 02, CA-TRF-01 à 04)** :
- RG-DEP-03 : montant strictement positif, échelle respectée (ex. pas de décimales en XOF) — `INVALID_AMOUNT_SCALE`.
- RG-DEP-04 : compte `FROZEN` ou `CLOSED` → `ACCOUNT_NOT_ACTIVE`.
- RG-DEP-05 : plafonds (Phase 6).
- RG-IDEM-01 : `Idempotency-Key` obligatoire sur `POST /deposits` et `/withdrawals` (400 `IDEMPOTENCY_KEY_REQUIRED`).
- RG-IDEM-02 : même clé + même corps → réponse initiale rejouée avec `Idempotent-Replayed: true`.
- RG-IDEM-03 : même clé + corps différent → 422 `IDEMPOTENCY_KEY_REUSED`.
- RG-IDEM-04 : même clé en traitement → 409 `REQUEST_IN_PROGRESS` (UNIQUE(user_id, idempotency_key) en base — le gagnant à la charge de la DB).
- RG-TRF-05 : plafonds daily — recalculés depuis le grand livre (pas de counters mutables).
- RG-TRF-07 : `POST /transfers/quote` — prévisualisation — pas d'écriture.
- CA-DEP-01 : dépôt de 50 000 XOF → solde passe à 50 000 — exactement 2 écritures (débit CASH_VAULT + crédit client).
- CA-DEP-02 : retrait supérieur au solde → refus — aucune écriture.

## 5.4 Commandes prévues

| Commande | Ce qu'elle fait | Pourquoi | Importance |
|---|---|---|---|
| `.\mvnw.cmd test` | Compile + exécute les tests (incl. new `LedgerServiceTest`, `DepositServiceTest`, `WithdrawalServiceTest`, `IdempotencyTest`) | Vérification de la phase — CA-LED-04 (concurrence) est complexe à réfutable sans concurrency | ✅ CRITIQUE |
| `Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing` | Vérifie que l'app tourne | Validation de démarrage | Importante |
| `curl.exe -sS -X POST http://localhost:8080/api/v1/deposits -H "Authorization: Bearer <JWT TELLER>" -H "Idempotency-Key: ...uuid..." -d '{"accountId":"...","amount":50000}'` | Exercice live — dépôt 50 000 XOF (CA-DEP-01) | Vérifie live les 2 écritures + solde à 50 000 | Importante (partie de Phase 5 et de Phase 4 5.5) |
| `docker exec fincore-db psql -U fincore -d fincore -c "SELECT transaction_id, account_id, direction, amount, balance_after, entry_hash FROM ledger_entry ORDER BY id DESC LIMIT 5;"` | Exercice — affiche les 5 dernières écritures — point d'entrée pédagogique : Chainage + solde_after + `entry_hash` (RG-LED-02/05) — **à lire pour comprendre le journal** | Important — interrompre d'observer | Importante |
| `Invoke-WebRequest http://localhost:8080/admin/ledger/verify -UseBasicParsing` | Exercice live — `GET /admin/ledger/verify` recalcule la chaîne de hachages — prouve qu'un test updères d'alteration se détecte | Vérifie RG-LED-03 (ALTERATION) | Importante |

## 5.5 Fichiers clés à créer (prévision)

**Module `ledger`** :

| Fichier | Rôle |
|---|---|
| `ledger/domain/FinancialTransaction.java` | Entity (type, status, amount, feeAmount, sourceAccountId, destinationAccountId, reversalOf, reference unique) |
| `ledger/domain/LedgerEntry.java` | Entity immuable (transactionId, accountId, direction, amount>0, balanceAfter, prevHash, entryHash) |
| `ledger/domain/EntryDirection.java` | `DEBIT` / `CREDIT` (enum — prévoir CHECK `amount > 0` en base) |
| `ledger/domain/TransactionType.java` | `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `FX_TRANSFER`, `EXTERNAL_TRANSFER`, `FEE`, `INTEREST`, `REVERSAL` |
| `ledger/domain/TransactionStatus.java` | `PENDING`, `COMPLETED`, `FAILED`, `REVERSED` |
| `ledger/application/LedgerService.java` | `LedgerService.post(transaction)` — deux écritures + mise à jour du solde — `@Transactional` |
| `ledger/application/LedgerVerifierService.java` | Recalcule la chaîne de hachages — `GET /admin/ledger/verify` (ADMIN/AUDITOR) — renvoie liste des anomalies |
| `ledger/repository/FinancialTransactionRepository.java` | `findByReferenceUnique`, `findByStatus(PENDING)` |
| `ledger/repository/LedgerEntryRepository.java` | `findByAccountIdOrderById`, requêtes natives pour ca-led-04 |
| `ledger/application/MoneyMapper.java` | `Money` ↔ `BigDecimal` (ledger étant le module financier) |
| `ledger/application/LedgerHasher.java` | Compute `entryHash` + `prevHash` — utile dans tests (CA-LED-03) |
| `ledger/infra/NativeLedgerRepository.java` — adapts aux requêtes SQL natives pour CA-LED-02 (trigger) et CA-LED-04 | Adaptations JPA / requêtes natives |

**Module `payment`** :

| Fichier | Rôle |
|---|---|
| `payment/api/DepositController.java` | `POST /api/v1/deposits` (TELLER) — body `DepositRequest(accountId, amount, currency)` + `Idempotency-Key` header |
| `payment/api/WithdrawalController.java` | `POST /api/v1/withdrawals` (TELLER) — body `WithdrawalRequest` — plafond + solde — 50 000 XOF — retourner 422 `INSUFFICIENT_FUNDS` à partir de `AuthService.register()`... |
| `payment/application/DepositService.java` | `deposit(request, accountId)` : vérif solde du compte (CASH_VAULT), pre-check (RG-DEP-01 à 05) |
| `payment/application/WithdrawalService.java` | `withdrawal(request)` : vérif solde, pre-check (RG-DEP-02 à 05) |
| `payment/application/FeeService.java` | Calcule les frais retrait (0,3 % minimum 100 XOF / maximum 3000 XOF) — pour Phase 6 (RG-TRF-03 — clamp + arrondi) |
| `payment/repository/IdempotencyRepository.java` | `findOrInsert(userId, key, endpoint, requestHash, responseHash)` + `save` — UNIQUE(user_id, idempotency_key) — tentative d'INSERT |
| `payment/domain/IdempotencyRecord.java` | `status(PENDING/COMPLETED)`, `responseBody (JSONB)`, `expiresAt` (24 h) |
| `payment/infrastructure/IdempotencyService.java` | Service idempotent — à adapter après les tests de concurrence (Phase 6) |

**Migrations** :

| Migration | Contenu |
|---|---|
| `V4__create_account.sql` | Table `account` (§6.1) + FK + checks + index (§6.2) |
| `V5__create_financial_transaction.sql` | Table `financial_transaction` + index (transactionId) |
| `V6__create_ledger_entry.sql` | Table `ledger_entry` (BIGSERIAL id) + index (account_id, id) + index (transaction_id) |
| `V7__ledger_immuabilite_triggers.sql` | Triggers PostgreSQL interdisant `UPDATE` et `DELETE` sur `ledger_entry` (exception explicite — CA-LED-02) |
| `V8__seed_system_accounts.sql` | Seed des comptes systèmes (§4.5) |

## 5.6 Pièges à surveiller

1. **Verrouillage des comptes (RG-LED-06)** : `SELECT … FOR UPDATE` sur les comptes uniquement (pas sur `ledger_entry` — pas de compte FK isolé — modifier uniquement les soldes via JPA EntityManager.persist).
2. **Calcul du solde mis à jour** (RG-LED-04) : `balanceAfter = account.balance + crédit − débit` calculé **dans** le hook PostgreSQL ou manuellement en Java (pas de `getBalance().add()` existant — le solde est caché) — en code Java via la lecture avant écriture (`MmontantCredit - montantDebit`)... correction : la convention : solde = Σ crédits − Σ débits — on calcule `newBalance = oldBalance + credit - debit` — savoir que le solde d'un compte client ne doit jamais être négatif — **plus simple de faire la sommation sur le niveau du grand livre — mais attention à la concurrence — solution proposée : en JPQL recompute sum sur `ledger_entry` dans la transaction — optimisé par l'index `(account_id, id)`**. |
| **Immuabilité des écritures (RG-LED-03)** : triggers PostgreSQL dans migration — pour tester, même désactiver — vérifier la base PostgreSQL — plutôt sur un `UPDATE` via `psql` direct — à noter : `ledger_entry` — ne jamais avoir l'app `findOne` puis `save()` — vérifier avec `Update attempt` via `psql`. |
| **Hachage dans tests (CA-LED-03)** : `LedgerHasher.hash(prevHash, transactionId, accountId, direction, amount, currency, createdAt)` — SHA-256 hex 64 chars — test : calculer manuellement un hachage connu et le comparer (un truc déterministe). |
| **Concurrence des virements (CA-TRF-02)** : pattern countDownLatch à réutiliser — ExecutorService — liée — attention à faire `list.get(0).complete()` — 50 virements concurrents — à reproduire dans `TransferServiceTest` Phase 6. |

## 5.7 Questions d'entretien — Phase 5

**Q1. Comment représenter l'entité `LedgerEntry` immuable ?**
Un `Entity` sans mutables except `balanceAfter` — utilisation d'un proxy pattern pour l'auto — on met à jour uniquement `balanceAfter` à l'instant de l'ajout — le test `Update attempt via psql` — vérifier le trigger. |

**Q2. Comment représenter le verrouillage (RG-LED-06) ?**
`ORDER BY id` → récupération `ORDERED` via JPA/Hibernate — si 2 transactions arrivent, elles acquièrent les comptes en ordre : le blocage se produit — checker le "deadlock avoidance" — **Schéma standard —** Plutôt qu'un `SELECT … FOR UPDATE` sur les `accounts` avant toute écriture — en **JPQL** — avec `<ORDER BY id>`. |

**Q3. Comment représenter le test de l'équilibre (CA-LED-01/02)?**
Tests : écriture déséquilibrée via `psql` — le trigger PostgreSQL — plutôt sur un `UPDATE` via `psql` direct — je partage — ATTENDS — alternative : fake seed — plus rapide — voir ? |

**Q4. Comment représenter le chaînage de hachages (RG-LED-05)?**
`entryHash = SHA-256(prevHash ‖ transactionId ‖ accountId ‖ direction ‖ amount ‖ currency ‖ createdAt)` — plus prochain ref — à décider — `prevHash` initial = 64 zéros — mémoriser la valeur par compte dans un hashMap ou requête à chacun — mais attends, on ne modifie JAMAIS — on l'injecte manuellement à la prochaine écriture via `LedgerEntry.lastHash(accountId)` — requête SQL à modifier manuellement — voir planification — je modifierai cela après l'exécution. |

**Q5. Comment calculer un solde actuellement (RG-LED-07)?**
C'est déjà fait pour quand on vérifie `Σ crédits − Σ débits` — attention à la compatibilité de la base (eager load / jdbc — en fait — préférer `query JPQL` sur `ledger_entry` — à tester sur la base — un bon cas démo de "reconciliation") |

## 5.8 Exercice pour l'apprentissage (Phase 5)

1. Ajoute un test : `doit_refuser_le_depot_si_le_compte_est_cloture` — vérifie `ACCOUNT_NOT_ACTIVE` (403? 409? — voir la table — actuellement 409 per chunk of the cahier) — voir s'il faut reloader du lourd (désactiver l'account dans test) — je repasserai là-dessus après vérif du statut (Frozen vs Closed) — prévoir `AccountFrozenClosedException`.
2. Exercice supplier (relié à Phase 6 — attention : est-ce que le timing ça marche ?) : expire d'une erreur (un injection de `TransactionFeeException` via un `LedgerVerifierServiceTest` — vérifier qu'on détecte `LedgerUnbalanced` — le test d'ajustement se fait — FAUX dans un 1er passage (écriture actuelle — prévoir une séquence — séparé de PostgreSQL — surtout pas de mocks — TESTS AVEC LA BASE VRAIE).
3. Bonus : désactive le trigger via `psql` — `ALTER TABLE ledger_entry DISABLE TRIGGER ALL` (PostgreSQL) — tentative de `UPDATE` — vérifie que l'erreur de l'app n'interdit pas le `UPDATE` (attention — PAS de risque — on l'activa après) — Résumé : la migration PostgreSQL re-démarre — voir "test - green with a new migration".
