# MonoTrader

Overlay monochrome Android pour MetaTrader 5 en mode split-screen.

## Fonctionnalités

- **Filtre gris semi-transparent** positionnable sur la moitié gauche, droite ou plein écran
- **Anti-flash** : lissage des transitions (200ms par défaut) pour réduire le clignotement des prix
- **Opacité réglable** de 0% à 85%
- **Contrôle par notification** : pause/reprise et arrêt sans ouvrir l'app
- **Non-intrusif** : les clics passent à travers le filtre vers MT5

## Cas d'usage

Utilisez votre tablette en split-screen :
- **Côté gauche** : MetaTrader 5 avec le filtre MonoTrader activé (monochrome, anti-flash)
- **Côté droit** : TradingView en couleurs normales

## Installation

### Depuis GitHub Actions (recommandé)
1. Allez dans l'onglet **Actions** du repo
2. Cliquez sur le dernier build réussi
3. Téléchargez l'APK depuis **Artifacts**
4. Installez l'APK sur votre tablette (activer "Sources inconnues")

### Build local
```bash
git clone https://github.com/VOTRE_USER/MonoTrader.git
cd MonoTrader
./gradlew assembleDebug
```
L'APK se trouve dans `app/build/outputs/apk/debug/`

## Permissions requises

- **Affichage par-dessus d'autres apps** : pour dessiner le filtre
- **Notifications** : pour les contrôles dans la barre de notification

## Compatibilité

- Android 8.0+ (API 26)
- Testé sur Xiaomi Pad 7 (HyperOS)

## Licence

MIT License
