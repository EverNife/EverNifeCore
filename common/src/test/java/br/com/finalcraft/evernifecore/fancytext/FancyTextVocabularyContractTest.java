package br.com.finalcraft.evernifecore.fancytext;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the single-name vocabulary (hover/click/clickCommand/clickSuggest/clickLink) against the
 * Component the previous per-concept setters used to produce, for both {@link FancySegment} and
 * {@link FancyFormatter}. Every expected literal below was captured by actually running those
 * predecessor calls and serializing the resulting Component to JSON (via
 * {@link GsonComponentSerializer}) before they were renamed - none of them were deduced from
 * reading the rendering code.
 *
 * <p>Both sides of every comparison are routed through the same JSON round trip: some payloads
 * (an item hover built from a non-SNBT string, a click value the URL serializer normalizes) are
 * not perfectly preserved by the serializer, so comparing a freshly-rendered Component against one
 * rebuilt from serialized JSON would fail on that normalization alone, regardless of the rename.
 */
public class FancyTextVocabularyContractTest {

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    private static Component captured(String json) {
        return GSON.deserialize(json);
    }

    private static Component roundTrip(Component component) {
        return GSON.deserialize(GSON.serialize(component));
    }

    @Test
    void hoverStringMatchesTheOldSetHoverTextOnASegment() {
        Component expected = captured("{\"hover_event\":{\"action\":\"show_text\",\"value\":\"Tooltip line\"},\"text\":\"head\"}");
        Component actual = new FancySegment("head").hover("Tooltip line").toComponent();
        assertEquals(expected, roundTrip(actual));
    }

    @Test
    void hoverListMatchesTheOldSetHoverTextOfListOnASegment() {
        Component expected = captured("{\"hover_event\":{\"action\":\"show_text\",\"value\":\"line1\\nline2\"},\"text\":\"head\"}");
        Component actual = new FancySegment("head").hover(Arrays.asList("line1", "line2")).toComponent();
        assertEquals(expected, roundTrip(actual));
    }

    @Test
    void hoverItemMatchesTheOldSetHoverItemOnASegment() {
        Component expected = captured("{\"hover_event\":{\"action\":\"show_item\",\"id\":\"minecraft:diamond\",\"count\":1},\"text\":\"head\"}");
        Component actual = new FancySegment("head").hoverItem("minecraft:diamond").toComponent();
        assertEquals(expected, roundTrip(actual));
    }

    @Test
    void clickWithValueAndTypeMatchesTheOldSetClickActionOnASegment() {
        Component expectedRun = captured("{\"click_event\":{\"action\":\"run_command\",\"command\":\"/cmd\"},\"text\":\"head\"}");
        Component actualRun = new FancySegment("head").click("/cmd", ClickActionType.RUN_COMMAND).toComponent();
        assertEquals(expectedRun, roundTrip(actualRun));

        Component expectedUrl = captured("{\"click_event\":{\"action\":\"open_url\",\"url\":\"https://example.com\"},\"text\":\"head\"}");
        Component actualUrl = new FancySegment("head").click("https://example.com", ClickActionType.OPEN_URL).toComponent();
        assertEquals(expectedUrl, roundTrip(actualUrl));

        Component expectedSuggest = captured("{\"click_event\":{\"action\":\"suggest_command\",\"command\":\"/suggest \"},\"text\":\"head\"}");
        Component actualSuggest = new FancySegment("head").click("/suggest ", ClickActionType.SUGGEST_COMMAND).toComponent();
        assertEquals(expectedSuggest, roundTrip(actualSuggest));
    }

    @Test
    void clickWithTypeOnlyMatchesTheOldSetClickActionOfOneArgumentOnASegment() {
        // A pre-existing quirk this test must keep pinned, not "fix": changing only the type
        // leaves the previous click value untouched, so the render still carries the un-clicked
        // "/cmd" as an open_url action.
        Component expected = captured("{\"click_event\":{\"action\":\"open_url\",\"url\":\"https:///cmd\"},\"text\":\"head\"}");
        Component actual = new FancySegment("head", null, "/cmd", ClickActionType.RUN_COMMAND)
                .click(ClickActionType.OPEN_URL)
                .toComponent();
        assertEquals(expected, roundTrip(actual));
    }

    @Test
    void clickCommandMatchesTheOldSetRunCommandActionOnASegment() {
        Component expected = captured("{\"click_event\":{\"action\":\"run_command\",\"command\":\"/cmd\"},\"text\":\"head\"}");
        Component actual = new FancySegment("head").clickCommand("/cmd").toComponent();
        assertEquals(expected, roundTrip(actual));
    }

    @Test
    void clickSuggestMatchesTheOldSetSuggestCommandActionOnASegment() {
        Component expected = captured("{\"click_event\":{\"action\":\"suggest_command\",\"command\":\"/suggest \"},\"text\":\"head\"}");
        Component actual = new FancySegment("head").clickSuggest("/suggest ").toComponent();
        assertEquals(expected, roundTrip(actual));
    }

