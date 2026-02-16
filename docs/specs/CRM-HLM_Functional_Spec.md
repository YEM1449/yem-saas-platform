# CRM-HLM — Spécification Fonctionnelle

**Projet :** CRM-HLM (CRM Promotion & Construction)

**Version :** 1.0-draft

**Date :** 16 février 2026

**Auteur :** Senior Software Architect & Project Director


---

## Table des matières

- 1. Résumé exécutif

- 2. Périmètre

- 3. Personas & rôles

- 4. Vue d’ensemble des modules

- 5. Parcours utilisateurs

- 6. Exigences fonctionnelles

- 7. Règles métier

- 8. Workflows & machines d’états

- 9. Exigences non-fonctionnelles (vue métier)

- 10. KPIs & reporting

- 11. Roadmap (confirmée vs ouverte)

- 12. Implémenté vs planifié

- 13. Incohérences, conflits & résolutions

- 14. Points ouverts & décisions attendues

- 15. Glossaire

- 16. Sources

- 17. Matrice de traçabilité


---

## 1. Résumé exécutif

CRM-HLM vise à fournir une solution CRM intégrée pour un groupe opérant dans la **promotion immobilière**, la **construction**, et la **gestion multi-projets**, couvrant le cycle complet du projet : prospection foncière, autorisations, commercialisation, construction, achats/logistique, suivi administratif/financier et SAV. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L7–L8]

Le MVP attendu cible : **utilisateurs & rôles**, **multi-sociétés / multi-projets**, **CRM commercial**, **prospection foncière**, **workflow administratif simplifié** et **tableaux de bord essentiels**. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L350–L383]


## 2. Périmètre

### 2.1 Inclus (in-scope)

- Gestion multi-sociétés & multi-projets (consolidation, filtrage, droits). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L25–L28]
- Prospection foncière (base terrains, pipeline acquisition, calcul COS/CES). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L29–L32] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L104–L107]
- Commercial (prospects/contacts, lots, pipeline, réservation, génération de documents, envoi SMS/email). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L33–L38] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L120–L123]
- Administratif (suivi autorisations Maroc/UE, archivage, alertes). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L56–L59] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L169–L172]
- Dashboards essentiels (indicateurs commerciaux, état lots/projets, vue synthétique direction). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L380–L383]


### 2.2 Hors périmètre (out-of-scope)

- Tout ce qui n’est pas explicitement décrit dans les documents fournis (ex : application mobile dédiée, BI externe, connecteurs spécifiques). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L24–L64]


### 2.3 Hypothèses

- La solution est conçue comme une application **SaaS cloud** accessible « depuis tout support ». [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L18–L19]
- Les détails d’intégration (comptabilité, site web, partenaires) restent à définir : uniquement l’intention est citée. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L420–L423]


## 3. Personas & rôles

Le CDC nomme explicitement des profils/acteurs (admin, direction, commercial, technique) et des acteurs opérationnels (chef de chantier, responsable foncier, SAV, DSI, etc.). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L356–L359] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L133] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L89–L90] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L83–L84]


### 3.1 Rôles (RBAC) — proposition minimale

> **Note :** la granularité exacte des rôles/profils est à valider. Voir [OPEN POINT] §14.1.


| Rôle | Description | Accès typiques | Sources |
| --- | --- | --- | --- |
| Admin | Administration comptes, rôles, paramètres. | Tous modules + paramétrage | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L68–L84] |
| Direction | Vision consolidée, arbitrages et validations. | Dashboards + validation étapes clés | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L358–L383] |
| Commercial | Cycle prospect → vente → réservation. | Prospects, lots, pipeline, réservations, KPIs | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L364–L369] |
| Responsable foncier | Pipeline acquisition et analyses. | Terrains, étapes foncières, COS/CES | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L370–L374] |
| Chef de chantier / Technique | Suivi chantier et opérations terrain. | Planning, avancement, journal, photos/incidents | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L125–L138] |
| Achats / Logistique | DA/BC, fournisseurs, réceptions. | Achats, fournisseurs, rapprochements | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L395–L399] |
| Stock / Magasinier | Entrées/sorties, inventaires, alertes. | Stocks chantier (QR/NFC) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L391–L394] |
| DAF / Finance | Suivi coûts, marges, export. | Finance & reporting | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L406–L409] |
| SAV | Tickets, interventions, clôture. | Module SAV | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L417–L419] |


