# BUSINESS SPECIFICATION — CRM-HLM

**Version :** 1.0
**Date :** 15 février 2026
**Statut :** Document de travail — Revue décisionnelle requise
**Classification :** Confidentiel

---

## Table des matières

1. Résumé exécutif
2. Contexte métier BTP / Promotion immobilière
3. Portée & périmètre
4. Utilisateurs cibles, rôles, organisation
5. Exigences business (non-fonctionnelles « métier »)
6. Règles métier (haut niveau)
7. Indicateurs de succès (KPIs) & reporting
8. Modèle commercial & packaging
9. Risques & plan de mitigation
10. Roadmap business
11. Annexes

---

[PAGE BREAK]

## 1. Résumé exécutif

### 1.1 Contexte et opportunité

CRM-HLM est une plateforme SaaS multi-tenant conçue pour les équipes de promotion immobilière et de construction (BTP). [EVIDENCE] Le système fournit un accès aux données isolé par tenant, des permissions basées sur les rôles et une API REST consommée par une application Angular mono-page. (EV: docs/overview.md#Mission)

La plateforme adresse le besoin de digitalisation des processus commerciaux des promoteurs immobiliers : gestion du portefeuille de biens, suivi des prospects, gestion des réservations et acomptes, et pilotage opérationnel via un tableau de bord. [INFERENCE] Justification : la combinaison des modules Property, Contact, Deposit et Dashboard observée dans le code couvre l'ensemble du cycle de vie d'une transaction immobilière, de la prospection à la vente.

### 1.2 Proposition de valeur

CRM-HLM centralise les opérations commerciales d'un promoteur immobilier dans une interface unique, offrant :

- **Isolation multi-tenant** : chaque promoteur (tenant) dispose d'un espace de données strictement cloisonné. [EVIDENCE] Le JWT inclut un claim `tid` (tenant ID) extrait par `JwtAuthenticationFilter` et stocké dans un `TenantContext` (ThreadLocal). Tous les repositories filtrent par `tenant_id`. (EV: docs/architecture.md#Tenant-isolation-enforcement)

- **Gestion du cycle de vie des biens** : du brouillon à la vente, en passant par la réservation, avec verrouillage automatique du stock. [EVIDENCE] L'enum `PropertyStatus` définit six états : DRAFT → ACTIVE → RESERVED → SOLD, plus WITHDRAWN et ARCHIVED. (EV: hlm-backend/.../property/domain/PropertyStatus.java)

- **Processus de réservation avec acompte** : dépôt d'acompte, confirmation, annulation et expiration automatique avec notifications. [EVIDENCE] Le `DepositService` gère le cycle complet PENDING → CONFIRMED / CANCELLED / EXPIRED, avec un scheduler horaire pour l'expiration automatique. (EV: hlm-backend/.../deposit/service/DepositService.java#runHourlyWorkflow)

- **Contrôle d'accès granulaire (RBAC)** : trois rôles avec des permissions différenciées. [EVIDENCE] Les rôles ADMIN (CRUD complet), MANAGER (CRU, sans suppression), AGENT (lecture seule) sont définis dans `UserRole`. (EV: hlm-backend/.../user/domain/UserRole.java)

### 1.3 Périmètre MVP vs Futur (In/Out)

| Catégorie | In-scope (MVP) | Out-of-scope (Futur) |
|---|---|---|
| Authentification | Login JWT mono-rôle par tenant | SSO, OAuth2, MFA [OPEN] |
| Biens immobiliers | CRUD + 5 types + dashboard + statut | Médias/photos, visite virtuelle [OPEN] |
| Contacts | Prospects + Clients, conversion, intérêts | Import CSV, communication intégrée [OPEN] |
| Réservations | Acompte, confirmation, annulation, expiration auto | Paiement en ligne, contrat digital [OPEN] |
| Notifications | In-app (dépôts) | Email, SMS, push [OPEN] |
| Multi-tenant | Isolation par JWT `tid` | Marketplace inter-tenants [OPEN] |
| Reporting | Dashboard propriétés, rapport dépôts par agent | BI avancée, exports PDF [OPEN] |
| Infrastructure | Monolithe Spring Boot + Angular SPA | Microservices, scaling horizontal [OPEN] |

### 1.4 Résumé des risques majeurs (business/delivery)

1. **Risque d'adoption** : absence de données sur les utilisateurs cibles et le marché adressable. [OPEN] Quels segments de promoteurs (taille, géographie) sont visés en priorité ?
2. **Risque légal** : aucune mention de conformité RGPD, droit immobilier marocain ou régulation des acomptes. [OPEN] Quelles obligations légales encadrent la collecte de données personnelles et la gestion des acomptes ?
3. **Risque delivery** : plusieurs tests d'intégration pré-existants en échec (AuthMeIT, ContactControllerIT, TenantControllerIT). [EVIDENCE] Les rapports Failsafe montrent des échecs sur ces suites. (EV: hlm-backend/target/failsafe-reports/)
4. **Risque de scalabilité** : architecture monolithique, verrouillage pessimiste sur PostgreSQL. [INFERENCE] Justification : le `PESSIMISTIC_WRITE` lock dans `PropertyRepository` peut poser des problèmes de contention à forte volumétrie.

---

[PAGE BREAK]

## 2. Contexte métier BTP / Promotion immobilière

### 2.1 Acteurs et processus actuels (as-is)

Le système CRM-HLM modélise les acteurs suivants :

**Promoteur immobilier (Tenant)** : l'entité organisationnelle propriétaire des données. Chaque promoteur dispose d'un espace isolé, identifié par une clé unique (`tenantKey`). [EVIDENCE] La table `tenant` constitue le registre des tenants. L'endpoint `POST /tenants` permet le bootstrap d'un nouveau promoteur. (EV: docs/api.md#Tenants)

**Utilisateurs internes** : les collaborateurs du promoteur, répartis en trois rôles hiérarchiques :

- **Administrateur** (ROLE_ADMIN) : accès complet, gestion des ressources, suppression.
- **Manager** (ROLE_MANAGER) : création, lecture, mise à jour (sans suppression).
- **Agent** (ROLE_AGENT) : consultation uniquement (rôle par défaut).

[EVIDENCE] Ces rôles sont définis dans `UserRole` avec une hiérarchie documentée. (EV: hlm-backend/.../user/domain/UserRole.java)

**Prospects et clients** : les personnes physiques ou morales intéressées par les biens du promoteur. [EVIDENCE] Le modèle distingue `ContactType` (PROSPECT, TEMP_CLIENT, CLIENT) et `ContactStatus` (PROSPECT, QUALIFIED_PROSPECT, CLIENT, ACTIVE_CLIENT, COMPLETED_CLIENT, REFERRAL, LOST). (EV: hlm-backend/.../contact/domain/ContactType.java, ContactStatus.java)

### 2.2 Problématiques et pain points

[INFERENCE] Sur la base des fonctionnalités développées, les problématiques métier adressées sont :

1. **Gestion manuelle du stock de biens** : sans système centralisé, les promoteurs risquent la double réservation d'un même bien. Le système résout cela par un verrouillage automatique (`ACTIVE → RESERVED`). Justification : l'existence de l'`ErrorCode.PROPERTY_ALREADY_RESERVED` et du lock pessimiste confirme que la double réservation est un cas d'usage critique. (EV: hlm-backend/.../common/error/ErrorCode.java)

2. **Suivi des prospects fragmenté** : la qualification des prospects (statut, intérêts pour des biens spécifiques) nécessite un outil dédié. Le module Contact + ContactInterest répond à ce besoin. [EVIDENCE] L'API `POST /api/contacts/{id}/interests` et `GET /api/contacts/{id}/interests` permettent de gérer les intérêts d'un prospect pour des biens spécifiques. (EV: docs/api.md#Contacts)

3. **Absence de traçabilité des acomptes** : les dépôts, confirmations et annulations ne sont pas tracés de manière fiable. Le module Deposit avec notifications automatiques et rapport par agent apporte cette traçabilité. [EVIDENCE] L'endpoint `GET /api/deposits/report` agrège les dépôts avec filtres (statut, agent, contact, propriété, période). (EV: docs/api.md#Deposits)

4. **Manque de visibilité opérationnelle** : sans tableau de bord, les managers ne disposent pas de vue consolidée. Le dashboard propriétés permet de suivre le stock par statut et période. [EVIDENCE] L'endpoint `GET /dashboard/properties/summary` fournit des statistiques agrégées. (EV: docs/api.md#Property-Dashboard)

### 2.3 Objectifs de transformation (to-be)

| Objectif | Module CRM-HLM | Indicateur cible |
|---|---|---|
| Éliminer la double réservation | Property locking + Deposit | 0 conflit de réservation [INFERENCE] |
| Centraliser le suivi prospect | Contact + Interest | TBD [OPEN] |
| Automatiser l'expiration des acomptes | Deposit scheduler | Expiration automatique à l'échéance [EVIDENCE] (EV: DepositService.java#runHourlyWorkflow) |
| Fournir une visibilité en temps réel | Dashboard + Notifications | TBD [OPEN] |
| Garantir l'isolation des données | Multi-tenant architecture | 0 fuite de données inter-tenant [EVIDENCE] (EV: docs/architecture.md#Tenant-isolation-enforcement) |

---

[PAGE BREAK]

## 3. Portée & périmètre

### 3.1 In-scope (MVP)

Les modules suivants sont implémentés et testés :

**3.1.1 Authentification & Sécurité**

- Login par tenant (`tenantKey` + `email` + `password`) → JWT HS256. [EVIDENCE] (EV: docs/api.md#POST-/auth/login)
- Vérification d'identité (`GET /auth/me`). [EVIDENCE] (EV: docs/api.md#GET-/auth/me)
- RBAC à trois niveaux (ADMIN, MANAGER, AGENT). [EVIDENCE] (EV: hlm-backend/.../user/domain/UserRole.java)
- Isolation tenant par claim JWT `tid` + ThreadLocal. [EVIDENCE] (EV: docs/architecture.md#Tenant-isolation-enforcement)

**3.1.2 Gestion des biens immobiliers**

- CRUD complet : création (DRAFT), mise à jour, consultation, suppression logique (soft delete). [EVIDENCE] (EV: docs/api.md#Properties)
- Cinq types de biens : VILLA, DUPLEX, APPARTEMENT, LOT, TERRAIN_VIERGE, avec validation spécifique par type. [EVIDENCE] (EV: hlm-backend/.../property/domain/PropertyType.java)
- Cycle de vie : DRAFT → ACTIVE → RESERVED → SOLD (+ WITHDRAWN, ARCHIVED). [EVIDENCE] (EV: hlm-backend/.../property/domain/PropertyStatus.java)
- Référence unique par tenant (`reference_code`). [EVIDENCE] (EV: hlm-backend/.../property/domain/Property.java#UniqueConstraint)
- Dashboard agrégé par tenant avec filtres temporels. [EVIDENCE] (EV: docs/api.md#Property-Dashboard)

**3.1.3 Gestion des contacts (Prospects & Clients)**

- CRUD contacts avec recherche paginée (texte libre, statut, type). [EVIDENCE] (EV: docs/api.md#Contacts)
- Workflow prospect : PROSPECT → QUALIFIED_PROSPECT → TEMP_CLIENT → CLIENT. [EVIDENCE] (EV: hlm-backend/.../contact/domain/ContactStatus.java, ContactType.java)
- Gestion des intérêts (association contact ↔ bien). [EVIDENCE] (EV: docs/api.md#POST-/api/contacts/{id}/interests)
- Détails spécialisés : `ProspectDetail` (budget, source, notes) et `ClientDetail` (type client, société, ICE, SIRET). [EVIDENCE] (EV: hlm-backend/.../contact/domain/ProspectDetail.java, ClientDetail.java)
- Conversion prospect → client via acompte. [EVIDENCE] (EV: docs/api.md#POST-/api/contacts/{id}/convert-to-client)

**3.1.4 Réservations & Acomptes**

- Création d'acompte avec verrouillage automatique du bien (ACTIVE → RESERVED). [EVIDENCE] (EV: hlm-backend/.../deposit/service/DepositService.java#create)
- Cycle de vie : PENDING → CONFIRMED / CANCELLED / EXPIRED. [EVIDENCE] (EV: hlm-backend/.../deposit/domain/DepositStatus.java)
- Anti-double réservation : unicité (tenant, contact, property) + vérification statut ACTIVE. [EVIDENCE] (EV: hlm-backend/.../deposit/repo/DepositRepository.java, DepositService.java)
- Expiration automatique par scheduler horaire. [EVIDENCE] (EV: hlm-backend/.../deposit/service/DepositService.java#runHourlyWorkflow)
- Libération automatique du bien sur annulation ou expiration (RESERVED → ACTIVE). [EVIDENCE] (EV: hlm-backend/.../deposit/service/DepositService.java#releasePropertyReservation)
- Rapport par agent, contact, bien, statut et période. [EVIDENCE] (EV: docs/api.md#GET-/api/deposits/report)

**3.1.5 Notifications**

- Notifications in-app par utilisateur et tenant. [EVIDENCE] (EV: docs/api.md#Notifications)
- Types : DEPOSIT_CREATED, DEPOSIT_PENDING, DEPOSIT_DUE_SOON, DEPOSIT_CONFIRMED, DEPOSIT_CANCELLED, DEPOSIT_EXPIRED. [EVIDENCE] (EV: hlm-backend/.../notification/domain/NotificationType.java)
- Marquage lu/non-lu. [EVIDENCE] (EV: docs/api.md#POST-/api/notifications/{id}/read)

**3.1.6 Frontend Angular**

- SPA avec routage protégé (`/app/*`). [EVIDENCE] (EV: docs/frontend.md#Routing)
- Pages : login, properties (liste avec badges statut), contacts (liste + détail), prospects (liste + détail + dépôts). [EVIDENCE] (EV: hlm-frontend/src/app/features/)
- Proxy de développement vers le backend. [EVIDENCE] (EV: docs/frontend.md#Local-development)

### 3.2 Out-of-scope (MVP)

| Fonctionnalité | Remarque |
|---|---|
| Gestion des utilisateurs en self-service | [OPEN] Pas d'endpoint pour créer/modifier des utilisateurs au-delà du seed initial. L'API tenants crée un tenant+owner mais pas d'invitation d'utilisateurs. |
| Import/export de données | [OPEN] Aucun mécanisme d'import CSV ou export Excel identifié. |
| Médias et photos de biens | [OPEN] L'entité Property ne contient aucun champ média. |
| Paiement en ligne | [OPEN] Les acomptes sont tracés mais le paiement est externe. |
| Communication intégrée (email, SMS) | [OPEN] Les notifications sont exclusivement in-app. |
| Internationalisation (i18n) | [OPEN] L'interface semble mélanger français et anglais. |
| Application mobile | [OPEN] Frontend uniquement web. |

### 3.3 Dépendances et prérequis (data, ops, légaux)

**Techniques :**
- PostgreSQL 14+ [EVIDENCE] (EV: docs/local-dev.md#Prerequisites)
- Java 21 [EVIDENCE] (EV: hlm-backend/pom.xml)
- Docker (pour Testcontainers) [EVIDENCE] (EV: docs/local-dev.md#Prerequisites)
- Node 18+, npm 9+ [EVIDENCE] (EV: docs/local-dev.md#Prerequisites)

**Opérationnels :**
- Hébergement de la base PostgreSQL et du backend Spring Boot. [OPEN] Quelle infrastructure d'hébergement est prévue (cloud provider, on-premise) ?
- Gestion des secrets (JWT_SECRET, credentials DB). [EVIDENCE] Le secret JWT est injecté par variable d'environnement et doit faire ≥ 32 caractères. (EV: README.md#Required-Environment-Variables)

**Légaux :**
- [OPEN] Quelle conformité RGPD est requise pour le traitement des données personnelles des prospects/clients ?
- [OPEN] Quel cadre légal régit les acomptes de réservation immobilière au Maroc ?
- [OPEN] Des CGU/CGV sont-elles nécessaires pour l'utilisation du SaaS ?

---

[PAGE BREAK]

## 4. Utilisateurs cibles, rôles, organisation

### 4.1 Personas

**Persona 1 : Administrateur (ROLE_ADMIN)**

| Attribut | Détail |
|---|---|
| Rôle système | ROLE_ADMIN |
| Permissions | CRUD complet sur toutes les ressources, suppression, accès dashboard |
| Cas d'usage | Configuration du tenant, gestion des biens, supervision des agents, consultation des rapports |
| Source | [EVIDENCE] (EV: hlm-backend/.../user/domain/UserRole.java) |

**Persona 2 : Manager (ROLE_MANAGER)**

| Attribut | Détail |
|---|---|
| Rôle système | ROLE_MANAGER |
| Permissions | Création, lecture, mise à jour (pas de suppression), accès dashboard |
| Cas d'usage | Création de biens, mise à jour des statuts, consultation des rapports, supervision d'équipe |
| Source | [EVIDENCE] (EV: hlm-backend/.../user/domain/UserRole.java) |

**Persona 3 : Agent commercial (ROLE_AGENT)**

| Attribut | Détail |
|---|---|
| Rôle système | ROLE_AGENT |
| Permissions | Lecture seule sur les biens et contacts (rôle par défaut pour les nouveaux utilisateurs) |
| Cas d'usage | Consultation du stock, recherche de prospects, réception de notifications |
| Source | [EVIDENCE] (EV: hlm-backend/.../user/domain/UserRole.java) |

[OPEN] Existe-t-il d'autres personas métier non couverts par le système actuel (directeur commercial, service juridique, comptabilité) ?

### 4.2 Gouvernance et RACI

[OPEN] Aucune matrice RACI n'est définie dans les sources. Les questions suivantes restent ouvertes :

- Qui est responsable de la validation finale d'une réservation (confirmation de l'acompte) ?
- Qui décide du passage d'un bien de DRAFT à ACTIVE ?
- Qui autorise l'annulation d'un acompte confirmé ?
- Comment sont gérés les conflits entre agents sur un même prospect ?

[INFERENCE] Proposition de RACI basée sur les permissions techniques :

| Activité | ADMIN | MANAGER | AGENT |
|---|---|---|---|
| Créer un bien | R/A | R | I |
| Publier un bien (DRAFT → ACTIVE) | R/A | R | I |
| Créer un acompte | R/A | R | C |
| Confirmer un acompte | R/A | R | I |
| Annuler un acompte | R/A | R | I |
| Supprimer un bien | R/A | — | — |

Justification : les permissions `@PreAuthorize` dans les contrôleurs définissent les rôles autorisés pour chaque opération, ce qui constitue un RACI technique de facto.

### 4.3 Parcours opérationnels (jour type)

**Parcours type d'un Agent commercial :**

1. Connexion via login (tenant + email + mot de passe). [EVIDENCE] (EV: docs/api.md#POST-/auth/login)
2. Consultation de la liste des biens disponibles (filtrage par type, statut, ville). [EVIDENCE] (EV: docs/api.md#GET-/api/properties)
3. Recherche de prospects dans la base contacts. [EVIDENCE] (EV: docs/api.md#GET-/api/contacts)
4. Consultation des intérêts d'un prospect pour des biens spécifiques. [EVIDENCE] (EV: docs/api.md#GET-/api/contacts/{id}/interests)
5. Consultation des notifications (acomptes en échéance, confirmations). [EVIDENCE] (EV: docs/api.md#GET-/api/notifications)

**Parcours type d'un Manager/Admin :**

1. Connexion.
2. Consultation du dashboard propriétés (stock, tendances). [EVIDENCE] (EV: docs/api.md#GET-/dashboard/properties/summary)
3. Création ou mise à jour de biens immobiliers. [EVIDENCE] (EV: docs/api.md#POST-/api/properties)
4. Création d'acomptes pour qualifier les prospects intéressés. [EVIDENCE] (EV: docs/api.md#POST-/api/deposits)
5. Confirmation ou annulation des acomptes en attente. [EVIDENCE] (EV: docs/api.md#POST-/api/deposits/{id}/confirm)
6. Consultation du rapport des dépôts par agent. [EVIDENCE] (EV: docs/api.md#GET-/api/deposits/report)

---

[PAGE BREAK]

## 5. Exigences business (non-fonctionnelles « métier »)

### 5.1 Qualité de service attendue (SLA, support)

[OPEN] Aucun SLA n'est défini dans les sources. Les questions suivantes doivent être traitées :

- Quel est le taux de disponibilité cible (99,5 % ? 99,9 %) ?
- Quel temps de réponse maximum est acceptable pour les endpoints critiques (login, création d'acompte) ?
- Quel support est prévu (horaires, canaux, niveaux de priorité) ?
- Existe-t-il un plan de reprise après sinistre (RPO/RTO) ?

### 5.2 Auditabilité et traçabilité (exigence métier)

Le système fournit plusieurs éléments de traçabilité :

- **Horodatage** : toutes les entités principales (Property, Contact, Deposit, Notification) disposent de champs `created_at` et `updated_at`. [EVIDENCE] (EV: hlm-backend/.../property/domain/Property.java, contact/domain/Contact.java)
- **Identification de l'acteur** : les biens enregistrent `created_by` et `updated_by` (UUID de l'utilisateur). [EVIDENCE] (EV: hlm-backend/.../property/domain/Property.java#createdBy)
- **Suppression logique** : les biens ne sont pas supprimés physiquement mais marqués `deleted_at`. [EVIDENCE] (EV: hlm-backend/.../property/domain/Property.java#softDelete)
- **Notifications** : chaque événement de dépôt génère une notification persistée avec payload JSON. [EVIDENCE] (EV: hlm-backend/.../notification/domain/Notification.java)

[OPEN] Manques identifiés en matière d'audit :
- Pas de journal d'audit (audit log) centralisé pour toutes les opérations.
- Pas de traçabilité des connexions (login/logout).
- Pas de versioning des modifications de contacts ou de biens.

### 5.3 Sécurité & confidentialité (niveau attendu, conformité)

**Mesures en place :**

- Authentification par JWT HS256 avec secret ≥ 32 caractères et TTL configurable. [EVIDENCE] (EV: docs/security.md#JWT-configuration)
- Isolation tenant stricte : chaque requête est filtrée par `tenant_id`. [EVIDENCE] (EV: docs/architecture.md#Tenant-isolation-enforcement)
- RBAC à trois niveaux avec `@PreAuthorize`. [EVIDENCE] (EV: docs/security.md#RBAC-conventions)
- Nettoyage du contexte tenant après chaque requête (prévention des fuites ThreadLocal). [EVIDENCE] (EV: docs/security.md#Tenant-context-rules)
- Fail-fast sur secret manquant : l'application refuse de démarrer si `JWT_SECRET` est absent. [EVIDENCE] (EV: README.md#Required-Environment-Variables)

[OPEN] Points de sécurité non couverts :
- Chiffrement des données au repos (database encryption) ?
- Rate limiting sur les endpoints d'authentification ?
- Politique de mots de passe (complexité, rotation) ?
- Conformité RGPD (droit à l'oubli, portabilité, consentement) ?
- Gestion des secrets en production (vault, KMS) ?
- HTTPS/TLS en production ?

---

[PAGE BREAK]

## 6. Règles métier (haut niveau)

### 6.1 Définitions : Prospect / Client / Acompte / Réservation / Bien / Projet

**Prospect** : personne physique ou morale enregistrée dans le système sans engagement financier. Un prospect peut exprimer des intérêts pour un ou plusieurs biens. [EVIDENCE] Le `ContactType.PROSPECT` est le type par défaut à la création d'un contact. (EV: hlm-backend/.../contact/domain/ContactType.java)

**Client temporaire (TEMP_CLIENT)** : prospect ayant versé un acompte mais dont le dépôt n'est pas encore confirmé. Période limitée (par défaut, jusqu'à la date d'échéance du dépôt, généralement 7 jours). [EVIDENCE] Le `ContactType.TEMP_CLIENT` est attribué lors de la création d'un acompte ; `tempClientUntil` est fixé à la `dueDate` du dépôt. (EV: hlm-backend/.../deposit/service/DepositService.java#applyContactReservationWorkflow)

**Client** : contact dont l'acompte a été confirmé. La conversion est irréversible. [EVIDENCE] Lors de la confirmation, le contact passe à `ContactType.CLIENT` et un `ClientDetail` est créé. (EV: docs/ai/deep-context.md#Deposit-lifecycle)

**Acompte (Deposit)** : engagement financier d'un prospect sur un bien, avec montant, date de dépôt, date d'échéance et référence. [EVIDENCE] L'entité `Deposit` contient les champs `amount`, `currency`, `depositDate`, `dueDate`, `reference`, `status`. (EV: hlm-backend/.../deposit/domain/Deposit.java)

**Réservation** : le résultat de la création d'un acompte sur un bien. Le bien passe au statut `RESERVED` et ne peut recevoir d'autre acompte tant que le premier est actif (PENDING ou CONFIRMED). [EVIDENCE] La création d'un dépôt déclenche `property.setStatus(PropertyStatus.RESERVED)`. (EV: hlm-backend/.../deposit/service/DepositService.java#create)

**Bien (Property)** : unité immobilière appartenant au portefeuille d'un promoteur. Cinq types sont supportés : VILLA, DUPLEX, APPARTEMENT, LOT, TERRAIN_VIERGE. [EVIDENCE] (EV: hlm-backend/.../property/domain/PropertyType.java)

**Projet** : [OPEN] Une table `project` existe dans le schéma de base de données (mentionnée dans docs/database.md) mais aucune API ni logique métier n'est associée dans le code actuel. Quel est le rôle prévu du concept de « projet » dans CRM-HLM ?

### 6.2 Contraintes de stock et anti-double réservation

**Règle 1 — Unicité de la réservation active par bien :**
Un bien ne peut faire l'objet que d'un seul acompte actif (PENDING ou CONFIRMED) à un moment donné. Toute tentative de création d'un second acompte sur le même bien génère une erreur 409 (`PROPERTY_ALREADY_RESERVED`). [EVIDENCE] (EV: hlm-backend/.../deposit/service/DepositService.java#create, ErrorCode.java)

**Règle 2 — Seuls les biens ACTIVE acceptent un acompte :**
La création d'un acompte n'est possible que sur un bien au statut `ACTIVE`. Un bien DRAFT, RESERVED, SOLD, WITHDRAWN ou ARCHIVED est refusé (erreur 409). [EVIDENCE] Le code vérifie `property.getStatus() != PropertyStatus.ACTIVE` avant d'autoriser le dépôt. (EV: hlm-backend/.../deposit/service/DepositService.java#create)

**Règle 3 — Verrouillage pessimiste :**
Le chargement du bien lors de la création d'un acompte utilise un `SELECT ... FOR UPDATE` (PESSIMISTIC_WRITE) pour garantir la cohérence en cas d'accès concurrent. [EVIDENCE] (EV: hlm-backend/.../property/repo/PropertyRepository.java#findByTenantIdAndIdForUpdate)

**Règle 4 — Unicité (tenant, contact, bien) :**
Un contact ne peut avoir qu'un seul acompte actif par bien dans un même tenant. [EVIDENCE] Le repository vérifie `existsByTenant_IdAndContact_IdAndPropertyId` avant la création. (EV: hlm-backend/.../deposit/service/DepositService.java#create)

**Règle 5 — Libération automatique du bien :**
Lorsqu'un acompte est annulé ou expire, le bien associé repasse de `RESERVED` à `ACTIVE`. [EVIDENCE] La méthode `releasePropertyReservation()` est appelée dans `cancel()` et `expireDeposit()`. (EV: hlm-backend/.../deposit/service/DepositService.java#releasePropertyReservation)

**Règle 6 — Index partiel de sécurité :**
Un index unique partiel `ux_deposit_active_reservation_per_property` garantit au niveau base de données qu'un seul acompte actif (PENDING ou CONFIRMED) peut exister par bien et tenant. [EVIDENCE] (EV: hlm-backend/...db/changelog/changes/ — index partiel)

### 6.3 Politique d'annulation/expiration et responsabilités

**Expiration automatique :**
Un scheduler horaire (`runHourlyWorkflow`) identifie les acomptes PENDING dont la date d'échéance est dépassée et les passe en statut EXPIRED. Le bien est libéré et le contact revient au type PROSPECT (sauf s'il est déjà CLIENT). [EVIDENCE] (EV: hlm-backend/.../deposit/service/DepositService.java#expireDeposit)

**Annulation manuelle :**
Un acompte PENDING ou CONFIRMED peut être annulé via `POST /api/deposits/{id}/cancel`. Le bien est libéré et le contact revient au type PROSPECT (sauf s'il est déjà CLIENT). [EVIDENCE] (EV: docs/api.md#POST-/api/deposits/{id}/cancel)

**Notification des parties :**
Chaque changement de statut d'un acompte (création, confirmation, annulation, expiration, échéance proche) génère une notification in-app pour l'agent concerné. [EVIDENCE] (EV: hlm-backend/.../notification/domain/NotificationType.java)

[OPEN] Les questions suivantes restent ouvertes :
- Quel est le délai d'échéance par défaut (7 jours ?) et est-il configurable par tenant ?
- Qui est habilité à annuler un acompte confirmé ?
- Quelles sont les conséquences financières d'une annulation après confirmation ?
- Le prospect est-il notifié de l'expiration de son acompte ?

---

[PAGE BREAK]

## 7. Indicateurs de succès (KPIs) & reporting

### 7.1 KPIs opérationnels (conversion, délais, expirations, stock)

Le système fournit les éléments de reporting suivants :

**Dashboard propriétés :**
L'endpoint `GET /dashboard/properties/summary` agrège les statistiques du stock par tenant, avec filtrage temporel (`from`, `to`, `preset`). [EVIDENCE] (EV: docs/api.md#Property-Dashboard)

[OPEN] Quelles métriques exactes sont retournées par le `PropertySummaryDTO` ? (nombre par statut, évolution, tendances ?)

**Rapport des acomptes :**
L'endpoint `GET /api/deposits/report` fournit :
- Liste des acomptes avec filtres (statut, agent, contact, bien, période).
- Agrégation par agent (nombre d'acomptes, montant total par agent).
[EVIDENCE] (EV: docs/api.md#GET-/api/deposits/report, hlm-backend/.../deposit/service/DepositService.java#report)

**KPIs dérivables :**

| KPI | Source | Disponibilité |
|---|---|---|
| Taux de conversion prospect → client | Contact (status changes) | [INFERENCE] Dérivable des changements de ContactType. Justification : le passage PROSPECT → TEMP_CLIENT → CLIENT est traçable. |
| Nombre de biens réservés / actifs / vendus | Dashboard propriétés | [EVIDENCE] (EV: PropertySummaryDTO) |
| Délai moyen entre acompte et confirmation | Deposit (createdAt vs confirmedAt) | [INFERENCE] Calculable à partir des timestamps. |
| Taux d'expiration des acomptes | Deposit (EXPIRED / total) | [INFERENCE] Calculable à partir du rapport dépôts. |
| Montant total des acomptes par agent | Deposit report | [EVIDENCE] (EV: DepositReportByAgent) |

### 7.2 KPIs business (adoption, rétention, satisfaction)

[OPEN] Aucun KPI business n'est défini dans les sources :
- Quel est le nombre de tenants actifs cible à 6 mois, 12 mois ?
- Quel taux de rétention mensuel est visé ?
- Comment sera mesurée la satisfaction utilisateur (NPS, CSAT) ?
- Quel est le revenu moyen par tenant (ARPU) ciblé ?

### 7.3 Cadence de reporting (hebdo/mensuel)

[OPEN] Aucune cadence de reporting n'est définie dans les sources :
- À quelle fréquence les managers doivent-ils consulter le dashboard ?
- Des rapports automatiques (email récapitulatif hebdomadaire) sont-ils prévus ?
- Quelle est la rétention des données historiques pour le reporting ?

---

[PAGE BREAK]

## 8. Modèle commercial & packaging

### 8.1 Hypothèses de pricing (2–3 options)

[OPEN] Aucune information sur le pricing n'est présente dans les sources. Les questions suivantes doivent être arbitrées :

- Le modèle est-il freemium, par abonnement, par utilisateur, par bien, ou par transaction ?
- Quelle est la fourchette de prix envisagée ?
- Existe-t-il des niveaux de service (Free/Pro/Enterprise) ?

[INFERENCE] Proposition de modèles courants dans le SaaS immobilier B2B (à valider) :

| Option | Modèle | Base tarifaire | Remarque |
|---|---|---|---|
| A | Abonnement par tenant | Mensuel fixe par taille de portefeuille | Prévisibilité des revenus |
| B | Par utilisateur | Mensuel par utilisateur actif | Aligne croissance et revenu |
| C | Hybride | Base fixe + variable par transaction (acompte) | Alignement sur la valeur métier |

Justification : le système multi-tenant avec RBAC supporte naturellement un modèle par tenant avec différenciation par rôle/utilisateur.

### 8.2 Hypothèses d'onboarding et services

[OPEN] Le processus d'onboarding n'est pas documenté :
- Comment un nouveau promoteur s'inscrit-il (self-service via `POST /tenants` ou accompagnement commercial) ?
- Quelle formation est prévue pour les utilisateurs ?
- Existe-t-il un service d'import de données existantes (biens, contacts) ?

[EVIDENCE] L'endpoint `POST /tenants` (public) permet la création d'un tenant avec son utilisateur propriétaire. (EV: docs/api.md#POST-/tenants)

[INFERENCE] Le mode actuel est probablement en self-service technique (API uniquement), ce qui nécessitera une interface d'inscription pour le lancement commercial. Justification : l'endpoint est public et ne requiert aucune authentification préalable.

### 8.3 Hypothèses Go-to-market (canaux)

[OPEN] Aucune stratégie go-to-market n'est définie dans les sources :
- Quel marché géographique est ciblé en priorité (Maroc, Afrique du Nord, francophonie) ?
- Quels canaux d'acquisition sont envisagés (direct, partenariats, salons BTP) ?
- Existe-t-il des prospects ou clients pilotes identifiés ?
- Quelle équipe commerciale est prévue ?

---

[PAGE BREAK]

## 9. Risques & plan de mitigation

### 9.1 Risques business (marché, adoption, légal)

| # | Risque | Probabilité | Impact | Catégorie | Statut |
|---|---|---|---|---|---|
| B1 | Absence de validation marché : aucune donnée sur les promoteurs cibles | TBD [OPEN] | Élevé | Marché | [OPEN] |
| B2 | Cadre légal non clarifié (RGPD, droit immobilier) | TBD [OPEN] | Élevé | Légal | [OPEN] |
| B3 | Absence de pricing validé | TBD [OPEN] | Moyen | Commercial | [OPEN] |
| B4 | Dépendance à un seul marché/géographie | TBD [OPEN] | Moyen | Marché | [OPEN] |
| B5 | Pas de mécanisme de gestion des utilisateurs en self-service | Élevé | Moyen | Adoption | [INFERENCE] L'absence d'API user management (au-delà du seed) freine l'onboarding. |

### 9.2 Risques delivery (frontend, qualité, ops)

| # | Risque | Probabilité | Impact | Catégorie | Statut |
|---|---|---|---|---|---|
| D1 | Tests d'intégration pré-existants en échec (AuthMeIT, ContactControllerIT, TenantControllerIT) | Confirmé | Moyen | Qualité | [EVIDENCE] (EV: hlm-backend/target/failsafe-reports/) |
| D2 | Frontend minimaliste (pas de formulaire de création de bien, contacts en lecture seule) | Élevé | Élevé | UX | [INFERENCE] Les pages frontend n'exposent que la consultation ; les opérations d'écriture nécessitent l'API directement. |
| D3 | Architecture monolithique : couplage backend/frontend | Faible | Moyen | Scalabilité | [INFERENCE] Le monolithe est adapté au MVP mais limitera le scaling à terme. |
| D4 | Verrouillage pessimiste sur PostgreSQL (contention sous charge) | Faible (MVP) | Moyen | Performance | [INFERENCE] Le `PESSIMISTIC_WRITE` est suffisant pour la charge MVP mais pourrait poser problème à l'échelle. |
| D5 | Absence de monitoring et alerting | TBD [OPEN] | Élevé | Ops | [OPEN] Quel outil de monitoring est prévu en production ? |
| D6 | Notifications uniquement in-app (pas d'email/SMS) | Élevé | Moyen | UX | [EVIDENCE] Seul le type Notification in-app est implémenté. (EV: NotificationType.java) |

### 9.3 Plan de réduction du risque

| Priorité | Risque | Action de mitigation |
|---|---|---|
| P0 | D1 — Tests en échec | Corriger les tests AuthMeIT, ContactControllerIT, TenantControllerIT avant merge en production. |
| P0 | B2 — Cadre légal | Consulter un juriste spécialisé en droit immobilier et RGPD pour le marché cible. |
| P1 | D2 — Frontend minimaliste | Développer les formulaires de création de biens et contacts dans l'interface Angular. |
| P1 | B5 — User management | Implémenter une API de gestion des utilisateurs (invitation, modification de rôle). |
| P1 | D6 — Notifications email | Ajouter un canal email pour les notifications critiques (expiration, confirmation). |
| P2 | B1 — Validation marché | Mener des entretiens avec 5-10 promoteurs cibles avant le lancement commercial. |
| P2 | B3 — Pricing | Tester 2-3 modèles tarifaires avec les prospects pilotes. |
| P2 | D4 — Contention locks | Surveiller les métriques de latence en production ; envisager le verrouillage optimiste si nécessaire. |

---

[PAGE BREAK]

## 10. Roadmap business

[OPEN] Aucune roadmap business n'est explicitement définie dans les sources. La proposition ci-dessous est entièrement [INFERENCE], basée sur l'état actuel du produit et les lacunes identifiées.

[INFERENCE] Proposition de roadmap en phases :

**Phase 1 — Stabilisation MVP (court terme)**

- Correction des tests d'intégration défaillants (D1).
- Complétion du frontend : formulaires de création de biens, contacts, recherche avancée (D2).
- API de gestion des utilisateurs (invitation, rôles) (B5).
- Clarification du cadre légal (RGPD, acomptes) (B2).

Justification : ces éléments sont des prérequis à tout déploiement auprès de clients réels.

**Phase 2 — Lancement pilote (moyen terme)**

- Notifications email pour les événements critiques (D6).
- Onboarding guidé (interface d'inscription, parcours d'introduction).
- Tests avec 3-5 promoteurs pilotes, collecte de feedback.
- Validation du modèle de pricing (B3).

Justification : le retour utilisateur est nécessaire avant le scaling commercial.

**Phase 3 — Croissance (long terme)**

- Import/export de données (CSV, Excel).
- Gestion documentaire (contrats, médias).
- Dashboard avancé et reporting personnalisé.
- Application mobile.
- Intégrations tierces (comptabilité, signature électronique).

[OPEN] Quels sont les objectifs temporels pour chaque phase (T+3 mois, T+6 mois, T+12 mois) ?

---

[PAGE BREAK]

## 11. Annexes

### Annexe A — Glossaire

| Terme | Définition | Synonymes interdits |
|---|---|---|
| **Tenant** | Entité organisationnelle (promoteur immobilier) disposant d'un espace de données isolé dans CRM-HLM. Identifié par un UUID (`tenant_id`) et une clé unique (`tenantKey`). | Client SaaS, Organisation |
| **Bien (Property)** | Unité immobilière du portefeuille d'un tenant. Types : VILLA, DUPLEX, APPARTEMENT, LOT, TERRAIN_VIERGE. | Produit, Article, Logement |
| **Prospect** | Contact de type PROSPECT, sans engagement financier. | Lead, Opportunité |
| **Client temporaire (TEMP_CLIENT)** | Prospect ayant versé un acompte non encore confirmé. Durée limitée à la date d'échéance du dépôt. | Pré-client |
| **Client** | Contact dont l'acompte a été confirmé. Type irréversible. | Acheteur |
| **Acompte (Deposit)** | Engagement financier d'un prospect sur un bien. Statuts : PENDING, CONFIRMED, CANCELLED, EXPIRED. | Versement, Arrhes, Caution |
| **Réservation** | État d'un bien (`RESERVED`) résultant de la création d'un acompte actif. | Blocage, Option |
| **Contact** | Entité regroupant prospects et clients. Dispose d'un type (`ContactType`) et d'un statut workflow (`ContactStatus`). | Personne, Individu |
| **Intérêt (ContactInterest)** | Association entre un contact et un bien, indiquant un intérêt commercial. | Favori, Souhait |
| **Notification** | Message in-app adressé à un utilisateur suite à un événement métier (dépôt). | Alerte, Message |
| **Dashboard** | Vue agrégée des statistiques du portefeuille d'un tenant. | Tableau de bord |
| **RBAC** | Contrôle d'accès basé sur les rôles (ADMIN, MANAGER, AGENT). | Permissions, Droits |

### Annexe B — Questions ouvertes / Informations manquantes

**Marché & stratégie :**
1. Quels segments de promoteurs (taille, géographie) sont visés en priorité ?
2. Quelle est la taille du marché adressable (TAM/SAM/SOM) ?
3. Quels canaux d'acquisition sont envisagés ?
4. Existe-t-il des concurrents directs identifiés ?

**Légal & conformité :**
5. Quelle conformité RGPD est requise ?
6. Quel cadre légal régit les acomptes de réservation immobilière au Maroc ?
7. Des CGU/CGV sont-elles nécessaires ?

**Produit & fonctionnalités :**
8. Quel est le rôle prévu du concept « projet » (table existante sans API) ?
9. Le délai d'échéance des acomptes (7 jours par défaut) est-il configurable par tenant ?
10. Des médias (photos, plans) seront-ils associés aux biens ?
11. Un mécanisme d'import/export de données est-il prévu ?
12. L'internationalisation (i18n) est-elle prévue ?

**Commercial & pricing :**
13. Quel modèle de pricing est envisagé ?
14. Quels niveaux de service sont prévus ?
15. Quel est le processus d'onboarding cible (self-service vs accompagné) ?

**Opérations & infrastructure :**
16. Quelle infrastructure d'hébergement est prévue ?
17. Quel outil de monitoring est prévu en production ?
18. Quel est le SLA cible de disponibilité ?
19. Quelle politique de sauvegarde et restauration est prévue ?

**Gouvernance :**
20. Qui est responsable de la validation finale des réservations ?
21. Qui autorise l'annulation d'un acompte confirmé ?
22. Comment sont gérés les conflits entre agents sur un même prospect ?

### Annexe C — Hypothèses

| # | Hypothèse | Justification |
|---|---|---|
| H1 | Le marché cible principal est la promotion immobilière au Maroc. | La devise par défaut est MAD (Dirham marocain) et les champs ICE/SIRET dans `ClientDetail` sont typiques du contexte marocain/francophone. (EV: Property.java#currency, ClientDetail.java#ice) |
| H2 | Le MVP est destiné à un nombre limité de tenants (< 50). | L'architecture monolithique avec verrouillage pessimiste est adaptée à une charge modérée. |
| H3 | Les agents commerciaux constituent la majorité des utilisateurs quotidiens. | Le rôle AGENT est le rôle par défaut pour les nouveaux utilisateurs, suggérant un ratio agents/managers élevé. (EV: UserRole.java) |
| H4 | La double réservation est le pain point critique #1 des promoteurs. | L'investissement significatif dans le mécanisme anti-double réservation (lock pessimiste, index partiel, error codes dédiés) indique une priorité métier forte. |
| H5 | Le délai d'échéance par défaut des acomptes est de 7 jours. | Le code utilise `now.plusDays(7)` comme `dueDate` par défaut. (EV: DepositService.java#create) |

### Annexe D — Incohérences à arbitrer

| # | Sujet | Version A | Version B | Recommandation |
|---|---|---|---|---|
| I1 | Rôle AGENT et dépôts | Le rôle AGENT est défini comme « read-only » dans `UserRole.java` | L'endpoint `POST /api/deposits` ne semble pas restreint par rôle (pas de `@PreAuthorize` visible dans la documentation) | Clarifier : les agents peuvent-ils créer des acomptes ? Vérifier les annotations `@PreAuthorize` sur `DepositController`. |
| I2 | Table `project` | La table `project` est mentionnée dans la documentation database (EV: docs/database.md) | Aucune API, service ou entité `Project` n'est visible dans le code source | Déterminer si « project » est une fonctionnalité prévue ou un artéfact obsolète. |
| I3 | Langue de l'interface | Le frontend mélange anglais (labels, boutons) et français (« Réservation / Acompte ») | Aucune stratégie i18n définie | Choisir une langue principale et appliquer de manière cohérente. |

### Annexe E — Traceability Business

| Objectif métier | Module/Workflow | Données clés | KPIs | Risques |
|---|---|---|---|---|
| Éliminer la double réservation | Property locking (ACTIVE → RESERVED) + Deposit uniqueness | PropertyStatus, DepositStatus, ux_deposit_active_reservation_per_property | Nombre de conflits 409 / mois | D4 (contention locks) |
| Centraliser le suivi prospect | Contact + Interest + ProspectDetail | ContactType, ContactStatus, ContactInterest | Taux conversion prospect → client | B1 (adoption) |
| Automatiser le cycle de vie des acomptes | Deposit lifecycle + Scheduler + Notifications | Deposit (PENDING → CONFIRMED/CANCELLED/EXPIRED), NotificationType | Délai moyen confirmation, taux expiration | D6 (notifications email manquantes) |
| Garantir l'isolation des données | Multi-tenant (JWT `tid`, TenantContext, tenant_id FK) | tenant_id sur toutes les tables | 0 fuite inter-tenant | — |
| Piloter l'activité commerciale | Dashboard + Deposit report | PropertySummaryDTO, DepositReportResponse, DepositReportByAgent | Stock par statut, montant par agent | D5 (absence de monitoring) |
| Convertir les prospects en clients | Convert-to-client workflow + ClientDetail | ContactType transition, ClientDetail creation | Nombre de conversions / mois | B5 (user management limité) |
| Tracer les actions des agents | Property (created_by, updated_by), Notification payload | UUID acteur, timestamps | Activité par agent | Audit log incomplet (§5.2) |

---

*Fin du document.*

*Ce document a été généré sur la base exclusive des sources présentes dans le dépôt CRM-HLM (code, documentation, discussions). Toute affirmation non tagguée [EVIDENCE] est identifiée comme [INFERENCE] ou [OPEN]. Les informations marquées [OPEN] nécessitent des décisions ou investigations complémentaires avant le lancement commercial.*
