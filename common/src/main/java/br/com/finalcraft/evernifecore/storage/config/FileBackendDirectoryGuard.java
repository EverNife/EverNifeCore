package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.storage.StorageBootReport;
import br.com.finalcraft.evernifecore.storage.StorageConfigException;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Refuses two ENABLED file backends that resolve to the same directory, or to one directory inside
 * the other. It is a config error, detectable without touching disk, so it runs with the rest of the
 * storage.yml validation - before anything connects.
 *
 * <p>Fatal rather than a warning because the symptom is silent write loss: a file backend keys its
 * locks inside a store owned by ITS Storage instance, so two backends over one directory are two
 * independent lock maps over the same files - concurrent writes to one key overwrite each other with
 * no error, and each backend lists the other's files as its own.
 *
 * <p>An h2 file inside such a directory is allowed: its database file does not match the extension a
 * file backend lists, so it is neither read nor overwritten.
 */
public final class FileBackendDirectoryGuard {

    private FileBackendDirectoryGuard() {
    }

    /**
     * @param backends       every declared backend; the disabled ones are skipped, since a backend
     *                       that is never opened cannot collide with anything
     * @param storageYmlFile the file to name in the report, or {@code null} for the generic name
     * @throws StorageConfigException carrying the rendered report when any two of them overlap
     */
    public static void check(Map<String, BackendDefinition> backends, File storageYmlFile) {
        List<BackendDefinition> fileBackends = new ArrayList<>();
        for (BackendDefinition backend : backends.values()) {
            if (backend.isEnabled() && backend.getType().isFileBacked()) {
                fileBackends.add(backend);
            }
        }

        List<Overlap> overlaps = new ArrayList<>();
        for (int i = 0; i < fileBackends.size(); i++) {
            for (int j = i + 1; j < fileBackends.size(); j++) {
                BackendDefinition first = fileBackends.get(i);
                BackendDefinition second = fileBackends.get(j);
                Path firstDir = resolve(first);
                Path secondDir = resolve(second);
                //equality falls out of startsWith in both directions; it is tested apart only so the
                //report can say WHICH of the two shapes the admin is looking at
                if (firstDir.equals(secondDir)) {
                    overlaps.add(new Overlap(first, firstDir, second, secondDir, true));
                } else if (firstDir.startsWith(secondDir) || secondDir.startsWith(firstDir)) {
                    overlaps.add(new Overlap(first, firstDir, second, secondDir, false));
                }
            }
        }

        if (!overlaps.isEmpty()) {
            throw new StorageConfigException(
                    String.join("\n", StorageBootReport.renderOverlappingDirectories(overlaps, storageYmlFile)));
        }
    }

    /**
     * The directory a file backend will actually open. Absolute and normalized, never {@code toRealPath}:
     * on a first boot the directory does not exist yet, and resolving a path that is not there throws.
     */
    private static Path resolve(BackendDefinition backend) {
        try {
            return Paths.get(backend.getPath()).toAbsolutePath().normalize();
        } catch (InvalidPathException notAPath) {
            //the storage would fail on the same call when it opened; failing here just means the
            //message can still name which backend and which key the admin has to go and fix
            throw new StorageConfigException("Backend '" + backend.getName() + "' has a 'path' that is not"
                    + " a valid path on this system: '" + backend.getPath() + "'", notAPath);
        }
    }

    /** Two enabled file backends whose directories cannot coexist, and the resolved paths that prove it. */
    public static final class Overlap {

        private final BackendDefinition first;
        private final Path firstDirectory;
        private final BackendDefinition second;
        private final Path secondDirectory;
        private final boolean sameDirectory;

        Overlap(BackendDefinition first, Path firstDirectory,
                BackendDefinition second, Path secondDirectory, boolean sameDirectory) {
            this.first = first;
            this.firstDirectory = firstDirectory;
            this.second = second;
            this.secondDirectory = secondDirectory;
            this.sameDirectory = sameDirectory;
        }

        public BackendDefinition getFirst() {
            return first;
        }

        public Path getFirstDirectory() {
            return firstDirectory;
        }

        public BackendDefinition getSecond() {
            return second;
        }

        public Path getSecondDirectory() {
            return secondDirectory;
        }

        /** True when both resolve to the very same directory; false when one merely sits inside the other. */
        public boolean isSameDirectory() {
            return sameDirectory;
        }

        /** The backend whose directory contains the other's, or {@code null} when they are the same one. */
        public BackendDefinition getOuter() {
            if (sameDirectory) {
                return null;
            }
            return firstDirectory.startsWith(secondDirectory) ? second : first;
        }

        /** The backend nested inside the other's directory, or {@code null} when they are the same one. */
        public BackendDefinition getInner() {
            if (sameDirectory) {
                return null;
            }
            return firstDirectory.startsWith(secondDirectory) ? first : second;
        }
    }
}
