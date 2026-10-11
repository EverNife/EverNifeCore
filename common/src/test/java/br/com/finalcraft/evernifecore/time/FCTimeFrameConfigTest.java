package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.factory.ConfigFactoryCodec;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.everyconfig.config.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link FCTimeFrame} as a config type: the readable form both ways, ISO on read, a useful refusal. */
@ECoreTest
class FCTimeFrameConfigTest {

    private static final ObjectMapper MAPPER = ConfigFactory.inMemoryCodec().getObjectMapper();

    private static long read(String text) throws Exception {
        return MAPPER.readValue("\"" + text + "\"", FCTimeFrame.class).getMillis();
    }

    @Test
    void readsTheReadableFormAndIso() throws Exception {
        assertEquals(2_000, read("2s"));
        assertEquals(500, read("500ms"));
        assertEquals(180_000, read("3m"));
        assertEquals(5_400_000, read("1h30m"));
        assertEquals(86_400_000, read("1d"));
        assertEquals(2_000, read("PT2S"));
    }

    @Test
    void writesReadableAndReadsBackTheSame(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("times.yml");
        Config config = ConfigFactory.open(file);
        config.setValue("half", FCTimeFrame.of(500));
        config.setValue("long", FCTimeFrame.of(Duration.ofMinutes(90)));
        config.save();

        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(text.contains("half: 500ms"), text);
        assertTrue(text.contains("long: 1h 30m"), text);
        Config reopened = ConfigFactory.open(file);
        assertEquals(500, reopened.getValue("half", FCTimeFrame.class).getMillis());
        assertEquals(Duration.ofMinutes(90), reopened.getValue("long", FCTimeFrame.class).toDuration());
    }

    @Test
    void unreadableTextFailsNamingItAndTheFormat() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> FCTimeFrame.fromConfigString("abc"));
        assertTrue(refused.getMessage().contains("'abc'"), refused.getMessage());
        assertTrue(refused.getMessage().contains("500ms") && refused.getMessage().contains("1h30m"), refused.getMessage());

        Exception viaMapper = assertThrows(Exception.class, () -> read("abc"));
        assertTrue(viaMapper.getMessage().contains("'abc' is not a duration"), viaMapper.getMessage());
    }

    public static class Timed {
        public Duration wait = Duration.ofSeconds(2);
    }

    @Test
    void aPlainDurationKeepsItsIsoFormInConfigAndStorage() throws Exception {
        String inMemory = MAPPER.writeValueAsString(new Timed());
        assertTrue(inMemory.contains("wait: PT2S"), inMemory);
        String stored = new String(ConfigFactoryCodec.json(Timed.class).encode(new Timed()), StandardCharsets.UTF_8);
        assertTrue(stored.contains("\"PT2S\""), stored);
    }
}
