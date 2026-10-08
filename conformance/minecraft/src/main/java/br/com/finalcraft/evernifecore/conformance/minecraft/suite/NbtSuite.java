package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.NbtDoor;
import br.com.finalcraft.evernifecore.minecraft.util.FCItemUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCNBTUtil;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Every {@code FCNBTUtil} method against this server, and a write/read pair that crosses a restart. A method a
 * version cannot serve has to refuse there - each one names the version it starts at in the table below, and a
 * quiet success below it is a failure too.
 */
public final class NbtSuite implements ConformanceSuite {

    // Where FCNBTUtil starts to work; below each, a refusal is the expected answer.
    /** Persistent data of entities and tile entities. */
    private static final MCDetailedVersion PDC_SINCE = MCDetailedVersion.v1_14_R1;
    /** Persistent data of a chunk, and of a block through it. */
    private static final MCDetailedVersion CHUNK_PDC_SINCE = MCDetailedVersion.v1_16_R3;
    /** Item components. */
    private static final MCDetailedVersion COMPONENTS_SINCE = MCDetailedVersion.v1_20_R4;

    private static final String STATE_FILE = "persist.dat";

    @Override
    public String getId() {
        return "nbt";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        File dataFolder = ctx.getDataFolder();
        return Arrays.asList(
                ConformanceCase.of("text", NbtSuite::text),
                PlayerCases.of("item-plain", ctx, player -> itemPlain()),
                PlayerCases.of("item-live", ctx, NbtSuite::itemLive),
                PlayerCases.of("item-identifier", ctx, player -> itemIdentifier()),
                PlayerCases.of("entity", ctx, player -> withPig(player, NbtSuite::entity)),
                PlayerCases.of("entity-player", ctx, player -> {
                    String name = FCNBTUtil.readEntity(player, nbt -> nbt.hasTag("Pos") ? "has Pos" : "no Pos");
                    return Verdict.of("has Pos".equals(name), name);
                }),
                PlayerCases.of("entity-data", ctx, player -> PlayerCases.since(PDC_SINCE, () -> withPig(player, pig -> {
                    FCNBTUtil.modifyEntityData(pig, nbt -> nbt.setString("k", "v"));
                    String read = FCNBTUtil.readEntityData(pig, nbt -> nbt.getString("k"));
                    return Verdict.of("v".equals(read), "k=" + read);
                }))),
                PlayerCases.of("tile", ctx, player -> withChest(player, NbtSuite::tile)),
                PlayerCases.of("tile-data", ctx, player -> PlayerCases.since(PDC_SINCE, () -> withChest(player, block -> {
                    FCNBTUtil.modifyTileEntityData(block.getState(), nbt -> nbt.setString("k", "v"));
                    String read = FCNBTUtil.readTileEntityData(block.getState(), nbt -> nbt.getString("k"));
                    return Verdict.of("v".equals(read), "k=" + read);
                }))),
                PlayerCases.of("block-data", ctx, player -> PlayerCases.since(CHUNK_PDC_SINCE, () -> blockData(above(player)))),
                PlayerCases.of("chunk-data", ctx, player -> PlayerCases.since(CHUNK_PDC_SINCE, () -> {
                    Block block = above(player);
                    FCNBTUtil.modifyChunkData(block.getChunk(), nbt -> nbt.setString("k", "v"));
                    String read = FCNBTUtil.readChunkData(block.getChunk(), nbt -> nbt.getString("k"));
                    FCNBTUtil.modifyChunkData(block.getChunk(), nbt -> nbt.removeKey("k"));
                    return Verdict.of("v".equals(read), "k=" + read);
                })),
                ConformanceCase.of("file", () -> file(dataFolder)));
    }

    private static Verdict text() {
        ReadWriteNBT empty = FCNBTUtil.createEmpty();
        boolean wasEmpty = FCNBTUtil.isEmpty(empty) && FCNBTUtil.isEmpty(null);
        empty.setInteger("a", 1);
        ReadWriteNBT back = FCNBTUtil.parse(empty.toString());
        return Verdict.of(wasEmpty && !FCNBTUtil.isEmpty(empty) && back.getInteger("a") == 1, "empty=" + wasEmpty + " parsed=" + back);
    }

    private static Verdict itemPlain() {
        ItemStack plain = new ItemStack(Material.STONE);
        ItemStack carried = FCNBTUtil.modifyItem(plain, nbt -> nbt.setString("k", "v"));
        String onPlain = FCNBTUtil.readItem(plain, nbt -> nbt.hasTag("k") ? nbt.getString("k") : "absent");
        ReadableNBT view = FCNBTUtil.readItem(carried);
        return Verdict.of("v".equals(view.getString("k")) && "absent".equals(onPlain),
                "returned.k=" + view.getString("k") + " plain.k=" + onPlain + " returned.class=" + carried.getClass().getSimpleName());
    }

