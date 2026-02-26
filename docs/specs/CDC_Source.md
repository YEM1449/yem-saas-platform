# Cahier des Charges — Source (DOCX → Markdown)

This file is a direct text extraction of `CDC_Premium_Final_Styled.docx` for traceability.

----

Cahier des Charges – CRM Promotion & Construction
Version Premium – Mise en page Tech SaaS Moderne
Client : HLM’s Corp

Sommaire

Cahier des Charges – Version Intégrale
# Cahier des Charges – Solution CRM Intégrée pour Promoteur & Constructeur Immobilier

Introduction

Ce document présente le cahier des charges complet d’une solution CRM pensée spécifiquement pour les besoins d’un groupe œuvrant dans la promotion immobilière, la construction et la gestion multi-projets. L’objectif est de mettre à disposition un outil moderne, intuitif et parfaitement aligné avec les processus métiers du secteur, tout en offrant une vision globale et fiable pour la prise de décision.

La solution proposée couvre l’ensemble du cycle de vie d’un projet immobilier : prospection foncière, études et autorisations, commercialisation, construction, achats, logistique, suivi administratif, gestion financière et service après-vente. Elle est conçue pour être évolutive, hébergée dans le cloud et conforme aux exigences réglementaires du Maroc et de l’Union Européenne.

---

1. Objectifs de la Solution

1.1 Objectifs Métier

* Centraliser les données et standardiser les processus internes.
* Améliorer la qualité du suivi commercial et renforcer la performance des équipes.
* Optimiser la coordination entre les directions promotion, technique, administrative et financière.
* Fluidifier le pilotage des chantiers et la gestion logistique.
* Structurer les démarches réglementaires et anticiper les échéances.
* Offrir une visibilité consolidée à la direction pour faciliter les décisions stratégiques.

1.2 Objectifs Techniques

* Proposer une solution SaaS hébergée dans le cloud, accessible depuis tout support.
* Garantir la sécurité et la confidentialité des données.
* Permettre l’intégration avec les systèmes existants (comptabilité, ERP, site web…).
* Assurer une conformité totale au RGPD (Maroc + UE).

---

2. Périmètre Fonctionnel

2.1 Gestion Multi-Sociétés & Multi-Projets

* Administration de plusieurs sociétés et suivi indépendant des projets.
* Consolidation des données pour une vision globale.
* Gestion des droits d'accès par profil, société et projet.

2.2 Prospection Foncière

* Base terrains : propriétaires, titres, surfaces, contraintes.
* Calculs COS/CES automatiques.
* Pipeline d’acquisition : prospection → analyse → négociation → signature.

2.3 Module Commercial

* Gestion des prospects et contacts.
* Catalogue et suivi des lots : statuts, prix, disponibilités.
* Pipeline de vente personnalisable.
* Documents générés automatiquement (réservations, contrats, appels de fonds).
* Envoi SMS et email depuis le CRM.

2.4 Suivi Construction

* Planning Gantt chantier détaillé.
* Avancement par phase (gros œuvre, second œuvre, finitions).
* Journal de chantier (photos, incidents, remarques).
* Gestion qualité, sécurité et conformité.
* Suivi des sous-traitants.

2.5 Gestion des Stocks Chantier

* Entrées / sorties de matériaux avec QR/NFC.
* Transferts inter-chantiers.
* Alerte sur les ruptures.
* Inventaires automatisés.

2.6 Achats & Logistique

* Demandes d’achats.
* Validation hiérarchique.
* Suivi des bons de commande.
* Gestion fournisseurs.
* Rapprochements BC / BL / Facture.

2.7 Module Administratif (Maroc – Union Européenne)

* Suivi complet des étapes d’autorisations officielles.
* Archivage documentaire.
* Alertes réglementaires.

2.8 Finance & Contrôle de Gestion

* Suivi des coûts prévisionnels/réalisés.
* Marges par bâtiment, lot ou projet.
* Export comptable.
* Suivi des encaissements.

