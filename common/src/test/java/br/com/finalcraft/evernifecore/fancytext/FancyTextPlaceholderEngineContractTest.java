package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The {@code ${key}} engine: case-insensitive keys, values computed only when the rendered text
 * cites them, once per render, and no interference with the {@code %papi_x%} tokens that belong to
 * PlaceholderAPI.
 */
public class FancyTextPlaceholderEngineContractTest {

    private static String render(FancyText fancyText) {
        return fancyText.toLegacyString(RenderContext.EMPTY);
    }

    @Test
    void aPlaceholderResolvesRegardlessOfHowTheKeyIsCased() {
        assertEquals("you have 42", render(FancyText.of("you have ${saldo}").placeholder("saldo", 42)));
        assertEquals("you have 42", render(FancyText.of("you have ${SALDO}").placeholder("saldo", 42)));
        assertEquals("you have 42", render(FancyText.of("you have ${SaLdO}").placeholder("SALDO", 42)));
    }

    @Test
    void anUndeclaredKeyIsLeftExactlyAsWritten() {
        assertEquals("you have ${saldo}", render(FancyText.of("you have ${saldo}").placeholder("other", 1)));
    }

    @Test
    void thePlaceholdersAreResolvedInTheHoverAndInTheClickValueToo() {
        FancyText fancyText = FancyText.of("hi ${name}")
                .hover("hovering ${name}")
                .clickCommand("/msg ${name}")
                .placeholder("name", "Steve");

        FancySegment resolved = (FancySegment) fancyText;
        assertEquals("hi Steve", render(fancyText));
        // the declaration itself never mutates the text it was declared on
        assertEquals("hi ${name}", resolved.getText());
        assertEquals("hovering ${name}", resolved.getHoverText());
        assertEquals("/msg ${name}", resolved.getClickActionText());
    }

    @Test
    void aSupplierIsNeverInvokedWhenTheTextDoesNotCiteItsKey() {
        AtomicInteger calls = new AtomicInteger();
        FancyText fancyText = FancyText.of("nothing to resolve here")
                .placeholder("expensive", () -> {
                    calls.incrementAndGet();
                    return "computed";
                });

        assertEquals("nothing to resolve here", render(fancyText));
        assertEquals(0, calls.get(), "a placeholder the text never cites must not be resolved");
    }

    @Test
    void aKeyCitedTwiceInTheSameRenderIsResolvedOnce() {
        AtomicInteger calls = new AtomicInteger();
        FancyText fancyText = FancyText.of("${saldo} and again ${SALDO} and ${saldo}")
                .placeholder("saldo", () -> {
                    calls.incrementAndGet();
                    return "100";
                });

        assertEquals("100 and again 100 and 100", render(fancyText));
        assertEquals(1, calls.get(), "the same key in one render must cost exactly one resolution");
    }

    @Test
    void aSupplierIsResolvedAgainOnTheNextRender() {
        AtomicInteger calls = new AtomicInteger();
        FancyText fancyText = FancyText.of("${counter}")
                .placeholder("counter", () -> String.valueOf(calls.incrementAndGet()));

        assertEquals("1", render(fancyText));
        assertEquals("2", render(fancyText), "memoisation lasts one render, not forever");
    }

    @Test
    void aSupplierThatResolvesToNullIsNotInvokedAgainInTheSameRender() {
        AtomicInteger calls = new AtomicInteger();
        FancyText fancyText = FancyText.of("${nothing} ${nothing} ${nothing}")
                .placeholder("nothing", () -> {
                    calls.incrementAndGet();
                    return null;
                });

        assertEquals("${nothing} ${nothing} ${nothing}", render(fancyText),
                "a key that resolves to nothing leaves its token as written");
        assertEquals(1, calls.get(), "null is a real answer and must be remembered like any other");
    }

    @Test
    void aPerPlayerPlaceholderLeavesTheTokenAloneForARecipientWithoutPlayerData() {
        Function<PlayerData, Object> perPlayer = playerData -> playerData.getUniqueId();
        FancyText fancyText = FancyText.of("hello ${who}").placeholder("who", perPlayer);

        assertEquals("hello ${who}", render(fancyText));
    }

    // A lambda literal picks the Supplier/Function overload, but a variable DECLARED as Object binds
    // to the Object overload even when it happens to hold a Supplier - and is then printed as-is.
    @Test
    void aValueDeclaredAsObjectFallsIntoTheObjectOverloadEvenWhenItHoldsASupplier() {
        Supplier<String> supplier = () -> "computed";
        Object supplierAsObject = supplier;

        String rendered = render(FancyText.of("${x}").placeholder("x", supplierAsObject));

        assertNotEquals("computed", rendered, "the Object overload does not unwrap a Supplier");
        assertEquals(String.valueOf(supplierAsObject), rendered);
    }

    @Test
    void aPlaceholderDeclaredOnTheChainIsVisibleToEveryPieceOfIt() {
        FancyFormatter formatter = FancyFormatter.of("first ${who}")
                .append(" then ${who}")
                .placeholder("who", "Steve");

        assertEquals("first Steve then Steve", render(formatter));
    }

    @Test
    void aPieceThatDeclaresTheSameKeyShadowsTheChain() {
        FancySegment own = new FancySegment(" then ${who}");
        own.placeholder("who", "Alex");

        FancyFormatter formatter = FancyFormatter.of("first ${who}")
                .append(own)
                .placeholder("who", "Steve");

        assertEquals("first Steve then Alex", render(formatter));
    }

    @Test
    void placeholdersFromAMapAdaptToTheKindOfValueTheyHold() {
        Map<String, Object> values = new HashMap<>();
        values.put("plain", "A");
        values.put("lazy", (Supplier<String>) () -> "B");

        assertEquals("A B", render(FancyText.of("${plain} ${lazy}").placeholders(values)));
    }

    // PlaceholderAPI owns '%key%'; the engine must not touch those tokens, so both closures can sit
    // in the same text and each be resolved by the machinery that owns it.
    @Test
    void papiTokensAreLeftForTheCompoundReplacerAndNotTouchedByTheEngine() {
        FancyText fancyText = FancyText.of("${saldo} tem %papi_vault_balance%").placeholder("saldo", "R$10");

        assertEquals("R$10 tem %papi_vault_balance%", render(fancyText),
                "the ${} engine must leave every %...% token untouched");

        CompoundReplacer papiLike = CompoundReplacer.from(
                new RegexReplacer<Object>().addParser("papi_vault_balance", o -> "999"), new Object());

        FancyText resolvedByBoth = FancyText.of("${saldo} tem %papi_vault_balance%")
                .placeholder("saldo", "R$10")
                .replace(papiLike);

        assertEquals("R$10 tem 999", resolvedByBoth.toLegacyString(RenderContext.EMPTY));
    }
}