    private static Verdict itemLive(Player player) {
        int slot = player.getInventory().firstEmpty();
        player.getInventory().setItem(slot, new ItemStack(Material.STONE));
        ItemStack live = player.getInventory().getItem(slot);
        ItemStack returned = FCNBTUtil.modifyItem(live, nbt -> nbt.setString("k", "v"));
        String fresh = FCNBTUtil.readItem(player.getInventory().getItem(slot), nbt -> nbt.getString("k"));
        return Verdict.of(returned == live && "v".equals(fresh), "returned==live=" + (returned == live) + " slot.k=" + fresh);
    }

    private static Verdict itemIdentifier() {
        ItemStack fromId = FCItemUtils.fromMinecraftIdentifier("minecraft:stone 1 0 {ecctag:7}");
        int value = FCNBTUtil.readItem(fromId, nbt -> nbt.hasTag("ecctag") ? nbt.getInteger("ecctag") : -1);
        String identifier = FCItemUtils.getMinecraftIdentifier(fromId);
        return Verdict.of(value == 7 && identifier.contains("ecctag"), "ecctag=" + value + " identifier=" + identifier);
    }

    private static Verdict entity(Entity pig) {
        int keys = FCNBTUtil.readEntity(pig, nbt -> nbt.getKeys().size());
        FCNBTUtil.modifyEntity(pig, nbt -> nbt.setShort("Fire", (short) 200));
        int fire = pig.getFireTicks();
        short read = FCNBTUtil.readEntity(pig, nbt -> nbt.getShort("Fire"));
        return Verdict.of(keys > 0 && fire == 200 && read == 200, "keys=" + keys + " fireTicks=" + fire + " Fire=" + read
                + " valid=" + pig.isValid());
    }

    private static Verdict tile(Block block) {
        BlockState tile = block.getState();
        String id = FCNBTUtil.readTileEntity(tile, nbt -> nbt.getString("id"));
        String name = MCVersion.isHigherEquals(MCVersion.v1_13) ? "{\"text\":\"probe\"}" : "probe";
        FCNBTUtil.modifyTileEntity(tile, nbt -> nbt.setString("CustomName", name));
        String read = FCNBTUtil.readTileEntity(block.getState(), nbt -> nbt.getString("CustomName"));
        return Verdict.of(!id.isEmpty() && read != null && read.contains("probe"), "id=" + id + " CustomName=" + read);
    }

    private static Verdict blockData(Block block) {
        FCNBTUtil.modifyBlockData(block, nbt -> nbt.setString("k", "v"));
        String read = FCNBTUtil.readBlockData(block, nbt -> nbt.getString("k"));
        FCNBTUtil.modifyBlockData(block, nbt -> nbt.removeKey("k"));
        boolean cleared = FCNBTUtil.readBlockData(block, nbt -> nbt.getKeys().isEmpty());
        return Verdict.of("v".equals(read) && cleared, "k=" + read + " clearedAfterRemove=" + cleared);
    }

    private static Verdict file(File dataFolder) {
        File file = new File(dataFolder, "probe.dat");
        FCNBTUtil.writeFile(file, FCNBTUtil.parse("{a:1,nested:{b:\"x\"}}"));
        ReadWriteNBT read = FCNBTUtil.readFile(file);
        boolean missingIsEmpty = FCNBTUtil.isEmpty(FCNBTUtil.readFile(new File(dataFolder, "missing.dat")));
        return Verdict.of(read.getInteger("a") == 1 && "x".equals(read.getCompound("nested").getString("b")) && missingIsEmpty,
                "read=" + read + " missingIsEmpty=" + missingIsEmpty);
    }

    private interface OnEntity {
        Verdict run(Entity entity) throws Exception;
    }

    private interface OnBlock {
        Verdict run(Block block) throws Exception;
    }

    private static Verdict withPig(Player player, OnEntity body) throws Exception {
        Location spot = player.getLocation().clone().add(2, 0, 0);
        Entity pig = spot.getWorld().spawnEntity(spot, EntityType.PIG);
        try {
            return body.run(pig);
        } finally {
            pig.remove();
        }
    }

    private static Verdict withChest(Player player, OnBlock body) throws Exception {
        Block block = above(player);
        Material before = block.getType();
        block.setType(Material.CHEST);
        try {
            return body.run(block);
        } finally {
            block.setType(before);
        }
    }

