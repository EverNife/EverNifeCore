package br.com.finalcraft.evernifecore.minecraft.testkit;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * What a server double has to answer {@code getRegistry} with for Bukkit's own classes to load
 * without a server.
 *
 * <p>Bukkit constants became registry lookups, and the lookup happens in a static initializer:
 * naming {@code Enchantment} asks for every vanilla enchantment, and naming {@code InventoryType}
 * reaches {@code MenuType}, which does the same. {@code Registry} in turn refuses to initialize
 * unless the server answers <em>every</em> {@code getRegistry} with something non-null - so a double
 * that answers none of them cannot even mention those classes.</p>
 *
 * <p>Only the enchantment registry has contents, because it is the one the item engine reads
 * through. It mints an entry for whatever key it is asked for: there is no vanilla data here to look
 * anything up in, and a test that names an enchantment wants that name back, not a refusal.</p>
 */
public final class BukkitRegistries {

    private static final Map<NamespacedKey, Enchantment> ENCHANTMENTS = new LinkedHashMap<>();

    private static final Registry<?> ENCHANTMENT_REGISTRY =
            describedRegistry(BukkitRegistries::mintEnchantment, ENCHANTMENTS.values());

    private static final Registry<?> EMPTY_REGISTRY =
            describedRegistry(key -> null, Collections.<Keyed>emptyList());

    private BukkitRegistries() {

    }

    /** The registry a server double hands back for {@code type}. Never {@code null}. */
    public static Registry<?> forType(Class<?> type) {
        return Enchantment.class.equals(type) ? ENCHANTMENT_REGISTRY : EMPTY_REGISTRY;
    }

    private static Enchantment mintEnchantment(NamespacedKey key) {
        Enchantment existing = ENCHANTMENTS.get(key);
        if (existing != null) {
            return existing;
        }
        NamedEnchantment minted = new NamedEnchantment(key);
        ENCHANTMENTS.put(key, minted);
        return minted;
    }

    private static Registry<?> describedRegistry(Function<NamespacedKey, ? extends Keyed> lookup,
                                                 Collection<? extends Keyed> contents) {
        return (Registry<?>) Proxy.newProxyInstance(Registry.class.getClassLoader(),
                new Class<?>[]{Registry.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "get":
                            case "getOrThrow":
                                return lookup.apply((NamespacedKey) args[0]);
                            case "iterator":
                                return new ArrayList<Keyed>(contents).iterator();
                            case "stream":
                                return new ArrayList<Keyed>(contents).stream();
                            case "equals":
                                return proxy == args[0];
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "toString":
                                return "RegistryDouble";
                            default:
                                return null;
                        }
                    }
                });
    }

    /** The enchantment reduced to what an item-data line needs of it: a key and a level ceiling. */
    private static final class NamedEnchantment extends Enchantment {

        private final NamespacedKey key;

        private NamedEnchantment(NamespacedKey key) {
            this.key = key;
        }

        @Override
        public NamespacedKey getKey() {
            return key;
        }

        @Override
        public NamespacedKey getKeyOrThrow() {
            return key;
        }

        @Override
        public NamespacedKey getKeyOrNull() {
            return key;
        }

        @Override
        public boolean isRegistered() {
            return true;
        }

        @Override
        public String getTranslationKey() {
            return "enchantment." + key.getNamespace() + "." + key.getKey();
        }

        @Override
        public String getName() {
            return key.getKey();
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