### 3.2 Table des permissions (module-level)

Le système vise une **gestion fine des permissions** par société, projet et module. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L27–L28] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L359]


| Module | Actions | Rôles minimum | Sources |
| --- | --- | --- | --- |
| Utilisateurs & rôles | CRUD utilisateurs, affectation rôles, désactivation | Admin | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L68–L84] |
| Sociétés & projets | CRUD sociétés/projets, consolidation/filtrage | Admin, Direction | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L25–L28] |
| Commercial | CRUD prospects, lots, pipeline, réservation, génération doc | Commercial, Direction | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L364–L369] |
| Foncier | CRUD terrains, étapes, validation direction | Responsable foncier, Direction | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L200–L204] |
| Administratif | MAJ étapes autorisations, archivage, alertes | Admin, Direction, Technique | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L375–L379] |
| Dashboards | Lecture KPIs et synthèses | Direction (+ rôles selon périmètre) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L380–L383] |


## 4. Vue d’ensemble des modules

Les modules listés ci-dessous proviennent du périmètre fonctionnel et du backlog priorisé. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L24–L64] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L352–L423]


| Module | Description (métier) | Statut (MVP / post-MVP) | Sources |
| --- | --- | --- | --- |
| Gestion des utilisateurs & rôles | Comptes, rôles, permissions par société/projet/module. | MVP (P1) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L356–L359] |
| Multi-sociétés / multi-projets | Administration sociétés/projets + consolidation des données. | MVP (P1) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L360–L363] |
| Commercial | Prospects, lots, pipeline de vente, réservations, docs, SMS/email. | MVP (P1) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L364–L369] |
| Prospection foncière | Base terrains + pipeline acquisition + COS/CES. | MVP (P1) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L370–L374] |
| Administratif (Maroc+UE) | Autorisations, archivage, alertes. | MVP (P1) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L375–L379] |
| Tableaux de bord essentiels | KPIs commerciaux + synthèse direction. | MVP (P1) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L380–L383] |
| Construction (phase 1) | Planning Gantt + suivi par phase + journal. | Post-MVP (P2) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L387–L390] |
| Stocks chantier | Entrées/sorties, QR/NFC, alertes, inventaires. | Post-MVP (P2) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L391–L394] |
| Achats & fournisseurs | DA/BC, rapprochements, historique fournisseurs. | Post-MVP (P2) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L395–L399] |
| Automatisations & notifications | Rappels, scénarios marketing/opérationnels. | Post-MVP (P2) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L400–L402] |
| Finance complet | Prévisionnel vs réalisé, marges, export comptable avancé. | Évolutif (P3) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L406–L409] |
| Qualité & sécurité chantier | Checklists, signalements, conformité. | Évolutif (P3) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L410–L413] |
| Sous-traitants | Docs légaux, notation, historique. | Évolutif (P3) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L414–L416] |
| SAV | Tickets, interventions, clôture. | Évolutif (P3) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L417–L419] |
| Intégrations externes (API) | Comptabilité, site web, outils partenaires. | Évolutif (P3) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L420–L423] |


### 4.1 Entités métier (vue fonctionnelle)

> Liste minimale issue des descriptions (textes) et corroborée par l’ERD (support). [SRC: erd_detail_pro2.png | L1]


