# Référentiel des Règles de Gestion — YEM HLM

> Document dérivé du **code source réel** le **14/06/2026**.
> Chaque règle est ancrée dans un fichier et une méthode. Les règles non
> implémentées sont signalées comme telles. Les améliorations proposées par
> l'équipe sont regroupées en fin de document, clairement séparées des règles
> existantes.
>
> *Remplace la version concise du 03/06/2026 (qui citait encore les anciens états
> `ACTE_NOTARIE`/`LIVRE` d'avant Wave 12). Voir aussi
> `docs/spec/business-rules-audit.md` pour l'inventaire métier détaillé.*

---

## Synthèse

| Indicateur | Nombre |
|---|---|
| Règles implémentées et vérifiées (✅) | 71 |
| Règles partiellement implémentées (⚠️) | 3 |
| Règles documentées mais absentes du code (❌) | 0 |
| Fonctionnalités suggérées | 13 |
| Règles à fondement légal (⚖️) | 21 |
| Règles ✅ sans test automatisé identifié | ~40 ← *signal de risque* |

*Avertissement Nadia (juriste) : les valeurs légales (5 %, 7 jours, taux de TVA,
durées de rétention) sont celles codées dans l'application. Elles correspondent à
la Loi 44-00 et au CGI marocains à la date de rédaction, mais **doivent être
revérifiées auprès des textes officiels en vigueur** avant tout usage contractuel.*

---

## Comment lire ce document

- Chaque règle a un **identifiant stable** (ex. `RG-B05`) que l'on peut citer en
  réunion, en ticket ou en revue de code.
- Le champ **Règle (langage métier)** est écrit pour un agent commercial : aucun
  jargon technique. Le jargon (noms de classes, méthodes) est réservé au champ
  **Implémentation**, pour qu'un développeur retrouve le code en 10 secondes.
- Le champ **Statut** dit honnêtement ce que fait le code aujourd'hui, pas ce
  qu'on aimerait qu'il fasse.
- Le champ **Base légale** rattache la règle à un article quand il existe.

## Légende

| Symbole | Signification |
|---|---|
| ✅ | Implémentée et vérifiée dans le code |
| ⚠️ | Partiellement implémentée (existe mais incomplète ou non câblée partout) |
| ❌ | Documentée ailleurs mais absente du code |
| ⚖️ | Règle à fondement légal (Loi 44-00, Loi 09-08, CGI) |

## Sommaire

| Catégorie | Domaine | Règles |
|---|---|---|
| **RG-A** | Sécurité & Isolation Tenant | 9 |
| **RG-B** | Pipeline Commercial (Vente) | 17 |
| **RG-C** | Portail Acquéreur | 5 |
| **RG-D** | Hiérarchie Projet (Projet→Tranche→Immeuble→Bien) | 7 |
| **RG-E** | Module 3D (Viewer + Uploader) | 6 |
| **RG-F** | Reporting & KPI | 6 |
| **RG-G** | Finance & Échéancier VEFA | 14 |
| **RG-H** | UX/UI & Conformité transverse | 13 |

---

## RG-A · Sécurité & Isolation Tenant

### RG-A01 · Toute donnée appartient à une seule société
**Statut :** ✅
**Catégorie :** Sécurité
**Base légale :** —

**Règle (langage métier) :**
Chaque enregistrement (bien, contact, vente…) appartient à une société et une
seule. Avant toute lecture ou écriture, le système exige de savoir « pour quelle
société » agit l'utilisateur. Sans cette information, l'opération est refusée.

**Implémentation :**
`SocieteContextHelper.requireSocieteId()` → lève `CrossSocieteAccessException`.
Appelé en première ligne de quasiment tous les services (ex.
`VenteService.create():129`, `ReservationService.create():94`). ~280 sites.

**Vérifiée par test :** `CrossSocieteIsolationIT`, `VenteControllerIT.get_crossSocieteVente_returns404`.

**Exemple concret :**
Un agent connecté pour la société A ouvre une vente de la société B : le système
répond **404 Introuvable** (et non 403, pour ne révéler aucune existence).

---

### RG-A02 · Double barrière d'isolation (service + base de données)
**Statut :** ✅
**Catégorie :** Sécurité
**Base légale :** —

**Règle (langage métier) :**
L'isolation entre sociétés est garantie à deux niveaux : dans le code métier, et
directement dans la base de données. Même un oubli dans le code serait rattrapé
par la base.

**Implémentation :**
Niveau 1 : filtre `WHERE societe_id = ?` dans toutes les requêtes
(`findBySocieteIdAnd...`). Niveau 2 : PostgreSQL Row-Level Security activé sur
toutes les tables métier (changesets `050`/`051`), positionné par
`RlsContextAspect.setSocieteIdOnConnection():66`.

**Vérifiée par test :** RLS phase 2 (changeset `051`) ; tests d'intégration.

**Exemple concret :**
Une requête SQL qui oublierait le filtre société ne retournerait quand même que
les lignes de la société active, grâce à la politique RLS.

---

### RG-A03 · Contexte « système » pour les tâches planifiées
**Statut :** ✅
**Catégorie :** Sécurité
**Base légale :** —

**Règle (langage métier) :**
Les traitements automatiques (expiration d'options, relances) tournent sans
utilisateur connecté. Ils utilisent un contexte « système » spécial qui contourne
le filtre société de façon contrôlée.

**Implémentation :**
`SocieteContext.setSystem():44` + sentinelle nil-UUID
`00000000-0000-0000-0000-000000000000` qui bypasse la RLS
(`RlsContextAspect.NIL_UUID:47`). Les sweeps balayent toutes les sociétés
(ex. `DataRetentionScheduler.runRetention()`).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Le balayage horaire des options expirées traite les ventes de toutes les sociétés,
même si personne n'est connecté.

---

### RG-A04 · Trois rôles CRM hiérarchisés
**Statut :** ✅
**Catégorie :** Sécurité
**Base légale :** —

**Règle (langage métier) :**
Trois rôles existent dans le CRM : **Administrateur** (tout faire), **Manager**
(créer/modifier, pas supprimer), **Agent** (consultation et actions commerciales
courantes). Un quatrième rôle, **Super-Admin**, gère la plateforme au-dessus des
sociétés.

**Implémentation :**
`UserRole` = `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`. Contrôle par
`@PreAuthorize("hasAnyRole(...)")` sur les contrôleurs et
`SecurityConfig.securityFilterChain()`.

**Vérifiée par test :** `PropertyControllerIT` (23 tests RBAC).

**Exemple concret :**
Un Agent qui tente de supprimer un bien reçoit **403 Accès refusé** ; un
Administrateur réussit.

---

### RG-A05 · Cloisonnement des espaces par rôle
**Statut :** ✅
**Catégorie :** Sécurité
**Base légale :** —

**Règle (langage métier) :**
Chaque famille d'URL est réservée à un type d'utilisateur : le portail acquéreur
n'accède pas au CRM, et le CRM n'accède pas aux URL réservées au super-admin.

**Implémentation :**
`SecurityConfig.java:107-129` : `/api/admin/**` → `SUPER_ADMIN` ;
`/api/portal/**` → `PORTAL` ; `/api/**` → `ADMIN/MANAGER/AGENT`.

**Vérifiée par test :** tests de sécurité par chaîne de filtres.

**Exemple concret :**
Un acquéreur (rôle Portail) qui appelle l'URL CRM `/api/ventes` est bloqué : le
portail n'a accès qu'à `/api/portal/**`.

---

### RG-A06 · Authentification par cookie httpOnly (jamais de jeton en clair)
**Statut :** ✅
**Catégorie :** Sécurité
**Base légale :** —

**Règle (langage métier) :**
Le jeton de connexion est stocké dans un cookie sécurisé inaccessible au
JavaScript de la page, ce qui protège contre le vol de session. Aucun jeton n'est
conservé dans le stockage local du navigateur.

**Implémentation :**
`JwtAuthenticationFilter` lit le cookie ; `CookieTokenHelper` /
`PortalCookieHelper`. Session sans état (`SessionCreationPolicy.STATELESS`,
`SecurityConfig.java:89`).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Même si un script tiers s'exécutait dans la page, il ne pourrait pas lire le
jeton de l'utilisateur connecté.

---

### RG-A07 · En-têtes de sécurité HTTP durcis
**Statut :** ✅
**Catégorie :** Sécurité
**Base légale :** —

**Règle (langage métier) :**
L'application interdit d'être affichée dans un cadre (anti-« clickjacking »),
restreint les sources de contenu autorisées et désactive les capteurs inutiles
(caméra, micro, géolocalisation).

**Implémentation :**
`SecurityConfig.java:45-88` : Content-Security-Policy, `frame-ancestors 'none'`,
`X-Frame-Options: DENY`, Permissions-Policy, HSTS (si TLS actif).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Un site malveillant ne peut pas intégrer YEM HLM dans une iframe pour piéger un
utilisateur.

---

### RG-A08 · Quotas par société (biens, contacts, projets, utilisateurs)
**Statut :** ✅
**Catégorie :** Sécurité / Commercial (SaaS)
**Base légale :** —

**Règle (langage métier) :**
Chaque société a des plafonds (nombre de biens, contacts, projets, utilisateurs)
selon son abonnement. Toute création au-delà du plafond est refusée.

**Implémentation :**
`QuotaService.enforceBienQuota/enforceContactQuota/enforceProjectQuota/enforceUserQuota`.
Câblé dans `PropertyService`, `ContactService`, `ProjectService`,
`ProjectGenerationService:82-83`, `InvitationService`. Codes `QUOTA_*_ATTEINT` (409).

**Vérifiée par test :** `QuotaServiceTest` (8 tests).

**Exemple concret :**
Une société limitée à 100 biens qui tente de générer un projet de 120 lots est
bloquée avec **409 QUOTA_BIENS_ATTEINT**.

---

### RG-A09 · Limitation du débit des invitations
**Statut :** ✅
**Catégorie :** Sécurité
**Base légale :** —

**Règle (langage métier) :**
Un administrateur ne peut pas envoyer plus de 10 invitations par heure, pour
éviter les envois en masse et les abus.

**Implémentation :**
`RateLimiterService.checkInvitation(adminId)` (10/h, clé `invitation:{adminId}`),
appelé dans `UserManagementController.inviter()`.

**Vérifiée par test :** `LoginRateLimitIT` (rate-limit auth).

**Exemple concret :**
Au 11ᵉ envoi d'invitation dans l'heure, l'admin reçoit un refus pour dépassement.

---

## RG-B · Pipeline Commercial (Vente)

> La **Vente** suit une machine à états VEFA (Loi 44-00). Vocabulaire unique :
> on dit toujours « Vente » pour l'objet, « bien » pour le lot immobilier, et on
> nomme chaque état par son code (`OPTION`, `RESERVE`, etc.).

### RG-B01 · Cycle de vie d'une Vente (11 étapes + annulation)
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** ⚖️ Loi 44-00 (VEFA Maroc)

**Règle (langage métier) :**
Une Vente avance par étapes ordonnées :
`PROSPECT → OPTION → RÉSERVATION → PÉRIODE DE RÉTRACTATION → ACOMPTE →
COMPROMIS → FINANCEMENT → ACTE → LIVRAISON AVEC RÉSERVES → RÉSERVES LEVÉES →
LIVRAISON DÉFINITIVE`. L'état `ANNULÉ` met fin à la vente à tout moment.

**Implémentation :**
`VenteStatut` (12 valeurs) ; matrice `VenteService.validateTransition():1001-1019`.

**Vérifiée par test :** `VenteServiceTest` (machine à états).

**Exemple concret :**
Depuis `COMPROMIS`, on ne peut aller que vers `FINANCEMENT` ou `ANNULÉ` — passer
directement à `ACTE` est refusé avec **409 INVALID_STATUS_TRANSITION**.

---

### RG-B02 · Les transitions interdites sont rejetées
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** —

**Règle (langage métier) :**
On ne peut pas sauter d'étape ni revenir en arrière hors des chemins autorisés.
Une fois `LIVRAISON DÉFINITIVE` ou `ANNULÉ` atteint, la vente est close (états
terminaux).

**Implémentation :**
`VenteService.validateTransition()` ; états terminaux
`LIVRE_DEFINITIF, ANNULE -> Set.of()` (`:1014`). Transition identique = no-op
idempotent (`:1002`).

**Vérifiée par test :** `VenteServiceTest`.

**Exemple concret :**
Tenter de rouvrir une vente `ANNULÉ` vers `COMPROMIS` est refusé.

---

### RG-B03 · Un seul engagement actif par bien
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** —

**Règle (langage métier) :**
Un même bien ne peut être engagé que dans une seule vente active à la fois. Tant
qu'une vente n'est pas annulée, aucune autre vente ne peut viser ce bien.

**Implémentation :**
Garde `existsBySocieteIdAndPropertyIdAndStatutNot(..., ANNULE)`
(`VenteService.create():220`, `createOption():283`) → `PropertyAlreadyEngagedException`
(409). Filet anti-concurrence : index unique partiel `uk_vente_active_property`
(changeset `075`).

**Vérifiée par test :** `VenteControllerIT.create_secondVenteOnSameProperty_returns409`.

**Exemple concret :**
Deux agents créent une vente sur le même appartement en même temps : le second
reçoit **409 PROPERTY_ALREADY_ENGAGED**.

---

### RG-B04 · Le bien doit être disponible (ou déjà réservé) pour démarrer une vente
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** —

**Règle (langage métier) :**
On ne crée une vente que sur un bien `DISPONIBLE` ou déjà `RÉSERVÉ`. Si le bien
est disponible, il passe automatiquement en réservé. Un bien vendu ou retiré ne
peut pas démarrer une nouvelle vente.

**Implémentation :**
`VenteService.create():229-236` (ACTIVE → `propertyWorkflow.reserve()`, sinon
exige RESERVED) → `InvalidPropertyStatusTransitionException`.

**Vérifiée par test :** `VenteServiceTest` (précondition statut bien).

**Exemple concret :**
Créer une vente sur un bien au statut `VENDU` est refusé.

---

### RG-B05 · Le statut du bien suit l'avancement de la vente
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** —

**Règle (langage métier) :**
Le statut commercial du bien découle de la vente : il devient **VENDU** au moment
de l'acte notarié (`ACTE`), et **revient à DISPONIBLE** si la vente est annulée.

**Implémentation :**
`VenteService.applyStatutChange():637-653` : `ACTE → propertyWorkflow.sell()` ;
`ANNULE → releaseReservation()` ou `cancelSaleToAvailable()`. Source unique :
`PropertyCommercialWorkflowService`.

**Vérifiée par test :** `VenteServiceTest` (ANNULE libère le bien).

**Exemple concret :**
Quand une vente passe à `ACTE`, l'appartement bascule de `RÉSERVÉ` à `VENDU` sans
action manuelle.

---

### RG-B06 · Création d'une option (blocage temporaire 1 à 72 h)
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** ⚖️ Pratique VEFA / pré-réservation

**Règle (langage métier) :**
Un agent peut poser une **option** sur un bien disponible : un blocage temporaire
de 1 à 72 heures. Une durée hors borne est ramenée dans la borne. Le bien passe
en réservé pendant l'option.

**Implémentation :**
`VenteService.createOption():272-309` — `duree = min(max(dureeHeures,1),72)`,
`optionExpireAt = now + duree`. Contact promu `QUALIFIED_PROSPECT`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️ (couvert en IT VEFA, CI).

**Exemple concret :**
Un agent pose une option de 48 h sur un lot ; le lot apparaît réservé pour les
autres agents.

---

### RG-B07 · Expiration automatique des options
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** —

**Règle (langage métier) :**
Une option non confirmée à l'échéance est automatiquement annulée et le bien
redevient disponible. L'agent est notifié.

**Implémentation :**
`VenteService.expireOverdueOptions():384-396` (balayage horaire via
`VenteVefaScheduler`) → `ANNULE`, motif `AUTRE`, libération du bien, notification
`OPTION_EXPIRED`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Une option de 24 h posée hier est automatiquement levée ce matin ; le lot
réapparaît disponible au catalogue.

---

### RG-B08 · Plafond légal du dépôt de garantie : 5 %
**Statut :** ✅
**Catégorie :** Pipeline / Finance
**Base légale :** ⚖️ Art. 618-4 Loi 44-00

**Règle (langage métier) :**
À la confirmation de réservation, le dépôt de garantie versé ne peut **pas
dépasser 5 % du prix** de vente convenu. Au-delà, la confirmation est refusée.

**Implémentation :**
`VenteService.confirmReservation():319-328` : `maxDepot = prix × 0,05`
(`MarketConfig.getDepotGarantieMaxPct()`) → `ViolationLegaleException` si dépassé.

**Vérifiée par test :** `VenteServiceTest` (garde dépôt).

**Exemple concret :**
Pour un bien à 1 000 000 MAD, un dépôt de 80 000 MAD est refusé (max autorisé :
50 000 MAD).

---

### RG-B09 · Ouverture automatique du délai de rétractation
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** ⚖️ Art. 618-3 Loi 44-00 (Maroc = 7 jours ; France = 10 jours)

**Règle (langage métier) :**
Dès la confirmation de réservation, un **délai légal de rétractation de 7 jours**
s'ouvre automatiquement. La vente passe en « période de rétractation » et la date
de fin de délai est calculée par le système.

**Implémentation :**
`VenteService.confirmReservation():336-339` :
`dateFinDelaiReflexion = today + getDelaiRetractationJours()` (7 MA / 10 FR) ;
statut → `EN_RETRACTATION`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Une réservation confirmée le 14/06/2026 ouvre un délai jusqu'au 21/06/2026.

---

### RG-B10 · Exercice de la rétractation dans le délai
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** ⚖️ Art. 618-3 Loi 44-00

**Règle (langage métier) :**
Pendant le délai, l'acquéreur peut se rétracter : la vente est annulée, le bien
libéré, les échéances en attente annulées, et une obligation de remboursement du
dépôt créée. Hors délai, la rétractation est refusée.

**Implémentation :**
`VenteService.exerciseRetractation():349-373` : refus si statut ≠
`EN_RETRACTATION` ou délai dépassé (`RetractationImpossibleException`) ; sinon
`ANNULE` + motif `DESISTEMENT_ACHETEUR` + `cancelAllPendingByVente()` +
`VenteAnnuleeEvent`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Un acquéreur se rétracte au 5ᵉ jour : la vente est annulée et son dépôt de
50 000 MAD devient « dû » au remboursement.

---

### RG-B11 · Clôture automatique du délai de rétractation
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** ⚖️ Art. 618-3 Loi 44-00

**Règle (langage métier) :**
Si l'acquéreur ne se rétracte pas dans le délai, la vente passe automatiquement
de « période de rétractation » à « réservée » : le client est définitivement
engagé.

**Implémentation :**
`VenteService.closeExpiredRetractations():399-410` (balayage planifié) :
`EN_RETRACTATION → RESERVE`, notification `RETRACTATION_DELAI_CLOS`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Le délai expiré sans rétractation, la vente devient « réservée » et l'agent reçoit
une notification.

---

### RG-B12 · Les étapes légales ne peuvent être atteintes par la voie générique
**Statut :** ✅
**Catégorie :** Pipeline / Sécurité métier
**Base légale :** ⚖️ Loi 44-00 (préserve les contrôles légaux)

**Règle (langage métier) :**
Certaines étapes (option, réservation, période de rétractation, livraison avec
réserves, levée de réserves) imposent des contrôles légaux. On **ne peut pas**
les atteindre par le changement de statut générique : il faut passer par l'action
dédiée qui applique ces contrôles.

**Implémentation :**
`VenteService.GUARDED_ENTRY_ENDPOINTS:557-563` ; `updateStatut():570-576` lève
`GuardedStageEntryException` si la cible est gardée. (Corrige la faille EX-001.)

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Forcer `PATCH /statut` vers `RESERVE` est refusé avec un message indiquant
d'utiliser `POST /confirm-reservation` — ce qui réapplique le plafond de 5 % et
le délai de rétractation.

---

### RG-B13 · Le motif est obligatoire pour annuler une vente
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** —

**Règle (langage métier) :**
Toute annulation de vente doit indiquer un motif (crédit refusé, désistement,
litige, accord des parties…). Sans motif, l'annulation est refusée.

**Implémentation :**
`VenteService.applyStatutChange():590-596` exige `motifAnnulation` ;
`MotifAnnulation` (6 valeurs).

**Vérifiée par test :** `VenteServiceTest` (ANNULE-needs-motif).

**Exemple concret :**
Annuler une vente sans motif renvoie une erreur ; choisir « Crédit refusé » la
valide.

---

### RG-B14 · Prix de vente strictement positif
**Statut :** ✅
**Catégorie :** Pipeline / Finance
**Base légale :** —

**Règle (langage métier) :**
Le prix de vente doit être strictement supérieur à zéro. Un prix fourni à zéro ou
négatif est une erreur explicite. Lors d'une conversion depuis réservation, le
prix calculé (prix bien − avance − réduction) ne peut être négatif.

**Implémentation :**
`VenteService.create():159,196` → `PrixVenteInvalideException` (422
`PRIX_VENTE_INVALIDE`) ; calcul borné `:172-177` (règle A-004).

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Saisir un prix de vente de 0 MAD est refusé ; le système exige un montant positif
ou le prix catalogue du bien.

---

### RG-B15 · Le contact progresse automatiquement avec la vente
**Statut :** ✅
**Catégorie :** Pipeline / CRM
**Base légale :** —

**Règle (langage métier) :**
Le statut du contact suit la vente, sans jamais reculer : il devient **client
actif** à la création de la vente, et **client finalisé** à la livraison
définitive. Les contacts « perdus » ou « apporteurs » ne sont jamais modifiés.

**Implémentation :**
`VenteService.advanceContactStatus():938-949` (progression
PROSPECT→…→COMPLETED_CLIENT, jamais de downgrade, ignore `LOST`/`REFERRAL`),
dans la même transaction que la mutation vente. `ContactStatus.canTransitionTo()`
encadre les transitions manuelles.

**Vérifiée par test :** `VenteServiceTest`.

**Exemple concret :**
À la livraison définitive, le contact passe en « client finalisé » et la vente
déclenche le recalcul des KPI.

---

### RG-B16 · Probabilité et date de clôture estimées par étape
**Statut :** ✅
**Catégorie :** Pipeline / Reporting
**Base légale :** —

**Règle (langage métier) :**
Chaque étape porte une probabilité de conclusion par défaut (5 % en prospect à
100 % en livraison) et une estimation du nombre de jours restant. Ces valeurs
alimentent les prévisions commerciales.

**Implémentation :**
`VenteService.defaultProbability():951-966` et `estimatedDaysToClose():968-982`.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Une vente au stade `FINANCEMENT` est affichée à 50 % de probabilité avec ~60
jours estimés avant clôture.

---

### RG-B17 · Cohérence des dates de la vente
**Statut :** ✅
**Catégorie :** Pipeline
**Base légale :** —

**Règle (langage métier) :**
Les dates clés d'une vente doivent être logiques entre elles (par exemple, la
livraison ne peut pas précéder le compromis). Une incohérence est refusée.

**Implémentation :**
`DateCoherenceValidator.validateVenteDates()` (appelé `create():210`),
`validateEcheanceDates()` → `DateCoherenceException`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️ (règle F4).

**Exemple concret :**
Saisir une date de livraison antérieure à la date de compromis est rejeté.

---

## RG-C · Portail Acquéreur

### RG-C01 · L'acquéreur ne voit que ses propres ventes
**Statut :** ✅
**Catégorie :** Portail / Sécurité
**Base légale :** —

**Règle (langage métier) :**
Connecté à son espace, un acquéreur ne voit que les ventes qui le concernent.
Toute tentative d'accès à la vente d'un autre acquéreur renvoie « introuvable »,
sans rien révéler.

**Implémentation :**
`PortalVenteService.requireOwnedVente():93-102` : compare le contactId du jeton
au contact de la vente → 404 si différent. Identité = sujet du jeton portail.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️ (couvert par IT portail).

**Exemple concret :**
Un acquéreur qui modifie l'identifiant de vente dans l'URL pour voir celle d'un
voisin reçoit **404**.

---

### RG-C02 · Téléchargement de document lié à la bonne vente (anti-IDOR)
**Statut :** ✅
**Catégorie :** Portail / Sécurité
**Base légale :** —

**Règle (langage métier) :**
Un acquéreur ne peut télécharger qu'un document **rattaché à sa propre vente**. Un
identifiant de document appartenant à un autre dossier de la société n'est pas
atteignable.

**Implémentation :**
`PortalVenteService.getDocumentKey():71-77` : recherche scopée
`findBySocieteIdAndVente_IdAndId(societeId, venteId, docId)`. *(Correction de la
faille décrite dans le commit `1602fa8`.)*

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Un acquéreur qui devine l'identifiant du contrat d'un autre client et le passe
avec sa propre vente reçoit **404**.

---

### RG-C03 · Vue acquéreur en lecture seule et allégée
**Statut :** ✅
**Catégorie :** Portail
**Base légale :** —

**Règle (langage métier) :**
L'espace acquéreur présente une version simplifiée de la vente (référence, statut,
prix, dates clés, échéances, documents) — pas les données internes commerciales.

