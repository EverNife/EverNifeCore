package br.com.finalcraft.evernifecore.pageviewer.nav;

import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageContext;

import java.util.function.Function;

/**
 * The bar under a page: previous, the page indicator, next. Every strategy draws the same bar and
 * differs only in where a click leads, so the look of navigation is translated and restyled in one
 * place.
 */
public class PVExtraMessages {

    @FCLocale(lang = LocaleType.EN_US, children = {
            @FCLocale.Child(text = "                 &r"),
            @FCLocale.Child(text = "§a§l<§2<§a§l<&r", hover = "\n§a§l<§2<§a§l<\n", click = "%on_previous_page_click%"),
            @FCLocale.Child(text = "           &r")
    })
    @FCLocale(lang = LocaleType.PT_BR, children = {
            @FCLocale.Child(text = "                 &r"),
            @FCLocale.Child(text = "§a§l<§2<§a§l<&r", hover = "\n§a§l<§2<§a§l<\n", click = "%on_previous_page_click%"),
            @FCLocale.Child(text = "           &r")
    })
    protected static LocaleMessage PREVIOUS_PAGE_WHEN_AVAILABLE;

    @FCLocale(lang = LocaleType.EN_US, children = {
            @FCLocale.Child(text = "                 &r"),
            @FCLocale.Child(text = "§7§l<§7<§7§l<&r", hover = "\n§7§l<§7<§7§l<\n"),
            @FCLocale.Child(text = "           &r")
    })
    @FCLocale(lang = LocaleType.PT_BR, children = {
            @FCLocale.Child(text = "                 &r"),
            @FCLocale.Child(text = "§7§l<§7<§7§l<&r", hover = "\n§7§l<§7<§7§l<\n"),
            @FCLocale.Child(text = "           &r")
    })
    protected static LocaleMessage PREVIOUS_PAGE_WHEN_UNAVAILABLE;

    @FCLocale(lang = LocaleType.EN_US,
            text = "§ePage [${current_page}/${last_page}]§r",
            hover = "\n§a Refresh page [${current_page}]"
                  + "\n§7You are §e#${my_rank}§7 of §e${total}\n",
            click = "%on_refresh_page_click%")
    @FCLocale(lang = LocaleType.PT_BR,
            text = "§ePágina [${current_page}/${last_page}]§r",
            hover = "\n§aAtualizar a página [${current_page}]"
                  + "\n§7Você está em §e#${my_rank}§7 de §e${total}\n",
            click = "%on_refresh_page_click%")
    protected static LocaleMessage CENTER_PAGE_BUTTON;

    @FCLocale(lang = LocaleType.EN_US, children = {
            @FCLocale.Child(text = "            &r"),
            @FCLocale.Child(text = "§a§l>§2>§a§l>", hover = "\n§a§l>§2>§a§l>\n", click = "%on_next_page_click%"),
    })
    @FCLocale(lang = LocaleType.PT_BR, children = {
            @FCLocale.Child(text = "            &r"),
            @FCLocale.Child(text = "§a§l>§2>§a§l>", hover = "\n§a§l>§2>§a§l>\n", click = "%on_next_page_click%"),
    })
    protected static LocaleMessage NEXT_PAGE_WHEN_AVAILABLE;

    @FCLocale(lang = LocaleType.EN_US, children = {
            @FCLocale.Child(text = "            &r"),
            @FCLocale.Child(text = "§7§l>§7>§7§l>&r", hover = "\n§7§l>§7>§7§l>\n"),
    })
    @FCLocale(lang = LocaleType.PT_BR, children = {
            @FCLocale.Child(text = "            &r"),
            @FCLocale.Child(text = "§7§l>§7>§7§l>&r", hover = "\n§7§l>§7>§7§l>\n"),
    })
    protected static LocaleMessage NEXT_PAGE_WHEN_UNAVAILABLE;

    /**
     * The bar for one reader. {@code linkFor} answers what clicking a page number should run;
     * a page number outside the page range is never asked for, it simply gets no link.
     */
    public static FancyText navigationBar(PageContext context, Function<Integer, String> linkFor) {
        int currentPage = context.getCurrentPage();
        int lastPage = context.getLastPage();

        FancyText previousPageButton = currentPage > 1
                ? PREVIOUS_PAGE_WHEN_AVAILABLE.getFancyText(context.getReader())
                : PREVIOUS_PAGE_WHEN_UNAVAILABLE.getFancyText(context.getReader());

        FancyText nextPageButton = lastPage > currentPage
                ? NEXT_PAGE_WHEN_AVAILABLE.getFancyText(context.getReader())
                : NEXT_PAGE_WHEN_UNAVAILABLE.getFancyText(context.getReader());

        //append copies what it takes in, so the locale's own text cannot be reached by the replace()
        //calls below - those bake this send's links into the chain's own copies.
        return FancyFormatter.of()
                .append(previousPageButton)
                .append(CENTER_PAGE_BUTTON.getFancyText(context.getReader()))
                .append(nextPageButton)
                .replace("%on_previous_page_click%", linkOf(context, linkFor, currentPage - 1))
                .replace("%on_next_page_click%", linkOf(context, linkFor, currentPage + 1))
                .replace("%on_refresh_page_click%", linkOf(context, linkFor, currentPage))
                .addPlaceholder("current_page", currentPage)
                .addPlaceholder("last_page", lastPage)
                .addPlaceholder("total", context.getTotalRows())
                //Declared, not computed: the scan behind it only runs where the hover cites the key,
                //and a reader who is not on the list still reads a whole sentence.
                .addParser("my_rank", renderContext -> {
                    Integer rank = context.getReaderRank();
                    return rank == null ? "-" : rank;
                });
    }

    private static String linkOf(PageContext context, Function<Integer, String> linkFor, int page) {
        return page < 1 || page > context.getLastPage() ? "" : linkFor.apply(page);
    }
}
