package com.zai.chunkerimporter.gui;

import com.zai.chunkerimporter.BedrockWorld;
import com.zai.chunkerimporter.BedrockWorldScanner;
import com.zai.chunkerimporter.ChunkerBedrockImporter;
import com.zai.chunkerimporter.ChunkerConfig;
import com.zai.chunkerimporter.ConversionJob;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Screen listing the Bedrock worlds found in a folder and converting them to
 * Java Edition with the embedded Chunker engine.
 */
public class BedrockImportScreen extends Screen {
        private static final int LIST_HALF_WIDTH = 220;
        private static final int LIST_TOP = 58;
        private static final int LIST_BOTTOM_MARGIN = 34;
        private static final int ROW_HEIGHT = 28;
        private static final int IMPORT_BUTTON_WIDTH = 62;

        /** The conversion currently running (or finished), kept so the screen can be reopened. */
        private static ConversionJob activeJob = null;

        private final Screen parent;
        private final ChunkerConfig config;

        private EditBox pathBox;
        private List<BedrockWorld> worlds = List.of();
        private double scroll = 0.0D;
        private ConversionJob job = null;
        private boolean scannedOnce = false;
        /** Dropped .mcworld files waiting for the current conversion to finish. */
        private final Deque<Path> dropQueue = new ArrayDeque<>();

        public BedrockImportScreen(Screen parent) {
                super(Component.translatable("chunker_bedrock_importer.screen.title"));
                this.parent = parent;
                this.config = ChunkerConfig.load();
                this.job = activeJob;
        }

        @Override
        protected void init() {
                // Path field + Refresh button side by side, centered as one group (no overlap)
                Component refreshLabel = Component.translatable("chunker_bedrock_importer.screen.refresh");
                int refreshWidth = Math.max(40, this.font.width(refreshLabel) + 12);
                int fieldWidth = Math.min(300, this.width - refreshWidth - 60);
                if (fieldWidth < 80) fieldWidth = Math.max(80, this.width - refreshWidth - 20);
                int groupWidth = fieldWidth + 4 + refreshWidth;
                int groupX = this.width / 2 - groupWidth / 2;
                pathBox = new EditBox(this.font, groupX, 30, fieldWidth, 20,
                                Component.translatable("chunker_bedrock_importer.screen.path_label"));
                pathBox.setMaxLength(400);
                pathBox.setValue(currentRootString());
                pathBox.setHint(Component.translatable("chunker_bedrock_importer.screen.hint"));
                addRenderableWidget(pathBox);
                addRenderableWidget(Button.builder(refreshLabel, b -> rescan())
                                .bounds(groupX + fieldWidth + 4, 30, refreshWidth, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("chunker_bedrock_importer.screen.done"), b -> onClose())
                                .bounds(this.width / 2 - 50, this.height - 26, 100, 20).build());

                if (!this.scannedOnce) {
                        rescan();
                }
        }

        private String currentRootString() {
                if (!config.bedrockWorldsFolder.isBlank()) return config.bedrockWorldsFolder;
                // Pre-fill with the first Bedrock folder that actually exists
                for (Path root : BedrockWorldScanner.defaultSearchRoots(null)) {
                        if (Files.isDirectory(root)) return root.toString();
                }
                return "";
        }

        private void rescan() {
                config.bedrockWorldsFolder = pathBox.getValue().trim();
                config.save();
                List<Path> roots = config.bedrockWorldsFolder.isBlank()
                                ? BedrockWorldScanner.defaultSearchRoots(null)
                                : List.of(Path.of(config.bedrockWorldsFolder));
                this.worlds = BedrockWorldScanner.scan(roots);
                this.scroll = 0.0D;
                this.scannedOnce = true;
        }

        // ------------------------------------------------------------------ rendering

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                super.extractRenderState(graphics, mouseX, mouseY, partialTick);

                int listTop = LIST_TOP;
                int listBottom = this.height - LIST_BOTTOM_MARGIN;
                int x0 = this.width / 2 - LIST_HALF_WIDTH;
                int x1 = this.width / 2 + LIST_HALF_WIDTH;

                graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 10, 0xFFFFFFFF);

                graphics.fill(x0, listTop, x1, listBottom, 0x99000000);

                if (this.worlds.isEmpty() && this.job == null) {
                        int midY = (listTop + listBottom) / 2;
                        graphics.centeredText(this.font, Component.translatable("chunker_bedrock_importer.screen.empty"),
                                        this.width / 2, midY - 12, 0xFFAAAAAA);
                        graphics.centeredText(this.font, Component.translatable("chunker_bedrock_importer.screen.drop_hint"),
                                        this.width / 2, midY + 6, 0xFF666666);
                } else if (this.scannedOnce && this.job == null) {
                        Component header = Component.translatable("chunker_bedrock_importer.screen.worlds_of");
                        int headerY = listTop + 2;
                        graphics.text(this.font, header, x0 + 4, headerY, 0xFFCCCCCC);
                        String folder = pathBox != null ? pathBox.getValue().trim() : "";
                        if (folder.length() > 60) folder = "..." + folder.substring(folder.length() - 57);
                        if (!folder.isBlank()) {
                                graphics.text(this.font, Component.literal(folder), x0 + 4 + this.font.width(header) + 6, headerY, 0xFF888888);
                        }
                }

                graphics.enableScissor(x0, listTop, x1, listBottom);
                int rowY = listTop + 18 - (int) this.scroll;
                for (BedrockWorld world : this.worlds) {
                        if (rowY + ROW_HEIGHT >= listTop && rowY < listBottom) {
                                drawWorldRow(graphics, world, x0, x1, rowY, mouseX, mouseY);
                        }
                        rowY += ROW_HEIGHT;
                }
                graphics.disableScissor();

                if (this.job != null) {
                        drawJobOverlay(graphics, mouseX, mouseY);
                }
        }

        private void drawWorldRow(GuiGraphicsExtractor graphics, BedrockWorld world, int x0, int x1, int y, int mouseX, int mouseY) {
                boolean rowHover = mouseX >= x0 && mouseX <= x1 && mouseY >= y && mouseY < y + ROW_HEIGHT && this.job == null;
                if (rowHover) {
                        graphics.fill(x0 + 2, y, x1 - 2, y + ROW_HEIGHT, 0x30FFFFFF);
                }

                String name = world.name();
                graphics.text(this.font, Component.literal(name), x0 + 6, y + 4, 0xFFFFFFFF);

                String detail = formatSize(world.size()) + "  -  "
                                + DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(new Date(world.lastModified()));
                String path = world.path().toString();
                graphics.text(this.font, Component.literal(detail), x0 + 6, y + 16, 0xFF777777);
                int detailWidth = this.font.width(detail);
                int pathX = x0 + 6 + detailWidth + 8;
                int pathMaxWidth = (x1 - IMPORT_BUTTON_WIDTH - 14) - pathX;
                if (pathMaxWidth > 40) {
                        Component pathText = Component.literal(path);
                        graphics.enableScissor(pathX, y, pathX + pathMaxWidth, y + ROW_HEIGHT);
                        graphics.text(this.font, pathText, pathX, y + 16, 0xFF555555);
                        graphics.disableScissor();
                        graphics.setTooltipForNextFrame(Component.literal(path), mouseX, mouseY);
                }

                if (world.mcworld()) {
                        graphics.text(this.font, Component.literal(".mcworld"), x1 - IMPORT_BUTTON_WIDTH - 52, y + 4, 0xFF55AAFF);
                }

                // Import button
                int bx1 = x1 - 6;
                int bx0 = bx1 - IMPORT_BUTTON_WIDTH;
                boolean btnHover = mouseX >= bx0 && mouseX <= bx1 && mouseY >= y + 4 && mouseY < y + 24 && this.job == null;
                graphics.fill(bx0, y + 4, bx1, y + 24, btnHover ? 0xFF3C8527 : 0xFF275E1B);
                graphics.centeredText(this.font, Component.translatable("chunker_bedrock_importer.screen.import"),
                                (bx0 + bx1) / 2, y + 10, 0xFFFFFFFF);
        }

        private void drawJobOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
                graphics.fill(0, 0, this.width, this.height, 0xA0000000);

                int panelWidth = 320;
                int px0 = this.width / 2 - panelWidth / 2;
                int px1 = this.width / 2 + panelWidth / 2;
                int py0 = this.height / 2 - 70;
                int py1 = this.height / 2 + 70;
                graphics.fill(px0, py0, px1, py1, 0xE0060606);
                graphics.outline(px0, py0, px1, py1, 0xFF555555);

                ConversionJob current = this.job;
                if (current == null) return;

                if (current.state == ConversionJob.State.COMPLETED) {
                        graphics.centeredText(this.font, Component.translatable("chunker_bedrock_importer.screen.success", current.levelId),
                                        this.width / 2, py0 + 14, 0xFF55FF55);
                        graphics.centeredText(this.font, Component.translatable("chunker_bedrock_importer.screen.success_hint"),
                                        this.width / 2, py0 + 30, 0xFFAAAAAA);
                        drawCenteredButton(graphics, mouseX, mouseY, px0, px1, py0 + 44,
                                        Component.translatable("chunker_bedrock_importer.screen.play"), 0xFF3C8527);
                } else if (current.state == ConversionJob.State.FAILED) {
                        graphics.centeredText(this.font, Component.translatable("chunker_bedrock_importer.screen.failed", current.error),
                                        this.width / 2, py0 + 14, 0xFFFF5555);
                        drawCenteredButton(graphics, mouseX, mouseY, px0, px1, py0 + 44,
                                        Component.translatable("chunker_bedrock_importer.screen.close"), 0xFF444444);
                } else {
                        Component label = "extracting".equals(current.message)
                                        ? Component.translatable("chunker_bedrock_importer.screen.extracting")
                                        : "compacting".equals(current.message)
                                        ? Component.translatable("chunker_bedrock_importer.screen.compacting")
                                        : Component.translatable("chunker_bedrock_importer.screen.converting", current.worldName);
                        graphics.centeredText(this.font, label, this.width / 2, py0 + 16, 0xFFFFFFFF);

                        int barX0 = px0 + 24;
                        int barX1 = px1 - 24;
                        int barY = py0 + 44;
                        graphics.fill(barX0, barY, barX1, barY + 8, 0xFF333333);
                        int progressWidth = (int) Math.round((barX1 - barX0) * Math.max(0.0D, Math.min(1.0D, current.progress)));
                        if (progressWidth > 0) {
                                graphics.fill(barX0, barY, barX0 + progressWidth, barY + 8, 0xFF3C8527);
                        }
                        String percent = Math.round(current.progress * 100.0D) + "%";
                        graphics.centeredText(this.font, Component.literal(percent), this.width / 2, barY + 14, 0xFFFFFF55);
                        graphics.centeredText(this.font, Component.translatable("chunker_bedrock_importer.screen.background_hint"),
                                        this.width / 2, py1 - 16, 0xFF888888);
                }
        }

        private boolean drawCenteredButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int px0, int px1, int y,
                                           Component label, int color) {
                int bx0 = this.width / 2 - 70;
                int bx1 = this.width / 2 + 70;
                boolean hover = mouseX >= bx0 && mouseX <= bx1 && mouseY >= y && mouseY < y + 20;
                graphics.fill(bx0, y, bx1, y + 20, hover ? lighten(color) : color);
                graphics.centeredText(this.font, label, this.width / 2, y + 6, 0xFFFFFFFF);
                return hover;
        }

        private static int lighten(int color) {
                int r = Math.min(255, ((color >> 16) & 0xFF) + 30);
                int g = Math.min(255, ((color >> 8) & 0xFF) + 30);
                int b = Math.min(255, (color & 0xFF) + 30);
                return 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        // ------------------------------------------------------------------ input

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
                if (this.job != null) {
                        int py0 = this.height / 2 - 70;
                        int buttonY = py0 + 44;
                        if (event.y() >= buttonY && event.y() < buttonY + 20 && Math.abs(event.x() - this.width / 2) <= 70) {
                                if (this.job.state == ConversionJob.State.COMPLETED) {
                                        this.job.claimed = true;
                                        openWorld(this.job);
                                } else if (this.job.state == ConversionJob.State.FAILED) {
                                        this.job.claimed = true;
                                        activeJob = null;
                                        this.job = null;
                                        startNextDropped(); // chain the next dropped .mcworld, if any
                                }
                                return true;
                        }
                        return true; // consume clicks while the overlay is visible
                }

                int listTop = LIST_TOP;
                int listBottom = this.height - LIST_BOTTOM_MARGIN;
                int x0 = this.width / 2 - LIST_HALF_WIDTH;
                int x1 = this.width / 2 + LIST_HALF_WIDTH;
                if (event.x() >= x0 && event.x() <= x1 && event.y() >= listTop && event.y() < listBottom) {
                        int bx1 = x1 - 6;
                        int bx0 = bx1 - IMPORT_BUTTON_WIDTH;
                        int rowY = listTop + 18 - (int) this.scroll;
                        for (BedrockWorld world : this.worlds) {
                                if (event.y() >= rowY + 4 && event.y() < rowY + 24 && event.x() >= bx0 && event.x() <= bx1) {
                                        startImport(world);
                                        return true;
                                }
                                rowY += ROW_HEIGHT;
                        }
                }
                return super.mouseClicked(event, doubled);
        }

        // ------------------------------------------------------------------ drag & drop

        @Override
        public void onFilesDrop(List<Path> files) {
                for (Path file : files) {
                        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (fileName.endsWith(".mcworld")) {
                                this.dropQueue.add(file);
                        }
                }
                if (this.dropQueue.isEmpty()) return;

                // Show the dropped files' folder in the path field for context
                Path first = this.dropQueue.peek();
                Path parent = first.getParent();
                if (parent != null && pathBox != null) {
                        pathBox.setValue(parent.toString());
                }
                startNextDropped();
        }

        /** Starts the conversion of the next dropped file, if no job is running. */
        private void startNextDropped() {
                if (this.job != null) return;
                Path next = this.dropQueue.poll();
                if (next == null) return;
                try {
                        String fileName = next.getFileName().toString();
                        String name = fileName.endsWith(".mcworld")
                                        ? fileName.substring(0, fileName.length() - ".mcworld".length())
                                        : fileName;
                        BedrockWorld world = new BedrockWorld(next, name, Files.size(next), Files.getLastModifiedTime(next).toMillis(), true);
                        startImport(world);
                } catch (Exception e) {
                        ChunkerBedrockImporter.LOGGER.error("Failed to import dropped file {}", next, e);
                        startNextDropped();
                }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
                if (this.job == null && !this.worlds.isEmpty()) {
                        int listTop = LIST_TOP;
                        int listBottom = this.height - LIST_BOTTOM_MARGIN;
                        int contentHeight = 18 + this.worlds.size() * ROW_HEIGHT + 8;
                        int visibleHeight = listBottom - listTop;
                        int maxScroll = Math.max(0, contentHeight - visibleHeight);
                        this.scroll = Math.max(0.0D, Math.min(maxScroll, this.scroll - scrollY * 24.0D));
                        return true;
                }
                return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public void onClose() {
                // Return to a freshly scanned world list (so the converted world shows up)
                this.minecraft.setScreenAndShow(new SelectWorldScreen(null));
        }

        // ------------------------------------------------------------------ conversion

        private void startImport(BedrockWorld world) {
                if (activeJob != null && !activeJob.claimed
                                && activeJob.state != ConversionJob.State.COMPLETED && activeJob.state != ConversionJob.State.FAILED) {
                        return; // a conversion is already running
                }
                try {
                        Path savesDir = this.minecraft.getLevelSource().getBaseDir();
                        String baseName = sanitizeLevelName(world.name());
                        String levelId = baseName;
                        int counter = 2;
                        while (Files.exists(savesDir.resolve(levelId))) {
                                levelId = baseName + " (" + counter++ + ")";
                        }
                        Path output = savesDir.resolve(levelId);

                        ConversionJob newJob = new ConversionJob(world.path(), output, levelId);
                        newJob.worldName = world.name();
                        newJob.onComplete = () -> this.minecraft.execute(() -> {
                                if (newJob.state == ConversionJob.State.COMPLETED && config.autoOpen
                                                && this.minecraft.level == null) {
                                        newJob.claimed = true;
                                        openWorld(newJob);
                                }
                        });

                        activeJob = newJob;
                        this.job = newJob;
                        Thread thread = new Thread(newJob::run, "Chunker-Bedrock-Import");
                        thread.setDaemon(true);
                        thread.start();
                } catch (Exception e) {
                        ChunkerBedrockImporter.LOGGER.error("Failed to start conversion", e);
                }
        }

        private void openWorld(ConversionJob finishedJob) {
                this.minecraft.createWorldOpenFlows().openWorld(finishedJob.levelId,
                                () -> this.minecraft.setScreenAndShow(new SelectWorldScreen(null)));
        }

        private static String sanitizeLevelName(String name) {
                String cleaned = name.replaceAll("[\\\\/:*?\"<>|.]", " ").replaceAll("\\s+", " ").trim();
                if (cleaned.isBlank()) cleaned = "Bedrock World";
                if (cleaned.length() > 50) cleaned = cleaned.substring(0, 50).trim();
                return cleaned;
        }

        private static String formatSize(long bytes) {
                if (bytes <= 0) return "?";
                if (bytes < 1024L * 1024L) return (bytes / 1024L) + " KB";
                if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0D * 1024.0D));
                return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0D * 1024.0D * 1024.0D));
        }
}
