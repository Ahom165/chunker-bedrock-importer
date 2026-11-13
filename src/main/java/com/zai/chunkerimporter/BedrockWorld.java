package com.zai.chunkerimporter;

import java.nio.file.Path;

/**
 * A Bedrock world discovered on disk (either a folder with a LevelDB "db" directory
 * or a .mcworld zip archive).
 *
 * @param path         path to the world folder or .mcworld file
 * @param name         world name (from Bedrock level.dat "LevelName") or file name
 * @param size         approximate size in bytes
 * @param lastModified last modified timestamp
 * @param mcworld      true if the world is a .mcworld zip archive
 */
public record BedrockWorld(Path path, String name, long size, long lastModified, boolean mcworld) {
}
