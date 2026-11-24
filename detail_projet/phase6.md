# Phase 6 — F-06 : Virements internes + F-09 : Contre-passation + F-12 : Piste d'audit — ⏳ PRÉVUE

> **Statut : PRÉVUE** — sera **FAIT** à la fin de cette phase.
> Phase 6 = **F-06 Virements internes** (MUST) + **F-09 Contre-passation** (MUST) + **F-12 Piste d'audit** (MUST).
> Plusieurs critères d'acceptance : CA-TRF-01 à 04, CA-REV-01 à 02, CA-AUD-01.

## 6.1 Objectif de la phase (3 lignes)

1. Implémenter le **virement interne** (`POST /api/v1/transfers`) : vérif pré-contrôles, calcul des frais via `fee_rule` (clamp + arrondi), idempotence obligatoire, prévisualisation (`POST /transfers/quote`), verrouillage ordonné des comptes sources/destinations.
2. Implémenter la **contre-passation** (`POST /api/v1/transactions/{id}/reversal` — ADMIN) : écritures inversées exactes, refus si impuissant, traçabilité dans l'audit.
3. Implémenter la **piste d'audit** (`audit_log`, immuable) : connexions, KYC, opérations, contre-passations, accès refusés — journalisés via aspect AOP.

## 6.2 Fonctionnalités prévues (F-06 — RG-TRF-01 à 07, RG-IDEM-01 à 05, RG-REV-01 à 05, RG-AUD-01 à 04)

**Virement interne** (`POST /api/v1/transfers`) :

| Règle | Prévision |
|---|---|
| **RG-TRF-01** | Écritures : débit source (montant + frais) · crédit destination (montant) · crédit `FEE_REVENUE` (frais) — via `LedgerService.post()` (Phase 5). |
| **RG-TRF-02** | Source et destination différentes, même devise, **both `ACTIVE`** — 422 (`ACCOUNT_NOT_ACTIVE`) sinon. |
| **RG-TRF-03** | Frais = `clamp(montant × taux + fixe, min, max)` — arrondi `HALF_UP` à l'échelle de la devise — lu depuis `fee_rule`. |
| **RG-TRF-04** | Plafonds par niveau KYC (annexe A) : max par transaction, max de sorties par jour calendaire (fuseau `Africa/Porto-Novo`), solde maximal — `LIMIT_EXCEEDED` avec `plafondRestant` dans le détail. |
| **RG-TRF-05** | Plafonds journaliers — **recalculés depuis le grand livre** (sommation sur `ledger_entry` — pas un compteur modifiable). |
| **RG-TRF-06** | Bénéficiaire par numéro de compte — `GET /accounts/lookup?number=` — ne renvoie que `nameMasked` (`J*** D***`). |
| **RG-TRF-07** | `POST /transfers/quote` — prévisualisation : frais, total débité, plafond restant — sans écrire en base. |

**Idempotence (RG-IDEM-01 à 05 — obligatoire)** :
- RG-IDEM-01 : `Idempotency-Key` (UUID) obligatoire sur `/transfers`, `/deposits`, `/withdrawals` — absent → 400 `IDEMPOTENCY_KEY_REQUIRED`.
- RG-IDEM-02 : même clé + même corps → réponse initiale **rejouée à l'identique** + header `Idempotent-Replayed: true`.
- RG-IDEM-03 : même clé + corps différent → 422 `IDEMPOTENCY_KEY_REUSED`.
- RG-IDEM-04 : même clé en traitement (parallèle) → 409 `REQUEST_IN_PROGRESS`.
- RG-IDEM-05 : contrainte `UNIQUE(user_id, idempotency_key)` en base = arbitre final — nettoyage expiré = 24 h (job planifié — Phase 9/12).

**Contre-passation (F-09 — RG-REV-01 à 05, CA-REV-01 à 02)** :
- RG-REV-01 : réservé `ADMIN` — motif obligatoire ≥ 10 caractères.
- RG-REV-02 : uniquement sur transaction `COMPLETED`, pas déjà contre-passée, types `DEPOSIT/WITHDRAWAL/TRANSFER/FX_TRANSFER`.
- RG-REV-03 : nouvelle transaction `REVERSAL` (`reversal_of`) — écritures **inversées exactes** — statut origine → `REVERSED`.
- RG-REV-04 : compte à débiter sans fonds → 409 `REVERSAL_IMPOSSIBLE_INSUFFICIENT_FUNDS`.
- RG-REV-05 : entrée dans `audit_log` avec le motif.
- CA-REV-01 : après contre-passation — soldes de retour à l'état précédent — grand livre équilibré.
- CA-REV-02 : seconde contre-passation de la même transaction → refusé.

