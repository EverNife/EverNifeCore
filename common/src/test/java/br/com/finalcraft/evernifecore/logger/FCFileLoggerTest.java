package br.com.finalcraft.evernifecore.logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the file logger promises about the file: it appends, it is UTF-8, every line is on disk
 * before the next one is written, and nothing it can be handed makes it throw at the call site.
 */
class FCFileLoggerTest {

    // ------------------------------------------------------------------
    //  The file
    // ------------------------------------------------------------------

    @Test
    void aSecondInstanceAppendsToWhatTheFirstOneWrote(@TempDir Path dir) throws IOException {
        File target = dir.resolve("audit.log").toFile();

        FCFileLogger first = FCFileLogger.of(target).build();
        first.log("first run");
        first.close();

        FCFileLogger second = FCFileLogger.of(target).build();
        second.log("second run");
        second.close();

        assertEquals(list("first run", "second run"), linesOf(target),
                "opening the same file again must continue it, not truncate it");
    }

    @Test
    void whatIsWrittenComesBackByteForByteInUtf8(@TempDir Path dir) throws IOException {
        File target = dir.resolve("accents.log").toFile();

        FCFileLogger logger = FCFileLogger.of(target).build();
        logger.log("Petrus comprou pão-de-açúcar por 250 €");
        logger.close();

        assertEquals(list("Petrus comprou pão-de-açúcar por 250 €"), linesOf(target),
                "the file is UTF-8 whatever the platform's default charset says");
    }

    @Test
    void everyLineIsOnDiskBeforeTheNextOneIsWritten(@TempDir Path dir) throws IOException {
        File target = dir.resolve("live.log").toFile();

        FCFileLogger logger = FCFileLogger.of(target).build();
        logger.log("this line is readable while the server runs");

        assertEquals(list("this line is readable while the server runs"), linesOf(target),
                "an audit log nobody flushed is an audit log nobody can read yet");
        logger.close();
    }

    @Test
    void placeholdersAreFormattedByTheSameFormatterTheConsoleUses(@TempDir Path dir) throws IOException {
        File target = dir.resolve("format.log").toFile();

        try (FCFileLogger logger = FCFileLogger.of(target).build()) {
            logger.log("{} bought {} for {}", "Petrus", "Diamond", 250);
        }

        assertEquals(list("Petrus bought Diamond for 250"), linesOf(target));
    }

    // ------------------------------------------------------------------
    //  Timestamps
    // ------------------------------------------------------------------

    @Test
    void aLineIsStampedOnlyWhenTheBuilderAskedForIt(@TempDir Path dir) throws IOException {
        File bare = dir.resolve("bare.log").toFile();
        File stamped = dir.resolve("stamped.log").toFile();

        try (FCFileLogger logger = FCFileLogger.of(bare).build()) {
            logger.log("no stamp here");
        }
        try (FCFileLogger logger = FCFileLogger.of(stamped).withTimestamps().build()) {
            logger.log("stamped");
        }

        assertEquals(list("no stamp here"), linesOf(bare),
                "timestamps are OFF by default: turning them on would rewrite the format of every"
                        + " 2.x file in the same migration that only edits the call site");
        assertTrue(linesOf(stamped).get(0).matches("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}] stamped"),
                "the default stamp is the whole prefix, brackets and trailing space included: "
                        + linesOf(stamped));
    }

    @Test
    void aTimestampPatternOfMyOwnIsUsedWhole(@TempDir Path dir) throws IOException {
        File target = dir.resolve("custom-stamp.log").toFile();

        try (FCFileLogger logger = FCFileLogger.of(target).withTimestamps("HH:mm >> ").build()) {
            logger.log("mine");
        }

        assertTrue(linesOf(target).get(0).matches("\\d{2}:\\d{2} >> mine"), linesOf(target).toString());
    }

    // ------------------------------------------------------------------
    //  Nothing escapes the call site
    // ------------------------------------------------------------------

    @Test
    void aFileThatCannotBeOpenedCostsTheLinesAndNothingElse(@TempDir Path dir) throws IOException {
        //a plain file where a folder would have to be: mkdirs() cannot win this one on any platform
        File blocker = dir.resolve("blocker").toFile();
        Files.write(blocker.toPath(), "i am a file".getBytes(StandardCharsets.UTF_8));
        File impossible = new File(blocker, "inside-a-file.log");

        FCFileLogger logger = FCFileLogger.of(impossible).build();
        logger.log("dropped");
        logger.log("also dropped {}", 2);
        logger.close();

        assertFalse(impossible.exists(), "nothing was created where nothing could be created");
        assertEquals("i am a file", new String(Files.readAllBytes(blocker.toPath()), StandardCharsets.UTF_8),
                "and the file that was in the way is untouched");
    }

    @Test
    void closingTwiceIsAsHarmlessAsClosingOnce(@TempDir Path dir) throws IOException {
        File target = dir.resolve("twice.log").toFile();

        FCFileLogger logger = FCFileLogger.of(target).build();
        logger.log("one");
        logger.close();
        logger.close();

        assertFalse(logger.isOpen());
        assertEquals(list("one"), linesOf(target));
    }

    @Test
    void aLineWrittenAfterTheCloseIsDroppedInsteadOfReopeningTheFile(@TempDir Path dir) throws IOException {
        File target = dir.resolve("after-close.log").toFile();

        FCFileLogger logger = FCFileLogger.of(target).build();
        logger.log("before");
        logger.close();
        logger.log("after");

        assertEquals(list("before"), linesOf(target),
                "a handle everyone let go of must not resurrect the file behind their backs");
    }