    private static Block above(Player player) {
        return player.getLocation().getBlock().getRelative(0, 3, 0);
    }

    // ------------------------------------------------------------------
    //  Across a restart: write onto objects that outlive it - a solid block, a chest, a minecart (which never
    //  despawns), the player and two items in their inventory - and read each one back after.
    // ------------------------------------------------------------------

    @Override
    public List<ConformanceCase> getWriteCases(ConformanceContext ctx) {
        Persisted run = new Persisted();
        return Arrays.asList(
                PlayerCases.of("write-block-data-stone", ctx, player -> PlayerCases.since(CHUNK_PDC_SINCE, () -> {
                    Block stone = run.prepare(player).stone;
                    FCNBTUtil.modifyBlockData(stone, nbt -> nbt.setString("k", "stone"));
                    String read = FCNBTUtil.readBlockData(stone, nbt -> nbt.getString("k"));
                    return Verdict.of("stone".equals(read), "type=" + stone.getType() + " k=" + read);
                })),
                PlayerCases.of("write-block-data-chest", ctx, player -> PlayerCases.since(CHUNK_PDC_SINCE, () -> {
                    Block chest = run.prepare(player).chest;
                    FCNBTUtil.modifyBlockData(chest, nbt -> nbt.setString("k", "chest"));
                    String read = FCNBTUtil.readBlockData(chest, nbt -> nbt.getString("k"));
                    return Verdict.of("chest".equals(read), "type=" + chest.getType() + " k=" + read);
                })),
                PlayerCases.of("write-chunk-data", ctx, player -> PlayerCases.since(CHUNK_PDC_SINCE, () -> {
                    Block stone = run.prepare(player).stone;
                    FCNBTUtil.modifyChunkData(stone.getChunk(), nbt -> nbt.setString("k", "chunk"));
                    String read = FCNBTUtil.readChunkData(stone.getChunk(), nbt -> nbt.getString("k"));
                    return Verdict.of("chunk".equals(read), "k=" + read);
                })),
                PlayerCases.of("write-tile", ctx, player -> {
                    Block chest = run.prepare(player).chest;
                    FCNBTUtil.modifyTileEntity(chest.getState(), nbt -> nbt.setString("CustomName", customName()));
                    String read = FCNBTUtil.readTileEntity(chest.getState(), nbt -> nbt.getString("CustomName"));
                    return Verdict.of(read != null && read.contains("ecpersist"), "CustomName=" + read);
                }),
                PlayerCases.of("write-tile-data", ctx, player -> PlayerCases.since(PDC_SINCE, () -> {
                    Block chest = run.prepare(player).chest;
                    FCNBTUtil.modifyTileEntityData(chest.getState(), nbt -> nbt.setString("k", "tile"));
                    String read = FCNBTUtil.readTileEntityData(chest.getState(), nbt -> nbt.getString("k"));
                    return Verdict.of("tile".equals(read), "k=" + read);
                })),
                PlayerCases.of("write-entity", ctx, player -> {
                    Entity cart = run.prepare(player).cart;
                    FCNBTUtil.modifyEntity(cart, nbt -> nbt.setString("CustomName", customName()));
                    String read = FCNBTUtil.readEntity(cart, nbt -> nbt.getString("CustomName"));
                    return Verdict.of(read.contains("ecpersist") && cart.isValid(), "CustomName=" + read + " valid=" + cart.isValid()
                            + " uuid=" + cart.getUniqueId());
                }),
                PlayerCases.of("write-entity-data", ctx, player -> PlayerCases.since(PDC_SINCE, () -> {
                    Entity cart = run.prepare(player).cart;
                    FCNBTUtil.modifyEntityData(cart, nbt -> nbt.setString("k", "cart"));
                    String read = FCNBTUtil.readEntityData(cart, nbt -> nbt.getString("k"));
                    return Verdict.of("cart".equals(read), "k=" + read);
                })),
                PlayerCases.of("write-entity-player", ctx, player -> {
                    int levelBefore = player.getLevel();
                    FCNBTUtil.modifyEntity(player, nbt -> nbt.setInteger("XpLevel", 7));
                    int read = FCNBTUtil.readEntity(player, nbt -> nbt.getInteger("XpLevel"));
                    return Verdict.of(player.getLevel() == 7 && read == 7, "levelBefore=" + levelBefore + " getLevel=" + player.getLevel()
                            + " XpLevel=" + read + " online=" + player.isOnline());
                }),
                PlayerCases.of("write-item", ctx, player -> {
                    int slot = player.getInventory().firstEmpty();
                    player.getInventory().setItem(slot, new ItemStack(Material.STONE));
                    FCNBTUtil.modifyItem(player.getInventory().getItem(slot), nbt -> nbt.setString("k", "item"));
                    String read = FCNBTUtil.readItem(player.getInventory().getItem(slot), nbt -> nbt.getString("k"));
                    run.prepare(player).state.setInteger("itemSlot", slot);
                    return Verdict.of("item".equals(read), "slot=" + slot + " k=" + read);
                }),
                PlayerCases.of("write-components", ctx, player -> PlayerCases.since(COMPONENTS_SINCE, () -> {
                    ItemStack built = ItemEngine.get().transform(new ItemStack(Material.STONE),
                            Collections.singletonList("components:{\"minecraft:max_stack_size\":1}"));
                    int slot = player.getInventory().firstEmpty();
                    player.getInventory().setItem(slot, built);
                    ItemStack held = player.getInventory().getItem(slot);
                    String read = NbtDoor.components().read(held, nbt -> nbt.toString());
                    run.prepare(player).state.setInteger("componentSlot", slot);
                    return Verdict.of(held.getMaxStackSize() == 1 && read.contains("max_stack_size"),
                            "slot=" + slot + " maxStackSize=" + held.getMaxStackSize() + " components=" + read);
                })),
                PlayerCases.of("write-state", ctx, player -> {
                    ReadWriteNBT state = run.prepare(player).state;
                    FCNBTUtil.writeFile(new File(ctx.getDataFolder(), STATE_FILE), state);
                    return Verdict.pass("state=" + state);
                }));
    }

