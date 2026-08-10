package br.com.finalcraft.evernifecore.minecraft.itemstack.testkit;

import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemProbe;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemRuntime;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitRegistries;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.testkit.Doubles;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.inventory.ItemFactory;

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
 *
 * <p>The main thread is whichever thread installed this rig, and no other. Answering everyone yes
 * would be the same rig quietly disarming every main-thread guard in the code under test, for as long
 * as it is installed - the answer follows the thread that asks.</p>
 */
public final class ItemWorld implements AutoCloseable {

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
        this.previousServer = Bukkit.getServer();
        setBukkitServer(buildServer(runtime, Thread.currentThread()));
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

    private static Server buildServer(ItemRuntime runtime, Thread mainThread) {
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
                                return Doubles.neutral(method.getReturnType());
                        }
                    }
                });

        String bukkitVersion = runtime.getVersion() == null ? "1.21.1-R0.1-SNAPSHOT"
                : runtime.getVersion().getReleaseFamily() + ".0-R0.1-SNAPSHOT";

        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(),
                new Class<?>[]{Server.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "getItemFactory":
                                return itemFactory;
                            case "getRegistry":
                                return BukkitRegistries.forType((Class<?>) args[0]);
                            case "getBukkitVersion":
                                return bukkitVersion;
                            case "getVersion":
                                return bukkitVersion;
                            case "isPrimaryThread":
                                return Thread.currentThread() == mainThread;
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            case "toString":
                                return "ServerDouble";
                            default:
                                return Doubles.neutral(method.getReturnType());
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

}