    // ------------------------------------------------------------------
    //  Rollover on open
    // ------------------------------------------------------------------

    @Test
    void withoutRollOnOpenTheTargetIsSimplyAppended(@TempDir Path dir) throws IOException {
        File target = dir.resolve("latest.log").toFile();
        Files.write(target.toPath(), "from the previous run\n".getBytes(StandardCharsets.UTF_8));

        try (FCFileLogger logger = FCFileLogger.of(target).build()) {
            logger.log("from this run");
        }

        assertEquals(list("from the previous run", "from this run"), linesOf(target),
                "the roll is opt-in; without it nothing about the old behaviour changes");
        assertEquals(list("latest.log"), namesIn(dir), "and no archive is invented");
    }

    @Test
    void rollingWithNothingToArchiveCreatesNoArchive(@TempDir Path dir) throws IOException {
        File target = dir.resolve("latest.log").toFile();

        try (FCFileLogger logger = FCFileLogger.of(target).rollOnOpen().build()) {
            logger.log("the very first line");
        }

        assertEquals(list("latest.log"), namesIn(dir), "there was nothing to roll aside");
        assertEquals(list("the very first line"), linesOf(target));
    }

    @Test
    void theRollMovesTheOldContentAsideAndReopensTheTargetEmpty(@TempDir Path dir) throws IOException {
        File target = dir.resolve("latest.log").toFile();
        Files.write(target.toPath(), "yesterday\n".getBytes(StandardCharsets.UTF_8));

        try (FCFileLogger logger = FCFileLogger.of(target).rollOnOpen().build()) {
            logger.log("today");
        }

        assertEquals(list("today"), linesOf(target), "the target holds THIS run and nothing older");

        File archived = theOneFileOtherThan(dir, "latest.log");
        assertTrue(archived.getName().matches("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-1\\.log"),
                "the default archive keeps the date, the counter and the original extension: "
                        + archived.getName());
        assertEquals(list("yesterday"), linesOf(archived), "and it is the old content that moved");
    }

    @Test
    void anArchiveNameAlreadyTakenIsNeverOverwritten(@TempDir Path dir) throws IOException {
        File target = dir.resolve("latest.log").toFile();
        Files.write(target.toPath(), "the run being archived\n".getBytes(StandardCharsets.UTF_8));
        //deterministic collision: the name the roll resolves to is already on disk
        File taken = dir.resolve("archive-1.log").toFile();
        Files.write(taken.toPath(), "an older run nobody may lose\n".getBytes(StandardCharsets.UTF_8));

        try (FCFileLogger logger = FCFileLogger.of(target).rollOnOpen("archive-{n}.log").build()) {
            logger.log("the new run");
        }

        assertEquals(list("an older run nobody may lose"), linesOf(taken),
                "asserting by name would pass even here - the file that was there must still be there");
        assertEquals(list("the run being archived"), linesOf(dir.resolve("archive-2.log").toFile()),
                "the counter moved on to the first free name");
        assertEquals(list("the new run"), linesOf(target));
    }

    @Test
    void aCustomPatternProducesTheNameItAsksFor(@TempDir Path dir) throws IOException {
        File target = dir.resolve("latest.log").toFile();
        Files.write(target.toPath(), "archived by a pattern of my own\n".getBytes(StandardCharsets.UTF_8));

        try (FCFileLogger logger = FCFileLogger.of(target).rollOnOpen("audit-{date}-{n}.log").build()) {
            logger.log("this run");
        }

        File archived = theOneFileOtherThan(dir, "latest.log");
        assertTrue(archived.getName().matches("audit-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-1\\.log"),
                "both tokens are resolved, everything else is literal: " + archived.getName());
        assertEquals(list("archived by a pattern of my own"), linesOf(archived));
    }

    @Test
    void aPatternWithNoCounterStillRefusesToOverwrite(@TempDir Path dir) throws IOException {
        File target = dir.resolve("latest.log").toFile();
        Files.write(target.toPath(), "the run being archived\n".getBytes(StandardCharsets.UTF_8));
        File taken = dir.resolve("previous.log").toFile();
        Files.write(taken.toPath(), "an older run nobody may lose\n".getBytes(StandardCharsets.UTF_8));

        try (FCFileLogger logger = FCFileLogger.of(target).rollOnOpen("previous.log").build()) {
            logger.log("the new run");
        }

        assertEquals(list("an older run nobody may lose"), linesOf(taken),
                "a pattern that never asked for a counter gets one rather than destroy a file");
        assertEquals(list("the run being archived"), linesOf(dir.resolve("previous-2.log").toFile()));
    }

    // ------------------------------------------------------------------
    //  Reading the folder
    // ------------------------------------------------------------------

    private static List<String> linesOf(File file) throws IOException {
        return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
    }

    private static List<String> namesIn(Path dir) {
        List<String> names = new ArrayList<>();
        for (File each : dir.toFile().listFiles()) {
            names.add(each.getName());
        }
        names.sort(null);
        return names;
    }

    private static File theOneFileOtherThan(Path dir, String name) {
        List<String> others = namesIn(dir);
        others.remove(name);
        assertEquals(1, others.size(), "expected exactly one other file in " + dir + ", found " + others);
        return dir.resolve(others.get(0)).toFile();
    }

    private static List<String> list(String... values) {
        List<String> asList = new ArrayList<>();
        for (String value : values) {
            asList.add(value);
        }
        return asList;
    }
}
