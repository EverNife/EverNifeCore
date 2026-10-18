package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.core.DenyEntryMessage;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading, showing, storing and completing each stock flag type. */
@ECoreTest
class FlagTypesTest {

    private enum Mode { SURVIVAL, CREATIVE }

    @Test
    void tristateParsesAnyCaseAndFormatsLowercase() throws Exception {
        assertEquals(Tristate.TRUE, FlagTypes.TRISTATE.parse("TrUe"));
        assertEquals(Tristate.UNDEFINED, FlagTypes.TRISTATE.parse("undefined"));
        assertEquals("false", FlagTypes.TRISTATE.format(Tristate.FALSE));
        assertEquals(Arrays.asList("true"), FlagTypes.TRISTATE.tabComplete("t"));
        assertEquals(3, FlagTypes.TRISTATE.tabComplete("").size());
        assertEquals(Tristate.TRUE, FlagTypes.TRISTATE.fromJson(FlagTypes.TRISTATE.toJson(Tristate.TRUE)));
    }

    @Test
    void numbersRoundTripAndRefuseText() throws Exception {
        assertEquals(5, FlagTypes.INTEGER.parse(" 5 "));
        assertEquals("5", FlagTypes.INTEGER.format(5));
        assertTrue(FlagTypes.INTEGER.toJson(5).isInt());
        assertEquals(5, FlagTypes.INTEGER.fromJson(FlagTypes.INTEGER.toJson(5)));
        assertEquals(2.5, FlagTypes.DOUBLE.parse("2.5"));
        assertEquals(2.5, FlagTypes.DOUBLE.fromJson(FlagTypes.DOUBLE.toJson(2.5)));
        assertEquals(Collections.emptyList(), FlagTypes.INTEGER.tabComplete("1"));
        FlagParseException refused = assertThrows(FlagParseException.class, () -> FlagTypes.INTEGER.parse("five"));
        assertTrue(refused.getMessage().contains("'five'") && refused.getMessage().contains("whole number"), refused.getMessage());
    }

    @Test
    void textIsTakenAsIs() throws Exception {
        assertEquals("Welcome, traveller", FlagTypes.TEXT.parse("Welcome, traveller"));
        assertEquals("hi", FlagTypes.TEXT.format("hi"));
        assertEquals("hi", FlagTypes.TEXT.fromJson(FlagTypes.TEXT.toJson("hi")));
    }

    @Test
    void enumByNameAnyCase() throws Exception {
        FlagType<Mode> mode = FlagTypes.enumOf(Mode.class);
        assertEquals(Mode.CREATIVE, mode.parse("Creative"));
        assertEquals("survival", mode.format(Mode.SURVIVAL));
        assertEquals(Arrays.asList("creative"), mode.tabComplete("c"));
        FlagParseException refused = assertThrows(FlagParseException.class, () -> mode.parse("adventure"));
        assertTrue(refused.getMessage().contains("survival, creative"), refused.getMessage());
    }

    @Test
    void locationUsesTheCompactForm() throws Exception {
        WorldLocPos pos = WorldLocPos.of(10.5, 64, -3, "world");
        assertEquals(pos, FlagTypes.LOCATION.parse(FlagTypes.LOCATION.format(pos)));
        assertEquals(pos, FlagTypes.LOCATION.fromJson(FlagTypes.LOCATION.toJson(pos)));
        FlagParseException refused = assertThrows(FlagParseException.class, () -> FlagTypes.LOCATION.parse("world 1 2 3"));
        assertTrue(refused.getMessage().contains("world|x|y|z"), refused.getMessage());
    }

    @Test
    void storedObjectIsNotAScalar() {
        assertThrows(FlagParseException.class, () -> FlagTypes.TEXT.fromJson(FlagTypes.jackson("x", DenyEntryMessage.class)
                .toJson(new DenyEntryMessage())));
    }

    @Test
    void jacksonReadsKeyValuePairsWithQuotes() throws Exception {
        FlagType<DenyEntryMessage> type = FlagTypes.jackson("deny-entry-message", DenyEntryMessage.class);
        assertEquals(new DenyEntryMessage(Tristate.TRUE, "fica fora"), type.parse("denied=true message=\"fica fora\""));
        assertEquals(new DenyEntryMessage(Tristate.FALSE, "stay out now"), type.parse("message='stay out now'  denied=FALSE"));
        DenyEntryMessage onlyDenied = type.parse("denied=true");
        assertEquals(Tristate.TRUE, onlyDenied.getDenied());
        assertNull(onlyDenied.getMessage());
    }

