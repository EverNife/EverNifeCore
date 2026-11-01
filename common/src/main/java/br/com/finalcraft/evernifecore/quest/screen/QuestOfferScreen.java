package br.com.finalcraft.evernifecore.quest.screen;

import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.QuestParams;
import br.com.finalcraft.evernifecore.screen.ClickContext;
import br.com.finalcraft.evernifecore.screen.ClickRule;
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

import java.util.Objects;

/**
 * One quest offered to the viewer - the core's default for {@link ScreenKey#QUEST_OFFER}, built from a {@link Request}:
 * its name and description, what its gate still misses (greyed, on the accept button), and accept (starts it) or decline
 * (closes). The admin restyles it in {@code guis/QuestOfferLayout.yml}.
 *
 * <p>The quest icon reads {@code {name}} and {@code {description}}; the accept button reads {@code {missing}}, empty
 * when the gate passes.</p>
 */
public final class QuestOfferScreen extends Screen<QuestOfferScreen.QuestOfferLayout> {

    @FCLocale(lang = LocaleType.EN_US, text = "§cThe quest did not start: %why%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cA quest não começou: %why%")
    private static LocaleMessage NOT_STARTED;

    private static final RegexReplacer<QuestOfferScreen> TEXT = new RegexReplacer<QuestOfferScreen>(Closures.BRACKET)
            .addParser("name", screen -> screen.definition.getName())
            .addParser("description", screen -> screen.definition.getDescription())
            .addParser("missing", screen -> screen.missing.get());

    private final Request request;
    private final QuestDefinition definition;
    private final MutableState<String> missing = new MutableState<>("");

    private QuestOfferScreen(QuestOfferLayout layout, Request request) {
        super(layout.getRows(), layout);
        this.request = request;
        this.definition = Objects.requireNonNull(request.getEngine().get(request.getQuest()),
                () -> "Quest '" + request.getQuest() + "' is not registered in " + request.getEngine() + ": register it before offering it.");
        key(ScreenKey.QUEST_OFFER);
        owner(request.getEngine().getOwner());
        icon(l -> l.QUEST).id("quest");
        icon(l -> l.ACCEPT).id("accept").dependsOn(missing).onClick(this::accept);
        icon(l -> l.DECLINE).id("decline").onClick(ClickContext::close);
        addReplacer(TEXT, this);
        addComponent(component -> check(component.getView()));
    }

    /** {@code request} on the admin's {@code QuestOfferLayout}. */
    @Nonnull
    public static QuestOfferScreen of(@Nonnull Request request) {
        return new QuestOfferScreen(Layouts.of(QuestOfferLayout.class), request);
    }

    /** What the gate misses for the viewer, as the accept button shows it; empty when it passes. */
    @Nonnull
    public String getMissing() {
        return missing.get();
    }

    private void check(ScreenView view) {
        GateResult result = request.getEngine().checkStart(view.getViewer(), request.getQuest());
        missing.set(result == null || result.isPassed() ? "" : "§8" + ClickRule.missingOf(result));
    }

    private void accept(ClickContext click) {
        QuestEngine engine = request.getEngine();
        ProgressOwner owner = ProgressOwner.player(click.getViewer().getUniqueId());
        engine.start(owner, request.getQuest(), QuestParams.NONE).whenComplete((result, failure) -> click.getView().getScheduler().run(() -> {
            if (result != null && result.isSuccess()) {
                click.close();
                return;
            }
            FancyText rewritten = result == null ? null : engine.getPresenter().refusal(click.getViewer(), definition, result);
            if (rewritten != null) {
                click.getViewer().sendMessage(rewritten);
            } else if (NOT_STARTED != null) {
                String why = failure != null ? failure.toString() : result.getWhy() != null ? result.getWhy()
                        : result.getGate() != null ? ClickRule.missingOf(result.getGate()) : result.toString();
                NOT_STARTED.custom().addPlaceholder("%why%", why).send(click.getViewer());
            }
            check(click.getView());
        }));
    }

    /** Which quest of which engine to offer. */
    public static final class Request {

        private final QuestEngine engine;
        private final QuestId quest;

        private Request(QuestEngine engine, QuestId quest) {
            this.engine = Objects.requireNonNull(engine, "engine");
            this.quest = Objects.requireNonNull(quest, "quest");
        }

        public static Request of(QuestEngine engine, QuestId quest) {
            return new Request(engine, quest);
        }

        public QuestEngine getEngine() {
            return engine;
        }

        public QuestId getQuest() {
            return quest;
        }

        @Override
        public String toString() {
            return "QuestOffer.Request{" + quest + "}";
        }
    }

    /** What an offer looks like, and the file the admin restyles it in. */
    @ScreenLayout(title = "§8Quest", rows = 3)
    public static class QuestOfferLayout extends Layout {

        @IconData(slot = {4})
        public Icon QUEST = Icon.of("minecraft:writable_book").displayName("§e{name}").lore("§7{description}");

        @IconData(slot = {11})
        public Icon ACCEPT = Icon.of(DefaultIcons.wool(DefaultIcons.Color.LIME))
                .addLocale(LocaleType.EN_US, "§a§lAccept", "{missing}")
                .addLocale(LocaleType.PT_BR, "§a§lAceitar", "{missing}");

        @IconData(slot = {15})
        public Icon DECLINE = DefaultIcons.deny();

        @IconData(slot = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 12, 13, 14, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26}, background = true)
        public Icon BACKGROUND = Icon.of(DefaultIcons.pane(DefaultIcons.Color.GRAY)).displayName("§7");
    }
}
