# Phase 2 — F-01 : Authentification (inscription, login, refresh, logout) — ✅ FAIT

> **Statut : FAIT — suite verte.** Tous les tests de la phase passent :
> `Tests run: 46, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`
> (vérifié le 20/11/2026 avec `.\mvnw.cmd test`).

## 2.1 Objectif de la phase (3 lignes)

1. Prouver que la stack de sécurité de Boot 4 / Spring Security 7 fonctionne : JWT, resource server, rôles.
2. Implémenter la fonctionnalité **F-01** (RG-AUTH-01 à 06, CA-AUTH-01 à 04) : inscription publique, login avec vérification de mot de passe et verrouillage, refresh token rotatif, logout.
3. Mettre en place la matrice de droits de base (CUSTOMER vs autres rôles) via `SecurityFilterChain` — pour la phase 3 (K Client/KYC).

## 2.2 Notions à comprendre

- **JWT (JSON Web Token)** — chaîne composée de `header.payload.signature`, en base64url. Ici : signature **HS256** (HMAC-SHA256) avec une clé secrète **≥ 256 bits** fournie par variable d'environnement (`FINCORE_JWT_SECRET`). Analogie Symfony : le token d'accès de `lexik/jwt-authentication-bundle`, mais ici **sans bundle** : Spring Security 7 appelle directement notre `JwtDecoder`.
- **Resource server** — cette fois, Spring Security ne vérifie pas le token d'accès lui-même : il délègue à `JwtDecoder` (bean de `identity`). C'est le client qui **ne possède pas la clé de signature** : il peut envoyer le JWT, mais le serveur seul peut valider sa signature et ses dates.
- **Refresh token opaque** — jeton jeté au milieu, conservé uniquement de façon **hachée** (SHA-256) en base. "Opaque" = le serveur ne connaît pas sa structure. Comme le JWT est lisible par le client, on envoie un jeton à 256 bits (indevinable) au client et on ne stocke que son haché : une fuite de base ne permet pas de se connecter.
- **Anti-énumération (RG-AUTH-05)** — « utilisateur inconnu » et « mauvais mot de passe » renvoient **exactement** le même 401 (`Identifiants invalides.`). Pour compenser le timing (BCrypt est plus lent sur un vrai mot de passe), un mot de passe jetable est hashé à l'enregistrement — l'hachage dure le même temps dans les deux cas.
- **Timing attack** — attaque classique par comparaison de réponses lentes/rapides. Le verrouillage après 5 échecs (RG-AUTH-05) évite aussi que l'on puisse essayer indéfiniment : le compte est bloqué 15 min.

## 2.3 Commandes exécutées (reconstructed from the working tree + the run on 20/11/2026)

| Commande | Ce qu'elle fait | Pourquoi l'avoir lancée | Importance |
|---|---|---|---|
| `java -version` | Affiche la version du JDK installé (`27.0.1.0.3+1-2`) | Vérifier que le JDK est présent et entendu d'un compilateur. Le projet cible Java 25 (LTS) ; repli possible sur Java 21 (voir cahier départage §2.3) — on note ici que le poste tourne en 27 (non LTS, accepted for the exercise) | Importante |
| `docker version` | Affiche la version du client Docker (29.8.1, Desktop 4.93.0) et du serveur Docker Engine (29.8.1) | Vérifier que Docker Desktop tourne — indispensable aux Tests Testcontainers (base PostgreSQL éphémère pendant les tests). Une base absente = tests d'intégration qui ne passent pas | Importante |
| `.\mvnw.cmd test` | Compile et exécute **toutes** les tests Maven : unitaires + d'intégration (Testcontainers PostgreSQL 17) | **Exécution de la phase 2** : lance les tests AuthFlowTest + PasswordPolicyTest + suite classique, sur la vraie base via Testcontainers. Résultat : `Tests run: 46, Failures: 0, Errors: 0` + `BUILD SUCCESS` | ✅ CRITIQUE — la preuve par HTTP/SQL du respect de CA-AUTH-01 à 04 |

> **À faire manuellement quand tu démarres la phase 2** (commandes standards écrites dans `docs/GUIDE_DETAILLE.md` §2, à adapter) :
>
> ```powershell
> Invoke-WebRequest -Uri "https://start.spring.io/starter.zip?type=maven-project&language=java&groupId=com.wince&artifactId=fincore&packageName=com.wince.fincore&javaVersion=25&dependencies=web,security,oauth2-resource-server,data-jpa,validation,postgresql,flyway,actuator,devtools,testcontainers" -OutFile fincore.zip -UseBasicParsing
> Expand-Archive -Path fincore.zip -DestinationPath . -Force
> ```

