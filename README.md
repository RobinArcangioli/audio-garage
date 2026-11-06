# Audio Garage

Application Android personnelle (sideload) : tap sur une piste d'une playlist YouTube publique, téléchargement en arrière-plan (M4A natif), puis lecture automatique. Cahier des charges : [`PROMPT_MAITRE_v2.md`](PROMPT_MAITRE_v2.md). Règles de travail : [`CLAUDE.md`](CLAUDE.md).

> Outil privé, hors Play Store. Le téléchargement depuis YouTube contrevient à ses conditions d'utilisation ; aucun contournement de DRM, aucune fonction de partage.

## Prérequis hôte

Ubuntu 24.04 avec uniquement : Docker (plugin `docker compose`), Git, `make`, `adb` (paquet `android-sdk-platform-tools` ou platform-tools de Google). Aucun JDK ni SDK Android sur l'hôte.

## Commandes

| Commande | Effet |
|---|---|
| `make image` | Construit l'image `audio-garage-builder:1` (JDK 17 + SDK Android). Automatique au premier `make` sinon. |
| `make build` | Compile tous les modules (sources debug + tests). |
| `make test` | Tests unitaires JVM. |
| `make lint` | Android lint (échoue sur toute erreur). |
| `make apk` | APK debug → `app/build/outputs/apk/debug/app-debug.apk`. |
| `make install` | Installe l'APK sur l'appareil connecté via l'`adb` de l'hôte et lance l'app. |
| `make bench` | Harnais de benchmark des extracteurs (étape 0.10, pas encore implémenté). |
| `make shell` / `make clean` | Shell dans le conteneur / suppression des sorties de build. |

Les conteneurs tournent avec l'UID/GID de l'utilisateur hôte (`HOST_UID`/`HOST_GID`, exportés par le Makefile) : aucun fichier du dépôt n'appartient à root. Le cache Gradle est dans le volume nommé `audio-garage_gradle-cache` ; le SDK est intégré à l'image.

### Installer sur le téléphone

USB : activer les options développeur et le débogage USB, brancher, accepter l'empreinte, puis `make apk install`.
Wi-Fi (Android 11+) : options développeur → Débogage sans fil → Associer avec un code, puis `adb pair IP:PORT` et `adb connect IP:PORT`.

L'APK debug est signé avec `keystore/debug.keystore`, clé de debug **non secrète** versionnée volontairement : la signature est identique d'un build à l'autre, donc `adb install -r` met à jour l'app sans la désinstaller.

## Configuration réseau particulière (proxy, CA d'entreprise)

Sans proxy, rien à faire. Derrière un proxy TLS :
- déposer les certificats `*.crt` dans `docker/certs/` (ignorés par git), ils sont ajoutés au magasin système et au `cacerts` Java de l'image ;
- passer la configuration propre à la machine dans un `docker-compose.override.yml` (ignoré par git), par exemple `JAVA_TOOL_OPTIONS` avec `-Dhttps.proxyHost/-Dhttps.proxyPort` et l'argument de build `SDKMANAGER_OPTS`.

## Stack (versions dans `gradle/libs.versions.toml`)

AGP 9.5.1 (Kotlin intégré, sans plugin `kotlin-android`), Gradle 9.6.1, Kotlin 2.4.21, JDK 17, Compose (BOM 2026.09.00), Koin 4.2.2.
`compileSdk` 37 (exigé par les dernières bibliothèques AndroidX), `targetSdk` 36 (appareil de référence Galaxy A16 / Android 16), `minSdk` 26.

Modules : `:app`, `:core:model` (Kotlin pur), `:core:data`, `:feature:downloader`, `:feature:player`. Configuration partagée dans `build-logic/` (plugins de convention).
