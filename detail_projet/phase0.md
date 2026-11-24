# Phase 0 — Squelette du projet, Docker, Flyway, `/actuator/health` — ✅ FAIT

> **Statut : FAIT.** Squelette du projet FinCore : parent `spring-boot-starter-parent:4.1.2`, `javaVersion=25`,
> commits : `33471a4 chore: initialiser le squelette FinCore (phase 0)`, `6251db9 docs: guide phase 0`.

## 0.1 Objectif de la phase (3 lignes)

1. Mettre en place un squelette **Spring Boot 4.1.2 / Java 25** qui **démarre**.
2. Faire tourner **PostgreSQL 17** dans un conteneur Docker (aucune installation manuelle de base de données).
3. Créer la première migration Flyway et prouver que `GET /actuator/health` répond `UP`, tout versionner avec Git.

## 0.2 Notions à comprendre

| Concept | Analogie Symfony/Laravel | Explication |
|---|---|---|
| `pom.xml` | `composer.json` | Fichier de dépendances. `<dependency>` = `composer require`. Maven Télécharge des artefacts dans `~/.m2/repository` (le cache local). |
| `mvnw` / `mvnw.cmd` | binaire `composer` (local) / `symfony` | Script qui télécharge **Maven lui-même** au 1er lancement (version pinnée dans `.mvn/wrapper/maven-wrapper.properties`). Pas d'outil à installer — le projet embarque sa propre build. |
| Spring Initializr (`https://start.spring.io`) | `symfony new mon-projet` ou `composer create-project` | Générateur en ligne de squelettes — un "create-project" cible Java. |
| Conteneur | machine virtuelle démarrée | Image immuable (`postgres:17`) + conteneur = instance de cette image en cours d'exécution. |
| Volume | disque persistant hôte | Sans volume, les données PostgreSQL disparaissent à l'arrêt du conteneur. |
| Healthcheck | — | Commande exécutée périodiquement **dans** le conteneur (`pg_isready`). Tant que ça échoue, l'état est `starting` — `depends_on: condition: service_healthy` bloque `docker compose up app` jusqu'à ce que le service est sain : **pas de course « app avant la base »**. |
| Flyway (`V1__*.sql`) | `make:migration` | On écrit **à la main** le fichier `V1__init_app_user.sql` — çéquivalent de `doctrine:migrations:diff`/`php artisan make:migration`. Autre point commun : une migration **commitée ne se modifie jamais** (correction = nouvelle `V2__…`). |
| `ddl-auto: validate` | `auto_generate_classes: false` + validate | Hibernate ne crée **rien**. `validate` vérifie que l'entité Java correspond au schéma SQL — **le schéma est la propriété de Flyway**. Démarrer pour carrément déraper = fail-fast. |
| `Testcontainers` | — | **La base en vrai** : démarre un vrai PostgreSQL dans Docker pendant les tests, puis le détruit (CI sans base installée). Interdit de mocker la base pour le grand livre (cahier). |

## 0.3 Commandes exécutées

> En Phase 0, les commandes étaient effectuées via le binaire `Invoke-WebRequest` (PowerShell), et **toutes** les commandes utiles sont expliquées ci-dessous : à retaper manuellement si tu effectues le projet selon le guide `docs/GUIDE_DETAILLE.md` §0.3.

