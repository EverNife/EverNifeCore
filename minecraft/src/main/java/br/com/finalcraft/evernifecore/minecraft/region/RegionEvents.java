package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.flag.DamageContext;
import br.com.finalcraft.evernifecore.region.flag.SpawnContext;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/** What every region listener repeats: the disabled-world check, positions, platform-neutral ids and the player behind a hit. */
final class RegionEvents {

    private RegionEvents() {
    }

    /** The only reader of {@code Regions.disabled-worlds}: nothing in such a world is asked of a region. */
    static boolean isDisabled(World world) {
        return ECSettings.REGIONS_DISABLED_WORLDS.contains(world.getName());
    }

    static WorldLocPos position(Location at) {
        return WorldLocPos.of(at.getX(), at.getY(), at.getZ(), at.getWorld().getName());
    }

    /** {@code at} as a location, facing where {@code like} faces. */
    static Location toLocation(WorldLocPos at, Location like) {
        World world = like.getWorld().getName().equals(at.getWorldName()) ? like.getWorld() : Bukkit.getWorld(at.getWorldName());
        return new Location(world, at.getX(), at.getY(), at.getZ(), like.getYaw(), like.getPitch());
    }

    static String id(Material material) {
        return "minecraft:" + material.name().toLowerCase(Locale.ROOT);
    }

    static String id(EntityType type) {
        return "minecraft:" + type.name().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("deprecation")   //getItemInHand is the one form every served version has
    static @Nullable String itemInHandId(Player player) {
        ItemStack held = player.getItemInHand();
        return held == null || held.getType() == Material.AIR ? null : id(held.getType());
    }

    /** The player behind {@code damager}: the shooter of a projectile, the igniter of TNT, the owner of a pet; null for none. */
    static @Nullable Player rootPlayer(@Nullable Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            Object shooter = ((Projectile) damager).getShooter();
            return shooter instanceof Entity ? rootPlayer((Entity) shooter) : null;
        }
        if (damager instanceof TNTPrimed) {
            return rootPlayer(((TNTPrimed) damager).getSource());
        }
        if (damager instanceof Tameable && ((Tameable) damager).isTamed() && ((Tameable) damager).getOwner() != null) {
            return Bukkit.getPlayer(((Tameable) damager).getOwner().getUniqueId());
        }
        return null;
    }

    static DamageContext.Kind victimKind(Entity victim) {
        if (victim instanceof Player) {
            return DamageContext.Kind.PLAYER;
        }
        if (victim instanceof Animals) {
            return DamageContext.Kind.ANIMAL;
        }
        return victim instanceof Monster ? DamageContext.Kind.MONSTER : DamageContext.Kind.OTHER;
    }

    static SpawnContext.Kind spawnKind(Entity spawned) {
        if (spawned instanceof Animals) {
            return SpawnContext.Kind.ANIMAL;
        }
        return spawned instanceof Monster ? SpawnContext.Kind.MONSTER : SpawnContext.Kind.OTHER;
    }
}
