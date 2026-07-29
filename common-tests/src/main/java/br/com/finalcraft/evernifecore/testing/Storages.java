package br.com.finalcraft.evernifecore.testing;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Writes the {@code storage.yml} a test boots against.
 *
 * <p>The backends themselves come from EveryDatabase - this class owns no persistence, only the
 * handful of lines that tell EverNifeCore which one to use. That is the part every storage test
 * used to copy.</p>
 *
 * <p>The mandatory {@code network:} block is written for you, pointing at the one declared backend.
 * Only a test about the block itself needs {@link #networkBackendId} or {@link #withoutNetworkBlock}.</p>
 *
 * <pre>{@code
 * File yml = Storages.h2("my_test_db").writeTo(tempDir);
 * File yml = Storages.localFile().loadMode("RECENT", 60).writeTo(tempDir);
 * }</pre>
 */
public final class Storages {

    private final String backendId;
    private final List<String> backendLines = new ArrayList<String>();
    private final List<String> extraBackendLines = new ArrayList<String>();
    private final List<String> playerdataLines = new ArrayList<String>();
    private final List<String> networkLines = new ArrayList<String>();
    private final List<String> rawTopLevelLines = new ArrayList<String>();
    private String fileName = "storage.yml";
    private String jdbcUrl;
    private String pathPlaceholder;
    private String networkBackendId;      // null before networkBackendId(); resolved to backendId at write
    private boolean writeNetworkBlock = true;

    private Storages(String backendId) {
        this.backendId = backendId;
    }

    /** One YAML file per collection under {@code <dir>/storagedata}. */
    public static Storages localFile() {
        Storages storages = new Storages("test_files");
        storages.pathPlaceholder = "storagedata";
        storages.backendLines.add("    type: localfile");
        storages.backendLines.add("    path: \"${path}\"");
        storages.backendLines.add("    format: yaml");
        return storages;
    }

    /** One YAML file per player key under {@code <dir>/StorageData} - the factory default backend. */
    public static Storages groupedFile() {
        Storages storages = new Storages("grouped");
        storages.pathPlaceholder = "StorageData";
        storages.backendLines.add("    type: groupedfile");
        storages.backendLines.add("    path: \"${path}\"");
        storages.backendLines.add("    format: yaml");
        storages.playerdataLines.add("  storage-backend-id: grouped");
        storages.playerdataLines.add("  collection: evernifecore_playerdata");
        return storages;
    }

    /**
     * An in-memory H2 database. {@code dbName} has to be unique per test that expects a fresh
     * database: {@code DB_CLOSE_DELAY=-1} keeps it alive for the whole JVM, so two tests sharing a
     * name share the data.
     */
    public static Storages h2(String dbName) {
        Storages storages = new Storages("test_h2");
        storages.fileName = "storage_h2_" + dbName + ".yml";
        storages.jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        storages.backendLines.add("    type: h2");
        storages.backendLines.add("    url: \"" + storages.jdbcUrl + "\"");
        return storages;
    }

    /** EveryDatabase's in-memory backend: nothing on disk, nothing survives the process. */
    public static Storages memory() {
        Storages storages = new Storages("test_memory");
        storages.backendLines.add("    type: memory");
        return storages;
    }

    /** Renames the backend, for a test that asserts on the id itself. */
    public Storages backendId(String backendId) {
        Storages renamed = new Storages(backendId);
        renamed.backendLines.addAll(backendLines);
        renamed.extraBackendLines.addAll(extraBackendLines);
        renamed.playerdataLines.addAll(playerdataLines);
        renamed.networkLines.addAll(networkLines);
        renamed.rawTopLevelLines.addAll(rawTopLevelLines);
        renamed.fileName = fileName;
        renamed.pathPlaceholder = pathPlaceholder;
        renamed.jdbcUrl = jdbcUrl;
        renamed.networkBackendId = networkBackendId;
        renamed.writeNetworkBlock = writeNetworkBlock;
        return renamed;
    }

    /**
     * Renames the sub-directory of {@code baseDir} a file backend roots itself in. Two configurations
     * that must not share a directory - or must - say so through this.
     */
    public Storages dataPath(String subDirectory) {
        this.pathPlaceholder = subDirectory;
        return this;
    }

    /**
     * Declares a SECOND enabled backend alongside the primary one - what a test needs to move data
     * between backends, or to prove two of them collide. {@code lines} are the entry's own fields,
     * four-space indented and {@code ${path}}-aware, exactly like the primary backend's.
     */
    public Storages extraBackend(String id, String... lines) {
        extraBackendLines.add("  " + id + ":");
        extraBackendLines.add("    enabled: true");
        Collections.addAll(extraBackendLines, lines);
        return this;
    }

    /** As {@link #extraBackend}, but declared {@code enabled: false}. */
    public Storages extraBackendDisabled(String id, String... lines) {
        extraBackendLines.add("  " + id + ":");
        extraBackendLines.add("    enabled: false");
        Collections.addAll(extraBackendLines, lines);
        return this;
    }

    /**
     * Points {@code network.storage-backend-id} at a backend other than the primary one - normally
     * one declared through {@link #extraBackend}. Without this the network block names the primary
     * backend, which is what a single-backend test wants.
     */
    public Storages networkBackendId(String backendId) {
        this.networkBackendId = backendId;
        return this;
    }

    /** Raw {@code network:} lines, for a key this class does not model yet. Two-space indented. */
    public Storages networkLines(String... lines) {
        Collections.addAll(networkLines, lines);
        return this;
    }

    /**
     * Writes NO {@code network:} block at all. Only a test asserting that the missing block cancels
     * the boot wants this - the block is required, so every other configuration is unbootable.
     */
    public Storages withoutNetworkBlock() {
        this.writeNetworkBlock = false;
        return this;
    }

    /** {@code playerdata.load-mode: ALL} - every player is loaded at boot. */
    public Storages loadModeAll() {
        playerdataLines.add("  load-mode: ALL");
        return this;
    }

    /** {@code playerdata.load-mode: RECENT} plus the day window; the rest loads lazily. */
    public Storages loadModeRecent(int recentDays) {
        playerdataLines.add("  load-mode: RECENT");
        playerdataLines.add("  recent-days: " + recentDays);
        return this;
    }

    /** Raw {@code playerdata:} lines, for a key this class does not model yet. Two-space indented. */
    public Storages playerdataLines(String... lines) {
        Collections.addAll(playerdataLines, lines);
        return this;
    }

    /** Raw top-level YAML lines, for a section this class does not model (e.g. {@code schema:}). */
    public Storages rawLines(String... lines) {
        Collections.addAll(rawTopLevelLines, lines);
        return this;
    }

    /** The file this configuration writes to inside the target directory. */
    public Storages fileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    /**
     * The JDBC url this configuration points at, or {@code null} for a backend that has none. A
     * test that reaches into the database directly - to corrupt a row, say - needs the same url
     * the boot used.
     */
    public String jdbcUrl() {
        return jdbcUrl;
    }

    /** The YAML text, with any data path resolved against {@code baseDir}. */
    public String toYaml(Path baseDir) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("storage-backends:\n");
        yaml.append("  ").append(backendId).append(":\n");
        yaml.append("    enabled: true\n");
        for (String line : backendLines) {
            yaml.append(resolvePath(line, baseDir)).append('\n');
        }
        for (String line : extraBackendLines) {
            yaml.append(resolvePath(line, baseDir)).append('\n');
        }
        yaml.append("default-backend: ").append(backendId).append('\n');
        //required by the parser, so it is written by default: a test that does not care about the
        //network family still has to boot, and pointing it at the one declared backend is the answer
        if (writeNetworkBlock) {
            yaml.append("network:\n");
            yaml.append("  storage-backend-id: ")
                    .append(networkBackendId != null ? networkBackendId : backendId).append('\n');
            for (String line : networkLines) {
                yaml.append(line).append('\n');
            }
        }
        for (String line : rawTopLevelLines) {
            yaml.append(line).append('\n');
        }
        if (!playerdataLines.isEmpty()) {
            yaml.append("playerdata:\n");
            for (String line : playerdataLines) {
                yaml.append(line).append('\n');
            }
        }
        return yaml.toString();
    }

    /** Writes the configuration into {@code baseDir} and returns the file to hand to the boot. */
    public File writeTo(Path baseDir) {
        File file = baseDir.resolve(fileName).toFile();
        try {
            Files.write(file.toPath(), toYaml(baseDir).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file, e);
        }
        return file;
    }

    private String resolvePath(String line, Path baseDir) {
        if (pathPlaceholder == null) {
            return line;
        }
        //forward slashes: a Windows path with backslashes would need YAML escaping
        String dataPath = baseDir.resolve(pathPlaceholder).toString().replace("\\", "/");
        return line.replace("${path}", dataPath);
    }
}
