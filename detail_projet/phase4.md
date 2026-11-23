# Phase 4 — F-03 : Comptes + numéro de compte modulo 97 + comptes système + seed — ✅ FAIT

> **Statut : FAIT — suite verte.** Tous les tests de la phase passent :
> `Tests run: 107, Failures: 0, Errors: 0, Skipped: 0` (dépassement vs Phase 3 : +43), `BUILD SUCCESS`
> (vérifié le 23/11/2026 avec `./mvnw verify`).

> Tests par classe : `AccountEndpointTest` 19, `AccountServiceTest` 19, `AccountNumberGeneratorTest` 5 —
> 43 nouveaux (107 − 64 = 43 ✅ — Phase 3 : 64).
>
> points forts : `seed` des comptes systèmes dans `V4__create_account.sql` (6 codes × 3 devises) — la clé est générée en SQL (`lpad('BJ' …)` + `97 − (n % 97)`) — compatible avec `AccountNumberGenerator`.

## 4.1 Objectif de la phase (3 lignes)

1. Introduire l'entité **compte client** (`account`) — devises `XOF`/`EUR`/`USD` — numéro BEN unique — statuts `ACTIVE/FROZEN/CLOSED` — RG-ACC-01 à 04.
2. Générer + valider les **numéros de compte** (10 chiffres + clé mod 97 — RG-ACC-03, CA-ACC-01).
3. Créer les **comptes systèmes** (`CASH_VAULT`, `FEE_REVENUE`, `FX_POSITION`, `EXTERNAL_SETTLEMENT`, `SUSPENSE`, `INTEREST_EXPENSE`) au démarrage (migration + seed — RG-ACC-06).

## 4.2 Fonctionnalités implémentées

**Table `account`** (V4 — cahier §6.1) — DDL :

```sql
CREATE TABLE account (
    id UUID PRIMARY KEY,
    account_number VARCHAR(14) NOT NULL UNIQUE,         -- BJ + 10 + 2
    customer_id UUID NULL REFERENCES customer (id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,                          -- CURRENT|SAVINGS|WALLET|SYSTEM
    system_code VARCHAR(20),                            -- CASH_VAULT|…|INTEREST_EXPENSE
    currency VARCHAR(3) NOT NULL,                       -- XOF|EUR|USD
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',       -- ACTIVE|FROZEN|CLOSED
    balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,                  -- nettoyage Phase 5 (RG-LED-06)
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ NULL,
    CONSTRAINT ck_account_kind CHECK (...),
    CONSTRAINT ck_account_currency CHECK (...),
    CONSTRAINT ck_account_status CHECK (...)
);
CREATE UNIQUE INDEX uq_account_customer_kind_currency
    ON account (customer_id, kind, currency) WHERE customer_id IS NOT NULL;  -- RG-ACC-02
```

**Règles couvertes — cahier §7 (RG-ACC) et §5 :**

