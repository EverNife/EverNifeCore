package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.CommandContext;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.SpawnContext;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.action.VehicleContext;
import br.com.finalcraft.evernifecore.action.XpContext;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.quest.ObjectiveNature;
import br.com.finalcraft.evernifecore.quest.ObjectiveType;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A stock objective over one action source: it counts the actions whose key (the block, item or creature) matches
 * the filter's glob - {@code minecraft:*_ore} - or every action when the filter names none. One subclass per source.
 */
public abstract class ActionObjectiveType implements ObjectiveType<ActionObjectiveType.KeyFilter> {

    private final String key;
    private final EventSource source;
    private final String param;
    private final String description;

    protected ActionObjectiveType(String name, EventSource source, String param, String description) {
        this.key = "evernifecore:" + name;
        this.source = source;
        this.param = param;
        this.description = description;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<ParamSpec> getParams() {
        return Collections.singletonList(ParamSpec.optional(param, ParamSpec.Kind.ID, null,
                "What counts, by id; globs allowed (minecraft:*_ore). Left out, every " + source.getName() + " action counts."));
    }

    @Override
    public ObjectiveNature getNature() {
        return ObjectiveNature.EVENT;
    }

    @Override
    public Set<EventSource> getSources() {
        return Collections.singleton(source);
    }

    @Override
    public KeyFilter parseFilter(ParsedParams params) {
        return new KeyFilter(params.getString(param));
    }

    @Override
    public boolean matches(ActionEvent<?> event, KeyFilter filter) {
        if (filter.pattern == null) {
            return true;
        }
        TypeKey actionKey = keyOf(event.getContext());
        return actionKey != null && actionKey.matches(filter.pattern);
    }

    @Override
    public long countOf(ActionEvent<?> event, KeyFilter filter) {
        ActionContext context = event.getContext();
        if (context instanceof ItemContext) {
            return Math.max(1, ((ItemContext) context).getAmount());
        }
        if (context instanceof XpContext) {
            return Math.max(1, ((XpContext) context).getAmount());
        }
        return 1;
    }

    /** What an action is about: the block, the item, the creature, the vehicle; null when the action has no such key. */
    public static @Nullable TypeKey keyOf(ActionContext context) {
        if (context instanceof BlockContext) {
            return ((BlockContext) context).getBlockKey();
        }
        if (context instanceof ItemContext) {
            return ((ItemContext) context).getItemKey();
        }
        if (context instanceof EntityContext) {
            return ((EntityContext) context).getEntity().getKey();
        }
        if (context instanceof DamageContext) {
            return ((DamageContext) context).getVictim().getKey();
        }
        if (context instanceof SpawnContext) {
            return ((SpawnContext) context).getEntity().getKey();
        }
        if (context instanceof VehicleContext) {
            Actor vehicle = ((VehicleContext) context).getVehicle();
            return vehicle == null ? null : vehicle.getKey();
        }
        if (context instanceof CommandContext) {
            String label = CommandContext.label(((CommandContext) context).getCommandLine());
            return label.isEmpty() ? null : TypeKey.of("command", label);
        }
        return null;
    }

    /** The glob an objective wrote; null for "every action". */
    public static final class KeyFilter {

        final @Nullable String pattern;

        KeyFilter(@Nullable String pattern) {
            this.pattern = pattern;
        }

        public @Nullable String getPattern() {
            return pattern;
        }

        @Override
        public String toString() {
            return pattern == null ? "*" : pattern;
        }
    }
}
