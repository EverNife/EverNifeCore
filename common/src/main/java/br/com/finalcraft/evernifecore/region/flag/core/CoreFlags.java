package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.region.actor.ECEntityType;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.context.VehicleContext;

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
    public static ActorDenyFlag DENY_BLOCK_PLACE_ENVIRONMENT = new ActorDenyFlag(FlagKey.of(OWNER, "deny-block-place-environment"), DENY_BLOCK_PLACE,
            "TRUE: the environment - lava lighting fire, snow, ice - cannot place blocks in the region. Where it is not set, deny-block-place"
                    + " decides.", "environment");
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
    public static DenySpawnFlag DENY_SPAWN = new DenySpawnFlag();
    public static DenySpawnFlag DENY_SPAWN_MONSTER = new DenySpawnFlag("deny-spawn-monster", DENY_SPAWN, ECEntityType.MONSTER,
            "TRUE: monsters do not spawn in the region. Where it is not set, deny-spawn decides.");
    public static DenySpawnFlag DENY_SPAWN_ANIMAL = new DenySpawnFlag("deny-spawn-animal", DENY_SPAWN, ECEntityType.ANIMAL,
            "TRUE: animals do not spawn in the region. Where it is not set, deny-spawn decides.");
    public static DenyEntryWithMessageFlag DENY_ENTRY_WITH_MESSAGE = new DenyEntryWithMessageFlag();
    public static GreetingFlag GREETING = new GreetingFlag();
    public static FarewellFlag FAREWELL = new FarewellFlag();
    public static DenyMessageFlag DENY_MESSAGE = new DenyMessageFlag();
    public static TeleportPointFlag TELEPORT_POINT = new TeleportPointFlag();
    public static TrackChangesFlag TRACK_CHANGES = new TrackChangesFlag();
    public static SpawnPointFlag SPAWN_POINT = new SpawnPointFlag();
    public static DenyItemFlag DENY_ITEM_PICKUP = new DenyItemFlag("deny-item-pickup", EventSource.ITEM_PICKUP,
            "TRUE: items cannot be picked up in the region; only/except in the settings narrow it to some items.");
    public static DenyItemFlag DENY_ITEM_DROP = new DenyItemFlag("deny-item-drop", EventSource.ITEM_DROP,
            "TRUE: items cannot be dropped in the region; only/except in the settings narrow it to some items.");
    public static DenyVehicleFlag DENY_VEHICLE = new DenyVehicleFlag();
    public static DenyVehicleFlag DENY_VEHICLE_PLACE = new DenyVehicleFlag("deny-vehicle-place", DENY_VEHICLE, VehicleContext.Action.PLACE,
            "TRUE: vehicles cannot be placed in the region. Where it is not set, deny-vehicle decides.");
    public static DenyVehicleFlag DENY_VEHICLE_BREAK = new DenyVehicleFlag("deny-vehicle-break", DENY_VEHICLE, VehicleContext.Action.BREAK,
            "TRUE: vehicles cannot be broken in the region. Where it is not set, deny-vehicle decides.");
    public static DenyVehicleFlag DENY_VEHICLE_ENTER = new DenyVehicleFlag("deny-vehicle-enter", DENY_VEHICLE, VehicleContext.Action.ENTER,
            "TRUE: vehicles cannot be entered in the region. Where it is not set, deny-vehicle decides.");
    public static DenyFireSpreadFlag DENY_FIRE_SPREAD = new DenyFireSpreadFlag();
    public static DenyFluidFlowFlag DENY_FLUID_FLOW = new DenyFluidFlowFlag();
    public static DenyDamageFromMobsFlag DENY_DAMAGE_FROM_MOBS = new DenyDamageFromMobsFlag();
    public static TimeLockFlag TIME_LOCK = new TimeLockFlag();
    public static WeatherLockFlag WEATHER_LOCK = new WeatherLockFlag();
    public static GameModeFlag GAME_MODE = new GameModeFlag();
    public static FlightFlag ALLOW_FLY = FlightFlag.allowFly();
    public static FlightFlag FORCE_FLY = FlightFlag.forceFly();
    public static PotionEffectsFlag POTION_EFFECTS = new PotionEffectsFlag();
    public static CommandFlag COMMAND_ON_ENTER = CommandFlag.onEnter();
    public static CommandFlag COMMAND_ON_EXIT = CommandFlag.onExit();
    public static AmountEveryFlag HEAL = new AmountEveryFlag("heal", AmountEveryFlag.Effect.HEAL,
            "Heals whoever stands in the region by the amount once per interval (2 every 5s), up to full health.");
    public static AmountEveryFlag FEED = new AmountEveryFlag("feed", AmountEveryFlag.Effect.FEED,
            "Feeds whoever stands in the region by the amount once per interval (2 every 5s), up to a full bar.");
    public static AmountEveryFlag DAMAGE_OVER_TIME = new AmountEveryFlag("damage-over-time", AmountEveryFlag.Effect.DAMAGE,
            "Damages whoever stands in the region by the amount once per interval (2 every 5s); armor counts unless ignoreArmor"
                    + " is set in the settings.");
    public static AmountEveryFlag HUNGER_OVER_TIME = new AmountEveryFlag("hunger-over-time", AmountEveryFlag.Effect.HUNGER,
            "Takes the amount of food from whoever stands in the region once per interval (2 every 5s).");

    private CoreFlags() {
    }

    /** Every core flag as the fields hold it now, each parent before the flags that refine it. */
    public static List<RegionFlag<?>> all() {
        return Arrays.asList(DENY_PVP, INVINCIBLE, DENY_DAMAGE_ANIMALS, DENY_DAMAGE_FROM_MOBS, DENY_BLOCK_BREAK, DENY_BLOCK_BREAK_MOBS,
                DENY_BLOCK_BREAK_ENVIRONMENT, DENY_BLOCK_BREAK_FAKE_PLAYERS, DENY_BLOCK_PLACE, DENY_BLOCK_PLACE_ENVIRONMENT,
                DENY_BLOCK_PLACE_FAKE_PLAYERS, DENY_FIRE_SPREAD, DENY_FLUID_FLOW, DENY_EXPLOSION, DENY_EXPLOSION_MOBS, DENY_INTERACT,
                DENY_INTERACT_BLOCKS, DENY_INTERACT_ENTITIES, DENY_ENTRY, DENY_EXIT, DENY_TELEPORT_IN, DENY_TELEPORT_OUT,
                DENY_SPAWN, DENY_SPAWN_MONSTER, DENY_SPAWN_ANIMAL, DENY_ITEM_PICKUP, DENY_ITEM_DROP, DENY_VEHICLE, DENY_VEHICLE_PLACE,
                DENY_VEHICLE_BREAK, DENY_VEHICLE_ENTER, DENY_ENTRY_WITH_MESSAGE, GREETING, FAREWELL, DENY_MESSAGE, TELEPORT_POINT,
                SPAWN_POINT, TRACK_CHANGES, TIME_LOCK, WEATHER_LOCK, GAME_MODE, ALLOW_FLY, FORCE_FLY, POTION_EFFECTS, COMMAND_ON_ENTER,
                COMMAND_ON_EXIT, HEAL, FEED, DAMAGE_OVER_TIME, HUNGER_OVER_TIME);
    }

    /** Registers every core flag under {@code owner} - EverNifeCore's own plugin data. */
    public static void registerAll(ECPluginData owner) {
        for (RegionFlag<?> flag : all()) {
            FlagRegistry.get().register(owner, flag);
        }
    }
}
