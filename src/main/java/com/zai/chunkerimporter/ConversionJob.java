package com.zai.chunkerimporter;

import com.hivemc.chunker.conversion.WorldConverter;
import com.hivemc.chunker.conversion.encoding.EncodingType;
import com.hivemc.chunker.conversion.encoding.base.Version;
import com.hivemc.chunker.conversion.encoding.base.reader.LevelReader;
import com.hivemc.chunker.conversion.encoding.base.writer.LevelWriter;
import com.hivemc.chunker.scheduling.task.TrackedTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Runs the Bedrock -> Java conversion with the embedded Chunker engine on a
 * background thread. State/progress fields are volatile so the GUI can poll them.
 */
public class ConversionJob {
        public enum State {PREPARING, CONVERTING, COMPLETED, FAILED}

        private final Path input;
        private final Path outputWorldFolder;

        public volatile State state = State.PREPARING;
        public volatile double progress = 0.0D;
        public volatile String message = "";
        /** Final folder name (level id) inside the saves directory. */
        public volatile String levelId = "";
        /** Display name of the source Bedrock world. */
        public volatile String worldName = "?";
        public volatile String error = "";

        /** Optional callback (runs on the conversion thread - use Minecraft#execute inside it). */
        public volatile Runnable onComplete;

        /** Set to true by the GUI once it consumed a COMPLETED/FAILED transition. */
        public volatile boolean claimed = false;

        public ConversionJob(Path input, Path outputWorldFolder, String levelId) {
                this.input = input;
                this.outputWorldFolder = outputWorldFolder;
                this.levelId = levelId;
        }

        /**
         * Blocking conversion - call from a background thread only.
         */
        public void run() {
                Path worldDir = input;
                Path tempDir = null;
                try {
                        // 1) Extract .mcworld archives (they are plain zip files)
                        if (input.getFileName() != null && input.getFileName().toString().toLowerCase().endsWith(".mcworld")) {
                                message = "extracting";
                                tempDir = outputWorldFolder.resolveSibling(".chunker-importer-tmp-" + Long.toHexString(System.nanoTime()));
                                extractMcworld(input, tempDir);
                                worldDir = locateWorldFolder(tempDir);
                        }

                        // 2) Convert with Chunker (auto-detects the Bedrock version)
                        state = State.CONVERTING;
                        message = "converting";

                        WorldConverter converter = new WorldConverter(UUID.randomUUID());
                        converter.setProcessMaps(true);
                        converter.setProcessLootTables(true);
                        converter.setProcessItems(true);
                        converter.setProcessEntities(true);
                        converter.setProcessBlockEntities(true);
                        converter.setProcessBiomes(true);
                        converter.setProcessHeightMap(true);
                        converter.setProcessLighting(true);
                        converter.setProcessColumnPreTransform(true);
                        converter.setLevelDBCompaction(true);
                        converter.setDiscardEmptyChunks(true);
                        converter.setAllowNBTCopying(false);

                        Optional<? extends LevelReader> reader = EncodingType.findReader(worldDir.toFile(), converter);
                        if (reader.isEmpty()) {
                                throw new IOException("Unknown world format - not a supported Bedrock world");
                        }
                        // Write the world in the latest Java format supported by Chunker
                        // (Minecraft 26.3 automatically upgrades older worlds on load)
                        Optional<? extends LevelWriter> writer = EncodingType.JAVA.createWriter(outputWorldFolder.toFile(), Version.LATEST, converter);
                        if (writer.isEmpty()) {
                                throw new IOException("Failed to create the Java world writer");
                        }

                        ChunkerBedrockImporter.LOGGER.info("Converting {} ({} -> Java {}) into {}",
                                        worldDir, reader.get().getEncodingType().getName(), reader.get().getVersion(), outputWorldFolder);

                        TrackedTask<Void> task = converter.convert(reader.get(), writer.get());
                        while (!task.future().isDone()) {
                                progress = Math.min(task.getProgress(), 0.999D);
                                Thread.sleep(100L);
                        }
                        progress = 1.0D;
                        // Propagate a conversion failure, if any
                        task.future().join();
                        state = State.COMPLETED;
                        ChunkerBedrockImporter.LOGGER.info("Conversion of {} finished: {}", input, outputWorldFolder);
                } catch (Throwable t) {
                        ChunkerBedrockImporter.LOGGER.error("Bedrock -> Java conversion failed", t);
                        state = State.FAILED;
                        String msg = t.getMessage();
                        error = (msg == null || msg.isBlank()) ? t.getClass().getSimpleName() : msg;
                } finally {
                        if (tempDir != null) {
                                deleteRecursively(tempDir);
                        }
                }
        }

        /**
         * Some .mcworld archives wrap the world in a root folder: if the extraction
         * root does not contain level.dat, look one level down for it.
         */
        private static Path locateWorldFolder(Path extractedDir) throws IOException {
                if (Files.isRegularFile(extractedDir.resolve("level.dat"))) {
                        return extractedDir;
                }
                try (var stream = Files.newDirectoryStream(extractedDir)) {
                        for (Path child : stream) {
                                if (Files.isDirectory(child) && Files.isRegularFile(child.resolve("level.dat"))) {
                                        return child;
                                }
                        }
                }
                return extractedDir;
        }

        private static void extractMcworld(Path mcworldFile, Path targetDir) throws IOException {
                Files.createDirectories(targetDir);
                try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(mcworldFile))) {
                        ZipEntry entry;
                        while ((entry = zip.getNextEntry()) != null) {
                                Path target = targetDir.resolve(entry.getName()).normalize();
                                if (!target.startsWith(targetDir)) {
                                        throw new IOException("Illegal entry in .mcworld archive: " + entry.getName());
                                }
                                if (entry.isDirectory()) {
                                        Files.createDirectories(target);
                                } else {
                                        Files.createDirectories(target.getParent());
                                        Files.write(target, zip.readAllBytes());
                                }
                        }
                }
        }

        private static void deleteRecursively(Path dir) {
                try (Stream<Path> walk = Files.walk(dir)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                                try {
                                        Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                        });
                } catch (IOException ignored) {
                }
        }
}
