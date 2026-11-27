# Questions d'entretien - FinCore API

## Java

1. **Pourquoi BigDecimal et pas double pour l'argent ?**
   - `double` est binaire et perd en précision (0.1+0.2). `BigDecimal` est décimal exact. FinCore utilise `Money(amount, currency)` avec échelle fixe et arrondi HALF_EVEN.
   - Fichier : `shared/domain/Money.java`

2. **Pourquoi un record pour Money ?**
   - Immuabilité, equals/hashCode/toString générés, constructeur compact pour valider les invariants (montant non null, échelle normalisée).
   - Fichier : `shared/domain/Money.java`

3. **Pourquoi injecter un Clock au lieu de Instant.now() ?**
   - Testabilité et fuseau bancaire `Africa/Porto-Novo`. Les règles temporelles (cumul journalier, intérêts) deviennent reproductibles.
   - Fichier : `shared/infrastructure/ClockConfig.java`

4. **Pourquoi pas de Lombok ?**
   - Objectif pédagogique : voir le code réel, maîtriser les accesseurs, éviter la magie. Convention du projet (cahier §2.2).
   - Fichier : `AGENTS.md` / cahier §2.2

5. **Comment éviter le N+1 sur 10 000 lignes de relevé ?**
   - Charger les transactions par batch avec `findAllById`, pas un `findById` par ligne. Index sur `(account_id, id)`.
   - Fichier : `statement/application/StatementService.java`

## Spring Boot

6. **Comment fonctionne l'injection de dépendances ?**
   - Par constructeur uniquement (pas de `@Autowired` sur champs). Spring instancie les beans et les injecte. Équivalent conteneur de services Symfony.
   - Fichier : tous les services (`*Service.java`)

7. **Qu'est-ce qu'un profil Spring ?**
   - `dev` (seed, Swagger ouvert), `test` (Testcontainers), `prod` (logs JSON, Swagger désactivé). Activé via `spring.profiles.active`.
   - Fichier : `application.yml`

8. **Comment gérer les erreurs de façon uniforme ?**
   - `@RestControllerAdvice` + `ProblemDetail` (RFC 9457), code métier stable, traceId depuis MDC, timestamp via Clock.
   - Fichier : `shared/api/GlobalExceptionHandler.java`

9. **Comment sécuriser l'API ?**
   - Spring Security stateless, JWT HS256, `hasRole`, `@PreAuthorize`, 401 sans jeton, 403 rôle insuffisant.
   - Fichier : `shared/infrastructure/SecurityConfig.java`, `identity/infrastructure/IdentitySecurityConfig.java`

10. **Comment exposer Swagger ?**
    - springdoc-openapi, UI sur `/swagger-ui.html`, protégé/désactivé en prod via profil.
    - Fichier : `application.yml`, `shared/infrastructure/OpenApiConfig.java`

## JPA / Hibernate

11. **Pourquoi `ddl-auto: validate` et pas `update` ?**
    - Flyway est le seul propriétaire du schéma. Hibernate vérifie seulement la cohérence. Évite les modifications silencieuses en prod.
    - Fichier : `application.yml`

12. **Comment fonctionne le verrou optimiste ?**
    - Colonne `version` BIGINT incrémentée à chaque UPDATE. Conflit de version → exception. Filet de sécurité en plus du verrou pessimiste.
    - Fichier : `account/domain/Account.java`

13. **Pourquoi des triggers PostgreSQL pour l'immuabilité ?**
    - Interdisent UPDATE/DELETE sur `ledger_entry` et `audit_log` au niveau base, même via SQL direct. La correction passe par contre-passation.
    - Fichier : `V5__create_financial_transaction.sql`

14. **Comment éviter le N+1 ?**
    - `@EntityGraph`, fetch join ciblé, ou chargement par batch (`findAllById`). Index sur les FK.
    - Fichier : `statement/application/StatementService.java`

## Transactions et concurrence

15. **Comment garantir l'atomicité d'un virement ?**
    - `@Transactional` sur la couche application : transaction + écritures + mise à jour soldes dans une seule transaction DB. Tout COMMIT ou rien.
    - Fichier : `ledger/application/LedgerService.java`

16. **Comment éviter les deadlocks lors de virements croisés A→B et B→A ?**
    - Verrouillage `SELECT … FOR UPDATE` dans l'ordre croissant des UUID des comptes. Ainsi les deux transactions prennent les verrous dans le même ordre.
    - Fichier : `ledger/application/LedgerService.java#lockAccounts`

17. **Qu'est-ce que l'idempotence et comment l'implémenter ?**
    - Table `idempotency_record` avec contrainte UNIQUE `(user_id, idempotency_key)`. Même clé + même corps → rejeu exact (`Idempotent-Replayed: true`). Corps différent → 422. Clé en cours → 409.
    - Fichier : `payment/application/IdempotencyService.java`, `V6__...sql`

18. **Comment vérifier la partie double ?**
    - En code : somme des DEBIT = somme des CREDIT par devise avant tout INSERT. Violation → `LEDGER_UNBALANCED` + rollback.
    - Fichier : `ledger/application/LedgerService.java#apply`

19. **Comment fonctionne le chaînage de hachages ?**
    - `entry_hash = SHA-256(prev_hash | transaction_id | account_id | direction | amount | currency | created_at)`. `prev_hash` initial = 64 zéros. Vérification par recomputation.
    - Fichier : `ledger/application/LedgerService.java#computeHash`, `#verify`

