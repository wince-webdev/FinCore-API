# 📖 Guide détaillé — Journal pédagogique FinCore API

> **Objectif** : pouvoir refaire chaque phase seul et l'expliquer en entretien.
> Le lecteur connaît **PHP/Symfony (ou Laravel)** et un peu de Java, mais n'a
> jamais utilisé Docker en pratique. Chaque notion est donc expliquée avec une
> analogie quand elle existe.

---

# PHASE 0 — Squelette du projet, Docker, Flyway, premier endpoint

## 0.1 Objectif de la phase

1. Mettre en place un squelette Spring Boot 4.1.2 / Java 25 qui **démarre**.
2. Faire tourner PostgreSQL 17 dans un conteneur Docker (aucune install manuelle de base).
3. Créer la première migration Flyway et prouver que `/actuator/health` répond `UP`.
4. Tout versionner avec Git (premier commit propre).

## 0.2 Notions à comprendre

### Spring Boot ≈ le « microkernel » de Symfony
En Symfony, `bin/console` charge le conteneur, les bundles, la config. Spring Boot
fait pareil : il **détecte** ce qui est sur le classpath (dans le `pom.xml`) et
configure tout à votre place. C'est de l'« auto-configuration » : si `spring-boot-starter-data-jpa`
est présent, Boot configure Hibernate automatiquement.

### Le conteneur d'injection de Spring ≈ le conteneur de services Symfony
`@Service`, `@Component` en Java = services YAML/autowiring en Symfony. La règle
d'or du projet : **injection par constructeur uniquement** (jamais `@Autowired`
sur les champs), comme le fait Symfony avec la promotion d'arguments.

### `pom.xml` ≈ `composer.json`
| PHP | Java/Maven |
|---|---|
| `composer require vendor/package` | `<dependency>` dans `pom.xml` |
| `composer.lock` | (géré par Maven à chaque build) |
| `vendor/` | `~/.m2/repository` (cache local) |
| `composer.json` scripts | plugins du `<build>` (ex. `spring-boot-maven-plugin`) |

**Maven** = le gestionnaire de paquets + le build. Il télécharge les dépendances
depuis **Maven Central** (l'équivalent de packagist.org).

### Maven Wrapper (`mvnw` / `mvnw.cmd`) ≈ `composer` local + `symfony` binary
`mvnw.cmd` est un script qui **télécharge Maven tout seul** (version 3.10.1, cf.
`.mvn/wrapper/maven-wrapper.properties`) au premier lancer. Personne n'a besoin
d'avoir Maven installé : le projet embarque son outil. Sur Windows on tape
`.\mvnw.cmd`, sous Linux/macOS `./mvnw`.

### Spring Initializr ≈ le « create-project » de Symfony/Laravel
`https://start.spring.io` = le générateur en ligne qui produit un ZIP contenant
le squelette (pom, classe principale, tests). Équivalent de
`symfony new mon-projet` ou `composer create-project`.

### Docker ≈ Vagrant, en mieux
- **Image** = machine virtuelle figée (ici `postgres:17`) ≈ une VM packagée.
- **Conteneur** = une instance de cette image qui tourne ≈ la VM démarrée.
- **Volume** = disque dur persistant ≈ un dossier partagé hôte/VM. Sans volume,
  toutes les données disparaissent à l'arrêt du conteneur.
- **`docker-compose.yml`** ≈ un `docker-compose.yaml`… justement, c'est le même
  concept que pour Laravel : on décrit les services (`db`, plus tard `app`) et
  `docker compose up` lance tout d'un bloc.
- **Healthcheck** = le conteneur se teste lui-même en boucle ; Docker ne déclare
  le service « prêt » que quand il répond.

### Flyway ≈ Doctrine Migrations
| Doctrine | Flyway |
|---|---|
| `make:migration` | on écrit à la main `V1__....sql` |
| `doctrine:migrations:migrate` | fait **automatiquement** au démarrage |
| table `migration_versions` | table `flyway_schema_history` |
| fichier `Version20261119...php` | `V1__init_app_user.sql` (numéro + nom) |

**Règle absolue** : une migration déjà commitée ne se modifie **jamais**. On
corrige avec une nouvelle (`V2__...`). Équivalent du rebase interdit en prod.

### `ddl-auto: validate` ≈ `doctrine.orm.auto_generate_classes: false` + validate
Hibernate peut créer ou mettre à jour le schéma tout seul (`create`, `update`)
— **c'est interdit ici**. Seule Flyway crée les tables. `validate` vérifie
uniquement que les entités Java correspondent au schéma SQL : si l'un des deux
dérape, le démarrage échoue au lieu de casser la base en silence.

### Actuator ≈ le bundle `framework-extra-bundle` + pages de santé
Spring Boot Actuator expose des endpoints de supervision. Le plus utile :
`/actuator/health` (répond `{"status":"UP"}`), utilisé par Docker pour savoir
si l'application est vivante.

### Testcontainers ≈ l'équivalent « la base en vrai »
Plutôt que de mocker la base (interdit par le cahier pour le grand livre),
Testcontainers **démarre un vrai PostgreSQL dans Docker** pendant les tests,
l'exécute, puis le détruit. C'est comme si votre CI lançait une VM éphémère
pour chaque suite de tests.

## 0.3 Commandes exécutées

### A. Vérification de l'environnement

```powershell
java -version
```
| | |
|---|---|
| **Ce qu'elle fait** | Affiche la version du JDK installé |
| **Options** | aucune |
| **Résultat attendu** | `java version "27..."` (le projet cible 25, on compile avec `--release 25`) |
| **Erreur fréquente** | `'java' n'est pas reconnu` → ajouter le JDK au PATH ou réinstaller |

```powershell
git --version
docker version --format "{{.Server.Version}}"
```
| | |
|---|---|
| **Ce qu'elle fait** | `git --version` : version de Git. `docker version` : contacte le **daemon** Docker (le service qui gère les conteneurs) et affiche sa version |
| **Options** | `--format "{{.Server.Version}}"` = n'affiche que le numéro du serveur (sortie propre, exploitable) |
| **Résultat attendu** | `git version 2.46...` et `29.8.1` |
| **Erreur fréquente** | `failed to connect to the docker API` = Docker Desktop n'est pas lancé → le démarrer |

```powershell
Start-Process -FilePath "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe"
```
| | |
|---|---|
| **Ce qu'elle fait** | Démarre Docker Desktop **en arrière-plan** (`Start-Process` = l'équivalent de `exec` détaché) |
| **Options** | chemin variable d'environnement `$env:LOCALAPPDATA` = emplacement utilisateur |
| **Résultat attendu** | Le daemon répond après 20-60 s (barre de tâche Docker) |
| **Erreur fréquente** | Lancer Docker **avant** tout `docker compose` |

### B. Génération du squelette

