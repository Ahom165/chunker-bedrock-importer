package com.zai.chunkerimporter;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans folders for Bedrock Edition worlds:
 * <ul>
 *     <li>directories containing a LevelDB "db" folder and a level.dat file,</li>
 *     <li>.mcworld zip archives.</li>
 * </ul>
 */
public final class BedrockWorldScanner {
        /** Cap the folder size walk so huge worlds do not freeze the client. */
        private static final int MAX_SIZE_WALK_ENTRIES = 40_000;

        private BedrockWorldScanner() {
        }

        /**
         * Candidate search roots: the custom configured folder (if any) plus the
         * well-known Bedrock save locations for the current platform.
         */
        public static List<Path> defaultSearchRoots(String customFolder) {
                List<Path> roots = new ArrayList<>();
                if (customFolder != null && !customFolder.isBlank()) {
                        roots.add(Path.of(customFolder.trim()));
                }
                // Game-relative folder users can drop Bedrock worlds into
                roots.add(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("bedrock_worlds"));

                String os = System.getProperty("os.name", "").toLowerCase();
                String home = System.getProperty("user.home", "");
                if (os.contains("win")) {
                        String localAppData = System.getenv("LOCALAPPDATA");
                        if (localAppData == null || localAppData.isBlank()) localAppData = home + "\\AppData\\Local";
                        // Minecraft for Windows (UWP / Microsoft Store)
                        roots.add(Path.of(localAppData, "Packages", "Microsoft.MinecraftUWP_8wekyb3d8bbwe",
                                        "LocalState", "games", "com.mojang", "minecraftWorlds"));
                        // Education / preview variant
                        roots.add(Path.of(localAppData, "Packages", "Microsoft.MinecraftWindowsBeta_8wekyb3d8bbwe",
                                        "LocalState", "games", "com.mojang", "minecraftWorlds"));
                } else if (os.contains("mac")) {
                        roots.add(Path.of(home, "Library", "Application Support", "minecraftpe", "games", "com.mojang", "minecraftWorlds"));
                } else {
                        roots.add(Path.of(home, ".local", "share", "minecraftpe", "games", "com.mojang", "minecraftWorlds"));
                }
                // Android (PojavLauncher / Minecraft for mobile)
                roots.add(Path.of("/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds"));
                return roots;
        }

        /**
         * Scan the given roots. A root may be:
         * the Bedrock worlds folder itself, a single Bedrock world folder,
         * a single .mcworld file, or anything else (then its direct children are checked).
         */
        public static List<BedrockWorld> scan(List<Path> roots) {
                List<BedrockWorld> worlds = new ArrayList<>();
                for (Path root : roots) {
                        if (root == null) continue;
                        Path normalized = root.toAbsolutePath().normalize();
                        if (Files.isDirectory(normalized)) {
                                if (isBedrockWorld(normalized)) {
                                        worlds.add(toWorld(normalized, false));
                                        continue;
                                }
                                try (DirectoryStream<Path> stream = Files.newDirectoryStream(normalized)) {
                                        for (Path child : stream) {
                                                String fileName = child.getFileName().toString().toLowerCase();
                                                if (Files.isDirectory(child) && isBedrockWorld(child)) {
                                                        worlds.add(toWorld(child, false));
                                                } else if (Files.isRegularFile(child) && fileName.endsWith(".mcworld")) {
                                                        worlds.add(toWorld(child, true));
                                                }
                                        }
                                } catch (IOException ignored) {
                                        // Unreadable folder, skip
                                }
                        } else if (Files.isRegularFile(normalized) && normalized.getFileName().toString().toLowerCase().endsWith(".mcworld")) {
                                worlds.add(toWorld(normalized, true));
                        }
                }
                worlds.sort(Comparator.comparingLong(BedrockWorld::lastModified).reversed());
                return worlds;
        }

        /** A Bedrock world folder contains a LevelDB "db" directory and a level.dat file. */
        public static boolean isBedrockWorld(Path dir) {
                return Files.isDirectory(dir.resolve("db")) && Files.isRegularFile(dir.resolve("level.dat"));
        }

        private static BedrockWorld toWorld(Path path, boolean mcworld) {
                String name = null;
                if (mcworld) {
                        name = readNameFromMcworld(path);
                } else {
                        Path levelDat = path.resolve("level.dat");
                        if (Files.isRegularFile(levelDat)) {
                                name = BedrockLevelDat.readLevelName(levelDat);
                        }
                }
                if (name == null || name.isBlank()) {
                        name = path.getFileName().toString();
                }

                long size;
                try {
                        size = mcworld ? Files.size(path) : folderSize(path);
                } catch (IOException ignored) {
                        size = 0;
                }
                long modified;
                try {
                        modified = Files.getLastModifiedTime(path).toMillis();
                } catch (IOException ignored) {
                        modified = 0;
                }
                return new BedrockWorld(path, name.trim(), size, modified, mcworld);
        }

        private static String readNameFromMcworld(Path mcworldFile) {
                try (ZipFile zip = new ZipFile(mcworldFile.toFile())) {
                        ZipEntry entry = zip.getEntry("level.dat");
                        if (entry == null) {
                                // Some .mcworld archives wrap the world in a single root folder
                                for (var elements = zip.entries(); elements.hasMoreElements() && entry == null; ) {
                                        ZipEntry candidate = elements.nextElement();
                                        if (candidate.getName().toLowerCase().endsWith("level.dat")) {
                                                entry = candidate;
                                        }
                                }
                        }
                        if (entry == null) return null;
                        Path temp = Files.createTempFile("chunker-importer-leveldat", ".dat");
                        try {
                                try (var in = zip.getInputStream(entry)) {
                                        Files.write(temp, in.readAllBytes());
                                }
                                return BedrockLevelDat.readLevelName(temp);
                        } finally {
                                Files.deleteIfExists(temp);
                        }
                } catch (Exception e) {
                        ChunkerBedrockImporter.LOGGER.debug("Failed to read level.dat inside {}", mcworldFile, e);
                        return null;
                }
        }

        private static long folderSize(Path folder) {
                final long[] size = {0};
                final int[] visited = {0};
                try (var walk = Files.walk(folder)) {
                        walk.forEach(p -> {
                                if (visited[0]++ > MAX_SIZE_WALK_ENTRIES) return;
                                try {
                                        if (Files.isRegularFile(p)) size[0] += Files.size(p);
                                } catch (IOException ignored) {
                                }
                        });
                } catch (IOException ignored) {
                }
                return size[0];
        }
}