---

2.9 Détails des Fonctionnalités & Cas d’Usage (Use Cases)

Cette section apporte une vision approfondie de chaque fonctionnalité, illustrée par des scénarios d’utilisation réels et complétée par des schémas UML/BPMN et workflows opérationnels avancés.

2.9.1 Gestion des Utilisateurs & Rôles

**Description :** Administration des comptes et des permissions.

**Comment l’utilisateur l’utilise :**

* L’administrateur crée, modifie et désactive les comptes.
* Il attribue ou retire des rôles spécifiques.

**Use Case : Création d’un utilisateur**

1. Accès au module « Gestion Utilisateurs ».
2. Saisie des informations du collaborateur.
3. Attribution du rôle.
4. Envoi automatique d’un email d’activation.

**Schéma UML (activité) – Gestion utilisateurs :** *(à intégrer en visuel PNG dans la version exportée)*

```
[Admin] → (Créer Utilisateur) → [Système enregistre] → (Activer Compte) → [Utilisateur]
```

**Workflow avancé :**

```
Demande RH → Validation DSI → Création Compte → Attribution Rôles → Notification
```

---

2.9.2 Prospection Foncière

**Description :** Identification et analyse des terrains.

**Comment l’utilisateur l’utilise :**

* Le responsable ajoute une fiche terrain.
* Il renseigne documents, surfaces, contraintes.

**Use Case : Analyse d’un terrain**

1. Import du titre foncier.
2. Calcul automatique COS/CES.
3. Enregistrement des négociations.
4. Validation par la direction.

**UML – Diagramme de Séquence : Analyse foncière**

```
Utilisateur → CRM : Ouvre fiche terrain
CRM → Service Urbanisme : Calcule COS/CES
Service Urbanisme → CRM : Résultats
CRM → Direction : Notification
```

**Workflow foncier avancé :**

```
Repérage → Contact propriétaire → Collecte documents → Étude technique → Étude financière → Négociation → Décision
```

---

2.9.3 Commercialisation & Gestion des Lots

**Description :** Gestion opérationnelle des ventes.

**Use Case : Vente d’un lot**

1. Le commercial identifie le prospect.
2. Il propose un lot disponible.
3. Le client confirme.
4. Le CRM génère le contrat.

**BPMN – Processus de vente :**

```
(Début) → [Recherche Prospect] → [Choix du Lot] → {Validation Client} → [Génération Contrat] → (Fin)
```

**Workflow commercial avancé :**

```
Prospect → Qualification → Proposition → Négociation → Réservation → Signature → Appels de fonds
```

---

2.9.4 Suivi Construction

**Description :** Suivi complet du chantier.

**Use Case : Mise à jour de l’avancement**

1. Mise à jour du Gantt.
2. Ajout photos.
3. Gestion incidents.

**UML – Diagramme d’activités chantier :**

```
[Chef chantier] → (Ouvrir Planning) → (Déclarer Avancement) → (Ajouter Photo) → [CRM met à jour les KPIs]
```

**Workflow chantier avancé :**

```
Ordre de Service → Installation chantier → Gros œuvre → Second œuvre → Finitions → Réception technique
```

---

2.9.5 Stocks et Logistique

**Use Case : Entrée en stock**

1. Scan QR.
2. Validation quantité.
3. Mise à jour inventaire.

**BPMN – Processus gestion stock :**

```
Livraison → {Contrôle qualité} → Stockage → Mise à jour CRM → Notification chantier
```

---

2.9.6 Achats et Fournisseurs

**Use Case : Demande d’achat**

1. Création DA.
2. Validation hiérarchique.
3. Transformation en BC.

**UML – Diagramme de Classe Achats :**

```
DA → BC → Facture → Fournisseur → Article
```

**Workflow Achats avancé :**

```
DA → Validation → Consultation fournisseurs → BC → Réception → Facturation
```

---

2.9.7 Suivi Administratif

**Use Case : Mise à jour d’une autorisation**