```powershell
Invoke-WebRequest -Uri "https://start.spring.io/starter.zip?..." -OutFile fincore.zip -UseBasicParsing
```
| | |
|---|---|
| **Ce qu'elle fait** | Télécharge le ZIP du projet depuis Spring Initializr (équivalent `curl -o`) |
| **Paramètres clés** | `type=maven-project` (build Maven), `language=java`, `groupId=com.wince` (nom de l'organisation), `artifactId=fincore` (nom du projet), `packageName=com.wince.fincore` (paquet racine), `javaVersion=25`, `bootVersion=4.1.2.RELEASE`*, `dependencies=web,security,...` |
| **Résultat attendu** | `fincore.zip` (~17 Ko) |
| **Erreur fréquente** | URL mal encodée → vérifier que `-d`/`-d` séparent bien clé/valeur |

\* **Piège P0-1** : Initializr annonce `4.1.2.RELEASE` mais Maven Central publie
`4.1.2` (le suffixe `.RELEASE` a été abandonné depuis Boot 3). Corrigé dans le `pom.xml`.

```powershell
Expand-Archive -Path fincore.zip -DestinationPath . -Force
```
| | |
|---|---|
| **Ce qu'elle fait** | Décompresse le ZIP (équivalent de 7-Zip / `unzip`) |
| **Options** | `-Force` = écrase si le dossier existe déjà |
| **Résultat attendu** | Dossier `fincore/` contenant `pom.xml`, `mvnw.cmd`, `src/` |

### C. Docker

```powershell
docker compose up -d db
```
| | |
|---|---|
| **Ce qu'elle fait** | Crée et démarre **uniquement** le service `db` défini dans `docker-compose.yml` |
| **Options** | `-d` = *detached* : la commande rend la main, le conteneur tourne en arrière-plan |
| **Résultat attendu** | `Container fincore-db Started` puis `healthy` après ~10 s |
| **Erreur fréquente** | `port is already allocated` = un autre service occupe le port → changer `POSTGRES_PORT` |

```powershell
docker compose ps
```
| | |
|---|---|
| **Ce qu'elle fait** | Liste les conteneurs du projet courant avec leur état |
| **Résultat attendu** | `fincore-db ... (healthy) ... 0.0.0.0:5433->5432/tcp` |

```powershell
docker exec fincore-db psql -U fincore -d fincore -c "\du"
```
| | |
|---|---|
| **Ce qu'elle fait** | `docker exec` = exécute une commande **dans** le conteneur. Ici `psql` liste les rôles PostgreSQL (`\du`) |
| **Options** | `-U fincore` = utilisateur, `-d fincore` = base, `-c` = commande SQL |
| **Résultat attendu** | Une ligne rôle `fincore` |

### D. Build et tests

```powershell
.\mvnw.cmd verify
```
| | |
|---|---|
| **Ce qu'elle fait** | Exécute le cycle complet Maven : compile → tests unitaires → tests d'intégration → package → vérification |
| **Étapes internes** | `test` tourne `FinCoreApplicationTests` qui démarre un PostgreSQL Testcontainers, exécute Flyway, charge le contexte Spring, puis détruit la base |
| **Résultat attendu** | `BUILD SUCCESS` / `Tests run: 1, Failures: 0, Errors: 0` |
| **Erreur fréquente** | `Non-resolvable parent POM` → version du parent inexistante (voir P0-1) ; `role does not exist` → mauvais PostgreSQL (voir P0-3) |

```powershell
.\mvnw.cmd verify -U
```
| | |
|---|---|
| **Ce qu'elle fait** | Idem + `-U` = *update* : force Maven à **re-télécharger** même les échecs mis en cache (`*.lastUpdated`) |
| **Quand l'utiliser** | Après avoir corrigé un `pom.xml` : sinon Maven croit encore que l'artefact n'existe pas |

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```
| | |
|---|---|
| **Ce qu'elle fait** | Compile et **démarre** l'application (équivalent `symfony server:start`) |
| **Options** | `-Dspring-boot.run.profiles=dev` = active le profil `dev` (équivalent `APP_ENV=dev`) |
| **Résultat attendu** | Ligne `Started FinCoreApplication` + port 8080 |

### E. Vérification HTTP

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing
```
| | |
|---|---|
| **Ce qu'elle fait** | Envoie une requête GET (équivalent `curl` ou le client HTTP de PhpStorm) |
| **Résultat attendu** | `200` + `{"groups":["liveness","readiness"],"status":"UP"}` |
| **Erreur fréquente** | `Connection refused` = l'app n'est pas encore démarrée (attendre 15-30 s) |

### F. Git

```powershell
git init
git add -A
git commit -m "chore: initialiser le squelette FinCore (phase 0)"
```
| | |
|---|---|
| **Ce qu'elle fait** | `init` crée le dépôt ; `add -A` stage tout (respectant `.gitignore`) ; `commit` fige l'état |
| **Convention** | **Conventional Commits** imposée par le cahier : `feat:`, `test:`, `docs:`, `chore:`… |
| **Erreur fréquente** | `Please tell me who you are` → `git config user.name "..."` + `git config user.email "..."` |

## 0.4 Fichiers créés ou modifiés

| Fichier | Rôle |
|---|---|
| `pom.xml` | Dépendances Maven + plugin Boot ; **corrigé** : parent `4.1.2`, springdoc `-starter-` |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | Maven Wrapper (build sans Maven installé) |
| `src/main/java/.../FinorianCalendar.java`* | Classe principale `@SpringBootApplication` — point d'entrée |
| `src/main/resources/application.yml` | Configuration : datasource, JPA `validate`, Flyway, profils `dev`/`test`/`prod` |
| `src/main/resources/db/migration/V1__init_app_user.sql` | 1ʳᵉ migration : table `app_user` + contraintes |
| `docker-compose.yml` | Service `db` (postgres:17, port **5433**, healthcheck, volume, réseau) |
| `.env.example` | Modèle de variables (jamais de secret réel en Git) |
| `.gitignore` | Ignore `.env`, `target/`, IDE… |
| `src/test/.../TestcontainersConfiguration.java` | Conteneur PostgreSQL de test (`postgres:17` au lieu de `latest`) |
| `src/{main,test}/java/.../<module>/package-info.java` ×10 | Arborescence des modules (docs + suivi Git) |
| `docs/GUIDE_DETAILLE.md` | Ce fichier |

\* le fichier réel s'appelle `FinCoreApplication.java`.

## 0.5 Code clé commenté

### `application.yml` — le cœur de la configuration
```yaml
spring:
  profiles:
    default: dev          # profil actif si aucun n'est imposé
  datasource:
    url: ${FINCORE_DB_URL:jdbc:postgresql://localhost:5433/fincore}
    #  ^^^ ${VAR:defaut} = lit la variable d'environnement VAR,
    #      sinon utilise la valeur par défaut après les deux-points.
    #      Équivalent Symfony : %env(resolve:DATABASE_URL)% avec fallback.
  jpa:
    hibernate:
      ddl-auto: validate  # Hibernate NE crée rien, il vérifie seulement
    open-in-view: false   # pas de requête lazy cachée dans un contrôleur
  flyway:
    enabled: true         # exécute V1__*.sql au démarrage si pas encore fait
```

### `V1__init_app_user.sql` — la migration
```sql
CREATE TABLE app_user (
    id              UUID PRIMARY KEY,          -- identifiant universel (pas de BIGSERIAL)
    email           VARCHAR(320) NOT NULL,     -- 320 = longueur max d'un email (RFC 5321)
    password_hash   VARCHAR(100) NOT NULL,     -- jamais le mot de passe en clair !
    role            VARCHAR(20) NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,               -- horodatage avec fuseau (UTC)
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_role CHECK (role IN ('CUSTOMER','TELLER','ADMIN','AUDITOR'))
);
```
**Pourquoi `TIMESTAMPTZ` et pas `TIMESTAMP`** ? Le cahier impose des horodatages
UTC ; `TIMESTAMPTZ` stocke en UTC et convertit à l'affichage — zéro confusion
de fuseau, exactement comme `datetime` immuable en PHP.

**Pourquoi `VARCHAR + CHECK` et pas un `enum` PostgreSQL** ? Un enum natif est
lourd à faire évoluer (migration complexe) ; un `CHECK` s'ajoute en une ligne.

### `docker-compose.yml` — le service base
```yaml
services:
  db:
    image: postgres:17            # image officielle, tag figé (jamais "latest")
    ports:
      - "${POSTGRES_PORT:-5433}:5432"
      #        host:conteneur ──┘    └─ le conteneur écoute toujours sur 5432
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER:-fincore} ..."]
      # $$ = échappe pour Docker Compose → le $ reste pour le shell du conteneur
```

## 0.6 Comment vérifier

```powershell
# 1. La base tourne
docker compose ps                    # → (healthy)

# 2. Les tests passent
.\mvnw.cmd verify                    # → BUILD SUCCESS

# 3. L'app répond
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
# puis, dans un 2e terminal :
Invoke-WebRequest http://localhost:8080/actuator/health
# → {"status":"UP"}

# 4. La migration a été exécutée
docker exec fincore-db psql -U fincore -d fincore -c "SELECT * FROM flyway_schema_history;"
# → 1 row : V1, success=true
```

## 0.7 Pièges rencontrés

### P0-1 — `Non-resolvable parent POM : 4.1.2.RELEASE`
- **Erreur exacte** : `spring-boot-starter-parent:pom:4.1.2.RELEASE was not found`
- **Cause** : Initializr annonce encore le suffixe `.RELEASE`, mais Maven Central
  ne publie que `4.1.2` (vérifié : `https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/` → `4.1.2/` existe, `4.1.2.RELEASE/` → 404).
- **Correction** : `<version>4.1.2</version>` dans le parent du `pom.xml`, puis `.\mvnw.cmd verify -U` (`-U` vide le cache d'échec).
- **Leçon** : toujours vérifier qu'une version existe sur Maven Central avant de
  blâmer le réseau.

### P0-2 — `No enum constant ... SerializationFeature.write-dates-as-timestamps`
- **Erreur exacte** : échec de liaison de `spring.jackson.serialization` au démarrage des tests.
- **Cause** : **Boot 4 = Jackson 3** (package `tools.jackson`). La feature
  `WRITE_DATES_AS_TIMESTAMPS` a été **supprimée** : les dates sont toujours en
  ISO-8601. La config héritée d'exemples Boot 3 casse.
- **Correction** : supprimer le bloc `spring.jackson.serialization` du `application.yml`.
- **Leçon** (directive §2.3 du cahier) : **ne jamais deviner une API de
  librairie à partir d'exemples anciens** — Boot 4 ≠ Boot 3.

### P0-3 — `FATAL: le rôle « fincore » n'existe pas`
- **Erreur exacte** : échec Flyway/Hikari au démarrage de l'app alors que le
  conteneur est sain.
- **Cause** : **deux PostgreSQL sur le même port** — le poste a Laragon qui
  tourne déjà un PostgreSQL sur `127.0.0.1:5432`. `localhost` résout d'abord en
  `127.0.0.1` → l'application atteint le PostgreSQL de Laragon (sans le rôle
  `fincore`), pas celui de Docker (`0.0.0.0:5433`).
- **Correction** : passer FinCore sur le port **5433** dans `docker-compose.yml`,
  `.env.example` et `application.yml`.
- **Leçon** : en cas de doute, `netstat -ano | findstr :5432` révèle qui écoute
  sur quel port. Ne jamais supposer que `localhost` = le conteneur Docker.

### P0-4 — L'artefact springdoc d'Initializr est introuvable
- **Erreur** : `org.springdoc:openapi-starter-webmvc-ui:3.2.0` → 404 sur Central.
- **Correction** : l'artefact réel est `org.springdoc:springdoc-openapi-starter-webmvc-ui`
  (avec le préfixe `springdoc-`). Vérification faite : son parent POM est
  `spring-boot-starter-parent:4.1.2` → pleinement compatible Boot 4.1.

## 0.8 Questions d'entretien (Phase 0)

**Q1. Quelle différence entre `mvn` et `mvnw` ?**
`mvnw` (Maven Wrapper) embarque la version exacte de Maven utilisée par le
projet : n'importe quel développeur (ou CI) build avec la même version sans
rien installer. C'est l'équivalent d'un `composer.phar` commité.

**Q2. Pourquoi `ddl-auto: validate` et pas `update` ?**
Parce que le schéma appartient **exclusivement** à Flyway. Laisser Hibernate
modifier les tables en démarrage provoquerait des divergences silencieuses entre
environnements. `validate` casse au démarrage si le code et le SQL divergent —
c'est voulu : échouer tôt et fort.

**Q3. Qu'est-ce qu'un healthcheck dans Docker et à quoi ça sert ?**
Une commande exécutée périodiquement dans le conteneur (`pg_isready` ici).
Tant qu'elle échoue, l'état est `starting` ; Docker Compose peut alors **attendre**
la santé (`depends_on: condition: service_healthy`) avant de lancer l'app — on
évite la course au démarrage « app avant la base ».

**Q4. Pourquoi un port différent (5433) au lieu d'arrêter le PostgreSQL de Laragon ?**
Indépendance : le poste du développeur peut héberger d'autres projets. Chaque
projet Docker réserve son port — c'est le principe de non-régression de
l'environnement. `netstat` identifie toujours les conflits.

## 0.9 Exercice pour le candidat

1. Ajoutez une **2ᵉ migration** `V2__add_phone_to_app_user.sql` qui ajoute une
   colonne `phone VARCHAR(20)` à `app_user`, relancez `.\mvnw.cmd verify` et
   vérifiez avec `psql` que la colonne existe dans le conteneur de test…
   puis dans la base `docker compose` (relancez l'app : Flyway l'applique aussi).
2. **Ne modifiez surtout pas** `V1__...` : c'est la règle d'or.
3. Bonus : ajoutez `GET /actuator/info` avec `info.app.name=FinCore` dans
   `application.yml` et observez la différence.

---

---

# PHASE 1 — Socle transverse : Money, erreurs RFC 9457, correlationId, Swagger

## 1.1 Objectif de la phase

1. Créer le **domaine monétaire** (`Money`, `Currency`) : impossible d'additionner deux devises différentes.
2. Normaliser **toutes** les erreurs au format RFC 9457 (`application/problem+json`) avec un code métier stable.
3. Corréler chaque requête : même identifiant dans la réponse, les logs et les erreurs (`X-Correlation-Id` / `traceId`).
4. Servir **Swagger UI** en dev ; tout le reste reste refusé sans jeton (401).
5. Horloge injectable (`Clock`) — jamais `Instant.now()` dans les services.

## 1.2 Notions à comprendre

### Value object (`record`) ˜ classe immuable Symfony
`Money` est un **record** Java : les champs sont `final`, le constructeur valide,
`equals`/`hashCode` sont générés. C'est l'équivalent d'un value object
`readonly` en PHP 8.2 — mais avec **zéro boilerplate**.

### `BigDecimal` vs `float` ˜ `bcmath` vs `float` en PHP
Un `double` ne peut pas représenter exactement `0.1` : les banques refusent.
`BigDecimal` est un nombre décimal exact (comme `bcmath`), et l'**arrondi
HALF_EVEN** (« bancaire ») évite la dérive systématique : on arrondit vers le
nombre **pair** (2,5 ? 2 ; 3,5 ? 4), donc sur des millions d'opérations la
somme des arrondis reste nulle.

### `@RestControllerAdvice` ˜ listener `kernel.exception` Symfony
Un seul endroit qui transforme **toutes** les exceptions en réponse HTTP.
L'équivalent exact est un listener sur l'événement `kernel.exception` qui
retourne un `JsonErrorResponse`.

### RFC 9457 = standard « problem+json »
```json
{ "type": "…/errors/insufficient-funds", "title": "Solde insuffisant",
  "status": 422, "detail": "…", "instance": "/api/v1/…",
  "code": "INSUFFICIENT_FUNDS", "traceId": "…", "timestamp": "…" }
```
Le champ **`code`** est la clé : stable, en majuscules, sur lequel le front
branche sa logique. Le `detail` (français) peut changer sans casser le client.
Équivalent Symfony : les formats d'erreur normalisés du bundle API Platform.

### `OncePerRequestFilter` ˜ middleware Laravel/Symfony
Un filtre exécuté **une seule fois** par requête, avant le contrôleur.
Ici il pose le `correlationId` dans le **MDC** (Map Diagnostic Context de
SLF4J) : c'est un « tableau magique » par thread, vidé en `finally`.

### `MDC` ˜ le contexte du logger (monolog processor)
En Symfony on ajoute un *processor* Monolog pour injecter un UUID dans chaque
ligne. Ici `MDC.put("correlationId", …)` fait pareil : la clé apparaît
automatiquement dans le pattern de log configuré par `logging.pattern.correlation`.

### `Clock` ˜ injecter une horloge plutôt que `time()`
Règle d'or testable : on n'appelle jamais `Instant.now()` dans un service —
on **injecte** un `Clock`. En test, on fournit `Clock.fixed(…)` : les
scénarios « cumul du jour » deviennent déterministes. Fuseau bancaire :
`Africa/Porto-Novo` (UTC+1, siège BCEAO) — le stockage reste en UTC.

### Spring Security par défaut ˜ `access_control` tout à `IS_AUTHENTICATED_FULLY`
Sans configuration, Spring Security **verrouille tout** (c'est pourquoi
`/api-docs` répondait 401 en phase 0). `SecurityConfig` ouvre
ponctuellement `health` + `swagger`, et le reste exige un jeton ? **401**
(et non 403 : 403 dirait « vous êtes connecté mais sans droits », alors
qu'ici il n'y a même pas de jeton — CA-AUTH-04).

## 1.3 Commandes exécutées

### A. Compilation rapide (aller-retour)
```powershell
.\mvnw.cmd -q -DskipTests compile
```
| | |
|---|---|
| **Ce qu'elle fait** | Compile uniquement les sources **sans** exécuter les tests |
| **Options** | `-q` = quiet (n'affiche que les erreurs) ; `-DskipTests` = saute les tests |
| **Résultat attendu** | Aucune sortie = succès |
| **Quand l'utiliser** | Vérifier qu'une erreur de syntaxe/typage est corrigée avant de lancer les tests complets |

### B. Tests ciblés
```powershell
.\mvnw.cmd test "-Dtest=MoneyTest,CurrencyTest,CorrelationIdFilterTest,GlobalExceptionHandlerTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```
| | |
|---|---|
| **Ce qu'elle fait** | N'exécute **que** les classes nommées (surefire = plugin de tests Maven) |
| **Options** | `-Dtest=` liste les classes (séparées par `,`) ; `-Dsurefire.failIfNoSpecifiedTests=false` évite l'échec si un nom ne matche rien |
| **Piège PowerShell** | Les `-D` contenant des points doivent être **entre guillemets** : sans quotes, PowerShell coupe au `.` et Maven voit « phase inconnue » |
| **Résultat attendu** | `Tests run: 29, Failures: 0` |

### C. Build complet
```powershell
.\mvnw.cmd verify
```
| | |
|---|---|
| **Ce qu'elle fait** | Cycle complet : compile ? tests ? package (JAR) ; inclut `FinCoreApplicationTests` (Testcontainers + contexte réel) |
| **Résultat attendu** | `BUILD SUCCESS` / `Tests run: 30, Failures: 0, Errors: 0` |
| **Erreur fréquente** | `forked VM terminated` + `malloc failed` = **mémoire épuisée** ? fermer l'app dev (`Stop-Process`) avant de relancer les tests |

### D. Redémarrage de l'app + vérifications
```powershell
# Redémarrage (l'app lancée en phase 0 doit être arrêtée d'abord)
Start-Process .\mvnw.cmd -ArgumentList "spring-boot:run" -WindowStyle Hidden

# Swagger + contrat ouverts en dev
curl.exe -sS -o NUL -w "%{http_code}" http://localhost:8080/swagger-ui.html   # ? 200
curl.exe -sS -o NUL -w "%{http_code}" http://localhost:8080/api-docs          # ? 200

# 401 + corps RFC 9457 + correlationId propagé (CA-AUTH-04)
curl.exe -sS -H "X-Correlation-Id: trace-essai-1" http://localhost:8080/no-such-path
# ? {"type":"…/errors/unauthenticated","code":"UNAUTHENTICATED","traceId":"trace-essai-1",…}

# Le même identifiant ressort en en-tête de réponse
curl.exe -sS -D - -o NUL http://localhost:8080/actuator/health | findstr X-Correlation-Id
```

## 1.4 Fichiers créés ou modifiés

| Fichier | Rôle |
|---|---|
| `shared/domain/Currency.java` | Enum XOF(0)/EUR(2)/USD(2) + échelle autorisée |
| `shared/domain/Money.java` | Value object monétaire : arrondi HALF_EVEN, `parse()` rejette l'échelle excessive |
| `shared/domain/ErrorCode.java` | Table exhaustive des codes métier ? statut HTTP + URI type |
| `shared/domain/FinCoreException.java` | Exception métier transportant le `ErrorCode` + détails structurés |
| `shared/api/GlobalExceptionHandler.java` | `@RestControllerAdvice` : tout ? ProblemDetail RFC 9457 (traceId, timestamp via Clock) |
| `shared/infrastructure/CorrelationIdFilter.java` | Lit/génère `X-Correlation-Id`, MDC, nettoyage en `finally` |
| `shared/infrastructure/ClockConfig.java` | Bean `Clock` en `Africa/Porto-Novo` |
| `shared/infrastructure/OpenApiConfig.java` | Document OpenAPI : titre, contact, schéma JWT `bearerAuth` |
| `shared/infrastructure/SecurityConfig.java` | Health/Swagger publics ; sinon 401/403 en `problem+json` |
| `application.yml` | `logging.pattern.correlation`, `spring.mvc.problemdetails`, springdoc off en prod |
| `src/test/…/MoneyTest.java` | 15 tests : arrondis, signes, cross-currency, `parse()` |
| `src/test/…/CurrencyTest.java` | 3 tests : échelles et devises |
| `src/test/…/CorrelationIdFilterTest.java` | 4 tests : génération, reprise, nettoyage du MDC |
| `src/test/…/GlobalExceptionHandlerTest.java` | 7 tests : statuts, codes, champs RFC 9457, masquage des 500 |

## 1.5 Code clé commenté

### `Money` — le constructeur normalise tout
```java
public Money(BigDecimal amount, Currency currency) {
    Objects.requireNonNull(amount, "…");
    Objects.requireNonNull(currency, "…");
    // On arrondit ICI : le domaine ne stocke jamais une échelle « sale ».
    amount = amount.setScale(currency.scale(), RoundingMode.HALF_EVEN);
}
```
Le constructeur compact d'un `record` peut **réaffecter** les paramètres —
c'est le point d'entrée unique où tous les invariants sont garantis.

### `parse()` — rejet explicite pour la saisie utilisateur
```java
if (value.scale() > currency.scale()) {
    throw new FinCoreException(ErrorCode.INVALID_AMOUNT_SCALE,
            "La devise XOF autorise au plus 0 décimale(s)…");
}
```
**Distinction cruciale** : `Money.of()` *arrondit* (calculs internes),
`Money.parse()` *rejette* (saisie API, RG-DEP-03 ? 400). Arrondir silencieusement
une saisie reviendrait à voler 0,50 XOF au client sans le prévenir.

### `CorrelationIdFilter` — le `finally` obligatoire
```java
MDC.put(MDC_KEY, correlationId);
response.setHeader(HEADER, correlationId);
try {
    filterChain.doFilter(request, response);
} finally {
    MDC.remove(MDC_KEY);   // TOUJOURS : sinon le thread suivant hérite de l'ID
}
```
Les threads Tomcat sont **réutilisés** : sans `finally`, la requête 42
porterait le correlationId de la requête 41 dans ses logs — erreur de
diagnostic très dure à trouver.

### `application.yml` — le pattern de log corrélé
```yaml
logging:
  pattern:
    correlation: "%X{correlationId:-} "
    #             ^^^^^^^^^^^^^^^^^^ variable MDC, "-"" = valeur si absente
```
Chaque ligne de log commence par le même `traceId` que celui renvoyé au
client dans l'erreur : une plainte = une `grep`.

## 1.6 Comment vérifier

```powershell
.\mvnw.cmd verify                                  # ? 30 tests, BUILD SUCCESS
curl.exe -sS -o NUL -w "%{http_code}`n" http://localhost:8080/swagger-ui.html   # 200
curl.exe -sS -H "X-Correlation-Id: abc" http://localhost:8080/api/v1/x | jq .code # "UNAUTHENTICATED"
```

## 1.7 Pièges rencontrés

### P1-1 — `swagger-ui` / `api-docs` ? 401 (avant SecurityConfig)
- **Cause** : Spring Security (démarré au P0-0) verrouille **tout** par défaut.
- **Correction** : `SecurityConfig` avec `permitAll` sur `health` + `swagger` + `api-docs`, `anyRequest().authenticated()` pour le reste.
- **Leçon** : en Spring Security, on n'ouvre pas, on **ferme** — le défaut (verrouillé) est le bon.

### P1-2 — Sans jeton ? 403 « Forbidden » au lieu de 401
- **Cause** : sans `AuthenticationEntryPoint` explicite, Spring renvoie 403 par défaut — alors qu'aucun jeton n'a même été présenté.
- **Correction** : `.exceptionHandling()` avec un `AuthenticationEntryPoint` ? 401 `UNAUTHENTICATED` + corps `problem+json` (CA-AUTH-04).
- **Leçon** : 401 = « prouvez qui vous êtes » ; 403 = « vous êtes identifié mais refusé ». Les confondre fait perdre des heures au front.

### P1-3 — `found duplicate key logging` au démarrage
- **Erreur exacte** : SnakeYAML refuse deux clés `logging:` dans le **même** document YAML.
- **Cause** : ajout du `logging.pattern.correlation` dans un nouveau bloc alors que `logging.level` existait déjà avant le `---`.
- **Correction** : fusionner dans **un seul** bloc `logging` par document (les séparateurs `---` délimitent des documents distincts, chacun a le sien).
- **Leçon** : un `---` = un fichier YAML à part entière ; les clés ne se fusionnent pas.

### P1-4 — Tests de filtrage : mock Mockito = headers fantômes
- **Symptôme** : `response.getHeader(...)` renvoie `null` alors que le filtre a bien appelé `setHeader` — le test échoue à tort.
- **Cause** : un mock généré par Mockito n'a **aucune logique métier** : `setHeader` et `getHeader` sont indépendants (le mock ne stocke rien).
- **Correction** : utiliser `MockHttpServletRequest` / `MockHttpServletResponse` de Spring Test, qui reproduisent le vrai comportement du conteneur servlet.
- **Leçon** : mock = on simule les *interactions*, pas le *comportement*. Pour le comportement, prendre le mock « réel » de la framework.

### P1-5 — `malloc failed` dans le fork Maven (tests)
- **Erreur exacte** : `There is insufficient memory for the Java Runtime Environment to continue` dans `*.dumpstream`.
- **Cause** : 7,8 Go de RAM, l'app dev + le JVM de tests + IDE tournent en même temps ? le fork surefire ne peut pas s'allouer ~20 Mo.
- **Correction** : `Stop-Process` sur l'app Spring Boot avant `mvnw verify`, puis relancer.
- **Leçon** : les tests Spring Boot (contexte complet + Testcontainers) sont gourmands — un seul build à la fois sur une machine de 8 Go.

### P1-6 — HALF_EVEN : le test attendait le mauvais résultat
- **Symptôme** : `1250.50 ? 1251` attendu, `1250` obtenu.
- **Cause** : l'arrondi bancaire va au nombre **pair** : 1250 est pair ? `1250.50` **descend** à 1250 (et `1251.50` **monte** à 1252).
- **Leçon** : HALF_EVEN n'arrondit pas « vers le haut », il élimine la dérive biaisée sur des millions d'écritures.

## 1.8 Questions d'entretien (Phase 1)

**Q1. Pourquoi un `record` Java pour `Money` et pas une classe ?**
Le `record` génère automatiquement constructeur, accesseurs, `equals`,
`hashCode`, `toString` — et interdit les champs non-`final`. Résultat : une
classe valeur **sérialisable, comparable, immuable** en 10 lignes, au lieu de
~80 en classe standard. C'est l'équivalent d'un value object `readonly` PHP.

**Q2. Différence entre `Money.of()` et `Money.parse()` ?**
`of()` est l'entrée **interne** : il arrondit (HALF_EVEN) car les calculs
peuvent produire des échelles sales (taux de frais). `parse()` est l'entrée
**utilisateur** : il **rejette** une échelle supérieure à la devise
(`100.50` en XOF ? 400 `INVALID_AMOUNT_SCALE`) — on n'arrondit jamais une
saisie sans prévenir le client.

**Q3. Pourquoi `traceId` dans le corps d'erreur ?**
Parce que c'est le **seul** lien fiable entre une plainte client et les logs
serveur. Le client nous donne son `traceId`, on le `grep` dans ELK/Loki, on
trouve la ligne exacte — sans lui, il faudrait reconstituer la requête à
partir d'une heure et d'une URL. Il vient du MDC : même valeur que l'en-tête
`X-Correlation-Id` et que le pattern de log.

**Q4. Pourquoi 401 et non 403 sur une API sans jeton ?**
401 (`Unauthorized`) = « je ne sais pas qui vous êtes, prouvez-le » : le
client doit renvoyer son JWT. 403 (`Forbidden`) = « je sais qui vous êtes,
mais vous n'y avez pas le droit » : renvoyer le JWT ne changera rien. Une
erreur de classification fait perdre des heures au front (boucle de retry
inutile ou affichage « session expirée » à tort).

**Q5. Pourquoi un `Clock` injecté au lieu de `Instant.now()` ?**
Reproductibilité. Les règles « cumul du jour » (CA-LIM-03) dépendent du
temps : avec `Instant.now()`, un test qui échoue à minuit ou en CI décale
échoue aléatoirement. Avec `Clock.fixed(2026-03-15T10:30Z, …)`, le test est
figé et déterministe. C'est l'équivalent de la « freeze time » de Clockify
ou du `Clock` de Symfony (RFC de PHP).

**Q6. Où s'arrête `GlobalExceptionHandler` — que voit le client sur un 500 ?**
Jamais la stack trace : un message générique (« Une erreur interne est
survenue… ») + le `traceId`. Le détail réel part en `log.error` côté serveur.
Filtrer le message évite les fuites d'information (nom de table, chemin
disque, requête SQL) — c'est la règle OWASP A05:2021.

## 1.9 Exercice pour le candidat

1. Ajoutez un test qui prouve que `Money.parse("100.00", XOF)` est **accepté**
   (échelle 2 > 0 ? non — 2 décimales > 0 décimales ? doit être **rejeté**) :
   vérifiez la frontière exacte, et corrigez le test si votre intuition
   était fausse.
2. Faites échouer volontairement un test (`assertThat(true).isFalse()`),
   lancez `.\mvnw.cmd test`, et localisez le rapport HTML sous
   `target/surefire-reports/`.
3. Appelez l'API avec un `X-Correlation-Id` de votre choix, provoquez une
   erreur, et **retrouvez** ce même identifiant dans la sortie console de
   l'app : prouvez la corrélation bout-en-bout.

---

# PHASE 2 — F-01 : Authentification (inscription, login, refresh, logout) — ✅ FAIT

> **Statut : FAIT — suite verte.** `Tests run: 46, Failures: 0, Errors: 0, Skipped: 0`,
> `BUILD SUCCESS` (vérifié le 20/11/2026 avec `.\mvnw.cmd test`).

## 2.1 Objectif de la phase (3 lignes)

1. Prouver que la stack de sécurité de Boot 4 / Spring Security 7 fonctionne : JWT, resource server, rôles.
2. Implémenter la fonctionnalité **F-01** (RG-AUTH-01 à 06, CA-AUTH-01 à 04) : inscription publique, login avec vérification de mot de passe et verrouillage, refresh token rotatif, logout.
3. Mettre en place la matrice de droits de base (CUSTOMER vs autres rôles) via `SecurityFilterChain` — pour la phase 3 (K Client/KYC).

## 2.2 Notions à comprendre

- **JWT (JSON Web Token)** — chaîne `header.payload.signature`, en base64url — ici signature **HS256** (HMAC-SHA256) avec une clé secrète **≥ 256 bits** via variable d'environnement (`FINCORE_JWT_SECRET`) — analogie Symfony : le token d'accès de `lexik/jwt-authentication-bundle`, mais sans bundle : Spring Security 7 appelle directement notre `JwtDecoder`.
- **Resource server** — Spring Security ne vérifie pas le token d'accès lui-même : il délage à `JwtDecoder` (bean de `identity`) — le client ne possède PAS la clé de signature.
- **Refresh token opaque** — jeté au milieu, conservé uniquement de façon **hachée** (SHA-256) en base — "opaque" = le serveur ne connaît pas sa structure.
- **Anti-énumération (RG-AUTH-05)** — « utilisateur inconnu » et « mauvais mot de passe » renvoient **exactement** le même 401 — compensé par le timing (BCrypt) — motif du jeton jeté avec `unknownUserHash`.
- **Timing attack** — attaque par comparaison de réponses lentes/rapides — le verrouillage après 5 échecs (RG-AUTH-05) évite aussi l'essai indéfini.

## 2.3 Commandes exécutées

| Commande | Ce qu'elle fait | Pourquoi l'avoir lancée | Importance |
|---|---|---|---|
| `java -version` | Affiche la version du JDK (`27.0.1.0.3+1-2`) | Vérifier que le JDK est présent — cible Java 25 LTS — repli possible sur Java 21 (voir cahier §2.3) — on note le poste en 27 (non LTS, accepted for the exercise) | Importante |
| `docker version` | Affiche client+server Docker (29.8.1, Desktop 4.93.0) | Vérifier que Docker Desktop tourne — indispensable aux Tests Testcontainers — base PostgreSQL éphémère pendant les tests | Importante |
| `.\mvnw.cmd test` | Compile et exécute **toutes** les tests Maven (unitaires + d'intégration Testcontainers) | **Exécution de la phase 2** — AuthFlowTest + PasswordPolicyTest + suite classique — résultat : `Tests run: 46, Failures: 0, Errors: 0` + `BUILD SUCCESS` | ✅ CRITIQUE |

## 2.4 Fichiers créés ou modifiés

| Fichier | Rôle |
|---|---|
| `src/main/java/com/wince/fincore/identity/api/AuthController.java` | Contrôleur public `POST /api/v1/auth/{register,login,refresh}` + `/auth/logout` — délage vers `AuthService` |
| `src/main/java/com/wince/fincore/identity/api/RegisterRequest.java` | DTO `email` + `password` (Bean Validation) — RG-AUTH-02 (que des CUSTOMER) |
| `src/main/java/com/wince/fincore/identity/api/LoginRequest.java` | DTO `email` + `password` — format e-mail valide, mais l'existence du compte **jamais** révélée (RG-AUTH-05) |
| `src/main/java/com/wince/fincore/identity/api/RefreshTokenRequest.java` | DTO `refreshToken` — le jeton voyage dans le **corps** (pas en URL — pas dans les logs) |
| `src/main/java/com/wince/fincore/identity/api/AuthResponse.java` | Record : `userId, email, role, tokenType, accessToken, expiresIn, refreshToken` |
| `src/main/java/com/wince/fincore/identity/application/AuthService.java` | Orchestre PasswordPolicy + JwtService + RefreshTokenService — inscription, login (verrouillage RG-AUTH-05), rotation, logout — une seule classe qui sabre la logique métier — testable sans servlet ni container |
| `src/main/java/com/wince/fincore/identity/application/PasswordPolicy.java` | Politique RG-AUTH-01 (≥ 12 car., majuscule + minuscule + chiffre + spécial) — renvoie **toute la liste** des règles violées en une seule erreur 400 |
| `src/main/java/com/wince/fincore/identity/application/JwtService.java` | Émission des access tokens HS256 avec les claims `sub` (UUID), `role`, `iat`, `exp` (15 min), `jti` |
| `src/main/java/com/wince/fincore/identity/application/RefreshTokenService.java` | Génération (256 bits), rotation (invalide le jeton présenté + chaîne de suivi), révocation de famille (RG-AUTH-04), lookup par hash SHA-256 |
| `src/main/java/com/wince/fincore/identity/domain/AppUser.java` | Entité JPA `app_user` (email, passwordHash, role, enabled, failedAttempts, lockedUntil) + logique de verrouillage |
| `src/main/java/com/wince/fincore/identity/domain/RefreshToken.java` | Entité JPA `refresh_token` : id, user, tokenHash, expiresAt, revokedAt, replacedBy |
| `src/main/java/com/wince/fincore/identity/domain/Role.java` | Enuméré `CUSTOMER / TELLER / ADMIN / AUDITOR` |
| `src/main/java/com/wince/fincore/identity/infrastructure/IdentitySecurityConfig.java` | Beans du resource server JWT : `JwtDecoder` + convertisseur du claim `role` → autorité `ROLE_CUSTOMER` (utilisé par `hasRole("ADMIN")`) |
| `src/main/java/com/wince/fincore/shared/infrastructure/SecurityConfig.java` | Chaîne de sécurité : `permitAll` sur `/auth/{register,login,refresh}` + santé/Swagger, `authenticated()` sinon, `oauth2ResourceServer` + `PasswordEncoder` bean (BCrypt 12) |
| `src/main/java/com/wince/fincore/shared/api/GlobalExceptionHandler.java` | + `@ExceptionHandler(AccessDeniedException)` : 403 `ACCESS_DENIED` typé pour les règles déclenchées **dans** un contrôleur |
| `src/main/resources/db/migration/V2__create_refresh_token.sql` | Migration `refresh_token` (`id UUID PK`, `user_id FK`, `token_hash VARCHAR(64) UNIQUE`, `expires_at`, `revoked_at`, `replaced_by`) + index + CHECK sur la longueur du hash |
| `src/main/resources/application.yml` | + `fincore.security.*` (secret, TTL 15 min, TTL refresh 7 j, 5 échecs, 15 min de verrou) — en prod : `FINCORE_JWT_SECRET` sans valeur de repli |
| `src/main/java/com/wince/fincore/identity/repository/AppUserRepository.java` | `JpaRepository` : `findByEmail`, `existsByEmail` |
| `src/main/java/com/wince/fincore/identity/repository/RefreshTokenRepository.java` | `JpaRepository` : `findByTokenHash`, `revokeAllForUser` (UPDATE JPQL) |
| `src/test/java/com/wince/fincore/identity/api/AuthFlowTest.java` | Tests d'intégration F-01 (CA-AUTH-01 à 04) sur PostgreSQL réel via MockMvc + Testcontainers |
| `src/test/java/com/wince/fincore/identity/application/PasswordPolicyTest.java` | Tests de PasswordPolicy |
| `.env.example` | Variables de sécurité (`FINCORE_JWT_SECRET`), écrite en phase 2 |

## 2.5 Code clé

### a. JWT — emission des access tokens (`JwtService.issueAccessToken`)

```java
JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("fincore")
        .subject(user.getId().toString())          // UUID de l'utilisateur (jamais l'email)
        .issuedAt(now)
        .expiresAt(now.plus(accessTokenTtl))        // 15 min (RG-AUTH-03)
        .claim("role", user.getRole().name())      // le claim que convertissent SecurityConfig + IdentitySecurityConfig
        .id(UUID.randomUUID().toString())          // jti
        .build();
return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(HS256).build(), claims))
        .getTokenValue();