| Règle | Couverture |
|---|---|
| RG-ACC-01 (types + devises — XOF par défaut) | `AccountKind`, `SystemCode`, `AccountService.open` — XOF par défaut si DTO null — `Currency`/`Money` (Phase 1, échelle) |
| RG-ACC-02 (un seul compte par (type, devise) — client non REJECTED) | UQ partielle `uq_account_customer_kind_currency` (SQL) + `AccountService.open` — 400 `VALIDATION_FAILED` — client REJECTED — test `open_account_for_rejected_customer_400` |
| RG-ACC-03 (numéro BEN + clé mod 97, validation à la saisie) | `AccountNumberGenerator` — `generate()` / `isValid()` — **convention** : clé = `97 − (n mod 97)` ∈ 1..97 — validation — pré-contrôle bénéficiaire `GET /accounts/lookup` — 400 `INVALID_ACCOUNT_NUMBER` (CA-ACC-01) — test `lookup_rejects_invalid_number_400` |
| RG-ACC-04 (ACTIVE/FROZEN/CLOSED — transitions) | `AccountService.changeStatus` — gel ACTIVE→FROZEN, dégel FROZEN→ACTIVE, clôture — depuis CLOSED : `ILLEGAL_STATE_TRANSITION` (409) — systèmes non modifiables |
| RG-ACC-05 (clôture — solde 0 — pas de transaction PENDING) | solde à zéro vérifié (`isZeroBalance`) — `admin_cannot_close_account_with_nonzero_balance_409` — le critère « sans transaction PENDING » **n'a pas de source de données encore** (table `financial_transaction` — Phase 5 — F-04) — documenté — solde-0 implémenté |
| RG-ACC-06 (comptes systèmes — 6 codes × 3 devises — seed au démarrage) | `V4__create_account.sql` — INSERTs — `AccountEndpointTest.system_accounts_are_seeded_at_startup` |
| RG-ACC-07 (balance client jamais négative) | ∞ de la Phase 5 (grand livre recompute la balance — F-04, RG-LED-07) — pas d'écriture de solde par `AccountService` en Phase 4 |

**Statuts — §8 + F-13 :** `PATCH /accounts/{id}/status` — ADMIN — gel/dégel/clôture — motif obligatoire (F-13) — statut mis à jour en base — `closed_at` positionné.

**§5 — matrice de droits :** `POST /accounts` — TELLER/ADMIN — `GET /accounts` — CUSTOMER — `GET /accounts/{id}` — CUSTOMER/ADMIN — `GET /accounts/lookup?number=` — CUSTOMER — `PATCH /accounts/{id}/status` — ADMIN — tests HTTP (`AccountEndpointTest`).

**§5 — CA-ACC-02 :** CUSTOMER lit un compte d'un autre client → 404 (`AccountService.findById(id, Role.CUSTOMER, currentCustomerId)` — chemin identical pour l'ADMIN) — test `customer_cannot_read_others_account_404`.

## 4.3 Commandes exécutées

| Commande | Ce qu'elle fait | Pourquoi l'avoir lancée | Importance |
|---|---|---|---|
| `./mvnw verify` | Compile + exécute toutes les suites : unitaires + d'intégration (Testcontainers PostgreSQL 17) | Résultat : `Tests run: 107, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS` | ✅ CRITIQUE — preuve par HTTP/SQL du respect de CA-ACC-01/02 + RG-ACC-06 |
| `git status --short` | État de l'arborescence (7 fichiers modifiés — account/*.java, migration, tests, Customer.setKycStatus) | Préparation aux commits par l'auteur | Importante |

## 4.4 Fichiers créés ou modifiés