1. Ajout étape.
2. Archivage du document.
3. Notification direction.

**Workflow Administratif détaillé :**

```
Dépôt dossier → Compléments → Commissions → Observations → Validation finale → Autorisation
```

---

2.9.8 Module Financier

**Use Case : Calcul des marges**

1. Consultation coûts réels.
2. Comparaison prévisionnel / réalisé.
3. Génération rapport.

**BPMN – Processus suivi financier :**

```
Dépenses → Enregistrements → Consolidation → Analyse → Reporting direction
```

---

2.9.9 Service Après-Vente (SAV)

**Use Case : Traitement ticket SAV**

1. Déclaration client.
2. Planification intervention.
3. Clôture.

**Workflow SAV :**

```
Ticket → Diagnostic → Intervention → Validation client → Clôture
```

---

2.9.2 Prospection Foncière

**Description :** Suivi complet des terrains identifiés.

**Comment l’utilisateur l’utilise :**

* Le responsable foncier ajoute un terrain.
* Il joint plans, photos, titres fonciers.
* Le système calcule automatiquement COS/CES.

**Use Case : Analyse d’un terrain**

1. Le responsable foncier crée une fiche terrain.
2. Il ajoute documents et contraintes urbanistiques.
3. Le système calcule la constructibilité.
4. La direction valide ou refuse l’étude.

---

2.9.3 Commercialisation & Gestion des Lots

**Description :** Vente des biens immobiliers via un pipeline structuré.

**Comment l’utilisateur l’utilise :**

* Le commercial ouvre une fiche prospect.
* Il propose un lot disponible.
* Il génère la réservation et transmet les documents.

**Use Case : Vente d’un lot**

1. Le commercial recherche un prospect dans le CRM.
2. Il ouvre le module “Lots” pour consulter les disponibilités.
3. Il propose un lot au client.
4. Le client confirme, le CRM génère automatiquement le contrat.

---

2.9.4 Suivi Construction

**Description :** Pilotage complet du chantier.

**Comment l’utilisateur l’utilise :**

* Le chef de chantier met à jour le planning.
* Il ajoute des photos et remonte les incidents.
* La direction technique visualise l’avancement.

**Use Case : Mise à jour de l’avancement**

1. Le chef de chantier ouvre le Gantt.
2. Il modifie le pourcentage d’avancement.
3. Il ajoute une photo du terrain.
4. Le système génère automatiquement un rapport.

---

2.9.5 Gestion des Stocks Chantier

**Description :** Gestion du matériel et des matériaux.

**Comment l’utilisateur l’utilise :**

* Le magasinier scanne un produit entrant.
* Le chef de chantier valide les sorties.

**Use Case : Entrée de stock**

1. Le magasinier scanne un QR code.
2. Il confirme la quantité réceptionnée.
3. Le stock global s’actualise.

---

2.9.6 Achats et Fournisseurs

**Description :** Suivi intégral des commandes.

**Comment l’utilisateur l’utilise :**

* Le chantier formule une demande d’achat.
* Le service Achats valide et crée un BC.

**Use Case : Demande d’achat**

1. Le chef de chantier crée une DA.
2. L’acheteur compare devis fournisseurs.
3. Il valide et envoie le BC.

---

2.9.7 Suivi Administratif

**Description :** Gestion des autorisations.

**Comment l’utilisateur l’utilise :**

* Le service administratif met à jour les étapes.
* La direction suit l’état des dossiers.

**Use Case : Mise à jour d’une autorisation**

1. Le chargé admin modifie une étape (ex : “Commission technique”).
2. Il joint les documents reçus.
3. Le système alerte la direction en cas de retard.

---

2.9.8 Module Financier

**Description :** Pilotage des coûts et marges.

**Comment l’utilisateur l’utilise :**

* Le DAF consulte les coûts réalisés.
* Il exporte les écritures vers la comptabilité.

**Use Case : Calcul des marges**