| Entité | Description | Modules | Sources |
| --- | --- | --- | --- |
| Société | Entité juridique ; périmètre de consolidation et d’accès. | Multi-sociétés | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L25–L27] |
| Projet | Programme immobilier ; regroupe lots, chantier, finance. | Multi-projets | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L26–L27] |
| Lot | Bien commercialisable (prix, disponibilité, typologie). | Commercial | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L35–L36] |
| Prospect | Contact en pipeline de vente. | Commercial | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L34–L36] |
| Réservation | Engagement initial sur un lot + documents générés. | Commercial | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L368] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L490–L503] |
| Terrain | Bien foncier identifié + documents + analyse (COS/CES). | Foncier | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L29–L32] |
| Autorisation | Étape/résultat d’un processus réglementaire. | Administratif | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L57–L59] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L169–L172] |
| Demande d’achat / BC / BL / Facture | Chaîne achats avec validations et rapprochements. | Achats | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L159–L162] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L55] |
| Article / Stock / Mouvement | Matériel chantier, inventaire, entrées/sorties (QR/NFC). | Stocks | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L45–L49] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L145–L147] |
| Ticket SAV | Réclamation post-livraison + intervention + clôture. | SAV | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L189–L192] |


## 5. Parcours utilisateurs

### 5.1 De la prospection foncière à la décision

- Création fiche terrain + pièces (titres/plans/photos) → calcul COS/CES → validation direction. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L194–L204] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L97–L103]
- Pipeline foncier (repérage → ... → décision). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L104–L107]


### 5.2 Du prospect à la réservation et aux appels de fonds

- Création/qualification prospect → proposition lot → négociation → réservation → signature → appels de fonds. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L120–L123]
- Génération de documents lors de la réservation (PDF). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L490–L503]


### 5.3 Du besoin chantier à la facture fournisseur

- Demande d’achat (DA) → validation → consultation fournisseurs → bon de commande (BC) → réception → facturation. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L159–L162]
- Rapprochement BC/BL/Facture pour sécuriser les paiements. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L55] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L398]


### 5.4 De l’avancement chantier au reporting

- Mise à jour du planning Gantt + ajout photos/incidents → mise à jour des KPIs. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L125–L134]
- Phasage chantier (ordre de service → ... → réception technique). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L135–L138]


### 5.5 Du ticket SAV à la clôture

- Ticket → diagnostic → intervention → validation client → clôture. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L189–L192]


## 6. Exigences fonctionnelles

### 6.1 Table des exigences (FR)

