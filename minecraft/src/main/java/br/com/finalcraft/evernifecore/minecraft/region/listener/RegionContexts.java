package br.com.finalcraft.evernifecore.minecraft.region.listener;

import br.com.finalcraft.ecnmsutils.NMSUtils;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.api.platoverride.entity.NativeEntity;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.region.actor.Actor;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.actor.TypeKey;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Internal: what every region listener repeats to build an ActionContext - the disabled-world check, positions, type keys and actors. */
public final class RegionContexts {

    /** The metadata key Citizens marks its NPCs with - the convention other NPC plugins follow too. */
    public static final String CITIZENS_NPC_METADATA = "NPC";
    public static final TypeKey FIRE = TypeKey.of("environment", "fire");
    public static final TypeKey LIGHTNING = TypeKey.of("environment", "lightning");
    /** What blows up when no entity does: a bed in the Nether, a respawn anchor. */
    public static final TypeKey EXPLOSION = TypeKey.of("environment", "explosion");

    private static final ConcurrentHashMap<Material, TypeKey> BLOCK_KEYS = new ConcurrentHashMap<>();

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

    /**
     * What the block of {@code material} is, by the server's own block registry: {@code minecraft:stone}, and on a modded
     * server the mod's namespace. Read through the NMS bridge where it serves, else from Bukkit's {@code Keyed} (1.13+),
     * else {@code bukkit:<constant>} - a name Bukkit made up, and its namespace says so.
     */
    public static TypeKey key(Material material) {
        return BLOCK_KEYS.computeIfAbsent(material, RegionContexts::blockKey);
    }

    private static TypeKey blockKey(Material material) {
        if (NMSUtils.isSupported() && material.isBlock()) {
            try {
                return TypeKey.parse(NMSUtils.get().blocks().getRegistryName(material));
            } catch (RuntimeException unreadable) {
                //a mod material the bridge cannot map back to a block: the next rung still names it
            }
        }
        return MCVersion.isHigherEquals(MCVersion.v1_13) ? MaterialKeys.of(material) : bukkitNamed(material.name());
    }

    /** What {@code stack} is, by the server's own item registry; the same ladder as {@link #key(Material)}. */
    public static TypeKey key(ItemStack stack) {
        if (NMSUtils.isSupported()) {
            try {
                return TypeKey.parse(NMSUtils.get().items().getRegistryName(stack));
            } catch (RuntimeException unreadable) {
                //no item the registry knows behind the stack: the next rung still names it
            }
        }
        return MCVersion.isHigherEquals(MCVersion.v1_13) ? MaterialKeys.of(stack.getType()) : bukkitNamed(stack.getType().name());
    }

    /**
     * What {@code entity} is, by the server's own entity registry: {@code minecraft:creeper}, a mod's mob under the mod's
     * namespace; a player is {@link Actor#getPlayerKey()}. The same ladder as {@link #key(Material)}, with
     * {@code EntityType} keyed from 1.14.
     */
    public static TypeKey key(Entity entity) {
        if (entity instanceof Player) {
            return Actor.getPlayerKey();
        }
        if (NMSUtils.isSupported()) {
            try {
                String name = NMSUtils.get().entities().getRegistryName(entity);
                if (name != null) {
                    return entityKey(name);
                }
            } catch (RuntimeException unreadable) {
                //an entity the registry does not save: the next rung still names it
            }
        }
        if (MCVersion.isHigherEquals(MCDetailedVersion.v1_14_R1)) {
            try {
                return EntityTypeKeys.of(entity.getType());
            } catch (RuntimeException unknown) {
                //EntityType.UNKNOWN has no key
            }
        }
        return bukkitNamed(entity.getType().name());
    }

    /** An entity registry name as a key. 1.7.10 writes none with a namespace: a bare {@code Pig} is the platform's own, a {@code modid.Name} the mod's. */
    static TypeKey entityKey(String registryName) {
        if (registryName.indexOf(':') > 0) {
            return TypeKey.parse(registryName);
        }
        int dot = registryName.indexOf('.');
        return dot > 0 && dot < registryName.length() - 1
                ? TypeKey.of(registryName.substring(0, dot), registryName.substring(dot + 1))
                : TypeKey.of(Actor.getPlayerKey().getNamespace(), registryName);
    }

    private static TypeKey bukkitNamed(String constant) {
        return TypeKey.of("bukkit", constant.toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("deprecation")   //getItemInHand is the one form every served version has
    public static @Nullable TypeKey itemInHandKey(Player player) {
        ItemStack held = player.getItemInHand();
        return held == null || held.getType() == Material.AIR ? null : key(held);
    }

    /** {@code player} acting as itself - or as the machine it is, when {@code fakePlayer} - with a handle on demand. */
    public static Actor actor(Player player, boolean fakePlayer) {
        Supplier<NativeEntity> handle = () -> new NativeEntity(player);
        return fakePlayer
                ? Actor.entity(key(player), ECEntityType.FAKE_PLAYER, FCBukkitUtil.adapt(player), handle)
                : Actor.player(FCBukkitUtil.adapt(player), handle);
    }

    /**
     * {@code entity} as an actor: its role, the player behind it, and a handle on demand. A Citizens NPC - an entity
     * carrying the {@code NPC} metadata, a player-shaped one included - is an {@link ECEntityType#NPC} with no player
     * behind it; a lightning bolt is the environment.
     */
    public static Actor actor(Entity entity) {
        if (entity instanceof LightningStrike) {
            return Actor.environment(LIGHTNING);
        }
        if (entity.hasMetadata(CITIZENS_NPC_METADATA)) {
            return Actor.entity(key(entity), ECEntityType.NPC, null, () -> new NativeEntity(entity));
        }
        if (entity instanceof Player) {
            return actor((Player) entity, FCBukkitUtil.isFakePlayer((Player) entity));
        }
        Player root = rootPlayer(entity);
        return Actor.entity(key(entity), entityType(entity), root == null ? null : FCBukkitUtil.adapt(root),
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

    /** The role of a non-player entity: a Citizens NPC, an {@link Animals}, a {@link Monster} or anything else. */
    public static ECEntityType entityType(Entity entity) {
        if (entity.hasMetadata(CITIZENS_NPC_METADATA)) {
            return ECEntityType.NPC;
        }
        if (entity instanceof Animals) {
            return ECEntityType.ANIMAL;
        }
        return entity instanceof Monster ? ECEntityType.MONSTER : ECEntityType.OTHER;
    }

    /** Bukkit's own key of a {@code Material}: loaded only from 1.13 on, where it is {@code Keyed}. */
    private static final class MaterialKeys {

        static TypeKey of(Material material) {
            NamespacedKey key = material.getKey();
            return TypeKey.of(key.getNamespace(), key.getKey());
        }
    }

    /** Bukkit's own key of an {@code EntityType}: loaded only from 1.14 on, where it is {@code Keyed}. */
    private static final class EntityTypeKeys {

        static TypeKey of(EntityType type) {
            NamespacedKey key = type.getKey();
            return TypeKey.of(key.getNamespace(), key.getKey());
        }
    }
}
