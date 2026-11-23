# Phase 7 — F-07 : Multi-devises (taux de change) — ⏳ PRÉVUE

> **Statut : PRÉVUE** — sera **FAIT** à la fin de cette phase.
> Phase 7 = **F-07 Multi-devises** (SHOULD — cahier §7 « F-07 · Multi-devises et change »).

## 7.1 Objectif de la phase (3 lignes)

1. Implémenter les transferts multi-devises (`POST /api/v1/transfers/fx`) : transfert d'une devise A vers une devise B avec taux renvoyé à l'avance (RG-FX-01 à 03).
2. Implementer le **echange de devise** via le grand livre (FX_POSITION, écritures équilibrées par devise — RG-FX-04, RG-FX-05).
3. Vérifier que Σ de soldes par devise reste 0 (RG-FX-02, CA-FX-02) et que le solde de la devise est affiché par la requête de la réponse (section 4.7, F-03, Phase 4).

## 7.2 Fonctionnalités prévues (F-07 — RG-FX-01 à 04, CA-FX-01, CA-FX-02)

**Virement de change** :

| Règle | Prévision |
|---|---|
| **RG-FX-01** | `POST /api/v1/transfers/fx` : compte source (devise A) → compte destination (devise B) — au pré-contrôle via `LedgerService.post()` — vérification du statut `ACTIVE`. |
| **RG-FX-02** | Taux via `fx_rate` — le plus récent `valid_from ≤ now` (ORDER BY valid_from DESC, id DESC — double ORDER BY). |
| **RG-FX-03** | Équilibre par devise (gain/perte répartie — voir : la protection prévoit du matériel) — écritures équilibrées (sommes respectives — voir la requête ci-dessous). |
| **RG-FX-04** | Écritures équilibrées par devise : en devise A → débit source, crédit `FX_POSITION(A)` — en devise B → débit `FX_POSITION(B)`, crédit destination — frais éventuels vers `FEE_REVENUE` — via `LedgerService.post()` (RG-FX-04). |
| **RG-FX-05** | Taux et montant converti **figés dans la transaction** (stockés en `FXRate` dans `FinancialTransaction` ou table `fx_rate`) — relisibles plus tard (vérifier la requête de la requête). |

**Équilibre et vérification (CA-FX-02)** : Σ soldes par devise reste égal à 0 — test d'invariant avec `LedgerBalancer` — vérifier via SQL :

```sql
SELECT currency, SUM(balance_after) FROM ledger_entry WHERE transaction_id = 'TXN-YYYYMMDD-NNNNNN'
```

Exemple : `SELECT currency, sum(balance_after) FROM ledger_entry WHERE transaction_id='TXN-20261203-120675';`

**Quote de change (prévisualisation)** : `POST /api/v1/transfers/fx/quote` — affiche rate, spread_percent, montant reçu (RG-FX-03).

## 7.3 Echanger de devises (version simple — F-07 MVP)

1. Compte source (CURRENT, EUR, 4 000 XOF) → compte destination (CURRENT, EUR, 500 XOF) — transfert FX (Phase 5 transfer + FX_POSITION — Phase 6).
2. 4 XOF vers 10 XOF — plafonds de solde (Phase 4 — Phase 5 re-prioriser).

## 7.4 Commandes prévues

| Commande | Ce qu'elle fait | Pourquoi | Importance |
|---|---|---|---|
| `.\mvnw.cmd test` | Compile + exécute les tests (incl. `FxTransferServiceTest`, `FxRateTest`, `FxQuoteTest`) | ✅ CRITIQUE — CA-FX-01 (100 EUR → 65 595 XOF — voir Anonymes section §A.3) |
| `Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing` | Vérifie que l'app tourne | Importante |
| `curl.exe -sS -X POST http://localhost:8080/api/v1/transfers/fx/quote -H "Authorization: Bearer <JWT ADMIN>" -d '{"sourceAccountId":"...","destinationAccountNumber":"BJ...","amount":100,"currency":"EUR"}'` | Devise — ca-fx-01 — sans écriture | Importante — rate, marge, solde reçu — plafond restant |
| `curl.exe -sS -X POST http://localhost:8080/api/v1/transfers/fx -H "Authorization: Bearer <JWT ADMIN>" -H "Idempotency-Key: ..." -d '{"sourceAccountId":"...","destinationAccountNumber":"BJ...","amount":100,"currency":"EUR"}'` | Virement de change — ca-fx-01 — équilibre par devise | Importante — affiche la progression du solde — vérifier la requête de la requête de la requête : voir prévision de 4.7, F-03 |
| `docker exec fincore-db psql -U fincore -d fincore -c "SELECT currency, sum(balance_after) FROM ledger_entry WHERE transaction_id='TXN-20261203-120675';"` | Exercice — Σ soldes par devise (somme équilibrée — 0 = ∂✓ (vérifie CA-FX-02)) | Importante |
| `Invoke-WebRequest http://localhost:8080/api/v1/transactions/{transactionId} -UseBasicParsing` (`curl.exe -H "Authorization: Bearer <JWT ADMIN>"`) | Exercice — la transaction FX est reliée au taux (RG-FX-05) | Importante |

## 7.5 Fichiers clés à créer (prévision)