| FR-ID | Module | Description | Priorité | Critères d’acceptation (extraits) | Sources |
| --- | --- | --- | --- | --- | --- |
| FR-001 | Utilisateurs & rôles | Créer/modifier/désactiver des utilisateurs, gérer rôles et permissions par société/projet/module. | Must (P1) | Un admin peut créer un utilisateur, lui attribuer un rôle, déclencher un email d’activation ; accès refusé si rôle insuffisant. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L356–L359] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L72–L77] |
| FR-002 | Multi-sociétés / multi-projets | Créer des sociétés et des projets, filtrer et consolider les données pour la direction. | Must (P1) | Un utilisateur autorisé peut lister/éditer sociétés & projets ; la direction consulte une vue consolidée multi-sociétés. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L360–L363] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L26–L27] |
| FR-003 | Commercial | Gérer prospects/contacts avec historique et relances. | Must (P1) | CRUD prospect ; historique accessible ; changements d’étape pipeline tracés. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L365] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L470–L483] |
| FR-004 | Commercial | Gérer lots (prix, disponibilité, typologie) + statuts. | Must (P1) | CRUD lot ; changement de statut ; filtres sur disponibilité. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L366] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L474–L482] |
| FR-005 | Commercial | Pipeline commercial configurable (étapes personnalisables). | Must (P1) | Admin/PO peut définir étapes ; commercial peut déplacer un prospect entre étapes ; audit des changements. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L367] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L477] |
| FR-006 | Commercial | Réservations : validation + génération automatique de documents (PDF). | Must (P1) | Créer réservation sur un lot disponible ; générer PDF ; le lot passe dans un statut réservé. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L368] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L490–L503] |
| FR-007 | Dashboards | Tableaux de bord MVP (ventes, prospects actifs, lots restants, synthèse direction). | Must (P1) | Dashboard affiche indicateurs ; filtres par société/projet ; lecture par direction. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L380–L383] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L498–L502] |
| FR-008 | Foncier | Base terrains + pipeline acquisition + calcul COS/CES. | Must (P1) | Créer fiche terrain ; calcul COS/CES ; progression étapes pipeline ; validation direction. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L370–L374] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L509–L518] |
| FR-009 | Administratif | Workflow autorisations (Maroc+UE) + alertes + archivage docs. | Must (P1) | Suivi étapes ; pièces jointes archivées ; alertes sur échéances/retards ; notification direction. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L375–L379] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L169–L172] |
| FR-010 | Construction | Planning Gantt + suivi phases + journal chantier. | Should (P2) | Chef chantier met à jour avancement ; ajout photo ; incident ; reporting KPIs. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L387–L390] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L125–L134] |
| FR-011 | Stocks chantier | Entrées/sorties, QR/NFC, transferts, alertes ruptures, inventaires. | Should (P2) | Scan QR ; mise à jour inventaire ; alerte rupture ; inventaire enregistré. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L391–L394] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L45–L49] |
| FR-012 | Achats & fournisseurs | DA→Validation→BC→Réception→Facturation + rapprochement BC/BL/Facture. | Should (P2) | Création DA ; validation hiérarchique ; BC ; réception ; facture rapprochée avant paiement. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L395–L399] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L159–L162] |
| FR-013 | Automatisations | Rappels automatisés + scénarios marketing/opérationnels. | Should (P2) | Définir règles de rappel (ex. relance prospect) ; notifications envoyées ; journalisation. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L400–L402] |
| FR-014 | Finance complet | Prévisionnel vs réalisé, marges par projet, export comptable avancé. | Could (P3) | Saisie/ingestion dépenses ; calcul marges ; exports ; rapports direction. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L406–L409] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L174–L181] |
| FR-015 | Qualité & sécurité chantier | Checklists + signalements + conformité. | Could (P3) | Créer checklist ; enregistrer signalement ; statut conformité consultable. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L410–L413] |
| FR-016 | Sous-traitants | Documents légaux + notation + historique. | Could (P3) | Fiche sous-traitant avec documents ; notation ; historique interventions. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L414–L416] |
| FR-017 | SAV | Gestion de tickets, interventions, clôture. | Could (P3) | Ticket créé ; affectation ; intervention ; validation client ; clôture. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L417–L419] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L189–L192] |
| FR-018 | Intégrations externes | Intégrations (comptabilité, site web, partenaires) via API. | Could (P3) | Un mécanisme d’intégration existe (API/webhooks/exports) ; périmètre à préciser. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L420–L423] |


### 6.2 Endpoints / intégrations (vue métier)

> [OPEN POINT] Les endpoints détaillés ne sont pas présents dans les sources ; tableau ci-dessous = besoins minimaux (CRUD + actions) déduits des sprints.


| Domaine | Opérations minimales | Consommateurs | Sources |
| --- | --- | --- | --- |
| Sociétés/Projets | CRUD + listing filtré/consolidé | Admin, Direction | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L25–L28] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L451–L456] |
| Prospects/Lots | CRUD + pipeline transitions | Commercial | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L470–L475] |
| Réservations | Créer réservation + générer documents | Commercial | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L490–L503] |
| Terrains | CRUD + COS/CES + transitions pipeline | Foncier, Direction | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L509–L518] |
| Autorisations | CRUD étapes + docs + alertes | Admin, Direction | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L375–L379] |
| Achats | DA/BC/BL/Facture + rapprochements | Achats/Finance | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L159–L162] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L55] |
| Intégrations externes | Export/Push vers comptabilité, etc. | Finance/DSI | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L420–L423] |


## 7. Règles métier

> Les règles ci-dessous sont dérivées de la description des cas d’usage et des workflows, sans ajouter de règles non documentées.