| Fichier | Rôle |
|---|---|
| `src/main/java/com/wince/fincore/account/domain/AccountKind.java` | `CURRENT, SAVINGS, WALLET, SYSTEM` (cahier §6.1, RG-ACC-01) |
| `src/main/java/com/wince/fincore/account/domain/SystemCode.java` | `CASH_VAULT, FEE_REVENUE, FX_POSITION, EXTERNAL_SETTLEMENT, SUSPENSE, INTEREST_EXPENSE` (RG-ACC-06) |
| `src/main/java/com/wince/fincore/account/domain/AccountStatus.java` | `ACTIVE, FROZEN, CLOSED` (RG-ACC-04) |
| `src/main/java/com/wince/fincore/account/domain/Account.java` | Entité `account` (§6.1) — `currency` en `@Enumerated(EnumType.STRING)` (**piège P4-1** — voir 4.7) — `isSystem()`, `setStatus()`, `setClosedAt()`, `setOpenedAt()`, `setBalance()` |
| `src/main/java/com/wince/fincore/account/repository/AccountRepository.java` | `findByCustomerIdAndKindAndCurrency`, `findByAccountNumber`, `existsByAccountNumber`, `findByCustomerId`, `findAllBySystemCode` |
| `src/main/java/com/wince/fincore/account/application/AccountNumberGenerator.java` | Génération + validation — `generate()` (SecureRandom, 10 chiffres, 1ᵉʳ non nul) + `isValid()` — convention de la clé — pure function |
| `src/main/java/com/wince/fincore/account/application/AccountService.java` | Cas F-03 — `open` (RG-ACC-01 à 07 — nombre mod 97 — nombre unique par (type, devise)), `findByCustomerId`, `findById` (CUSTOMER — 404 des autres — CA-ACC-02), `changeStatus` (RG-ACC-04/05), `lookupBeneficiary` (pré-contrôle — RG-ACC-03, F-06) |
| `src/main/java/com/wince/fincore/account/api/AccountController.java` | §8 — `POST /accounts` (201 — pattern AuthController — **piège P4-2**), `GET /accounts`, `GET /accounts/{id}`, `GET /accounts/lookup`, `PATCH /accounts/{id}/status` — DTO en bean validation |
| `src/main/java/com/wince/fincore/account/api/OpenAccountRequest.java` | DTO POST — `customerId` + `kind` + `currency` NULL → XOF (RG-ACC-01) |
| `src/main/java/com/wince/fincore/account/api/AccountStatusUpdateRequest.java` | DTO PATCH — `status` obligatoire — `motif` obligatoire FROZEN/CLOSED (F-13) |
| `src/main/java/com/wince/fincore/account/api/AccountResponse.java` | DTO de réponse — miroir de `account` — projette l'entité, jamais de donnée sensible |
| `src/main/java/com/wince/fincore/account/api/AccountLookupResponse.java` | Pré-contrôle — `maskedName` — « J*** D*** » (F-06, CA-KYC-01) |
| `src/main/java/com/wince/fincore/customer/domain/Customer.java` | **Modifié** : `setKycStatus(KycStatus)` (Phase 3 → Phase 4 : RG-ACC-02 — ajout de l'écriture du statut KYC — absence vérifiée — test http `open_account_for_rejected_customer_400`) |
| `src/main/resources/db/migration/V4__create_account.sql` | Migration V4 : `CREATE TABLE account` + CHECKs + `CREATE UNIQUE INDEX uq_account_customer_kind_currency` + **seed des comptes systèmes** (6 codes × 3 devises, `gen_random_uuid()`, clé `97 − (n % 97)`) — RG-ACC-06 |
| `src/test/java/com/wince/fincore/account/application/AccountNumberGeneratorTest.java` | Unit — generate/isValid/distinct/clé — CA-ACC-01, RG-ACC-03 |
| `src/test/java/com/wince/fincore/account/application/AccountServiceTest.java` | Unit — `open` (ACTIVE/0/XOF-par-défaut/duplicat/REJECTED/SYSTEM/404) — `findById` (CA-ACC-02 — 404 — ADMIN — allowed) — `changeStatus` (gel/dégel/clôture/motif/solde-0/système) — `lookupBeneficiary` (CA-ACC-01/nom masqué « M*** D*** ») |
| `src/test/java/com/wince/fincore/account/api/AccountEndpointTest.java` | Intégration — Testcontainers — §5 — 19 tests (seed 6 × 3 devises, POST 201, CUSTOMER 403, 400 rejeté, 400 duplicat, GET mes comptes, 404 autre client, 404 système, TELLER 403, ADMIN 200, lookup 400/200 masqué, freeze/unfreeze/close, 409 sans motif/solde, 409 système) |

## 4.5 Code clé

### a. Génération + validation — `AccountNumberGenerator` (RG-ACC-03)

```java
/** Clé : 97 − (n mod 97) — convention ICC — borne 1..97. */
private static int checkKey(String body) {
    return MOD - new BigInteger(body).mod(BigInteger.valueOf(MOD)).intValue();
}

public static String generate() {
    String body = generateBody();                          // 10 chiffres — 1er non nul
    return PREFIX + body + String.format("%02d", checkKey(body));
}

public static boolean isValid(String accountNumber) {
    // préfixe "BJ", longueur 14, body = \d{10}, clé = \d{2} — clé == 97 − (n mod 97)
}
```

- **Concept clé (RG-ACC-03)** : le numéro est **joint** (pas nouveau point d'entrée) — la validation est également l'équivalent de la « validation de la clé à chaque saisie d'un numéro de bénéficiaire » — ici via `AccountService.lookupBeneficiary` — POST-Phase 6 si transfer.
- **Concept PHP/Symfony (§2.2)** : utilisation de `BigInteger` pour le mod 97 — vérifié dans le test `check_key_convention_on_generated_example` — équivalent aux traitements BCMath sur 10 chiffres.

### b. Ouverture + RG-ACC-02 — `AccountService.open`

```java
Customer customer = customerRepository.findById(request.customerId()).orElseThrow(RESOURCE_NOT_FOUND);
if (customer.getKycStatus() == KycStatus.REJECTED) {
    throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "...");  // RG-ACC-02 — section §7 Phase 3
}
if (accountRepository.findByCustomerIdAndKindAndCurrency(id, kind, currency).isPresent()) {
    throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "...");  // RG-ACC-02 — un seul compte par (type, devise)
}
Account account = new Account(UUID.randomUUID(), generateUniqueAccountNumber(),
        request.customerId(), request.kind(), null, currency, Money.zero(currency).amount());
account.setOpenedAt(clock.instant());
return accountRepository.save(account);
```

- **Concept clé — phase dépendance** : la règle métier (KYC REJECTED — RG-ACC-02, Phase 3) est activée par `Customer.setKycStatus` — le service comprend — extension de la fonctionnalité de Phase 3.

### c. Matrice de droits — `AccountController` (§5)

```java
@PostMapping
@PreAuthorize("hasRole('TELLER') or hasRole('ADMIN')")
public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(accountService.open(request)));
}
```

- **Concept clé (§5 — CUSTOMER ne lit que ses comptes — CA-ACC-02)** : `currentCustomerId()` — `account.customer_id` (§6.1) pointe vers `customer.id` — alors que `currentUserId()` = id de `app_user` — le chemin — `customerRepository.findByUserId(...).map(Customer::getId)` — pour la vérification.
- **Concept PHP/Symfony** : ADMIN lire n'importe quel compte (`role == Role.CUSTOMER && ...` — vérifiée — `admin_reads_account_detail_200` — le `Role.CUSTOMER` — Aucune comparaison de `ROLE_ADMIN` — la communauté mondiale des API — l'ADMIN peut lire n'importe quel compte — pas d'exception — le CUSTOMER est limité à ses comptes).

### d. Seed — `V4__create_account.sql` (RG-ACC-06)

```sql
WITH pairs(code, kind, ccy) AS (VALUES ('CASH_VAULT','SYSTEM','XOF'), …, ('INTEREST_EXPENSE','SYSTEM','USD')),
gen AS (SELECT code, kind, ccy, floor(random() * 9000000000 + 1000000000)::bigint AS body FROM pairs)
INSERT INTO account (id, account_number, kind, system_code, currency, status, balance, version, opened_at)
SELECT gen_random_uuid(),
       'BJ' || lpad(body::text, 10, '0') || lpad((97 - (body % 97::bigint))::text, 2, '0'),
       kind, code, ccy, 'ACTIVE', 0, 0, now()
FROM gen;
```

- **Concept clé** : seed en SQL (migration-embedded) — les 6 codes × les 3 devises — est **identique à `AccountNumberGenerator.checkKey`** — vérifiable via `AccountEndpointTest.system_accounts_are_seeded_at_startup` (`isValid` sur chaque numéro).

## 4.6 Comment vérifier

- **Suite complète** : `./mvnw verify` → `Tests run: 107, Failures: 0, Errors: 0` — inclut :
  - `AccountEndpointTest.system_accounts_are_seeded_at_startup` : 6 comptes systèmes, 1ᵉʳ par (code × devise), `isValid` sur les numéros — CA-ACC-01 via `lookup_rejects_invalid_number_400`;
  - `AccountEndpointTest.admin_reads_account_detail_200` : GET — 200 — ADMIN — lisible sans être un client (§5);
  - `AccountEndpointTest.customer_cannot_read_others_account_404` : CA-ACC-02 — 404 — Même pattern que `KycEndpointTest.minor_cannot_register` du Point (Phase 3) : précision la idea — la règle est **le non-révélation** — indifférencier autant l'accès externe.
  - Commandes manuelles (live) : `curl.exe -X POST .../accounts -H "Authorization: Bearer <ADMIN>" -d '{"customerId":"...","kind":"WALLET","currency":"EUR"}'` (201 — CUSTOMER → 403).
  - État en base : `SELECT account_number, kind, currency, balance FROM account WHERE customer_id IS NOT NULL;`.
- **Rétro-vérifiable** : `git log --oneline` — histoire de commits propres — Conventional Commits — vous êtes prêt à commettre vos fichiers (`git add -A; git commit`) — le bilan = : `.\mvnw.cmd verify` → `Tests run: 107` + suite — ✅.

## 4.7 Pièges rencontrés

1. **P4-1 — `Schema validation: wrong column type...` — `currency` — `varchar` — mais `smallint` (TINYINT) attendu** : `Account.currency` est un `enum` JPA — par défaut (`@Enumerated` absent) l'enum est un `int` — la base a `currency VARCHAR(3)` — l'EntityManager **féfaçon — validation — de la table** — le **Piège du Hibernate 6** — l'entité a une énumération de classe — la base a varchar — `Type - mismatch` — autrement dit l'inverse d'une enum `STRING` — ici on veut varchar — ajouter **`@Enumerated(EnumType.STRING)`** — **Piège du JPA** — déjà résolu dans la JVM de l'exécution — ailleurs, évaluer l'impact — documentation — du Java 25.
2. **P4-2 — `Status expected:<201> but was:<200>` sur `POST /accounts`** : `@PostMapping` renvoie **200** — or — les autres POST de la suite — réponse du code de l'appel — `AuthController.register` — pattern **`ResponseEntity.status(HttpStatus.CREATED).body(...)`** (Phase 2) — volontaire : le POST crée une ressource — reprendre le pattern — du code 200 — conversion en 201.
3. **P4-3 — ADMIN sans `Customer` — 404 — `currentCustomerId()`** : le chemin est plus complexe — `findByUserId` — les identités — vrai — relecture — car l'ADMIN n'a pas de Customer — la solution — null-safe dans `findById` — vérifiable via `admin_reads_account_detail_200` — vrai — réflexion finale — voir code.
4. **Mutation test pédagogique (aucun commit)** : supprime localement la validation `KycStatus.REJECTED` dans `AccountService.open` — relance `./mvnw verify` — `open_account_for_rejected_customer_400` rouge — rétablis.

## 4.8 Questions d'entretien — Phase 4

**Q1. Comment calculer une clé de contrôle modulo 97 sur 10 chiffres sans perdre en précision ?**
`String body` → `new BigInteger(body)` → `mod(97)` → `key = 97 − mod` — borne 1..97 — éviter `int` (jusqu'à 9 999 999 999 — plus que `Integer.MAX_VALUE` — avec BigInteger — pédagogique : `intValueExact` — 42) — équivalent BCMath (`bcsub`, `bcmod`).

**Q2. Comment représenter un numéro de 10 chiffres sans warnings Java ?**
`String` — validation à l'API (pré-contrôle — `AccountService.lookupBeneficiary` — 400 `INVALID_ACCOUNT_NUMBER` — si mauvaise clé — H4/Règle §7).

**Q3. Comment représenter un solde de compte dans l'API ? (§6.1 — `balance NUMERIC` + DTO)**
`AccountResponse` — `balance` est un `BigDecimal` — scale = `currency.scale()` (0 XOF — 2 EUR/USD — voir `Money`) — repose sur l'échelle de la devise — le `Jackson` écrit le format (`0`, `12.34`) — `décimales` invisibles dans la base (`NUMERIC(19,4)`).

**Q4. Comment implémenter « un seul compte par (type, devise) » — RG-ACC-02 ?**
Indice 1 : Backstop SQL — `CREATE UNIQUE INDEX uq_account_customer_kind_currency ON account (customer_id, kind, currency) WHERE customer_id IS NOT NULL` — pourquoi PARTIEL — PostgreSQL : NULLs différents — (Multiple NULLs — 2047) — les comptes systèmes — unique sur (code, ccy) — Indice 2 : Backstop Java — `AccountService.open` — `findByCustomerIdAndKindAndCurrency(...).isPresent()` → 400 `VALIDATION_FAILED` — Indice 3 : Client REJECTED — `KycStatus.REJECTED` — Positionné en Phase 3 — check écrit dans le service.

**Q5. Comment expliquer le flux « pré-contrôle » de `GET /accounts/lookup?number=` ?**
Flux dans `AccountService.lookupBeneficiary(number)` :
1. `AccountNumberGenerator.isValid(number)` — invalide → 400 `INVALID_ACCOUNT_NUMBER` (CA-ACC-01 — clé mod 97 — RG-ACC-03) ;
2. `accountRepository.findByAccountNumber(number).orElseThrow(404)` — ben inexistant en base — vérification de l'existence dans la base) ;
3. `account.isSystem()` → 404 (les systèmes n'ont pas de bénéficiaire) ;
4. `customerRepository.findById(account.getCustomerId())` — Customer lié — ;
5. `AccountLookupResponse.of(first, last)` → « M*** D*** » (F-06 — CA-KYC-01 — néant de donnée sensible — juste le masque — RG-KYC-03).

**Q6. Différence entre `Account.customerId` et `AppUser.id` — quand les différencie-t-on ? (cahier §6.1)**
`AppUser.id` = id de **session** (login, verrou — Phase 2 — claim `sub` du JWT — RG-AUTH-06) — `Customer.id` = **donnée métier** — `Account.customer_id` → `customer.id` (§6.1).
- Le JWT n'a jamais de Customer — le « client » côté API est `Customer.id` — resolveur : `customerRepository.findByUserId(userId).map(Customer::getId)` — où `userId` = `currentUserId()` (claim `sub`) — AppUser → Customer — null si l'ADMIN n'a pas de Customer.
- Côté service, la visibilité (§5) compare `account.getCustomerId()` (Customer.id) avec `currentCustomerId()` — et `AccountService.findById(id, role, currentCustomerId)` — le statut CUSTOMER ne voit que ses comptes — 200 ADMIN — le Customer — l'ADMIN peut lire n'importe quel compte.

## 4.9 Exercice pour l'apprentissage

1. **Découvrir le flux** — lance `./mvnw verify -Dtest=AccountEndpointTest#teller_opens_account_201` — vérifie 1 test vert — puis ajoute un `System.out.println` dans `AccountService.open` — observe l'ordre : changement d'état → `ResponseEntity.status(HttpStatus.CREATED)` → `AccountResponse.from` — supprime le print.
2. **Mutation test (aucun commit)** — supprime localement le bloc REJECTED de `AccountService.open` — relance `./mvnw verify` — `AccountEndpointTest.open_account_for_rejected_customer_400` rouge (201 attendu — aucune validation) — rétabris le bloc.
3. **Remarque pédagogique** : le modulo 97 — voir 4.8 Q1 — convention écrite en SQL (`V4`) et reproduite en Java (`AccountNumberGenerator.checkKey`) — vérifiable via `AccountEndpointTest.system_accounts_are_seeded_at_startup` (`isValid(numéro)` sur chaque ligne seedée).
