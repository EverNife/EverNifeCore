package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;

import java.util.Arrays;
import java.util.List;

/**
 * The flags EverNifeCore ships, as the instances a plugin writes against ({@code region.getFlag(CoreFlags.DENY_PVP)}).
 * Registered by the core's enable. A field may be swapped for a subclass while it is unregistered: stored values
 * follow the key, so they come back on the new instance once it registers.
 */
public final class CoreFlags {

    /** The owner every core flag's key names - EverNifeCore's own plugin name, lowercased. */
    public static final String OWNER = "evernifecore";

    public static DenyPvpFlag DENY_PVP = new DenyPvpFlag();
    public static InvincibleFlag INVINCIBLE = new InvincibleFlag();
    public static DenyDamageAnimalsFlag DENY_DAMAGE_ANIMALS = new DenyDamageAnimalsFlag();
    public static DenyBlockBreakFlag DENY_BLOCK_BREAK = new DenyBlockBreakFlag();
    public static ActorDenyFlag DENY_BLOCK_BREAK_MOBS = new ActorDenyFlag(FlagKey.of(OWNER, "deny-block-break-mobs"), DENY_BLOCK_BREAK,
            "TRUE: animals and monsters cannot break blocks in the region. Where it is not set, deny-block-break decides.", "animal", "monster");
    public static ActorDenyFlag DENY_BLOCK_BREAK_ENVIRONMENT = new ActorDenyFlag(FlagKey.of(OWNER, "deny-block-break-environment"), DENY_BLOCK_BREAK,
            "TRUE: the environment - fire, weather, growth - cannot break blocks in the region. Where it is not set, deny-block-break decides.",
            "environment");
    public static ActorDenyFlag DENY_BLOCK_BREAK_FAKE_PLAYERS = new ActorDenyFlag(FlagKey.of(OWNER, "deny-block-break-fake-players"), DENY_BLOCK_BREAK,
            "TRUE: fake players - machines acting as a player - cannot break blocks in the region. Where it is not set, deny-block-break decides.",
            "fake-player");
    public static DenyBlockPlaceFlag DENY_BLOCK_PLACE = new DenyBlockPlaceFlag();
    public static ActorDenyFlag DENY_BLOCK_PLACE_FAKE_PLAYERS = new ActorDenyFlag(FlagKey.of(OWNER, "deny-block-place-fake-players"), DENY_BLOCK_PLACE,
            "TRUE: fake players - machines acting as a player - cannot place blocks in the region. Where it is not set, deny-block-place decides.",
            "fake-player");
    public static DenyExplosionFlag DENY_EXPLOSION = new DenyExplosionFlag();
    public static ActorDenyFlag DENY_EXPLOSION_MOBS = new ActorDenyFlag(FlagKey.of(OWNER, "deny-explosion-mobs"), DENY_EXPLOSION,
            "TRUE: animals and monsters - a creeper - cannot explode in the region. Where it is not set, deny-explosion decides.", "animal", "monster");
    public static DenyInteractFlag DENY_INTERACT = new DenyInteractFlag();
    public static DenyInteractBlocksFlag DENY_INTERACT_BLOCKS = new DenyInteractBlocksFlag(DENY_INTERACT);
    public static DenyInteractEntitiesFlag DENY_INTERACT_ENTITIES = new DenyInteractEntitiesFlag(DENY_INTERACT);
    public static DenyEntryFlag DENY_ENTRY = new DenyEntryFlag();
    public static DenyExitFlag DENY_EXIT = new DenyExitFlag();
    public static DenyTeleportInFlag DENY_TELEPORT_IN = new DenyTeleportInFlag();
    public static DenyTeleportOutFlag DENY_TELEPORT_OUT = new DenyTeleportOutFlag();
    public static DenyMonsterSpawnFlag DENY_MONSTER_SPAWN = new DenyMonsterSpawnFlag();
    public static DenyAnimalSpawnFlag DENY_ANIMAL_SPAWN = new DenyAnimalSpawnFlag();
    public static DenyEntryWithMessageFlag DENY_ENTRY_WITH_MESSAGE = new DenyEntryWithMessageFlag();
    public static GreetingFlag GREETING = new GreetingFlag();
    public static FarewellFlag FAREWELL = new FarewellFlag();
    public static DenyMessageFlag DENY_MESSAGE = new DenyMessageFlag();
    public static TeleportPointFlag TELEPORT_POINT = new TeleportPointFlag();
    public static TrackChangesFlag TRACK_CHANGES = new TrackChangesFlag();

    private CoreFlags() {
    }

    /** Every core flag as the fields hold it now, each parent before the flags that refine it. */
    public static List<RegionFlag<?>> all() {
        return Arrays.asList(DENY_PVP, INVINCIBLE, DENY_DAMAGE_ANIMALS, DENY_BLOCK_BREAK, DENY_BLOCK_BREAK_MOBS, DENY_BLOCK_BREAK_ENVIRONMENT,
                DENY_BLOCK_BREAK_FAKE_PLAYERS, DENY_BLOCK_PLACE, DENY_BLOCK_PLACE_FAKE_PLAYERS, DENY_EXPLOSION, DENY_EXPLOSION_MOBS, DENY_INTERACT,
                DENY_INTERACT_BLOCKS, DENY_INTERACT_ENTITIES, DENY_ENTRY, DENY_EXIT, DENY_TELEPORT_IN, DENY_TELEPORT_OUT,
                DENY_MONSTER_SPAWN, DENY_ANIMAL_SPAWN, DENY_ENTRY_WITH_MESSAGE, GREETING, FAREWELL, DENY_MESSAGE, TELEPORT_POINT,
                TRACK_CHANGES);
    }

    /** Registers every core flag under {@code owner} - EverNifeCore's own plugin data. */
    public static void registerAll(ECPluginData owner) {
        for (RegionFlag<?> flag : all()) {
            FlagRegistry.get().register(owner, flag);
        }
    }
}
