package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code interact-npc}: talking to a NPC, named {@code provider:id} ({@code npc: "citizens:12"}); globs on the entity
 * type also work. It counts the right click; {@code click: left} counts the punch instead.
 */
public final class InteractNpcObjectiveType extends ActionObjectiveType {

    public InteractNpcObjectiveType() {
        super("interact-npc", EventSource.INTERACT_NPC, "npc", "Interacting with a NPC.");
    }

    @Override
    public List<ParamSpec> getParams() {
        List<ParamSpec> params = new ArrayList<>(super.getParams());
        params.add(ParamSpec.optional("click", ParamSpec.Kind.STRING, "right", "right (the use, a talk) or left (the punch).")
                .oneOf("right", "left"));
        return params;
    }

    @Override
    public KeyFilter parseFilter(ParsedParams params) {
        String click = params.getString("click");
        return new NpcFilter(params.getString("npc"), "left".equals(click == null ? null : click.toLowerCase(Locale.ROOT))
                ? EntityContext.ClickType.LEFT : EntityContext.ClickType.RIGHT);
    }

    @Override
    public boolean matches(ActionEvent<?> event, KeyFilter filter) {
        EntityContext context = (EntityContext) event.getContext();
        if (context.getClickType() != ((NpcFilter) filter).clickType) {
            return false;
        }
        if (filter.getPattern() == null) {
            return true;
        }
        NpcRef npc = context.getEntity().getNpc();
        if (npc != null && npc.toString().equalsIgnoreCase(filter.getPattern())) {
            return true;
        }
        return super.matches(event, filter);
    }

    /** The NPC glob and the click that counts. */
    static final class NpcFilter extends KeyFilter {

        final EntityContext.ClickType clickType;

        NpcFilter(@Nullable String pattern, EntityContext.ClickType clickType) {
            super(pattern);
            this.clickType = clickType;
        }

        @Override
        public String toString() {
            return clickType == EntityContext.ClickType.LEFT ? super.toString() + " (left click)" : super.toString();
        }
    }
}
