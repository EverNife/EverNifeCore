package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.evernifecore.storage.StorageConfigException;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two file backends over one directory. The failure they cause is invisible - independent lock maps
 * over the same files, and each listing the other's as its own - so the config has to be refused
 * before anything opens, which is what these pin down.
 */
@ECoreTest
class FileBackendDirectoryGuardTest {

    @TempDir
    Path tempDir;

    /** A groupedfile primary plus one extra file backend, both enabled, at the given paths. */
    private File writeTwoFileBackends(String primaryPath, String extraType, String extraPath) {
        return Storages.groupedFile()
                .dataPath(primaryPath)
                .extraBackend("second",
                        "    type: " + extraType,
                        "    path: \"" + absolute(extraPath) + "\"",
                        "    format: yaml")
                .fileName("two_backends.yml")
                .writeTo(tempDir);
    }

    private String absolute(String relative) {
        return tempDir.resolve(relative).toString().replace("\\", "/");
    }

    @Test
    void twoFileBackendsOnTheSameDirectoryCancelTheBoot() {
        File yml = writeTwoFileBackends("shared", "localfile", "shared");

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(yml));

        String report = error.getMessage();
        assertTrue(report.contains("grouped"), report);
        assertTrue(report.contains("second"), report);
        assertTrue(report.contains("the SAME directory"), report);
        //the absolute resolved path, not the raw yaml string: that is what the backend will open
        assertTrue(report.contains(tempDir.resolve("shared").toAbsolutePath().normalize().toString()), report);
    }

    @Test
    void aDirectoryInsideAnotherCancelsTheBoot() {
        File yml = writeTwoFileBackends("outer", "localfile", "outer/inner");

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(yml));
        assertTrue(error.getMessage().contains("sits INSIDE the directory of"), error.getMessage());
    }

    @Test
    void nestingIsCaughtInTheOtherDirectionToo() {
        //the primary is the INNER one here: the guard must not depend on declaration order
        File yml = writeTwoFileBackends("outer/inner", "localfile", "outer");

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(yml));
        assertTrue(error.getMessage().contains("sits INSIDE the directory of"), error.getMessage());
    }

    @Test
    void aDisabledBackendOverTheSameDirectoryIsFine() {
        File yml = Storages.groupedFile()
                .dataPath("shared")
                .extraBackendDisabled("second",
                        "    type: localfile",
                        "    path: \"" + absolute("shared") + "\"",
                        "    format: yaml")
                .fileName("disabled_pair.yml")
                .writeTo(tempDir);

        //a backend that is never opened cannot collide with anything
        ParsedStorageConfig parsed = StorageYamlParser.parse(yml);
        assertEquals(2, parsed.getBackends().size());
    }

    @Test
    void anH2FileInsideAFileBackendDirectoryIsFine() {
        File yml = Storages.groupedFile()
                .dataPath("shared")
                .extraBackend("embedded",
                        "    type: h2",
                        "    url: \"jdbc:h2:file:./" + absolute("shared") + "/h2database\"")
                .fileName("h2_inside.yml")
                .writeTo(tempDir);

        //h2's database file does not carry the extension a file backend lists, so neither one ever
        //opens the other's - untidy, not corrupting, and deliberately not refused
        ParsedStorageConfig parsed = StorageYamlParser.parse(yml);
        assertNotNull(parsed.getBackend("embedded").orElse(null));
    }

    @Test
    void twoBackendsInSiblingDirectoriesAreFine() {
        File yml = writeTwoFileBackends("PlayerData", "groupedfile", "NetworkData");

        //the factory default's own shape: siblings under one parent, neither inside the other
        ParsedStorageConfig parsed = StorageYamlParser.parse(yml);
        assertEquals(2, parsed.getBackends().size());
    }

    @Test
    void noReportLineCarriesAConsoleColourCode() {
        File yml = writeTwoFileBackends("shared", "localfile", "shared");

        StorageConfigException error = assertThrows(StorageConfigException.class,
                () -> StorageYamlParser.parse(yml));

        //the report goes to log files and to consoles that do not interpret the section sign
        assertTrue(error.getMessage().indexOf('§') < 0, error.getMessage());
    }
}