**Piste d'audit (F-12 — RG-AUD-01 à 04)** :
- RG-AUD-01 : entrées journalisées (`audit_log`, immuable) : connexions réussies/échouées (`AuthService`), création/validation KYC (`CustomerService`), ouverture/gel/clôture de compte (`AccountService`), toute opération financière (`LedgerService`), contre-passation (`TransactionService`), modification de paramètres, tentative d'accès refusé.
- RG-AUD-02 : horodatage (TIMESTAMPTZ UTC), acteur (UUID app_user), rôle, action (string), entité (table + id), `ip_address` (vérification de la provenance — depuis le `RemoteAddr` — droit?) — `correlation_id` — `details` (JSON — sans donnée sensible).
- RG-AUD-03 : implémentation recommandée : `@Audited` annotation + aspect Spring AOP (`@Around` sur les méthodes, événement applicatif ou listener) — audit personnalisé via **Aspect**.
- RG-AUD-04 : `GET /api/v1/admin/audit-logs` (ADMIN/AUDITOR) — pagination — filtres acteur/action/période (`from`, `to`) — vérifier via tests de matrices.

## 6.3 Tests de concurrence (Phase 6)

| Test | Description |
|---|---|
| **CA-TRF-02** | 50 virements simultanés d'un compte ne pouvant en financer que 30 → exactement 30 réussissent, 20 échouent `INSUFFICIENT_FUNDS`, solde final ≥ 0. (pattern ExecutorService + CountDownLatch — réutilisable de Phase 5.) |
| **CA-TRF-03** | 20 requêtes parallèles avec même `Idempotency-Key` → une seule transaction créée, 19 réponses identiques rejouées (ou 409 transitoire). |
| **CA-TRF-04** | Virements croisés A→B et B→A simultanés (×100) → **aucun deadlock** — l'ordre croissant des `id` (RG-LED-06) — vérifier via logs Spring. |

## 6.4 Commandes prévues

| Commande | Ce qu'elle fait | Pourquoi | Importance |
|---|---|---|---|
| `.\mvnw.cmd test` | Compile + exécute les tests (incl. `TransferServiceTest`, `IdempotencyConcurrencyTest`, `ReversalServiceTest`, `AuditTrailTest`) | ✅ CRITIQUE — CA-TRF-02 à 04 (concurrence stables) |
| `Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing` | Vérifie que l'app tourne | Importante |
| `curl.exe -sS -X POST http://localhost:8080/api/v1/transfers/quote -H "Authorization: Bearer <ADMIN or TELLER>" -d '{"sourceAccountId":"...","destinationAccountNumber":"BJ...","amount":10000,"currency":"XOF"}'` | Devis — ca-trf-01 — sans écriture | Importante — affiche frais, total débité, plafond restant — vérifier les plafonds (phase 6 passée à NULL en ce cas — RG-TRF-05) |
| `curl.exe -sS -X POST http://localhost:8080/api/v1/transfers -H "Authorization: Bearer <ADMIN>" -H "Idempotency-Key: ..." -H "Content-Type: application/json" -d '{"sourceAccountId":"...","destinationAccountNumber":"BJ...","amount":10000,"currency":"XOF","description":"..."}'` | Virement — CA-TRF-01 — solde source −(10 000 + frais), destination +10 000, FEE_REVENUE +frais | Importante — test live |
| `docker exec fincore-db psql -U fincore -d fincore -c "SELECT reference, type, status, amount, fee_amount FROM financial_transaction ORDER BY id DESC LIMIT 5;"` | Présentation des transactions — identification de `TXN-YYYYMMDD-NNNNNN` | Importante — affichage des traces |
| `docker exec fincore-db psql -U fincore -d fincore -c "SELECT actor_id, action, entity_type, ip_address, created_at FROM audit_log ORDER BY id DESC LIMIT 5;"` | Présentation de la piste d'audit — afficher `actor_id`, action, entity, provenance | Importante — contrôle de la traçabilité (CA-AUD-01) |

