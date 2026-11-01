package br.com.finalcraft.evernifecore.quest.screen;

import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestState;
import br.com.finalcraft.evernifecore.quest.QuestStatus;
import br.com.finalcraft.evernifecore.screen.DefaultIcons;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.IconData;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.Layouts;
import br.com.finalcraft.evernifecore.screen.MutableState;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenKey;
import br.com.finalcraft.evernifecore.screen.ScreenLayout;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The viewer's quests, filtered by status - the core's default for {@link ScreenKey#QUEST_JOURNAL}, built from a
 * {@link Request}. A click on a quest tracks it; the filter button cycles ACTIVE, AVAILABLE, COMPLETED and every status.
 * The admin restyles it in {@code guis/QuestJournalLayout.yml}; a plugin replaces it with
 * {@code Screens.override(plugin, ScreenKey.QUEST_JOURNAL, ...)}.
 *
 * <p>The entry template reads {@code {id}}, {@code {name}}, {@code {description}}, {@code {status}} and
 * {@code {percent}} (of the current stage); the filter button reads {@code {filter}}.</p>
 */
public final class QuestJournalScreen extends Screen<QuestJournalScreen.QuestJournalLayout> {

    private static final QuestStatus[] CYCLE = {QuestStatus.ACTIVE, QuestStatus.AVAILABLE, QuestStatus.COMPLETED, null};

    private static final RegexReplacer<Row> ROW = new RegexReplacer<Row>(Closures.BRACKET)
            .addParser("id", row -> row.definition.getId().getName())
            .addParser("name", row -> row.definition.getName())
            .addParser("description", row -> row.definition.getDescription())
            .addParser("status", row -> row.status.name())
            .addParser("percent", row -> row.percent);

    private static final RegexReplacer<QuestJournalScreen> FILTER = new RegexReplacer<QuestJournalScreen>(Closures.BRACKET)
            .addParser("filter", screen -> screen.filter.get() == null ? "ALL" : screen.filter.get().name());

    private final Request request;
    private final MutableState<QuestStatus> filter;
    private final MutableState<List<Row>> rows = new MutableState<>(Collections.<Row>emptyList());

    private QuestJournalScreen(QuestJournalLayout layout, Request request) {
        super(layout.getRows(), layout);
        this.request = request;
        this.filter = new MutableState<>(request.getFilter());
        key(ScreenKey.QUEST_JOURNAL);
        if (request.getEngine() != null) {
            owner(request.getEngine().getOwner());
        }
        list(rows::get)
                .id("quests")
                .into(l -> l.ENTRY)
                .pagedBy(l -> l.PREVIOUS, l -> l.NEXT)
                .dependsOn(rows)
                .render((row, icon) -> icon.addScope(ROW, row))
                .onClick((click, row) -> row.engine.track(click.getViewer(), row.definition.getId()));
        icon(l -> l.FILTER).id("filter").dependsOn(filter).onClick(click -> {
            filter.set(next(filter.get()));
            reload(click.getView());
        });
        addReplacer(FILTER, this);
        addComponent(component -> reload(component.getView()));
    }

    /** {@code request} on the admin's {@code QuestJournalLayout}. */
    @Nonnull
    public static QuestJournalScreen of(@Nonnull Request request) {
        return new QuestJournalScreen(Layouts.of(QuestJournalLayout.class), request);
    }

    /** The quests listed now, in order. */
    @Nonnull
    public List<QuestId> getListed() {
        List<QuestId> ids = new ArrayList<>();
        for (Row row : rows.get()) {
            ids.add(row.definition.getId());
        }
        return ids;
    }

    public @Nullable QuestStatus getFilter() {
        return filter.get();
    }

    private static @Nullable QuestStatus next(@Nullable QuestStatus current) {
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                return CYCLE[(i + 1) % CYCLE.length];
            }
        }
        return CYCLE[0];
    }

    private void reload(ScreenView view) {
        UUID viewer = view.getViewerId();
        Collection<QuestEngine> engines = request.getEngine() == null ? QuestEngine.all() : Collections.singletonList(request.getEngine());
        List<Row> found = new ArrayList<>();
        QuestStatus wanted = filter.get();
        for (QuestEngine engine : engines) {
            for (QuestDefinition definition : engine.getAll()) {
                ProgressOwner owner = engine.getOwnerFor(viewer, definition.getId());
                QuestStatus status = owner == null ? QuestStatus.LOCKED : engine.getStatus(owner, definition.getId(), view.getViewer());
                if ((wanted == null || wanted == status) && definition.getMeta().getVisibility().lists(status)) {
                    QuestState state = owner == null ? null : engine.getState(owner, definition.getId()).orElse(null);
                    found.add(new Row(engine, definition, status, state == null ? 0 : (int) Math.floor(definition.getStageProgress(state) * 100)));
                }
            }
        }
        rows.set(found);
    }

    /** One quest as the template reads it. */
    private static final class Row {

        private final QuestEngine engine;
        private final QuestDefinition definition;
        private final QuestStatus status;
        private final int percent;

        private Row(QuestEngine engine, QuestDefinition definition, QuestStatus status, int percent) {
            this.engine = engine;
            this.definition = definition;
            this.status = status;
            this.percent = percent;
        }
    }

    /** Which quests a journal shows: one engine's or every engine's, starting at a status filter (null = every status). */
    public static final class Request {

        private final @Nullable QuestEngine engine;
        private final @Nullable QuestStatus filter;

        private Request(@Nullable QuestEngine engine, @Nullable QuestStatus filter) {
            this.engine = engine;
            this.filter = filter;
        }

        /** {@code engine}'s quests, or every engine's for null, starting at {@code filter} (null = every status). */
        public static Request of(@Nullable QuestEngine engine, @Nullable QuestStatus filter) {
            return new Request(engine, filter);
        }

        public @Nullable QuestEngine getEngine() {
            return engine;
        }

        public @Nullable QuestStatus getFilter() {
            return filter;
        }

        @Override
        public String toString() {
            return "QuestJournal.Request{" + (engine == null ? "every engine" : Objects.toString(engine)) + ", " + filter + "}";
        }
    }

    /** What the journal looks like, and the file the admin restyles it in. */
    @ScreenLayout(title = "§8Quests", rows = 6)
    public static class QuestJournalLayout extends Layout {

        @IconData(slot = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
                27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44})
        public Icon ENTRY = Icon.of("minecraft:book")
                .addLocale(LocaleType.EN_US, "§e{name}", "§7{description}", "§f{status} §7- §a{percent}%", "", "§a ✎ Click to track")
                .addLocale(LocaleType.PT_BR, "§e{name}", "§7{description}", "§f{status} §7- §a{percent}%", "", "§a ✎ Clique para acompanhar");

        @IconData(slot = {45})
        public Icon PREVIOUS = DefaultIcons.previousPage();

        @IconData(slot = {53})
        public Icon NEXT = DefaultIcons.nextPage();

        @IconData(slot = {49})
        public Icon FILTER = Icon.of("minecraft:hopper")
                .addLocale(LocaleType.EN_US, "§eFilter: §f{filter}", "§7Click to change")
                .addLocale(LocaleType.PT_BR, "§eFiltro: §f{filter}", "§7Clique para mudar");

        @IconData(slot = {46, 47, 48, 50, 51, 52}, background = true)
        public Icon BACKGROUND = Icon.of(DefaultIcons.pane(DefaultIcons.Color.GRAY)).displayName("§7");
    }
}
