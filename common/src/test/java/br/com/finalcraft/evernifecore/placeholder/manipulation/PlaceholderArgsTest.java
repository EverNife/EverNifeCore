package br.com.finalcraft.evernifecore.placeholder.manipulation;

import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** The {@code [...]} block of a placeholder template, read end to end through a {@link RegexReplacer}. */
class PlaceholderArgsTest {

    private enum Sort { NAME, ENTRY }

    private final RegexReplacer<Object> replacer = new RegexReplacer<>()
            .addParser("player_name", o -> "Steve")
            .addManipulator("echo_[value]", (o, ctx) -> ctx.getArgs("[value]").getValue())
            .addManipulator("name_[options]", (o, ctx) -> ctx.getArgs("[options]").getValue("fallback"))
            .addManipulator("players_[region]_count", (o, ctx) -> "count of " + ctx.getArgs("[region]").getValue())
            .addManipulator("battle_win_[options?]", (o, ctx) -> {
                PlaceholderArgs args = ctx.getArgs("[options]");
                return args.isEmpty() ? "absent" : args.getValue("top");
            })
            .addManipulator("top_[options]", (o, ctx) -> {
                PlaceholderArgs args = ctx.getArgs("[options]").allowOnly("index", "sort", "online");
                return args.getInt("index", 1) + " " + args.getEnum("sort", Sort.class, Sort.NAME) + " " + args.getBoolean("online", false);
            })
            .addManipulator("list_[options]", (o, ctx) -> String.join("+", ctx.getArgs("[options]").getList("sort")));

    private String apply(String placeholder) {
        return replacer.apply("%" + placeholder + "%", new Object());
    }

    // ---- the reading table: every escape a value can need ----

    @ParameterizedTest(name = "{0} reads {1}")
    @CsvSource(delimiter = '#', quoteCharacter = '`', value = {
            "echo_[abra\\]cadabra]#abra]cadabra",
            "echo_['abra]cadabra']#abra]cadabra",
            "echo_[abra[ca]dabra]#abra[ca]dabra",
            "echo_[abra\\[cadabra]#abra[cadabra",
            "name_[fallback='Área [livre]']#Área [livre]",
            "name_[fallback='a | b = c, d']#a | b = c, d",
            "name_[fallback=\"it's\"]#it's",
            "name_[fallback='it\\'s']#it's",
            "name_[fallback='\\{player_name}']#{player_name}",
            "name_[fallback='C:\\\\x']#C:\\x",
    })
    void theReadingTable(String placeholder, String expected) {
        assertEquals(expected, apply(placeholder));
    }

    // ---- the shapes of a block ----

    @Test
    void aPositionalValue() {
        assertEquals("arena", apply("echo_[arena]"));
    }

    @Test
    void namedValuesSeparatedByAPipe() {
        assertEquals("2 ENTRY true", apply("top_[index=2|sort=entry|online=true]"));
    }

    @Test
    void aCommaGivesOneKeySeveralValues() {
        assertEquals("name+entry", apply("list_[sort=name,entry]"));
    }

    @Test
    void anUnderscoreInsideABlockNeverCutsIt() {
        assertEquals("count of arena_red", apply("players_[arena_red]_count"));
    }

    @Test
    void aBalancedBlockEndsAtItsOwnBracket() {
        assertEquals("a[b]c", apply("echo_[a[b]c]"));
    }

    @Test
    void aNestedPlaceholderIsResolvedByTheSameReplacer() {
        assertEquals("Steve", apply("echo_[{player_name}]"));
        assertEquals("hi Steve", apply("name_[fallback=hi {player_name}]"));
    }

    @Test
    void anEscapedBraceStaysLiteral() {
        assertEquals("{player_name}", apply("echo_[\\{player_name}]"));
    }

    @Test
    void aNestedPlaceholderNobodyAnswersStaysAsWritten() {
        assertEquals("{nobody}", apply("echo_[{nobody}]"));
    }

    // ---- errors ----

    @Test
    void aBlockThatNeverClosesLeavesThePlaceholderAsWritten() {
        assertEquals("%echo_[abra%", replacer.apply("%echo_[abra%", new Object()));
    }

    @Test
    void aQuoteThatNeverClosesLeavesThePlaceholderAsWritten() {
        assertEquals("%echo_['abra]%", replacer.apply("%echo_['abra]%", new Object()));
    }

    @Test
    void aValueThatIsNotANumberSaysSoWhereTheValueWouldBe() {
        assertEquals("[options: index must be a number, got 'x']", apply("top_[index=x]"));
    }

    @Test
    void aKeyOutsideAllowOnlyIsNamedWithTheAcceptedOnes() {
        assertEquals("[options: unknown key 'colour', accepted: index, sort, online]", apply("top_[colour=red]"));
    }

    @Test
    void aBadEnumAndABadBooleanAreNamedToo() {
        assertEquals("[options: sort must be one of name, entry, got 'age']", apply("top_[sort=age]"));
        assertEquals("[options: online must be true or false, got 'maybe']", apply("top_[online=maybe]"));
    }

    // ---- identity ----

    @Test
    void equalsAndHashCodeIgnoreTheOrderTheKeysWereWrittenIn() {
        PlaceholderArgs ab = PlaceholderArgs.parse("options", "a=1|b=2", null);
        PlaceholderArgs ba = PlaceholderArgs.parse("options", "b=2|a=1", null);

        assertEquals(ab, ba);
        assertEquals(ab.hashCode(), ba.hashCode());
        assertEquals("[a=1|b=2]", ba.toString());
        assertNotEquals(ab, PlaceholderArgs.parse("options", "a=1|b=3", null));
    }

    @Test
    void severalValuesAndPositionalsKeepTheirOrder() {
        PlaceholderArgs args = PlaceholderArgs.parse("options", "x|sort=b,a", null);

        assertEquals(Arrays.asList("x"), args.getPositional());
        assertEquals(Arrays.asList("b", "a"), args.getList("sort"));
    }

    // ---- optional block ----

    @Test
    void anOptionalBlockLeftOutTakesItsUnderscoreAlong() {
        assertEquals("absent", apply("battle_win"));
    }

    @Test
    void anOptionalBlockPresentIsRead() {
        assertEquals("3", apply("battle_win_[top=3]"));
    }

    @Test
    void anOptionalBlockDoesNotSwallowOtherText() {
        assertEquals("%battle_win_x%", replacer.apply("%battle_win_x%", new Object()));
    }
}
