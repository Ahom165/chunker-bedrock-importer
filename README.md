# Chunker Bedrock Importer — Minecraft Java 26.3 (Fabric)

Mod client pour **Minecraft Java Edition 26.3** (Fabric) qui détecte vos **mondes Bedrock Edition**
(dossiers ou fichiers `.mcworld`), les **convertit automatiquement en monde Java** grâce au moteur
**Chunker** (Hive Games), les place dans le dossier `saves`, puis **ouvre le monde converti
directement** — tout depuis l'écran de sélection de monde.

---

## Installation

1. Installez **Fabric Loader 0.20.1 ou plus** pour Minecraft **26.3** (https://fabricmc.net/use/).
2. Placez `chunker-bedrock-importer-1.0.0+mc26.3.jar` dans le dossier `mods/` de votre installation.
3. Lancez Minecraft avec le profil Fabric (Java 25 requis — c'est la version utilisée par MC 26.3).

Le mod est autonome : aucune autre dépendance (pas besoin de Fabric API).

## Utilisation

1. Au menu principal, ouvrez **Solo** (écran de sélection de monde) : un bouton
   **« Importer Bedrock »** apparaît en haut à droite.
2. La fenêtre d'import liste automatiquement les mondes Bedrock trouvés dans :
   - **Windows** : `%LOCALAPPDATA%\Packages\Microsoft.MinecraftUWP_8wekyb3d8bbwe\LocalState\games\com.mojang\minecraftWorlds`
   - **macOS** : `~/Library/Application Support/minecraftpe/games/com.mojang/minecraftWorlds`
   - **Linux** : `~/.local/share/minecraftpe/games/com.mojang/minecraftWorlds`
   - **Android (PojavLauncher)** : `Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds`
   - le dossier `bedrock_worlds/` du répertoire du jeu (déposez-y vos mondes),
   - ou n'importe quel dossier / fichier `.mcworld` saisi dans le champ de chemin.
3. Cliquez **Importer** : la conversion s'exécute en arrière-plan avec une barre de progression.
   Vous pouvez fermer la fenêtre sans interrompre la conversion.
4. À la fin, le monde (nommé d'après son nom Bedrock d'origine, sans écraser les mondes existants)
   est enregistré dans `saves/` puis **ouvert automatiquement**. Il apparaît aussi dans la liste
   des mondes comme n'importe quel monde Java.

Interface disponible en **français** et **anglais** (selon la langue du jeu).

## Détails techniques

- **Moteur de conversion** : [Chunker](https://github.com/HiveGamesOSS/Chunker) **v1.21.0** (MIT, Hive Games),
  release officielle du 2 octobre 2026, embarquée dans le jar du mod (jar-in-jar).
  Le jar embarqué est « allégé » : les bibliothèques déjà fournies par Minecraft 26.3 (fastutil, Guava,
  Gson, failureaccess, lz4) sont exclues ; Caffeine est conservée (4 Mo au lieu de 32 Mo).
- **Détection de version** : Chunker détecte tout seul la version Bedrock du monde
  (1.12.0 → 1.21.130 et 1.26.0 → 1.26.60).
- **Sortie** : le monde converti est écrit **directement au format Java 26.3** (dernier format
  supporté par Chunker) — aucun upgrade ni rechargement intermédiaire.
- **Nom du monde** : lu directement dans le `level.dat` Bedrock (NBT little-endian, parseur intégré).
- **Sécurité** : le monde Bedrock source n'est jamais modifié ; les fichiers `.mcworld` sont extraits
  dans un dossier temporaire supprimé après la conversion.

## Limites connues

- Chunker (version open source 1.21.0) convertit les mondes **Bedrock 1.12.0 → 1.21.130 et
  1.26.0 → 1.26.60**. Un monde Bedrock plus récent peut ne pas être reconnu.
- Certaines entités/objets/potions sans équivalent Java exact sont signalés en warnings par Chunker
  (« Missing mapping ») et peuvent être omis — c'est un comportement du moteur Chunker lui-même.
- Conversion **Bedrock → Java uniquement** dans la fenêtre d'import (Java → Bedrock reste possible
  via l'application Chunker officielle).
- Mod **client uniquement** (écran de sélection de monde en solo).

## Recompiler le mod depuis les sources

Le dossier `chunker-bedrock-importer-sources.zip` contient le projet Gradle complet
(le jar Chunker allégé est inclus dans `libs/`) :

```bash
unzip chunker-bedrock-importer-sources.zip
cd chunker-bedrock-importer
./gradlew build          # nécessite JDK 25
# -> build/libs/chunker-bedrock-importer-1.0.0+mc26.3.jar
```

Pour reconstruire le moteur Chunker depuis son dépôt (optionnel) :

```bash
git clone https://github.com/HiveGames-OSS/Chunker.git
cd Chunker
# Dans cli/build.gradle.kts, le bloc tasks.shadowJar exclut les libs fournies par Minecraft :
#   exclude("it/unimi/**") ; exclude("com/google/**") ; exclude("net/jpountz/**")
#   exclude("org/checkerframework/**") ; exclude("org/jetbrains/**") ; exclude("org/intellij/**")
#   exclude("linux/**") ; exclude("darwin/**") ; exclude("win32/**")
# (conserver com/github/benmanes/caffeine : non fournie par Minecraft)
JAVA_HOME=<JDK 21> ./gradlew :cli:shadowJar -x test
cp build/libs/chunker-cli-*.jar ../chunker-bedrock-importer/libs/
```

Ou plus simple : télécharger le `chunker-cli-*.jar` pré-compilé depuis la page
[Releases du dépôt Chunker](https://github.com/HiveGamesOSS/Chunker/releases)
puis l'alléger comme décrit ci-dessus.

## Crédits et licences

- [Chunker](https://github.com/HiveGamesOSS/Chunker) par **Hive Games** (licence MIT) — moteur de conversion.
- Mod : licence MIT.

## Fichiers

| Fichier | Description |
| --- | --- |
| `chunker-bedrock-importer-1.0.0+mc26.3.jar` | Le mod prêt à installer (moteur Chunker inclus) |
| `chunker-bedrock-importer-sources.zip` | Code source complet du mod (Gradle, JDK 25) |