    @Test
    void clickLinkMatchesTheOldSetOpenLinkActionOnASegment() {
        Component expected = captured("{\"click_event\":{\"action\":\"open_url\",\"url\":\"https://example.com\"},\"text\":\"head\"}");
        Component actual = new FancySegment("head").clickLink("https://example.com").toComponent();
        assertEquals(expected, roundTrip(actual));
    }

    // --- FancyFormatter: every setter/getter above delegates to the last appended segment -------

    @Test
    void hoverStringMatchesTheOldSetHoverTextOnAFormatter() {
        Component expected = captured("{\"extra\":[\"first\",{\"hover_event\":{\"action\":\"show_text\",\"value\":\"Tooltip line\"},\"text\":\"head\"}],\"text\":\"\"}");
        FancyFormatter actual = new FancyFormatter().append("first").append("head");
        actual.hover("Tooltip line");
        assertEquals(expected, roundTrip(actual.toComponent()));
    }

    @Test
    void hoverListMatchesTheOldSetHoverTextOfListOnAFormatter() {
        Component expected = captured("{\"extra\":[\"first\",{\"hover_event\":{\"action\":\"show_text\",\"value\":\"line1\\nline2\"},\"text\":\"head\"}],\"text\":\"\"}");
        FancyFormatter actual = new FancyFormatter().append("first").append("head");
        actual.hover(Arrays.asList("line1", "line2"));
        assertEquals(expected, roundTrip(actual.toComponent()));
    }

    @Test
    void hoverItemMatchesTheOldSetHoverItemOnAFormatter() {
        Component expected = captured("{\"extra\":[\"first\",{\"hover_event\":{\"action\":\"show_item\",\"id\":\"minecraft:diamond\",\"count\":1},\"text\":\"head\"}],\"text\":\"\"}");
        FancyFormatter actual = new FancyFormatter().append("first").append("head");
        actual.hoverItem("minecraft:diamond");
        assertEquals(expected, roundTrip(actual.toComponent()));
    }

    @Test
    void clickWithValueAndTypeMatchesTheOldSetClickActionOnAFormatter() {
        Component expected = captured("{\"extra\":[\"first\",{\"click_event\":{\"action\":\"run_command\",\"command\":\"/cmd\"},\"text\":\"head\"}],\"text\":\"\"}");
        FancyFormatter actual = new FancyFormatter().append("first").append("head");
        actual.click("/cmd", ClickActionType.RUN_COMMAND);
        assertEquals(expected, roundTrip(actual.toComponent()));
    }

    @Test
    void clickWithTypeOnlyMatchesTheOldSetClickActionOfOneArgumentOnAFormatter() {
        Component expected = captured("{\"extra\":[\"first\",{\"click_event\":{\"action\":\"open_url\",\"url\":\"https:///cmd\"},\"text\":\"head\"}],\"text\":\"\"}");
        FancyFormatter actual = new FancyFormatter().append("first")
                .append(new FancySegment("head", null, "/cmd", ClickActionType.RUN_COMMAND));
        actual.click(ClickActionType.OPEN_URL);
        assertEquals(expected, roundTrip(actual.toComponent()));
    }

    @Test
    void clickCommandMatchesTheOldSetRunCommandActionOnAFormatter() {
        Component expected = captured("{\"extra\":[\"first\",{\"click_event\":{\"action\":\"run_command\",\"command\":\"/cmd\"},\"text\":\"head\"}],\"text\":\"\"}");
        FancyFormatter actual = new FancyFormatter().append("first").append("head");
        actual.clickCommand("/cmd");
        assertEquals(expected, roundTrip(actual.toComponent()));
    }

    @Test
    void clickSuggestMatchesTheOldSetSuggestCommandActionOnAFormatter() {
        Component expected = captured("{\"extra\":[\"first\",{\"click_event\":{\"action\":\"suggest_command\",\"command\":\"/suggest \"},\"text\":\"head\"}],\"text\":\"\"}");
        FancyFormatter actual = new FancyFormatter().append("first").append("head");
        actual.clickSuggest("/suggest ");
        assertEquals(expected, roundTrip(actual.toComponent()));
    }

    @Test
    void clickLinkMatchesTheOldSetOpenLinkActionOnAFormatter() {
        Component expected = captured("{\"extra\":[\"first\",{\"click_event\":{\"action\":\"open_url\",\"url\":\"https://example.com\"},\"text\":\"head\"}],\"text\":\"\"}");
        FancyFormatter actual = new FancyFormatter().append("first").append("head");
        actual.clickLink("https://example.com");
        assertEquals(expected, roundTrip(actual.toComponent()));
    }
}
