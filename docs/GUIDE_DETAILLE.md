# üìñ Guide d√©taill√© ‚Äî Journal p√©dagogique FinCore API

> **Objectif** : pouvoir refaire chaque phase seul et l'expliquer en entretien.
> Le lecteur conna√Æt **PHP/Symfony (ou Laravel)** et un peu de Java, mais n'a
> jamais utilis√© Docker en pratique. Chaque notion est donc expliqu√©e avec une
> analogie quand elle existe.

---

# PHASE 0 ‚Äî Squelette du projet, Docker, Flyway, premier endpoint

## 0.1 Objectif de la phase

1. Mettre en place un squelette Spring Boot 4.1.2 / Java 25 qui **d√©marre**.
2. Faire tourner PostgreSQL 17 dans un conteneur Docker (aucune install manuelle de base).
3. Cr√©er la premi√®re migration Flyway et prouver que `/actuator/health` r√©pond `UP`.
4. Tout versionner avec Git (premier commit propre).

## 0.2 Notions √† comprendre

### Spring Boot ‚âà le ¬´ microkernel ¬ª de Symfony
En Symfony, `bin/console` charge le conteneur, les bundles, la config. Spring Boot
fait pareil : il **d√©tecte** ce qui est sur le classpath (dans le `pom.xml`) et
configure tout √† votre place. C'est de l'¬´ auto-configuration ¬ª : si `spring-boot-starter-data-jpa`
est pr√©sent, Boot configure Hibernate automatiquement.

### Le conteneur d'injection de Spring ‚âà le conteneur de services Symfony
`@Service`, `@Component` en Java = services YAML/autowiring en Symfony. La r√®gle
d'or du projet : **injection par constructeur uniquement** (jamais `@Autowired`
sur les champs), comme le fait Symfony avec la promotion d'arguments.

### `pom.xml` ‚âà `composer.json`
| PHP | Java/Maven |
|---|---|
| `composer require vendor/package` | `<dependency>` dans `pom.xml` |
| `composer.lock` | (g√©r√© par Maven √† chaque build) |
| `vendor/` | `~/.m2/repository` (cache local) |
| `composer.json` scripts | plugins du `<build>` (ex. `spring-boot-maven-plugin`) |

**Maven** = le gestionnaire de paquets + le build. Il t√©l√©charge les d√©pendances
depuis **Maven Central** (l'√©quivalent de packagist.org).

### Maven Wrapper (`mvnw` / `mvnw.cmd`) ‚âà `composer` local + `symfony` binary
`mvnw.cmd` est un script qui **t√©l√©charge Maven tout seul** (version 3.10.1, cf.
`.mvn/wrapper/maven-wrapper.properties`) au premier lancer. Personne n'a besoin
d'avoir Maven install√© : le projet embarque son outil. Sur Windows on tape
`.\mvnw.cmd`, sous Linux/macOS `./mvnw`.

### Spring Initializr ‚âà le ¬´ create-project ¬ª de Symfony/Laravel
`https://start.spring.io` = le g√©n√©rateur en ligne qui produit un ZIP contenant
le squelette (pom, classe principale, tests). √âquivalent de
`symfony new mon-projet` ou `composer create-project`.

### Docker ‚âà Vagrant, en mieux
- **Image** = machine virtuelle fig√©e (ici `postgres:17`) ‚âà une VM packag√©e.
- **Conteneur** = une instance de cette image qui tourne ‚âà la VM d√©marr√©e.
- **Volume** = disque dur persistant ‚âà un dossier partag√© h√¥te/VM. Sans volume,
  toutes les donn√©es disparaissent √† l'arr√™t du conteneur.
- **`docker-compose.yml`** ‚âà un `docker-compose.yaml`‚Ä¶ justement, c'est le m√™me
  concept que pour Laravel : on d√©crit les services (`db`, plus tard `app`) et
  `docker compose up` lance tout d'un bloc.
- **Healthcheck** = le conteneur se teste lui-m√™me en boucle ; Docker ne d√©clare
  le service ¬´ pr√™t ¬ª que quand il r√©pond.

### Flyway ‚âà Doctrine Migrations
| Doctrine | Flyway |
|---|---|
| `make:migration` | on √©crit √† la main `V1__....sql` |
| `doctrine:migrations:migrate` | fait **automatiquement** au d√©marrage |
| table `migration_versions` | table `flyway_schema_history` |
| fichier `Version20261119...php` | `V1__init_app_user.sql` (num√©ro + nom) |

**R√®gle absolue** : une migration d√©j√† commit√©e ne se modifie **jamais**. On
corrige avec une nouvelle (`V2__...`). √âquivalent du rebase interdit en prod.

### `ddl-auto: validate` ‚âà `doctrine.orm.auto_generate_classes: false` + validate
Hibernate peut cr√©er ou mettre √† jour le sch√©ma tout seul (`create`, `update`)
‚Äî **c'est interdit ici**. Seule Flyway cr√©e les tables. `validate` v√©rifie
uniquement que les entit√©s Java correspondent au sch√©ma SQL : si l'un des deux
d√©rape, le d√©marrage √©choue au lieu de casser la base en silence.

### Actuator ‚âà le bundle `framework-extra-bundle` + pages de sant√©
Spring Boot Actuator expose des endpoints de supervision. Le plus utile :
`/actuator/health` (r√©pond `{"status":"UP"}`), utilis√© par Docker pour savoir
si l'application est vivante.

### Testcontainers ‚âà l'√©quivalent ¬´ la base en vrai ¬ª
Plut√¥t que de mocker la base (interdit par le cahier pour le grand livre),
Testcontainers **d√©marre un vrai PostgreSQL dans Docker** pendant les tests,
l'ex√©cute, puis le d√©truit. C'est comme si votre CI lan√ßait une VM √©ph√©m√®re
pour chaque suite de tests.

## 0.3 Commandes ex√©cut√©es

### A. V√©rification de l'environnement

```powershell
java -version
```
| | |
|---|---|
| **Ce qu'elle fait** | Affiche la version du JDK install√© |
| **Options** | aucune |
| **R√©sultat attendu** | `java version "27..."` (le projet cible 25, on compile avec `--release 25`) |
| **Erreur fr√©quente** | `'java' n'est pas reconnu` ‚Üí ajouter le JDK au PATH ou r√©installer |

```powershell
git --version
docker version --format "{{.Server.Version}}"
```
| | |
|---|---|
| **Ce qu'elle fait** | `git --version` : version de Git. `docker version` : contacte le **daemon** Docker (le service qui g√®re les conteneurs) et affiche sa version |
| **Options** | `--format "{{.Server.Version}}"` = n'affiche que le num√©ro du serveur (sortie propre, exploitable) |
| **R√©sultat attendu** | `git version 2.46...` et `29.8.1` |
| **Erreur fr√©quente** | `failed to connect to the docker API` = Docker Desktop n'est pas lanc√© ‚Üí le d√©marrer |

```powershell
Start-Process -FilePath "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe"
```
| | |
|---|---|
| **Ce qu'elle fait** | D√©marre Docker Desktop **en arri√®re-plan** (`Start-Process` = l'√©quivalent de `exec` d√©tach√©) |
| **Options** | chemin variable d'environnement `$env:LOCALAPPDATA` = emplacement utilisateur |
| **R√©sultat attendu** | Le daemon r√©pond apr√®s 20-60 s (barre de t√¢che Docker) |
| **Erreur fr√©quente** | Lancer Docker **avant** tout `docker compose` |

