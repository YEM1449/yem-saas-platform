# Guide Agent — Gérer mes visites

Le module **Visites** centralise vos rendez-vous commerciaux : prise de RDV rapide,
agenda, comptes-rendus et rappels automatiques. Toutes les heures sont en heure du
Maroc (Africa/Casablanca).

## 1. Ouvrir mon agenda

Menu latéral → **Visites** (`/app/visites`). L'agenda s'affiche en vue **Semaine** par
défaut. Basculez entre **Jour / Semaine / Mois**, naviguez avec ‹ › ou revenez sur
**Aujourd'hui**.

- En tant qu'**agent**, vous voyez uniquement vos propres visites.
- En tant que **manager/admin**, un filtre **Agent** permet de voir l'agenda de
  n'importe quel collaborateur, ou de toute la société.

Les couleurs indiquent le statut : **Planifiée** (bleu), **Confirmée** (ambre),
**Réalisée** (vert), **Annulée** (gris), **Absence** (rouge). Une légende est affichée
en bas de l'agenda.

## 2. Planifier une visite (moins de 20 secondes)

Cliquez sur **+ Planifier une visite** (ou sur un créneau vide de l'agenda, qui
pré-remplit la date). Renseignez :

1. **Contact** — recherche par nom, téléphone ou email.
2. **Date et heure**, **durée** (30 min par défaut), **type** (sur site, agence, visio,
   téléphonique), **lieu**.
3. **Programme** puis **Bien** (optionnels) — la liste des biens se filtre par programme.

Un **conflit de créneau** (vous avez déjà une visite qui se chevauche) est refusé avec
le message « Vous avez déjà une visite sur ce créneau. ». Un manager peut cocher
**Forcer malgré un conflit**.

Depuis une **fiche contact**, l'onglet **Visites** liste l'historique et propose
**Planifier une visite** déjà rattachée au contact.

## 3. Confirmer, puis faire le compte-rendu

- **Confirmer** une visite planifiée la passe en *Confirmée* (déclenche les rappels).
- Après le rendez-vous, **Saisir le compte-rendu** : décrivez le déroulé et choisissez
  un **résultat** (Intéressé, À relancer, Pas intéressé, **Opportunité créée**). La
  visite passe alors en *Réalisée* (le compte-rendu est obligatoire).
- Si le résultat est **Opportunité créée**, un bouton **Créer une vente** ouvre le
  pipeline de vente pré-rempli ; la vente créée reste reliée à la visite.
- **Marquer absence** (no-show) si le prospect ne s'est pas présenté.
- **Annuler** demande une raison ; un email d'annulation part si la visite était confirmée.

## 4. Rappels automatiques

Dès confirmation, des rappels sont programmés et **stockés en base** (ils survivent à un
redémarrage du serveur) :

- **24 h avant** : email à l'agent **et** au prospect (si son email est connu).
- **1 h avant** : email à l'agent.

L'annulation d'une visite annule les rappels encore en attente.

## 5. Ajouter à mon calendrier

Sur le détail d'une visite, **Ajouter à mon calendrier (.ics)** télécharge un fichier
iCalendar à importer dans Google/Apple/Outlook Calendar.

## 6. Suivre mes performances

Le tableau de bord d'accueil affiche **Visites réalisées (mois)** et le **taux de
conversion visite → opportunité**, calculés à partir de la même source que ce module.
