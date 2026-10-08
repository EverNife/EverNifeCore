package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.text.ITextMetrics;
import br.com.finalcraft.evernifecore.util.FCTextUtil;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * Chat text measured by the platform this server registered: the vanilla glyph widths (spacing included), a chat
 * line wide enough to centre in, and the centring helpers actually moving text - only {@link ITextMetrics#UNMEASURED}
 * leaves it untouched. What the client draws is read from outside ({@code /ecc text}), as evidence.
 */
public final class TextMetricsSuite implements ConformanceSuite {

    /** Glyph widths of the default font plus one pixel of spacing - two lines whose drawn width the client can confirm. */
    public static final String NARROW_LINE = "iiiiiiiiii";
    public static final String WIDE_LINE = "WWWWWWWWWW";

    @Override
    public String getId() {
        return "text-metrics";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("glyph-widths", () -> {
                    ITextMetrics metrics = metrics();
                    int i = metrics.widthOf("i"), a = metrics.widthOf("a"), w = metrics.widthOf("W"), aa = metrics.widthOf("aa");
                    return Verdict.of(i == 2 && a == 6 && w == 6 && aa == 2 * a,
                            "i=" + i + " a=" + a + " W=" + w + " aa=" + aa + " metrics=" + metrics.getClass().getSimpleName());
                }),
                ConformanceCase.of("chat-line", () -> {
                    int width = metrics().chatLineWidth();
                    return Verdict.of(width > 0, "chatLineWidth=" + width);
                }),
                ConformanceCase.of("align-center", () -> {
                    String centred = EverNifeCore.getPlatform().getChatAdapter().alignCenter("conformance");
                    String unmeasured = FCTextUtil.alignCenter(ITextMetrics.UNMEASURED, "conformance");
                    //measured: padded with spaces on the left (between reset codes); unmeasured: untouched
                    return Verdict.of(centred.contains(" conformance") && unmeasured.equals("conformance"),
                            "centred='" + centred + "' unmeasured='" + unmeasured + "'");
                }));
    }

    /** Sends the two lines whose drawn width the client can be asked about; answers their measured widths. */
    public static String sendLines(Player player) {
        player.sendMessage(NARROW_LINE);
        player.sendMessage(WIDE_LINE);
        return NARROW_LINE + "=" + metrics().widthOf(NARROW_LINE) + " " + WIDE_LINE + "=" + metrics().widthOf(WIDE_LINE);
    }

    private static ITextMetrics metrics() {
        return EverNifeCore.getPlatform().getChatAdapter().getTextMetrics();
    }
}
