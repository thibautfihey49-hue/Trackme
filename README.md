
# TrackMe - Partage position par SMS data + commande à distance

- Carte OSMdroid chargée au 1er démarrage
- Envoi SMS data in-app (pas via messagerie): SHAREPOS:lat;lng;ts
- Réception silencieuse + historique avec aperçu rues sans doublons (30m)
- Partage auto chaque minute (ForegroundService)
- Télécommande: envoie SHAREPOS_CMD:START:60:CODE pour démarrer le suivi à distance

## Build local
./gradlew assembleDebug

## Termux
pkg install git openjdk-17
git clone https://github.com/thibautfihey49-hue/Trackme
cd Trackme
./gradlew assembleDebug
# APK dans app/build/outputs/apk/debug/

## Push
git add .
git commit -m "v5 remote command"
git push origin main
