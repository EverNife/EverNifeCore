package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
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
import java.util.function.Supplier;

/** Internal: what every region listener repeats to build an ActionContext - the disabled-world check, positions, type keys and actors. */
public final class RegionContexts {

    private RegionContexts() {
    }

    /** The only reader of {@code Regions.disabled-worlds}: nothing in such a world is asked of a region. */
    public static boolean isDisabled(World world) {
        return ECSettings.REGIONS_DISABLED_WORLDS.contains(world.getName());
    }

    public static WorldLocPos position(Location at) {
        return WorldLocPos.of(at.getX(), at.getY(), at.getZ(), at.getWorld().getName());
    }

    /** {@code at} as a location, facing where {@code like} faces. */
    public static Location toLocation(WorldLocPos at, Location like) {
        World world = like.getWorld().getName().equals(at.getWorldName()) ? like.getWorld() : Bukkit.getWorld(at.getWorldName());
        return new Location(world, at.getX(), at.getY(), at.getZ(), like.getYaw(), like.getPitch());
    }

    public static TypeKey key(Material material) {
        return TypeKey.of("minecraft", material.name().toLowerCase(Locale.ROOT));
    }

    public static TypeKey key(EntityType type) {
        return TypeKey.of("minecraft", type.name().toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("deprecation")   //getItemInHand is the one form every served version has
    public static @Nullable TypeKey itemInHandKey(Player player) {
        ItemStack held = player.getItemInHand();
        return held == null || held.getType() == Material.AIR ? null : key(held.getType());
    }

    /** {@code player} acting as itself - or as the machine it is, when {@code fakePlayer} - with a handle on demand. */
    public static Actor actor(Player player, boolean fakePlayer) {
        Supplier<NativeEntity> handle = () -> new NativeEntity(player);
        return fakePlayer
                ? Actor.entity(key(player.getType()), ECEntityType.FAKE_PLAYER, FCBukkitUtil.adapt(player), handle)
                : Actor.player(FCBukkitUtil.adapt(player), handle);
    }

    /** {@code entity} as an actor: its role, the player behind it, and a handle on demand. */
    public static Actor actor(Entity entity) {
        if (entity instanceof Player) {
            return actor((Player) entity, FCBukkitUtil.isFakePlayer((Player) entity));
        }
        Player root = rootPlayer(entity);
        return Actor.entity(key(entity.getType()), entityType(entity), root == null ? null : FCBukkitUtil.adapt(root),
                () -> new NativeEntity(entity));
    }

    /** The player behind {@code damager}: the shooter of a projectile, the igniter of TNT, the owner of a pet; null for none. */
    public static @Nullable Player rootPlayer(@Nullable Entity damager) {
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

    /** The role of a non-player entity: an {@link Animals}, a {@link Monster} or anything else. */
    public static ECEntityType entityType(Entity entity) {
        if (entity instanceof Animals) {
            return ECEntityType.ANIMAL;
        }
        return entity instanceof Monster ? ECEntityType.MONSTER : ECEntityType.OTHER;
    }
}
