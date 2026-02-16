# CRM-HLM — Phase 1 : Document Intelligence (Document Map + Consolidation)

**Projet :** CRM-HLM (CRM Promotion & Construction)  
**Version :** 0.1 (Phase 1)  
**Date :** 16 février 2026  
**Auteur :** Senior Software Architect & Project Director  

## Table des matières

1. Source Inventory (Document Map)  
2. Vocabulaire unifié  
3. Consolidation Domaine & Modules  
   3.1 Liste consolidée des modules  
   3.2 Matrices de cross-reference (modules ↔ sources / entités / UI / API)  
4. Roadmap : extraction & réconciliation  
5. Incohérences, gaps & points ouverts  
6. Liste des sources utilisées  

---

## 1. Source Inventory (Document Map)

> Rappel : les documents textuels (DOCX) sont traités comme **sources primaires**. Les images/diagrammes (PNG) sont **support** uniquement : elles ne doivent pas être l’unique base d’une exigence.

| Nom | Type | But | Contributions | Confiance | Points clés extraits | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | DOCX (CDC) | Référence principale : périmètre, objectifs, cas d’usage, plan MVP/roadmap. | Modules métier, workflows, priorisation (MVP/post-MVP), exigences sécurité/conformité, livrables. | Haute | - Cycle de vie projet complet (foncier → autorisations → vente → construction → livraison → SAV). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L4–L5]<br>- Périmètre fonctionnel détaillé (multi-sociétés, foncier, commercial, chantier, stocks, achats, administratif, finance). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L18–L49]<br>- Use cases + workflows (ex : DA→BC→Facture ; autorisations ; ticket SAV). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L51–L245]<br>- Backlog priorisé P1/P2/P3 + sprints + date « Livraison MVP ». [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L302–L369]<br>- Conformité & sécurité (auth forte, chiffrement, backups, audit, RGPD). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L572–L575]<br>- Livrables attendus (application, docs, guides, diagrammes). [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L582–L587] | Le contenu textuel est identique à « CDC_Premium_Final_Styled (4).docx » (duplication avec/ sans diagrammes). |
| CDC_Premium_Final_Styled (4).docx | DOCX (CDC) | Copie de la référence principale (même contenu texte). | Redondant ; utile comme version alternative du même CDC. | Haute | - Contenu texte identique à CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx. | Fichier plus léger ; peut servir à comparer des exports/versions. |
| Cahier Des Charges Crm V2.docx | DOCX (CDC) | Version V2 plus courte (mêmes thèmes, moins détaillée). | Confirme les grands axes + certains jalons (Livraison MVP, sprints post-MVP) mais avec moins de détails que la version Premium. | Moyenne | - Confirme les modules principaux et la « Livraison MVP — 26 mai 2025 ». [SRC: Cahier Des Charges Crm V2.docx \| L204]<br>- Confirme les sprints post-MVP (stocks, achats, finance) avec livrables. [SRC: Cahier Des Charges Crm V2.docx \| L205–L206] | À utiliser pour corroboration ; privilégier la version Premium pour le détail. |
| erd_detail_pro2.png | Diagramme (ERD) | Vue d’ensemble des groupes d’entités (support). | Bornage data-domain : sociétés/projets/lots, prospects/clients/réservations, achats, stocks, foncier/chantier, finance, SAV. | Moyenne (support) | - Groupes d’entités explicités : SOCIETE/PROJET/LOT ; PROSPECT/CLIENT/RESERVATION ; DA/BC/FACTURE/FOURNISSEUR ; STOCK_MOUVEMENT/ARTICLE ; TERRAIN/CHANTIER ; ECRITURE_FINANCIERE ; TICKET_SAV. [SRC: erd_detail_pro2.png] | Support uniquement : doit être corroboré par textes/code pour être « vérité ». |
| uml_usecases_global_pro2.png | Diagramme (UML use cases) | Vue globale des capacités CRM (support). | Validation macro des modules (OCR partiel). | Basse (support) | - OCR n’a remonté que : « Suivi financier & marges », « SAV & réclamations clients ». [SRC: uml_usecases_global_pro2.png] | Texte interne non extractible de manière fiable (OCR). À revalider manuellement si besoin. |
| roadmap.png | Diagramme (roadmap) | Roadmap visuelle (support / hypothèse). | Hypothèses de séquencement (non corroborées automatiquement). | Basse (support) | - OCR n’a identifié que le titre « Roadmap ». [SRC: roadmap.png] | [OPEN POINT] Lecture manuelle requise pour extraire phases et jalons de l’image. |
| architecture_technique_real.png | Diagramme (architecture) | Schéma d’architecture technique conceptuel (support). | Confirme l’approche Frontend Web → API Backend → Business Layer → Database → Cloud Hosting. | Moyenne (support) | - Couches identifiées : Frontend Web, API Backend, Business Layer, Database, Cloud Hosting. [SRC: architecture_technique_real.png] | Ne fixe pas la stack (frameworks/DB/infra) : décision à prendre. |
| bpmn_vente_ultra_pro.png | Diagramme (BPMN) | Processus de vente (support). | Étapes vente + impacts CRM/Finance. | Moyenne (support) | - Étapes visibles : qualifier prospect → proposer lot → négocier ; établir échéancier → appel de fonds → encaissement ; mise à jour statut lot/indicateurs. [SRC: bpmn_vente_ultra_pro.png] | Support : le CDC texte reste la base (pipeline & workflow). |
| bpmn_achat_ultra_pro.png | Diagramme (BPMN) | Processus achats chantier (support). | Étapes DA/devis/BC/BL/facture + rapprochement + paiement. | Moyenne (support) | - Étapes visibles : exprimer besoin ; analyser devis ; créer BC ; livrer ; envoyer facture ; rapprocher BC/BL/facture ; régler facture. [SRC: bpmn_achat_ultra_pro.png] | Support : cohérent avec le workflow achat du CDC. |
| bpmn_sav_pro.png | Diagramme (BPMN) | Processus SAV (support). | Étapes ticket → intervention → clôture + MAJ CRM. | Moyenne (support) | - Étapes visibles : déclarer réclamation ; qualifier ticket ; planifier intervention ; intervenir ; rapport ; clôture ; MAJ historique/indicateurs. [SRC: bpmn_sav_pro.png] | Support : cohérent avec use case SAV du CDC. |
| architecture_technique.png | Image (PNG) | Diagramme/screenshot complémentaire (souvent version basse résolution ou variante). | Peut illustrer des workflows/organigrammes ; non exploité comme source primaire. | Basse (support) | - Non analysé en détail (doublon/miniature probable). [SRC: architecture_technique.png] | À réintégrer si des informations uniques y figurent. |
| bpmn.png | Image (PNG) | Diagramme/screenshot complémentaire (souvent version basse résolution ou variante). | Peut illustrer des workflows/organigrammes ; non exploité comme source primaire. | Basse (support) | - Non analysé en détail (doublon/miniature probable). [SRC: bpmn.png] | À réintégrer si des informations uniques y figurent. |
| bpmn_vente.png | Image (PNG) | Diagramme/screenshot complémentaire (souvent version basse résolution ou variante). | Peut illustrer des workflows/organigrammes ; non exploité comme source primaire. | Basse (support) | - Non analysé en détail (doublon/miniature probable). [SRC: bpmn_vente.png] | À réintégrer si des informations uniques y figurent. |
| cycle_projet (1).png | Image (PNG) | Diagramme/screenshot complémentaire (souvent version basse résolution ou variante). | Peut illustrer des workflows/organigrammes ; non exploité comme source primaire. | Basse (support) | - Non analysé en détail (doublon/miniature probable). [SRC: cycle_projet (1).png] | À réintégrer si des informations uniques y figurent. |
| cycle_projet.png | Image (PNG) | Diagramme/screenshot complémentaire (souvent version basse résolution ou variante). | Peut illustrer des workflows/organigrammes ; non exploité comme source primaire. | Basse (support) | - Non analysé en détail (doublon/miniature probable). [SRC: cycle_projet.png] | À réintégrer si des informations uniques y figurent. |
| logo_yem.png | Image (PNG) | Diagramme/screenshot complémentaire (souvent version basse résolution ou variante). | Peut illustrer des workflows/organigrammes ; non exploité comme source primaire. | Basse (support) | - Non analysé en détail (doublon/miniature probable). [SRC: logo_yem.png] | À réintégrer si des informations uniques y figurent. |
| organigramme.png | Image (PNG) | Diagramme/screenshot complémentaire (souvent version basse résolution ou variante). | Peut illustrer des workflows/organigrammes ; non exploité comme source primaire. | Basse (support) | - Non analysé en détail (doublon/miniature probable). [SRC: organigramme.png] | À réintégrer si des informations uniques y figurent. |
| uml.png | Image (PNG) | Diagramme/screenshot complémentaire (souvent version basse résolution ou variante). | Peut illustrer des workflows/organigrammes ; non exploité comme source primaire. | Basse (support) | - Non analysé en détail (doublon/miniature probable). [SRC: uml.png] | À réintégrer si des informations uniques y figurent. |
| workflow.png | Image (PNG) | Diagramme/screenshot complémentaire (souvent version basse résolution ou variante). | Peut illustrer des workflows/organigrammes ; non exploité comme source primaire. | Basse (support) | - Non analysé en détail (doublon/miniature probable). [SRC: workflow.png] | À réintégrer si des informations uniques y figurent. |
| workflow_chantier.png | Image (PNG) | Diagramme/screenshot complémentaire (souvent version basse résolution ou variante). | Peut illustrer des workflows/organigrammes ; non exploité comme source primaire. | Basse (support) | - Non analysé en détail (doublon/miniature probable). [SRC: workflow_chantier.png] | À réintégrer si des informations uniques y figurent. |
| workflow_global.png | Image (PNG) | Diagramme/screenshot complémentaire (souvent version basse résolution ou variante). | Peut illustrer des workflows/organigrammes ; non exploité comme source primaire. | Basse (support) | - Non analysé en détail (doublon/miniature probable). [SRC: workflow_global.png] | À réintégrer si des informations uniques y figurent. |


