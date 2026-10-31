package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.menu.MenuRegistry;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The stock effect that opens a registered menu by its id. */
final class StockMenus {

    static final String OPEN_MENU = "open-menu";

    private StockMenus() {
    }

    static void register(ECPluginData core) {
        Effects.registry().register(core, TypeDescriptor.<Effect>of("evernifecore:" + OPEN_MENU)
                .describe("Opens a registered menu for the player - on top of the screen a click came from, so back returns there.")
                .param(ParamSpec.required("menu", ParamSpec.Kind.STRING, "The menu id, owner:name - e.g. finalmenus:shop-vip."))
                .param(ParamSpec.optional("args", ParamSpec.Kind.LIST, Collections.emptyList(),
                        "What {0}..{n} read in the menu's text; each one has its {variables} filled."))
                .factory(params -> new OpenMenu(params.getString("menu"), params.getList("args")))
                .build());
    }

    static final class OpenMenu implements Effect {

        private final String menu;
        private final List<String> args;

        OpenMenu(String menu, List<String> args) {
            this.menu = menu;
            this.args = args;
        }

        @Override
        public void apply(ExecutionContext ctx) {
            if (ctx.getPlayer() == null) {
                //a menu is a window in front of someone: with nobody in the context there is nothing to do but say so
                ctx.getAudit().add(ctx, AuditKind.EFFECT_FAILED, this + ": no player to open a menu for");
                return;
            }
            List<String> resolved = new ArrayList<>(args.size());
            for (String arg : args) {
                resolved.add(ctx.resolve(arg));
            }
            MenuRegistry.get().open(ctx, menu, resolved);
        }

        @Override
        public String toString() {
            return OPEN_MENU + "(" + menu + ")";
        }
    }
}