```

**Retenir** : le JWT ne contient que des données **publiques** (identité + rôle). Le secret ne sert qu'à le signer — jeton d'accès court (15 min), pas une session.

### b. Rotation + famille (`RefreshTokenService.rotate`)

```java
RefreshToken current = requireValid(presentedToken);
if (current.isRevoked()) {                          // reuse detection
    revokeFamilyInNewTransaction.execute(s -> repository.revokeAllForUser(userId, now));
    throw new FinCoreException(ErrorCode.UNAUTHENTICATED, "Refresh token déjà utilisé…; session révoquée");
}
String newClearValue = issue(current.getUser());
current.revokeAsReplacedBy(successorId, now);        // revoque le jeton présenté
repository.save(current);
```

**Piège (CA-AUTH-03)** : la révocation de famille est **committée avant** que l'exception ne parte — sinon le `rollback` l'annulerait et le jeton volé resterait valide.

### c. Matrice de droits (`SecurityConfig.baseSecurityFilterChain`)

```java
.requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
.permitAll()                   // c'est ici qu'un jeton est obtenu — on ne peut pas le protéger
.anyRequest().authenticated()  // tout le reste requiert un jeton (401 sans jeton)
```

## 2.6 Comment vérifier

- ✅ `.\mvnw.cmd test` → `BUILD SUCCESS`, `Tests run: 46, Failures: 0`.
- En live (si la base docker tourne) : `Invoke-WebRequest http://localhost:8080/actuator/health` → `200` `{"status":"UP"}` — vérifier que `GET /api/v1/__probe/admin-only` → 401.

