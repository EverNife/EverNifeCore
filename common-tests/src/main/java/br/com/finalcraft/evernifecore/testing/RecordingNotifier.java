package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.notify.Notification;
import br.com.finalcraft.evernifecore.notify.NotifyChannel;
import br.com.finalcraft.evernifecore.notify.Notifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the {@link Notifier} delivered, for a test: a channel named {@code chat} registered under a test plugin, so
 * every default chain (they all end on chat) lands here instead of on the stock chat line. The text still reaches the
 * player's chat, as the stock channel would send it.
 */
public final class RecordingNotifier implements NotifyChannel {

    /** One delivery. */
    public static final class Delivery {
        public final UUID player;
        public final Notification notification;

        Delivery(UUID player, Notification notification) {
            this.player = player;
            this.notification = notification;
        }

        /** The text without color codes. */
        public String getText() {
            return notification.getText().toPlainText();
        }

        @Override
        public String toString() {
            return player + ": " + getText();
        }
    }

    private final String key;
    private final List<Delivery> deliveries = new CopyOnWriteArrayList<>();

    private RecordingNotifier(String key) {
        this.key = key;
    }

    /**
     * Registers a recorder under {@code owner}.
     *
     * @throws AssertionError when a {@code chat} channel is registered already (the core's stock one): the chains would
     *                        not know which one to take - open the recorder before {@code Notifier.registerStock}, or skip it
     */
    public static RecordingNotifier install(ECPluginData owner) {
        if (Notifier.get().find("chat").isPresent()) {
            throw new AssertionError("A 'chat' channel is registered already (" + Notifier.get().find("chat").get().getKey() + "), so"
                    + " a recorder named chat would make the name ambiguous: install the recorder before registerStock(core).");
        }
        RecordingNotifier recorder = new RecordingNotifier(owner.getMetaInfo().getName().toLowerCase(Locale.ROOT) + ":chat");
        Notifier.get().register(owner, recorder, "Records every notification for a test.");
        return recorder;
    }

    /** Takes the recorder out of the {@link Notifier}. */
    public void uninstall(ECPluginData owner) {
        Notifier.get().registry().unregister(owner, key);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public void deliver(FPlayer player, Notification notification) {
        deliveries.add(new Delivery(player.getUniqueId(), notification));
        notification.getText().send(player);
    }

    /** Every delivery, in order; live. */
    public List<Delivery> getDeliveries() {
        return deliveries;
    }

    /** The deliveries to {@code player}, in order. */
    public List<Delivery> to(FPlayer player) {
        List<Delivery> theirs = new ArrayList<>();
        for (Delivery delivery : deliveries) {
            if (delivery.player.equals(player.getUniqueId())) {
                theirs.add(delivery);
            }
        }
        return theirs;
    }

    public void clear() {
        deliveries.clear();
    }
}