---

## 2. Vocabulaire unifié

Objectif : normaliser les termes métier observés, et signaler les divergences/termes attendus mais absents.

| Terme(s) observé(s) | Terme canonique | Notes | Sources |
| --- | --- | --- | --- |
| Société / Sociétés | Société | Unité de gouvernance/entité juridique ; support du multi-sociétés. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L18–L20]; [SRC: erd_detail_pro2.png] |
| Projet / Projets (immobilier) | Projet | Programme/opération immobilière suivie de bout en bout. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L5]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L309]; [SRC: erd_detail_pro2.png] |
| Opération (immobilière) | Projet | Le CDC emploie aussi « opération » ; normalisé sur « projet ». | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L5] |
| Lot / Lots | Lot | Unité commerciale vendable (appartement/local/parking, etc.). | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L25–L27]; [SRC: erd_detail_pro2.png] |
| Prospect / Prospects | Prospect | Lead commercial avant contractualisation. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L24]; [SRC: erd_detail_pro2.png] |
| Client | Client | Client final après conversion/vente ; lié aux réservations/contrats/SAV. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L93–L96]; [SRC: bpmn_vente_ultra_pro.png]; [SRC: erd_detail_pro2.png] |
| Réservation / Réservations | Réservation | Étape entre négociation et signature ; génère des documents. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L315–L316]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L103]; [SRC: erd_detail_pro2.png] |
| Contrat / Contrats | Contrat (vente) | Document généré lors de la vente/réservation. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L27]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L96] |
| Appel(s) de fonds | Appel de fonds | Événements de facturation/encaissement selon échéancier. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L27]; [SRC: bpmn_vente_ultra_pro.png] |
| Échéancier | Échéancier (paiement) | Planification des appels de fonds/encaissements (mentionnée en BPMN). | [SRC: bpmn_vente_ultra_pro.png] |
| Terrain / Terrains | Terrain | Objet foncier (titre, surface, contraintes, COS/CES). | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L21–L23]; [SRC: erd_detail_pro2.png] |
| COS/CES | COS/CES (règles urbanisme) | Calculs automatiques de constructibilité. [OPEN POINT] Formules et sources de données. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L22] |
| Chantier | Chantier | Exécution travaux ; planning Gantt, journal, phases. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L29–L33]; [SRC: erd_detail_pro2.png] |
| Gantt | Planning (Gantt) | Représentation de planning chantier. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L29] |
| Autorisation(s) | Autorisation administrative | Étapes d’autorisations officielles ; statut + alertes. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L43–L45]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L142–L148] |
| Dossier (administratif) | Dossier administratif | Dépôt/compléments/commissions/observations/validation finale. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L148] |
| DA | Demande d’achat (DA) | Demande interne initiant le cycle achats. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L38]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L129–L133]; [SRC: erd_detail_pro2.png] |
| BC | Bon de commande (BC) | Commande fournisseur ; issue d’une DA validée. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L40–L42]; [SRC: bpmn_achat_ultra_pro.png] |
| BL | Bon de livraison (BL) | Réception ; utilisé pour rapprochement BC/BL/Facture. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L42]; [SRC: bpmn_achat_ultra_pro.png] |
| Facture | Facture | Document fournisseur ; rapproché puis réglé. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L42]; [SRC: bpmn_achat_ultra_pro.png]; [SRC: erd_detail_pro2.png] |
| Fournisseur(s) | Fournisseur | Partenaire achat ; historisation. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L41]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L345]; [SRC: erd_detail_pro2.png] |
| Article | Article (catalogue achat/stock) | Item commandable/stockable (mentionné en UML classe achats). | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L135]; [SRC: erd_detail_pro2.png] |
| Stock / Inventaire / QR / NFC | Stock chantier | Gestion entrées/sorties, transferts, ruptures, inventaires. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L34–L37]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L206–L209]; [SRC: erd_detail_pro2.png] |
| Mouvement stock | Mouvement de stock | Entité ERD : STOCK_MOUVEMENT. [OPEN POINT] Règles de valorisation & types de mouvement. | [SRC: erd_detail_pro2.png] |
| Écriture financière | Écriture financière | Entité ERD : ECRITURE_FINANCIERE + export comptable. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L48]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L232]; [SRC: erd_detail_pro2.png] |
| Ticket SAV | Ticket SAV | Réclamation client post-livraison ; assignation technicien ; clôture. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L160–L166]; [SRC: bpmn_sav_pro.png]; [SRC: erd_detail_pro2.png] |
| Rôle(s) | Rôle | Profil (admin, direction, commercial, technique…) et droits associés. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L305–L307] |
| Permission(s) | Permission | Droits fins par société/projet/module. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L20]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L306–L307] |
| KPI / Indicateurs | Indicateur (KPI) | Mesures : conversion, activité, avancement, qualité, marges. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L316]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L330]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L113]; [SRC: bpmn_sav_pro.png] |
| Dépôt (financier) / Acompte | Acompte | [OPEN POINT] Termes non explicitement présents dans les sources fournies ; clarifier si l’acompte est distinct de la « réservation » ou des « appels de fonds ». | [OPEN POINT] (absent des sources) |
| Temp Client | Prospect ou Client | [OPEN POINT] « Temp Client » non présent ; décider du modèle (ex : Prospect → Client lors de la réservation/signature). | [OPEN POINT] (absent des sources) |
| Property | Lot | [OPEN POINT] Terme non observé ; si besoin multi-langue, normaliser « Property » → « Lot ».  | [OPEN POINT] (absent des sources) |
| Tenant | Société (tenant) | [OPEN POINT] Multi-tenancy : confirmer si « société » = « tenant » (isolation). | [OPEN POINT] (absent des sources) |
| Agent | Utilisateur / Commercial | [OPEN POINT] Terme non observé ; clarifier rôles (agent commercial vs utilisateur générique). | [OPEN POINT] (absent des sources) |