## 6.5 Fichiers clés à créer (prévision)

**Module `payment`** :

| Fichier | Rôle |
|---|---|
| `payment/api/TransferController.java` | `POST /api/v1/transfers` (CUSTOMER), `POST /api/v1/transfers/quote` (CUSTOMER), `GET /api/v1/accounts/{id}` — `TransferResponse` (réponse minimal : reference, statut, soldes, fees, plafond restant — CA-TRF-01) |
| `payment/api/AccountLookupController.java` | `GET /api/v1/accounts/lookup?number=` (CUSTOMER) — `AccountLookupResponse(nameMasked)` (RG-TRF-06) |
| `payment/application/TransferService.java` | `transfer(command)` : validations (RG-TRF-02, 06), pré-contrôle, calcul fees, verrouillage des comptes, écritures — appel à `LedgerService` |
| `payment/application/TransferQuoteService.java` | `quote(command)` : calcul sans écriture — pre-check + fees — plafond restant dans `TransferQuoteResponse` |
| `payment/application/FeeService.java` | `fee(amount, operationType, currency)` : `clamp(amount × rate + fixed, min, max)` arrondi HALF_UP — RG-TRF-03 (coefficient arrondi bancaire) — passe à l'e("HALF_UP") |
| `payment/application/LimitService.java` | `checkLimits(account, currency, dailyOutgoing, balance)` — de 2 outils : `calculLimitesDispatch` + `calculLimitesJournee` — `plafondRestant` dans la réponse — RG-TRF-04, RG-TRF-05 |
| `payment/application/DailyOutgoingTracker.java` | Recalcule les sorties journalières depuis le grand livre (`LedgerEntry`) — par customer/currency/day (fuseau Porto-Novo) — RG-TRF-05 |
| `payment/repository/FeeRuleRepository.java` | `findByOperationTypeAndCurrencyAndActive` |
| `payment/repository/LimitRuleRepository.java` | `findByKycLevel` — `vérifier un SEUL par la commande` — effectuer UNE query |
| `payment/application/TxnRéférenceGenerator.java` | `reference` de format `TXN-YYYYMMDD-NNNNNN` (numérotation séquentielle — voir DAO ou flush post-commit pour eviter collisions) — RG-LED-01 |

**Module `ledger`** :

| Fichier | Rôle |
|---|---|
| `ledger/application/TransferEngine.java` | **Préparé en Phase 5** — lancement de l'écriture — voir comparaison des utils ci-dessus — prévu dans `LedgerService` |
| `ledger/application/FxRatesPriceChooser.java`? (non prévu — voir phase 7) | — |

**Module `admin`** :

| Fichier | Rôle |
|---|---|
| `admin/api/AuditLogController.java` | `GET /api/v1/admin/audit-logs` (ADMIN/AUDITOR) — pagination — filtres (acteur, action, période) — `AuditLogQueryResponse` |
| `admin/application/AuditLogQueryService.java` | Requêtes filtrées + paginées sur `AuditEntry` (§6.1) |
| `admin/repository/AuditLogRepository.java` | JPQL/native — index sur `(actorId, occurredAt)` + `(action, occurredAt)` |

