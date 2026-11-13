package com.zai.chunkerimporter;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint of the mod.
 *
 * The mod adds an "Import Bedrock" button to the singleplayer world selection
 * screen (via a mixin). From the import screen, Bedrock worlds are converted to
 * Java Edition using the embedded Chunker engine (https://github.com/HiveGamesOSS/Chunker),
 * written into the "saves" folder and can be opened immediately.
 */
public class ChunkerBedrockImporter implements ClientModInitializer {
	public static final String MOD_ID = "chunker_bedrock_importer";
	public static final Logger LOGGER = LoggerFactory.getLogger("ChunkerBedrockImporter");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Chunker Bedrock Importer loaded (engine: HiveGamesOSS/Chunker). Use the 'Import Bedrock' button on the world selection screen.");
	}
}
