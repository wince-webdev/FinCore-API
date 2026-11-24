# Phase 3 — F-02 : Clients et KYC — ✅ FAIT

> **Statut : FAIT — suite verte.** Tous les tests de la phase passent :
> `Tests run: 64, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`
> (vérifié le 23/11/2026 avec `./mvnw verify`).

> Note pédagogique : le socle de cette phase (entity `Customer`, `KycLevel`,
> `IdDocType`, `CustomerService`, `CustomerController`, `CustomerResponse`,
> `CustomerRepository`, migration `V3`, `KycEndpointTest`…) existait déjà dans
> l'arborescence (commit `b6d5e3a`) — il a été vérifié ici, puis complété :
> le passage en `LEVEL_3` est désormais ADMIN-seul et exige une source de
> revenus déclarée (RG-KYC-02).

## 3.1 Objectif de la phase (3 lignes)

1. Introduire l'entité **client** (`customer`) — clé lien `user_id` unique vers `AppUser` (Phase 2) — avec les énumérés `KycLevel` (`LEVEL_1/2/3`), `KycStatus` (`PENDING/VERIFIED/REJECTED`) et `IdDocType` (§6.1).
2. Implémenter les routes du KYC — `GET /customers/me` (profil du client, matrice §5 : CUSTOMER) et `PATCH /customers/{id}/kyc` (matrice §5 : TELLER, ADMIN).
3. Respecter l'intégrité des données : le numéro d'identification est **masqué** dans toute réponse (RG-KYC-03), le client est mineur ou majeur selon la `Clock` injectée (RG-KYC-04).

## 3.2 Fonctionnalités implémentées

**Socle Phase 3 (existait déjà, vérifié cette session) — Règles couvertes : RG-KYC-01 à 04, CA-KYC-01 à 02 (partiellement — voir 3.3) :**

| Fonctionnalité | Couverture |
|---|---|
| `Customer` créé à l'inscription : `LEVEL_1` / `PENDING`, `firstName`, `lastName`, `birthDate`, `phone` | RG-KYC-01 — `KycEndpointTest.customer_reads_own_profile`, `CustomerServiceTest.creation_defaults_to_level_1_pending` |
| `GET /customers/me` | §8 (CUSTOMER — profil du client) |
| `PATCH /customers/{id}/kyc` : niveau + doc (TELLER/ADMIN) | RG-KYC-02 LEVEL_2 — `KycEndpointTest.teller_patches_kyc_level` |
| Majeur/mineur (majeur = 18 ans passé le 18ᵉ anniversaire) | RG-KYC-04 — `CustomerServiceTest.isAdult`, `KycEndpointTest.minor_cannot_register`, `minor_cannot_be_escalated` |
| Masquage du numéro (`****6789`), hash SHA-256, jamais en clair | RG-KYC-03 / CA-KYC-01 — `CustomerServiceTest.doc_is_masked_and_never_clear`, `KycEndpointTest.teller_patches_kyc_level` |
| Inscription d'un mineur → 422 `CUSTOMER_UNDERAGE` | RG-KYC-04 / CA-KYC-02 — `KycEndpointTest.minor_cannot_register` |

**Complément nouveau de cette séance (RG-KYC-02 LEVEL_3, F-02) :**

| Fonctionnalité | Couverture |
|---|---|
| `LEVEL_3` : ADMIN seul (TELLER → 403 `ACCESS_DENIED`) | `KycEndpointTest.teller_cannot_reach_level_3` |
| `LEVEL_3` : montée acceptée (doc + source de revenus déclarée) | `KycEndpointTest.admin_reaches_level_3_with_declared_source_of_revenue` |
| `LEVEL_3` sans source de revenus → 400 `VALIDATION_FAILED` (`errors.sourceRevenus`) | `KycEndpointTest.admin_level_3_requires_declared_source_of_revenue`, `CustomerServiceTest.level_3_without_source_of_revenue_is_rejected` |
| `KycUpdateRequest` : nouveau composant `sourceRevenus` | DTO |