---

## 3. Consolidation Domaine & Modules

### 3.1 Liste consolidée des modules (textes d’abord, diagrammes ensuite)

| Module | Nom | Description | Workflows clés | Entités clés | UI/écrans connus | API/Intégrations connues | Sources |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MOD-01 | IAM & RBAC (Utilisateurs / Rôles / Permissions) | Gestion des comptes, rôles et permissions avec granularité par société/projet/module. | Demande RH → Validation DSI → Création compte → Attribution rôles → Notification. | Utilisateur, Rôle, Permission (implicites). | Module « Gestion Utilisateurs ». | [OPEN POINT] Endpoints d’auth/gestion utilisateurs non spécifiés. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L52–L67]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L18–L20]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L303–L307] |
| MOD-02 | Gestion multi-sociétés & multi-projets | Administration de plusieurs sociétés et suivi indépendant des projets avec consolidation pour la direction. | Création société/projet → affectation utilisateurs → filtres & reporting consolidés. | Société, Projet, (lien Projet↔Société). | [OPEN POINT] Écrans sociétés/projets non détaillés (mais requis). | [OPEN POINT] Tenancy/isolation non spécifiées. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L18–L20]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L307–L311] |
| MOD-03 | Prospection foncière (Terrains) | Base terrains + analyse constructibilité (COS/CES) + pipeline acquisition. | Repérage → Contact propriétaire → Collecte docs → Études (tech/fin) → Négociation → Décision. | Terrain, Document foncier, Propriétaire (implicite), Étude/évaluation (implicite). | [OPEN POINT] Écrans terrain/étude non listés explicitement. | [OPEN POINT] Service Urbanisme/calcul COS-CES : règles/paramètres à préciser. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L21–L23]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L70–L88]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L317–L322]; [SRC: erd_detail_pro2.png] |
| MOD-04 | Commercialisation (Prospects / Lots / Réservations / Contrats) | Gestion prospects/contacts, catalogue lots, pipeline de vente, génération documents (réservations/contrats/appels de fonds), communication SMS/email. | Prospect → Qualification → Proposition → Négociation → Réservation → Signature → Appels de fonds. | Prospect, Client, Lot, Réservation, Contrat, Appel de fonds (événement). | Module “Lots” (disponibilités) ; fiches prospect/lot. | SMS/email (provider non spécifié) ; [OPEN POINT] endpoints CRM. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L24–L28]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L91–L103]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L311–L317]; [SRC: bpmn_vente_ultra_pro.png]; [SRC: erd_detail_pro2.png] |
| MOD-05 | Suivi administratif & réglementaire (Maroc + UE) | Suivi des dossiers et étapes d’autorisations officielles, archivage documentaire, alertes réglementaires/retards. | Dépôt dossier → Compléments → Commissions → Observations → Validation finale → Autorisation. | Dossier administratif, Étape/Statut autorisation (implicite), Document (implicite). | [OPEN POINT] Écrans dossier/autorisation non listés explicitement. | Notifications/alertes (mécanisme non spécifié). | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L43–L45]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L142–L148]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L322–L327] |
| MOD-06 | Construction & chantier | Planning chantier (Gantt), avancement par phase, journal (photos/incidents), qualité/sécurité/conformité, sous-traitants. | Ordre de Service → Installation → Gros œuvre → Second œuvre → Finitions → Réception technique. | Chantier, Phase, Avancement, Journal, Photo, Incident, Sous-traitant (implicites). | Gantt chantier ; journal chantier. | [OPEN POINT] endpoints/rapports non spécifiés. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L29–L33]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L106–L117]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L333–L337]; [SRC: erd_detail_pro2.png] |
| MOD-07 | Stocks chantier & logistique | Entrées/sorties via QR/NFC, transferts inter-chantiers, alertes ruptures, inventaires. | Livraison → Contrôle qualité → Stockage → MAJ CRM → Notification chantier. | Article, Mouvement de stock, Stock, Inventaire (implicites). | Scan QR/NFC (UI magasinier). | [OPEN POINT] Modèle de valorisation stock & inventaires. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L34–L37]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L120–L126]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L337–L341]; [SRC: erd_detail_pro2.png] |
| MOD-08 | Achats & fournisseurs | Cycle achats de chantier : DA, validation hiérarchique, devis, BC, réception, facture, rapprochement BC/BL/Facture, historique fournisseurs. | DA → Validation → Consultation fournisseurs → BC → Réception → Facturation → Rapprochement → Paiement. | DA, BC, BL, Facture, Fournisseur, Article. | [OPEN POINT] Écrans DA/BC/fournisseurs non listés explicitement. | Intégration comptable potentielle (export écritures/achats) [OPEN POINT]. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L38–L42]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L129–L139]; [SRC: bpmn_achat_ultra_pro.png]; [SRC: erd_detail_pro2.png] |
| MOD-09 | Finance & contrôle de gestion | Suivi coûts prévisionnels/réalisés, marges par lot/projet, suivi encaissements, export comptable. | Dépenses → Enregistrements → Consolidation → Analyse → Reporting direction. | Écriture financière, Coût, Marge, Encaissement (implicites). | Rapport financier / tableaux de bord direction. | Export comptable ; intégrations compta/ERP [OPEN POINT]. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L46–L49]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L151–L157]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L351–L355]; [SRC: erd_detail_pro2.png] |
| MOD-10 | SAV & tickets | Gestion des réclamations clients post-livraison : ticket, qualification, planification intervention, clôture, MAJ historique/indicateurs. | Ticket → Diagnostic → Intervention → Validation client → Clôture. | Ticket SAV, Intervention, Rapport (implicites). | [OPEN POINT] Portail client vs saisie interne ; écrans ticket non décrits. | [OPEN POINT] SLA/notifications/assignation à détailler. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L160–L166]; [SRC: bpmn_sav_pro.png]; [SRC: erd_detail_pro2.png] |
| MOD-11 | Reporting & tableaux de bord | Tableaux de bord essentiels (commercial, lots vendus/restants, avancement administratif, vue direction). | Agrégation KPI → vues par société/projet → export/partage [OPEN POINT]. | Indicateur/KPI, agrégats (implicites). | Tableaux de bord (direction + opérationnel). | [OPEN POINT] Spécification des KPIs et des sources de calcul. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L327–L330]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L316]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L552–L559] |
| MOD-12 | Automatisations & notifications | Rappels et scénarios marketing/opérationnels ; alertes (stock, administratif). | Événement (retard/rupture/échéance) → règle → notification → suivi. | Notification, Règle, Événement (implicites). | [OPEN POINT] Centre notifications, templates, canaux. | SMS/email ; notifications in-app. [OPEN POINT] | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L325–L327]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L346–L349] |
| MOD-13 | Intégrations externes & API | Intégration avec systèmes existants (comptabilité, ERP, site web…) et exposition API. | [OPEN POINT] Cas d’usage d’intégration à préciser (synchro prospects? écritures? stock?). | Connecteurs (implicites), objets métiers exportés. | [OPEN POINT] Portails / webhooks. | Comptabilité ; site web ; partenaires. {OPEN POINT} protocoles (REST/GraphQL/webhooks). | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L15]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L365–L368]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L279] |


