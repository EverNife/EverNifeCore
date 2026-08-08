package br.com.finalcraft.evernifecore.pageviewer.theme;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.time.ECTimeFormat;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A rule above the entries, and optionally the date and how many entries there are. Every line of it
 * is a locale message, so translating or restyling a page is editing {@code lang_XX.yml} - the same
 * road as any other message of the core.
 *
 * <p>Instances are immutable; every {@code withX} hands back another theme.</p>
 */
public final class ClassicPageTheme implements PageTheme {

    /**
     * 53 dashes: {@code -} advances 6 pixels and the chat line is 320 wide, so
     * {@code floor(320/6) = 53} fills it without overflowing - see {@code McTextMetricsTest}.
     * Written out rather than measured because the measurement depends on the CLIENT, and the colour
     * has to sit INSIDE the repeated unit or the reset that closes it cancels the strikethrough.
     */
    @FCLocale(lang = LocaleType.EN_US, text = "§a§m-----------------------------------------------------")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§m-----------------------------------------------------")
    protected static LocaleMessage PAGE_RULE;

    @FCLocale(lang = LocaleType.EN_US, text = "§7Date of today: ${date_of_today}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7Data de hoje: ${date_of_today}")
    protected static LocaleMessage DATE_OF_TODAY_IS;

    @FCLocale(lang = LocaleType.EN_US,
            text = "§7From a total of ${total} players...",
            hover = "\n§7Showing §e${shown}§7 of §e${total}\n")
    @FCLocale(lang = LocaleType.PT_BR,
            text = "§7De um total de ${total} jogadores...",
            hover = "\n§7Mostrando §e${shown}§7 de §e${total}\n")
    protected static LocaleMessage OF_A_TOTAL_OF_X_PLAYERS;

    @FCLocale(lang = LocaleType.EN_US,
            text = "§7From a total of ${total}...",
            hover = "\n§7Showing §e${shown}§7 of §e${total}\n")
    @FCLocale(lang = LocaleType.PT_BR,
            text = "§7De um total de ${total}...",
            hover = "\n§7Mostrando §e${shown}§7 de §e${total}\n")
    protected static LocaleMessage OF_A_TOTAL_OF_X_ENTRIES;

    @FCLocale(lang = LocaleType.EN_US, text = "§7§o Nothing to show here...")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7§o Nada para mostrar aqui...")
    protected static LocaleMessage NOTHING_TO_SHOW;

    // The unit the measured rule repeats. The colour lives inside it because straightLineOf closes
    // the whole run with a reset, and a colour written before that run would be cancelled by it.
    private static final String RULE_UNIT = "§a§m-§r";

    static final ClassicPageTheme LITERAL = new ClassicPageTheme(false, false, false);
    static final ClassicPageTheme AUTO_FIT = new ClassicPageTheme(true, false, false);

    private final boolean autoFit;
    private final boolean includeDate;
    private final boolean includeTotalCount;

    private ClassicPageTheme(boolean autoFit, boolean includeDate, boolean includeTotalCount) {
        this.autoFit = autoFit;
        this.includeDate = includeDate;
        this.includeTotalCount = includeTotalCount;
    }

    /** Adds today's date under the rule. */
    public ClassicPageTheme withDate() {
        return new ClassicPageTheme(autoFit, true, includeTotalCount);
    }

    /** Adds how many entries there are, whose hover says how many of them this page can reach. */
    public ClassicPageTheme withTotalCount() {
        return new ClassicPageTheme(autoFit, includeDate, true);
    }

    @Override
    public List<FancyText> header(PageContext context) {
        List<FancyText> header = new ArrayList<>(3);

        header.add(autoFit
                ? FancyText.of(EverNifeCore.getPlatform().getChatAdapter().straightLineOf(RULE_UNIT))
                : PAGE_RULE.getFancyText(context.getReader()).copy());

        if (includeDate) {
            header.add(DATE_OF_TODAY_IS.getFancyText(context.getReader()).copy()
                    .addPlaceholder("date_of_today", ECTimeFormat.getFormattedNoHours(System.currentTimeMillis())));
        }

        if (includeTotalCount) {
            LocaleMessage counter = context.isAboutPlayers() ? OF_A_TOTAL_OF_X_PLAYERS : OF_A_TOTAL_OF_X_ENTRIES;
            header.add(counter.getFancyText(context.getReader()).copy()
                    .addPlaceholder("total", context.getTotalRows())
                    .addPlaceholder("shown", context.getShownRows()));
        }

        return header;
    }

    @Override
    public List<FancyText> footer(PageContext context) {
        return Collections.emptyList();
    }

    @Override
    public @Nullable FancyText empty(PageContext context) {
        return NOTHING_TO_SHOW.getFancyText(context.getReader()).copy();
    }
}
