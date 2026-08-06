package br.com.finalcraft.evernifecore.minecraft.itemstack.testkit;

import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemProbe;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemRuntime;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * A server described rather than run: the item engine stands on exactly the runtime a test names,
 * and item metadata is real enough to write to and read back.
 *
 * <pre>{@code
 * try (ItemWorld world = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
 *     ItemStack sword = FCItemFactory.from(Material.DIAMOND_SWORD).displayName("&bDoom").build();
 *     assertEquals(Arrays.asList("type:DIAMOND_SWORD", "amount:1", "name:&bDoom"),
 *             world.getEngine().read(sword).getLines());
 * }
 * }</pre>
 *
 * <p>What cannot be faked is not faked. Item tags come from a library that reaches into the server's
 * own classes, so no headless JVM has them and this rig never claims to - a test that wants the tag
 * asks for a runtime without it and proves the refusal instead.</p>
 */
public final class ItemWorld implements AutoCloseable {

    private static boolean enchantsRegistered = false;

    /** A runtime that answers metadata and resolves enchantments - most of what an item is. */
    public static ItemWorld withMetadata(MCDetailedVersion version) {
        return install(ItemRuntime.of(version, ItemProbe.ITEM_META, ItemProbe.ENCHANT_REGISTRY));
    }

    /** A runtime described exactly, for the tests that are about what it cannot do. */
    public static ItemWorld install(ItemRuntime runtime) {
        return new ItemWorld(runtime);
    }

    private final Server previousServer;
    private final ItemEngine engine;
    private boolean closed = false;

    private ItemWorld(ItemRuntime runtime) {
        registerEnchantments();
        this.previousServer = Bukkit.getServer();
        setBukkitServer(buildServer(runtime));
        this.engine = ItemEngine.install(runtime);
    }

    public ItemEngine getEngine() {
        return engine;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        ItemEngine.uninstall();
        setBukkitServer(previousServer);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The described server
    // -----------------------------------------------------------------------------------------------------------------

    private static Server buildServer(ItemRuntime runtime) {
        ItemFactory itemFactory = (ItemFactory) Proxy.newProxyInstance(
                ItemFactory.class.getClassLoader(), new Class<?>[]{ItemFactory.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "getItemMeta":
                                //no metadata at all is a real answer, and some runtimes give it
                                return runtime.has(ItemProbe.ITEM_META) ? ItemMetaDouble.create() : null;
                            case "isApplicable":
                                return true;
                            case "asMetaFor":
                                return args[0];
                            case "updateMaterial":
                                return args[1];
                            case "equals":
                                if (args.length == 2) {
                                    return args[0] == null ? args[1] == null : args[0].equals(args[1]);
                                }
                                return proxy == args[0];
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "toString":
                                return "ItemFactoryDouble";
                            default:
                                return null;
                        }
                    }
                });

        String bukkitVersion = runtime.getVersion() == null ? "1.21.1-R0.1-SNAPSHOT"
                : runtime.getVersion().getShortVersion().replace("v", "").replace("_", ".")
                + ".0-R0.1-SNAPSHOT";

        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(),
                new Class<?>[]{Server.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "getItemFactory":
                                return itemFactory;
                            case "getBukkitVersion":
                                return bukkitVersion;
                            case "getVersion":
                                return bukkitVersion;
                            case "isPrimaryThread":
                                return true;
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            case "toString":
                                return "ServerDouble";
                            default:
                                return method.getReturnType().isPrimitive() ? Boolean.FALSE : null;
                        }
                    }
                });
    }

    private static void setBukkitServer(Server server) {
        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Bukkit.server could not be replaced; the described server "
                    + "this rig is built on cannot be installed.", e);
        }
    }

    /**
     * Puts a couple of enchantments in the registry, once.
     *
     * <p>Bukkit's own constants are only names off a server - the registry that turns a key back
     * into one is filled by the server implementation, and there is none here.</p>
     */
    private static void registerEnchantments() {
        if (enchantsRegistered) {
            return;
        }
        enchantsRegistered = true;
        Enchantment.registerEnchantment(new NamedEnchantment("sharpness"));
        Enchantment.registerEnchantment(new NamedEnchantment("unbreaking"));
    }

    /** The enchantment reduced to what an item-data line needs of it: a key and a level ceiling. */
    private static final class NamedEnchantment extends Enchantment {

        private final String name;

        private NamedEnchantment(String name) {
            super(NamespacedKey.minecraft(name));
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getMaxLevel() {
            return 5;
        }

        @Override
        public int getStartLevel() {
            return 1;
        }

        @Override
        public EnchantmentTarget getItemTarget() {
            return EnchantmentTarget.ALL;
        }

        @Override
        public boolean isTreasure() {
            return false;
        }

        @Override
        public boolean isCursed() {
            return false;
        }

        @Override
        public boolean conflictsWith(Enchantment other) {
            return false;
        }

        @Override
        public boolean canEnchantItem(ItemStack item) {
            return true;
        }
    }

}
