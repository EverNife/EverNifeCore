package br.com.finalcraft.evernifecore.minecraft.action.listener;

import br.com.finalcraft.evernifecore.action.Actions;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.event.EnchantActionEvent;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;

/**
 * An item enchanted at a table ({@link EventSource#ENCHANT}), asked where the table stands. A denial, or a cancelled
 * {@code Pre}, leaves the item as it was; one that went through posts its {@link EnchantActionEvent} Post.
 */
public final class EnchantActionListener implements ECListener {

    @Override
    public boolean silentRegistration() {
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!McActionContexts.isDisabled(event.getEnchantBlock().getWorld())
                && Actions.denied(context(event), EnchantActionEvent.Pre.class, EnchantActionEvent.Pre::new)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterEnchant(EnchantItemEvent event) {
        if (!McActionContexts.isDisabled(event.getEnchantBlock().getWorld())) {
            Actions.post(EnchantActionEvent.Post.class, () -> new EnchantActionEvent.Post(context(event)));
        }
    }

    private static ItemContext context(EnchantItemEvent event) {
        return new ItemContext(EventSource.ENCHANT, McActionContexts.position(event.getEnchantBlock().getLocation()),
                McActionContexts.actor(event.getEnchanter()), event, McActionContexts.key(event.getItem()), event.getItem().getAmount(), null,
                McActionContexts.item(event.getItem()));
    }
}