20. **Comment gérer la concurrence sur un solde ?**
    - Verrou pessimiste (`@Lock(PESSIMISTIC_WRITE)`) + `EntityManager.refresh` sous verrou. Test de 50 virements simultanés : exactement 30 réussissent.
    - Fichier : `payment/application/TransferService.java`, `PaymentEndpointTest`

## Sécurité

21. **Comment empêcher l'énumération d'utilisateurs au login ?**
    - Message d'erreur identique pour utilisateur inconnu et mauvais mot de passe. Compteur d'échecs + verrouillage.
    - Fichier : `identity/application/AuthService.java`

22. **Comment fonctionne la rotation du refresh token ?**
    - Chaque utilisation invalide le token et en émet un nouveau (`replaced_by`). Réutilisation d'un token déjà utilisé révoque toute la famille.
    - Fichier : `identity/application/RefreshTokenService.java`

23. **Pourquoi retourner 404 et pas 403 pour un compte d'un autre client ?**
    - Ne pas révéler l'existence de la ressource. Règle §5 du cahier. Journalisé comme tentative d'accès refusé.
    - Fichier : `account/application/AccountService.java#findById`

24. **Comment masquer les données sensibles ?**
    - Numéro de pièce stocké masqué (`****1234`), jamais en clair dans l'API ni les logs. RG-KYC-03.
    - Fichier : `customer/domain/Customer.java`, `customer/application/CustomerService.java`

## Docker et livraison

25. **Comment fonctionne le Dockerfile multi-étapes ?**
    - Étape build : JDK + Maven Wrapper, dépendances cachées, `package`. Étape runtime : JRE, utilisateur non-root, healthcheck.
    - Fichier : `Dockerfile`

26. **Pourquoi `depends_on: condition: service_healthy` ?**
    - L'app attend que PostgreSQL soit prêt (pg_isready) avant de démarrer, évite les erreurs de connexion au boot.
    - Fichier : `docker-compose.yml`

27. **Comment fonctionne Flyway avec Docker ?**
    - Les migrations sont dans le classpath (`db/migration`), exécutées au démarrage de l'app. `ddl-auto: validate` vérifie le schéma.
    - Fichier : `application.yml`, `db/migration/`

## Conception bancaire

28. **Qu'est-ce que la partie double ?**
    - Toute transaction produit au moins deux écritures dont Σ débits = Σ crédits par devise. Le solde = Σ crédits − Σ débits. Les comptes système peuvent être négatifs.
    - Fichier : `docs/ARCHITECTURE.md`, `ledger/`

29. **Qu'est-ce qu'un compte système ?**
    - Caisse, produits de frais, position de change, compensation, suspense, charges d'intérêts. Créés au seed, suivent la même convention comptable.
    - Fichier : `V4__create_account.sql`, `account/domain/SystemCode.java`

30. **Comment fonctionne un virement externe asynchrone ?**
    - Création PENDING (débit source, crédit suspense). Scheduler traite : succès → suspense→settlement + COMPLETED ; échec → remboursement + FAILED. Outbox pour notifications.
    - Fichier : `payment/application/ExternalTransferService.java`

31. **Qu'est-ce que l'outbox pattern ?**
    - Écrire l'événement dans la même transaction que le métier, puis un dispatcher l'envoie. Évite la perte de message si le commit réussit mais la notification échoue.
    - Fichier : `notification/`, `V8__phase8_outbox.sql`

32. **Comment fonctionnent les alertes AML ?**
    - Évaluation asynchrone post-commit via `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`. 4 règles (montant, fractionnement, dormant). Ne bloque pas l'opération.
    - Fichier : `compliance/application/AmlService.java`, `AmlTransactionListener.java`

33. **Comment générer un relevé PDF fiable ?**
    - OpenPDF, tableau des mouvements, totaux, date de génération, empreinte SHA-256 du contenu en pied de page.
    - Fichier : `statement/api/PdfStatementGenerator.java`

34. **Comment fonctionne la réconciliation ?**
    - Recalcule le solde depuis le grand livre pour chaque compte et le compare à `account.balance`. Retourne les écarts (doit être vide).
    - Fichier : `admin/api/AdminReportController.java#reconcile`

35. **Comment calculer les intérêts d'épargne ?**
    - Job le 1er du mois à 02:00 (Africa/Porto-Novo) : solde moyen journalier × taux annuel × jours / 365. Idempotent via table `interest_run`.
    - Fichier : `account/application/InterestService.java`

## Questions transverses

36. **Quelle est la différence entre @Controller et @RestController ?**
    - `@RestController` = `@Controller` + `@ResponseBody` : retourne du JSON directement. Utilisé partout dans FinCore.
    - Fichier : tous les contrôleurs

37. **Qu'est-ce qu'un DTO record ?**
    - Record Java immuable, validation Bean Validation. Les entités JPA ne sortent jamais par l'API.
    - Fichier : `payment/api/*Command.java`, `*Response.java`

38. **Comment tester sans mocker la base ?**
    - Testcontainers PostgreSQL : base réelle pour les tests du grand livre. Règle du cahier : jamais mocker la DB pour le ledger.
    - Fichier : `TestcontainersConfiguration.java`, `PaymentEndpointTest.java`

39. **Comment versionner le schéma ?**
    - Flyway : scripts `V1__`, `V2__`… immuables une fois commités. Toute correction = nouvelle migration.
    - Fichier : `db/migration/`

40. **Comment observer l'application en production ?**
    - Actuator (`/actuator/health` liveness/readiness), Micrometer (métriques custom), logs JSON avec correlationId en profil prod.
    - Fichier : `application.yml`, `shared/infrastructure/CorrelationIdFilter.java`
