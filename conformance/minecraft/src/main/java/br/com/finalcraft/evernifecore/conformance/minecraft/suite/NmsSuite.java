package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.ecnmsutils.NMSUtils;
import br.com.finalcraft.ecnmsutils.entities.NmsEntities;
import br.com.finalcraft.ecnmsutils.items.NmsItems;
import br.com.finalcraft.ecnmsutils.registries.NmsOreDictionary;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * The ECNmsUtils bridge the core embeds, subject by subject, against the classes of this server. The item
 * dictionary is asked for a name in this revision's own vocabulary: the Forge OreDictionary up to 1.12.2, the
 * item tags after.
 */
public final class NmsSuite implements ConformanceSuite {

    @Override
    public String getId() {
        return "nms";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                ConformanceCase.of("unsupported-never-null", () -> Verdict.of(NMSUtils.get() != null && NMSUtils.isSupported(),
                        "revision=" + NMSUtils.revision().map(Enum::name).orElse("none")
                                + " bridge=" + NMSUtils.get().getClass().getSimpleName())),
                ConformanceCase.of("items-registry-name", () -> {
                    String name = items().getRegistryName(new ItemStack(Material.STONE));
                    return Verdict.of("minecraft:stone".equals(name), "stone=" + name);
                }),
                ConformanceCase.of("items-from-registry-name", () -> {
                    ItemStack stone = items().fromRegistryName("minecraft:stone", 1, 0);
                    return Verdict.of(stone.getType() == Material.STONE && stone.getAmount() == 1,
                            "type=" + stone.getType() + " amount=" + stone.getAmount());
                }),
                ConformanceCase.of("items-localized-name", () -> {
                    String name = items().getLocalizedName(new ItemStack(Material.STONE));
                    return Verdict.of(name != null && !name.isEmpty() && !name.contains("null"), "stone='" + name + "'");
                }),
                ConformanceCase.of("items-validate-handle", () -> {
                    ItemStack validated = items().validateHandle(new ItemStack(Material.STONE));
                    Object handle = handleOf(validated);
                    return Verdict.of(validated.getClass().getSimpleName().equals("CraftItemStack") && handle != null,
                            "class=" + validated.getClass().getName() + " handle=" + handle);
                }),
                ConformanceCase.of("items-is-tool", () -> {
                    boolean pickaxe = items().isTool(new ItemStack(Material.DIAMOND_PICKAXE));
                    boolean stone = items().isTool(new ItemStack(Material.STONE));
                    return Verdict.of(pickaxe && !stone, "pickaxe=" + pickaxe + " stone=" + stone);
                }),
                ConformanceCase.of("items-armor-slots", () -> {
                    ItemStack helmet = new ItemStack(Material.IRON_HELMET);
                    ItemStack stone = new ItemStack(Material.STONE);
                    boolean helmetIsHelmet = items().isHelmet(helmet);
                    boolean helmetIsBoots = items().isBoots(helmet);
                    boolean stoneIsAny = items().isArmor(stone) || items().isHelmet(stone) || items().isChestplate(stone)
                            || items().isLeggings(stone) || items().isBoots(stone);
                    return Verdict.of(helmetIsHelmet && !helmetIsBoots && !stoneIsAny,
                            "helmet.isHelmet=" + helmetIsHelmet + " helmet.isBoots=" + helmetIsBoots + " stone.anyArmor=" + stoneIsAny);
                }),
                ConformanceCase.of("entities-registry-name", () -> withPig(pig -> {
                    String name = entities().getRegistryName(pig);
                    return Verdict.of(name != null && name.toLowerCase().contains("pig"), "pig=" + name);
                })),
                ConformanceCase.of("entities-roundtrip", () -> withPig(pig -> {
                    Entity back = entities().asBukkit(entities().asMinecraft(pig));
                    return Verdict.of(back != null && back.getUniqueId().equals(pig.getUniqueId()),
                            "pig=" + pig.getUniqueId() + " back=" + (back == null ? null : back.getUniqueId()));
                })),
                ConformanceCase.of("worlds-roundtrip", () -> {
                    World world = Bukkit.getWorlds().get(0);
                    World back = NMSUtils.get().worlds().asBukkit(NMSUtils.get().worlds().asMinecraft(world));
                    return Verdict.of(back != null && back.getName().equals(world.getName()),
                            "world=" + world.getName() + " back=" + (back == null ? null : back.getName()));
                }),
                ConformanceCase.of("entities-fake-player", () -> PlayerCases.onPlayer(ctx, player -> {
                    boolean fake = entities().isFakePlayer(player);
                    return Verdict.of(!fake, player.getName() + ".isFakePlayer=" + fake);
                })),
                ConformanceCase.of("registries-ore-dictionary", NmsSuite::oreDictionary));
    }

    private static Verdict oreDictionary() {
        boolean tags = MCVersion.isHigherEquals(MCVersion.v1_13);
        String oreName = tags ? "minecraft:logs" : "logWood";
        ItemStack oakLog = new ItemStack(Material.matchMaterial(tags ? "OAK_LOG" : "LOG"));
        NmsOreDictionary dictionary = NMSUtils.get().registries().oreDictionary();
        int items = dictionary.getOreItemStacks(oreName).size();
        List<String> onOakLog = dictionary.getOreNamesFrom(oakLog);
        return Verdict.of(dictionary.hasOreName(oreName) && items >= 1 && onOakLog.contains(oreName),
                oreName + ".items=" + items + " oakLog.names=" + onOakLog + " dictionary=" + dictionary.getClass().getSimpleName());
    }

    private static NmsItems items() {
        return NMSUtils.get().items();
    }

    private static NmsEntities entities() {
        return NMSUtils.get().entities();
    }

    private static Object handleOf(ItemStack craftItemStack) throws ReflectiveOperationException {
        Field handle = craftItemStack.getClass().getDeclaredField("handle");
        handle.setAccessible(true);
        return handle.get(craftItemStack);
    }

    private interface OnEntity {
        Verdict run(Entity entity) throws Exception;
    }

    private static Verdict withPig(OnEntity body) throws Exception {
        World world = Bukkit.getWorlds().get(0);
        Entity pig = world.spawnEntity(world.getSpawnLocation(), EntityType.PIG);
        try {
            return body.run(pig);
        } finally {
            pig.remove();
        }
    }
}
