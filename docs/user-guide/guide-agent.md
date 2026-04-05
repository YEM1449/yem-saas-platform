# Guide Agent Commercial — HLM CRM

## Votre rôle

En tant qu'**Agent**, vous gérez vos contacts et vos dossiers de vente au quotidien.  
Vous avez accès en lecture à l'ensemble des ressources de votre société, et en écriture sur vos propres dossiers.

---

## 1. Se connecter

1. Rendez-vous sur l'URL de votre CRM (ex. `https://yem-hlm.youssouf-mehdi.workers.dev`).
2. Saisissez votre **adresse email** et votre **mot de passe**.
3. Si votre société dispose de plusieurs espaces, sélectionnez le bon.
4. Cliquez sur **Connexion**.

> **Oubli du mot de passe ?** Contactez votre Manager ou Administrateur pour qu'il vous renvoie un lien d'activation.

---

## 2. Tableau de bord

À la connexion, vous arrivez sur le **tableau de bord** qui affiche :

| Carte | Description |
|---|---|
| Ventes actives | Nombre de ventes en cours (tous statuts sauf LIVRE / ANNULE) |
| Acomptes | Acomptes enregistrés ce mois |
| Prospects actifs | Contacts au statut PROSPECT ou QUALIFIED_PROSPECT |
| Réservations | Réservations ouvertes (ACTIVE) |

Les **accès rapides** vous emmènent directement vers Ventes, Contacts, Biens, Réservations, Projets et Tâches.

---

## 3. Gérer vos contacts

### Créer un contact
1. Menu **Contacts** → bouton **Nouveau contact**.
2. Renseignez Prénom, Nom, Email et/ou Téléphone (au moins un des deux est obligatoire).
3. Cliquez **Enregistrer**.

### Qualifier un prospect
Un contact **PROSPECT** peut être qualifié :
1. Ouvrez sa fiche → bouton **Qualifier comme prospect**.
2. Renseignez la source, les fourchettes de budget et des notes.
3. Le statut passe automatiquement à **QUALIFIED_PROSPECT**.

### Statuts du pipeline contact

| Statut | Signification |
|---|---|
| PROSPECT | Premier contact, pas encore qualifié |
| QUALIFIED_PROSPECT | Besoins identifiés, budget connu |
| CLIENT | Réservation ou acompte signé |
| ACTIVE_CLIENT | Vente créée dans le système |
| COMPLETED_CLIENT | Vente livrée (LIVRE) |
| REFERRAL | Client satisfait, source de recommandations |
| LOST | Dossier perdu |

> **Astuce :** Les statuts CLIENT, ACTIVE_CLIENT et COMPLETED_CLIENT sont mis à jour **automatiquement** par le pipeline de vente. Il n'est pas nécessaire de les changer manuellement.

---

## 4. Pipeline de vente (Ventes)

### Créer une vente
1. Menu **Ventes** → bouton **Nouvelle vente**.
2. Sélectionnez le **contact** (acquéreur), le **bien** et renseignez le prix de vente.
3. Vous pouvez lier une réservation existante.
4. Cliquez **Créer**.

Le contact passe automatiquement au statut **ACTIVE_CLIENT**.

### États du pipeline vente

```
COMPROMIS ──→ FINANCEMENT ──→ ACTE_NOTARIE ──→ LIVRE
    │               │               │
    └───────────────┴───────────────┴──→ ANNULE
```

| Statut | Étape |
|---|---|
| COMPROMIS | Compromis de vente signé |
| FINANCEMENT | Dossier de financement en cours |
| ACTE_NOTARIE | Acte notarié signé |
| LIVRE | Bien livré à l'acquéreur |
| ANNULE | Vente annulée (état final) |

### Avancer le pipeline
1. Ouvrez le détail d'une vente.
2. Cliquez **Avancer le pipeline**.
3. Renseignez la date de transition (obligatoire pour ACTE_NOTARIE et LIVRE).
4. Ajoutez des notes si nécessaire → **Confirmer**.

### Annuler une vente
Depuis le dialogue d'avancement : cliquez **Annuler la vente à la place**, puis confirmez.

---

## 5. Échéancier de paiement

Depuis la fiche vente, section **Échéancier** :

- **Ajouter une échéance** : libellé, montant, date d'échéance.
- **Marquer comme payée** : cliquez le bouton **Payé** sur la ligne correspondante.

Les échéances apparaissent aussi dans l'espace acquéreur (portail) du client.

---

## 6. Documents

Attachez des documents à un dossier de vente :
1. Section **Documents** de la fiche vente → bouton **+ Ajouter**.
2. Sélectionnez le fichier sur votre ordinateur.
3. Le fichier est uploadé et visible immédiatement.

Les documents peuvent aussi être attachés aux Contacts, Biens, Projets, Acomptes et Réservations.

---

## 7. Inviter l'acquéreur sur le portail

1. Fiche vente → section **Portail acquéreur**.
2. Bouton **Inviter** : un lien magique est envoyé par email à l'acquéreur.
3. L'acquéreur accède à son espace personnel avec un lien unique (48 h de validité).

---

## 8. Tâches

- Menu **Tâches** → liste de vos tâches assignées.
- Bouton **Nouvelle tâche** pour en créer une.
- Filtrez par statut, priorité ou date d'échéance.

---

## 9. Réservations

Depuis la fiche d'un contact → onglet **Réservations** :
1. Sélectionnez le bien et renseignez le prix de réservation.
2. Le bien passe en statut **RESERVED** pendant 7 jours.
3. Convertissez en acompte ou en vente le moment venu.

---

## Assistance

En cas de problème :
- Contactez votre **Manager** ou **Administrateur**.
- Consultez la section [Dépannage](../09-troubleshooting/README.md).