## 2.7 Pièges rencontrés

- **P2-1** : « Jwt and Opaque Token are the only supported formats » — il faut déclarer `.jwt(Customizer.withDefaults())` dans `oauth2ResourceServer` — la simple présence d'un bean `JwtDecoder` ne suffit plus.
- **P2-2** : 403 au lieu de 401 sur les endpoints protégés par `@PreAuthorize` quand l'authentification est un `AccessDeniedException` — il l'atteint **dans** le contrôleur — il faut le `@ExceptionHandler(AccessDeniedException)` dans `GlobalExceptionHandler`.
- **P2-3** : `FINCORE_JWT_SECRET` en prod — le profil prod redéfinit la propriété sans valeur de repli — si la variable d'environnement n'est pas posée, l'app **refuse de démarrer** (échec net) — c'est voulu.
- **P2-4** : déclarer `jwt-secret` dans deux documents YAML — le dernier gagne en profil actif (document YAML à part entière par bloc).
- **P2-5** : `version` (verrou optimiste JPA) — pas utilisé en phase 2 — utilisé pour la phase 5 (grand livre).

## 2.8 Questions d'entretien — Phase 2

**Q1. Quelles sont les différences entre access token et refresh token dans FinCore ?**
Access token (JWT HS256, 15 min, vérifié par le serveur via `JwtDecoder` dans le resource server) et refresh token (opaque, 7 j, client détient la valeur en clair, hachée en base).

