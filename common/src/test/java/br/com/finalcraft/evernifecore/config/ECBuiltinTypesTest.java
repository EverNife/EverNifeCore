package br.com.finalcraft.evernifecore.config;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.GenericCooldown;
import br.com.finalcraft.evernifecore.fancytext.ClickActionType;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.time.DayOfToday;
import br.com.finalcraft.everylibs.util.FCTimeUtil;
import br.com.finalcraft.everylibs.util.numberwrapper.NumberWrapper;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the built-in {@code ECBuiltinTypes} registrations: canonical round-trips plus the legacy
 * READ-compat contract (a position stored as a legacy map OR a legacy string must still read).
 */
@ECoreTest
class ECBuiltinTypesTest {


    /** Open a fresh Config over a brand-new file using the framework's type-aware YAML codec. */
    private Config open(Path dir) {
        return Config.open(dir.resolve("data.yml"), ConfigFactory.codecForFile("data.yml"));
    }

    // ==================== position round-trips + compat ====================

    @Test
    void worldBlockPosRoundTrips(@TempDir Path dir) {
        WorldBlockPos pos = new WorldBlockPos(10, 64, -30, "world");

        Config cfg = open(dir);
        cfg.setValue("pos", pos);
        cfg.save();

        Config reopened = open(dir);
        WorldBlockPos read = reopened.getValue("pos", WorldBlockPos.class);
        assertEquals(pos, read);
    }

    @Test
    void worldBlockPosReadsLegacyMapForm(@TempDir Path dir) {
        // A file the legacy engine wrote as a MAP (worldName/x/y/z).
        Map<String, Object> legacyMap = new LinkedHashMap<>();
        legacyMap.put("x", 5);
        legacyMap.put("y", 72);
        legacyMap.put("z", 9);
        legacyMap.put("worldName", "nether");

        Config cfg = open(dir);
        cfg.setValue("pos", legacyMap);
        cfg.save();

        Config reopened = open(dir);
        WorldBlockPos read = reopened.getValue("pos", WorldBlockPos.class);
        assertEquals(new WorldBlockPos(5, 72, 9, "nether"), read);
    }

    @Test
    void worldBlockPosReadsLegacyStringForm(@TempDir Path dir) {
        // A file the legacy engine wrote as a single scalar string via serialize().
        WorldBlockPos original = new WorldBlockPos(-7, 15, 128, "end");

        Config cfg = open(dir);
        cfg.setValue("pos", original.serialize());
        cfg.save();

        Config reopened = open(dir);
        WorldBlockPos read = reopened.getValue("pos", WorldBlockPos.class);
        assertEquals(original, read);
    }

    @Test
    void locPosRoundTrips(@TempDir Path dir) {
        LocPos pos = new LocPos(1.5, 2.25, -3.75);

        Config cfg = open(dir);
        cfg.setValue("loc", pos);
        cfg.save();

        Config reopened = open(dir);
        LocPos read = reopened.getValue("loc", LocPos.class);
        assertEquals(pos, read);
    }

    @Test
    void blockPosListSerializesAsCompactStringList(@TempDir Path dir) {
        List<BlockPos> positions = Arrays.asList(new BlockPos(1, 2, 3), new BlockPos(-4, 5, -6));

        Config cfg = open(dir);
        cfg.setValue("spots", positions);
        cfg.save();

        Config reopened = open(dir);
        // a list whose element type declares a compact element form stores as a string-list (one
        // compact line per element), while the SAME type stays a rich map as a solo value.
        assertEquals(Arrays.asList("1|2|3", "-4|5|-6"), reopened.getStringList("spots"));
        assertEquals(positions, reopened.getList("spots", BlockPos.class));
    }

    // ==================== time types ====================

    @Test
    void fcTimeFrameRoundTrips(@TempDir Path dir) {
        // 1d 2h 3m 4s expressed in millis.
        long millis = ((((1L * 24 + 2) * 60 + 3) * 60) + 4) * 1000;
        FCTimeFrame frame = FCTimeFrame.of(millis);

        Config cfg = open(dir);
        cfg.setValue("frame", frame);
        cfg.save();

        Config reopened = open(dir);
        FCTimeFrame read = reopened.getValue("frame", FCTimeFrame.class);
        assertEquals(frame.getMillis(), read.getMillis());
    }

