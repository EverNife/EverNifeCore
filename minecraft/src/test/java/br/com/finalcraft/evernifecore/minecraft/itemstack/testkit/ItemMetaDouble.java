package br.com.finalcraft.evernifecore.minecraft.itemstack.testkit;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Item metadata that actually remembers what was written to it, with no server behind it.
 *
 * <p>The gui rig's factory answers no metadata at all, which is the right double for a screen and
 * the wrong one for an item: every part that writes a name, a lore or a flag becomes untestable,
 * and that is precisely how the item path came to be proved only on a running server.</p>
 *
 * <p>Two behaviours are copied deliberately because code depends on them. Reading metadata off a
 * stack answers a copy, so a change is only kept by writing it back - which is what makes the
 * order edits are applied in observable. And asking for a custom model data that was never set
 * throws, the way Bukkit does: reading it as a sentinel is the defect this exists to catch.</p>
 */
public final class ItemMetaDouble implements InvocationHandler {

    public static ItemMeta create() {
        return new ItemMetaDouble().asMeta();
    }

    private String displayName;
    private List<String> lore;
    private final Set<ItemFlag> flags = EnumSet.noneOf(ItemFlag.class);
    private final Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
    private Integer customModelData;
    private boolean unbreakable;
    private int damage;

    private ItemMeta asMeta() {
        return (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(),
                new Class<?>[]{ItemMeta.class, Damageable.class}, this);
    }

    private ItemMetaDouble copy() {
        ItemMetaDouble copy = new ItemMetaDouble();
        copy.displayName = this.displayName;
        copy.lore = this.lore == null ? null : new ArrayList<>(this.lore);
        copy.flags.addAll(this.flags);
        copy.enchants.putAll(this.enchants);
        copy.customModelData = this.customModelData;
        copy.unbreakable = this.unbreakable;
        copy.damage = this.damage;
        return copy;
    }

    /** Two metas are the same when they say the same things, which is what item similarity reads. */
    private boolean sameAs(Object other) {
        if (!(other instanceof ItemMeta) || !Proxy.isProxyClass(other.getClass())) {
            return false;
        }
        Object handler = Proxy.getInvocationHandler(other);
        if (!(handler instanceof ItemMetaDouble)) {
            return false;
        }
        ItemMetaDouble that = (ItemMetaDouble) handler;
        return equalOrBothNull(displayName, that.displayName)
                && equalOrBothNull(lore, that.lore)
                && flags.equals(that.flags)
                && enchants.equals(that.enchants)
                && equalOrBothNull(customModelData, that.customModelData)
                && unbreakable == that.unbreakable
                && damage == that.damage;
    }

    private static boolean equalOrBothNull(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] arguments) {
        Object[] args = arguments == null ? new Object[0] : arguments;
        switch (method.getName()) {
            case "setDisplayName":
                displayName = (String) args[0];
                return null;
            case "getDisplayName":
                return displayName;
            case "hasDisplayName":
                return displayName != null;

            case "setLore":
                lore = args[0] == null ? null : new ArrayList<>((List<String>) args[0]);
                return null;
            case "getLore":
                return lore == null ? null : new ArrayList<>(lore);
            case "hasLore":
                return lore != null && !lore.isEmpty();

            case "addItemFlags":
                Collections.addAll(flags, (ItemFlag[]) args[0]);
                return null;
            case "removeItemFlags":
                for (ItemFlag flag : (ItemFlag[]) args[0]) {
                    flags.remove(flag);
                }
                return null;
            case "getItemFlags":
                return EnumSet.copyOf(flags);
            case "hasItemFlag":
                return flags.contains(args[0]);

            case "setCustomModelData":
                customModelData = (Integer) args[0];
                return null;
            case "hasCustomModelData":
                return customModelData != null;
            case "getCustomModelData":
                if (customModelData == null) {
                    throw new IllegalStateException("We don't have CustomModelData! Check hasCustomModelData first!");
                }
                return customModelData;

            case "addEnchant":
                enchants.put((Enchantment) args[0], (Integer) args[1]);
                return true;
            case "removeEnchant":
                return enchants.remove(args[0]) != null;
            case "getEnchants":
                return new LinkedHashMap<>(enchants);
            case "hasEnchants":
                return !enchants.isEmpty();
            case "hasEnchant":
                return enchants.containsKey(args[0]);
            case "getEnchantLevel":
                return enchants.containsKey(args[0]) ? enchants.get(args[0]) : 0;

            case "setUnbreakable":
                unbreakable = (Boolean) args[0];
                return null;
            case "isUnbreakable":
                return unbreakable;

            case "setDamage":
                damage = (Integer) args[0];
                return null;
            case "getDamage":
                return damage;
            case "hasDamage":
                return damage != 0;

            case "clone":
                return copy().asMeta();
            case "equals":
                return sameAs(args[0]);
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "ItemMetaDouble{" + displayName + ", " + lore + ", " + flags + ", " + enchants
                        + ", cmd=" + customModelData + "}";
            default:
                return method.getReturnType().isPrimitive() ? neutral(method.getReturnType()) : null;
        }
    }

    private static Object neutral(Class<?> returnType) {
        if (returnType == boolean.class) {
            return Boolean.FALSE;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        if (returnType == float.class) {
            return 0F;
        }
        return 0D;
    }

}