1. Le DAF ouvre le rapport financier.
2. Il consulte les coûts réels.
3. Le système calcule automatiquement la marge.

---

2.9.9 Service Après-Vente (SAV)

**Description :** Suivi des réclamations après livraison.

**Comment l’utilisateur l’utilise :**

* Le client déclare un ticket.
* Le SAV planifie une intervention.

**Use Case : Traitement d’un ticket SAV**

1. Le client soumet une réclamation.
2. Le service SAV affecte un technicien.
3. Le technicien clôture l’intervention.

---

3. Schémas Visuels Intégrés (Placeholders à compléter dans Canva / Draw.io)

> **Note :** Tous les schémas seront créés dans Canva ou Draw.io. Les emplacements ci-dessous servent de placeholders visuels à remplir lors de la phase de mise en forme.

3.1 Organigramme Fonctionnel — *PLACEHOLDER*

```
[INSÉRER ORGANIGRAMME ICI]
```

3.2 Workflow Général — *PLACEHOLDER*

```
[INSÉRER WORKFLOW ICI]
```

3.3 Cycle de Vie Projet — *PLACEHOLDER*

```
[INSÉRER DIAGRAMME CYCLE DE VIE ICI]
```

3.4 UML – Use Cases — *PLACEHOLDER*

```
[INSÉRER UML USE CASE ICI]
```

3.5 UML – Diagramme d’Activité — *PLACEHOLDER*

```
[INSÉRER UML ACTIVITÉ ICI]
```

3.6 UML – Séquence — *PLACEHOLDER*

```
[INSÉRER UML SÉQUENCE ICI]
```

3.7 BPMN – Processus Commercial — *PLACEHOLDER*

```
[INSÉRER BPMN VENTE ICI]
```

3.8 Workflow Chantier — *PLACEHOLDER*

```
[INSÉRER WORKFLOW CHANTIER ICI]
```

3.9 Architecture Technique — *PLACEHOLDER*

```
[INSÉRER ARCHITECTURE TECHNIQUE ICI]
```

3.10 Schéma Base de Données (ERD) — *PLACEHOLDER*

```
[INSÉRER SCHÉMA BASE DE DONNÉES ICI]
```

3.11 Architecture API — *PLACEHOLDER*

```
[INSÉRER ARCHITECTURE API ICI]
```

*(La version PNG sera générée séparément : organigramme, workflow, cycle projet, roadmap, UML, BPMN.)*

3.1 Organigramme Fonctionnel (version textuelle)

```
Direction Générale
│
├─ Promotion : Prospection foncière – Commercial – Marketing
├─ Technique : Bureau d’études – Travaux – Qualité & Sécurité
├─ Achats & Logistique : Achats – Stock & dépôts
└─ Finance : Comptabilité – Contrôle de gestion
```

3.2 Workflow Général

```
Prospection → Études & Autorisations → Commercialisation
→ Construction → Livraison → SAV
```

3.3 Cycle de Vie Projet

```
Acquisition foncière → Études → Pré-commercialisation → Vente
→ Construction → Livraison → SAV
```

---

4. Planification Agile & MVP

4.1 Philosophie Agile

La méthode Agile permet d’aboutir rapidement à un produit fonctionnel tout en gardant une grande flexibilité dans l’évolution du projet. Les retours des équipes opérationnelles sont intégrés à chaque étape.

4.2 Définition du MVP

Le MVP inclut : utilisateurs & rôles, multi-projets, CRM commercial, prospection foncière, workflow administratif simplifié et tableaux de bord essentiels.

4.3 Backlog Priorisé (version détaillée)

Le backlog est structuré pour prioriser les fonctionnalités essentielles au lancement (MVP), puis les modules nécessaires au fonctionnement complet de l’entreprise, et enfin les éléments évolutifs qui renforceront la productivité à long terme.

**P1 – Priorité Critique (MVP)**

Ces fonctionnalités constituent la colonne vertébrale du CRM et doivent être opérationnelles dès la première mise en production.