### 3.2 Matrices de cross-reference

#### 3.2.1 Modules ↔ Sources

| Module | Nom | Sources principales |
| --- | --- | --- |
| MOD-01 | IAM & RBAC (Utilisateurs / Rôles / Permissions) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L52–L67]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L18–L20]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L303–L307] |
| MOD-02 | Gestion multi-sociétés & multi-projets | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L18–L20]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L307–L311] |
| MOD-03 | Prospection foncière (Terrains) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L21–L23]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L70–L88]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L317–L322]; [SRC: erd_detail_pro2.png] |
| MOD-04 | Commercialisation (Prospects / Lots / Réservations / Contrats) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L24–L28]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L91–L103]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L311–L317]; [SRC: bpmn_vente_ultra_pro.png]; [SRC: erd_detail_pro2.png] |
| MOD-05 | Suivi administratif & réglementaire (Maroc + UE) | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L43–L45]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L142–L148]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L322–L327] |
| MOD-06 | Construction & chantier | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L29–L33]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L106–L117]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L333–L337]; [SRC: erd_detail_pro2.png] |
| MOD-07 | Stocks chantier & logistique | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L34–L37]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L120–L126]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L337–L341]; [SRC: erd_detail_pro2.png] |
| MOD-08 | Achats & fournisseurs | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L38–L42]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L129–L139]; [SRC: bpmn_achat_ultra_pro.png]; [SRC: erd_detail_pro2.png] |
| MOD-09 | Finance & contrôle de gestion | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L46–L49]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L151–L157]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L351–L355]; [SRC: erd_detail_pro2.png] |
| MOD-10 | SAV & tickets | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L160–L166]; [SRC: bpmn_sav_pro.png]; [SRC: erd_detail_pro2.png] |
| MOD-11 | Reporting & tableaux de bord | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L327–L330]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L316]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L552–L559] |
| MOD-12 | Automatisations & notifications | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L325–L327]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L346–L349] |
| MOD-13 | Intégrations externes & API | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L15]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L365–L368]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L279] |