## 3.3 Commandes exécutées

| Commande | Ce qu'elle fait | Pourquoi l'avoir lancée | Importance |
|---|---|---|---|
| `./mvnw verify` | Compile et exécute **toutes** les suites Maven : unitaires + d'intégration (Testcontainers PostgreSQL 17) | Vérification globale de la phase 3 : **64 tests, 0 échec, 0 erreur, 0 défaut, 0 sauté** | ✅ CRITIQUE — preuve par HTTP/SQL du respect de CA-KYC-01 à 02 |
| `git status --short` / `git diff --stat` | État de l'arborescence (7 fichiers modifiés : 5 source/test + 2 doc) | État de l'arbre avant l'archivage des commits par l'auteur | Importante |

> Travail pendant le dev — **P3-1** : le 1er `./mvnw verify` est passé en erreur avec `Unhandled exception type Exception` dans `KycEndpointTest` — le helper `customerId(MvcResult)` appelle `read()` (déclaré `throws Exception`) sans clause — voir 3.7.

## 3.4 Fichiers créés ou modifiés

| Fichier | Rôle |
|---|---|
| `src/main/java/com/wince/fincore/customer/api/KycUpdateRequest.java` | **Modifié** : nouveau composant `sourceRevenus` (String) — RG-KYC-02 (source de revenus déclarée au LEVEL_3) |
| `src/main/java/com/wince/fincore/customer/application/CustomerService.java` | **Modifié** : vérification RG-KYC-02 — doc obligatoire au LEVEL_3 + `sourceRevenus` obligatoire, 400 `VALIDATION_FAILED` |
| `src/main/java/com/wince/fincore/customer/api/CustomerController.java` | **Modifié** : contrôle ADMIN au LEVEL_3 dans `updateKyc` (403 `ACCESS_DENIED`) — nouveau helper `hasAuthority(Role)` |
| `src/test/java/com/wince/fincore/customer/api/KycEndpointTest.java` | **Modifié** : 3 nouveaux tests (TELLER interdit, ADMIN autorisé, sourceRevenus manquante) + helper `customerId(MvcResult)` |
| `src/test/java/com/wince/fincore/customer/application/CustomerServiceTest.java` | **Modifié** : 2 nouveaux tests (sourceRevenus manquante / déclarée) + 4 appels de constructeur mis à jour |

## 3.5 Code clé

### a. Contrôle ADMIN au LEVEL_3 — `CustomerController.updateKyc`

```java
// RG-KYC-02 : « LEVEL_3 (justificatif de domicile + source de revenus
// déclarée) est validé par un ADMIN » — le TELLER est validé à
// LEVEL_2 seul, l'escalade à LEVEL_3 demande le rôle ADMIN.
if (request.kycLevel() == KycLevel.LEVEL_3 && !hasAuthority(Role.ADMIN)) {
    throw new FinCoreException(ErrorCode.ACCESS_DENIED,
            "Seul un ADMIN peut atteindre le LEVEL_3 (RG-KYC-02).");
}
return CustomerResponse.from(customerService.updateKyc(id, request));
```

- **Concept clé (RG-KYC-02)** : la matrice §5 autorise TELLER + ADMIN à appeler `PATCH` — mais la règle métier élargit le niveau de sollicitant : **LEVEL_2 = TELLER/ADMIN, LEVEL_3 = ADMIN seul**. La vérification de rôle est faite dans le contrôleur car elle dépend de *la valeur du body* — un `@PreAuthorize` statique ne peut pas exprimer ça.
- **Concept clé (RG-AUTH-06)** : l'autorité courante vient du claim `role` du JWT — `IdentitySecurityConfig` a déclaré `JwtGrantedAuthoritiesConverter` avec `authorityPrefix = "ROLE_"` sur le claim `role`, donc `hasAuthority(Role.ADMIN)` ⇔ `hasRole('ADMIN')` dans les SpEL — même source de vérité.

### b. Vérification RG-KYC-02 (source de revenus) — `CustomerService.updateKyc`

