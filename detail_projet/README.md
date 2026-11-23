# Dossier `detail_projet` — Journal pédagogique de FinCore

> Journal pédagogique **au fil de l'eau** de l'implémentation de FinCore API
> (Java Spring Boot, cahier des charges : `C:\laragon\www\wince\JAVA\CAHIER_DES_CHARGES.md`).
>
> **À lire attentivement** si tu veux réapprendre le Java Spring Boot avec un
> projet réel. Ce dossier est écrit pour une personne débutante en Java : chaque
> section explique les concepts, les commandes, **pourquoi chaque commande est
> lancée** et **la raison de son importance**.

## Structure

| Fichier | Statut | Contenu |
|---|---|---|
| `phase0.md` | ✅ FAIT | Squelette Boot 4.1.2 / Java 25, Docker PostgreSQL 17, V1, `/actuator/health` |
| `phase1.md` | ✅ FAIT | Money, erreurs RFC 9457, correlationId, Swagger, Clock |
| `phase2.md` | ✅ FAIT | F-01 Authentication (JWT, refresh, rotation, verrouillage) |
| `phase3.md` | ✅ FAIT | F-02 Clients et KYC |
| `phase4.md` | ⏳ PRÉVUE | F-03 Comptes + numéro mod 97 + seed |
| `phase5.md` | ⏳ PRÉVUE | F-04 Grand livre + F-05 Dépôts / retraits |
| `phase6.md` | ⏳ PRÉVUE | F-06 Virements + plafonds + idempotence + F-09 Contre-passation + F-12 Audit |
| `phase7.md` | ⏳ PRÉVUE | F-07 Multi-devises |
| `phase8.md` | ⏳ PRÉVUE | F-08 Virements externes asynchrones + outbox + notifications |
| `phase9.md` | ⏳ PRÉVUE | F-10 Relevés JSON/CSV/PDF + F-13 Rapports |
| `phase10.md` | ⏳ PRÉVUE | F-11 Alertes AML |
| `phase11.md` | ⏳ PRÉVUE | F-14 Intérêts, Micrometer, ArchUnit |
| `phase12.md` | ⏳ PRÉVUE | Dockerfile, Compose, CI, README, ADR, glossaire, questions d'entretien |

Chaque fichier contient, dans l'ordre : **objectif de la phase** · **notions à
comprendre** (avec analogies PHP/Symfony quand elles existent) · **commandes
exécutées** (tableau : commande · ce qu'elle fait · pourquoi · importance) ·
**fichiers créés/modifiés** · **code clé** · **comment vérifier** ·
**pièges rencontrés** · **questions d'entretien** · **exercice pour l'apprentissage**.

## Comment lire

1. Commence par `phase0.md` → suis-les dans l'ordre jusqu'à `phase2.md`
   (phases FAIT).
2. Utilise les **questions d'entretien** de chaque phase pour t'auto-interroger.
3. Utilise les **exercices** pour appuyer l'apprentissage à chaque phase.
4. Quand les phases PRÉVUES seront faites, le fichier correspondant sera
   réécrit en statut FAIT (journaux au fil de l'eau).
