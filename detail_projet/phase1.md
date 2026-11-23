# Phase 1 — Socle transverse : Money, erreurs RFC 9457, correlationId, Swagger — ✅ FAIT

> **Statut : FAIT.** Squelette commun à tous les projets (`shared`), commits
> `f80daa7 feat: socle transverse phase 1…` + `a0790fa docs: guide phase 1`.

## 1.1 Objectif de la phase (3 lignes)

1. Créer le **domaine monétaire** (`Money`, `Currency`) : on ne peut plus additionner deux devises différentes en mémoire.
2. Normaliser **toutes** les erreurs au format **RFC 9457** (`application/problem+json`) avec un code métier stable (`ErrorCode`).
3. Corréler chaque requête : même identifiant dans la réponse, les logs et les erreurs (`X-Correlation-Id` / `traceId`), injecter `Clock` (pas d'`Instant.now()`), ouvrir Swagger UI en dev.

## 1.2 Notions à comprendre

| Concept | Analogie PHP/Symfony | Explication |
|---|---|---|
| **Value object** (`record`) | classe immuable en PHP 8.2 (`readonly`) | `Money` est un `record` Java : champs `final`, constructeur valide, `equals`/`hashCode` générés — par 10 lignes, sans le boilerplate. |
| `BigDecimal` vs `double` | `bcmath` vs `float` | Un `double` ne peut pas représenter 0.1 en mémoire — les banques refusent. `BigDecimal` = nombre décimal exact (comme `bcmath`). |
| **HALF_EVEN** (arrondi bancaire) | — | Arrondi vers le nombre **pair** (2,5 → 2 ; 3,5 → 4) : évite la dérive systématique sur des millions d'écritures. |
| `@RestControllerAdvice` | listener `kernel.exception` | Un seul endroit qui transforme **toutes** les exceptions en réponse HTTP. |
| **MDC** (Map Diagnostic Context) | contexte du logger | « Tableau magique » par thread (SLF4J) : `MDC.put("correlationId", …)` — chaque ligne de log contient automatiquement l'ID du client. |
| `OncePerRequestFilter` | middleware Laravel/Symfony | Filtre exécuté **une seule fois** par requête, avant le contrôleur. |

## 1.3 Commandes exécutées

| Commande | Ce qu'elle fait | Pourquoi l'avoir lancée | Importance |
|---|---|---|---|
| `.\mvnw.cmd -q -DskipTests compile` | Compile uniquement les sources, sans exécuter les tests (`-q` = quiet) | Aller-retour rapide après un changement : le compilateur pointe syntaxe/typage. | Importante (aller-retour) |
| `.\mvnw.cmd test "-Dtest=MoneyTest,CurrencyTest,CorrelationIdFilterTest,GlobalExceptionHandlerTest" "-Dsurefire.failIfNoSpecifiedTests=false"` | Exécute **uniquement** les classes nommées (surefire). **Piège PowerShell** : les `-D` contenant des points doivent être entre guillemets. | Vérifier qu'une nouvelle classe/test passe sans lancer la suite longue. | Importante |
| `.\mvnw.cmd verify` | Cycle complet : compile → tests → package (JAR). Inclut `FinCoreApplicationTests` (Testcontainers + contexte Spring) | Vérification complète à la fin de la phase. | ✅ CRITIQUE |
| `Start-Process .\mvnw.cmd -ArgumentList "spring-boot:run" -WindowStyle Hidden` | Démarre l'application dev (équivalent `symfony server:start`) | Vérifier que le contexte Spring entier démarre. | Importante |
| `Invoke-WebRequest http://localhost:8080/swagger-ui.html` et `http://localhost:8080/api-docs` (`curl -sS -o NUL -w "%{http_code}"`) | Laisse dev *ouvert* et vérifie que Swagger UI est accessible | Pré-requis de la phase (Swagger UI « ouvert en dev ») | Importante |
| `Invoke-WebRequest -Uri "http://localhost:8080/api-docs/v3/api-docs"` / `api-docs` | Bon point d'entrée OpenAPI | Vérifier que le contrat OpenAPI est bien déclenché (bean `OpenApiConfig`). | Importante |
| `curl.exe -sS -H "X-Correlation-Id: trace-essai-1" http://localhost:8080/no-such-path` | Provoque une erreur sans jeton — on observe le `traceId` propre | Prouve la correlation end-to-end : chaque erreur doit porter l'ID du client. | Importante |
| `curl.exe -sS -D - -o NUL http://localhost:8080/actuator/health` (`findstr X-Correlation-Id`) | Affiche la **réponse** de `/actuator/health` et filtre l'en-tête `X-Correlation-Id` | Vérifie que l'ID généré côté serveur est bien renvoyé dans l'en-tête de réponse (code de CorrelationIdFilter). | Importante |
| `curl.exe -sS http://localhost:8080/api/v1/x` (ou `/api/v1/__probe/admin-only`) | Appel d'un endpoint protégé sans jeton | Retourne `401` avec le corps RFC 9457 + `traceId` (CA-AUTH-04 de la phase 2). | Importante (procédure par le HTTP) |

## 1.4 Fichiers créés ou modifiés

| Fichier | Rôle |
|---|---|
| `shared/domain/Currency.java` | Enum `XOF(0)`, `EUR(2)`, `USD(2)` — échelle autorisée par devise. |
| `shared/domain/Money.java` | `record` monétaire : arrondi HALF_EVEN dans le constructeur, `parse()` qui **rejette** l'échelle excessive. |
| `shared/domain/ErrorCode.java` | Table exhaustive du §9 : code métier, statut HTTP, URI type — enum fermée pour forcer le traitement des cas (`switch`). |
| `shared/domain/FinCoreException.java` | Exception métier : transporte `ErrorCode` + détails structurés. |
| `shared/api/GlobalExceptionHandler.java` | `@RestControllerAdvice` : toute exception → `ProblemDetail` RFC 9457 (code/traceId/timestamp). |
| `shared/infrastructure/CorrelationIdFilter.java` | `OncePerRequestFilter` : lit/génère `X-Correlation-Id`, pose le MDC, nettoie en `finally`. |
| `shared/infrastructure/ClockConfig.java` | Bean `Clock` en `Africa/Porto-Novo` (siège BCEAO). |
| `shared/infrastructure/OpenApiConfig.java` | Document OpenAPI : titre, contact, schéma JWT `bearerAuth`. |
| `shared/infrastructure/SecurityConfig.java` | Phase 2 étendre (mais ancré) : health/Swagger publics, sinon 401/403 en `problem+json`. |
| `application.yml` | `logging.pattern.correlation` (`%X{correlationId:-}`), `spring.mvc.problemdetails: true`, springdoc off en prod. |
| `src/test/…/MoneyTest.java` | 10 + 2 + 3 tests : arrondis HALF_EVEN, signes, cross-currency, `parse()`, `plus()`, `multiply()`. |
| `src/test/…/CurrencyTest.java` | 3 tests : échelles et devises. |
| `src/test/…/CorrelationIdFilterTest.java` | 4 tests : génération, reprise (incoming header), nettoyage du MDC. |
| `src/test/…/GlobalExceptionHandlerTest.java` | 7 tests : statuts, codes, champs RFC 9457, masquage des 500 (pas de stack trace côté client). |

## 1.5 Code clé

### a. `Money` — le constructeur normalise tout

```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        // Le constructeur compact d'un record peut réaffecter les paramètres —
        // c'est LE point d'entrée unique où tous les invariants sont garantis.
        amount = amount.setScale(currency.scale(), RoundingMode.HALF_EVEN);
    }
}
```

**`parse()` vs `of()`** (distinction cruciale RG-DEP-03) : `Money.of()` *arrondit* (calculs internes), `Money.parse()` *rejette* (saisie API) — arrondir une saisie « 100.50 XOF » reviendrait à donner 50 sous au client sans qu'il sache.

### b. `CorrelationIdFilter` — `finally` obligatoire

```java
MDC.put(MDC_KEY, correlationId);
response.setHeader(HEADER, correlationId);
try {
    filterChain.doFilter(request, response);
} finally {
    // TOUJOURS en finally : les threads du pool sont réutilisés — sans
    // nettoyage, la requête suivante porterait ce traceId dans ses logs.
    MDC.remove(MDC_KEY);
}
```

### c. `application.yml` — le pattern de log corrélé

```yaml
logging:
  pattern:
    correlation: "%X{correlationId:-} "
    #             ^^^^^^^^^^^^^^^^^^ variable MDC, "-"" = valeur si absente
```

Chaque ligne de log begin par le même ID que celui renvoyé au client — **une plainte client = un `grep`** dans les logs.

## 1.6 Comment vérifier

- `.\mvnw.cmd verify` → `BUILD SUCCESS` (≈ 30 tests).
- `Invoke-WebRequest http://localhost:8080/swagger-ui.html` → 200 (dev).
- Corrélation : `curl.exe -sS -H "X-Correlation-Id: abc" http://localhost:8080/api/v1/x` → bien que ce soit une 401, le corps contient `"traceId":"abc"` — prouve que GlobalExceptionHandler et CorrelationIdFilter vont ensemble.

## 1.7 Pièges rencontrés

### P1-1 — `swagger-ui` / `api-docs` → 401 avant SecurityConfig (phase 2, mais traduit)
- **Cause** : Spring Security bloque **tout** par défaut.
- **Correction** : `SecurityConfig` avec `permitAll` sur health + swagger + api-docs, `anyRequest().authenticated()` pour le reste.
- **Leçon** : en Spring Security on n'ouvre pas — on **ferme** (le défaut est le bon).

### P1-2 — Sans jeton → 403 « Forbidden » au lieu de 401
- **Correction** : `.exceptionHandling()` avec `AuthenticationEntryPoint` → 401 `UNAUTHENTICATED`.
- **Leçon** : 401 = « prouvez qui vous êtes » ; 403 = « identifié mais refusé » — les confondre fait perdre des heures.

### P1-3 — `Found duplicate key logging` au démarrage
- **Erreur exacte** : SnakeYAML refuse deux clés `logging:` dans le même document YAML.
- **Cause** : ajout du bloc `logging.pattern.correlation` dans un nouveau document alors que `logging.level` existait déjà avant le `---`.
- **Correction** : merger dans un seul bloc par document (`---` = un fichier YAML à part entière).

### P1-4 — Mockito mock = headers fantômes
- **Correction** : utiliser `MockHttpServletRequest` / `MockHttpServletResponse` de Spring Test (le mock « réel »).

### P1-5 — `malloc failed` dans le fork Maven
- **Correction** : `Stop-Process` sur l'app Spring Boot avant `mvnw verify`.

### P1-6 — HALF_EVEN attendant le mauvais résultat
- `1250.50 → 1250` car 1250 est **pair**. Règle : HALF_EVEN ne signifie pas « arrondir vers le haut ».

## 1.8 Questions d'entretien — Phase 1

**Q1. Pourquoi un `record` Java pour `Money` et pas une classe ?**
Le `record` génère constructeur, accesseurs, `equals`, `hashCode`, `toString`, et interdit les champs non-`final`. Résultat : classe valeur sérialisable, comparable, immuable en ~10 lignes au lieu de ~80.

**Q2. Différence entre `Money.of()` et `Money.parse()` ?**
`of()` = entrée **interne** (arrondit, HALF_EVEN, calculs internes). `parse()` = entrée **utilisateur** (rejette l'échelle > devise, sinon on arrondirait sans prévenir le client).

**Q3. Pourquoi un `traceId` dans le corps d'erreur ?**
C'est le seul lien fiable entre une plainte client et les logs serveur : le client nous donne son traceId → `grep` dans ELK/Loki → ligne exacte. Il vient du MDC (même valeur que l'en-tête `X-Correlation-Id`).

**Q4. Pourquoi 401 et non 403 quand il n'y a pas de jeton ?**
401 (Unauthorized) : « je ne sais pas qui vous êtes, prouvez-le » — retourne le JWT. 403 (Forbidden) : « identifié, mais pas droits » — retourner le JWT ne changera rien. Une erreur de classification perd des heures au front.

**Q5. Pourquoi un `Clock` injecté au lieu de `Instant.now()` ?**
Reproductibilité des règles temporelles dans les tests : `Clock.fixed(...)` → scénarios déterministes (par ex. la limite de solde du tour précédent). Fuseau banking `Africa/Porto-Novo` (UTC+1, siège BCEAO).

**Q6. Où s'arrête `GlobalExceptionHandler` — que voit le client sur un 500 ?**
Jamais la stack trace : message générique + `traceId`. Le détail va en `log.error` côté serveur (OWASP A05:2021 — filtrer le message évite les fuites d'information).

## 1.9 Exercice pour l'apprentissage (Phase 1)

1. Écris un test provoquant une erreur (par ex. appelle `GlobalExceptionHandler` via un endpoint inexistant `/api/v1/x`) — vérifie que `traceId` du corp d'erreur est **identique** à l'en-tête `X-Correlation-Id` de la réponse : tu viens de prouver la corrélation bout-en-bout.
2. Modifie un test existant (par ex. `MoneyTest`) pour l'empêcher de failer volontairement (`assertThat(true).isFalse()`), relance `.\mvnw.cmd test` et localise le rapport HTML sous `target/surefire-reports/`.
3. Tentative d'intégration continue : ajoute au `pom.xml` le plugin `spotless` (formatage) et lance `.\mvnw.cmd spotless:check` — observe l'erreur de formatage et applique `spotless:apply` (phase 12 dans la réalité, mais bon à parcourir maintenant).