| Commande | Ce qu'elle fait | Pourquoi l'avoir lancée | Importance |
|---|---|---|---|
| `java -version` | Affiche la version du JDK | 1re étape de pré-requis : compiler et exécuter du Java. | Importante |
| `git --version` | Version de Git | Pré-requis du projet (git init/commit à la fin). | Importante |
| `docker version --format "{{.Server.Version}}"` | Version du client **et** du serveur Docker | Pré-requis pour les Tests Testcontainers. | Importante |
| `Start-Process -FilePath "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe"` | Lance Docker Desktop en arrière-plan (`Start-Process` = `exec` détaché) | Le client docker ne fonctionne pas sans le **daemon**. Vérifier que le serveur répond (une ligne dans `docker version`). | Importante |
| `Invoke-WebRequest -Uri "https://start.spring.io/starter.zip?..." -OutFile fincore.zip -UseBasicParsing` (`curl -o fincore.zip …`) | Télécharge le ZIP du squelette depuis Spring Initializr | Skelette d'un projet Spring dans un clic — équivalent `symfony new`/`composer create-project`. | Importante (mais effectuée en externe par défaut : le squelette est désormais en réalité dans le repo : vérifier `pom.xml`, `mvnw.cmd`) |
| `Expand-Archive -Path fincore.zip -DestinationPath . -Force` (`unzip`) | Décompresse le ZIP → dossier `fincore/` contenant `pom.xml`, `mvnw.cmd`, `src/` | Suite de l'étape précédente. | Importante |
| `docker compose up -d db` | Crée et démarre **uniquement** le service `db` (PostgreSQL 17, port 5433, volume, réseau). `-d` = mode détaché (rend la main, le conteneur continue en arrière-plan) | Lance la base **avant** que l'app ne démarre. | Importante |
| `docker compose ps` | Liste les conteneurs avec leur état | Vérifie le service est `healthy` (pg_isready). | Importante |
| `docker exec fincore-db psql -U fincore -d fincore -c "\du"` | Exécute `psql` **dans** le conteneur (`\du` liste les rôles SQL) | Preuve que la base tourne et que le rôle `fincore` existe — le mauvais rôle (ou un mauvais port) = échec de Flyway au démarrage. | Importante |
| `.\mvnw.cmd verify` | Cycle Maven complet : compile → tests → package. Lance `FinCoreApplicationTests` (Testcontainers + contexte Spring) | Vérifie que **tout est vert** à la fin de la phase. | ✅ CRITIQUE |
| `.\mvnw.cmd verify -U` | `-U` = re-télécharge les dépendances même si Maven croit encore qu'elles sont absentes | Après correction de `pom.xml` (par ex. après le Piège P0-1). | Importante (cache d'échec Maven). |
| `.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev` | Compile et démarre l'application (équivalent `symfony server:start`), active le profil `dev` | Vérifier le démarrage de l'application en local. | Importante (partie « comment vérifier », suivante). |
| `Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing` (`curl`) | Envoie une requête GET (équivalent `curl`) | **La preuve par l'HTTP** que l'app tourne : attendre 15-30 s après le démarrage. Résultat attendu : `{"status":"UP"}`. | ✅ CRITIQUE |
| `git init` / `git add -A` / `git commit -m "chore: initialiser le squelette FinCore (phase 0)"` | Crée le dépôt, stage, fige l'état. Convention : **Conventional Commits** (`feat:`, `test:`, `docs:`, `chore:`…) | Historique propre à lire plus tard et les fichiers non-commités sont des **changements effrayants**. | Importante |

## 0.4 Fichiers créés ou modifiés

