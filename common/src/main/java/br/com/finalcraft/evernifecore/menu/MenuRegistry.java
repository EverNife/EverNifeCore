package br.com.finalcraft.evernifecore.menu;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.screen.ClickContext;
import br.com.finalcraft.evernifecore.screen.ScreenNavigation;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every registered {@link Menu}, by its {@code owner:name}: anything - a command, the {@code open-menu} effect, another
 * plugin - opens one by id with positional arguments. A plugin's menus leave with its shutdown. Thread-safe.
 */
public final class MenuRegistry {

    private static final MenuRegistry INSTANCE = new MenuRegistry();

    private final Map<MenuId, Menu> menus = new ConcurrentHashMap<>();

    private MenuRegistry() {
    }

    public static MenuRegistry get() {
        return INSTANCE;
    }

    /**
     * @throws IllegalArgumentException when {@code menu} belongs to another plugin
     * @throws IllegalStateException naming both owners when the id is already registered
     */
    public void register(ECPluginData owner, Menu menu) {
        if (menu.getOwner() != owner) {
            throw new IllegalArgumentException("The menu " + menu.getId() + " was built by " + menu.getOwner().getMetaInfo().getName()
                    + " and is being registered by " + owner.getMetaInfo().getName() + ": a plugin registers only the menus it built with"
                    + " Menu.of(itself, name).");
        }
        Menu held = menus.putIfAbsent(menu.getId(), menu);
        if (held != null) {
            throw new IllegalStateException("[" + held.getOwner().getMetaInfo().getName() + "] already registered the menu " + menu.getId()
                    + ", and [" + owner.getMetaInfo().getName() + "] tried to register it again. One menu per id: unregister the first, or"
                    + " give the second another name.");
        }
    }

    /**
     * Takes out one menu of {@code owner} - a reload that drops a menu file, an editor that deletes one. {@code name} is
     * the bare name or the full {@code owner:name}.
     *
     * @return whether a menu was registered under it
     * @throws IllegalArgumentException when the menu under that id was registered by another plugin, or the name breaks
     *                                  the id rule
     */
    public boolean unregister(ECPluginData owner, String name) {
        MenuId id = MenuId.parse(name, owner.getMetaInfo().getName());
        Menu held = menus.get(id);
        if (held == null) {
            return false;
        }
        if (held.getOwner() != owner) {
            throw new IllegalArgumentException("[" + owner.getMetaInfo().getName() + "] cannot unregister the menu " + id + ": it was registered by ["
                    + held.getOwner().getMetaInfo().getName() + "]. Only the owner takes a menu out.");
        }
        return menus.remove(id, held);
    }

    /** Takes out every menu {@code owner} registered; called by its shutdown. */
    public void unregisterAll(ECPluginData owner) {
        menus.values().removeIf(menu -> menu.getOwner() == owner);
    }

    /** The menu {@code id} ({@code owner:name}, any case) names; null when none or when the id is malformed. */
    public @Nullable Menu find(String id) {
        try {
            int colon = id == null ? -1 : id.indexOf(':');
            return colon < 0 ? null : menus.get(MenuId.parse(id, id.substring(0, colon)));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    public Collection<Menu> getAll() {
        return Collections.unmodifiableCollection(new ArrayList<>(menus.values()));
    }

    public Collection<Menu> getOwnedBy(ECPluginData owner) {
        List<Menu> owned = new ArrayList<>();
        for (Menu menu : menus.values()) {
            if (menu.getOwner() == owner) {
                owned.add(menu);
            }
        }
        return owned;
    }

    /**
     * Opens {@code id} for {@code viewer} as a new navigation, {@code arguments} being its {@code {0}}..{@code {n}}. Its
     * open gate is checked first and paid once the screen is up, so an open the platform refuses charges nothing: a
     * denial tells the viewer why and the future fails with that text (a payment refused after the window opened closes
     * it again).
     *
     * @throws IllegalArgumentException naming the id, the registered menus and how to register, when nothing is registered under it
     */
    public CompletableFuture<ScreenView> open(FPlayer viewer, String id, List<String> arguments) {
        Menu menu = require(id);
        ExecutionContext ctx = MenuContext.of(viewer, Caller.of(menu.getOwner(), menu.getId().toString()), arguments);
        String refusal = refusalToOpen(menu, ctx, false);
        if (refusal != null) {
            return refused(refusal);
        }
        return Screens.open(viewer, menu.getScreen(), arguments.toArray()).thenCompose(view -> {
            String unpaid = refusalToOpen(menu, ctx, true);
            if (unpaid == null) {
                return CompletableFuture.completedFuture(view);
            }
            view.close();
            return refused(unpaid);
        });
    }

    /**
     * Opens {@code id} from where {@code from} runs: on top of the screen a click came from (back returns to it), or as
     * a new navigation for the context's player. Completes with what the menu hands back when opened on top, with its
     * view otherwise. The open gate is checked and paid as {@link #open(FPlayer, String, List)} does.
     *
     * @throws IllegalArgumentException as {@link #open(FPlayer, String, List)}
     * @throws IllegalStateException when the context has no player to open it for
     */
    public CompletableFuture<?> open(ExecutionContext from, String id, List<String> arguments) {
        ClickContext click = from.getAttribute(ClickContext.class.getName(), ClickContext.class);
        if (click == null || click.getViewer() == null) {
            if (from.getPlayer() == null) {
                throw new IllegalStateException("The menu " + id + " was asked open by " + from.getCaller() + ", whose context has no player:"
                        + " build it with .player(player), or call open(viewer, id, arguments).");
            }
            return open(from.getPlayer(), id, arguments);
        }
        Menu menu = require(id);
        ExecutionContext ctx = MenuContext.of(click.getViewer(), Caller.of(menu.getOwner(), menu.getId().toString()), arguments);
        String refusal = refusalToOpen(menu, ctx, false);
        if (refusal != null) {
            return refused(refusal);
        }
        return ScreenNavigation.open(click.getView(), menu.getScreen(), arguments.toArray(), opened -> refusalToOpen(menu, ctx, true));
    }

    /** Checks (or pays) the open gate; the denial text, already told to the player, or null when it passed. */
    private static @Nullable String refusalToOpen(Menu menu, ExecutionContext ctx, boolean paying) {
        if (menu.getOpenGate() == null) {
            return null;
        }
        GateResult result = paying ? menu.getOpenGate().pay(ctx) : menu.getOpenGate().check(ctx);
        if (result.isPassed()) {
            return null;
        }
        String why = result.explain().toLegacyString();
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().sendMessage("§c" + why);
        }
        return why;
    }

    private static <T> CompletableFuture<T> refused(String why) {
        CompletableFuture<T> refused = new CompletableFuture<>();
        refused.completeExceptionally(new IllegalStateException(why));
        return refused;
    }

    private Menu require(String id) {
        Menu menu = find(id);
        if (menu == null) {
            List<String> ids = new ArrayList<>();
            for (MenuId each : menus.keySet()) {
                ids.add(each.toString());
            }
            Collections.sort(ids);
            throw new IllegalArgumentException("No menu '" + id + "' is registered. Registered menus: " + ids + ". The plugin that owns it"
                    + " registers it with MenuRegistry.get().register(owner, Menu.of(owner, \"<name>\")...build()).");
        }
        return menu;
    }
}