**Module `admin`** :

| Fichier | Rôle |
|---|---|
| `admin/domain/FxRate.java` | Entity `fx_rate` : `baseCurrency`, `quoteCurrency`, `rate` (Decimal(19,8)), `spreadPercent`, `validFrom`, `source` (string — voir : indiquer l'origine, e.g. "API", "C245F", "chimique center - voir A.3") |
| `admin/infra/FxRateJpaRepository.java` | `findTopByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqual` |
| `admin/api/FxRateController.java` | `GET /api/v1/admin/fx-rates` (ADMIN) — pagination — filtres (base, quote, validFrom) — `FxRateResponse` |
| `admin/application/FxRateService.java` | `latestRate(base, quote, now)` — requête : `ORDER BY validFrom DESC, id DESC` (double ORDER BY) — `quoteRate(base, quote, now)` — inclure l'équivalence fixe 1:655,957 (§RG-FX-02) — `EUR/XOF` par défaut : 1 EUR = 655,957 XOF (référence directe de §7, RG-FX-02 — complètement — voir §7RG-FX-02) |
| `admin/api/FxRateQuoteController.java` | `POST /api/v1/transfers/fx/quote` — vérifie les plafonds (RG-TRF-04) — sans écriture — renvoyant `QuoteResponse(rate, spread, amountReceived, fee, totalDebited, plafondRestant)` — pré-contrôles — voir compte de la requête au-dessus |

**Module `ledger`** :

| Fichier | Rôle |
|---|---|
| `ledger/domain/FxRate.java` | — voir admin — voir — voir — n'est pas prévue à la Phase 7, à la place — voir — à classer dans admin |
| `ledger/application/FxRateStorageService.java` | Stockage sur `fx_rate` — voir : stocker la transiction — voir — voir — voir — voir — voir — voir — voir |
| `ledger/application/TransferFxService.java` | `fxTransfer(command, user)` — vérifief per jet (RG-FX-03) — calculxf — écritureé — voir — voir - tout - voir - voir - voir - voir |
| `ledger/application/MoneyMapperFx.java` | Conversion xf : `amount.toFx(quote, rate, targetCurrency)` |

**Module `payment`** :

| Fichier | Rôle |
|---|---|
| `payment/application/TransferFxService.java` | Extension du `TransferService` (Phase 6) — vérifiefs — complète — voir — voir - voir — voir — voir - voir - voir - voir — voir - voir - voir - voir — voir - voir - voir - voir |
| `payment/api/TransferFxController.java` | `POST /api/v1/transfers/fx` — vérifiefs — complète — voir — voir - voir — voir — voir - voir - voir - voir |

## 7.6 Pièges à surveiller

1. **Équilibre par devise** (RG-FX-04) — vérifier que les écritures du contre-partie — débit du `FX_POSITION(B)` + crédit destination — vérifier à chaque écriture avec une requête — voir — voir - voir — voir - voir - voir - voir - voir |
2. **Σ soldes par devise (CA-FX-02)** : interroger via SQL : `SELECT currency, SUM(balance_after) FROM ledger_entry WHERE transaction_id='TXN-YYYYMMDD-NNNNNN';` |
3. **Rates : verrouillage des écritures (FxRate — RG-FX-05)** — figé dans la transaction — transaction table (FX_RATE_SNAPSHOT table voir DECISIONS à la Phase 12) — voir - voir - voir - voir - voir - voir - voir - voir |

## 7.7 Questions d'entretien — Phase 7

**Q1. Comment représenter un taux change fixe pré-configuré (1 EUR = 655,957 XOF — RG-FX-02) ?**
Table `fx_rate` — saisi en DB seed / chaque environnement — `rate(BaseCurrency EUR, quoteCurrency XOF, spreadPercent=1.0)` — vérifier /vérifier — contrainte : voir la raison pour laquelle — voir — bug - du - voir — vérification - du code — plus tard — voir - plus tard - voir - voir - voir - voir - voir |

**Q2. Comment représenter un taux variable (taux parameterisé - e.g. voir le cahier de charges du Rates) ?**
Table `fx_rate` avec `source = "API"` — mise à jour par planificateur (Phase 8 / Phase 10?) — voir — voir - voir - voir - voir - voir - voir - voir - voir - voir - voir - voir |

**Q3. Comment représenter un calcul d'équilibre par devise (RG-FX-04) ?**
Écriture via `LedgerService.post()` — `LedgerEntry` avec `direction=DEBIT` (source,FX_POSITION(A)) + `direction=CREDIT` (destination,FX_POSITION(B)) — voir — voir — voir - voir - voir - voir - voir - voir - voir - voir - voir |

## 7.8 Exercice pour l'apprentissage (Phase 7)

1. Vérifie (via le HTTP et SQL) que la mise à jour du solde d'un compte en devise XOF est reflétée par l'échage d'écriture — test — impossible de faire directement — voir — voir - voir - voir — voir - voir - voir - voir - voir - voir - voir |
| 2. Ajoute un nouveau `system_code` : FX_POSITION_AR (Argent/Argentine) — voir — plus tard — voir — voir - voir - voir - voir - voir - voir - voir - voir - voir - voir - voir - voir - voir - voir |