```java
if (request.kycLevel() == KycLevel.LEVEL_3
        && (request.sourceRevenus() == null || request.sourceRevenus().isBlank())) {
    throw new FinCoreException(ErrorCode.VALIDATION_FAILED,
            "Le passage au niveau LEVEL_3 requiert une source de revenus déclarée.",
            Map.of("errors", Map.of("sourceRevenus", "obligatoire")));
}
```

- **Concept clé** : le DTO valide la *forme* (`@NotNull` sur `kycLevel`) — les *règles métier* (obligation conditionnelle : doc + sourceRevenus au LEVEL_3) appartiennent au service (équivalent de `PasswordPolicy`, Phase 2 : « une seule classe qui sabre la logique métier — testable sans servlet ni container »).
- **Concept pédagogique (CA-KYC-01)** : la source de revenus est **requise** (RG-KYC-02) mais **jamais exposée** (§6.1 : colonne absente, §2.2 Phase 0 : « donnée sensible en log ») — elle voyage dans le body, non loguée, non persistée.

## 3.6 Comment vérifier

- **Suite complète** : `./mvnw verify` → `Tests run: 64, Failures: 0, Errors: 0` — inclut :
  - `KycEndpointTest.teller_cannot_reach_level_3` : TELLER + `LEVEL_3` → **403** `ACCESS_DENIED` (preuve HTTP de RG-KYC-02),
  - `KycEndpointTest.admin_reaches_level_3_with_declared_source_of_revenue` : ADMIN + `LEVEL_3` + `sourceRevenus` → **200**, `idDocNumberMasked == ****6789`, et la réponse **ne contient pas** le numéro clair,
  - `KycEndpointTest.admin_level_3_requires_declared_source_of_revenue` : ADMIN sans `sourceRevenus` → **400** `VALIDATION_FAILED` + `errors.sourceRevenus` non vide.
- **Commandes manuelles (rétro-vérifiables en live)** :
  ```powershell
  # 1. inscription (RG-AUTH-02 → 201, role CUSTOMER) :
  Invoke-WebRequest -Uri "http://localhost:8080/api/v1/auth/register" -Method Post `
      -ContentType "application/json" `
      -Body '{"email":"marie@fincore.test","firstName":"Marie","lastName":"Dupont","birthDate":"1990-01-05","phone":"0612345678","password":"Aa1!Aa1!Aa1!Aa1!"}'
  # 2. login → accessToken ; customer id via GET /customers/me
  # 3. PATCH /customers/{id}/kyc : TELLER → 403 ; ADMIN + sourceRevenus → 200
  ```
- **État en base (optionnel)** : `SELECT kyc_level, id_doc_number_masked FROM customer;` — seul le masqué apparaît.

## 3.7 Pièges rencontrés

1. **P3-1 (`KycEndpointTest`)** : `Unhandled exception type Exception` — le helper `customerId(MvcResult)` appelle `read(...)` (déclaré `throws Exception`) — il faut répéter la clause `throws Exception` sur le helper.
   *Indication pédagogique* : la signature d'un helper doit refléter les exceptions lancées par le code appelé.
2. **P3-2 (RG-KYC-02)** : avant la complémentation, rien ne refusait au TELLER de monter en LEVEL_3 — la règle n'était qu'implicite dans la javadoc de `KycLevel` — le test `teller_cannot_reach_level_3` (403) est le polymorphisme Analyse → Pratique de la règle.
3. **Test de mutation pédagogique (aucun commit)** : supprime localement le bloc ADMIN du `CustomerController` (le `if (request.kycLevel() == KycLevel.LEVEL_3 && !hasAuthority(Role.ADMIN))`), relance `./mvnw verify` — tu vois `KycEndpointTest.teller_cannot_reach_level_3` **rouge** (200 au lieu de 403) : ton test prouve que la règle est implémentée — puis **rétablis** le bloc.

## 3.8 Questions d'entretien — Phase 3

