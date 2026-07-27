package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.fancytext.hover.FancyHover;
import br.com.finalcraft.evernifecore.fancytext.hover.ItemHover;
import br.com.finalcraft.everyconfig.config.Config;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Everything the codec writes that is NOT visible text travels literally: a click action is a command
 * or a URL and an item hover is an id/SNBT string, so an {@code &} in either one must come back as an
 * {@code &} and not as a colour code. Read back through the {@link FancyText} interface, and compared
 * field by field, because EveryConfig's binder swallows a decode failure and would let a silently
 * emptied value pass a "did not throw" assertion.
 */
@ECoreTest
public class FancyTextCodecLiteralPayloadTest {


    private Config open(Path dir) {
        return ConfigFactory.open(dir.resolve("data.yml"));
    }

    @Test
    void aClickUrlWithQueryParametersSurvivesTheRoundTripUntouched(@TempDir Path dir) {
        String url = "https://x.com/a?b=1&c=2";
        FancySegment original = new FancySegment("§aOpen it");
        original.setClickLink(url);

        Config cfg = open(dir);
        cfg.setValue("msg", original);
        cfg.save();

        FancyText firstRead = open(dir).getValue("msg", FancyText.class);
        assertEquals(url, firstRead.getClickActionText(), "the '&' of a query string is not a colour code");
        assertEquals(ClickActionType.OPEN_URL, firstRead.getClickActionType());
        assertEquals("§aOpen it", firstRead.getText(), "visible text still carries its colour codes");

        // a second save->load cycle must be a fixed point: the value that came back is the value that
        // goes out, or the lang file would keep changing shape on every boot.
        Config reopened = open(dir);
        reopened.setValue("msg", firstRead);
        reopened.save();
        assertEquals(url, open(dir).getValue("msg", FancyText.class).getClickActionText(),
                "the click text must survive a read->write->read cycle unchanged");
    }

    @Test
    void anItemHoverPayloadWithAnAmpersandSurvivesTheRoundTripUntouched(@TempDir Path dir) {
        String rawItem = "{id:\"minecraft:diamond\",tag:{display:{Name:\"Salt & Pepper\"}}}";
        FancySegment original = new FancySegment("§bHover me");
        original.setHoverItem(rawItem);

        Config cfg = open(dir);
        cfg.setValue("msg", original);
        cfg.save();

        FancyText firstRead = open(dir).getValue("msg", FancyText.class);
        FancyHover hover = firstRead.getHover();
        assertNotNull(hover, "the item hover must survive the read");
        assertInstanceOf(ItemHover.class, hover, "an 'item' hoverType must decode back to an ItemHover");
        assertEquals(rawItem, ((ItemHover) hover).rawItem(), "an item id/SNBT payload is not colour-coded text");
        assertEquals(ItemHover.LEGACY_SENTINEL + rawItem, firstRead.getHoverText());

        Config reopened = open(dir);
        reopened.setValue("msg", firstRead);
        reopened.save();
        FancyHover secondHover = open(dir).getValue("msg", FancyText.class).getHover();
        assertEquals(rawItem, ((ItemHover) secondHover).rawItem(),
                "the item payload must survive a read->write->read cycle unchanged");
    }

    @Test
    void aPlainTooltipStillCarriesItsColourCodesBothWays(@TempDir Path dir) {
        FancySegment original = new FancySegment("§cDanger", "§7line one\n§7line two");

        Config cfg = open(dir);
        cfg.setValue("msg", original);
        cfg.save();

        FancyText read = open(dir).getValue("msg", FancyText.class);
        assertEquals("§7line one\n§7line two", read.getHoverText(),
                "a text tooltip is the one hover payload that IS text, so it keeps the colour translation");
    }
}
