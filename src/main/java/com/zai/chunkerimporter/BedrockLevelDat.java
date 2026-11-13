package com.zai.chunkerimporter;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Minimal parser for the Bedrock Edition level.dat file.
 *
 * Bedrock level.dat layout: an 8-byte header (little-endian int "storage version",
 * then little-endian int "length"), followed by uncompressed little-endian NBT.
 * We only need the "LevelName" string tag, so unknown payloads are skipped.
 */
public final class BedrockLevelDat {
        private BedrockLevelDat() {
        }

        /**
         * Read the "LevelName" tag from a Bedrock level.dat file.
         *
         * @param levelDatFile path to level.dat
         * @return the level name, or null if it could not be parsed
         */
        public static String readLevelName(Path levelDatFile) {
                try {
                        byte[] bytes = Files.readAllBytes(levelDatFile);
                        if (bytes.length <= 8) return null;
                        byte[] header = new byte[8];
                        byte[] payload = new byte[bytes.length - 8];
                        System.arraycopy(bytes, 0, header, 0, 8);
                        System.arraycopy(bytes, 8, payload, 0, payload.length);

                        // Try raw little-endian NBT first (standard Bedrock level.dat), then compressed variants
                        Exception lastFailure;
                        try {
                                return parse(payload);
                        } catch (Exception rawFailure) {
                                lastFailure = rawFailure;
                        }
                        for (boolean gzip : new boolean[]{true, false}) {
                                try {
                                        InputStream decompressed = gzip
                                                        ? new GZIPInputStream(new ByteArrayInputStream(payload))
                                                        : new InflaterInputStream(new ByteArrayInputStream(payload));
                                        return parse(decompressed.readAllBytes());
                                } catch (Exception compressedFailure) {
                                        lastFailure = compressedFailure;
                                }
                        }
                        throw lastFailure;
                } catch (Exception e) {
                        ChunkerBedrockImporter.LOGGER.debug("Failed to parse Bedrock level.dat {}", levelDatFile, e);
                        return null;
                }
        }

        private static String parse(byte[] bytes) throws IOException {
                if (bytes.length < 4) throw new IOException("level.dat payload too small");
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
                LevelNameReader reader = new LevelNameReader(in);
                reader.parseRoot();
                return reader.levelName;
        }

        private static final class LevelNameReader {
                private final DataInputStream in;
                String levelName;

                LevelNameReader(DataInputStream in) {
                        this.in = in;
                }

                void parseRoot() throws IOException {
                        int type = in.readUnsignedByte();
                        if (type != 10) throw new IOException("Root tag is not a compound (" + type + ")");
                        readString(); // root name
                        readCompoundPayload();
                }

                private void readCompoundPayload() throws IOException {
                        while (true) {
                                int type = in.readUnsignedByte();
                                if (type == 0) return; // TAG_End
                                lastKeyName = readString(); // tag name
                                if (type == 8) {
                                        String value = readString();
                                        if (levelName == null && "LevelName".equals(lastKeyName)) {
                                                levelName = value;
                                        }
                                } else if (type == 9) {
                                        readListPayload();
                                } else if (type == 10) {
                                        readCompoundPayload();
                                } else {
                                        skipPayload(type);
                                }
                        }
                }

                private String lastKeyName = "";

                private void readListPayload() throws IOException {
                        int elementType = in.readUnsignedByte();
                        int length = readIntLE();
                        if (length <= 0) return;
                        if (elementType == 9) {
                                for (int i = 0; i < length; i++) readListPayload();
                        } else if (elementType == 10) {
                                for (int i = 0; i < length; i++) readCompoundPayload();
                        } else if (elementType == 8) {
                                for (int i = 0; i < length; i++) readString();
                        } else {
                                for (int i = 0; i < length; i++) skipPayload(elementType);
                        }
                }

                private void skipPayload(int type) throws IOException {
                        switch (type) {
                                case 1 -> in.skipBytes(1);
                                case 2 -> in.skipBytes(2);
                                case 3, 5 -> skipFully(4);
                                case 4, 6 -> skipFully(8);
                                case 7 -> skipFully(readIntLE() & 0xFFFFFFFFL); // byte array
                                case 8 -> readString();
                                case 11 -> skipFully((readIntLE() & 0xFFFFFFFFL) * 4L); // int array
                                case 12 -> skipFully((readIntLE() & 0xFFFFFFFFL) * 8L); // long array
                                default -> throw new IOException("Unknown NBT tag type " + type);
                        }
                }

                private void skipFully(long count) throws IOException {
                        long remaining = count;
                        while (remaining > 0) {
                                long skipped = in.skip(remaining);
                                if (skipped <= 0) throw new IOException("Unexpected end of level.dat");
                                remaining -= skipped;
                        }
                }

                private String readString() throws IOException {
                        int length = readUnsignedShortLE();
                        byte[] bytes = new byte[length];
                        in.readFully(bytes);
                        return new String(bytes, StandardCharsets.UTF_8);
                }

                private int readUnsignedShortLE() throws IOException {
                        int b1 = in.readUnsignedByte();
                        int b2 = in.readUnsignedByte();
                        return (b2 << 8) | b1;
                }

                private int readIntLE() throws IOException {
                        return Integer.reverseBytes(in.readInt());
                }
        }
}
