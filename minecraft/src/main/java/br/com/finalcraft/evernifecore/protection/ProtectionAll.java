package br.com.finalcraft.evernifecore.protection;

import br.com.finalcraft.evernifecore.protection.integration.ProtectionHandler;
import br.com.finalcraft.evernifecore.protection.integration.imp.GriefDefenderHandler;
import br.com.finalcraft.evernifecore.protection.integration.imp.GriefPreventionPlusHandler;
import br.com.finalcraft.evernifecore.protection.integration.imp.WorldGuardHandler;
import br.com.finalcraft.evernifecore.vectors.CuboidSelection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ProtectionAll implements ProtectionHandler{

    private static final ProtectionAll INSTANCE = new ProtectionAll(
            Arrays.asList(
                    Bukkit.getPluginManager().isPluginEnabled("WorldGuard") ? new WorldGuardHandler() : null,
                    Bukkit.getPluginManager().isPluginEnabled("GriefDefender") ? new GriefDefenderHandler() : null,
                    Bukkit.getPluginManager().isPluginEnabled("GriefPreventionPlus") ? new GriefPreventionPlusHandler() : null
            ).stream().filter(Objects::nonNull).collect(Collectors.toList())
    );

    public static ProtectionAll getInstance() {
        return INSTANCE;
    }

    private final List<ProtectionHandler> PROTECTION_HANDLERS = new ArrayList<>();

    public ProtectionAll(List<ProtectionHandler> protectionHandlers) {
        this.PROTECTION_HANDLERS.addAll(protectionHandlers);
    }

    public List<ProtectionHandler> getProtectionHandlers() {
        return PROTECTION_HANDLERS;
    }

    @Override
    public String getName() {
        return "ProtectionAll";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        for (ProtectionHandler protectionHandler : PROTECTION_HANDLERS) {
            if (!protectionHandler.canBuild(player, location)){
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canBreak(Player player, Location location) {
        for (ProtectionHandler protectionHandler : PROTECTION_HANDLERS) {
            if (!protectionHandler.canBreak(player, location)){
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canInteract(Player player, Location location) {
        for (ProtectionHandler protectionHandler : PROTECTION_HANDLERS) {
            if (!protectionHandler.canInteract(player, location)){
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canAttack(Player player, Entity victim) {
        for (ProtectionHandler protectionHandler : PROTECTION_HANDLERS) {
            if (!protectionHandler.canAttack(player, victim)){
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canUseAoE(Player player, Location location, int range) {
        for (ProtectionHandler protectionHandler : PROTECTION_HANDLERS) {
            if (!protectionHandler.canUseAoE(player, location, range)){
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canBuildOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        for (ProtectionHandler protectionHandler : PROTECTION_HANDLERS) {
            if (!protectionHandler.canBuildOnRegion(player, world, cuboidSelection)){
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canBreakOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        for (ProtectionHandler protectionHandler : PROTECTION_HANDLERS) {
            if (!protectionHandler.canBreakOnRegion(player, world, cuboidSelection)){
                return false;
            }
        }
        return true;
    }

}
