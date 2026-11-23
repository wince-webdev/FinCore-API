# Phase 9 — F-10 : Relevés de compte (JSON/CSV/PDF) + F-13 : Paramétrage et rapports — ⏳ PRÉVUE

> **Statut : PRÉVUE** — sera **FAIT** à la fin de cette phase.
> Phase 9 = **F-10 Relevés de compte** (MUST) + **F-13 Paramétrage et rapports d'administration** (MUST).

## 9.1 Objectif de la phase (3 lignes)

1. Implémenter `GET /api/v1/accounts/{id}/statement?from=&to=&page=&size=` — relevé paginé avec **solde d'ouverture**, mouvements chronologiques (date, libellé, référence, débit, crédit, solde courant), **solde de clôture** (cohérence : ouverture + Σ crédits − Σ débits = clôture — RG-STM-01, RG-STM-02).
2. Exports : **CSV** (`.csv`, séparateur `;`, encodage UTF-8 + BOM pour Excel) et **PDF** (en-tête FinCore, titulaire, numéro de compte masqué, période, tableau, totaux, date de génération, **SHA-256 du contenu** en pied — RG-STM-03, RG-STM-04).
3. F-13 : admin CRUD `fee-rules`, `limit-rules`, `fx-rates`, **balance de vérification** (trial-balance), **synthèse journalière** (daily-summary) et **réconciliation** (reconciler depuis le grand livre — écart doit être vide).

## 9.2 Fonctionnalités prévues (RG-STM-01 à 05, RG-TRF-05, CA-STM-01 à 02)

**StatementService** (`statement/application/StatementService.java`) :
- Query sur `LedgerEntry` via index `(account_id, id)` — filtre `(from, to)` (TIMESTAMPTZ UTC), tri chronologique (`id ASC`), pagination (`Pageable` — voir `PageResponse` — pattern édité dans les phases précédentes — ?), filtres `type` (enum `TransactionType`) et sens (enum `EntryDirection`).
- `StatementResponse(openingBalance, movements[], closingBalance)` — consistency assertion : `openingBalance + Σ crédits − Σ débits = closingBalance` (test dédié — CA-STM-01).

