# CLAUDE.md : Audio Garage (Android natif)

Le cahier des charges complet est dans `PROMPT_MAITRE_v2.md`. Ce fichier rappelle les règles à respecter à chaque session.

## Projet
App Android native Kotlin, usage personnel (sideload). L'utilisateur colle l'URL d'une playlist YouTube publique, voit les métadonnées, **tape une piste** : téléchargement en arrière-plan (M4A natif, pas de MP3) puis lecture automatique. Priorité absolue : **performance et fiabilité du téléchargement dans le temps.**

## Règles de travail
1. **Une étape à la fois**, selon la roadmap du prompt maître. S'arrêter à la fin de chaque étape et attendre ma validation.
2. **Tout build/test/lint dans Docker** (Ubuntu 24.04 hôte, conteneur avec UID/GID hôte). Utiliser `make build|test|lint|apk|install|bench`. Seuls `adb` et les mesures d'arrière-plan passent par l'hôte et l'appareil physique.
3. **Un commit à la fin de chaque étape**, uniquement si build, tests et lint passent. Conventional Commits. Un commit par sous-étape si l'étape est grosse. Jamais de réécriture d'historique sans accord.
4. **Honnêteté** : ne jamais dire qu'une chose est testée si elle ne l'est pas ; ne jamais inventer de chiffres ; vérifier les API et versions dans la doc officielle.
5. Réponses en français, code et commentaires en anglais, textes UI en ressources (français par défaut).

## Stack
Kotlin, coroutines/Flow, Jetpack Compose + Material 3, **Koin** (avec `verify()` en test), Room, DataStore, OkHttp, Coil, WorkManager (+ foreground service ou jobs UIDT, à départager), AndroidX Media3. `compileSdk`/`targetSdk` 36, `minSdk` 26.

## Appareil de test de référence
Samsung Galaxy A16, Android 16, One UI. Les comportements d'arrière-plan ne se valident que dessus.

## Décisions structurantes
- **Extracteur non tranché** : NewPipeExtractor vs yt-dlp embarqué, derrière l'interface `AudioSource.fetch(videoId, destination): Flow<FetchProgress>`. Décision uniquement sur les mesures (fiabilité, stabilité dans le temps, débit, coût/maintenance), via `make bench` et l'étape 0.13.
- **Tap** : `TapPolicy` interchangeable. `QUEUE` d'abord, puis `LATEST_WINS` avec debounce 600 ms après validation du POC.
- **Pipeline** : persisté en Room, idempotent, reprenable. `MediaSessionService` démarré au tap, app au premier plan. L'UI observe, elle ne pilote pas.
- **Protection réseau** : `NetworkPolicyConfig` (rate limiter + jitter, concurrence bornée, retry avec backoff, timeouts distincts, circuit breaker, cache, journal CSV).
- **Format** : M4A natif. Vérifier durée et seek des fichiers. Pas de FFmpeg.

## Interdits
- Enchaîner des étapes sans validation.
- Commiter du code qui ne compile pas ou dont les tests échouent.
- Lancer une campagne de mesure sur YouTube sans plan validé et arrêt automatique au premier signe de blocage.
- Secrets en dur ; API dépréciées sans justification ; abstractions superflues.
- Aucun contournement de DRM, aucune fonction de partage ou de distribution.