1. **Gestion des utilisateurs & rôles**

   * Création de comptes utilisateurs.
   * Paramétrage des rôles (admin, direction, commercial, technique).
   * Gestion fine des permissions par société, projet et module.

2. **Gestion multi-sociétés / multi-projets**

   * Création de sociétés avec identité propre.
   * Création, modification et suivi des projets.
   * Filtrage global et reporting consolidé.

3. **Module Commercial – version MVP**

   * Gestion des prospects : fiches, historique, relances.
   * Fiches lots : prix, disponibilité, typologie.
   * Pipeline commercial configurable.
   * Réservations : validation, documents générés automatiquement.
   * Indicateurs de performance : taux de conversion, activité commerciale.

4. **Prospection foncière**

   * Base terrains : propriétaires, titres, surfaces, localisation.
   * Suivi des opportunités foncières.
   * Pipeline acquisition : étapes de qualification → négociation → signature.
   * Analyse préliminaire du COS/CES.

5. **Workflow administratif simplifié (Maroc + UE)**

   * Suivi des dépôts de dossiers.
   * Statuts des autorisations.
   * Alertes sur les échéances ou retards.
   * Archivage des documents clés.

6. **Tableaux de bord essentiels**

   * Indicateurs commerciaux.
   * Suivi projets : lots vendus / restants, état d’avancement administratif.
   * Vue synthétique pour la direction.

---

**P2 – Priorité Haute (post-MVP)**

Modules nécessaires au fonctionnement complet des opérations internes.

7. **Module Construction – phase 1**

   * Planning Gantt de chantier.
   * Suivi par phase (gros œuvre, second œuvre, finitions).
   * Journal de chantier.

8. **Gestion des stocks sur chantier**

   * Entrées/sorties.
   * Scans QR/NFC.
   * Alertes et inventaires.

9. **Achats & fournisseurs**

   * Demandes d’achats.
   * Bons de commande.
   * Rapprochements BL/BC/Facture.
   * Historique fournisseurs.

10. **Automatisations & notifications avancées**

    * Rappels automatisés.
    * Scénarios marketing ou opérationnels.

---

**P3 – Priorité Moyenne / Évolutive**

Modules visant à enrichir la solution sur le long terme.

11. **Module Finance complet**

    * Prévisionnel vs réalisé.
    * Marges par projet.
    * Export comptable avancé.

12. **Qualité & sécurité chantier**

    * Checklists.
    * Signalements.
    * Conformité.

13. **Module Sous-traitants**

    * Documents légaux.
    * Notation et historique.

14. **SAV & gestion des tickets**

    * Déclarations clients.
    * Suivi interventions.

15. **Intégrations externes (API)**

    * Comptabilité.
    * Site web.
    * Outils partenaires.

---

4.4 Sprints (2 semaines) – Version détaillée

Chaque sprint est conçu pour livrer un incrément concret, utilisable et testable. Les livrables sont catégorisés en :

* **Livrables techniques** (architecture, développement, intégration)
* **Livrables métiers** (fonctionnalités utilisables, documents, workflows)
* **Tests & validation**
* **RACI** (Responsable – Acteur – Consulté – Informé)

**Sprint 1 — 03/03 → 16/03 — Architecture & Fondations**

**Objectifs :** Mise en place des bases techniques du futur CRM.

**Livrables techniques :**

* Architecture applicative validée.
* Mise en place des environnements (dev, staging, production).
* Configuration cloud & sécurité (authentification, accès, SSO si applicable).
* Base du module "Utilisateurs" (backend + début interface).

**Livrables métiers :**

* Structure initiale des rôles (Direction, Admin, Commercial, Technique...).
* Documentation des profils et permissions.

**Tests :**

* Connexion/déconnexion.
* Gestion des sessions.
* Test des rôles simples.

**RACI :**

* **R (Responsable)** : Architecte / Lead Dev
* **A (Acteur)** : Équipe technique
* **C (Consulté)** : Direction informatique / DSI
* **I (Informé)** : Direction générale