**Q2. Pourquoi stocker uniquement le haché du refresh token ?**
Defense en profondeur : si la base est exfiltrée, l'attaquant obtient des SHA-256 indevinables — ni réutilisables, ni éditables.

**Q3. Comment fonctionne la rotation et la révocation de famille (RG-AUTH-04) ?**
À chaque `/auth/refresh` : jeton présenté révoqué (`revokedAt = now`, lié à son successeur via `replacedBy`) + nouveau jeton émis. Si un jeton déjà utilisé est présenté → tous les jetons actifs révoqués d'un coup (`UPDATE ... WHERE user_id = ? AND revokedAt IS NULL` dans une transaction `REQUIRES_NEW`) + 401 — signal d'un vol de session.

**Q4. Pourquoi le verrouillage après 5 échecs (RG-AUTH-05) et un message identique en toutes situations ?**
5 échecs consécutifs → `lockedUntil = now + 15 min` — évite les attaques par force brute infinie — le message identique pour inconnu/erreur de mot de passe limite l'énumération d'utilisateurs — le timing est compensé par le `unknownUserHash`.

**Q5. Comment Spring Security vérifie-t-il l'access token ?**
`Authorization: Bearer <jwt>` — Spring Security appelle notre `JwtDecoder` (bean déposé par `IdentitySecurityConfig`) : signature HS256 + dates, puis délage aux autorités via `JwtGrantedAuthoritiesConverter` sur le claim `role`.

