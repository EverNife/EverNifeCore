package br.com.finalcraft.evernifecore.minecraft.npc;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.action.listener.McActionContexts;
import br.com.finalcraft.evernifecore.minecraft.waypoint.Reflect;
import br.com.finalcraft.evernifecore.npc.NpcProvider;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * An NPC plugin the core never compiles against: named by its plugin name, an API class and a click event, all as
 * strings. Whether the API class loads is looked up once and kept; the click event is subscribed with
 * {@code registerEvent} only while {@code interact-npc} is needed.
 */
abstract class ReflectedNpcProvider implements NpcProvider {

    private final String id;
    private final String pluginName;
    private final String apiClass;
    private final String clickEvent;
    private final Plugin core;
    private final Listener listener = new Listener() {
    };
    private volatile @Nullable Boolean apiLoaded;
    private volatile int apiLookups;

    ReflectedNpcProvider(String id, String pluginName, String apiClass, String clickEvent, Plugin core) {
        this.id = id;
        this.pluginName = pluginName;
        this.apiClass = apiClass;
        this.clickEvent = clickEvent;
        this.core = core;
    }

    /** A click on an NPC: who, which NPC (its actor carries the reference), and where it stands. */
    static final class Click {

        final Player player;
        final Actor npc;
        final Location at;

        Click(Player player, Actor npc, Location at) {
            this.player = player;
            this.npc = npc;
            this.at = at;
        }
    }

    /** The right-click {@code event} reports; null for a left click, or an NPC this provider cannot place. */
    abstract @Nullable Click click(Event event);

    @Override
    public String getId() {
        return id;
    }

    /** The API class first, looked up once: an absent plugin costs one failed {@code Class.forName}, ever. */
    @Override
    public boolean isAvailable() {
        return isApiLoaded() && EverNifeCore.getPlatform().isPluginEnabled(pluginName);
    }

    private boolean isApiLoaded() {
        Boolean loaded = apiLoaded;
        if (loaded == null) {
            apiLookups++;
            try {
                Class.forName(apiClass, false, ReflectedNpcProvider.class.getClassLoader());
                loaded = true;
            } catch (ClassNotFoundException | LinkageError absent) {
                loaded = false;
            }
            apiLoaded = loaded;
        }
        return loaded;
    }

    /** How many times the API class was looked up - once at most. */
    int getApiLookups() {
        return apiLookups;
    }

    /** A packet NPC: no server entity, so no handle - its actor is keyed {@code <provider>:npc}. */
    Actor packetNpc(NpcRef ref) {
        return Actor.entity(TypeKey.of(id, "npc"), ECEntityType.NPC, null, null, ref);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void start() {
        if (!isAvailable()) {
            return;
        }
        Class<? extends Event> type = (Class<? extends Event>) Reflect.type(clickEvent);
        //the Pre where other plugins still change the click, the Post after every one of them had its say
        Bukkit.getPluginManager().registerEvent(type, listener, EventPriority.NORMAL, (ignored, event) -> {
            if (type.isInstance(event)) {
                pre(event);
            }
        }, core, true);
        Bukkit.getPluginManager().registerEvent(type, listener, EventPriority.MONITOR, (ignored, event) -> {
            if (type.isInstance(event)) {
                post(event);
            }
        }, core, true);
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(listener);
    }

    private void pre(Event event) {
        EntityContext context = context(event);
        if (context != null && Actions.denied(context)) {
            ((Cancellable) event).setCancelled(true);
        }
    }

    private void post(Event event) {
        if (context(event) != null) {
            Actions.post(EventSource.INTERACT_NPC, () -> context(event));
        }
    }

    private @Nullable EntityContext context(Event event) {
        Click click;
        try {
            click = click(event);
        } catch (RuntimeException apiChanged) {
            EverNifeCore.getLog().warning("[NPC] A " + pluginName + " click could not be read, so it raises no interact-npc: "
                    + apiChanged.getMessage() + ". Update EverNifeCore, or report the " + pluginName + " version in use.");
            return null;
        }
        return click == null ? null : McActionContexts.npcUse(click.player, click.npc, click.at, event);
    }

    static Optional<WorldLocPos> position(@Nullable Location at) {
        return at == null || at.getWorld() == null ? Optional.empty() : Optional.of(McActionContexts.position(at));
    }

    /** {@code target.method(args)} through {@link Reflect}, or null when {@code target} is. */
    static @Nullable Object call(@Nullable Object target, String method, Object... args) {
        return target == null ? null : Reflect.call(target, method, args);
    }
}