---

**Sprint 2 — 17/03 → 30/03 — Multi-sociétés & Multi-projets**

**Objectifs :** Permettre la gestion de plusieurs sociétés et projets.

**Livrables techniques :**

* Structures backend « société » et « projet ».
* API de gestion des projets.
* Interfaces de création/édition des sociétés et projets.

**Livrables métiers :**

* Fiches sociétés et projets.
* Paramètres administratifs (adresses, équipes, statuts...).

**Tests :**

* Création de plusieurs sociétés.
* Filtrage par société.
* Rattachement utilisateurs ↔ sociétés.

**RACI :**

* **R :** Lead Dev
* **A :** Dev Front & Back
* **C :** Direction Promotion + Direction Technique
* **I :** Direction générale

---

**Sprint 3 — 31/03 → 13/04 — Prospects & Lots**

**Objectifs :** Construction du cœur commercial.

**Livrables techniques :**

* Module prospects (CRUD complet + API).
* Fiches lots (backend + interface + filtres).
* Système de pipeline commercial.

**Livrables métiers :**

* Définition du pipeline commercial (étapes personnalisables).
* Fiches prospects opérationnelles.
* Catalogue dynamique des lots.

**Tests :**

* Ajout / modification de prospects.
* Mise à jour du statut d’un lot.
* Changement d’étape dans le pipeline.

**RACI :**

* **R :** Product Owner
* **A :** Équipe Dev
* **C :** Équipe commerciale
* **I :** Direction générale

---

**Sprint 4 — 14/04 → 27/04 — Réservations & Tableaux de bord MVP**

**Objectifs :** Rendre la commercialisation opérationnelle.

**Livrables techniques :**

* Module réservations (backend + génération PDF).
* Dashboard commercial.
* Améliorations UI/UX.

**Livrables métiers :**

* Fiche réservation complète.
* Indicateurs : ventes, prospects actifs, lots restants.

**Tests :**

* Réservation d’un lot.
* Génération des documents.
* Lecture des KPIs.

**RACI :**

* **R :** Product Owner
* **A :** Dev Front / Back
* **C :** Commercial + Direction
* **I :** DSI

---

**Sprint 5 — 28/04 → 11/05 — Prospection Foncière**

**Objectifs :** Intégrer la recherche et l’analyse foncière.

**Livrables techniques :**

* Module terrains.
* Système de pipeline foncier.
* Calcul automatique COS/CES.

**Livrables métiers :**

* Fiches terrains opérationnelles.
* Historique de négociations.
* Première version du tableau d’analyse.

**Tests :**

* Ajout d’un terrain.
* Passage d’une étape à l’autre.
* Calculs de faisabilité.

**RACI :**

* **R :** Product Owner
* **A :** Dev
* **C :** Direction Promotion
* **I :** Direction générale

---

**Sprint 6 — 12/05 → 25/05 — Workflow Administratif (Maroc + UE)**

**Objectifs :** Structurer les étapes réglementaires.

**Livrables techniques :**

* Module workflow.
* Système d’alertes.
* Archivage documentaire.

**Livrables métiers :**

* Process réglementaire paramétré.
* Dossiers projets enrichis.
* Alertes automatisées.

**Tests :**

* Ajout d’une étape.
* Passage automatique / manuel.
* Notifications.

**RACI :**

* **R :** Product Owner
* **A :** Équipe Dev
* **C :** Juridique / Administratif
* **I :** Direction générale

---

📌 Livraison MVP — 26 mai 2025

Le CRM est entièrement fonctionnel sur les modules clés : Commercial, Prospection, Projets, Workflow administratif, Tableaux de bord.

---

4.5 Sprints d’Évolution (post-MVP) – Version détaillée

Les sprints suivants étendent le périmètre fonctionnel vers les modules opérationnels et financiers.

**Sprint 7 — 26/05 → 08/06 — Module Construction (Phase 1)**