## 2.4 Fichiers créés ou modifiés

| Fichier | Rôle |
|---|---|
| `src/main/java/com/wince/fincore/identity/api/AuthController.java` | Contrôleur public `POST /api/v1/auth/{register,login,refresh}` et `/auth/logout` — délage vers `AuthService` (DTO en `@Valid`, réponses `AuthResponse`) |
| `src/main/java/com/wince/fincore/identity/api/RegisterRequest.java` | DTO : `email` + `password` (Bean Validation) — RG-AUTH-02 (que des CUSTOMER) |
| `src/main/java/com/wince/fincore/identity/api/LoginRequest.java` | DTO : `email` + `password` (format e-mail valide, mais l'existence du compte **jamais** révélée, RG-AUTH-05) |
| `src/main/java/com/wince/fincore/identity/api/RefreshTokenRequest.java` | DTO : `refreshToken` — le jeton voyage dans le corps, pas en URL (pas dans les logs) |
| `src/main/java/com/wince/fincore/identity/api/AuthResponse.java` | Enregistrement (record) : `userId, email, role, tokenType, accessToken, expiresIn, refreshToken` |
| `src/main/java/com/wince/fincore/identity/application/AuthService.java` | Orchestre PasswordPolicy + JwtService + RefreshTokenService : inscription, login (verrouillage RG-AUTH-05), rotation, logout. Une seule classe qui sabre la logique métier — testable sans servlet ni container |
| `src/main/java/com/wince/fincore/identity/application/PasswordPolicy.java` | Politique de mot de passe (RG-AUTH-01 : ≥ 12 car., majuscule + minuscule + chiffre + spécial) — renvoie **toute la liste** des règles violées en une seule erreur 400 |
| `src/main/java/com/wince/fincore/identity/application/JwtService.java` | Émission des access tokens HS256 avec les claims `sub` (UUID), `role`, `iat`, `exp` (15 min), `jti` |
| `src/main/java/com/wince/fincore/identity/application/RefreshTokenService.java` | Génération (256 bits), rotation (invalide le jeton présenté + chaîne de suivi), révocation de famille (RG-AUTH-04), lookup par hash SHA-256 |
| `src/main/java/com/wince/fincore/identity/domain/AppUser.java` | Entité JPA `app_user` (email, passwordHash, role, enabled, failedAttempts, lockedUntil) + logique de verrouillage |
| `src/main/java/com/wince/fincore/identity/domain/RefreshToken.java` | Entité JPA `refresh_token` : id, user, tokenHash, expiresAt, revokedAt, replacedBy |
| `src/main/java/com/wince/fincore/identity/domain/Role.java` | Énumération `CUSTOMER / TELLER / ADMIN / AUDITOR` |
| `src/main/java/com/wince/fincore/identity/infrastructure/IdentitySecurityConfig.java` | Beans du resource server JWT : `JwtDecoder` + convertisseur du claim `role` → autorité `ROLE_CUSTOMER` (utilisé par `hasRole("ADMIN")`) |
| `src/main/java/com/wince/fincore/shared/infrastructure/SecurityConfig.java` | Chaîne de sécurité (phase 2) : `permitAll` sur `/auth/{register,login,refresh}` + santé/Swagger, `authenticated()` sinon, `oauth2ResourceServer` + `PasswordEncoder` bean (BCrypt 12) |
| `src/main/java/com/wince/fincore/shared/api/GlobalExceptionHandler.java` | + `@ExceptionHandler(AccessDeniedException)` : 403 `ACCESS_DENIED` correctement typé pour les règles de rôle déclenchées **dans** un contrôleur |
| `src/main/resources/db/migration/V2__create_refresh_token.sql` | Migration: table `refresh_token` (`id UUID PK`, `user_id FK`, `token_hash VARCHAR(64) UNIQUE`, `expires_at`, `revoked_at`, `replaced_by`) + index + CHECK sur la longueur du hash |
| `src/main/resources/application.yml` | + Propriétés `fincore.security.*` (secret, TTL 15 min, TTL refresh 7 j, 5 échecs, 15 min de verrou). En prod : `FINCORE_JWT_SECRET` sans valeur de repli (échec net au démarrage) |
| `src/main/java/com/wince/fincore/identity/repository/AppUserRepository.java` | `JpaRepository` : `findByEmail`, `existsByEmail` |
| `src/main/java/com/wince/fincore/identity/repository/RefreshTokenRepository.java` | `JpaRepository` : `findByTokenHash`, `revokeAllForUser` (UPDATE JPQL — révocation de famille en une requête) |
| `src/test/java/com/wince/fincore/identity/api/AuthFlowTest.java` | Tests d'intégration F-01 (CA-AUTH-01 à 04) sur PostgreSQL réel via MockMvc + Testcontainers |
| `src/test/java/com/wince/fincore/identity/application/PasswordPolicyTest.java` | Tests de PasswordPolicy |
| `.env.example` | + variables de sécurité (`FINCORE_JWT_SECRET`), écrite en phase 2 |

## 2.5 Code clé

### a. JWT — emission des access tokens (`JwtService.issueAccessToken`)

```java
JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("fincore")
        .subject(user.getId().toString())   // UUID de l'utilisateur (jamais l'email)
        .issuedAt(now)
        .expiresAt(now.plus(accessTokenTtl))  // 15 min (RG-AUTH-03)
        .claim("role", user.getRole().name()) // le claim que convertissent SecurityConfig + IdentitySecurityConfig
        .id(UUID.randomUUID().toString())      // jti
        .build();
return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(HS256).build(), claims))
        .getTokenValue();
```

**Ce qu'il faut retenir** : le JWT ne contient que des **données publiques** (identité + rôle). Le **secret** ne sert qu'à le signer — c'est un jeton d'accès court (15 min), pas une session.

### b. Rotation + famille (`RefreshTokenService.rotate`)

```java
// 1. lookup par hash SHA-256 du token présenté
RefreshToken current = requireValid(presentedToken);
// 2. reuse detection → révocation de toute la famille (dans une transaction REQUIRES_NEW)
if (current.isRevoked()) {
    revokeFamilyInNewTransaction.execute(s -> repository.revokeAllForUser(userId, now));
    throw new FinCoreException(ErrorCode.UNAUTHENTICATED, "Refresh token déjà utilisé…; session révoquée");
}
// 3. nouveau jeton + relier la chaîne
String newClearValue = issue(current.getUser());
current.revokeAsReplacedBy(successorId, now);
repository.save(current);
```

**Piège (CA-AUTH-03)** : la révocation de famille est **committée avant** que l'exception ne parte — sinon le `rollback` l'annulerait et le jeton volé resterait valide.

### c. Matrice de droits de base (`SecurityConfig.baseSecurityFilterChain`)

```java
.requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
.permitAll()                       // c'est ici qu'un jeton est obtenu — on ne peut pas le protéger
.anyRequest().authenticated()      // tout le reste requiert un jeton (401 sans jeton)
```

## 2.6 Comment vérifier

- ✅ `.\mvnw.cmd test` → `BUILD SUCCESS`, `Tests run: 46, Failures: 0`.
- En live (si la base docker tourne) :
  - `Invoke-WebRequest http://localhost:8080/actuator/health` → `200` `{"status":"UP"}`.
  - Décoder un JWT généré (on le fait en phase 3, avec `JwtDecoder.decode` ou sur [https://jwt.io](https://jwt.io)) — tu dois voir les claims `sub`, `role`, `iat`, `exp`, `jti` (RG-AUTH-06).
  - `curl.exe -sS http://localhost:8080/api/v1/__probe/admin-only` → 401 (authentification requise).

## 2.7 Pièges rencontrés (à consulter si tu bloques)

1. **Piège Spring Security 7** : « Jwt and Opaque Token are the only supported formats » → il faut déclarer explicitement `.jwt(Customizer.withDefaults())` dans `oauth2ResourceServer` — la simple présence d'un bean `JwtDecoder` ne suffit plus (corrigé dans `SecurityConfig`, ligne ~125).
2. **403 au lieu de 401** sur les endpoints protégés par `@PreAuthorize` quand l'authentification est un `AccessDeniedException` : il l'atteint **dans** le contrôleur, pas via le filter → il faut le `@ExceptionHandler(AccessDeniedException)` dans `GlobalExceptionHandler`.
3. **`FINCORE_JWT_SECRET` en prod** : le profil prod redéfinit la propriété sans valeur de repli — si la variable d'environnement n'est pas posée, l'app **refuse de démarrer** (le placeholder `${FINCORE_JWT_SECRET}` provoque un échec net). C'est voulu.
4. **Déclarer `jwt-secret` dans deux documents YAML** (bloc principal + bloc prod) : le dernier gagne en profil actif — avec un `---` dans `application.yml`, on a un document YAML à part entière par bloc.
5. Le `version` (verrou optimiste JPA) — pas encore utilisé en phase 2 (utilisé pour la phase 5 du grand livre).

## 2.8 Questions d'entretien — Phase 2

**Q1. Quelles sont les différences entre access token et refresh token dans FinCore ?**
Access token (JWT HS256, 15 min, lu et vérifié par le serveur via `JwtDecoder` dans le *resource server* Spring Security, pas modifiable par le client — ou peut-il ? HS256 seul approuvé, mais la police de vérification est le `JwtDecoder` beans du contexte) et refresh token (opaque, 7 j, client détient la valeur en clair, hachée en base).

**Q2. Pourquoi stocker uniquement le haché du refresh token ?**
Defense en profondeur : si la base est exfiltrée, l'attaquant obtient des SHA-256 indevinables — il ne peut ni les réutiliser, ni les éditer. Un mot de passe BCrypt se comporte pareillement, mais ici le jeton est aléatoire de 256 bits — un hachage non salé est donc suffisant.

**Q3. Comment fonctionne la rotation et la révocation de famille (RG-AUTH-04) ?**
À chaque `/auth/refresh` : le jeton présenté est révoqué (`revokedAt = now`, lié à son successeur via `replacedBy`) et un nouveau jeton est émis. Si un jeton déjà utilisé est présenté, tous les jetons encore actifs de l'utilisateur sont révoqués d'un coup (`UPDATE ... WHERE user_id = ? AND revokedAt IS NULL` dans une transaction `REQUIRES_NEW`) et l'API répond 401 — signal d'un vol de session.

**Q4. Pourquoi le verrouillage après 5 échecs (RG-AUTH-05) et un message identique en toutes situations ?**
5 échecs consécutifs → `lockedUntil = now + 15 min` — évite les attaques par force brute infinie. Le message identique pour inconnu/erreur de mot de passe limite l'énumération d'utilisateurs. Le timing est compensé par le `unknownUserHash`.

**Q5. Comment Spring Security vérifie-t-il l'access token ?**
Le client envoie `Authorization: Bearer <jwt>` — Spring Security le découpe (bearer), appelle notre `JwtDecoder` (bean déposé par `IdentitySecurityConfig`) : signature HS256 + dates, puis délage aux autorités via `JwtGrantedAuthoritiesConverter` sur le claim `role`.

**Q6. Pourquoi HS256 et pas RS256 ?**
Un seul émetteur/vérificateur (l'API elle-même) → une seule paire de clés suffit — pas de clés publiques à distribuer à des services tiers (contrairement aux services tiers qui doivent vérifier sans posséder la clé de signature).

**Q7. Comment le client utilise-t-il les tokens dans les requêtes ?**
`Authorization: Bearer <accessToken>` pour les endpoints protégés ; le refresh token se transmet dans le **corps** (pas en URL, pas en header) des appels `/auth/refresh` et `/auth/logout` — pendant lequels aucun jeton ne doit être nécessaire.

## 2.9 Exercice pour l'apprentissage (Phase 2)

1. Écris un test JUnit (dans `AuthFlowTest`, ou un nouveau `RefreshTokenServiceTest`) :
   - Vérifie que l'access token de l'utilisateur de test contient bien `sub == userId` et `role == "CUSTOMER"` (RG-AUTH-06) — c'est exactement ce qu'assure le test `rg_auth_06_claims_du_jwt` existant.
   - Alternative : relance `.\mvnw.cmd test -Dtest=AuthFlowTest` et vérifie que tu trouves bien 11 tests verts.
2. Entraîne-toi à la rotation : dans une requête manuelle (`curl.exe` ou MockMvc), appelle `POST /api/v1/auth/refresh` deux fois avec le même refresh token : la seconde appel renvoie **401** (CA-AUTH-03, replay détecté) — on a vérifié.
3. **Tu pourrais ajouter** (à ta guise) le test d'idempotence du login : un mot de passe trop court → 400 + `errors.password` non vide (CA-AUTH-01) — ce test est déjà fait par `ca_auth_01_rejet_d_un_mot_de_passe_faible`.