#### 3.2.2 Modules ↔ Entités / Tables (candidates)

| Module | Nom | Entités clés |
| --- | --- | --- |
| MOD-01 | IAM & RBAC (Utilisateurs / Rôles / Permissions) | Utilisateur, Rôle, Permission (implicites). |
| MOD-02 | Gestion multi-sociétés & multi-projets | Société, Projet, (lien Projet↔Société). |
| MOD-03 | Prospection foncière (Terrains) | Terrain, Document foncier, Propriétaire (implicite), Étude/évaluation (implicite). |
| MOD-04 | Commercialisation (Prospects / Lots / Réservations / Contrats) | Prospect, Client, Lot, Réservation, Contrat, Appel de fonds (événement). |
| MOD-05 | Suivi administratif & réglementaire (Maroc + UE) | Dossier administratif, Étape/Statut autorisation (implicite), Document (implicite). |
| MOD-06 | Construction & chantier | Chantier, Phase, Avancement, Journal, Photo, Incident, Sous-traitant (implicites). |
| MOD-07 | Stocks chantier & logistique | Article, Mouvement de stock, Stock, Inventaire (implicites). |
| MOD-08 | Achats & fournisseurs | DA, BC, BL, Facture, Fournisseur, Article. |
| MOD-09 | Finance & contrôle de gestion | Écriture financière, Coût, Marge, Encaissement (implicites). |
| MOD-10 | SAV & tickets | Ticket SAV, Intervention, Rapport (implicites). |
| MOD-11 | Reporting & tableaux de bord | Indicateur/KPI, agrégats (implicites). |
| MOD-12 | Automatisations & notifications | Notification, Règle, Événement (implicites). |
| MOD-13 | Intégrations externes & API | Connecteurs (implicites), objets métiers exportés. |


