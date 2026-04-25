# Guide utilisateur — Visualiseur 3D

Ce guide explique comment utiliser la vue 3D de bâtiment selon votre rôle.

## 1. Accéder au visualiseur

Il existe deux points d'entrée.

**Depuis un projet**

Ouvrez un projet dans `Projets`, puis naviguez vers l'onglet `Visualiseur 3D`. Cette vue plein écran est dédiée à l'exploration interactive du bâtiment.

**Depuis le tableau de bord commercial**

Le tableau de bord commercial propose un onglet `3D` qui intègre le visualiseur directement dans la page analytique. Vous pouvez consulter les KPIs du projet et la vue 3D côte à côte.

## 2. Lire la légende des couleurs

Chaque lot du modèle est coloré selon son statut commercial actuel.

| Couleur | Statut affiché | Signification |
| --- | --- | --- |
| Bleu | DISPONIBLE | le lot peut être réservé ou vendu |
| Ambre | RESERVÉ | une réservation est en cours |
| Vert | VENDU | une vente est enregistrée pour ce lot |
| Gris | LIVRÉ | le lot a été livré ou est hors-commercialisation |

La légende reste visible en bas du visualiseur. Vous pouvez la masquer en cliquant sur `Légende`.

## 3. Survol et informations d'un lot

Placez votre curseur sur un lot dans le modèle. Une infobulle apparaît avec :

- la référence du lot
- le statut courant
- la typologie et la surface
- le prix affiché quand il est disponible

Le lot survolé s'illumine légèrement pour confirmer la sélection.

## 4. Interaction selon votre rôle

### Agent ou Manager — ouvrir le formulaire de vente

Cliquez sur un lot disponible. Le système ouvre directement le formulaire de création de vente pré-rempli avec la référence du lot.

Cliquer sur un lot réservé ou vendu affiche son état sans proposer d'action commerciale.

### Admin — exporter en PDF

Dans l'onglet tableau de bord 3D, utilisez le bouton `Exporter PDF` en haut à droite de la vue. L'export capture l'état courant de la vue et du modèle. Un indicateur de chargement s'affiche pendant la génération.

### Acquéreur (portail) — vue en lecture seule

Depuis votre espace portail, vous accédez à une vue 3D du bâtiment de votre projet. Cette vue est en lecture seule :

- les couleurs montrent l'état général de la commercialisation
- le survol affiche les informations du lot
- aucun clic ne déclenche d'action commerciale

Votre lot personnel est identifiable par sa référence dans l'infobulle.

## 5. Navigation au clavier

Si vous ne disposez pas d'une souris, utilisez le clavier pour explorer les lots.

| Touche | Action |
| --- | --- |
| `Tab` | passer au lot suivant |
| `Shift+Tab` | revenir au lot précédent |
| `Entrée` ou `Espace` | sélectionner le lot actif |

## 6. KPIs du projet (tableau de bord)

Dans l'onglet tableau de bord 3D, cliquez sur `Afficher KPIs` pour ouvrir le panneau flottant. Il présente :

- nombre et pourcentage de lots disponibles, réservés, vendus, livrés
- chiffre d'affaires réalisé et prévisionnel

Le panneau reste superposé au modèle et peut être masqué avec le bouton `×` en haut à droite.

## 7. Filtrer par statut

Utilisez le sélecteur `Statut` dans la barre d'outils pour n'afficher qu'une catégorie de lots. Les autres lots restent dans la scène mais leurs informations sont filtrées de l'affichage.

## 8. Mise à jour automatique

Le visualiseur actualise les couleurs toutes les 30 secondes sans recharger la page ni recréer la scène. Si un lot vient d'être vendu par un collègue, son couleur passera au vert au prochain rafraîchissement automatique.

## 9. Points d'attention

- le modèle 3D doit avoir été importé par un administrateur avant que le visualiseur soit disponible pour un projet
- si le modèle n'est pas encore disponible, un message d'erreur s'affiche à la place de la scène
- les performances peuvent varier selon la taille du modèle et la capacité du poste de travail
