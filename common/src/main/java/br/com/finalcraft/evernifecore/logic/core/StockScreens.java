package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.screen.ScreenKey;
import br.com.finalcraft.evernifecore.screen.ScreenRequest;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;

import java.util.Collections;
import java.util.List;

/** The stock effect that opens a screen by its key. */
final class StockScreens {

    static final String OPEN_SCREEN = "open-screen";

    private StockScreens() {
    }

    static void register(ECPluginData core) {
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:" + OPEN_SCREEN)
                .describe("Opens the screen a key names for the player - whatever override answers it, else the core's default.")
                .param(ParamSpec.required("screen", ParamSpec.Kind.STRING, "The screen key, owner:name - e.g. evernifecore:deliveries."))
                .param(ParamSpec.optional("args", ParamSpec.Kind.LIST, Collections.emptyList(),
                        "What {0}..{n} read in the screen's text; each one has its {variables} filled."))
                .factory(params -> new OpenScreen(ScreenKey.parse(params.getString("screen")), params.getList("args")))
                .build());
    }

    static final class OpenScreen implements Effect {

        private final ScreenKey key;
        private final List<String> args;

        OpenScreen(ScreenKey key, List<String> args) {
            this.key = key;
            this.args = args;
        }

        @Override
        public void apply(ExecutionContext ctx) {
            FPlayer player = ctx.getPlayer();
            if (player == null) {
                //a screen is a window in front of someone: with nobody in the context there is nothing to do but say so
                ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, this + ": no player to open a screen for");
                return;
            }
            Object[] resolved = new Object[args.size()];
            for (int i = 0; i < resolved.length; i++) {
                resolved[i] = ctx.resolve(args.get(i));
            }
            Screens.open(player, key, ScreenRequest.of(ctx.getCaller().getPlugin()).args(resolved));
        }

        @Override
        public String toString() {
            return OPEN_SCREEN + "(" + key + ")";
        }

    }

}