| Fichier | Rôle |
|---|---|
| `pom.xml` | Dépendances Maven + parent `spring-boot-starter-parent` — **corrigé** : version `4.1.2` (le `.RELEASE` a été abandonné), artefact **`springdoc-openapi-starter-webmvc-ui`** (v3.2.0, préfixe `springdoc-`) — Piège P0-4. |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/*` | Maven Wrapper — build sans Maven installé. |
| `FinCoreApplication.java` (`src/main/java/com/wince/fincore/`) | Point d'entrée : `@SpringBootApplication` — l'annotation **découvre et configure tout à notre place** (auto-configuration). |
| `application.yml` | Configuration : `datasource` (URL/user/password, port **5433** — conflict Laragon), `spring.jpa.hibernate.ddl-auto: validate`, `flyway.enabled`, profils `dev`/`test`/`prod`. |
| `V1__init_app_user.sql` | 1ʳᵉ migration : table `app_user` — `id UUID PRIMARY KEY`, `email VARCHAR(320) UNIQUE`, `password_hash VARCHAR(100)` (BCrypt ≈ 60 chars), `role VARCHAR(20) CHECK IN (4 rôles)`, `enabled`, `failed_attempts`, `locked_until TIMESTAMPTZ`, `created_at`. |
| `docker-compose.yml` | Service `db` : `postgres:17` (tag figé), port hôte `5433 → conteneur 5432`, healthcheck `pg_isready`, volume `fincore-db-data`, réseau `fincore-net`. |
| `.env.example` | Modèle de variables d'environnement — **complété en phase 2** avec `FINCORE_JWT_SECRET`. |
| `.gitignore` | Ignore `.env`, `target/`, IDE, logs… |
| `TestcontainersConfiguration.java` | Bean `PostgreSQLContainer` via `@ServiceConnection` — base éphémère **réelle** pendant les tests. |
| `src/{main,test}/java/com/wince/fincore/<module>/package-info.java` ×10 | Arborescence des 10 modules (`shared`, `identity`, `customer`, `account`, `ledger`, `payment`, `statement`, `compliance`, `notification`, `admin`) — traçabilité Git et module vide pour le futur. |
| `docs/GUIDE_DETAILLE.md` | Document pédagogique écrit au fil de l'eau — ce que tu lis attentivement. |

## 0.5 Code clé

### a. `application.yml` — cœur de la configuration

```yaml
spring:
  datasource:
    url: ${FINCORE_DB_URL:jdbc:postgresql://localhost:5433/fincore}
    #  ${VAR:défaut} — littéral Java : si FINCORE_DB_URL n'est pas
    #  définie, utiliser le défaut après le deux-points
  jpa:
    hibernate:
      ddl-auto: validate      # Hibernate NE crée rien, il vérifie
  flyway:
    enabled: true             # exécute V1__*.sql au démarrage si pas encore fait
```

### b. `V1__init_app_user.sql` — détails pédagogiques

```sql
CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL,  -- 320 = longueur max RFC 5321
    password_hash   VARCHAR(100) NOT NULL,  -- jamais le mot de passe en clair !
    role            VARCHAR(20) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,           -- NULL = non verrouillé
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_role CHECK (role IN ('CUSTOMER','TELLER','ADMIN','AUDITOR')),
    CONSTRAINT ck_app_user_failed_attempts CHECK (failed_attempts >= 0)
);
CREATE INDEX idx_app_user_role ON app_user (role);
```

**Pourquoi `TIMESTAMPTZ` et pas `TIMESTAMP` ?** Le cahier impose des horodatages UTC — `TIMESTAMPTZ` stocke en UTC et convertit à l'affichage. **Pourquoi `VARCHAR + CHECK` et pas un enum PostgreSQL ?** Un enum natif est lourd à faire évoluer ; un CHECK s'ajoute en une ligne.

### c. `docker-compose.yml` — détail pédagogique

```yaml
services:
  db:
    image: postgres:17            # tag figé (jamais "latest")
    ports:
      - "${POSTGRES_PORT:-5433}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER:-fincore} -d $${POSTGRES_DB:-fincore}"]
      # $$ = échappe pour Docker Compose → le $ reste pour le shell du conteneur
```

## 0.6 Comment vérifier

```powershell
# 1. La base tourne et est saine
docker compose ps                    # → fincore-db … (healthy), 0.0.0.0:5433->5432

# 2. Les tests passent
.\mvnw.cmd verify                    # → BUILD SUCCESS

# 3. L'app répond (2e terminal)
Start-Process .\mvnw.cmd -ArgumentList "spring-boot:run" -WindowStyle Hidden
Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing
# → 200 {"status":"UP"}

# 4. La migration a été exécutée
docker exec fincore-db psql -U fincore -d fincore -c "SELECT * FROM flyway_schema_history;"
# → 1 row : V1, success=true
```

## 0.7 Pièges rencontrés

### P0-1 — `Non-resolvable parent POM : 4.1.2.RELEASE`
- **Erreur exacte** : `spring-boot-starter-parent:pom:4.1.2.RELEASE was not found`.
- **Cause** : Initializr annonce `4.1.2.RELEASE`, mais Maven Central ne publie que `4.1.2`.
- **Correction** : `<version>4.1.2</version>` dans le parent du `pom.xml`, puis `.\mvnw.cmd verify -U`.
- **Leçon** : toujours vérifier qu'une version existe sur Maven Central avant de blâmer le réseau.

### P0-2 — `No enum constant ... SerializationFeature.write-dates-as-timestamps` au démarrage
- **Erreur exacte** : échec de liaison de `spring.jackson.serialization` (Boot 4 = Jackson 3).
- **Cause** : dans Boot 4, Jackson est externe (`tools.jackson`) et la feature `WRITE_DATES_AS_TIMESTAMPS` a été **supprimée** — les dates sont toujours ISO-8601.
- **Correction** : supprimer le bloc `spring.jackson.serialization` du `application.yml`.
- **Leçon** : **ne jamais deviner une API de librairie à partir d'exemples Boot 3** — Boot 4 ≠ Boot 3.

### P0-3 — `FATAL: le rôle « fincore » n'existe pas`
- **Cause** : **deux PostgreSQL sur le même port** — Laragon a déjà un PostgreSQL sur `127.0.0.1:5432`.
- **Correction** : configurer FinCore sur le port **5433**.
- **Leçon** : en cas de doute, `Get-NetTCPConnection -LocalPort 5432` révèle qui écoute sur quel port.

### P0-4 — artefact springdoc introuvable
- **Erreur** : `openapi-starter-webmvc-ui:3.2.0` → 404 sur Central.
- **Correction** : artefact réel : `org.springdoc:springdoc-openapi-starter-webmvc-ui` v3.2.0.

## 0.8 Questions d'entretien — Phase 0

**Q1. Quelle différence entre `mvn` et `mvnw` ?**
`mvnw` (Maven Wrapper) embarque la version pinnée de Maven (`.mvn/wrapper/maven-wrapper.properties`) — n'importe quel développeur ou CI build avec la même version sans rien installer. Équivalent d'un `composer.phar` commité.

**Q2. Pourquoi `ddl-auto: validate` et pas `update` ou `create` ?**
`validate` : Hibernate vérifie que le schéma SQL correspond aux entités, **sans modifier rien**. Le schéma appartient à Flyway — un `update` laisserait Hibernate modifier les tables en démarrage → divergences silencieuses. Échouer tôt et fort : le code et le SQL ne peuvent plus diverger en prod.

**Q3. Qu'est-ce qu'un healthcheck dans Docker et à quoi ça sert ?**
`pg_isready` exécuté périodiquement dans le conteneur — tant qu'il échoue, l'état est `starting` et `docker compose` (via `depends_on: condition: service_healthy`) **attend** la santé : on évite la race « app avant la base ».

**Q4. Pourquoi un port différent (5433) au lieu d'arrêter le PostgreSQL de Laragon ?**
Indépendance : le poste du développeur peut héberger d'autres projets. Chaque projet Docker réserve son port — `netstat` identifie toujours les conflits ; non-régression de l'environnement.

## 0.9 Exercice pour l'apprentissage (Phase 0)

1. Ajoute une **2ʳᵉ migration** `V2__add_phone_to_app_user.sql` (colonne `phone VARCHAR(20)`), relance `.\mvnw.cmd verify` et vérifie avec `docker exec fincore-db psql -U fincore -d fincore -c "\d app_user"` que la colonne existe dans le conteneur de test.
2. **Ne modifie surtout pas `V1`** (règle d'or du projet).
3. Bonus : ajoute `GET /actuator/info` avec `info.app.name=FinCore` dans `application.yml`.