| BR-ID | Règle | Portée | Sources |
| --- | --- | --- | --- |
| BR-001 | Les permissions sont contrôlées par rôle **et** par société/projet/module. | Sécurité / gouvernance | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L27–L28] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L359] |
| BR-002 | Un lot ne peut être réservé que s’il est disponible (statut à définir). | Commercial | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L35] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L368] |
| BR-003 | La réservation déclenche la génération de documents (au minimum PDF). | Commercial | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L490–L503] |
| BR-004 | Le pipeline commercial suit une séquence structurée (qualification → … → appels de fonds). | Commercial | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L120–L123] |
| BR-005 | Le pipeline foncier suit une séquence structurée (repérage → … → décision). | Foncier | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L104–L107] |
| BR-006 | Toute autorisation suit des étapes réglementaires et doit générer des alertes sur échéances/retards. | Administratif | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L375–L378] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L169–L172] |
| BR-007 | Un achat suit DA→validation→BC→réception→facturation ; le rapprochement BC/BL/facture est requis avant règlement. | Achats | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L159–L162] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L55] |
| BR-008 | Les mouvements de stock doivent mettre à jour l’inventaire et notifier le chantier (notification interne). | Stocks | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L145–L147] |
| BR-009 | Un ticket SAV doit passer par diagnostic/intervention/validation client avant clôture. | SAV | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L189–L192] |


## 8. Workflows & machines d’états

### 8.1 Table des workflows (vue opérationnelle)

| WF-ID | Workflow | États / étapes | Déclencheurs | Effets attendus | Sources |
| --- | --- | --- | --- | --- | --- |
| WF-001 | Gestion utilisateurs (avancé) | Demande RH → Validation DSI → Création compte → Email activation → Activation | Demande RH, validation DSI, clic activation | Compte créé, rôle attribué, traçabilité | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L82–L85] |
| WF-002 | Foncier (avancé) | Repérage → Contact propriétaire → Collecte docs → Étude technique → Étude financière → Négociation → Décision | Changement d’étape | Historique, décision validée/refusée | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L104–L107] |
| WF-003 | Commercial (avancé) | Prospect → Qualification → Proposition → Négociation → Réservation → Signature → Appels de fonds | Changement d’étape, confirmation client | Docs générés, lot réservé, KPI mis à jour | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L120–L123] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L490–L503] |
| WF-004 | Construction (avancé) | Ordre de service → Installation → Gros œuvre → Second œuvre → Finitions → Réception technique | MAJ avancement | KPIs chantier mis à jour, journal enrichi | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L135–L138] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L125–L134] |
| WF-005 | Stocks | Livraison → Contrôle qualité → Stockage → MAJ CRM → Notification chantier | Réception livraison, scan QR | Inventaire MAJ, alerte/notification | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L145–L147] |
| WF-006 | Achats | DA → Validation → Consultation fournisseurs → BC → Réception → Facturation | Création DA, validation, réception, facture | Traçabilité DA/BC/facture, rapprochement | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L159–L162] |
| WF-007 | Administratif | Dépôt dossier → Compléments → Commissions → Observations → Validation finale → Autorisation | Ajout étape, upload doc | Archivage, alertes échéances, notification direction | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L169–L172] |
| WF-008 | Finance | Dépenses → Enregistrements → Consolidation → Analyse → Reporting direction | Saisie dépenses, clôture période | Rapports, marges, exports | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L179–L182] |
| WF-009 | SAV | Ticket → Diagnostic → Intervention → Validation client → Clôture | Création ticket, affectation, validation client | Historique SAV, indicateurs | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L189–L192] |


### 8.2 Machines d’états (tableaux)

#### 8.2.1 Statut Lot (proposition)

> [OPEN POINT] Le CDC mentionne des statuts de lots mais ne fixe pas la liste ni les transitions.


| État | Entrée | Sortie | Notes | Sources |
| --- | --- | --- | --- | --- |
| Disponible | Création lot / remise en vente | Réservé | Transition déclenchée par création réservation | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L35] |
| Réservé | Création réservation | Signé / Annulé | Signature contrat ou annulation | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L368] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L120–L123] |
| Signé | Signature | En livraison (option) | Selon process de construction/livraison | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L120–L123] |


## 9. Exigences non-fonctionnelles (vue métier)

