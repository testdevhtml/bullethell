# BULLET STORM — jeu bullet hell Android

Jeu 100 % natif (Java, aucune bibliothèque externe), vue du dessus, esquive de centaines de balles.

## Obtenir l'APK à installer sur ton téléphone

### Option A — sans rien installer sur ton PC (GitHub Actions, gratuit)
1. Crée un compte sur github.com puis un nouveau dépôt (privé ou public).
2. Envoie-y tout le contenu de ce dossier (bouton « Add file > Upload files », glisse tous les fichiers, y compris le dossier `.github`).
3. Va dans l'onglet **Actions** : le workflow « Build APK » se lance tout seul (≈ 3-5 min).
4. Quand il est vert, clique dessus, puis télécharge **BulletStorm-apk** tout en bas (zip contenant `app-debug.apk`).
5. Copie `app-debug.apk` sur ton téléphone, ouvre-le et autorise l'installation depuis cette source.

### Option B — avec Android Studio
1. Installe Android Studio, puis **File > Open** et choisis ce dossier.
2. Attends la synchronisation Gradle, branche ton téléphone (débogage USB activé) et clique sur **Run**,
   ou **Build > Build APK(s)** pour obtenir `app/build/outputs/apk/debug/app-debug.apk`.

## Commandes de jeu
- Glisse n'importe où sur l'écran : joystick flottant (haut, bas, gauche, droite et diagonales).
- Le tir est automatique et visé sur l'ennemi le plus proche.
- Bouton **BOMBE** (bas droite) : efface toutes les balles et blesse les ennemis (2 par étage).
- Le point blanc cerclé de rouge est ta vraie zone de touche : bien plus petite que le vaisseau.

## Contenu
- **6 armes** : Blaster, Laser, Lance-TNT, Sniper, Orbes plasma, Tesla (foudre en chaîne).
  Blaster débloqué ; les 5 autres s'achètent à la boutique avec l'or gagné en jouant.
- **Évolution en partie** : chaque niveau gagné propose 3 choix (amélioration d'arme niv. 1→8,
  nouvelle arme jusqu'à 3 en même temps, bonus de dégâts / cadence / vitesse / PV / aimant).
- **5 boss** aux patterns différents, qui ouvrent l'étage suivant (les boss rebouclent avec plus de PV et des balles plus rapides) :
  1. Gardien Spirale — spirales et anneaux
  2. Tank Gatling — rafales en éventail et rideaux de balles
  3. Reine de la Ruche — invoque des drones, anneaux et fleurs de balles
  4. Prisme Laser — lasers annoncés puis tirés, faisceau balayant
  5. Noyau Oméga — enchaîne tous les patterns
- **Boutique permanente** : cœur de départ +1, dégâts +10 %. Sauvegarde automatique.

## Structure du code
- `app/src/main/java/com/bulletstorm/game/Game.java` — toute la logique (aucune dépendance Android)
- `GameView.java` — rendu Canvas et tactile
- `MainActivity.java` — plein écran, sauvegarde, bouton retour
