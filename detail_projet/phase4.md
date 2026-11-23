# Phase 4 — F-03 : Comptes + numéro de compte modulo 97 + comptes système + seed — ⏳ PRÉVUE

> **Statut : PRÉVUE** — sera **FAIT** à la fin de cette phase.
> Phase 4 = **F-03 Comptes** (MUST — cahier §7, §6.1, Anonymes de l'app de phase 4).

## 4.1 Objectif de la phase (3 lignes)

1. Implémenter les comptes (`CURRENT`, `SAVINGS`, `WALLET`) avec les devises (`XOF`, `EUR`, `USD`) — un compte = un numéro de compte **unique** (RG-ACC-03).
2. Implémenter le calcul du **numéro de compte BEN** (10 chiffres + clé de contrôle modulo 97 — validation à la saisie, `CA-ACC-01`).
3. Créer les **comptes systèmes** (`CASH_VAULT`, `FEE_REVENUE`, `FX_POSITION`, `EXTERNAL_SETTLEMENT`, `SUSPENSE`, `INTEREST_EXPENSE`) via migration + *seed* au démarrage (`RG-ACC-06`).

## 4.2 Fonctionnalités prévues

**Table `account`** (cahier §6.1) :

| Colonne | Type / Rôle |
|---|---|
| `id UUID PK`, `account_number VARCHAR UNIQUE` | numéro BEN (10 chiffres + clé modulo 97) |
| `customer_id FK NULL` | `NULL` pour les comptes systèmes |
| `kind` (enum : `CURRENT`, `SAVINGS`, `WALLET`, `SYSTEM`) | type de compte client |
| `system_code` (enum : `CASH_VAULT`, `FEE_REVENUE`, `FX_POSITION`, `EXTERNAL_SETTLEMENT`, `SUSPENSE`, `INTEREST_EXPENSE`) | code du compte système |
| `currency` (enum : `XOF`, `EUR`, `USD` — `Currency` existe déjà, Phase 1) | devise du compte |
| `status` (enum : `ACTIVE`, `FROZEN`, `CLOSED`) | statut de verrouillage / clôture |
| `balance NUMERIC` | solde du dénominateur |
| `version BIGINT` (verrou optimiste) | pour RG-LED-06 |
| `opened_at`, `closed_at` | horodatages |

**Règles prévues (RG-ACC)** :
- RG-ACC-01 : types + devises (nouveaux types possibles par extension du jeu d'enum : ajouter `WALLET` exige), 
- RG-ACC-02 : un client peut avoir un seul compte par (type, devise) — contrainte SQL UNIQUE(customer_id, kind, currency) (+ contrôle que customer est non REJECTED en Phase 3 — plutôt dans le service).
- RG-ACC-03 : numéro = `BJ` + 10 chiffres aléatoires + 2 chiffres de clé de contrôle (modulo 97 sur les 10 chiffres) — calculée côté serveur au démarrage ou validation lors de la saisie d'un bénéficiaire (utilisé par Phase 5 Transfers).
- RG-ACC-04 : statuts ACTIVE / FROZEN / CLOSED.
- RG-ACC-05 : clôture possible sifiningé à 0 et sans transaction `PENDING` — à valider dans Phase 5 (le service).
- RG-ACC-07 : `balance` client jamais négatif — de plusieurs contraintes CHECK dans Flyway (cahier §6.2).

**Migration prévue (V4)** : `CREATE TABLE account` + FK vers `customer` (nullable) + checks de base (cahier §6.2).

**Comptes systèmes prévus (Phase 4-fin)** : seed dans `application/AccountSeedService` au démarrage (dev) ou via SQL migration.

## 4.3 Commandes prévues

| Commande | Ce qu'elle fait | Pourquoi | Importance |
|---|---|---|---|
| `.\mvnw.cmd test` | Compile + exécute les tests (avec les nouveaux) | Vérification de la phase | ✅ CRITIQUE |
| `.\mvnw.cmd test "-Dtest=AccountServiceTest,AccountNumberValidatorTest"` | Tests ciblés du service de comptes et du validateur BEN | Aller-retour rapide | Importante |
| `curl.exe -sS -X POST -H "Authorization: Bearer <ADMIN>" -H "Content-Type: application/json" http://localhost:8080/api/v1/accounts -d '{"kind":"WALLET","currency":"EUR","customerId":"..."}'` | Crée un compte — vérifie que CUSTOMER peut ouvrir un compte (§5) | Vérifie le côté live | Importante (partie 4.5) |
| `docker exec fincore-db psql -U fincore -d fincore -c "SELECT account_number, kind, currency, balance FROM account;"` | Présentation des comptes + solde | Vérifie le seed des comptes systèmes + validité des numéros BEN en base (clé de contrôle valide) | Importante |

## 4.4 Fichiers clés à créer (prévision)

| Fichier | Rôle |
|---|---|
| `account/domain/Account.java` | Entity `account` (kind, systemCode, currency, status, balance (Money), version) |
| `account/domain/AccountKind.java` | Enum : `CURRENT`, `SAVINGS`, `WALLET`, `SYSTEM` |
| `account/domain/SystemAccountCode.java` | Enum : `CASH_VAULT`, `FEE_REVENUE`, `FX_POSITION`, `EXTERNAL_SETTLEMENT`, `SUSPENSE`, `INTEREST_EXPENSE` |
| `account/domain/AccountStatus.java` | Enum : `ACTIVE`, `FROZEN`, `CLOSED` |
| `account/application/AccountService.java` | Ouvre un compte (`openAccount`) : validation client, génération du numéro BEN, ajout dans la table |
| `account/application/AccountNumberValidator.java` | `validate(number)` — calcul de la clé de contrôle modulo 97 (RG-ACC-03) — methode testable via `AccountNumberValidatorTest` |
| `account/repository/AccountRepository.java` | `findByAccountNumber`, `findByCustomerIdAndKindAndCurrency`, `findBySystemCode` |
| `account/api/AccountController.java` | `POST /api/v1/accounts` (TELLER/ADMIN) — `AccountResponse` (masqué `account_number` partiel si SHIELDV5 rules) |
| `account/infra/AccountJpaRepository.java` — | pas besoin, sinon adapter JPA (nom sur table legacy `account`) |
| `account/application/AccountSeedService.java` | Crée les comptes systèmes au démarrage en mode dev (§12.3 Profil dev : seed) — pas en prod (reprise à la Phase 12) |
| `src/main/resources/db/migration/V4__create_account.sql` | Migration de la table `account` + index + FK |
| `src/main/resources/db/migration/V5__seed_system_accounts.sql` | Seed des comptes systèmes (CASH_VAULT XOF, FEE_REVENUE XOF, FX_POSITION XOF/EUR/USD, EXTERNAL_SETTLEMENT XOF, SUSPENSE XOF, INTEREST_EXPENSE XOF) |
| `src/test/java/com/wince/fincore/account/application/AccountNumberValidatorTest.java` | Tests : validation OK/invalid, exemples (BJ1234567890XX à ajuster — voir code) |
| `src/test/java/com/wince/fincore/account/application/AccountServiceTest.java` | Tests : ouverture compte client, NON exeption si client REJECTED? (prévu), un seul compte par (type, devise) |

## 4.5 Pièges à surveiller (ajuster lors de l'implémentation)

1. **Le modulo 97 en Java** : `int` ne suffit pas à grandes valeurs — comprendre la formule (nombre sur 10 chiffres sur 2 chiffres) — boiss0 pour les gros nombres — calculateur possible : ensemble de formule — mettre l'accent sur du stockage en chiffres (`String`/`BigInteger`).
2. ** solde = solde null initial — valeur par défaut `Money.zero(currency)` : NULL en base vs zéro — préférer le zéro (solde comptable = 0) — testé via `Account.of()` / `Account.zero()`.
3. **Gestion des devises des comptes (RG-ACC-01, RG-ACC-06)** : les comptes systèmes peuvent avoir des devises différentes (FX_POSITION EUR + FX_POSITION USD) — table `account` avec UNIQUE (customer_id, kind, currency) + CHECK unique de valeurs possibles (surkind == kind SYSTème).
4. **CLAVIER : le numéro BEN est unique (UNIQUE) — SEEDS — CASFAIR (changement à la Phase 5 ou later si besoin) — PASSWORD: seed des comptes systèmes à migration + surtout pas de seed explicite en Phase 4-5 (Phase 5 Déposer/retrait demands les soldes pré-seeds — faire le choix une fois Phase 5 implémentée — réserver discussion à la Phase 5).**
5. **Test SQL (RG-ACC-07)** : `docker exec fincore-db psql -U fincore -d fincore -c "SELECT account_number, kind, balance FROM account WHERE kind != 'SYSTEM' AND customer_id IS NOT NULL AND balance < 0;"` — attendre 0 rows.

## 4.6 Questions d'entretien — Phase 4

**Q1. Comment calculer une clé de contrôle modulo 97 sur 10 chiffres sans perdre en précision avec Java ?**
`String number` → `BigInteger` ou `new BigInteger(number)` → `remainder(BigInteger.valueOf(97))` → `key = (97 - remainder) % 97` — attention si 0 → 97 (clé sur 2 chiffres).

**Q2. Comment représenter un nombre de 10 chiffres sans warnings Java ?**
String + validation de la clé à l'API (pré-contrôle), régénération à l'inscription (Phase 3 — on peut faire `AccountService.generateAccountNumber()` prévu au préalable dès Phase 4 — prévoir dans `AccountService`).

**Q3. Comment représenter un solde de compte dans l'API ?**
DTO `AccountResponse` avec `balance: Money(amount, currency)` → structure répétée (nested JSON) — comme le cahier le montre (`balance NUMERIC` stocké + DTO avec champs `amount`, `currency`) — attention : les zéros flottants sont des Decimals — vérifier via `Money.parse()` / `money.zero(currency)`.

**Q4. Comment implémenter la contrainte « un seul compte par (type, devise) » ?**
UNIQUE(customer_id, kind, currency) en base + requête JPQL/HQL dans le service (`findByCustomerIdAndKindAndCurrency(...).isPresent()` → throw `EXCEPTION` valide `ALREADY_EXISTS` / 422 `EXISTING_ACCOUNT` — voir codes d'erreur (TODO — prévoir un code d'erreur dans `ErrorCode` — g Technique).

## 4.7 Exercice pour l'apprentissage (Phase 4)

1. Écris un test unitaire `AccountNumberValidatorTest` : `validate("BJ123456789028")` → `valid` si la clé 28 est correcte — calculer d'abord le modulo 97 de `1234567890` par une petite éxécution en PowerShell ou une calculatrice Java maison…
2. Alternative : ajoute `AccountSeedServiceTest` : vérifie que `seed` crée 6 comptes systèmes (CASH_VAULT XOF, …, INTEREST_EXPENSE XOF) — surtout sur `balance = 0`.
3. Relance `docker exec fincore-db psql -U fincore -d fincore -c "SELECT * FROM account WHERE customer_id IS NULL;"` → tu verifies (tu dois) voir 6 lignes systèmes.