#### 3.2.3 Modules ↔ UI / Pages (si connues)

| Module | Nom | UI/écrans |
| --- | --- | --- |
| MOD-01 | IAM & RBAC (Utilisateurs / Rôles / Permissions) | Module « Gestion Utilisateurs ». |
| MOD-02 | Gestion multi-sociétés & multi-projets | [OPEN POINT] Écrans sociétés/projets non détaillés (mais requis). |
| MOD-03 | Prospection foncière (Terrains) | [OPEN POINT] Écrans terrain/étude non listés explicitement. |
| MOD-04 | Commercialisation (Prospects / Lots / Réservations / Contrats) | Module “Lots” (disponibilités) ; fiches prospect/lot. |
| MOD-05 | Suivi administratif & réglementaire (Maroc + UE) | [OPEN POINT] Écrans dossier/autorisation non listés explicitement. |
| MOD-06 | Construction & chantier | Gantt chantier ; journal chantier. |
| MOD-07 | Stocks chantier & logistique | Scan QR/NFC (UI magasinier). |
| MOD-08 | Achats & fournisseurs | [OPEN POINT] Écrans DA/BC/fournisseurs non listés explicitement. |
| MOD-09 | Finance & contrôle de gestion | Rapport financier / tableaux de bord direction. |
| MOD-10 | SAV & tickets | [OPEN POINT] Portail client vs saisie interne ; écrans ticket non décrits. |
| MOD-11 | Reporting & tableaux de bord | Tableaux de bord (direction + opérationnel). |
| MOD-12 | Automatisations & notifications | [OPEN POINT] Centre notifications, templates, canaux. |
| MOD-13 | Intégrations externes & API | [OPEN POINT] Portails / webhooks. |


#### 3.2.4 Modules ↔ Endpoints / API (si connus)

| Module | Nom | Endpoints/API |
| --- | --- | --- |
| MOD-01 | IAM & RBAC (Utilisateurs / Rôles / Permissions) | [OPEN POINT] Endpoints d’auth/gestion utilisateurs non spécifiés. |
| MOD-02 | Gestion multi-sociétés & multi-projets | [OPEN POINT] Tenancy/isolation non spécifiées. |
| MOD-03 | Prospection foncière (Terrains) | [OPEN POINT] Service Urbanisme/calcul COS-CES : règles/paramètres à préciser. |
| MOD-04 | Commercialisation (Prospects / Lots / Réservations / Contrats) | SMS/email (provider non spécifié) ; [OPEN POINT] endpoints CRM. |
| MOD-05 | Suivi administratif & réglementaire (Maroc + UE) | Notifications/alertes (mécanisme non spécifié). |
| MOD-06 | Construction & chantier | [OPEN POINT] endpoints/rapports non spécifiés. |
| MOD-07 | Stocks chantier & logistique | [OPEN POINT] Modèle de valorisation stock & inventaires. |
| MOD-08 | Achats & fournisseurs | Intégration comptable potentielle (export écritures/achats) [OPEN POINT]. |
| MOD-09 | Finance & contrôle de gestion | Export comptable ; intégrations compta/ERP [OPEN POINT]. |
| MOD-10 | SAV & tickets | [OPEN POINT] SLA/notifications/assignation à détailler. |
| MOD-11 | Reporting & tableaux de bord | [OPEN POINT] Spécification des KPIs et des sources de calcul. |
| MOD-12 | Automatisations & notifications | SMS/email ; notifications in-app. [OPEN POINT] |
| MOD-13 | Intégrations externes & API | Comptabilité ; site web ; partenaires. {OPEN POINT} protocoles (REST/GraphQL/webhooks). |