| NFR-ID | Exigence | Critères | Sources |
| --- | --- | --- | --- |
| NFR-001 | SaaS cloud accessible multi-support | Accès web ; environnements dev/staging/prod mis en place | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L18–L19] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L435–L436] |
| NFR-002 | Sécurité : authentification forte & chiffrement | Contrôle d’accès + chiffrement en transit/au repos (à préciser) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L643–L647] |
| NFR-003 | Sauvegardes automatiques | Backups automatisés ; restauration testée | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L643–L647] |
| NFR-004 | Conformité RGPD (Maroc + UE) | Traçabilité, gestion des accès, principes RGPD à appliquer | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L21–L22] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L643–L647] |
| NFR-005 | Traçabilité & audits | Journalisation actions clés ; audit exportable | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L643–L647] |
| NFR-006 | Intégrations futures | Capacité d’intégration comptabilité/site/outils partenaires | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L21] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L420–L423] |


## 10. KPIs & reporting

- Commercial : taux de conversion, activité commerciale ; ventes, prospects actifs, lots restants. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L369] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L498]
- Projets : lots vendus/restants + avancement administratif (synthèse direction). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L382–L383]
- Finance : prévisionnel vs réalisé ; marges par projet/lot/bâtiment. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L60–L62] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L406–L408]


## 11. Roadmap (confirmée vs ouverte)

### 11.1 Sprints et jalons confirmés (texte)

Jalon explicitement daté : **Livraison MVP : 26 mai 2025**. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L632–L634] [SRC: Cahier Des Charges Crm V2.docx | L266]


| Sprint | Dates | Portée (résumé) | Statut | Sources |
| --- | --- | --- | --- | --- |
| S1 | 03/03 → 16/03 | Architecture & fondations + début module utilisateurs | Confirmé (texte) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L431–L444] |
| S2 | 17/03 → 30/03 | Multi-sociétés & multi-projets + API gestion projets | Confirmé (texte) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L451–L456] |
| S3 | 31/03 → 13/04 | Prospects & lots (CRUD+API) + pipeline commercial | Confirmé (texte) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L470–L483] |
| S4 | 14/04 → 27/04 | Réservations + génération PDF + dashboard commercial | Confirmé (texte) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L490–L502] |
| S5 | 28/04 → 11/05 | Prospection foncière + pipeline + COS/CES | Confirmé (texte) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L509–L518] |
| S6 | 12/05 → 25/05 | Workflow administratif (Maroc+UE) | Confirmé (texte) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L529–L533] |
| MVP | 26/05/2025 | Mise en production MVP | Confirmé (texte) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L632–L634] |
| S7+ | Post-MVP | Construction, stocks, achats, finance | Confirmé (texte) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L635–L639] |


### 11.2 Roadmap visuelle (image) — non réconciliée

- **roadmap.png** est une source de support, mais son contenu n’est pas lisible/extractible automatiquement dans l’état. → À confirmer manuellement. [SRC: roadmap.png | L1]


## 12. Implémenté vs planifié

- Les sources fournies décrivent des objectifs, périmètres et plans de livraison (MVP + sprints). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L350–L534]
- Aucun dépôt de code / preuve de mise en production n’est inclus dans le ZIP : l’état « Implémenté » ne peut pas être affirmé. [OPEN POINT]


## 13. Incohérences, conflits & résolutions

### 13.1 Conflits entre sources

Aucun conflit majeur détecté entre les deux CDC fournis sur : périmètre MVP, priorisation P1/P2/P3 et date de livraison MVP (les deux sources citent le 26 mai 2025). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L632–L634] [SRC: Cahier Des Charges Crm V2.docx | L266]


### 13.2 Zones de divergence de détail

- Certains diagrammes (UML/roadmap) ne sont pas exploitables automatiquement et ne peuvent pas être utilisés comme base unique. [SRC: uml_usecases_global_pro2.png | L1] [SRC: roadmap.png | L1]


## 14. Points ouverts & décisions attendues

### 14.1 Modèle RBAC exact

