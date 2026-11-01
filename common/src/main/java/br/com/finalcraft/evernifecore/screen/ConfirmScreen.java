package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;

/**
 * "Are you sure?", as a screen the whole server shares - the core's default for {@link ScreenKey#CONFIRM}. It answers
 * {@code Boolean} through the chain: {@code ctx.open(ConfirmScreen.of(request), Boolean.class)} inside a screen, or
 * {@link Screens#confirm} from anywhere. Denying hands back {@code false} and the screen underneath; walking away
 * cancels the answer.
 *
 * <p>What it looks like is the admin's: {@code guis/ConfirmLayout.yml} restyles the buttons, the background and the
 * size, and the plugin keeps saying only what it asks. With a {@link ConfirmRequest#getGate() gate}, confirming pays it
 * (a {@link ClickRule}) and the confirm button's lore reads {@code {cost}}.</p>
 *
 * <p>A plugin with a confirmation of its own extends it over a layout that extends {@link ConfirmLayout} - its extra
 * fields bound in the subclass constructor - and keeps the shift-click a dangerous request takes, the gate a confirm
 * pays and the {@code {cost}} of the lore.</p>
 */
public class ConfirmScreen<L extends ConfirmScreen.ConfirmLayout> extends Screen<L> {

    @FCLocale(lang = LocaleType.EN_US, text = "§cThis one is serious - hold SHIFT and click to confirm.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cEssa é séria - segure SHIFT e clique para confirmar.")
    private static LocaleMessage HOLD_SHIFT_TO_CONFIRM;

    @FCLocale(lang = LocaleType.EN_US, text = "§cYou cannot confirm yet: %missing%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cVocê ainda não pode confirmar: %missing%")
    private static LocaleMessage CANNOT_CONFIRM;

    private final ConfirmRequest request;
    private final ClickRule payment;

    /** {@code request} on {@code layout}, already wired: CONFIRM answers (or pays, or asks for SHIFT), DENY says no. */
    protected ConfirmScreen(@Nonnull L layout, @Nonnull ConfirmRequest request) {
        super(layout.getRows(), layout);
        this.request = request;
        this.payment = request.getGate() == null ? null
                : ClickRule.on().gate(request.getGate()).onSuccess(request.getOnConfirm().toArray(new Effect[0]));
        key(ScreenKey.CONFIRM);
        if (request.getOwner() != null) {
            owner(request.getOwner());
        }
        title(request.getQuestion());
        icon(l -> l.CONFIRM).onClick(this::onConfirm);
        icon(l -> l.DENY).onClick(context -> context.back(Boolean.FALSE));
        if (request.getDisplay() != null) {
            Grid grid = getGrid();
            icon(Slots.at(grid.getRows() / 2 + 1, grid.getWidth() / 2 + 1), Icon.of(request.getDisplay())).id("display");
        }
        if (payment != null) {
            //the gate's costs as it writes them, for whoever looks: a cost that depends on the viewer reads its base here
            addReplacer(new RegexReplacer<ConfirmScreen<?>>(Closures.BRACKET).addParser("cost", screen -> screen.describeCost()), this);
        }
    }

    /** The confirmation {@code request} on the admin's {@code ConfirmLayout}. */
    @Nonnull
    public static ConfirmScreen<ConfirmLayout> of(@Nonnull ConfirmRequest request) {
        return new ConfirmScreen<>(Layouts.of(ConfirmLayout.class), request);
    }

    @Nonnull
    public ConfirmRequest getRequest() {
        return request;
    }

    private String describeCost() {
        ECPluginData caller = request.getOwner() != null ? request.getOwner() : EverNifeCore.getEcPluginData();
        return payment.describeCost(ExecutionContext.builder(Caller.of(caller, "screen:" + ScreenKey.CONFIRM)).build());
    }

    private void onConfirm(ClickContext context) {
        if (request.isDangerous() && !context.getGesture().isShift()) {
            if (HOLD_SHIFT_TO_CONFIRM != null) {
                HOLD_SHIFT_TO_CONFIRM.send(context.getViewer());
            }
            return;
        }
        if (payment != null) {
            ExecutionContext ctx = context.newExecutionContext().build();
            if (payment.run(ctx) != ClickRule.Outcome.SUCCESS) {
                if (CANNOT_CONFIRM != null) {
                    CANNOT_CONFIRM.custom().addPlaceholder("%missing%", ClickRule.missingOf(request.getGate().check(ctx)))
                            .send(context.getViewer());
                }
                return;
            }
        }
        context.back(Boolean.TRUE);
    }

    /** What a confirmation looks like, and the file the admin restyles it in. */
    @ScreenLayout(title = "§8Are you sure?", rows = 3)
    public static class ConfirmLayout extends Layout {

        @IconData(slot = {11})
        public Icon CONFIRM = DefaultIcons.confirm();

        @IconData(slot = {15})
        public Icon DENY = DefaultIcons.deny();

        @IconData(slot = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14,
                16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26}, background = true)
        public Icon BACKGROUND = Icon.of(DefaultIcons.pane(DefaultIcons.Color.GRAY)).displayName("§7");

    }

}
