package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.ranking.RankedEntry;
import br.com.finalcraft.evernifecore.ranking.RankedList;
import br.com.finalcraft.evernifecore.ranking.RankedSnapshot;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The entries of a {@link RankedList} - the core's default for {@link ScreenKey#RANKING}, built from a
 * {@link RankingRequest}. The viewer's own row is marked, and a bar shows how close they are to the position above
 * theirs. The snapshot is read when the window opens, so the list fills a moment after it.
 *
 * <p>The entry template reads {@code {position}}, {@code {subject}}, {@code {value}} and {@code {marker}} (the
 * viewer's own row only); the bar reads {@code {percent}} and {@code {next}}.</p>
 */
public final class RankingScreen extends Screen<RankingScreen.RankingLayout> {

    private static final String SELF_MARKER = "§a» ";

    private static final RegexReplacer<Row> ROW = new RegexReplacer<Row>(Closures.BRACKET)
            .addParser("position", row -> row.position)
            .addParser("subject", row -> row.subject)
            .addParser("value", row -> row.value)
            .addParser("marker", row -> row.self ? SELF_MARKER : "");

    private static final RegexReplacer<RankingScreen> BAR = new RegexReplacer<RankingScreen>(Closures.BRACKET)
            .addParser("percent", screen -> Math.round(screen.ratio.get() * 100))
            .addParser("next", screen -> screen.next == null ? "-" : screen.next);

    private final RankingRequest request;
    private final MutableState<List<Row>> rows = new MutableState<>(Collections.<Row>emptyList());
    private final MutableState<Double> ratio = new MutableState<>(0d);
    private volatile Integer next;

    private RankingScreen(RankingLayout layout, RankingRequest request) {
        super(layout.getRows(), layout);
        this.request = request;
        key(ScreenKey.RANKING);
        if (request.getList().getOwner() != null) {
            owner(request.getList().getOwner());
        }
        if (request.getTitle() != null) {
            title(request.getTitle());
        }
        list(rows::get)
                .id("ranking")
                .into(l -> l.ENTRY)
                .pagedBy(l -> l.PREVIOUS, l -> l.NEXT)
                .dependsOn(rows)
                .render((row, icon) -> icon.addScope(ROW, row));
        progress(l -> l.PROGRESS, ratio).dependsOn(ratio);
        addReplacer(BAR, this);
        addComponent(component -> load(component.getView()));
    }

    /** {@code request}'s list on the admin's {@code RankingLayout}. */
    @Nonnull
    public static RankingScreen of(@Nonnull RankingRequest request) {
        return new RankingScreen(Layouts.of(RankingLayout.class), request);
    }

    @Nonnull
    public RankingRequest getRequest() {
        return request;
    }

    /** The bar: 1 at the top, the viewer's value against the one right above theirs otherwise, 0 when unranked. */
    public double getRatio() {
        return ratio.get();
    }

    private void load(ScreenView view) {
        request.getList().snapshot().thenAccept(snapshot -> view.getScheduler().run(() -> show(view.getViewerId(), snapshot)));
    }

    private <T> void show(UUID viewer, RankedSnapshot<T> snapshot) {
        @SuppressWarnings("unchecked")
        RankedList<T> list = (RankedList<T>) request.getList();
        Integer mine = snapshot.positionOf(viewer);
        List<Row> built = new ArrayList<>();
        Object mineValue = null;
        Object aboveValue = null;
        for (RankedEntry<T> entry : snapshot.getEntries()) {
            boolean self = mine != null && entry.getPosition() == mine;
            Object subject = list.isAboutPlayers() ? list.valueOf(entry, RankedList.PLAYER_KEY) : entry.getObject();
            Object value = list.valueOf(entry, RankedList.VALUE_KEY);
            built.add(new Row(entry.getPosition(), String.valueOf(subject), String.valueOf(value), self));
            if (self) {
                mineValue = entry.getOrderValue();
            } else if (mine != null && entry.getPosition() == mine - 1) {
                aboveValue = entry.getOrderValue();
            }
        }
        next = mine == null || mine == 1 ? null : mine - 1;
        ratio.set(ratioOf(mine, mineValue, aboveValue));
        rows.set(built);
    }

    //the nearer two values are, the nearer to 1, whichever way the list is ordered
    private static double ratioOf(@Nullable Integer position, @Nullable Object mine, @Nullable Object above) {
        if (position == null) {
            return 0d;
        }
        if (position == 1) {
            return 1d;
        }
        if (!(mine instanceof Number) || !(above instanceof Number)) {
            return 0d;
        }
        double a = Math.abs(((Number) mine).doubleValue());
        double b = Math.abs(((Number) above).doubleValue());
        double high = Math.max(a, b);
        return high == 0 ? 1d : Math.min(a, b) / high;
    }

    /** One entry as the template reads it. */
    private static final class Row {

        private final int position;
        private final String subject;
        private final String value;
        private final boolean self;

        private Row(int position, String subject, String value, boolean self) {
            this.position = position;
            this.subject = subject;
            this.value = value;
            this.self = self;
        }

    }

    /** What the ranking screen looks like, and the file the admin restyles it in. */
    @ScreenLayout(title = "§8Ranking", rows = 4)
    public static class RankingLayout extends Layout {

        @IconData(slot = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26})
        public Icon ENTRY = Icon.of("minecraft:paper").displayName("{marker}§e#{position} §f{subject}").lore("§7{value}");

        @IconData(slot = {27})
        public Icon PREVIOUS = DefaultIcons.previousPage();

        @IconData(slot = {35})
        public Icon NEXT = DefaultIcons.nextPage();

        @IconData(slot = {31})
        public Icon PROGRESS = Icon.of("minecraft:experience_bottle")
                .addLocale(LocaleType.EN_US, "§a{percent}% §7of the way to §e#{next}")
                .addLocale(LocaleType.PT_BR, "§a{percent}% §7do caminho até §e#{next}");

        @IconData(slot = {28, 29, 30, 32, 33, 34}, background = true)
        public Icon BACKGROUND = Icon.of(DefaultIcons.pane(DefaultIcons.Color.GRAY)).displayName("§7");

    }

}