### B. G√©n√©ration du squelette

```powershell
Invoke-WebRequest -Uri "https://start.spring.io/starter.zip?..." -OutFile fincore.zip -UseBasicParsing
```
| | |
|---|---|
| **Ce qu'elle fait** | T√©l√©charge le ZIP du projet depuis Spring Initializr (√©quivalent `curl -o`) |
| **Param√®tres cl√©s** | `type=maven-project` (build Maven), `language=java`, `groupId=com.wince` (nom de l'organisation), `artifactId=fincore` (nom du projet), `packageName=com.wince.fincore` (paquet racine), `javaVersion=25`, `bootVersion=4.1.2.RELEASE`*, `dependencies=web,security,...` |
| **R√©sultat attendu** | `fincore.zip` (~17 Ko) |
| **Erreur fr√©quente** | URL mal encod√©e ‚Üí v√©rifier que `-d`/`-d` s√©parent bien cl√©/valeur |

\* **Pi√®ge P0-1** : Initializr annonce `4.1.2.RELEASE` mais Maven Central publie
`4.1.2` (le suffixe `.RELEASE` a √©t√© abandonn√© depuis Boot 3). Corrig√© dans le `pom.xml`.

```powershell
Expand-Archive -Path fincore.zip -DestinationPath . -Force
```
| | |
|---|---|
| **Ce qu'elle fait** | D√©compresse le ZIP (√©quivalent de 7-Zip / `unzip`) |
| **Options** | `-Force` = √©crase si le dossier existe d√©j√† |
| **R√©sultat attendu** | Dossier `fincore/` contenant `pom.xml`, `mvnw.cmd`, `src/` |

### C. Docker

```powershell
docker compose up -d db
```
| | |
|---|---|
| **Ce qu'elle fait** | Cr√©e et d√©marre **uniquement** le service `db` d√©fini dans `docker-compose.yml` |
| **Options** | `-d` = *detached* : la commande rend la main, le conteneur tourne en arri√®re-plan |
| **R√©sultat attendu** | `Container fincore-db Started` puis `healthy` apr√®s ~10 s |
| **Erreur fr√©quente** | `port is already allocated` = un autre service occupe le port ‚Üí changer `POSTGRES_PORT` |

```powershell
docker compose ps
```
| | |
|---|---|
| **Ce qu'elle fait** | Liste les conteneurs du projet courant avec leur √©tat |
| **R√©sultat attendu** | `fincore-db ... (healthy) ... 0.0.0.0:5433->5432/tcp` |

```powershell
docker exec fincore-db psql -U fincore -d fincore -c "\du"
```
| | |
|---|---|
| **Ce qu'elle fait** | `docker exec` = ex√©cute une commande **dans** le conteneur. Ici `psql` liste les r√¥les PostgreSQL (`\du`) |
| **Options** | `-U fincore` = utilisateur, `-d fincore` = base, `-c` = commande SQL |
| **R√©sultat attendu** | Une ligne r√¥le `fincore` |

### D. Build et tests

```powershell
.\mvnw.cmd verify
```
| | |
|---|---|
| **Ce qu'elle fait** | Ex√©cute le cycle complet Maven : compile ‚Üí tests unitaires ‚Üí tests d'int√©gration ‚Üí package ‚Üí v√©rification |
| **√âtapes internes** | `test` tourne `FinCoreApplicationTests` qui d√©marre un PostgreSQL Testcontainers, ex√©cute Flyway, charge le contexte Spring, puis d√©truit la base |
| **R√©sultat attendu** | `BUILD SUCCESS` / `Tests run: 1, Failures: 0, Errors: 0` |
| **Erreur fr√©quente** | `Non-resolvable parent POM` ‚Üí version du parent inexistante (voir P0-1) ; `role does not exist` ‚Üí mauvais PostgreSQL (voir P0-3) |

```powershell
.\mvnw.cmd verify -U
```
| | |
|---|---|
| **Ce qu'elle fait** | Idem + `-U` = *update* : force Maven √† **re-t√©l√©charger** m√™me les √©checs mis en cache (`*.lastUpdated`) |
| **Quand l'utiliser** | Apr√®s avoir corrig√© un `pom.xml` : sinon Maven croit encore que l'artefact n'existe pas |

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```
| | |
|---|---|
| **Ce qu'elle fait** | Compile et **d√©marre** l'application (√©quivalent `symfony server:start`) |
| **Options** | `-Dspring-boot.run.profiles=dev` = active le profil `dev` (√©quivalent `APP_ENV=dev`) |
| **R√©sultat attendu** | Ligne `Started FinCoreApplication` + port 8080 |

### E. V√©rification HTTP

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing
```
| | |
|---|---|
| **Ce qu'elle fait** | Envoie une requ√™te GET (√©quivalent `curl` ou le client HTTP de PhpStorm) |
| **R√©sultat attendu** | `200` + `{"groups":["liveness","readiness"],"status":"UP"}` |
| **Erreur fr√©quente** | `Connection refused` = l'app n'est pas encore d√©marr√©e (attendre 15-30 s) |

### F. Git

```powershell
git init
git add -A
git commit -m "chore: initialiser le squelette FinCore (phase 0)"
```
| | |
|---|---|
| **Ce qu'elle fait** | `init` cr√©e le d√©p√¥t ; `add -A` stage tout (respectant `.gitignore`) ; `commit` fige l'√©tat |
| **Convention** | **Conventional Commits** impos√©e par le cahier : `feat:`, `test:`, `docs:`, `chore:`‚Ä¶ |
| **Erreur fr√©quente** | `Please tell me who you are` ‚Üí `git config user.name "..."` + `git config user.email "..."` |

## 0.4 Fichiers cr√©√©s ou modifi√©s

| Fichier | R√¥le |
|---|---|
| `pom.xml` | D√©pendances Maven + plugin Boot ; **corrig√©** : parent `4.1.2`, springdoc `-starter-` |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | Maven Wrapper (build sans Maven install√©) |
| `src/main/java/.../FinorianCalendar.java`* | Classe principale `@SpringBootApplication` ‚Äî point d'entr√©e |
| `src/main/resources/application.yml` | Configuration : datasource, JPA `validate`, Flyway, profils `dev`/`test`/`prod` |
| `src/main/resources/db/migration/V1__init_app_user.sql` | 1 ≥·µâ migration : table `app_user` + contraintes |
| `docker-compose.yml` | Service `db` (postgres:17, port **5433**, healthcheck, volume, r√©seau) |
| `.env.example` | Mod√®le de variables (jamais de secret r√©el en Git) |
| `.gitignore` | Ignore `.env`, `target/`, IDE‚Ä¶ |
| `src/test/.../TestcontainersConfiguration.java` | Conteneur PostgreSQL de test (`postgres:17` au lieu de `latest`) |
| `src/{main,test}/java/.../<module>/package-info.java` √ó10 | Arborescence des modules (docs + suivi Git) |
| `docs/GUIDE_DETAILLE.md` | Ce fichier |

\* le fichier r√©el s'appelle `FinCoreApplication.java`.

## 0.5 Code cl√© comment√©

### `application.yml` ‚Äî le c≈ìur de la configuration
```yaml
spring:
  profiles:
    default: dev          # profil actif si aucun n'est impos√©
  datasource:
    url: ${FINCORE_DB_URL:jdbc:postgresql://localhost:5433/fincore}
    #  ^^^ ${VAR:defaut} = lit la variable d'environnement VAR,
    #      sinon utilise la valeur par d√©faut apr√®s les deux-points.
    #      √âquivalent Symfony : %env(resolve:DATABASE_URL)% avec fallback.
  jpa:
    hibernate:
      ddl-auto: validate  # Hibernate NE cr√©e rien, il v√©rifie seulement
    open-in-view: false   # pas de requ√™te lazy cach√©e dans un contr√¥leur
  flyway:
    enabled: true         # ex√©cute V1__*.sql au d√©marrage si pas encore fait
```

### `V1__init_app_user.sql` ‚Äî la migration
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
UTC ; `TIMESTAMPTZ` stocke en UTC et convertit √† l'affichage ‚Äî z√©ro confusion
de fuseau, exactement comme `datetime` immuable en PHP.

**Pourquoi `VARCHAR + CHECK` et pas un `enum` PostgreSQL** ? Un enum natif est
lourd √† faire √©voluer (migration complexe) ; un `CHECK` s'ajoute en une ligne.

### `docker-compose.yml` ‚Äî le service base
```yaml
services:
  db:
    image: postgres:17            # image officielle, tag fig√© (jamais "latest")
    ports:
      - "${POSTGRES_PORT:-5433}:5432"
      #        host:conteneur ‚îÄ‚îÄ‚îò    ‚îî‚îÄ le conteneur √©coute toujours sur 5432
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER:-fincore} ..."]
      # $$ = √©chappe pour Docker Compose ‚Üí le $ reste pour le shell du conteneur
```

## 0.6 Comment v√©rifier

```powershell
# 1. La base tourne
docker compose ps                    # ‚Üí (healthy)

# 2. Les tests passent
.\mvnw.cmd verify                    # ‚Üí BUILD SUCCESS

# 3. L'app r√©pond
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
# puis, dans un 2e terminal :
Invoke-WebRequest http://localhost:8080/actuator/health
# ‚Üí {"status":"UP"}

# 4. La migration a √©t√© ex√©cut√©e
docker exec fincore-db psql -U fincore -d fincore -c "SELECT * FROM flyway_schema_history;"
# ‚Üí 1 row : V1, success=true
```

## 0.7 Pi√®ges rencontr√©s

### P0-1 ‚Äî `Non-resolvable parent POM : 4.1.2.RELEASE`
- **Erreur exacte** : `spring-boot-starter-parent:pom:4.1.2.RELEASE was not found`
- **Cause** : Initializr annonce encore le suffixe `.RELEASE`, mais Maven Central
  ne publie que `4.1.2` (v√©rifi√© : `https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/` ‚Üí `4.1.2/` existe, `4.1.2.RELEASE/` ‚Üí 404).
- **Correction** : `<version>4.1.2</version>` dans le parent du `pom.xml`, puis `.\mvnw.cmd verify -U` (`-U` vide le cache d'√©chec).
- **Le√ßon** : toujours v√©rifier qu'une version existe sur Maven Central avant de
  bl√¢mer le r√©seau.

### P0-2 ‚Äî `No enum constant ... SerializationFeature.write-dates-as-timestamps`
- **Erreur exacte** : √©chec de liaison de `spring.jackson.serialization` au d√©marrage des tests.
- **Cause** : **Boot 4 = Jackson 3** (package `tools.jackson`). La feature
  `WRITE_DATES_AS_TIMESTAMPS` a √©t√© **supprim√©e** : les dates sont toujours en
  ISO-8601. La config h√©rit√©e d'exemples Boot 3 casse.
- **Correction** : supprimer le bloc `spring.jackson.serialization` du `application.yml`.
- **Le√ßon** (directive ¬ß2.3 du cahier) : **ne jamais deviner une API de
  librairie √† partir d'exemples anciens** ‚Äî Boot 4 ‚â† Boot 3.

### P0-3 ‚Äî `FATAL: le r√¥le ¬´ fincore ¬ª n'existe pas`
- **Erreur exacte** : √©chec Flyway/Hikari au d√©marrage de l'app alors que le
  conteneur est sain.
- **Cause** : **deux PostgreSQL sur le m√™me port** ‚Äî le poste a Laragon qui
  tourne d√©j√† un PostgreSQL sur `127.0.0.1:5432`. `localhost` r√©sout d'abord en
  `127.0.0.1` ‚Üí l'application atteint le PostgreSQL de Laragon (sans le r√¥le
  `fincore`), pas celui de Docker (`0.0.0.0:5433`).
- **Correction** : passer FinCore sur le port **5433** dans `docker-compose.yml`,
  `.env.example` et `application.yml`.
- **Le√ßon** : en cas de doute, `netstat -ano | findstr :5432` r√©v√®le qui √©coute
  sur quel port. Ne jamais supposer que `localhost` = le conteneur Docker.

### P0-4 ‚Äî L'artefact springdoc d'Initializr est introuvable
- **Erreur** : `org.springdoc:openapi-starter-webmvc-ui:3.2.0` ‚Üí 404 sur Central.
- **Correction** : l'artefact r√©el est `org.springdoc:springdoc-openapi-starter-webmvc-ui`
  (avec le pr√©fixe `springdoc-`). V√©rification faite : son parent POM est
  `spring-boot-starter-parent:4.1.2` ‚Üí pleinement compatible Boot 4.1.

## 0.8 Questions d'entretien (Phase 0)

**Q1. Quelle diff√©rence entre `mvn` et `mvnw` ?**
`mvnw` (Maven Wrapper) embarque la version exacte de Maven utilis√©e par le
projet : n'importe quel d√©veloppeur (ou CI) build avec la m√™me version sans
rien installer. C'est l'√©quivalent d'un `composer.phar` commit√©.

**Q2. Pourquoi `ddl-auto: validate` et pas `update` ?**
Parce que le sch√©ma appartient **exclusivement** √† Flyway. Laisser Hibernate
modifier les tables en d√©marrage provoquerait des divergences silencieuses entre
environnements. `validate` casse au d√©marrage si le code et le SQL divergent ‚Äî
c'est voulu : √©chouer t√¥t et fort.

**Q3. Qu'est-ce qu'un healthcheck dans Docker et √† quoi √ßa sert ?**
Une commande ex√©cut√©e p√©riodiquement dans le conteneur (`pg_isready` ici).
Tant qu'elle √©choue, l'√©tat est `starting` ; Docker Compose peut alors **attendre**
la sant√© (`depends_on: condition: service_healthy`) avant de lancer l'app ‚Äî on
√©vite la course au d√©marrage ¬´ app avant la base ¬ª.

**Q4. Pourquoi un port diff√©rent (5433) au lieu d'arr√™ter le PostgreSQL de Laragon ?**
Ind√©pendance : le poste du d√©veloppeur peut h√©berger d'autres projets. Chaque
projet Docker r√©serve son port ‚Äî c'est le principe de non-r√©gression de
l'environnement. `netstat` identifie toujours les conflits.

## 0.9 Exercice pour le candidat

1. Ajoutez une **2·µâ migration** `V2__add_phone_to_app_user.sql` qui ajoute une
   colonne `phone VARCHAR(20)` √† `app_user`, relancez `.\mvnw.cmd verify` et
   v√©rifiez avec `psql` que la colonne existe dans le conteneur de test‚Ä¶
   puis dans la base `docker compose` (relancez l'app : Flyway l'applique aussi).
2. **Ne modifiez surtout pas** `V1__...` : c'est la r√®gle d'or.
3. Bonus : ajoutez `GET /actuator/info` avec `info.app.name=FinCore` dans
   `application.yml` et observez la diff√©rence.

---

---

# PHASE 1 ó Socle transverse : Money, erreurs RFC 9457, correlationId, Swagger

## 1.1 Objectif de la phase

1. CrÈer le **domaine monÈtaire** (`Money`, `Currency`) : impossible d'additionner deux devises diffÈrentes.
2. Normaliser **toutes** les erreurs au format RFC 9457 (`application/problem+json`) avec un code mÈtier stable.
3. CorrÈler chaque requÍte : mÍme identifiant dans la rÈponse, les logs et les erreurs (`X-Correlation-Id` / `traceId`).
4. Servir **Swagger UI** en dev ; tout le reste reste refusÈ sans jeton (401).
5. Horloge injectable (`Clock`) ó jamais `Instant.now()` dans les services.

## 1.2 Notions ‡ comprendre

### Value object (`record`) ò classe immuable Symfony
`Money` est un **record** Java : les champs sont `final`, le constructeur valide,
`equals`/`hashCode` sont gÈnÈrÈs. C'est l'Èquivalent d'un value object
`readonly` en PHP 8.2 ó mais avec **zÈro boilerplate**.

### `BigDecimal` vs `float` ò `bcmath` vs `float` en PHP
Un `double` ne peut pas reprÈsenter exactement `0.1` : les banques refusent.
`BigDecimal` est un nombre dÈcimal exact (comme `bcmath`), et l'**arrondi
HALF_EVEN** (´ bancaire ª) Èvite la dÈrive systÈmatique : on arrondit vers le
nombre **pair** (2,5 ? 2 ; 3,5 ? 4), donc sur des millions d'opÈrations la
somme des arrondis reste nulle.

### `@RestControllerAdvice` ò listener `kernel.exception` Symfony
Un seul endroit qui transforme **toutes** les exceptions en rÈponse HTTP.
L'Èquivalent exact est un listener sur l'ÈvÈnement `kernel.exception` qui
retourne un `JsonErrorResponse`.

### RFC 9457 = standard ´ problem+json ª
```json
{ "type": "Ö/errors/insufficient-funds", "title": "Solde insuffisant",
  "status": 422, "detail": "Ö", "instance": "/api/v1/Ö",
  "code": "INSUFFICIENT_FUNDS", "traceId": "Ö", "timestamp": "Ö" }
```
Le champ **`code`** est la clÈ : stable, en majuscules, sur lequel le front
branche sa logique. Le `detail` (franÁais) peut changer sans casser le client.
…quivalent Symfony : les formats d'erreur normalisÈs du bundle API Platform.

### `OncePerRequestFilter` ò middleware Laravel/Symfony
Un filtre exÈcutÈ **une seule fois** par requÍte, avant le contrÙleur.
Ici il pose le `correlationId` dans le **MDC** (Map Diagnostic Context de
SLF4J) : c'est un ´ tableau magique ª par thread, vidÈ en `finally`.

### `MDC` ò le contexte du logger (monolog processor)
En Symfony on ajoute un *processor* Monolog pour injecter un UUID dans chaque
ligne. Ici `MDC.put("correlationId", Ö)` fait pareil : la clÈ apparaÓt
automatiquement dans le pattern de log configurÈ par `logging.pattern.correlation`.

### `Clock` ò injecter une horloge plutÙt que `time()`
RËgle d'or testable : on n'appelle jamais `Instant.now()` dans un service ó
on **injecte** un `Clock`. En test, on fournit `Clock.fixed(Ö)` : les
scÈnarios ´ cumul du jour ª deviennent dÈterministes. Fuseau bancaire :
`Africa/Porto-Novo` (UTC+1, siËge BCEAO) ó le stockage reste en UTC.

### Spring Security par dÈfaut ò `access_control` tout ‡ `IS_AUTHENTICATED_FULLY`
Sans configuration, Spring Security **verrouille tout** (c'est pourquoi
`/api-docs` rÈpondait 401 en phase 0). `SecurityConfig` ouvre
ponctuellement `health` + `swagger`, et le reste exige un jeton ? **401**
(et non 403 : 403 dirait ´ vous Ítes connectÈ mais sans droits ª, alors
qu'ici il n'y a mÍme pas de jeton ó CA-AUTH-04).

## 1.3 Commandes exÈcutÈes

### A. Compilation rapide (aller-retour)
```powershell
.\mvnw.cmd -q -DskipTests compile
```
| | |
|---|---|
| **Ce qu'elle fait** | Compile uniquement les sources **sans** exÈcuter les tests |
| **Options** | `-q` = quiet (n'affiche que les erreurs) ; `-DskipTests` = saute les tests |
| **RÈsultat attendu** | Aucune sortie = succËs |
| **Quand l'utiliser** | VÈrifier qu'une erreur de syntaxe/typage est corrigÈe avant de lancer les tests complets |

### B. Tests ciblÈs
```powershell
.\mvnw.cmd test "-Dtest=MoneyTest,CurrencyTest,CorrelationIdFilterTest,GlobalExceptionHandlerTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```
| | |
|---|---|
| **Ce qu'elle fait** | N'exÈcute **que** les classes nommÈes (surefire = plugin de tests Maven) |
| **Options** | `-Dtest=` liste les classes (sÈparÈes par `,`) ; `-Dsurefire.failIfNoSpecifiedTests=false` Èvite l'Èchec si un nom ne matche rien |
| **PiËge PowerShell** | Les `-D` contenant des points doivent Ítre **entre guillemets** : sans quotes, PowerShell coupe au `.` et Maven voit ´ phase inconnue ª |
| **RÈsultat attendu** | `Tests run: 29, Failures: 0` |

### C. Build complet
```powershell
.\mvnw.cmd verify
```
| | |
|---|---|
| **Ce qu'elle fait** | Cycle complet : compile ? tests ? package (JAR) ; inclut `FinCoreApplicationTests` (Testcontainers + contexte rÈel) |
| **RÈsultat attendu** | `BUILD SUCCESS` / `Tests run: 30, Failures: 0, Errors: 0` |
| **Erreur frÈquente** | `forked VM terminated` + `malloc failed` = **mÈmoire ÈpuisÈe** ? fermer l'app dev (`Stop-Process`) avant de relancer les tests |

### D. RedÈmarrage de l'app + vÈrifications
```powershell
# RedÈmarrage (l'app lancÈe en phase 0 doit Ítre arrÍtÈe d'abord)
Start-Process .\mvnw.cmd -ArgumentList "spring-boot:run" -WindowStyle Hidden

# Swagger + contrat ouverts en dev
curl.exe -sS -o NUL -w "%{http_code}" http://localhost:8080/swagger-ui.html   # ? 200
curl.exe -sS -o NUL -w "%{http_code}" http://localhost:8080/api-docs          # ? 200

# 401 + corps RFC 9457 + correlationId propagÈ (CA-AUTH-04)
curl.exe -sS -H "X-Correlation-Id: trace-essai-1" http://localhost:8080/no-such-path
# ? {"type":"Ö/errors/unauthenticated","code":"UNAUTHENTICATED","traceId":"trace-essai-1",Ö}

# Le mÍme identifiant ressort en en-tÍte de rÈponse
curl.exe -sS -D - -o NUL http://localhost:8080/actuator/health | findstr X-Correlation-Id
```

## 1.4 Fichiers crÈÈs ou modifiÈs

| Fichier | RÙle |
|---|---|
| `shared/domain/Currency.java` | Enum XOF(0)/EUR(2)/USD(2) + Èchelle autorisÈe |
| `shared/domain/Money.java` | Value object monÈtaire : arrondi HALF_EVEN, `parse()` rejette l'Èchelle excessive |
| `shared/domain/ErrorCode.java` | Table exhaustive des codes mÈtier ? statut HTTP + URI type |
| `shared/domain/FinCoreException.java` | Exception mÈtier transportant le `ErrorCode` + dÈtails structurÈs |
| `shared/api/GlobalExceptionHandler.java` | `@RestControllerAdvice` : tout ? ProblemDetail RFC 9457 (traceId, timestamp via Clock) |
| `shared/infrastructure/CorrelationIdFilter.java` | Lit/gÈnËre `X-Correlation-Id`, MDC, nettoyage en `finally` |
| `shared/infrastructure/ClockConfig.java` | Bean `Clock` en `Africa/Porto-Novo` |
| `shared/infrastructure/OpenApiConfig.java` | Document OpenAPI : titre, contact, schÈma JWT `bearerAuth` |
| `shared/infrastructure/SecurityConfig.java` | Health/Swagger publics ; sinon 401/403 en `problem+json` |
| `application.yml` | `logging.pattern.correlation`, `spring.mvc.problemdetails`, springdoc off en prod |
| `src/test/Ö/MoneyTest.java` | 15 tests : arrondis, signes, cross-currency, `parse()` |
| `src/test/Ö/CurrencyTest.java` | 3 tests : Èchelles et devises |
| `src/test/Ö/CorrelationIdFilterTest.java` | 4 tests : gÈnÈration, reprise, nettoyage du MDC |
| `src/test/Ö/GlobalExceptionHandlerTest.java` | 7 tests : statuts, codes, champs RFC 9457, masquage des 500 |

## 1.5 Code clÈ commentÈ

### `Money` ó le constructeur normalise tout
```java
public Money(BigDecimal amount, Currency currency) {
    Objects.requireNonNull(amount, "Ö");
    Objects.requireNonNull(currency, "Ö");
    // On arrondit ICI : le domaine ne stocke jamais une Èchelle ´ sale ª.
    amount = amount.setScale(currency.scale(), RoundingMode.HALF_EVEN);
}
```
Le constructeur compact d'un `record` peut **rÈaffecter** les paramËtres ó
c'est le point d'entrÈe unique o˘ tous les invariants sont garantis.

### `parse()` ó rejet explicite pour la saisie utilisateur
```java
if (value.scale() > currency.scale()) {
    throw new FinCoreException(ErrorCode.INVALID_AMOUNT_SCALE,
            "La devise XOF autorise au plus 0 dÈcimale(s)Ö");
}
```
**Distinction cruciale** : `Money.of()` *arrondit* (calculs internes),
`Money.parse()` *rejette* (saisie API, RG-DEP-03 ? 400). Arrondir silencieusement
une saisie reviendrait ‡ voler 0,50 XOF au client sans le prÈvenir.

### `CorrelationIdFilter` ó le `finally` obligatoire
```java
MDC.put(MDC_KEY, correlationId);
response.setHeader(HEADER, correlationId);
try {
    filterChain.doFilter(request, response);
} finally {
    MDC.remove(MDC_KEY);   // TOUJOURS : sinon le thread suivant hÈrite de l'ID
}
```
Les threads Tomcat sont **rÈutilisÈs** : sans `finally`, la requÍte 42
porterait le correlationId de la requÍte 41 dans ses logs ó erreur de
diagnostic trËs dure ‡ trouver.

### `application.yml` ó le pattern de log corrÈlÈ
```yaml
logging:
  pattern:
    correlation: "%X{correlationId:-} "
    #             ^^^^^^^^^^^^^^^^^^ variable MDC, "-"" = valeur si absente
```
Chaque ligne de log commence par le mÍme `traceId` que celui renvoyÈ au
client dans l'erreur : une plainte = une `grep`.

## 1.6 Comment vÈrifier

```powershell
.\mvnw.cmd verify                                  # ? 30 tests, BUILD SUCCESS
curl.exe -sS -o NUL -w "%{http_code}`n" http://localhost:8080/swagger-ui.html   # 200
curl.exe -sS -H "X-Correlation-Id: abc" http://localhost:8080/api/v1/x | jq .code # "UNAUTHENTICATED"
```

## 1.7 PiËges rencontrÈs

### P1-1 ó `swagger-ui` / `api-docs` ? 401 (avant SecurityConfig)
- **Cause** : Spring Security (dÈmarrÈ au P0-0) verrouille **tout** par dÈfaut.
- **Correction** : `SecurityConfig` avec `permitAll` sur `health` + `swagger` + `api-docs`, `anyRequest().authenticated()` pour le reste.
- **LeÁon** : en Spring Security, on n'ouvre pas, on **ferme** ó le dÈfaut (verrouillÈ) est le bon.

### P1-2 ó Sans jeton ? 403 ´ Forbidden ª au lieu de 401
- **Cause** : sans `AuthenticationEntryPoint` explicite, Spring renvoie 403 par dÈfaut ó alors qu'aucun jeton n'a mÍme ÈtÈ prÈsentÈ.
- **Correction** : `.exceptionHandling()` avec un `AuthenticationEntryPoint` ? 401 `UNAUTHENTICATED` + corps `problem+json` (CA-AUTH-04).
- **LeÁon** : 401 = ´ prouvez qui vous Ítes ª ; 403 = ´ vous Ítes identifiÈ mais refusÈ ª. Les confondre fait perdre des heures au front.

### P1-3 ó `found duplicate key logging` au dÈmarrage
- **Erreur exacte** : SnakeYAML refuse deux clÈs `logging:` dans le **mÍme** document YAML.
- **Cause** : ajout du `logging.pattern.correlation` dans un nouveau bloc alors que `logging.level` existait dÈj‡ avant le `---`.
- **Correction** : fusionner dans **un seul** bloc `logging` par document (les sÈparateurs `---` dÈlimitent des documents distincts, chacun a le sien).
- **LeÁon** : un `---` = un fichier YAML ‡ part entiËre ; les clÈs ne se fusionnent pas.

### P1-4 ó Tests de filtrage : mock Mockito = headers fantÙmes
- **SymptÙme** : `response.getHeader(...)` renvoie `null` alors que le filtre a bien appelÈ `setHeader` ó le test Èchoue ‡ tort.
- **Cause** : un mock gÈnÈrÈ par Mockito n'a **aucune logique mÈtier** : `setHeader` et `getHeader` sont indÈpendants (le mock ne stocke rien).
- **Correction** : utiliser `MockHttpServletRequest` / `MockHttpServletResponse` de Spring Test, qui reproduisent le vrai comportement du conteneur servlet.
- **LeÁon** : mock = on simule les *interactions*, pas le *comportement*. Pour le comportement, prendre le mock ´ rÈel ª de la framework.

### P1-5 ó `malloc failed` dans le fork Maven (tests)
- **Erreur exacte** : `There is insufficient memory for the Java Runtime Environment to continue` dans `*.dumpstream`.
- **Cause** : 7,8 Go de RAM, l'app dev + le JVM de tests + IDE tournent en mÍme temps ? le fork surefire ne peut pas s'allouer ~20 Mo.
- **Correction** : `Stop-Process` sur l'app Spring Boot avant `mvnw verify`, puis relancer.
- **LeÁon** : les tests Spring Boot (contexte complet + Testcontainers) sont gourmands ó un seul build ‡ la fois sur une machine de 8 Go.

### P1-6 ó HALF_EVEN : le test attendait le mauvais rÈsultat
- **SymptÙme** : `1250.50 ? 1251` attendu, `1250` obtenu.
- **Cause** : l'arrondi bancaire va au nombre **pair** : 1250 est pair ? `1250.50` **descend** ‡ 1250 (et `1251.50` **monte** ‡ 1252).
- **LeÁon** : HALF_EVEN n'arrondit pas ´ vers le haut ª, il Èlimine la dÈrive biaisÈe sur des millions d'Ècritures.

## 1.8 Questions d'entretien (Phase 1)

**Q1. Pourquoi un `record` Java pour `Money` et pas une classe ?**
Le `record` gÈnËre automatiquement constructeur, accesseurs, `equals`,
`hashCode`, `toString` ó et interdit les champs non-`final`. RÈsultat : une
classe valeur **sÈrialisable, comparable, immuable** en 10 lignes, au lieu de
~80 en classe standard. C'est l'Èquivalent d'un value object `readonly` PHP.

**Q2. DiffÈrence entre `Money.of()` et `Money.parse()` ?**
`of()` est l'entrÈe **interne** : il arrondit (HALF_EVEN) car les calculs
peuvent produire des Èchelles sales (taux de frais). `parse()` est l'entrÈe
**utilisateur** : il **rejette** une Èchelle supÈrieure ‡ la devise
(`100.50` en XOF ? 400 `INVALID_AMOUNT_SCALE`) ó on n'arrondit jamais une
saisie sans prÈvenir le client.

**Q3. Pourquoi `traceId` dans le corps d'erreur ?**
Parce que c'est le **seul** lien fiable entre une plainte client et les logs
serveur. Le client nous donne son `traceId`, on le `grep` dans ELK/Loki, on
trouve la ligne exacte ó sans lui, il faudrait reconstituer la requÍte ‡
partir d'une heure et d'une URL. Il vient du MDC : mÍme valeur que l'en-tÍte
`X-Correlation-Id` et que le pattern de log.

**Q4. Pourquoi 401 et non 403 sur une API sans jeton ?**
401 (`Unauthorized`) = ´ je ne sais pas qui vous Ítes, prouvez-le ª : le
client doit renvoyer son JWT. 403 (`Forbidden`) = ´ je sais qui vous Ítes,
mais vous n'y avez pas le droit ª : renvoyer le JWT ne changera rien. Une
erreur de classification fait perdre des heures au front (boucle de retry
inutile ou affichage ´ session expirÈe ª ‡ tort).

**Q5. Pourquoi un `Clock` injectÈ au lieu de `Instant.now()` ?**
ReproductibilitÈ. Les rËgles ´ cumul du jour ª (CA-LIM-03) dÈpendent du
temps : avec `Instant.now()`, un test qui Èchoue ‡ minuit ou en CI dÈcale
Èchoue alÈatoirement. Avec `Clock.fixed(2026-03-15T10:30Z, Ö)`, le test est
figÈ et dÈterministe. C'est l'Èquivalent de la ´ freeze time ª de Clockify
ou du `Clock` de Symfony (RFC de PHP).

**Q6. O˘ s'arrÍte `GlobalExceptionHandler` ó que voit le client sur un 500 ?**
Jamais la stack trace : un message gÈnÈrique (´ Une erreur interne est
survenueÖ ª) + le `traceId`. Le dÈtail rÈel part en `log.error` cÙtÈ serveur.
Filtrer le message Èvite les fuites d'information (nom de table, chemin
disque, requÍte SQL) ó c'est la rËgle OWASP A05:2021.

## 1.9 Exercice pour le candidat

1. Ajoutez un test qui prouve que `Money.parse("100.00", XOF)` est **acceptÈ**
   (Èchelle 2 > 0 ? non ó 2 dÈcimales > 0 dÈcimales ? doit Ítre **rejetÈ**) :
   vÈrifiez la frontiËre exacte, et corrigez le test si votre intuition
   Ètait fausse.
2. Faites Èchouer volontairement un test (`assertThat(true).isFalse()`),
   lancez `.\mvnw.cmd test`, et localisez le rapport HTML sous
   `target/surefire-reports/`.
3. Appelez l'API avec un `X-Correlation-Id` de votre choix, provoquez une
   erreur, et **retrouvez** ce mÍme identifiant dans la sortie console de
   l'app : prouvez la corrÈlation bout-en-bout.