    @Override
    public List<ConformanceCase> getReadCases(ConformanceContext ctx) {
        Restored was = new Restored(ctx.getDataFolder());
        return Arrays.asList(
                PlayerCases.of("read-block-data-stone", ctx, player -> PlayerCases.since(CHUNK_PDC_SINCE, () -> {
                    Block stone = was.load().block("stone");
                    String read = FCNBTUtil.readBlockData(stone, nbt -> nbt.getString("k"));
                    return Verdict.of("stone".equals(read) && stone.getType() == Material.STONE, "type=" + stone.getType() + " k=" + read);
                })),
                PlayerCases.of("read-block-data-chest", ctx, player -> PlayerCases.since(CHUNK_PDC_SINCE, () -> {
                    Block chest = was.load().block("chest");
                    String read = FCNBTUtil.readBlockData(chest, nbt -> nbt.getString("k"));
                    return Verdict.of("chest".equals(read), "type=" + chest.getType() + " k=" + read);
                })),
                PlayerCases.of("read-chunk-data", ctx, player -> PlayerCases.since(CHUNK_PDC_SINCE, () -> {
                    String read = FCNBTUtil.readChunkData(was.load().block("stone").getChunk(), nbt -> nbt.getString("k"));
                    return Verdict.of("chunk".equals(read), "k=" + read);
                })),
                PlayerCases.of("read-tile", ctx, player -> {
                    Block chest = was.load().block("chest");
                    String read = FCNBTUtil.readTileEntity(chest.getState(), nbt -> nbt.getString("CustomName"));
                    return Verdict.of(read != null && read.contains("ecpersist"), "type=" + chest.getType() + " CustomName=" + read);
                }),
                PlayerCases.of("read-tile-data", ctx, player -> PlayerCases.since(PDC_SINCE, () -> {
                    String read = FCNBTUtil.readTileEntityData(was.load().block("chest").getState(), nbt -> nbt.getString("k"));
                    return Verdict.of("tile".equals(read), "k=" + read);
                })),
                //never touched by FCNBTUtil: tells a lost write apart from a world that drops the entity on its own
                PlayerCases.of("read-entity-control", ctx, player -> {
                    UUID control = UUID.fromString(was.load().state.getString("control"));
                    boolean found = was.entity(control) != null;
                    return Verdict.of(found, "untouched minecart " + control + " found=" + found);
                }),
                PlayerCases.of("read-entity", ctx, player -> {
                    UUID cartId = UUID.fromString(was.load().state.getString("cart"));
                    Entity cart = was.entity(cartId);
                    if (cart == null) {
                        return Verdict.fail("minecart " + cartId + " not among the loaded entities:" + was.loadedEntities());
                    }
                    String read = FCNBTUtil.readEntity(cart, nbt -> nbt.getString("CustomName"));
                    return Verdict.of(read.contains("ecpersist"), "CustomName=" + read);
                }),
                PlayerCases.of("read-entity-data", ctx, player -> PlayerCases.since(PDC_SINCE, () -> {
                    Entity cart = was.entity(UUID.fromString(was.load().state.getString("cart")));
                    String read = cart == null ? "no minecart" : FCNBTUtil.readEntityData(cart, nbt -> nbt.getString("k"));
                    return Verdict.of("cart".equals(read), "k=" + read);
                })),
                PlayerCases.of("read-entity-player", ctx, player -> {
                    int read = FCNBTUtil.readEntity(player, nbt -> nbt.getInteger("XpLevel"));
                    return Verdict.of(player.getLevel() == 7 && read == 7, "getLevel=" + player.getLevel() + " XpLevel=" + read);
                }),
                PlayerCases.of("read-item", ctx, player -> {
                    int slot = was.load().state.getInteger("itemSlot");
                    ItemStack held = player.getInventory().getItem(slot);
                    String read = held == null ? "empty slot" : FCNBTUtil.readItem(held, nbt -> nbt.getString("k"));
                    return Verdict.of("item".equals(read), "slot=" + slot + " k=" + read);
                }),
                PlayerCases.of("read-components", ctx, player -> PlayerCases.since(COMPONENTS_SINCE, () -> {
                    ItemStack held = player.getInventory().getItem(was.load().state.getInteger("componentSlot"));
                    String read = held == null ? "empty slot" : NbtDoor.components().read(held, nbt -> nbt.toString());
                    return Verdict.of(held != null && held.getMaxStackSize() == 1 && read.contains("max_stack_size"),
                            "maxStackSize=" + (held == null ? -1 : held.getMaxStackSize()) + " components=" + read);
                })));
    }