    @Test
    void jacksonFormatIsTheInverseOfParse() throws Exception {
        FlagType<DenyEntryMessage> type = FlagTypes.jackson("deny-entry-message", DenyEntryMessage.class);
        DenyEntryMessage value = new DenyEntryMessage(Tristate.TRUE, "boss fight in progress");
        assertEquals(value, type.parse(type.format(value)));
        assertEquals(value, type.fromJson(type.toJson(value)));
        assertEquals(Arrays.asList("denied="), type.tabComplete("d"));
    }

    @Test
    void jacksonTokenWithoutEqualsNamesTheTokenAndTheFormat() {
        FlagType<DenyEntryMessage> type = FlagTypes.jackson("deny-entry-message", DenyEntryMessage.class);
        FlagParseException refused = assertThrows(FlagParseException.class, () -> type.parse("denied=true stay"));
        assertTrue(refused.getMessage().contains("'stay'"), refused.getMessage());
        assertTrue(refused.getMessage().contains("denied=... message=..."), refused.getMessage());
        FlagParseException unclosed = assertThrows(FlagParseException.class, () -> type.parse("message=\"never closed"));
        assertTrue(unclosed.getMessage().contains("never closes"), unclosed.getMessage());
        FlagParseException badValue = assertThrows(FlagParseException.class, () -> type.parse("denied=maybe"));
        assertTrue(badValue.getMessage().contains("denied=... message=..."), badValue.getMessage());
    }

    @Test
    void amountEveryParsesFormatsAndRefusesANonPositiveAmount() throws Exception {
        AmountEvery value = FlagTypes.AMOUNT_EVERY.parse("2 every 5s");
        assertEquals(2, value.getAmount());
        assertEquals(Duration.ofSeconds(5), value.getInterval());
        assertEquals("2 every 5s", FlagTypes.AMOUNT_EVERY.format(value));
        assertEquals(value, FlagTypes.AMOUNT_EVERY.fromJson(FlagTypes.AMOUNT_EVERY.toJson(value)));
        assertEquals(value, FlagTypes.AMOUNT_EVERY.parse(FlagTypes.AMOUNT_EVERY.format(value)));
        for (String refused : Arrays.asList("0 every 5s", "-1 every 5s", "2 every 500ms", "2 each 5s", "two every 5s")) {
            FlagParseException error = assertThrows(FlagParseException.class, () -> FlagTypes.AMOUNT_EVERY.parse(refused), refused);
            assertTrue(error.getMessage().contains("every <duration>"), error.getMessage());
        }
    }

    @Test
    void textListSplitsOnSemicolonsAndKeepsOrder() throws Exception {
        List<String> commands = FlagTypes.TEXT_LIST.parse("console:say hi, all ; player:spawn;;");
        assertEquals(Arrays.asList("console:say hi, all", "player:spawn"), commands);
        assertEquals("console:say hi, all; player:spawn", FlagTypes.TEXT_LIST.format(commands));
        assertTrue(FlagTypes.TEXT_LIST.toJson(commands).isArray());
        assertEquals(commands, FlagTypes.TEXT_LIST.fromJson(FlagTypes.TEXT_LIST.toJson(commands)));
        assertThrows(FlagParseException.class, () -> FlagTypes.TEXT_LIST.parse(" ; "));
    }

    @Test
    void effectListReadsTheLevelAfterTheLastColonAndDefaultsItToOne() throws Exception {
        List<EffectLevel> effects = FlagTypes.EFFECT_LIST.parse("speed:2, Night_Vision, minecraft:jump_boost:3, minecraft:regeneration");
        assertEquals(Arrays.asList(EffectLevel.of("speed", 2), EffectLevel.of("night_vision", 1), EffectLevel.of("minecraft:jump_boost", 3),
                EffectLevel.of("minecraft:regeneration", 1)), effects);
        assertEquals("speed:2, night_vision:1, minecraft:jump_boost:3, minecraft:regeneration:1", FlagTypes.EFFECT_LIST.format(effects));
        assertEquals(effects, FlagTypes.EFFECT_LIST.fromJson(FlagTypes.EFFECT_LIST.toJson(effects)));
        assertThrows(FlagParseException.class, () -> FlagTypes.EFFECT_LIST.parse("speed:0"));
    }
}