    @Test
    void zonedDateTimeRoundTrips(@TempDir Path dir) {
        // Seed from the same formatter the codec uses, so no sub-second precision is expected to survive.
        String formatted = "2024/03/15 13:45:30";
        ZonedDateTime original = FCTimeUtil.universalDateConverter(formatted)
                .atZone(DayOfToday.getInstance().getZoneId());

        Config cfg = open(dir);
        cfg.setValue("when", original);
        cfg.save();

        Config reopened = open(dir);
        ZonedDateTime read = reopened.getValue("when", ZonedDateTime.class);
        assertEquals(formatted, FCTimeUtil.FORMATTER_DEFAULT.format(read));
    }

    // ==================== NumberWrapper ====================

    @Test
    void numberWrapperRoundTrips(@TempDir Path dir) {
        NumberWrapper<Integer> wrapper = NumberWrapper.of(42);

        Config cfg = open(dir);
        cfg.setValue("wrapped", wrapper);
        cfg.save();

        Config reopened = open(dir);
        NumberWrapper read = reopened.getValue("wrapped", NumberWrapper.class);
        assertEquals(42, read.intValue());
    }

    // ==================== Cooldown ====================

    @Test
    void cooldownRoundTrips(@TempDir Path dir) {
        GenericCooldown cooldown = new GenericCooldown("mycd", 1000L, 5000L, true);

        Config cfg = open(dir);
        cfg.setValue("cd", cooldown);
        cfg.save();

        Config reopened = open(dir);
        Cooldown read = reopened.getValue("cd", Cooldown.class);
        assertEquals("mycd", read.getIdentifier());
        assertEquals(1000L, read.getStart());
        assertEquals(5000L, read.getDuration());
    }

    // ==================== FancyText ====================

    @Test
    void fancyTextPlainRoundTrips(@TempDir Path dir) {
        // A live FancyText holds colorfied text (section sign); save decolorfies to '&', load re-colorfies,
        // so a section-sign source is the round-trip fixed point.
        FancyText fancyText = new FancySegment("§aHello World");

        Config cfg = open(dir);
        cfg.setValue("msg", fancyText);
        cfg.save();

        Config reopened = open(dir);
        FancyText read = reopened.getValue("msg", FancyText.class);
        assertEquals(fancyText, read);
    }

    @Test
    void fancyTextWithHoverAndClickRoundTrips(@TempDir Path dir) {
        FancyText fancyText = new FancySegment(
                "§aClick me",
                "§7A helpful tooltip",
                "/say hi",
                ClickActionType.RUN_COMMAND
        );

        Config cfg = open(dir);
        cfg.setValue("msg", fancyText);
        cfg.save();

        Config reopened = open(dir);
        FancyText read = reopened.getValue("msg", FancyText.class);
        assertEquals(fancyText, read);
    }

    @Test
    void fancyFormatterRoundTrips(@TempDir Path dir) {
        FancyText first = new FancySegment("§aFirst");
        FancyText second = new FancySegment("§bSecond", "§7hover");
        FancyFormatter formatter = new FancyFormatter().append(first).append(second);

        Config cfg = open(dir);
        cfg.setValue("msg", formatter);
        cfg.save();

        Config reopened = open(dir);
        FancyText read = reopened.getValue("msg", FancyText.class);

        assertTrue(read instanceof FancyFormatter, "a formatter node must load back as a FancyFormatter");
        List<FancyText> children = ((FancyFormatter) read).getFancyTextList();
        // The two meaningful texts survive in order (a leading empty seed from FancyFormatter.of() may prefix).
        assertTrue(children.contains(first), "first text must survive the round-trip");
        assertTrue(children.contains(second), "second text must survive the round-trip");
        assertTrue(children.indexOf(first) < children.indexOf(second), "order must be preserved");
        assertNotNull(read.getText());
    }
}
