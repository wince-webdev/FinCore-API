# Phase 3 — F-02 : Clients et KYC — ⏳ PRÉVUE

> **Statut : PRÉVUE** — sera **FAIT** à la fin de cette phase (journaux au fil de l'eau).
>
> Phase 3 = **Clients et KYC** (MUST — cahier §7 « F-02 · Clients et KYC »).
> Règles couvertes : RG-KYC-01 à RG-KYC-05, CA-KYC-01 à CA-KYC-02.

## 3.1 Objectif de la phase (3 lignes)

1. Introduire l'entité **client** (`customer`) dans le modèle de données, avec les énumérés de niveau (`KycLevel`) et de statut (`KycStatus`).
2. Implémenter les routes du KYC : monter de niveau/vérifier/rejeter, afficher les informations client — réservées à `TELLER` et `ADMIN`.
3. Respecter l'intégrité des données : le numéro d'identification est **masqué** dans toute réponse (RG-KYC-03), le client est mineur ou majeur selon la `Clock` (RG-KYC-04).

## 3.2 Fonctionnalités prévues

**Entité `customer`** (table — cahier §6.1) :

| Colonne | Type / Rôle |
|---|---|
| `id UUID PK`, `user_id FK UNIQUE` | relation avec `AppUser` du Phase 2 (session ↔ données métier) |
| `first_name`, `last_name`, `birth_date`, `phone` | inscription minimale du client (RG-KYC-01 : LEVEL_1 / PENDING) |
| `id_doc_type` (enum), `id_doc_number_masked` | jamais le numéro clair — masqué `****1234` |
| `kyc_level` (enum `KycLevel` : `LEVEL_1/2/3`), `kyc_status` (enum `KycStatus` : `PENDING/VERIFIED/REJECTED`) | montée et statut de vérification (RG-KYC-01 à 04) |
| `created_at` | horodatage (UTC, `TIMESTAMPTZ`) |

**Règles prévues** :
- RG-KYC-01 : tout client arrive en `LEVEL_1` / `PENDING` — vérifier à la création.
- RG-KYC-02 : passage en `LEVEL_2` avec numéro d'identification renseigné + validation par `TELLER` ou `ADMIN` ; `LEVEL_3` (justificatif de domicile + source de revenus) par `ADMIN` seul.
- RG-KYC-04 : le client doit avoir **au moins 18 ans** — calculé avec la `Clock` injectée.
- RG-KYC-05 : niveau KYC → plafonds (voir Phase 4, F-03).

**Endpoint prévu** : `PATCH /customers/{id}/kyc` (TELLER/ADMIN) — CA-KYC-01 : le numéro d'identification n'apparaît dans aucune réponse ni dans aucun log.

## 3.3 Commandes prévues (à lancer quand la phase est lancée)

| Commande | Ce qu'elle fait | Pourquoi | Importance |
|---|---|---|---|
| `.\mvnw.cmd test` | Compile + exécute tous les tests existants + nouveaux | Vérification globale de la phase | ✅ CRITIQUE |
| `.\mvnw.cmd test "-Dtest=CustomerServiceTest,KycServiceTest"` | Exécute **uniquement** les classes nommées (pinces les suites lourdes) | Aller-retour rapide sur le code frais | Importante |
| `Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing` | Vérifie que l'app tourne | Preuve que le contexte Spring a bien démarré avec la nouvelle entity | Importante |
| `curl.exe -X PATCH -H "Authorization: Bearer <JWT ADMIN>" http://localhost:8080/api/v1/customers/{id}/kyc` | Exécution manuelle du KYC (valider un client, lire son niveau) | Vérifie la matrice de droits vraiment (403 pour un `CUSTOMER`) | Importante |
| `docker exec fincore-db psql -U fincore -d fincore -c "SELECT kyc_level, kyc_status FROM customer LIMIT 3;"` | Présentation des clients dans la console | Vérifie l'étatKYC visible | Importante |

## 3.4 Fichiers clés à créer (prévision)

| Fichier | Rôle |
|---|---|
| `customer/domain/Customer.java` | Entity `customer` : `firstName`, `lastName`, `birthDate (LocalDate)`, `phone`, `idDocType`, `idDocNumber` (chiffré), `idDocNumberMasked`, `kycLevel`, `kycStatus` — plus méthodes de masquage `maskIdDoc()` et vérification d'âge `isAdult()` |
| `customer/domain/KycLevel.java` | `LEVEL_1 / LEVEL_2 / LEVEL_3` |
| `customer/domain/KycStatus.java` | `PENDING / VERIFIED / REJECTED` |
| `customer/domain/IdDocType.java` | `ID_CARD / PASSPORT / DRIVER_LICENSE / …` |
| `customer/application/CustomerService.java` | Orchestration KYC (vérifier / monter de niveau / statut), vérifications d'âge (RG-KYC-04) |
| `customer/api/CustomerController.java` | `PATCH /customers/{id}/kyc` — body `KycUpdateRequest` (`kycLevel`, `idDocType`, `idDocNumber`), réponse `CustomerResponse` — protégé par `@PreAuthorize("hasRole('TELLER') or hasRole('ADMIN')")` |
| `customer/api/CustomerResponse.java` | DTO de réponse — contient `idDocNumberMasked`, jamais le numéro clair |
| `customer/repository/CustomerRepository.java` | `JpaRepository` : `findByUserId(UUID)` |
| `src/main/resources/db/migration/V3__create_customer.sql` | Migration : table `customer` + FK vers `app_user` |
| `src/test/java/com/wince/fincore/customer/application/CustomerServiceTest.java` | Tests unitaires : client mineur (17 ans) vs majeur (18 ans), vérification de niveau |
| `src/test/java/com/wince/fincore/customer/api/KycEndpointTest.java` | Tests d'intégration : 403 pour `CUSTOMER` sur PATCH (matrice §5) |

## 3.5 Pièges à surveiller

1. **Stockage du numéro d'identification** (RG-KYC-03) : haché ou chiffré — jamais en clair en base, ni dans les logs — attention à l'**héritage** : ne pas copier-coller le numéro de pièce dans les traces (la règle de la phase 1 « pas de donnée sensible en log »).
2. **Vérification d'âge** (RG-KYC-04) : `birthDate.atStartOfDay(zone).toInstant().isBefore(now.minusYears(18))` avec `zone = Africa/Porto-Novo` — le 18e anniversaire est respecté le jour de sa naissance si la date limite est plus tôt — décision pédagogique en entretien.
3. **`@Transactional`** (règle §2.2 Phase 0) : les modifications du KYC sont atomiques — `CustomerService.verify()` sur plusieurs étapes (mis à jour statut + niveau) = rollback si un échec.
4. **Test SQL du masquage** (CA-KYC-01 — à faire en Phase 5 ou maintenant) : un test d'extraction JSON affiche que la réponse HTTP ne contient pas le numéro complet — vérifier `$.idDocNumberMasked` se termine par les 4 derniers chiffres du numéro (`****1234`) et que `$.idDocNumber` est absent — écrire `noIdDocNumberInResponse()`.

## 3.6 Questions d'entretien — Phase 3

**Q1. Comment représenter le client et le lier au `AppUser` ?**
Deux entities : `AppUser` = session (login, verrouillage), `Customer` = données métier — FK `user_id UNIQUE` dans `customer` vers `app_user` — avec `CustomerRepository.findByUserId(userId)` — deux responsabilités séparées : c'est du **Single Responsibility** aussi visible en architecture.

**Q2. Comment calculer l'âge avec une `Clock` injectée (RG-KYC-04) ?**
`atStartOfDay(zone).toInstant().isBefore(now.minusYears(18))` — le fuseau (Africa/Porto-Novo) est un paramètre du problème — le "18 ans" est une règle, mais le calcul est intégré à la localisation — vérifier avec `LocalDate.of(2007, 1, 5).atStartOfDay(zone)`… plus tard quand je la rédige tout à fait.

**Q3. Comment faire masquer une donnée chiffrée/hachée ?**
Distinguer stockage vs exposition : le chiffré/haché est lu dans la base, le maskage `****1234` se fait **dans le DTO de réponse** (http). Règle d'or (CA-KYC-01) : le numéro d'identification complet ne doit figurer **nulle part** : pas dans une réponse HTTP, pas dans un log, pas dans un retour d'erreur.

**Q4. Comment représenter un énuméré fermé statut (PENDING/VERIFIED/REJECTED) ?**
Énuméré Java `@Enumerated(EnumType.STRING)` stocké en varchar — ou interprété via CHECK (comme le cahier du §6.1 pour `role`) — plus simple à faire évoluer par une migration. On retient parfois dans les deux cas, et là on choisit avant de coder (édition) : on conseille l'énuméré.

**Q5. Comment deux écritures en base (modification du level KYC) sont-elles atomisées ?**
`@Transactional` sur la méthode `verify()` de `CustomerService` — équivalent du `@Transactional` sur `AuthService` (Phase 2) — importante en entretien : la transaction va jusqu'au retour de la méthode — si elle lève, rollback.

## 3.7 Exercice pour l'apprentissage (Phase 3)

1. Écris un test : client né à 17 ans → `assertThat(service.isAdult(...)).isFalse()` ; client né à 18 ans → `isTrue()` — tu dois découvrir la frontière exacte — cas 18 ans et 19 ans.
2. Entraîne-toi à **faire échir un test** (bug volontaire dans le code ou l'assertion) — localise l'erreur dans `target/surefire-reports/com.wince.fincore.customer.application.CustomerServiceTest.txt` — ce fichier est comme le profil de session, éditable…
3. Bonus : ajoute un 2e énuméré dans `IdDocType` (par ex. `PASSPORT`), écris un test — retester.
