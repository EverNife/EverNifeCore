package br.com.finalcraft.evernifecore.fancytext;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The one model invariant the chain now guarantees: <b>a chain never contains another chain, and it
 * owns every piece it holds.</b> There used to be two append routes with different contracts, picked
 * by arity - the single-argument one flattened a nested chain but stored a leaf by reference, and the
 * varargs one did neither. That made {@code of(x)} and {@code append(x)} build different trees, made
 * {@code copy()} unequal to its original, and let a caller reshape a chain from the outside long
 * after building it.
 */
class FancyTextAppendModelContractTest {

    private static FancyFormatter chainOfThree() {
        return FancyFormatter.of("§aone").append("§btwo").append("§cthree");
    }

    @Test
    void ofAndAppendBuildTheSameChainForALeaf() {
        FancySegment leaf = new FancySegment("§aleaf", "§7hover", "/cmd");

        assertEquals(new FancyFormatter().append(leaf), FancyFormatter.of(leaf),
                "of(x) and append(x) must agree on a leaf");
    }

    @Test
    void ofAndAppendBuildTheSameChainForANestedChain() {
        FancyFormatter inner = chainOfThree();

        FancyFormatter viaOf = FancyFormatter.of(inner);
        FancyFormatter viaAppend = new FancyFormatter().append(inner);

        assertEquals(3, viaOf.getFancyTextList().size(), "of(chain) must splice, not nest");
        assertEquals(viaAppend, viaOf, "of(chain) and append(chain) must agree");
    }

    @Test
    void copyEqualsItsOriginalForEveryWayOfBuildingAChain() {
        FancySegment a = new FancySegment("§aA");
        FancySegment b = new FancySegment("§bB", "§7hover b");
        FancySegment c = new FancySegment("§cC", null, "/c", ClickActionType.RUN_COMMAND);

        List<FancyFormatter> built = Arrays.asList(
                FancyFormatter.of(chainOfThree()),
                new FancyFormatter().append(a).append(b).append(c),
                new FancyFormatter().append(a, b, c));

        for (FancyFormatter formatter : built) {
            FancyFormatter copy = formatter.copy();
            assertNotSame(formatter, copy);
            assertEquals(formatter, copy, "copy() must stay structurally equal to its original");
        }
    }

    @Test
    void mutatingALeafAfterAppendingItDoesNotReshapeTheChain() {
        FancySegment leaf = new FancySegment("§aoriginal");
        FancyFormatter viaSingle = new FancyFormatter().append(leaf);

        FancySegment first = new FancySegment("§afirst");
        FancySegment second = new FancySegment("§bsecond");
        FancyFormatter viaVarargs = new FancyFormatter().append(first, second);

        leaf.setText("§4MUTATED");
        leaf.setHover("§4MUTATED HOVER");
        first.setText("§4MUTATED");
        second.setClickCommand("/mutated");

        assertEquals("§aoriginal", viaSingle.getFancyTextList().get(0).getText());
        assertEquals(null, viaSingle.getFancyTextList().get(0).getHoverText());
        assertEquals("§afirst", viaVarargs.getFancyTextList().get(0).getText());
        assertEquals(null, viaVarargs.getFancyTextList().get(1).getClickActionText());
    }

    @Test
    void aChainNeverContainsAnotherChain() {
        FancyFormatter outer = new FancyFormatter()
                .append("§ahead")
                .append(chainOfThree())
                .append(FancyFormatter.of(chainOfThree(), new FancySegment("§dtail")));

        for (FancyText piece : outer.getFancyTextList()) {
            assertFalse(piece instanceof FancyFormatter,
                    "a formatter must never end up holding another formatter: " + piece.getText());
        }
        assertEquals(1 + 3 + 3 + 1, outer.getFancyTextList().size(), "every piece must be spliced in, in order");
    }

    @Test
    void isEmptyAnswersForEveryShapeOfText() {
        assertTrue(new FancySegment().isEmpty(), "a leaf with no text at all is empty");
        assertTrue(new FancySegment("").isEmpty(), "a leaf with an empty string is empty");
        assertFalse(new FancySegment("§ax").isEmpty(), "a leaf with text is not empty");

        assertTrue(new FancyFormatter().isEmpty(), "a chain with no pieces is empty");
        assertTrue(new FancyFormatter().append("").append("").isEmpty(), "a chain of empty pieces is empty");
        assertFalse(new FancyFormatter().append("").append("§ax").isEmpty(),
                "one non-empty piece is enough to make the chain non-empty");
    }

    @Test
    void ofAListBuildsOnePiecePerLineJoinedByNewlines() {
        FancyText block = FancyText.of(Arrays.asList("first", "second", "third"));

        List<FancyText> pieces = ((FancyFormatter) block).getFancyTextList();
        assertEquals(3, pieces.size(), "one piece per line");
        assertEquals("first", pieces.get(0).getText());
        assertEquals("\nsecond", pieces.get(1).getText());
        assertEquals("\nthird", pieces.get(2).getText());
    }

