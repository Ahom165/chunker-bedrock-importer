package com.zai.chunkerimporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple JSON config stored in config/chunker_bedrock_importer.json.
 */
public class ChunkerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "chunker_bedrock_importer.json";

	/** Custom folder that contains Bedrock worlds (or a .mcworld file). Empty = auto-detect. */
	public String bedrockWorldsFolder = "";

	/** Automatically open the converted world once the conversion finishes. */
	public boolean autoOpen = true;

	public static ChunkerConfig load() {
		Path path = configPath();
		try {
			if (Files.exists(path)) {
				ChunkerConfig config = GSON.fromJson(Files.readString(path), ChunkerConfig.class);
				if (config != null) return config;
			}
		} catch (Exception e) {
			ChunkerBedrockImporter.LOGGER.warn("Failed to read config, using defaults", e);
		}
		return new ChunkerConfig();
	}

	public void save() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch (IOException e) {
			ChunkerBedrockImporter.LOGGER.warn("Failed to save config", e);
		}
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}
}
