package br.com.finalcraft.evernifecore.minecraft.protection;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.selection.CuboidSelection;
import br.com.finalcraft.evernifecore.minecraft.protection.integration.ProtectionHandler;
import br.com.finalcraft.evernifecore.minecraft.protection.integration.imp.GriefDefenderHandler;
import br.com.finalcraft.evernifecore.minecraft.protection.integration.imp.GriefPreventionPlusHandler;
import br.com.finalcraft.evernifecore.minecraft.protection.integration.imp.WorldGuardHandler;
import jakarta.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every registered {@link ProtectionHandler} asked as one: an action is allowed only when all of them allow it. Each
 * handler is registered under the plugin that owns it and leaves when that plugin disables.
 */
public final class ProtectionAll implements ProtectionHandler {

    private static final ProtectionAll INSTANCE = new ProtectionAll();

    private static final class Registration {
        final ECPluginData owner;
        final ProtectionHandler handler;

        Registration(ECPluginData owner, ProtectionHandler handler) {
            this.owner = owner;
            this.handler = handler;
        }
    }

    /** Read on every protected action, written only when a plugin comes or goes: copy on write fits. */
    private final List<Registration> registrations = new CopyOnWriteArrayList<>();

    private ProtectionAll() {
    }

    public static ProtectionAll getInstance() {
        return INSTANCE;
    }

    /** Adds {@code handler} under {@code owner}; every check asks it from now on. The same instance twice is one registration. */
    public void register(ECPluginData owner, ProtectionHandler handler) {
        Objects.requireNonNull(owner, "'owner' cannot be null when registering a ProtectionHandler!");
        Objects.requireNonNull(handler, "'handler' cannot be null when registering a ProtectionHandler!");
        for (Registration registration : registrations) {
            if (registration.handler == handler) {
                return;
            }
        }
        registrations.add(new Registration(owner, handler));
    }

    /** @return whether {@code handler} was registered */
    public boolean unregister(ProtectionHandler handler) {
        return registrations.removeIf(registration -> registration.handler == handler);
    }

    /** Drops every handler {@code owner} registered - what a plugin's disable does for it. @return how many left */
    public int unregisterAll(ECPluginData owner) {
        int before = registrations.size();
        registrations.removeIf(registration -> registration.owner == owner);
        return before - registrations.size();
    }

    /** The handlers in registration order; a snapshot, never the live list. */
    public List<ProtectionHandler> getProtectionHandlers() {
        List<ProtectionHandler> handlers = new ArrayList<>(registrations.size());
        for (Registration registration : registrations) {
            handlers.add(registration.handler);
        }
        return Collections.unmodifiableList(handlers);
    }

    /** The handler EverNifeCore ships for {@code pluginName}, or null when it ships none; a new instance each call. */
    public static @Nullable ProtectionHandler builtInFor(String pluginName) {
        switch (pluginName) {
            case "WorldGuard":
                return new WorldGuardHandler();
            case "GriefDefender":
                return new GriefDefenderHandler();
            case "GriefPreventionPlus":
                return new GriefPreventionPlusHandler();
            default:
                return null;
        }
    }

    @Override
    public String getName() {
        return "ProtectionAll";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        for (Registration registration : registrations) {
            if (!registration.handler.canBuild(player, location)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canBreak(Player player, Location location) {
        for (Registration registration : registrations) {
            if (!registration.handler.canBreak(player, location)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canInteract(Player player, Location location) {
        for (Registration registration : registrations) {
            if (!registration.handler.canInteract(player, location)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canAttack(Player player, Entity victim) {
        for (Registration registration : registrations) {
            if (!registration.handler.canAttack(player, victim)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canUseAoE(Player player, Location location, int range) {
        for (Registration registration : registrations) {
            if (!registration.handler.canUseAoE(player, location, range)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canBuildOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        for (Registration registration : registrations) {
            if (!registration.handler.canBuildOnRegion(player, world, cuboidSelection)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canBreakOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        for (Registration registration : registrations) {
            if (!registration.handler.canBreakOnRegion(player, world, cuboidSelection)) {
                return false;
            }
        }
        return true;
    }

}