[OPEN POINT] Le CDC cite des rôles (admin, direction, commercial, technique) mais des acteurs supplémentaires apparaissent dans les workflows (chef chantier, foncier, SAV, DAF…). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L358–L359] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L125–L133]
- Option A : rôles stricts (4 rôles) + permissions fines par module.
- Option B : rôles métier dédiés (Foncier, Achats, Stock, Finance, SAV, Chantier) + héritage.
- **Recommandé : Option B** (réduction des exceptions, meilleure séparation des responsabilités).


### 14.2 Statuts normalisés (lots, prospects, tickets)

[OPEN POINT] Les documents mentionnent des statuts/pipelines mais pas les listes exhaustives ni toutes transitions. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L35–L36] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L189–L192]
- Option A : liste minimale alignée sur les workflows avancés (recommandé pour MVP).
- Option B : statuts plus granulaires dès V1 (risque complexité).
- **Recommandé : Option A**.


### 14.3 Modèle des documents générés et des templates

[OPEN POINT] « Génération PDF » est mentionnée mais les templates (réservation/contrat) et signatures ne sont pas spécifiés. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L490–L503] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L37]
- Option A : templates PDF statiques (HTML→PDF) pour MVP.
- Option B : moteur de templates paramétrable + versionning.
- **Recommandé : Option A** pour MVP, Option B en V2.


## 15. Glossaire

| Terme | Définition (dans CRM-HLM) | Sources |
| --- | --- | --- |
| Société | Entité juridique gérée dans le CRM ; base de consolidation et d’accès. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L25–L27] |
| Projet | Programme immobilier rattaché à une société. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L26–L27] |
| Lot | Unité commercialisable (appartement, etc.) avec prix et statut. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L35–L36] |
| Prospect | Contact commercial en cours de qualification/négociation. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L34–L36] |
| Réservation | Engagement initial du client sur un lot, avec documents générés. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L368] |
| DA / BC / BL | Demande d’achat / Bon de commande / Bon de livraison (rapprochement). | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L151–L162] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L55] |
| COS/CES | Indicateurs urbanistiques utilisés pour la constructibilité. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L31] |
| Ticket SAV | Réclamation client post-livraison, traitée via un workflow. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L184–L192] |


## 16. Sources

- CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx

- Cahier Des Charges Crm V2.docx

- erd_detail_pro2.png

- uml_usecases_global_pro2.png

- roadmap.png

- architecture_technique_real.png

- bpmn_vente_ultra_pro.png

- bpmn_achat_ultra_pro.png

- bpmn_sav_pro.png


## 17. Matrice de traçabilité (Feature/Rule → Source)

| Élément | Référence | Sources |
| --- | --- | --- |
| Gestion utilisateurs & rôles | FR-001 / BR-001 / WF-001 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L68–L84] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L356–L359] |
| Multi-sociétés / multi-projets | FR-002 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L25–L28] |
| Commercial (prospects, lots, pipeline, réservations) | FR-003→FR-006 / WF-003 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L364–L369] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L120–L123] |
| Dashboards essentiels | FR-007 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L380–L383] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L498–L502] |
| Foncier (terrains, COS/CES) | FR-008 / WF-002 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L370–L374] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L104–L107] |
| Administratif (autorisations, alertes) | FR-009 / WF-007 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L375–L379] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L169–L172] |
| Achats (DA→BC→Facture) | FR-012 / BR-007 / WF-006 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L150–L162] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L55] |
| Stocks chantier | FR-011 / WF-005 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L45–L49] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L145–L147] |
| Chantier (Gantt, journal) | FR-010 / WF-004 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L387–L390] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L135–L138] |
| Finance & marges | FR-014 / WF-008 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L59–L64] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L179–L182] |
| SAV | FR-017 / WF-009 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L184–L192] [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L189–L192] |
| Sécurité & conformité | NFR-002→NFR-005 | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L643–L647] |
| Livraison MVP | Jalon | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L632–L634] [SRC: Cahier Des Charges Crm V2.docx \| L266] |