> **Note** : aucun dépôt de code n’étant présent dans l’archive fournie, les matrices « endpoints » et « routes UI » restent majoritairement en **[OPEN POINT]**.

---

## 4. Roadmap : extraction & réconciliation (Roadmap ≠ vérité)

| Phase / Jalon | Périmètre (scope) | Dépendances | Risques | Statut vs sources | Preuves |
| --- | --- | --- | --- | --- | --- |
| V1 — MVP (Livraison cible : 26 mai 2025) | Fondations + modules clés : utilisateurs/rôles, multi-sociétés/projets, commercial MVP (prospects/lots/pipeline/réservations), prospection foncière, workflow administratif simplifié, tableaux de bord essentiels. | MOD-01 à MOD-05 + MOD-11 ; socle data model minimal + RBAC. | Sous-spécification des écrans/états ; choix stack ; intégrations SMS/email ; règles COS/CES ; gestion documentaire. | Confirmé (CDC texte). | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L300–L330]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L559–L565] |
| V2 — Opérationnelle (post-MVP, sprints 7–10) | Extension opérations : construction (phase 1), stocks chantier, achats, finance & contrôle de gestion (premier niveau). | V1 livré ; référentiels projets/chantier ; articles/fournisseurs ; mécanismes de rapprochement et exports. | Couplage finance↔achats↔stock ; qualité des données ; définition des processus de validation. | Confirmé (CDC texte). | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L332–L349]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L566–L569] |
| V3 — Avancée (évolutifs) | Enrichissements long terme : finance complet, qualité/sécurité, sous-traitants, SAV & tickets, intégrations externes (API). | V2 ; structuration des référentiels + observabilité + sécurité renforcée. | Effort d’intégrations ; dette fonctionnelle si états/workflows non figés ; conduite du changement. | Confirmé (CDC texte) pour l’intention, mais contenu détaillé partiel → [OPEN POINT] sur le périmètre exact. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L350–L369]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L552–L556] |
| [OPEN POINT] Roadmap.png — extraction visuelle | Le contenu texte de l’image n’a pas pu être extrait automatiquement (OCR). | N/A | Risque d’écart entre la roadmap visuelle et le CDC texte. | Non supporté automatiquement ; requiert relecture manuelle. | [SRC: roadmap.png] |


### 4.1 Plan de livraison suggéré (aligné sur le scope confirmé)

- **V1 (MVP)** : livrer MOD-01 à MOD-05 + MOD-11, avec un modèle de données minimal (Société/Projet/Lot/Prospect) et RBAC multi-société. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L300–L330]
- **V2 (opérationnelle)** : ajouter MOD-06 à MOD-09 en stabilisant les référentiels (Article/Fournisseur/DA/BC/Facture/Stock) et les exports. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L332–L349]
- **V3 (avancée)** : intégrer MOD-10, approfondir Finance, Qualité/Sécurité, Sous-traitants, et mettre en place les intégrations externes. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L350–L369]

---

## 5. Incohérences, gaps & points ouverts

### 5.1 Conflits (avec proposition de résolution)

| ID | Conflit | Résolution recommandée | Impact | Sources |
| --- | --- | --- | --- | --- |
| CON-01 | Le périmètre global inclut le SAV (cycle de vie « … → Livraison → SAV »). vs Le backlog place « SAV & gestion des tickets » en P3 (évolutif). | Considérer SAV **hors MVP** mais **dans le scope produit** (V3). Option recommandée : livrer un « SAV minimal » (création/assignation/clôture) en V3, après stabilisation du modèle Client/Lot. | Roadmap, workflow, modèle de données (Ticket SAV), UI (portail client vs saisie interne), SLA/notifications. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L5]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L362–L365]; [SRC: erd_detail_pro2.png] |
| CON-02 | Le périmètre fonctionnel décrit la Finance (coûts, marges, export, encaissements). vs Le backlog classe « Module Finance complet » en P3 et le place en sprint 10 (post-MVP). | Découper Finance en **Niveau 1** (suivi encaissements + exports basiques) en V2, puis **Finance complet** en V3. Recommandation : figer dès V1 le modèle minimal (écritures/encaissements) pour éviter migrations lourdes. | Data model (écritures/encaissements), API reporting, intégration compta, migrations. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L46–L49]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L351–L355]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L569]; [SRC: erd_detail_pro2.png] |
| CON-03 | Section 2.9 « Prospection Foncière / Commercialisation / Construction… » est présente deux fois (contenu partiellement dupliqué). vs Risque de divergence future entre les deux copies si elles évoluent. | Dédupliquer : ne conserver qu’une version de chaque use case détaillé (ou versionner explicitement « v1/v2 »). | Documentation, traçabilité, implémentation (évite exigences contradictoires). | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L70–L88]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L169–L189] |