    // Splicing dissolves the appended chain as a declaration level, so what it declared has to reach
    // its pieces - otherwise concatenating two messages would silently drop their placeholders.
    @Test
    void appendingAChainCarriesItsDeclarationsOntoThePiecesItSplicesIn() {
        FancyFormatter declaring = FancyFormatter.of("hi ${who}").append(" and ${who}");
        declaring.addPlaceholder("who", "Steve");

        FancyFormatter spliced = new FancyFormatter().append("[").append(declaring);

        assertEquals("[hi Steve and Steve", spliced.toLegacyString(RenderContext.empty()));
    }

    @Test
    void aPieceKeepsItsOwnValueWhenTheChainAroundItIsSplicedElsewhere() {
        FancySegment own = new FancySegment(" then ${who}");
        own.addPlaceholder("who", "Alex");

        FancyFormatter declaring = FancyFormatter.of("first ${who}").append(own);
        declaring.addPlaceholder("who", "Steve");

        FancyFormatter spliced = new FancyFormatter().append(declaring);

        assertEquals("first Steve then Alex", spliced.toLegacyString(RenderContext.empty()));
    }

    @Test
    void joinPutsTheSeparatorBetweenItemsAndKeepsEachPieceDecorated() {
        FancyFormatter joined = FancyText.join("§7, ", Arrays.asList("alpha", "beta"),
                item -> new FancySegment("§a" + item, "§7hover " + item));

        List<FancyText> pieces = joined.getFancyTextList();
        assertEquals(3, pieces.size(), "two items and one separator");
        assertEquals("§aalpha", pieces.get(0).getText());
        assertEquals("§7hover alpha", pieces.get(0).getHoverText(), "join must not flatten away each item's own hover");
        assertEquals("§7, ", pieces.get(1).getText());
        assertEquals("§abeta", pieces.get(2).getText());

        assertTrue(FancyText.join("§7, ", Arrays.<String>asList(), item -> new FancySegment(item)).isEmpty(),
                "joining nothing yields an empty chain, not a stray separator");
    }

    // ------------------------------------------------------------------
    //  absorbed from FancyTextModelContractTest
    // ------------------------------------------------------------------

    @Test
    void bothImplementationsAreFancyTextAndTheEmptyFactoryStartsWithNoSegment() {
        assertTrue(FancyText.of() instanceof FancySegment, "FancyText.of() must produce a segment");
        assertTrue(FancyFormatter.of() instanceof FancyText, "a FancyFormatter is a FancyText");
        assertEquals(0, FancyFormatter.of().getFancyTextList().size(),
                "FancyFormatter.of() must start with no segments, exactly like new FancyFormatter()");
    }

    @Test
    void formatterFactoryAndConstructorAgreeOnChildCount() {
        assertEquals(new FancyFormatter().getFancyTextList().size(),
                FancyFormatter.of().getFancyTextList().size(),
                "of() and the constructor must start with the same number of segments");
    }

    @Test
    void clickTypeAppliesToTheLastAppendedSegment() {
        FancyFormatter formatter = (FancyFormatter) FancyText.of("head").append("child");
        formatter.setClickType(ClickActionType.OPEN_URL);

        List<FancyText> children = formatter.getFancyTextList();
        assertEquals(ClickActionType.OPEN_URL,
                children.get(children.size() - 1).getClickActionType());
    }

    @Test
    void formattersWithDifferentContentAreNotEqual() {
        assertNotEquals(FancyFormatter.of("&aone"), FancyFormatter.of("&btwo"));
    }

    // ------------------------------------------------------------------
    //  absorbed from FancyFormatterEdgeCasesTest
    // ------------------------------------------------------------------

    // copy() must isolate the declarations: declaring on one side never leaks into the other.
    @Test
    public void copyIsolatesThePlaceholderMap() {
        FancyFormatter original = new FancyFormatter().append("${x} ${y}");
        original.addPlaceholder("a", "1");

        FancyFormatter copy = original.copy();
        copy.addPlaceholder("x", "onlyInTheCopy");
        assertFalse(original.getPlaceholderProvider().getParserMap().containsKey("x"), "copy leaked into original");
        assertTrue(copy.getPlaceholderProvider().getParserMap().containsKey("x"));

        original.addPlaceholder("y", "onlyInTheOriginal");
        assertFalse(copy.getPlaceholderProvider().getParserMap().containsKey("y"), "original leaked into copy");

        assertEquals("${x} onlyInTheOriginal", original.toLegacyString(RenderContext.empty()));
        assertEquals("onlyInTheCopy ${y}", copy.toLegacyString(RenderContext.empty()));
    }

    // An empty formatter has no segment to delegate to; getters/setters degrade instead of throwing.
    @Test
    public void emptyFormatterGettersAndSettersDoNotThrow() {
        FancyFormatter empty = new FancyFormatter();

        assertNull(empty.getText());
        assertNull(empty.getHoverText());
        assertNull(empty.getClickActionText());
        assertEquals(ClickActionType.NONE, empty.getClickActionType());

        assertDoesNotThrow(() -> empty.setHover("hover"));
        assertDoesNotThrow(() -> empty.setClick("cmd", ClickActionType.RUN_COMMAND));
        assertDoesNotThrow(() -> empty.setClickCommand("cmd"));
        assertDoesNotThrow(() -> empty.setClickSuggest("suggest"));
        assertDoesNotThrow(() -> empty.setClickLink("https://example.com"));
    }
}
