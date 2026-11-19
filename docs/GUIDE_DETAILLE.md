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