    private static String customName() {
        return MCVersion.isHigherEquals(MCVersion.v1_13) ? "{\"text\":\"ecpersist\"}" : "ecpersist";
    }

    /** What the write half places in the world, laid out once by whichever case needs it first. */
    private static final class Persisted {
        private ReadWriteNBT state;
        private Block stone;
        private Block chest;
        private Entity cart;

        Persisted prepare(Player player) {
            if (state != null) return this;
            state = FCNBTUtil.createEmpty();
            Block base = player.getLocation().getBlock();
            stone = base.getRelative(3, 0, 0);
            chest = base.getRelative(3, 0, 2);
            stone.setType(Material.STONE);
            chest.setType(Material.CHEST);
            cart = base.getWorld().spawnEntity(base.getRelative(-3, 0, 0).getLocation().add(0.5, 0, 0.5), EntityType.MINECART);
            Entity control = base.getWorld().spawnEntity(base.getRelative(-3, 0, 2).getLocation().add(0.5, 0, 0.5), EntityType.MINECART);
            state.setString("world", base.getWorld().getName());
            state.setIntArray("stone", new int[]{stone.getX(), stone.getY(), stone.getZ()});
            state.setIntArray("chest", new int[]{chest.getX(), chest.getY(), chest.getZ()});
            state.setString("cart", cart.getUniqueId().toString());
            state.setString("control", control.getUniqueId().toString());
            return this;
        }
    }

    /** {@code persist.dat} as the write half left it; every read case fails naming the write when it is missing. */
    private static final class Restored {
        private final File dataFolder;
        private ReadWriteNBT state;
        private World world;

        Restored(File dataFolder) {
            this.dataFolder = dataFolder;
        }

        Restored load() {
            if (state != null) return this;
            ReadWriteNBT read = FCNBTUtil.readFile(new File(dataFolder, STATE_FILE));
            if (FCNBTUtil.isEmpty(read)) {
                throw new IllegalStateException("no " + STATE_FILE + " - run '/ecc write nbt --player <name>' before the restart");
            }
            world = Bukkit.getWorld(read.getString("world"));
            state = read;
            return this;
        }

        Block block(String key) {
            int[] xyz = state.getIntArray(key);
            return world.getBlockAt(xyz[0], xyz[1], xyz[2]);
        }

        Entity entity(UUID id) {
            for (Entity entity : load().world.getEntities()) {
                if (entity.getUniqueId().equals(id)) return entity;
            }
            return null;
        }

        String loadedEntities() {
            StringBuilder loaded = new StringBuilder();
            for (Entity entity : world.getEntities()) {
                loaded.append(' ').append(entity.getType()).append('@').append(entity.getLocation().getBlockX()).append(',')
                        .append(entity.getLocation().getBlockZ()).append('/').append(entity.getUniqueId());
            }
            return loaded.toString();
        }
    }
}