**Q1. À quoi sert la table `app_user` par rapport à `customer` ? (V3, RG-AUTH-06)**
`app_user` = données de *session* (email, BCrypt, rôle, verrou Phase 2), `customer` = données de *métier* — la relation est une clé FK UNIQUE (`user_id`) — deux entities séparées = SRP ; équivalent PHP/Symfony : un "two entities = two collapsed templates" (AppUser = one, Customer = other).

**Q2. Comment représenter un énuméré fermé statut (PENDING/VERIFIED/REJECTED) ?**
Énuméré Java `@Enumerated(EnumType.STRING)` + `CHECK` PostgreSQL dans V3 — portable, évoluable par migration (jamais modifier V3 une fois commitée — éditer l'enum + `ALTER TABLE` à la place).

**Q3. Comment faire atomique une montée de niveau (RG-KYC-02 à 05) ?**
`@Transactional` sur `CustomerService.updateKyc` (règle §2.2 Phase 0 : couche `application`) — équivalent du `@Transactional` sur `AuthService` (Phase 2).

**Q4. Comment tirer la ligne entre « majeur → LEVEL_2 (TELLER/ADMIN) » et « LEVEL_3 = ADMIN seul » ? (RG-KYC-02)**
C'est justement la nuance du RG-KYC-02 : LEVEL_2 = "assisté à un agent de guichet", LEVEL_3 = "déclaration d'activité représentée" — le Level-3 est un décret plus fort : seul un représentant ADMIN peut s'engager de la déclaration. Analogie : Phase 5 (F-09) — admin backing / Admin — ici : Admin with a declared revenue.

**Q5. Pourquoi le numéro d'identification n'est-il jamais stocké en clair (CA-KYC-01) ?**
SEUL `id_doc_number_masked` est dans la base (V3), jamais le numéro clair : un dump PostgreSQL / `psql` / `printf(Customer)` ne révèle rien. Le hash SHA-256 dans `maskIdDoc` est **transitoire** (`@Transient`) — le masqué suffit à l'exposition.

**Q6. Comment faire échouer volontairement un code pendant le dev (sans briser le projet) ?**
Le `git stash` / modification temporaire + `./mvnw verify` + `git stash pop` — ou mieux (vérifié ici) : supprimer un bloc, observer le test rouge, rétablir.

## 3.9 Exercice pour l'apprentissage

1. **Échouer volontairement en local (manipulations seulement)** : démarre l'app (Phase 0), appelle `PATCH /api/v1/customers/{id}/kyc` en TELLER — observe le **403** — rejoue la requête en ADMIN avec `sourceRevenus` — observe le **200** — tu viens de redécouvrir la règle de la §5 + RG-KYC-02 sans *briser* le code : tout se passe par une commande HTTP (`curl.exe`).
2. **Découvrir le type JUnit (tests existants)** : relance `./mvnw verify -Dtest=KycEndpointTest` — tu dois retrouver **9 tests verts** dans `target/surefire-reports/com.wince.fincore.customer.api.KycEndpointTest.txt` — grep `Tests run:`.
3. **Ajoute-toi un test** dans `KycEndpointTest` : un `AUDITOR` tente le `PATCH` sur un client — la matrice §5 ne donne aucun droit PATCH à l'AUDITOR — écris ton test (401 sans jeton, 403 avec jeton AUDITOR — même pattern que `matrice_des_droits_admin_autorise` de `AuthFlowTest`, Phase 2), relance `./mvnw verify` pour voir le résultat.
4. **Test de mutation pédagogique (aucun commit)** : supprime localement le bloc ADMIN du `CustomerController` (le `if (request.kycLevel() == KycLevel.LEVEL_3 && !hasAuthority(Role.ADMIN))`), relance `./mvnw verify` — tu vois `KycEndpointTest.teller_cannot_reach_level_3` **rouge** (200 au lieu de 403) : ton test prouve que la règle est implémentée — puis **rétablis** le bloc : **réapprendre : le test d'entraînement mène à la règle implémentée**.