**Implémentation :**
`PortalVenteService.toPortalResponse():104-113` construit un `PortalVenteResponse`
réduit (pas de notes internes, pas d'agent, pas de probabilité).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
L'acquéreur voit « Échéance fondations — 100 000 MAD — au 15/09/2026 » mais pas
les annotations internes de l'agent.

---

### RG-C04 · Dépôt de document par l'acquéreur tracé comme « portail »
**Statut :** ✅
**Catégorie :** Portail
**Base légale :** —

**Règle (langage métier) :**
Quand l'acquéreur dépose lui-même un document depuis son espace, celui-ci est
marqué comme provenant du portail (et non d'un agent), pour la traçabilité.

**Implémentation :**
`PortalVenteService.uploadDocument()` → `VenteService.addDocumentFromPortal():874`
(positionne `uploadedByPortal = true`, sans utilisateur CRM).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
L'acquéreur téléverse sa pièce d'identité ; le document apparaît côté agent avec
la mention « déposé via le portail ».

---

### RG-C05 · Connexion acquéreur par lien magique (sans mot de passe)
**Statut :** ✅
**Catégorie :** Portail / Sécurité
**Base légale :** —

**Règle (langage métier) :**
L'acquéreur se connecte via un lien à usage unique envoyé par e-mail, valable un
temps limité — sans mot de passe à gérer.

**Implémentation :**
`PortalAuthService` : lien 32 octets aléatoires, haché en base, TTL 48 h, usage
unique (changeset `025`). Endpoints publics `/api/portal/auth/request-link` +
`/verify`. Jeton portail = cookie httpOnly `hlm_portal_auth`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
L'acquéreur clique « Accéder à mon espace » dans son e-mail et arrive connecté ;
le lien ne fonctionne plus une seconde fois.

---

## RG-D · Hiérarchie Projet (Projet → Tranche → Immeuble → Bien)

### RG-D01 · Hiérarchie à quatre niveaux
**Statut :** ✅
**Catégorie :** Hiérarchie
**Base légale :** —

**Règle (langage métier) :**
Le patrimoine s'organise en quatre niveaux emboîtés : **Projet** → **Tranche**
(phase de livraison) → **Immeuble** → **Bien** (lot vendable).

**Implémentation :**
Entités `Project`, `Tranche`, `Immeuble`, `Property` (FK `tranche_id`,
`immeuble_id`). Changesets `056`/`059`.

**Vérifiée par test :** `ProjectGenerationIT`.

**Exemple concret :**
Le projet « Les Jardins » contient la Tranche 1, qui contient l'Immeuble A, qui
contient l'appartement A-302.

---

### RG-D02 · Génération en masse d'un projet en une seule opération
**Statut :** ✅
**Catégorie :** Hiérarchie
**Base légale :** —

**Règle (langage métier) :**
On peut créer d'un coup un projet complet : ses tranches, ses immeubles, et tous
les lots par étage (parkings compris). L'opération est atomique (tout réussit, ou
rien n'est créé).

**Implémentation :**
`ProjectGenerationService.generate():79` (`@Transactional`) : projet → tranches →
immeubles → unités par étage + parkings. Génère les références de lots.

**Vérifiée par test :** `ProjectGenerationIT` ; `ProjectGenerationServiceTest`.

**Exemple concret :**
Le promoteur déclare « 2 tranches, 3 immeubles, 5 étages de 4 lots » et obtient
les lots créés en une opération.

---

### RG-D03 · Nom de projet unique par société
**Statut :** ✅
**Catégorie :** Hiérarchie
**Base légale :** —

**Règle (langage métier) :**
Deux projets ne peuvent pas porter le même nom dans une même société.

**Implémentation :**
`ProjectGenerationService.generate():87` → `ProjectNameAlreadyExistsException`.

**Vérifiée par test :** `project-wizard.spec.ts` (erreur nom dupliqué).

**Exemple concret :**
Créer un second projet nommé « Les Jardins » dans la même société est refusé.

---

### RG-D04 · Quotas vérifiés avant génération
**Statut :** ✅
**Catégorie :** Hiérarchie / SaaS
**Base légale :** —

**Règle (langage métier) :**
Avant de générer un projet, le système vérifie que la société n'a pas atteint son
plafond de projets ni son plafond de biens.

**Implémentation :**
`ProjectGenerationService.generate():82-83` : `enforceProjectQuota()` +
`enforceBienQuota()`.

**Vérifiée par test :** `QuotaServiceTest`.

**Exemple concret :**
Voir RG-A08 : une génération qui dépasserait le quota de biens est bloquée avant
toute création.

---

### RG-D05 · Cycle de vie de la tranche : avance séquentielle uniquement
**Statut :** ✅
**Catégorie :** Hiérarchie
**Base légale :** —

**Règle (langage métier) :**
Une tranche avance étape par étape, sans saut ni retour :
`EN PRÉPARATION → EN COMMERCIALISATION → EN TRAVAUX → ACHEVÉE → LIVRÉE`.

**Implémentation :**
`TrancheService.validateTransition():112-118` : `toIdx == fromIdx + 1`
strictement, sinon `InvalidTrancheTransitionException` (409). `TrancheStatut`
(5 valeurs).

**Vérifiée par test :** `TrancheServiceTest` (5 tests) ; `ProjectGenerationIT`.

**Exemple concret :**
Passer une tranche directement de « En préparation » à « En travaux » (en sautant
« En commercialisation ») est refusé.

---

### RG-D06 · Catégorie du bien dérivée de son type
**Statut :** ✅
**Catégorie :** Hiérarchie
**Base légale :** —

**Règle (langage métier) :**
Chaque type de bien (appartement, villa, studio, commerce, parking, terrain…) est
automatiquement rattaché à une grande catégorie (logement, commerce, foncier,
parking) pour les regroupements et statistiques.

**Implémentation :**
`PropertyType.category()` mappe les 10 types vers `PropertyCategory`
(APARTMENT, VILLA, COMMERCE, LAND, PARKING).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Un `T3` et un `STUDIO` sont tous deux comptés dans la catégorie « logement
(appartement) ».

---

### RG-D07 · Champs de localisation et professionnels du projet
**Statut :** ✅
**Catégorie :** Hiérarchie
**Base légale :** —

**Règle (langage métier) :**
Un projet porte son adresse (rue, ville, code postal) et des champs
professionnels : maître d'ouvrage, date d'ouverture de commercialisation, taux de
TVA, surface du terrain, prix moyen au m² cible.

**Implémentation :**
Changesets `059` (adresse) et `065` (champs professionnels) sur la table
`project`.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
La fiche projet affiche « Maître d'ouvrage : SARL Promoteur X — Ouverture
commerciale : 01/03/2026 — Prix moyen cible : 12 000 MAD/m² ».

---

## RG-E · Module 3D (Viewer + Uploader)

### RG-E01 · Validation binaire du fichier 3D (GLB + compression Draco)
**Statut :** ✅
**Catégorie :** 3D
**Base légale :** —

**Règle (langage métier) :**
Un modèle 3D téléversé doit être un vrai fichier GLB valide et compressé (Draco).
Le système vérifie le contenu réel du fichier, sans faire confiance à ce que le
client déclare.

**Implémentation :**
`GlbValidator.validate():43` : vérifie la signature `glTF`, la version 2, le chunk
JSON et la présence de `KHR_draco_mesh_compression` → `InvalidGlbException` (422).
Appelé par `Project3dService.upsertModel():77-83` (drapeau
`app.viewer3d.validate-glb-binary`, vrai en prod).

**Vérifiée par test :** `GlbValidatorTest` (7 tests).

**Exemple concret :**
Renommer un PDF en `.glb` et le téléverser est rejeté : la signature glTF est
absente.

---

### RG-E02 · Refus d'un modèle non compressé dès la demande d'upload
**Statut :** ✅
**Catégorie :** 3D
**Base légale :** —

**Règle (langage métier) :**
Dès la première étape (demande d'URL d'upload), un modèle déclaré non compressé
est refusé, pour économiser bande passante et stockage.

**Implémentation :**
`Project3dService.generateUploadUrl():169-171` : si `!dracoCompressed` →
`IllegalArgumentException`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Une demande d'upload d'un modèle non compressé est refusée avant tout transfert.

---

### RG-E03 · Upload direct sécurisé par URL pré-signée
**Statut :** ✅
**Catégorie :** 3D
**Base légale :** —

**Règle (langage métier) :**
Le fichier 3D est envoyé directement au stockage via une URL temporaire et
nominative, sans transiter par le serveur applicatif. La clé de stockage est
isolée par société et projet.

**Implémentation :**
`Project3dService.generateUploadUrl():166-178` :
`fileKey = models/{societeId}/{projetId}/{uuid}.glb`, URL PUT pré-signée
(`MediaStorageService.generatePresignedPutUrl`).

**Vérifiée par test :** `Project3dControllerIT`.

**Exemple concret :**
L'admin reçoit un lien d'upload valable quelques minutes ; le fichier va
directement dans le dossier du projet sur le stockage objet.

---

### RG-E04 · Couleurs des lots dérivées de leur statut commercial
**Statut :** ✅
**Catégorie :** 3D / Reporting
**Base légale :** —

**Règle (langage métier) :**
Dans la vue 3D, chaque lot est coloré selon son statut : disponible, réservé,
vendu, livré, retiré. Le code de couleur est dérivé d'une règle unique.

**Implémentation :**
`Project3dService.toDisplayStatus():249-257` :
`DRAFT/ACTIVE → DISPONIBLE`, `RESERVED → RESERVE`, `SOLD → VENDU`,
`WITHDRAWN → RETIRE`, `ARCHIVED → LIVRE`.

**Vérifiée par test :** `Project3dServiceTest`.

**Exemple concret :**
Un appartement réservé apparaît en orange dans la maquette 3D ; un vendu en vert.

---

### RG-E05 · Accès 3D du portail réservé aux acquéreurs concernés
**Statut :** ✅
**Catégorie :** 3D / Portail / Sécurité
**Base légale :** —

**Règle (langage métier) :**
Un acquéreur ne peut consulter la maquette 3D d'un projet que s'il a au moins une
vente sur un lot de ce projet. Sinon, l'accès est refusé (introuvable).

**Implémentation :**
`Project3dService.portalUserHasAccess():210` ; appliqué par
`PortalProject3dController` → 404 sinon.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️ (couvert par IT portail 3D).

**Exemple concret :**
Un acquéreur sans aucun lot dans le projet « Les Jardins » ne peut pas en ouvrir
la maquette 3D.

---

### RG-E06 · Instantané des statuts 3D mis en cache 10 secondes
**Statut :** ✅
**Catégorie :** 3D / Performance
**Base légale :** —

**Règle (langage métier) :**
Les couleurs de statut des lots affichées en 3D sont rafraîchies au plus toutes
les 10 secondes, pour rester réactives sans surcharger la base.

**Implémentation :**
`Project3dService.getStatusSnapshot():123-125` annoté `@Cacheable`
(`LOT_STATUS_3D_CACHE`, TTL 10 s), clé par société + projet.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Deux utilisateurs ouvrant la même maquette à 3 secondes d'intervalle partagent le
même instantané de statuts.

---

## RG-F · Reporting & KPI

### RG-F01 · Taux d'absorption — formule unique
**Statut :** ✅
**Catégorie :** Reporting
**Base légale :** —

**Règle (langage métier) :**
Le taux d'absorption mesure la commercialisation : c'est la part des biens
**vendus** parmi les biens commercialisables (disponibles + réservés + vendus).
Les brouillons sont exclus du dénominateur.

**Implémentation :**
`HomeDashboardService:152-157` :
`absorption = sold / (active + reserved + sold) × 100`. Source unique partagée
front/back (front : `core/utils/absorption.ts`).

**Vérifiée par test :** `absorption.spec.ts` (frontend).

**Exemple concret :**
Sur 100 lots commercialisables dont 30 vendus, l'absorption est de **30,0 %**
(les lots en brouillon ne comptent pas).

---

### RG-F02 · Chiffre d'affaires réalisé reconnu à la livraison
**Statut :** ✅
**Catégorie :** Reporting / Finance
**Base légale :** —

**Règle (langage métier) :**
Le « CA réalisé » (revenu reconnu) n'est compté qu'à la **livraison définitive**
du bien, pas à l'acte notarié — approche prudente de reconnaissance du revenu.

**Implémentation :**
`HomeDashboardDTO` / `HomeDashboardService` : `caLivre` filtré sur
`LIVRE_DEFINITIF`. (Distinct de la clôture commerciale à `ACTE`.)

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Une vente actée mais non livrée apparaît en « CA acté » mais **pas** encore en
« CA réalisé ».

---

### RG-F03 · Tunnel de valeur : en cours → acté → livré
**Statut :** ✅
**Catégorie :** Reporting
**Base légale :** —

**Règle (langage métier) :**
Le pipeline de valeur se lit en trois temps : *CA en cours* (compromis +
financement), *CA acté* (acte notarié = clôture commerciale), *CA livré*
(livraison = reconnaissance du revenu).

**Implémentation :**
Calcul front à partir de `pipelineData().stages` (tab Dirigeant) ;
`HomeDashboardService` fournit les agrégats par statut.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Le dirigeant voit : 5 M MAD en cours, 3 M actés, 1,5 M livrés.

---

### RG-F04 · KPI recalculés automatiquement sur événement
**Statut :** ✅
**Catégorie :** Reporting
**Base légale :** —

**Règle (langage métier) :**
Les indicateurs de tranche se recalculent automatiquement quand une vente est
finalisée ou qu'une échéance change — sans recalcul manuel.

**Implémentation :**
`SaleFinalizedEvent` / `EcheanceChangedEvent` publiés par `VenteService` ;
consommés par `KpiComputationService` → `KpiSnapshot` (changeset `062`).

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
À la livraison d'un lot, le taux d'absorption de sa tranche est mis à jour
automatiquement.

---

### RG-F05 · KPI propriétaire/dirigeant réservés aux profils habilités
**Statut :** ✅
**Catégorie :** Reporting / Sécurité
**Base légale :** —

**Règle (langage métier) :**
Le classement des agents (leaderboard) et certains KPI de direction ne sont
visibles que par les Administrateurs et Managers, pas par les Agents.

**Implémentation :**
`HomeDashboardService` section leaderboard gardée ADMIN/MANAGER ;
`CommercialDashboardService.resolveEffectiveAgentId()` applique le RBAC avant
agrégation.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Un Agent voit ses propres chiffres mais pas le classement comparatif des autres
agents.

---

### RG-F06 · Export des ventes avec TVA (CSV / PDF)
**Statut :** ✅
**Catégorie :** Reporting / Finance
**Base légale :** ⚖️ CGI (TVA)

**Règle (langage métier) :**
Le rapport des ventes s'exporte en CSV et PDF, avec pour chaque ligne le prix HT,
le taux de TVA et le prix TTC.

**Implémentation :**
`ReportExportService` (batch `findAllById`) ; modèle `ventes-report.html` 9
colonnes ; TVA via `TvaCalculator` (règle A-002).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
L'export PDF affiche « Lot A-302 — 800 000 HT — 10 % — 880 000 TTC ».

---

## RG-G · Finance & Échéancier VEFA

### RG-G01 · Échéancier légal d'appels de fonds en 7 étapes
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** ⚖️ Art. 618-17 Loi 44-00

**Règle (langage métier) :**
L'échéancier d'appels de fonds VEFA suit 7 étapes liées à l'avancement des
travaux, dont les pourcentages totalisent 100 % du prix :
signature 5 %, fondations 10 %, plancher RDC 15 %, gros œuvre 20 %, couverture
20 %, second œuvre 20 %, livraison/titre 10 %.

**Implémentation :**
`EcheancierLegal.MA` (7 étapes) ; généré par
`VenteService.generateEcheancierLegal():702-741` (montant = prix × pct / 100,
arrondi au centime ; échéances espacées de 2 mois).

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️ (P2 échéancier).

**Exemple concret :**
Pour un bien à 1 000 000 MAD, l'échéance « fondations » est de 100 000 MAD (10 %).

---

### RG-G02 · L'échéancier légal ne peut être généré qu'une fois
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** ⚖️ Art. 618-17 Loi 44-00

**Règle (langage métier) :**
On ne peut pas générer deux fois l'échéancier légal d'une même vente (sinon on
appellerait 200 % du prix). Le prix doit aussi être défini.

**Implémentation :**
`generateEcheancierLegal():707-716` : verrou pessimiste sur la vente
(`requireVenteForUpdate`), garde `existsByVente_IdAndEtapeIsNotNull`
(idempotence anti-concurrence DA-003), prix > 0 obligatoire.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Un second clic sur « Générer l'échéancier légal » est refusé : l'échéancier existe
déjà.

---

### RG-G03 · Le cumul des échéances ne peut excéder le prix
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** ⚖️ Art. 618-17 Loi 44-00

**Règle (langage métier) :**
La somme de toutes les échéances d'une vente ne peut jamais dépasser le prix de
vente convenu.

**Implémentation :**
`VenteService.assertCumulWithinPrice():744-752` (sous verrou de la vente lors de
`addEcheance`) → `ViolationLegaleException`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Pour un bien à 1 000 000 MAD ayant déjà 950 000 MAD d'échéances, ajouter une
échéance de 100 000 MAD est refusé.

---

### RG-G04 · Annulation d'une vente : annulation des échéances en attente
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** —

**Règle (langage métier) :**
Quand une vente est annulée, ses échéances **en attente** passent à « annulée »,
mais les échéances **déjà payées** restent inchangées (elles ont été encaissées).

**Implémentation :**
`echeanceRepository.cancelAllPendingByVente()` appelé dans `applyStatutChange`
(ANNULE, `:657-659`) et `exerciseRetractation():368`. `EcheanceStatut.ANNULEE`
(règle A-001). La trésorerie exclut les ANNULEE.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Une vente annulée avec 1 échéance payée et 6 en attente : les 6 en attente
deviennent « annulées », la payée reste « payée ».

---

### RG-G05 · Calcul du prix TTC et taux de TVA marocain
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** ⚖️ Code Général des Impôts (Maroc)

**Règle (langage métier) :**
Le taux de TVA est suggéré selon le bien : **0 %** logement social (≤ 100 m² et
≤ 250 000 MAD, désigné social), **10 %** logement moyen (≤ 150 m² et
≤ 700 000 MAD), **20 %** dans tous les autres cas. Le prix TTC est toujours
recalculé (jamais stocké).

**Implémentation :**
`TvaCalculator.suggestTaux()` et `computePrixTtc()` (prix_ttc = prix_ht ×
(1 + taux), arrondi 2 décimales).

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Un appartement de 90 m² à 240 000 MAD désigné social se voit suggérer **0 %** de
TVA ; une villa à 2 000 000 MAD, **20 %**.

---

### RG-G06 · Pénalités de retard de livraison
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** ⚖️ Art. 618-17 Loi 44-00 (montant contractuel)

**Règle (langage métier) :**
Si la livraison réelle dépasse la date prévue, une pénalité journalière
(par défaut **500 MAD/jour**, configurable) s'accumule. Elle n'est calculée que
pour les ventes encore actives non livrées.

**Implémentation :**
`VenteService.toResponse():1027-1038` : `joursRetard` + `penaliteAccumulee =
penaliteRetardJournalierMad × joursRetard`
(`MarketConfig.getPenaliteRetardJournalierMad()`, règle B-001). Agrégé dans
`TresorerieDashboardService`.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Une livraison prévue le 01/06/2026 toujours non livrée le 14/06/2026 affiche 13
jours de retard et 6 500 MAD de pénalité accumulée.

---

### RG-G07 · Workflow des appels de fonds (brouillon → émis → envoyé → payé)
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** —

**Règle (langage métier) :**
Un appel de fonds suit un cycle : seul un appel en **brouillon** peut être émis ;
un appel émis/envoyé peut être renvoyé ; un appel entièrement **payé** ne peut être
ni annulé ni recevoir de nouveau paiement. Tout paiement doit être positif.

**Implémentation :**
`CallForFundsWorkflowService.issue():65 / send():85 / cancel():141 / addPayment():165`
→ `InvalidPaymentScheduleStateException` ; `PaymentInvalidAmountException` si
montant ≤ 0.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Tenter d'annuler un appel de fonds déjà entièrement payé est refusé.

---

### RG-G08 · Plafond légal du dépôt à la confirmation
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** ⚖️ Art. 618-4 Loi 44-00

**Règle (langage métier) :**
Voir **RG-B08** — le dépôt de garantie est plafonné à 5 % du prix. (Règle
financière, énoncée ici pour le lecteur finance.)

**Implémentation :**
`VenteService.confirmReservation()` + `MarketConfig.getDepotGarantieMaxPct()`.

**Vérifiée par test :** `VenteServiceTest`.

**Exemple concret :**
Voir RG-B08.

---

### RG-G09 · Un seul dépôt/réservation active par bien
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** —

**Règle (langage métier) :**
On ne peut pas créer un dépôt sur un bien qui n'est pas disponible, qui a déjà un
dépôt actif (en attente ou confirmé), ou qui est tenu par une réservation active
non convertie.

**Implémentation :**
`DepositService.create():118-137` : gardes
`existsBySocieteIdAndContact_IdAndPropertyId`,
`existsBySocieteIdAndPropertyIdAndStatusIn(ACTIVE_STATUSES)`, réservation ACTIVE,
statut bien = ACTIVE. Verrou pessimiste sur le bien.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Créer un dépôt sur un lot déjà tenu par une réservation active est refusé.

---

### RG-G10 · Seul un dépôt « en attente » peut être confirmé
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** —

**Règle (langage métier) :**
Un dépôt ne peut être confirmé que s'il est au statut « en attente ».

**Implémentation :**
`DepositService.confirm():208-216` → `InvalidDepositStateException` sinon.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Confirmer un dépôt déjà confirmé est refusé.

---

### RG-G11 · Réservation : expiration automatique et alerte à 48 h
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** —

**Règle (langage métier) :**
Une réservation a une échéance (par défaut +7 jours). À l'échéance, elle expire
automatiquement et libère le bien. 48 h avant, l'agent reçoit une alerte (une
seule fois).

**Implémentation :**
`ReservationService.create():117-119` (expiry +7 j par défaut) ;
`runExpiryCheck():348` (expiration) ; `runExpirySoonCheck():360-374` (alerte 48 h,
drapeau `notifiedExpiringSoon`). Changeset `074`.

**Vérifiée par test :** `ReservationServiceTest` (5 tests).

**Exemple concret :**
Une réservation créée le 07/06/2026 expire le 14/06/2026 ; l'agent reçoit une
alerte le 12/06/2026.

---

### RG-G12 · Conversion réservation → dépôt sous verrou
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** —

**Règle (langage métier) :**
Seule une réservation **active** peut être convertie en dépôt formel, et
seulement si le bien est toujours réservé. Une vente concurrente qui aurait vendu
le bien empêche la conversion.

**Implémentation :**
`ReservationService.convertToDeposit():288-342` : verrou pessimiste sur le bien,
garde « bien encore RESERVED », réservation → `CONVERTED_TO_DEPOSIT`, dépôt créé
via `DepositService`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Convertir une réservation alors que le bien vient d'être vendu par un autre canal
est refusé.

---

### RG-G13 · Obligation de remboursement créée à l'annulation
**Statut :** ✅
**Catégorie :** Finance
**Base légale :** ⚖️ Art. 618-4 Loi 44-00 (restitution du dépôt)

**Règle (langage métier) :**
Quand une vente est annulée, une obligation de remboursement du dépôt est créée
automatiquement au statut « dû », avec le montant du dépôt versé. L'agent peut
ensuite la marquer « effectuée » (avec date, moyen et référence), ce qui est
audité. Un remboursement déjà effectué ne peut être ré-effectué.

**Implémentation :**
`RemboursementService.onVenteAnnulee():62` (écoute `VenteAnnuleeEvent`) crée le
`DU` ; `upsertDu():94` / `marquerEffectue():119` ; `StatutRemboursement`
(DU/EFFECTUE/ANNULE). Changeset `085`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Une vente annulée avec dépôt de 50 000 MAD crée un remboursement « dû » de
50 000 MAD ; l'agent le marque « effectué par virement le 20/06/2026 ».

---

### RG-G14 · Tableau de trésorerie : encaissé, à encaisser, prévisionnel, en retard
**Statut :** ✅
**Catégorie :** Finance / Reporting
**Base légale :** —

**Règle (langage métier) :**
Le tableau de trésorerie consolide : montant déjà encaissé, montant à encaisser,
prévisionnel sur les prochains mois, et montant en retard. Le prévisionnel exclut
ce qui est déjà compté « en retard » pour éviter les doubles comptes.

**Implémentation :**
`TresorerieDashboardService.getTresorerie():53-90` :
`sumPaidAll`, `sumDueAll`, `sumMontantDueInPeriod`, `sumMontantOverdue`,
prévisionnel par mois excluant l'avant-`today`, total pénalités de retard.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Le dirigeant voit « Encaissé 4,2 M — À encaisser 6,8 M — Prévisionnel 3 mois
2,1 M — En retard 350 000 MAD ».

---

## RG-H · UX/UI & Conformité transverse

### RG-H01 · Consentement ou base légale obligatoire à la création d'un contact
**Statut :** ✅
**Catégorie :** Conformité données
**Base légale :** ⚖️ Loi 09-08 Art. 4 / RGPD Art. 6

**Règle (langage métier) :**
On ne peut pas créer un contact sans soit son consentement explicite, soit une
base juridique de traitement déclarée.

**Implémentation :**
`ContactService.create():71-75` → `BusinessRuleException(CONSENT_REQUIRED)` si ni
`consentGiven` ni `processingBasis`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Enregistrer un prospect sans cocher le consentement et sans base légale est
refusé.

---

### RG-H02 · Rétention des données par durée légale
**Statut :** ✅
**Catégorie :** Conformité données
**Base légale :** ⚖️ Loi 09-08 (prospection) + Art. 618-17 Loi 44-00 (archives)

**Règle (langage métier) :**
Les données sont conservées selon leur nature : prospects **2 ans**, acquéreurs
**5 ans**, acquéreurs VEFA finalisés **10 ans** (obligation d'archivage). Un
balayage planifié applique ces durées.

**Implémentation :**
`DataRetentionScheduler` : `RETENTION_PROSPECT_DAYS=730`,
`RETENTION_ACQUEREUR_DAYS=1825`, `RETENTION_VEFA_DAYS=3650` ;
`findRetentionCandidatesByStatuses()` (3 passes). Doc `docs/legal/data-retention.md`.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Un prospect inactif supprimé il y a plus de 2 ans devient candidat à la purge
définitive.

---

### RG-H03 · Journalisation des lectures de données sensibles
**Statut :** ✅
**Catégorie :** Conformité données / Sécurité
**Base légale :** ⚖️ Loi 09-08 (traçabilité)

**Règle (langage métier) :**
La consultation des données légales sensibles (identité juridique d'un contact,
détails commerciaux) est tracée dans le journal d'audit.

**Implémentation :**
Aspect `@ReadAudit` (`ReadAuditAspect`) → `SensitiveDataReadEvent` →
`AuditEventListener` (REQUIRES_NEW). Annoté sur `ContactService.getLegalDetails()`
et `getCommercial()` (règle B-004).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Quand un manager ouvre la CIN et la situation matrimoniale d'un acquéreur, une
entrée d'audit « lecture donnée sensible » est créée.

---

### RG-H04 · Déclaration CNDP saisissable (conformité 09-08)
**Statut :** ✅
**Catégorie :** Conformité données
**Base légale :** ⚖️ Loi 09-08 (CNDP)

**Règle (langage métier) :**
L'administrateur de la société peut renseigner le numéro de déclaration CNDP et sa
date, preuve de conformité à la loi de protection des données.

**Implémentation :**
`ComplianceController` (`GET/PATCH /api/mon-espace/compliance`, `@PreAuthorize
ADMIN`) → `Societe.numeroCndp` / `dateDeclarationCndp` (règle B-005).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
L'admin saisit « CNDP n° D-GC-123/2026 déclaré le 02/01/2026 » dans son espace
conformité.

---

### RG-H05 · Données légales du contact isolées (CIN, situation, type acquéreur)
**Statut :** ✅
**Catégorie :** Conformité données
**Base légale :** ⚖️ Loi 44-00 (identification des parties)

**Règle (langage métier) :**
Les données légales sensibles du contact (CIN/passeport, situation matrimoniale,
type d'acquéreur résident/MRE/étranger) sont gérées via un point d'accès dédié,
séparé des données commerciales courantes.

**Implémentation :**
`ContactService.getLegalDetails()/updateLegalDetails():173-196` ;
`TypeAcquereur` (RESIDENT_MAROC, MRE, ETRANGER) ; changeset `080`.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Le type « MRE » (Marocain résidant à l'étranger) est enregistré sur la fiche
légale du contact, distinct de ses coordonnées commerciales.

---

### RG-H06 · Complétude du contact exigée selon l'étape (réservation / vente)
**Statut :** ⚠️ (service implémenté et testé ; câblage dans le flux vente non confirmé)
**Catégorie :** Conformité / Pipeline
**Base légale :** ⚖️ Loi 44-00 (identification de l'acquéreur)

**Règle (langage métier) :**
Avant une réservation, le contact doit avoir un téléphone ; avant la vente, il
doit aussi avoir une pièce d'identité (CIN) et une adresse. Sinon, l'opération
liste les champs manquants.

**Implémentation :**
`ContactCompletenessService.validateForStage():37-56` (RESERVATION → phone ;
VENTE → + nationalId + address) → `ClientIncompleteException` (422).
⚠️ Aucun appel à cette validation n'a été identifié dans `VenteService.create()` —
le service existe et est testé, mais son intégration au parcours vente reste à
confirmer (voir SUGG-02).

**Vérifiée par test :** `ContactCompletenessServiceTest` (5 tests).

**Exemple concret :**
Le service refuse de valider un contact sans CIN pour l'étape VENTE — sous réserve
qu'il soit bien appelé au moment de créer la vente.

---

### RG-H07 · Co-acquéreur / indivision (un enregistrement par vente)
**Statut :** ⚠️
**Catégorie :** Pipeline / Conformité
**Base légale :** ⚖️ Loi 44-00 (parties au contrat)

**Règle (langage métier) :**
Une vente peut porter un co-acquéreur avec son rôle (conjoint, indivisaire…). Un
seul enregistrement co-acquéreur est autorisé par vente.

**Implémentation :**
`CoAcquereurService.add():43-47` → `CoAcquereurAlreadyExistsException` si déjà
présent ; `RoleAcquereur` ; changeset `081`.
⚠️ Limité à **un** co-acquéreur par vente : ne couvre pas l'indivision à plus de
deux parties (voir SUGG-04).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
On ajoute le conjoint comme co-acquéreur d'une vente ; tenter d'en ajouter un
second est refusé.

---

### RG-H08 · Dossier de financement de l'acquéreur (suivi accord bancaire)
**Statut :** ✅
**Catégorie :** Finance / Pipeline
**Base légale :** —

**Règle (langage métier) :**
Le dossier de financement de l'acquéreur suit son statut auprès de la banque :
en cours, accord de principe, accord définitif, ou refusé. Les accords expirant
bientôt peuvent être identifiés.

**Implémentation :**
`DossierFinancementService.upsert():46` ; `StatutDossierFinancement` (4 valeurs) ;
`findAccordsExpiringSoon():73`. Relation 1:1 vente (changeset `082`).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Le dossier passe « accord de principe » le 10/06/2026 ; l'équipe relance avant
expiration de l'accord.

---

### RG-H09 · Livraison avec réserves et délai de levée
**Statut :** ✅
**Catégorie :** Pipeline / Conformité
**Base légale :** ⚖️ Loi 44-00 (réception/réserves)

**Règle (langage métier) :**
À la livraison, on enregistre les réserves constatées. Chaque réserve a un délai
de levée (par défaut **60 jours**). Quand toutes les réserves sont levées, la
vente passe à « réserves levées ».

**Implémentation :**
`VenteService.recordDelivery():448-474` (ACTE → LIVRE_AVEC_RESERVES, échéance =
today + `getDelaiLeveeReservesJours()`=60) ; `liftReserve():485-509` (dernière levée
→ RESERVES_LEVEES). Changeset `078`.

**Vérifiée par test :** Aucun test unitaire dédié trouvé ⚠️

**Exemple concret :**
Livraison avec 3 réserves (peinture, robinetterie, porte) à lever sous 60 jours ;
la 3ᵉ levée fait passer la vente à « réserves levées ».

---

### RG-H10 · Génération de documents légaux (contrat de réservation, PV, quittances)
**Statut :** ✅
**Catégorie :** Conformité / Documents
**Base légale :** ⚖️ Loi 44-00 (mentions obligatoires)

**Règle (langage métier) :**
Le système génère les documents légaux : contrat de réservation, PV de livraison
(avec réserves et pénalités), quittances d'appels de fonds — au format PDF.

**Implémentation :**
`VenteLegalDocumentService`, `VenteContractPdfService`, `CallForFundsPdfService`,
`DocumentGenerationService`. Endpoints
`POST /api/ventes/{id}/documents/contrat-reservation`, `/pv-livraison`,
`/echeances/{id}/quittance`. Checklist `docs/legal/pdf-review-checklist.md`.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
À la confirmation de réservation, l'agent génère le contrat de réservation PDF
prêt à signer.

---

### RG-H11 · Modèles PDF en référentiel de caractères numériques
**Statut :** ✅
**Catégorie :** UX / Documents
**Base légale :** —

**Règle (langage métier) :**
Les modèles PDF utilisent des caractères au format numérique (espaces insécables,
tirets) pour éviter les erreurs de génération du moteur PDF.

**Implémentation :**
Convention `&#160;` / `&#8212;` (jamais `&nbsp;`/`&mdash;`) dans tous les modèles
Thymeleaf — `openhtmltopdf` rejette les entités nommées HTML4.

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Un espace insécable dans « 1 250 000 MAD » est encodé `&#160;` pour ne pas casser
la génération du PDF.

---

### RG-H12 · Application en français uniquement, formats MAD/dates
**Statut :** ✅
**Catégorie :** UX
**Base légale :** —

**Règle (langage métier) :**
L'interface est entièrement en français. Les montants sont en dirhams (MAD) et les
dates au format jour/mois/année.

**Implémentation :**
Phase D : ngx-translate retiré (FR uniquement, 613 pipes), alias de redirection
`/biens` + `/projets`. Tokens de marque CSS.

**Vérifiée par test :** build frontend (lint) ; pas de test de format dédié ⚠️

**Exemple concret :**
Un prix s'affiche « 1 250 000 MAD » et une date « 14/06/2026 ».

---

### RG-H13 · États de chargement et états vides soignés
**Statut :** ✅
**Catégorie :** UX
**Base légale :** —

**Règle (langage métier) :**
Pendant le chargement, l'interface affiche des squelettes animés ; en l'absence de
données, un état vide explicite avec une action proposée — jamais une page blanche.

**Implémentation :**
Primitives `.skeleton` (styles.css) + `.empty-state` avec CTA, appliquées aux
pages tâches et tableau de bord (Wave 12 D).

**Vérifiée par test :** Aucun test trouvé ⚠️

**Exemple concret :**
Un tableau de bord encore en chargement montre des cartes-squelettes au lieu d'un
écran vide.

---

## Fonctionnalités & règles suggérées (non encore implémentées)

> Ces éléments **n'existent pas** (ou sont incomplets) dans le code actuel. Ce
> sont des recommandations de l'équipe pour renforcer la conformité métier et
> légale. Ils sont volontairement séparés des règles ci-dessus.

### SUGG-01 · Tests automatisés des gardes légales VEFA
**Domaine concerné :** RG-B, RG-G
**Priorité :** 🔴 Critique
**Motivation métier :** Les règles à plus fort risque juridique (plafond 5 %,
délai de rétractation 7 j, échéancier 100 %, étapes gardées) n'ont pas toutes de
test unitaire dédié identifié. Un changement de code pourrait les casser
silencieusement.
**Base légale :** ⚖️ Art. 618-3, 618-4, 618-17 Loi 44-00
**Règle proposée :** Couvrir par tests unitaires/IT : RG-B07, B09, B10, B11, B12,
G01, G02, G03, G05.
**Impact si absente :** Régression non détectée sur une obligation légale →
litige, nullité d'acte, sanction.
**Effort estimé :** M

### SUGG-02 · Câbler la complétude du contact dans le parcours vente
**Domaine concerné :** RG-H06
**Priorité :** 🔴 Critique
**Motivation métier :** Le service `ContactCompletenessService` existe et est testé,
mais n'est apparemment pas appelé à la création de la vente. Une vente peut donc
démarrer sans CIN ni adresse de l'acquéreur.
**Base légale :** ⚖️ Loi 44-00 (identification des parties au contrat)
**Règle proposée :** Appeler `validateForStage(..., VENTE)` au début de
`VenteService.create()` et `confirmReservation()`.
**Impact si absente :** Contrats générés sans identité complète → documents légaux
invalides.
**Effort estimé :** S

### SUGG-03 · Verrouillage du prix après l'acompte
**Domaine concerné :** RG-B14
**Priorité :** 🟠 Importante
**Motivation métier :** Le prix de vente est fixé à la création, mais aucun contrôle
n'empêche de le modifier une fois l'acompte versé. Modifier le prix après
engagement financier de l'acquéreur ouvre un risque de litige.
**Base légale :** ⚖️ Loi 44-00 (prix convenu au contrat)
**Règle proposée :** Refuser toute modification de `prixVente` une fois la vente au
statut `ACOMPTE` ou au-delà (→ 422).
**Impact si absente :** Contestation possible du prix par l'acquéreur ; incohérence
avec l'échéancier déjà émis.
**Effort estimé :** S

### SUGG-04 · Indivision à plus de deux parties (co-acquéreurs multiples)
**Domaine concerné :** RG-H07
**Priorité :** 🟠 Importante
**Motivation métier :** Une seule ligne co-acquéreur est autorisée par vente. Les
achats en indivision (familles, SCI à plusieurs associés) ne sont pas couverts.
**Base légale :** ⚖️ Loi 44-00 (pluralité d'acquéreurs)
**Règle proposée :** Autoriser N co-acquéreurs par vente avec quote-part dont la
somme = 100 %.
**Impact si absente :** Impossible de traiter les ventes en indivision sans
contournement.
**Effort estimé :** M

### SUGG-05 · Suivi de la condition suspensive de financement
**Domaine concerné :** RG-H08
**Priorité :** 🟠 Importante
**Motivation métier :** Le dossier de financement a un statut, mais l'échéance de la
condition suspensive (`dateLimiteFinancement`) ne déclenche pas d'effet automatique
en cas de refus/dépassement.
**Base légale :** ⚖️ Loi 44-00 (condition suspensive de crédit)
**Règle proposée :** À l'expiration sans accord, alerter et proposer l'annulation
avec motif `CREDIT_REFUSE` ; restitution du dépôt automatique.
**Impact si absente :** Ventes bloquées en `FINANCEMENT`, restitutions oubliées.
**Effort estimé :** M

### SUGG-06 · Expiration des réservations longues sans acte (90 j)
**Domaine concerné :** RG-G11
**Priorité :** 🟠 Importante
**Motivation métier :** Les réservations expirent selon leur échéance, mais aucune
purge ne cible les ventes restées « réservées » très longtemps sans progression
vers l'acte, qui immobilisent l'inventaire.
**Base légale :** —
**Règle proposée :** Alerter (puis proposer annulation) les ventes au statut
`RESERVE`/`ACOMPTE` inactives depuis > 90 jours.
**Impact si absente :** Stock fantôme, absorption faussée, lots bloqués.
**Effort estimé :** M

### SUGG-07 · Cession de contrat de réservation (revente avant livraison)
**Domaine concerné :** RG-B
**Priorité :** 🟡 Confort
**Motivation métier :** Un acquéreur VEFA peut vouloir céder son contrat avant
livraison. Aucune fonction de cession (transfert de la vente à un nouveau contact)
n'existe.
**Base légale :** ⚖️ Loi 44-00 (cession du contrat)
**Règle proposée :** Action « céder la vente » qui change l'acquéreur en conservant
l'historique et les échéances déjà payées.
**Impact si absente :** Géré hors système, perte de traçabilité.
**Effort estimé :** L

### SUGG-08 · Gestion du défaut de paiement de l'acquéreur
**Domaine concerné :** RG-G
**Priorité :** 🟠 Importante
**Motivation métier :** Les échéances passent « en retard » mais aucune procédure de
défaut (mise en demeure, pénalités acquéreur, résolution) n'est outillée.
**Base légale :** ⚖️ Loi 44-00 (déchéance/résolution)
**Règle proposée :** Workflow de relance graduée → mise en demeure → proposition de
résolution avec décompte.
**Impact si absente :** Recouvrement manuel, risque de contentieux mal documenté.
**Effort estimé :** L

### SUGG-09 · Quittances automatiques à chaque paiement encaissé
**Domaine concerné :** RG-G07, RG-H10
**Priorité :** 🟡 Confort
**Motivation métier :** Les quittances d'appels de fonds existent à la demande, mais
ne sont pas générées automatiquement à l'encaissement.
**Base légale :** ⚖️ CGI (justificatif de paiement)
**Règle proposée :** Générer et notifier la quittance PDF à chaque échéance passée
« payée ».
**Impact si absente :** Tâche manuelle répétitive, retards de justificatifs.
**Effort estimé :** S

### SUGG-10 · Audit immuable des actions légalement significatives
**Domaine concerné :** RG-A, RG-H03
**Priorité :** 🟠 Importante
**Motivation métier :** Les lectures sensibles sont auditées, mais il n'existe pas de
journal **immuable** (inaltérable, horodaté, chaîné) des actes légaux
(confirmation, rétractation, acte, livraison).
**Base légale :** ⚖️ Loi 09-08 + valeur probante
**Règle proposée :** Journal d'audit append-only avec empreinte chaînée pour les
événements VEFA clés.
**Impact si absente :** Faible valeur probante en cas de litige.
**Effort estimé :** M

### SUGG-11 · Restitution du dépôt reliée à la trésorerie
**Domaine concerné :** RG-G13, RG-G14
**Priorité :** 🟡 Confort
**Motivation métier :** Le remboursement « dû » est créé, mais il n'est relié à aucun
flux de trésorerie sortant pour suivi du décaissement.
**Base légale :** ⚖️ Art. 618-4 Loi 44-00
**Règle proposée :** Intégrer les remboursements « dus » au tableau de trésorerie
(sorties prévues) et les marquer « effectués » depuis ce tableau.
**Impact si absente :** Risque d'oubli de restitution, exposition légale.
**Effort estimé :** S

### SUGG-12 · Trésorerie prévisionnelle liée à l'avancement des travaux
**Domaine concerné :** RG-G14
**Priorité :** 🟡 Confort
**Motivation métier :** La trésorerie agrège les échéances, mais ne projette pas les
appels de fonds futurs liés à l'avancement réel des travaux par tranche.
**Base légale :** —
**Règle proposée :** Vue prévisionnelle croisant statut des tranches (RG-D05) et
échéancier légal (RG-G01).
**Impact si absente :** Pilotage de trésorerie partiel pour le dirigeant.
**Effort estimé :** M

### SUGG-13 · Champs commerciaux du bien exposés en 3D et fiches (vue, orientation)
**Domaine concerné :** RG-D, RG-E
**Priorité :** 🟡 Confort
**Motivation métier :** Les champs commerciaux (étage, orientation, prix TTC,
charges) existent (changeset `083`) mais leur exposition systématique dans la vue
3D et les fiches reste partielle.
**Base légale :** —
**Règle proposée :** Afficher orientation/vue/étage/prix TTC dans l'infobulle 3D et
la fiche bien.
**Impact si absente :** Argumentaire commercial appauvri.
**Effort estimé :** S

---

## RG-V — Module Visites & Agenda (Wave 16)

Rendez-vous commerciaux agent↔contact pour présenter un bien/programme. Toutes les
règles sont société-scoped (RG-A transverse). Code : `visite/` (controller/service/repo/domain).

| Règle | Énoncé | Statut | Ancrage code |
|---|---|---|---|
| **RG-V01** | Une Visite lie un agent et un Contact ; bien (Property) et programme (Project) optionnels | ✅ | `Visite`, `CreateVisiteRequest` |
| **RG-V02** | Transitions : PLANIFIEE→CONFIRMEE→REALISEE ; PLANIFIEE/CONFIRMEE→ANNULEE ; CONFIRMEE→NO_SHOW. Terminaux : REALISEE/ANNULEE/NO_SHOW | ✅ | `StatutVisite.allowedTransitions`, `VisiteService` |
| **RG-V03** | Types : SUR_SITE \| AGENCE \| VISIO \| TELEPHONIQUE | ✅ | `TypeVisite` |
| **RG-V04** | AGENT → ses propres visites ; MANAGER/ADMIN → toute la société ; PORTAL aucun accès | ✅ | `VisiteService.restrictedToOwnAgent()`, `@PreAuthorize` |
| **RG-V05** | Anti double-booking : pas de chevauchement pour un même agent → 409 ; MANAGER `override=true` tracé | ✅ | `VisiteService.verifierConflit`, `ConflitVisiteException` |
| **RG-V06** | REALISEE exige compteRendu + resultat ∈ {INTERESSE, A_RELANCER, PAS_INTERESSE, OPPORTUNITE_CREEE} ; OPPORTUNITE_CREEE → vente liée (pipeline existant) | ✅ | `enregistrerCompteRendu`, `lierVente`, `CompteRenduRequisException` (422) |
| **RG-V07** | Rappels persistants : H24 agent+prospect, H1 agent ; job DB-scan toutes 5 min ; idempotent ENVOYE ; jamais en mémoire | ✅ | `VisiteRappel`, `RappelVisiteJob`, `VisiteRappelService` |
| **RG-V08** | Annulation PLANIFIEE/CONFIRMEE → ANNULEE + raison ; annule rappels en attente ; email si était CONFIRMEE | ✅ | `VisiteService.annuler` |
| **RG-V09** | KPI « Visites réalisées » = COUNT(REALISEE) sur période ; conversion = OPPORTUNITE_CREEE/REALISEE. Source unique : VisiteService | ✅ | `countRealisees`/`countOpportunites`, `HomeDashboardService` |
| **RG-V10** | Stockage TIMESTAMPTZ (Instant) ; saisie/affichage/rappels/.ics en heure Casablanca | ✅ | `casablanca-time.ts`, `Instant` slots |

---

*Fin du référentiel. Identifiants RG-A à RG-H stables ; toute nouvelle règle doit
être ajoutée à la suite de sa catégorie avec un numéro séquentiel et un ancrage
code obligatoire.*