**Livrables techniques :**

* Mise en place du planning Gantt chantier.
* API d’avancement par phase.
* Interface chef de chantier.

**Livrables métiers :**

* Vue d’ensemble par bâtiment / étage.
* Journal de chantier opérationnel.

**Tests :**

* Mise à jour d’avancement.
* Création d’un journal.

**RACI :**

* R : Product Owner
* A : Dev Front/Back
* C : Direction Technique
* I : Direction Générale

---

**Sprint 8 — 09/06 → 22/06 — Stocks Chantier**

**Livrables techniques :**

* Module stocks complet.
* Gestion QR/NFC.

**Livrables métiers :**

* Suivi des consommations.
* Inventaires.

**Tests :**

* Entrées / sorties.
* Scan QR.

**RACI :**

* R : Lead Dev
* A : Équipe Dev
* C : Logistique / Achats
* I : Direction Technique

---

**Sprint 9 — 23/06 → 06/07 — Module Achats**

**Livrables techniques :**

* Demandes d’achats (DA).
* Bons de commande.
* Rapprochement BC/BL/Facture.

**Livrables métiers :**

* Base fournisseurs.
* Workflow de validation.

**Tests :**

* Création DA.
* Validation BC.

**RACI :**

* R : Product Owner
* A : Dev
* C : Achats
* I : DAF

---

**Sprint 10 — 07/07 → 20/07 — Finance & Contrôle de Gestion**

**Livrables techniques :**

* Module prévisionnel vs réalisé.
* Export comptable Maroc/UE.

**Livrables métiers :**

* Tableaux de bords financiers.
* Marges projet / bâtiment.

**Tests :**

* Simulation coûts.
* Génération export.

**RACI :**

* R : DAF
* A : Dev
* C : Direction Générale
* I : Auditeurs internes

---

4.7 Roadmap Visuelle

Une roadmap visuelle synthétique sera fournie au format PNG et inclura :

* Vision trimestrielle des livraisons,
* Dépendances entre modules,
* Jalons clés,
* Versions majeures (V1 – MVP, V2 – opérationnelle, V3 – avancée).

Le CRM est entièrement fonctionnel sur les modules clés : Commercial, Prospection, Projets, Workflow administratif, Tableaux de bord.
(2 semaines)

* **Sprint 1 :** architecture + utilisateurs.
* **Sprint 2 :** multi-sociétés + multi-projets.
* **Sprint 3 :** prospects + lots.
* **Sprint 4 :** réservations + tableaux de bord.
* **Sprint 5 :** prospection foncière.
* **Sprint 6 :** workflow administratif.

**→ Livraison MVP : 26 mai 2025**

4.5 Sprints Post-MVP

* **Sprint 7 :** construction (planning Gantt).
* **Sprint 8 :** stocks chantier.
* **Sprint 9 :** achats.
* **Sprint 10 :** finance.

4.6 Rituels Agile

Daily meeting, planning, review, retrospective, backlog refinement.

---

5. Conformité & Sécurité

* Authentification forte et chiffrement.
* Sauvegardes automatiques.
* Conformité RGPD (Maroc + UE).
* Traçabilité et audits.

---

6. Avantages pour le Client

* Solution pensée exclusivement pour les métiers immobiliers.
* Vision globale du foncier, du commercial, du chantier et des finances.
* Architecture cloud sécurisée et évolutive.
* Produit valorisable et revendable auprès d’autres promoteurs.

---

7. Livrables

* Application CRM opérationnelle.
* Documentation technique et fonctionnelle.
* Guides utilisateurs.
* Diagrammes visuels (PNG) : workflow, organigramme, cycle projet.
* Roadmap visuelle.
* Diagrammes UML & BPMN.

---

Annexes

Les éléments visuels seront générés à la demande :

* Organigramme (PNG)
* Workflow (PNG)
* Cycle projet (PNG)
* Roadmap (PNG)
* Diagrammes UML / BPMN (PNG)

---
