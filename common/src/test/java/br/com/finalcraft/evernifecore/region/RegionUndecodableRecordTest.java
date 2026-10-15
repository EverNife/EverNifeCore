package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.storage.ECStorage;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.everydatabase.modules.localfile.LocalFileConfig;
import br.com.finalcraft.everydatabase.modules.localfile.LocalFileStorage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One stored region that does not even decode costs only itself: the others load and it stays in storage as it was. */
class RegionUndecodableRecordTest extends ManagedRegionFixture {

    @Test
    void aRecordThatDoesNotDecodeIsSkippedLoggedByIdAndLeftInStorage() throws IOException {
        Path files = tempDir.resolve("files");
        ScriptedStorage fileBackend = ScriptedStorage.wrapping(new LocalFileStorage(new LocalFileConfig(files)));
        fileBackend.init().join();
        ECStorage fileStorage = track(fileBackend.asECStorage());

        RegionManager first = loaded(RegionManager.of(owner, fileStorage));
        created(first.create("good", WORLD, box(0, 0, 9, 9)));
        created(first.create("broken", WORLD, box(20, 20, 29, 29)));
        first.flush().join();
        first.close().join();

        Path brokenFile = fileOf(files, "broken");
        String text = new String(Files.readAllBytes(brokenFile), StandardCharsets.UTF_8);
        String corrupted = text.substring(0, text.length() / 2);   //cut in half, as a crash mid-write leaves it
        Files.write(brokenFile, corrupted.getBytes(StandardCharsets.UTF_8));

        //a restart: a backend opened afresh reads the files, not what the first one kept in memory
        ScriptedStorage restarted = ScriptedStorage.wrapping(new LocalFileStorage(new LocalFileConfig(files)));
        restarted.init().join();
        RegionManager second = RegionManager.of(owner, track(restarted.asECStorage()));
        List<String> logged = Logs.capture(() -> second.load().join());

        assertNotNull(second.get("good"), "the readable region loads");
        assertNull(second.get("broken"));
        assertTrue(logged.stream().anyMatch(line -> line.contains("'broken'") && line.contains("stays in storage untouched")),
                "the log names the region it skipped: " + logged);
        assertEquals(corrupted, new String(Files.readAllBytes(brokenFile), StandardCharsets.UTF_8), "the record is stored as it was");
    }

    private static Path fileOf(Path root, String name) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> matches = walk.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().startsWith(name + "."))
                    .collect(Collectors.toList());
            assertEquals(1, matches.size(), "one stored file for '" + name + "' under " + root + ": " + matches);
            return matches.get(0);
        }
    }
}
