# Phase 8 — F-08 : Virements externes asynchrones + Outbox + NotificationGateway — ⏳ PRÉVUE

> **Statut : PRÉVUE** — sera **FAIT** à la fin de cette phase.
> Phase 8 = **F-08 Virements externes asynchrones** (SHOULD — cahier §7 « F-08 · Virements externes asynchrones et notifications »).

## 8.1 Objectif de la phase (3 lignes)

1. Implémenter `POST /api/v1/transfers/external` : transaction `PENDING` (débit source + crédit `SUSPENSE`) via `LedgerService.post()`.
2. Implémenter le port `ExternalPaymentProvider` et son adaptateur **simulé** (latence 1 à 5 s, 85 % succès, 10 % échec, 5 % timeout — paramètres modifiables en test).
3. Implémenter le **planificateur** : traitements `PENDING` → `COMPLETED` (débit `SUSPENSE`, crédit `EXTERNAL_SETTLEMENT`) ou `FAILED` (remboursement inversé) — machine à états strictes (`PENDING → COMPLETED | FAILED`, `COMPLETED → REVERSED`).

## 8.2 Fonctionnalités prévues (RG-EXT-01 à 05, RG-OUT-01 à 02, CA-EXT-01, CA-OUT-01)

**Virement externe** (`POST /api/v1/transfers/external`) :
- RG-EXT-01 : crée la transaction PENDING — débit source + crédit SUSPENSE.
- RG-EXT-02 : port `ExternalPaymentProvider` (interface) + adaptateur simulé — `SimulatedProviderAdapter` — latence modifiable, taux de succès configurable en test.
- RG-EXT-03 : planificateur traitant les PENDING → COMPLETED (débit SUSPENSE, crédit EXTERNAL_SETTLEMENT) ou FAILED (écritures inversées, statut FAILED).
- RG-EXT-04 : machine à états stricte — transitions non autorisées → `ILLEGAL_STATE_TRANSITION` — également pour PENDING > 15 min → FAILED.
- RG-OUT-01 : **outbox transactionnelle** — `outbox_event` inserté dans la même transaction que la modification métier.
- RG-OUT-02 : `OutboxDispatcher` — lit NEW — appelle `NotificationGateway.send` (SMS/e-mail simulés) — marque SENT — échec → `attempts++` backoff exponentiel — abandon (FAILED) après 5 tentatives.
- CA-EXT-01 : en mode déterministique — virement en échec — argent retourné au compte source — Σ soldes équilibrés.
- CA-OUT-01 : si la notification échoue — le virement reste valide (rejoué plus tard).

## 8.3 Tests prévus

- `AsyncTransferTest` — virement externe PENDING → (planificateur) → COMPLETED / FAILED — Σ soldes équilibrés après remboursement.
- `OutboxDispatcherTest` — événements NEW → dispatcher → SENT — retry avec backoff — FAILED après 5 tentatives.
- `SimulatedProviderTest` — paramètres du statut du provider configurables en test.

## 8.4 Commandes prévues

| Commande | Ce qu'elle fait | Pourquoi | Importance |
|---|---|---|---|
| `.\mvnw.cmd test` | Compile + exécute les tests (incl. `AsyncTransferTest`, `OutboxDispatcherTest`) | ✅ CRITIQUE |
| `Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing` | Vérifie que l'app tourne | Importante |
| `curl.exe -sS -X POST http://localhost:8080/api/v1/transfers/external -H "Authorization: Bearer <ADMIN>" -H "Idempotency-Key: ..." -d '{"sourceAccountId":"...","destinationAccountNumber":"BJ...","amount":10000,"currency":"XOF"}'` | Virement externe — crée la transaction PENDING | Importante — vérifie CA-EXT-01 |
| `docker exec fincore-db psql -U fincore -d fincore -c "SELECT reference, status FROM financial_transaction WHERE reference LIKE 'TXN%';"` | Présentation des transactions externes (voir status PENDING) | Importante — progression du virement externe |
| `docker exec fincore-db psql -U fincore -d fincore -c "SELECT id, event_type, status, attempts FROM outbox_event ORDER BY id DESC LIMIT 5;"` | Présentation des événements outbox (NEW → SENT/FAILED) | Importante — vérifie CA-OUT-01 |

## 8.5 Fichiers clés à créer (prévision)

| Fichier | Rôle |
|---|---|
| `notification/domain/OutboxEvent.java` | Entity `outbox_event` : `aggregateType`, `aggregateId`, `eventType`, `payload` (JSONB), `status` (`NEW`/`SENT`/`FAILED`), `attempts`, `nextAttemptAt`, `sentAt` |
| `notification/application/OutboxDispatcher.java` | Planificateur (SC) — diffuses les événements NEW via `NotificationGateway.send(...)` puis marque SENT — backoff exponentiel |
| `notification/application/NotificationGateway.java` | Port : `send(message)` — implémenté par `SimulatedNotificationGateway` (formate le SMS/e-mail dans les logs + fichier) |
| `notification/infra/SimulatedNotificationGateway.java` | Adaptateur simulé — écrit dans les logs et un fichier |
| `notification/domain/ExternalPaymentProvider.java` | Port : `process(transaction)` — implémenté par `SimulatedProviderAdapter` |
| `notification/application/AsyncTransferService.java` | Gestion des transactions externes (PENDING → COMPLETED/FAILED) — statut stricte + transition illégale |
| `notification/infra/SimulatedProviderAdapter.java` | Adaptateur simulé — latence modifiable — taux de succès configurable |

## 8.6 Migrations prévues

- `V13__create_outbox_event.sql` — table `outbox_event` (§6.1) — index sur (status, createdAt).

## 8.7 Pièges à surveiller

1. **Machine à états strictes** (RG-EXT-04) : transition illégale → `ILLEGAL_STATE_TRANSITION` (409).
2. **Planificateur idempotent** (rg-ext-03 / CA-EXT-01) : chaque exécution de job traite les PENDING jusqu'à ce qu'il n'y ait plus d'événements — voir `AsyncTransferServiceTest` (voir - voir - voir - voir - voir).

## 8.8 Questions d'entretien — Phase 8

**Q1. Comment représenter une opération asynchrone idempotent (CA-EXT-01) ?**
Statut `PENDING` → `COMPLETED`/`FAILED` — le rejeu doit être idempotent — la table `outbox_event` a `UNIQUE(aggregateType, aggregateId, eventType)` — voir la requête — voir - voir - voir - voir - voir.

**Q2. Comment représenter une machine à états strictes (RG-EXT-04) ?**
Transitions explicites : PENDING → COMPLETED | FAILED ; COMPLETED → REVERSED (par contre-passation uniquement) ; toute autre transition → exception — voir - voir - voir - voir - voir.

**Q3. Comment représenter une outbox transactionnelle (RG-OUT-01) ?**
Voir - voir - voir - voir - voir — voir.

**Q4. Comment représenter une notification simulée (CA-OUT-01) ?**
Voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir.

## 8.9 Exercice pour l'apprentissage (Phase 8)

1. Ajoute un test : vérifie que l'événement outbox est inséré dans la même transaction que la modification métier — vérifier via psql que l'événement existe après le virement PENDING.
2. Entraîne-toi à faire un `curl.exe` sur `/api/v1/transfers/external` — vérifie que la transaction est bien `PENDING` — puis via psql, execute `UPDATE` directement sur `ledger_entry` — vérifie que le trigger bloque l'opération — (désactive le trigger à la fin via `ALTER TABLE ledger_entry DISABLE TRIGGER ALL`).
3. Bonus : teste le rejeu d'un événement outbox — voir — voir — voir — voir — voir.