## 2.9 Exercice pour l'apprentissage (Phase 2)

1. Écris un test JUnit dans `AuthFlowTest` (ou `RefreshTokenServiceTest`) : vérifie que l'access token de l'utilisateur de test contient `sub == userId` et `role == "CUSTOMER"` (RG-AUTH-06) — ou relance `.\mvnw.cmd test -Dtest=AuthFlowTest` et compte 11 tests verts.
2. Entraîne-toi à la rotation : dans une requête manuelle, appelle `POST /api/v1/auth/refresh` deux fois avec le même refresh token — la seconde appel renvoie **401** (CA-AUTH-03, replay détecté).
3. **Tu pourrais ajouter** le test d'idempotence du login — un mot de passe trop court → 400 + `errors.password` non vide.

---

# PHASE 3 — F-02 : Clients et KYC — ✅ FAIT (suite verte : 64 tests)

> Dépend de la Phase 2 (l'inscription par `/api/v1/auth/register` crée le `AppUser`). Le guide détaillé de la phase (détail file-level) : `detail_projet/phase3.md` — complément effectué en séance : LEVEL_3 = ADMIN seul + source de revenus déclarée (RG-KYC-02).

## 3.1 Objectif de la phase (3 lignes)

1. Introduire l'entité **client** (`customer`) : clé FK UNIQUE `user_id` vers `AppUser` (Phase 2) — énumérés `KycLevel` (`LEVEL_1/2/3`), `KycStatus` (`PENDING/VERIFIED/REJECTED`) (§6.1).
2. Implémenter les routes du KYC — `GET /customers/me` (CUSTOMER), `PATCH /customers/{id}/kyc` (matrice §5 : TELLER, ADMIN — LEVEL_3 : ADMIN seul).
3. Respecter l'intégrité des données : numéro d'identification **masqué** (RG-KYC-03), majeur/mineur via la `Clock` injectée (RG-KYC-04).

## 3.2 Notions à comprendre

- **Two entities = two collapsed templates (PHP/Symfony)** : `app_user` = session (login + verrouillage Phase 2), `customer` = données métier — SRP : on ne charge pas les secrets dans les données de session.
- **`CHECK` PostgreSQL dans V3** : énuméré fermé — évoluable via migration (jamais modifier V3 commitée — éditer l'enum + migration à part).
- **Le DTO valide la forme, le service valide les règles métier** — complément de la Phase 2 (PasswordPolicy) — prend la règle (« âge 18 ») qui a besoin du domaine, pas du schéma HTTP.
- **Mutation test (P3-2)** : le TELLER est validé à LEVEL_2 — mais le LEVEL_3 est "déclaration d'activité représentée" — validée par ADMIN — RG-KYC-02 — niveau **dépendant du body** → vérification dans le contrôleur (SpEL statique insuffisant).

## 3.3 Commandes exécutées

| Commande | Ce qu'elle fait | Pourquoi l'avoir lancée | Importance |
|---|---|---|---|
| `./mvnw verify` | Compile + exécute **toutes** les suites Maven (unitaires + d'intégration Testcontainers) | Vérification globale de la phase 3 — résultat : `Tests run: 64, Failures: 0, Errors: 0` + `BUILD SUCCESS` | ✅ CRITIQUE — preuve par HTTP/SQL de CA-KYC-01 à 02 |
| `git status --short` | État de l'arborescence (5 fichiers modifiés : 3 source + 2 test) | Préparation aux commits (par l'auteur) | Importante |

> **P3-1** : 1er `./mvnw verify` passé en erreur — `Unhandled exception type Exception` dans `KycEndpointTest` — le helper `customerId(MvcResult)` appelle `read()` sans clause `throws Exception` — signature d'helper = exceptions lancées par le code appelé.

## 3.4 Fichiers créés ou modifiés

| Fichier | Rôle |
|---|---|
| `src/main/java/com/wince/fincore/customer/api/KycUpdateRequest.java` | **Modifié** : nouveau composant `sourceRevenus` — RG-KYC-02 (déclaration au LEVEL_3) |
| `src/main/java/com/wince/fincore/customer/application/CustomerService.java` | **Modifié** : `sourceRevenus` obligatoire au LEVEL_3 → 400 `VALIDATION_FAILED` (`errors.sourceRevenus`) |
| `src/main/java/com/wince/fincore/customer/api/CustomerController.java` | **Modifié** : bloc ADMIN au LEVEL_3 dans `updateKyc` (403 `ACCESS_DENIED`) + helper `hasAuthority(Role)` |
| `src/test/java/com/wince/fincore/customer/api/KycEndpointTest.java` | **Modifié** : 3 nouveaux tests + helper `customerId(MvcResult)` |
| `src/test/java/com/wince/fincore/customer/application/CustomerServiceTest.java` | **Modifié** : 2 nouveaux tests (sourceRevenus manquante/déclarée) + 4 appels mis à jour (nouveau paramètre) |

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
```

### b. Vérification RG-KYC-02 — `CustomerService.updateKyc`

```java
if (request.kycLevel() == KycLevel.LEVEL_3
        && (request.sourceRevenus() == null || request.sourceRevenus().isBlank())) {
    throw new FinCoreException(ErrorCode.VALIDATION_FAILED,
            "Le passage au niveau LEVEL_3 requiert une source de revenus déclarée.",
            Map.of("errors", Map.of("sourceRevenus", "obligatoire")));
}
```

## 3.6 Comment vérifier

- `./mvnw verify` → `Tests run: 64, Failures: 0` — inclut :
  - `KycEndpointTest.teller_cannot_reach_level_3` : TELLER + LEVEL_3 → **403** `ACCESS_DENIED` (preuve HTTP de RG-KYC-02) ;
  - `KycEndpointTest.admin_reaches_level_3_with_declared_source_of_revenue` : ADMIN + LEVEL_3 + `sourceRevenus` → **200**, `idDocNumberMasked == ****6789`, **aucun numéro clair dans la réponse** (CA-KYC-01) ;
  - `KycEndpointTest.admin_level_3_requires_declared_source_of_revenue` : ADMIN sans `sourceRevenus` → **400** `VALIDATION_FAILED` + `errors.sourceRevenus` non vide.
- Commandes manuelles (live) : inscription `POST /api/v1/auth/register` (201) → login → `PATCH /api/v1/customers/{id}/kyc` (TELLER → 403, ADMIN avec `sourceRevenus` → 200).

## 3.7 Pièges rencontrés

- **P3-2** : avant complémentation, rien ne refusait au TELLER de monter en LEVEL_3 — règle implicite dans la javadoc de `KycLevel` — le test 403 rend la règle explicite.
- **P3-3 (mutation)** : suppression du bloc ADMIN du contrôleur → `teller_cannot_reach_level_3` rouge (200 au lieu de 403) → rétablir : le test d'entraînement prouve l'implémentation.

## 3.8 Questions d'entretien — Phase 3

**Q1. À quoi sert `app_user` par rapport à `customer` ?** — session vs métier — FK UNIQUE `user_id` — deux collapsed templates PHP/Symfony.

**Q2. Comment représenter un énuméré fermé statut (PENDING/VERIFIED/REJECTED) ?** — Énuméré Java `@Enumerated(EnumType.STRING)` + `CHECK` PostgreSQL (évoluable par migration — jamais modifier V3 commitée).

**Q3. Comment faire atomique une montée de niveau (RG-KYC-02 à 05) ?** — `@Transactional` sur `CustomerService.updateKyc` (§2.2 Phase 0) — équivalent du `@Transactional` sur `AuthService` (Phase 2).

**Q4. Comment distinguer « majeur → LEVEL_2 (TELLER/ADMIN) » de « LEVEL_3 = ADMIN seul » ? (RG-KYC-02)** — LEVEL_2 = « assisté à un agent de guichet » vs LEVEL_3 = « déclaration d'activité représentée » — Level-3 nécessite un représentant ADMIN.

**Q5. Pourquoi le numéro d'identification n'est-il jamais stocké en clair (CA-KYC-01) ?** — SEUL `id_doc_number_masked` en base (V3) ; hash SHA-256 dans `maskIdDoc` est transitoire (`@Transient`) — un dump PostgreSQL ne révèle rien.

## 3.9 Exercice pour l'apprentissage

1. **Échouer volontairement en local** : démarre l'app (Phase 0), `PATCH /api/v1/customers/{id}/kyc` en TELLER → observe le **403** — rejoue en ADMIN avec `sourceRevenus` → observe le **200** — redécouvre la règle de la §5 + RG-KYC-02 sans briser le code.
2. **Découvrir le type JUnit** : `./mvnw verify -Dtest=KycEndpointTest` → **9 tests verts** dans `target/surefire-reports/com.wince.fincore.customer.api.KycEndpointTest.txt` — grep `Tests run:`.
3. **Ajoute-toi un test** : un `AUDITOR` tente le PATCH — la matrice §5 ne donne aucun droit PATCH à l'AUDITOR — écris ton test (401 sans jeton, 403 avec jeton AUDITOR — même pattern que `matrice_des_droits_admin_autorise` de Phase 2), relance `./mvnw verify`.
4. **Mutation pédagogique (aucun commit)** : supprime le bloc ADMIN du contrôleur → relance `./mvnw verify` → `teller_cannot_reach_level_3` rouge (200 au lieu de 403) → rétablis le bloc.

---

# PHASE 4 — F-03 : Comptes + numéro de compte modulo 97 + comptes système + seed — ✅ FAIT

> **Statut : FAIT — suite verte.** `Tests run: 107, Failures: 0, Errors: 0, Skipped: 0` (43 nouveaux vs Phase 3 — 107 − 64), `BUILD SUCCESS` (23/11/2026).
> Tests : `AccountEndpointTest` 19, `AccountServiceTest` 19, `AccountNumberGeneratorTest` 5.
> Detail file-level : `detail_projet/phase4.md`.

## 4.1 Objectif de la phase (3 lignes)

1. Introduire l'entité **compte client** (`account`) — devises `XOF`/`EUR`/`USD` — numéro BEN unique — statuts `ACTIVE`/`FROZEN`/`CLOSED` — RG-ACC-01 à 04.
2. Générer et valider les numéros de compte — clé mod 97 sur 10 chiffres — pré-contrôle bénéficiaire — RG-ACC-03 — §8 — CA-ACC-01.
3. Créer les comptes systèmes (`CASH_VAULT`, `FEE_REVENUE`, `FX_POSITION`, `EXTERNAL_SETTLEMENT`, `SUSPENSE`, `INTEREST_EXPENSE` — 6 × 3 devises) au démarrage — migration + seed — RG-ACC-06.

## 4.2 Notions à comprendre

- **`VARCHAR` + `CHECK` — enuméré fermé dans la base** — équivalent de V3 — ajouter un niveau = constante + nouveau CHECK + nouveau déploiement — Pattern de `KycLevel` (Phase 3) — `AccountKind`, `SystemCode`, `AccountStatus`.
- **Mod 97 — `BigInteger`, pas `int`** — 10 chiffres peuvent dépasser `Integer.MAX_VALUE` — `AccountNumberGeneratorTest.check_key_convention_on_generated_example` — echo BCMath (`bcdiv`/`bcmod`) — Phase 5 (F-04 — RG-LED-06) — nettoyage verrou.
- **`Account.customerId` vs `AppUser.id`** — lien session/métier — resolveur `currentCustomerId()` — voir 4.5c.
- **`AccountNumberGenerator` — pure function** — `generate()`/`isValid()` — « validation de la clé à chaque saisie d'un numéro de bénéficiaire » (RG-ACC-03) — consommé par `AccountService.lookupBeneficiary` (`GET /accounts/lookup` — §8 — F-06).
- **Piège P4-1** : `Account.currency` — Hibernate 6 mappe les enums à `smallint` par défaut — conflit avec la colonne `VARCHAR(3)` — `SchemaManagementException` — « expecting [smallint (TINYINT)] » — voir 4.7.

## 4.3 Commandes exécutées

| Commande | Ce qu'elle fait | Pourquoi l'avoir lancée | Importance |
|---|---|---|---|
| `./mvnw verify` | Compile + exécute toutes les suites (unitaires + d'intégration Testcontainers) | `Tests run: 107, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS` — preuve par HTTP/SQL de CA-ACC-01/02 + RG-ACC-06 | ✅ CRITIQUE |
| `git status --short` | État de l'arborescence (7 fichiers modifiés) | Préparation aux commits par l'auteur | Importante |

## 4.4 Fichiers créés ou modifiés

| Fichier | Rôle |
|---|---|
| `src/main/java/com/wince/fincore/account/domain/Account.java` | Entity `account` — §6.1 — `currency` `@Enumerated(EnumType.STRING)` |
| `src/main/java/com/wince/fincore/account/domain/AccountKind.java` | `CURRENT, SAVINGS, WALLET, SYSTEM` — §6.1, RG-ACC-01 |
| `src/main/java/com/wince/fincore/account/domain/SystemCode.java` | `CASH_VAULT, FEE_REVENUE, FX_POSITION, EXTERNAL_SETTLEMENT, SUSPENSE, INTEREST_EXPENSE` — RG-ACC-06 |
| `src/main/java/com/wince/fincore/account/domain/AccountStatus.java` | `ACTIVE, FROZEN, CLOSED` — RG-ACC-04 |
| `src/main/java/com/wince/fincore/account/repository/AccountRepository.java` | `findByCustomerIdAndKindAndCurrency`, `findByAccountNumber`, `existsByAccountNumber`, `findByCustomerId`, `findAllBySystemCode` |
| `src/main/java/com/wince/fincore/account/application/AccountNumberGenerator.java` | Génération — `SecureRandom`, 10 chiffres (1er non nul) + clé — convention ICC — pure function |
| `src/main/java/com/wince/fincore/account/application/AccountService.java` | §8 — `open`, `findByCustomerId`, `findById` (CA-ACC-02), `changeStatus` (RG-ACC-04/05), `lookupBeneficiary` (pré-contrôle — RG-ACC-03, F-06) |
| `src/main/java/com/wince/fincore/account/api/AccountController.java` | §8 — `POST /accounts` (201 — pattern AuthController), `GET /accounts`, `GET /accounts/{id}`, `GET /accounts/lookup`, `PATCH /accounts/{id}/status` — DTO `@Valid` |
| `src/main/java/com/wince/fincore/account/api/OpenAccountRequest.java` | DTO POST — `currency` NULL → XOF — RG-ACC-01 |
| `src/main/java/com/wince/fincore/account/api/AccountStatusUpdateRequest.java` | DTO PATCH — `status` obligatoire — `motif` obligatoire FROZEN/CLOSED (F-13) |
| `src/main/java/com/wince/fincore/account/api/AccountResponse.java` | DTO de réponse — miroir `account` |
| `src/main/java/com/wince/fincore/account/api/AccountLookupResponse.java` | Pré-contrôle — `maskedName` — « J*** D*** » — F-06 — CA-KYC-01 |
| `src/main/java/com/wince/fincore/customer/domain/Customer.java` | **Modifié** : `setKycStatus(KycStatus)` — Phase 3 → 4 — RG-ACC-02 — vérifiée via `open_account_for_rejected_customer_400` |
| `src/main/resources/db/migration/V4__create_account.sql` | Migration — `CREATE TABLE account` + CHECKs + UQ partiel + **seed des comptes systèmes** (6 × 3 devises) — RG-ACC-06 |
| `src/test/java/com/wince/fincore/account/application/AccountNumberGeneratorTest.java` | Unit — CA-ACC-01 — convention mod 97 — distinctness 100 |
| `src/test/java/com/wince/fincore/account/application/AccountServiceTest.java` | Unit — `open`, `findById` (CA-ACC-02), `changeStatus`, `lookupBeneficiary` |
| `src/test/java/com/wince/fincore/account/api/AccountEndpointTest.java` | Intégration — 19 tests — §5 — CA-ACC-01/02 — RG-ACC-06 — PostgreSQL réel (Testcontainers) |

## 4.5 Code clé

### a. Génération et validation — `AccountNumberGenerator` (RG-ACC-03)

```java
public static String generate() {
    String body = generateBody();                    // 10 chiffres — 1er non nul
    return PREFIX + body + String.format("%02d", checkKey(body));
}

private static int checkKey(String body) {
    return MOD - new BigInteger(body).mod(BigInteger.valueOf(MOD)).intValue();  // 97 − (n mod 97) — borne 1..97
}

public static boolean isValid(String accountNumber) {
    // préfixe BJ, longueur 14, body \d{10}, clé \d{2} — clé == 97 − (n mod 97)
}
```

**Piège P4-2** : le changement de convention de la clé — éviter `97 - mod` qui peut donner `0` — la convention ICC donne 97 — ici la borne est 1..97 — vérifiée dans le test.

### b. Ouverture — `AccountService.open`

```java
Customer customer = customerRepository.findById(request.customerId()).orElseThrow(...);
if (customer.getKycStatus() == KycStatus.REJECTED) {
    throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "...");   // RG-ACC-02 — Phase 3 — Customer.setKycStatus
}
if (accountRepository.findByCustomerIdAndKindAndCurrency(id, kind, currency).isPresent()) {
    throw new FinCoreException(ErrorCode.VALIDATION_FAILED, "...");   // RG-ACC-02 — un seul compte par (type, devise)
}
Account account = new Account(UUID.randomUUID(), generateUniqueAccountNumber(),
        request.customerId(), request.kind(), null, currency, Money.zero(currency).amount());
account.setOpenedAt(clock.instant());
return accountRepository.save(account);
```

### c. 404 — CA-ACC-02 — `AccountService.findById`

```java
if (role == Role.CUSTOMER
        && (account.isSystem() || currentCustomerId == null
                || account.getCustomerId() == null
                || !account.getCustomerId().equals(currentCustomerId))) {
    throw new FinCoreException(ErrorCode.RESOURCE_NOT_FOUND, "Compte introuvable.");
}
```

- `currentCustomerId` = id du `Customer` lié à l'AppUser — `customerRepository.findByUserId(userId).map(Customer::getId)` — `userId` = `currentUserId()` (claim `sub` du JWT — RG-AUTH-06) — null si l'ADMIN n'a pas de Customer — `Account.customer_id` → `customer.id` (§6.1).

### d. Seed — `V4__create_account.sql` (RG-ACC-06)

```sql
WITH pairs(code, kind, ccy) AS (VALUES (…18 tuples…)),
gen AS (SELECT code, kind, ccy, floor(random() * 9000000000 + 1000000000)::bigint AS body FROM pairs)
INSERT INTO account (id, account_number, kind, system_code, currency, status, balance, version, opened_at)
SELECT gen_random_uuid(),
       'BJ' || lpad(body::text, 10, '0') || lpad((97 - (body % 97::bigint))::text, 2, '0'),
       kind, code, ccy, 'ACTIVE', 0, 0, now()
FROM gen;
```

- **Convention** : clé SQL = `97 − (n % 97)` — compatible `AccountNumberGenerator.checkKey` — vérifiable via `AccountEndpointTest.system_accounts_are_seeded_at_startup` — `isValid` sur chaque ligne seedée —echo RG-ACC-06.

## 4.6 Comment vérifier

- **Suite complète** : `./mvnw verify` → `Tests run: 107, Failures: 0, Errors: 0` — inclut :
  - `AccountEndpointTest.teller_opens_account_201` : TELLER + `POST /accounts` → 201 — `id`, `accountNumber` valide, `status=ACTIVE`, `currency=XOF`, `customerId`, line persistée — CA-ACC-02 via `customer_cannot_read_others_account_404` (404 pour un autre client) ;
  - `AccountEndpointTest.system_accounts_are_seeded_at_startup` : 6 comptes systèmes — `Account::isSystem`, `customerId NULL`, `status ACTIVE`, `isValid` — RG-ACC-06 — seed en migration ;
  - `AccountEndpointTest.admin_cannot_freeze_system_account_409` : statut système immutable — 409 — RG-ACC-06 ;
  - `AccountEndpointTest.admin_cannot_close_account_with_nonzero_balance_409` : solde non nul — 409 — RG-ACC-05.
- **Manuel (live)** : `curl.exe -X POST -H "Authorization: Bearer <ADMIN>" -H "Content-Type: application/json" http://localhost:8080/api/v1/accounts -d '{"customerId":"…","kind":"WALLET","currency":"EUR"}'` → 201 Created — contradiction ?— non — 201 attendu — pattern Phase 2.
- **Base** : `SELECT account_number, kind, currency, balance FROM account WHERE customer_id IS NULL;` → 18 lignes — comptes systèmes — validation : `SELECT account_number FROM account WHERE length(account_number) <> 14 OR substring(account_number, 1, 2) <> 'BJ';` → vide.

## 4.7 Pièges rencontrés

1. **P4-1 — `Schema validation: wrong column type in column [currency]; found [varchar], expecting [smallint (TINYINT)]`** — `Account.currency` est un `enum` JPA — JPA mappe les enums à `int` par défaut (`@Enumerated` absent) — conflit avec la colonne `VARCHAR(3)` — ajout de `@Enumerated(EnumType.STRING)` — piège pédagogique Phase 1 (`Currency` dans `Money`).
2. **P4-2 — `Status expected:<201> but was:<200>`** — `@PostMapping` — 200 — pattern `AuthController.register` — `ResponseEntity.status(HttpStatus.CREATED).body(...)` — Phase 2 — `AccountController.open` — POST = création — 201.
3. **Piège méta — diagnostique difficile : `No enum constant com.wince.fincore.identity.domain.Role.R_BEARER` → 500** — sur `GET /accounts/{id}` par ADMIN — `currentRole()` manipule désormais le claim du JWT — `Role.valueOf(jwt.getClaimAsString("role"))` — RG-AUTH-06 — plus robuste que la manipulation des autorités Spring Security 7 — vérifiable via `admin_reads_account_detail_200`.
4. **Mutation test pédagogique (aucun commit)** : supprime localement le bloc REJECTED de `AccountService.open` — `AccountEndpointTest.open_account_for_rejected_customer_400` rouge — rétabris.

## 4.8 Questions d'entretien — Phase 4

**Q1. Comment calculer une clé de contrôle modulo 97 sans perdre en précision ?** — `String body` → `new BigInteger(body)` → `.mod(BigInteger.valueOf(97))` → `97 − r` — borne 1..97 — éviter `int` (jusqu'à 9 999 999 999 — jusque `Integer.MAX_VALUE` = 2 147 483 647 — voir `AccountNumberGeneratorTest`) — BCMath (`bcsub`, `bcmod`) — Phase 5.

**Q2. Comment représenter un numéro de 10 chiffres ?** — `String` — validation à l'API — pré-contrôle — 400 `INVALID_ACCOUNT_NUMBER` — RG-ACC-03 — `GET /accounts/lookup?number=…` (§8 — F-06).

**Q3. Comment représenter un solde dans l'API ? (§6.1 — `balance NUMERIC`)** — `AccountResponse.balance` est un `BigDecimal` — l'échelle de la devise — `Money` — 0 XOF — 2 EUR/USD — sérialisé en JSON (`0`, `12.34`) — suivi — `spring.jackson.default-property-inclusion: non_null` — Phase 5.

**Q4. Comment implémenter « un seul compte par (type, devise) » — RG-ACC-02 ?** — Backstop SQL — UQ partiel — `CREATE UNIQUE INDEX uq_account_customer_kind_currency ON account (customer_id, kind, currency) WHERE customer_id IS NOT NULL` — pourquoi PARTIEL — NULL ≠ NULL — Backstop Java — `AccountService.open` — `findByCustomerIdAndKindAndCurrency(...).isPresent()` → 400 — Client REJECTED — `KycStatus.REJECTED` — Phase 3 — `Customer.setKycStatus`.

**Q5. À quoi sert la colonne `version` (§6.1) — Phase 4 ?** — BIGINT — présent, stocké — pas encore utilisé — Phase 5 (F-04 — RG-LED-06) — nettoyage verrou — Pattern pédagogique (Phase 0/2 — §6.2 — « verrou optimiste JPA »).

## 4.9 Exercice pour l'apprentissage

1. **Découvrir le flux** : `./mvnw verify -Dtest=AccountEndpointTest#teller_opens_account_201` — 1 test vert — injecter un `System.out.println` dans `AccountService.open` — observer — supprimer.
2. **Mutation test (aucun commit)** : supprime localement le bloc REJECTED de `AccountService.open` — relance `./mvnw verify` — `AccountEndpointTest.open_account_for_rejected_customer_400` rouge — rétabris.
3. **Remarque pédagogique** : le mod 97 — `AccountNumberGeneratorTest` — convention écrite en SQL (`V4`) et reproduite en Java (`AccountNumberGenerator.checkKey`) — difficile à tromper — vérifiable via `AccountEndpointTest.system_accounts_are_seeded_at_startup` — `isValid(numéro)` sur chaque ligne seedée.
