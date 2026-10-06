package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.stock.DenyEntryMessage;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

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
}