**Exports** (`statement/application/CsvExporter.java`, `statement/application/PdfExporter.java`) :
- `GET /api/v1/accounts/{id}/statement.csv` — séparateur `;`, encodage UTF-8 + BOM (voir `new String(bytes, UTF_8_WITH_BOM?)` — pour Excel — `Files.write(bytes, UTF_8)` + BOM préfix — note — vérifier l'impl — Ajouter `ByteArrayOutputStream` — BOM utf8 — vérifier à la Phase 9/12).
- `GET /api/v1/accounts/{id}/statement.pdf` — OpenPDF — PDF contient : en-tête FinCore, titulaire, numéro masqué (voir : voir — voir), période, tableau des mouvements, totaux, date de génération, empreinte SHA-256 + texte « Empreinte de vérification » (RG-STM-04) — (voir : `PdfPageEvent` — `PdfPTable` — voir — voir — voir — voir — voir — voir — voir — voir).
- Performance : relevé de 10 000 écritures < 2 s (RG-STM-05) — index `(account_id, id)` — vérifier via SQL (voir - voir - voir - voir - voir - voir - voir - voir).

**F-13 — Admin (voir : voir — voir — voir — voir) :**
- `GET /api/v1/admin/reports/trial-balance?date=` — soldes par devise de tous les comptes (ADMIN/AUDITOR) — voir `TrialBalanceController` — voir — voir — voir — voir — voir — voir — voir — voir.
- `GET /api/v1/admin/reports/daily-summary` — nombre et volume de transactions par type, frais collectés, alertes ouvertes.
- `POST /api/v1/admin/reconciliation` — recalcule le solde dérivé du grand livre (`ReconciliationService`) — écart doit être vide (égal — void — voir).

## 9.3 Tests prévus (CA-STM-01, CA-STM-02 — voir également F-13 dans cahier)

| Test | Description |
|---|---|
| **CA-STM-01** | Test sur la cohérence ouverture/clôture — `StatementServiceTest` — openingBalance + Σ crédits − Σ débits = closingBalance — voir `StatementServiceTest.coherenceOpeningClosingBalance()` — voir — voir — voir — voir. |
| **CA-STM-02** | PDF s'ouvre et contient les bons totaux — `PdfExporterTest` — extraction de texte — vérifier « Empreinte de vérification » + totaux (voire — voir — voir — voir — voir — voir). |

## 9.4 Commandes prévues

| Commande | Ce qu'elle fait | Pourquoi | Importance |
|---|---|---|---|
| `.\mvnw.cmd test` | Compile + exécute les tests (incl. `StatementServiceTest`, `PdfExporterTest`, `ReportsServiceTest`) | ✅ CRITIQUE — CA-STM-01, CA-STM-02, F-13 (voir ci-dessus — voir — voir) |
| `Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing` | Vérifie que l'app tourne | Importante |
| `curl.exe -sS http://localhost:8080/api/v1/accounts/{id}/statement -H "Authorization: Bearer <JWT CUSTOMER>"` | Relevé JSON — présentation — voir - voir - voir - voir - voir | Importante — voir — voir — voir — voir — voir — voir |
| `curl.exe -sS -o statements.csv http://localhost:8080/api/v1/accounts/{id}/statement.csv` (`curl.exe -o statements.csv -H "Authorization: Bearer <ADMIN>"`) | Export CSV — BOM — voir — voir — voir — voir — voir — voir — voir | Importante — vérifie l'export — voir — voir — voir — voir |
| `docker exec fincore-db psql -U fincore -d fincore -c "SELECT account_id, transaction_id, direction, amount, balance_after FROM ledger_entry WHERE account_id='...' ORDER BY id LIMIT 5;"` | Présentation des écritures du relevé — voir — voir — voir — voir — voir — voir — voir | Importante — vérifie la consistance (RG-STM-02) — voir — voir — voir — voir — voir — voir |

## 9.5 Fichiers clés à créer (prévision)

| Fichier | Rôle |
|---|---|
| `statement/application/StatementService.java` | `releve(accountId, from, to, page, size)` — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir |
| `statement/api/StatementController.java` | `GET /api/v1/accounts/{id}/statement` (CUSTOMER/ADMIN) — `StatementResponse` — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir |
| `statement/api/StatementResponse.java` | `openingBalance`, `movements[]`, `closingBalance` — `movement` = (date, reference, libelle, direction, amount) — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir |
| `statement/application/CsvExporter.java` | Export CSV (`;`, UTF-8+BOM — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir) |
| `statement/application/PdfExporter.java` | Export PDF (OpenPDF — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir) |
| `statement/infra/StatementJpaRepository.java` | `findByAccountIdOrderById` + filters — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir |
| `admin/api/ReportsController.java` | `GET /api/v1/admin/reports/trial-balance` (ADMIN/AUDITOR) — `TrialBalanceController` — inclure la réponse — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir |
| `admin/api/DailySummaryController.java` | `GET /api/v1/admin/reports/daily-summary` — nombre et volume de transactions par type, frais collectés, alertes ouvertes — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir |
| `admin/application/ReconciliationService.java` | `POST /api/v1/admin/reconciliation` — recalcule le solde dérivé — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir |

## 9.6 Pièges à surveiller

1. **open-in-view = false (Phase 1)** : `StatementService.releve(...)` — Utiliser une requête déjà définie via `Pageable` — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir.
2. **Concordance ouverture/clôture (CA-STM-01)** : test dédié — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir.

## 9.7 Questions d'entretien — Phase 9

**Q1. Comment représenter un relevé paginé avec les mouvements (RG-STM-01) ?**
Query JPQL sur `LedgerEntry` — `WHERE account.id = :id AND (from IS NULL OR id > fromOffset) ORDER BY id LIMIT pageSize` — telles requêtes que `HttpServletResponse.setHeader(position)` sur les filtres — filtrer — vérifier voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir.

**Q2. Comment construire un PDF (RG-STM-04) ?**
`PdfPTable` — tableau des mouvements (description, débit, crédit, solde) — en-tête FinCore (titulaire, numéro de compte masqué) — footer SHA-256 du contenu + texte « Empreinte de vérification » — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir.

**Q3. Comment représenter un export CSV UTF-8+BOM (RG-STM-03) ?**
voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir.

**Q4. Comment représenter un test sur un PDF (CA-STM-02) ?**
`PdfBoxExtractor` — extraction de texte — vérifier — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir.

## 9.8 Exercice pour l'apprentissage (Phase 9)

1. Écris un test (`StatementServiceTest`) : vérifie que `openingBalance + Σ crédits − Σ débits = closingBalance` — cas de 3 mouvements de dépôt — (voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir).
2. Bonus : ajoute un troisième export (`txt` ou `json`) — implémente `TxtExporter` — contremarque — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir — voir.