### 5.2 Points ouverts structurants (décisions manquantes)

| ID | Sujet | Options (1–3) | Recommandation | Évidence |
| --- | --- | --- | --- | --- |
| OP-01 | Stack technique (frontend/backend/DB) et normes | 1) Monolithe API (REST) + SPA Web<br>2) Backend modulaire + BFF + SPA<br>3) Microservices (à éviter en V1) | Option 1 ou 2. Reco : démarrer en monolithe modulaire (DDD light) pour livrer V1 rapidement, en gardant une séparation Domain/Application/Infra pour évoluer. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L13–L16]; [SRC: architecture_technique_real.png] |
| OP-02 | Modèle multi-sociétés : isolation (tenancy) | 1) Row-level security via `tenant_id` (société) + contrôle applicatif<br>2) Schéma par société<br>3) Base par société | Option 1 (row-level) en V1/V2 : plus simple à opérer, compatible consolidation. Ajouter contraintes et index `(tenant_id, ...)`. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L18–L20] |
| OP-03 | Authentification & gestion des sessions | 1) Auth email+mot de passe + MFA (TOTP/SMS)<br>2) SSO OAuth2/OIDC (Azure AD/Google, etc.)<br>3) SAML (grands comptes) | Option 1 en V1 (avec MFA si exigé) ; prévoir extension OIDC en V2/V3. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L572–L575] |
| OP-04 | Gestion documentaire (archivage dossiers, pièces foncières, contrats) | 1) Stockage objet (S3 compatible) + métadonnées en DB<br>2) Stockage DB (BLOB) (déconseillé)<br>3) GED externe intégrée | Option 1 : stockage objet + liens, versioning, contrôle d’accès par société/projet. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L44]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L327] |
| OP-05 | SMS / Email : fournisseurs & exigences (traçabilité, opt-in) | 1) Provider international (ex Twilio/MessageBird) (à valider couverture)<br>2) Provider local Maroc<br>3) SMTP interne + SMS passerelle opérateur | Choisir un provider avec couverture Maroc + logs ; centraliser templates + consentements (RGPD). | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L28]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L574] |
| OP-06 | Règles COS/CES : calcul et paramétrage | 1) Calcul automatique paramétré (formules + champs terrain)<br>2) Saisie manuelle + contrôle simple<br>3) Intégration SIG/urbanisme (plus tard) | Option 1 : calcul paramétré (audit des données) ; option 3 en V3 si besoin. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L22]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L75–L83] |
| OP-07 | États & transitions (lots, réservations, autorisations, tickets) | 1) Workflows state-machine stricts (transitions contrôlées)<br>2) Statuts libres (souples mais risques)<br>3) Hybride : statuts configurables + règles minimales | Option 3 : configurable, mais transitions critiques verrouillées (ex : Lot ‘vendu’). | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L25–L26]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L148]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L166] |
| OP-08 | API publique & intégrations (compta/ERP/site web) | 1) REST + webhooks (événements clés)<br>2) GraphQL interne + REST externe<br>3) ETL batch uniquement | Option 1 : REST + webhooks, versionnée, avec RBAC/tenancy sur chaque ressource. | [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L15]; [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx \| L365–L368] |
| OP-09 | Roadmap visuelle (roadmap.png) : extraction non fiable | 1) Relecture manuelle + transcription dans markdown<br>2) Re-export du slide en PDF texte<br>3) Fournir la source (PPTX/Canva/Draw.io) | Option 3 (source éditable) + Option 1 (transcription). | [SRC: roadmap.png] |


### 5.3 Hypothèses de travail (en attente de validation)

- Le système est une **solution SaaS cloud** accessible multi-supports. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L13]  
- La **conformité RGPD** est une exigence explicite (Maroc + UE), impliquant consentements, droits d’accès, traçabilité et suppression/export. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L16]  
- Le modèle d’autorisations est un **workflow multi-étapes** avec alertes de retard. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L148]  
- Le commercial nécessite un **pipeline configurable** et une gestion de statuts lots. [SRC: CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx | L25–L26]  

---

## 6. Liste des sources utilisées

- CDC_Premium_Final_Styled (4).docx
- CDC_Premium_Final_Styled_WITH_PRO_DIAGRAMS (1).docx
- Cahier Des Charges Crm V2.docx
- architecture_technique.png
- architecture_technique_real.png
- bpmn.png
- bpmn_achat_ultra_pro.png
- bpmn_sav_pro.png
- bpmn_vente.png
- bpmn_vente_ultra_pro.png
- cycle_projet (1).png
- cycle_projet.png
- erd_detail_pro2.png
- logo_yem.png
- organigramme.png
- roadmap.png
- uml.png
- uml_usecases_global_pro2.png
- workflow.png
- workflow_chantier.png
- workflow_global.png