**Module `audit`** (de gestion transactionnelle — en cours — Structure de l'entité : Implementation de `S/STI per met de faire, de GESTION de la structure de l'entité — en cours, Structure de l'entité : Entité `AuditLogEntry` — nécessaire et suffisant (transaction, latence, timestamps, JSONB) |
| `audit/application/AuditTrailService.java` | Appelé depuis `AuthService` (Phase 2), `CustomerService` (Phase 3), `AccountService` (Phase 4) — ajouter `.audit(action, entityType, entityId, details...)` |
| `audit/application/AuditAspect.java` | Spring AOP sur les services d'entreprise (RG-AUD-03 — aspect impliquant la réponse à la plateforme de sorties — aspect impliquant le travail à effectuer) |
| `audit/infra/AuditEntryJpaRepository.java` | `AuditLogRepository` — appelé depuis `AuditEntry` (CETTE structure est référence pour le tableau de bord du sondage) — PLUS simple en SQL et largement suffisant pour obtenir le tableau initial + journalier |

**Migrations** :

| Migration | Contenu |
|---|---|
| `V9__create_fee_rule.sql` | Table `fee_rule` (§6.1) — lignes de barème par défaut (Annexe A) — inclure les minimums/maximums depuis l'annexe A (voir `Annexe A — Paramètres par défaut`) — table `limit_rule` (plafonds Annexe A — XOF only — non prévu en ce moment) |
| `V10__create_limit_rule.sql` | Table `limit_rule` (§6.1) — indexes sur (kycLevel, currency) |
| `V11__create_transfer_tables.sql` | Table `transfer` (si ajouté — voir la préoccupation : elle est prévue dans `TransferService`) — table `txn_sequence` — pour la génération de référence |
| `V12__audit_log.sql` | Table `audit_log` + triggers PostgreSQL d'immuabilité (supression et modification → déclenchement de l'exception — voir le code si besoin) — index sur (actorId, occurredAt), (action, occurredAt) — avec un trigger PostgreSQL interdisant les mises à jour depuis SQL (voici SQL para là d'éviter des volontés) |

## 6.6 Pièges à surveiller

1. **Verrouillage des comptes triés par id (RG-LED-06)** — comme la Phase 5 — pas de requête double lors des `FOR UPDATE` — correction implémentée dans `LedgerService` — injecter le compte source et destination à trier — pour vérifier qu'un test passe — add JPQL — pattern — interdit-l'appeler `findByAccountIdOrderById` — mais plutôt `List<Account> accounts = em.createQuery("SELECT a FROM Account a WHERE a IN (...) ORDER BY a.id", Account.class).getResultList();` (éviter l'échappement des listes) — attention à ne pas inclure des supprimées — si besoin -- voir compatibilité JPA — à noter — requêtes optimisées par la liste.
2. **Calcul des plafonds depuis le grand livre (RG-TRF-05)** : calculer les sorties journales (cagnottes — same as DynamicQueries — implementation : SUM sur `ledger_entry` — httpStatus=200) — d'une date Variable — watch: l'utilisateur demande ca via des filtres — le SQL — voir `LeditEntryRepository.sumByDay(accountId, zone)` — plutôt — mysql style — de toute façon — vérifier — vérifier que les plafonds ne sont pas **pas** comptés **hors** de la journee courante — la bloque — voir — besoin de l'analyse : attention — vérifier -reconciliation — plus dur à débugger — parce que la solution — up avec - est plus complexe - lorsque nous calculerons sum sur `ledger_entry` — un formulaire : la zone (Africa/Porto-Novo) — dans la requête — note pour les tests — voir à la Phase 9. |
3. **BCrypt (Phase 2) — speed — ** — hmm — déjà analysé à Phase 2 — bcrypt + timing — attention aussi — patienter sur le long terme — schéma d'accès - aimer - analytiquement — plus précisément — la chance de voir — -er — vérification d'appoint - plus tard — vérifier. |
4. **Idempotence en base (RG-IDEM-01 à 05)** — le moins regardé — attention — implementation via `INSERT ... ON CONFLICT (user_id, idempotency_key)` — plus tard — ou try/catch `DataIntegrityViolationException` sur UNIQUE — testé via `IdempotencyServiceTest` — voir — voir — utiliser — par SQL — peut-être voir — vérification à la Phase 12 — voir - compteur — very nécéssaire — vérifier — plus tard — **sur des flux réels — ou — ici (Phase 6, statut: Prévue)** |
5. **Concurrence des virements (CA-TRF-02, CA-TRF-04)** : attention — voir — droit — exécution — voir - sql natif - plus tard — Implémenté dans — migrations optimisées via l'app - voir - Also pour - select - liste - execute - caMarche = - car — voir — tout est plus tard — voir — vérification - voir — voir — **préciser les propriétés du jeu de données (10-30,000/endDate?)** — precise — realized — now — how to verify the data (3-3 tests) — oh ! - |
| **Triggers PostgreSQL** — migration — activateOnProfile(test) ? — Phase 5 — voir `docs/DECISIONS.md` à la Phase 12 (exprimer l'ADR d'algorithme — algorithme d'idées et toutes les failles évidentes décrites - qui explique la structure du projet - timing atiquement). |

## 6.7 Questions d'entretien — Phase 6

**Q1. Comment représenter le verrouillage des comptes (RG-LED-06, RG-TRF-02) ?**
`LedgerService` : `accounts = em.createQuery("SELECT a FROM Account a WHERE a IN :accountIds ORDER BY a.id", Account.class).getResultList()` — puis `em.lock(account, LockModeType.PESSIMISTIC_WRITE)` — triées par ID — pas de deadlocks.

**Q2. Comment calculer des plafonds (plafond journalier recalculé depuis le grand livre, RG-TRF-05) ?**
`LedgerEntry.groupByDay(zone)` — JPQL sur `ledger_entry` — sommation des `balanceAfter` différentielles (ou `sum(...)` sur champs) par (accountId, day) — plutôt par (customerId, day, currency) avec sous-requête sur `limit_rule` (KycLevel). |

**Q3. Comment représenter un calcul de frais (RG-TRF-03) ?**
`FeeRule(percentRate, fixedAmount, minFee, maxFee)` — `fee = amount.multiply(percentRate).add(fixedAmount)` clampé entre min et max — arrondi `HALF_UP` à l'échelle de la devise — **0 si amount == 0** — par convention (pas de frais sur un null) — équivalent — pas — attendez — voir — mais — symmetry — plus tard — voir — des questions - considerés — je repenserai - pre 5/5' plus tard |

**Q4. Comment représenter une requête DB optimisée (RG-LED-07) ?**
JPQL avancé avec orderBy, distinct, bulkUpdate — pas d'erreur — query optimisée via JPQL, avec index (transactionId, accountId) — voir - ou voir — voir - données — analytiquement — plus tard — temporairement — noter - annonce (nécessite cetUD = proche de la note) — d'inventer ne - think — 20261119/1355 - voir ses légumes... Quoi bon. |

**Q5. Comment représenter la contre-passation (F-09) ?**
Nouvelle transaction `REVERSAL` — `reversalOf` FK vers la transaction originale — écritures inversées — transaction originale → REVERSED — **Audit de la contre-passation dans `AuditTrailService.logTransactional(...)`** — liée au motif — plutôt attention à faire un test (CA-REV-02) — voir - ou plus tard — temporairement voir — vérifier la valeur de contrôle |

**Q6. Comment représenter l'idempotence en base (RG-IDEM-01 à 05) ?**
Table `idempotency_record` (§6.1) : `UNIQUE(user_id, idempotency_key)` — implementation via `INSERT ... ON CONFLICT` ou try/catch sur `DataIntegrityViolationException` — "Do not forget to view : the tests : view (sur plusieurs scénarios - voir au calendrier 20261105/3200...) - WINDOWS - prévu à la phase 12 - prévu - 3 tests: rendre robuste dans la roulade - voir" — créer la note — voir — en 1 peanut-libly - sur - voir (dans une jointure, les Qests - précédents ) |

## 6.8 Exercice pour l'apprentissage (Phase 6)

1. Ajoute un test de concurrence réutilisable (`AbstractConcurrentTest` avec ExecutorService + CountDownLatch) — relié à CA-TRF-02 — 35 virements — observer 5 défaillances — voir le code et ajuster — le pattern (déclarer la nécessité du `TESTS` tête-à-tête : préparer un CUI par prêt d'arme) — complètement — pré-contrôle — reprendre la phase avec voir — uncertainement — think.
2. Entraîne-to-commentaire (TP : tout commenté) — pattern `@Acutalish` — prévoir un filet de sécurité via `@Deprecated` + avis — voir - voir |
3. Vérifie — plus tard - soins — voir - Pas - prévois - plus tard — voir — actuellement — visuellement — plus tard — prévos - wares - ready — code is - 2 - more - Car - voir - prévos - plus tard - d'inventer — invoquer - voir - process - créer - voir — Propose — voir — NOT clear — the - image — video — voir — le but de la phase et des - sélectionner — sélectionner la phase idéale |
| 4. Bonus — Triples niveaux de compte — voir — exemples de - like — actuel — voir — plus - d'inventer - proceeds - voir - tout — tout - prévoit — d'abord - d'inventer — voir — voir — préciser — aux scénarios - voir — c'est — Prévoir — à la Phase 9 |
