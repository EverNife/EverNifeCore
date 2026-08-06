package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A viewer: an identity, a set of permissions, a cursor, and the window they currently have open.
 *
 * <p>Opening is the interesting part. A real server answers {@code openInventory} by firing
 * {@code InventoryOpenEvent}, and the framework treats that event - not the call - as the moment the
 * window exists. {@link #refuseOpens(boolean)} is how a test plays the server that says no: the call
 * returns, no event follows, and nothing is supposed to be registered or scheduled.</p>
 */
public final class PlayerDouble {

    private final UUID uniqueId = UUID.randomUUID();
    private final String name;
    private final Set<String> permissions = new LinkedHashSet<>();
    private final SurfaceDouble playerInventory = new SurfaceDouble(36);
    private final List<String> messages = new ArrayList<>();
    private final Player face;
    private final GuiEventBus events;

    private ItemStack cursor;
    private SyntheticInventoryView openView;
    private boolean online = true;
    private boolean refuseOpens = false;

    PlayerDouble(String name, GuiEventBus events) {
        this.name = name;
        this.events = events;
        this.face = Doubles.of(Player.class)
                .on("getUniqueId", args -> uniqueId)
                .on("getName", args -> name)
                .on("isOnline", args -> online)
                .on("hasPermission", args -> args[0] instanceof String && permissions.contains(args[0]))
                .on("getItemOnCursor", args -> cursor)
                .on("getOpenInventory", args -> openView)
                .on("openInventory", args -> open((Inventory) args[0]))
                .on("closeInventory", args -> {
                    close();
                    return null;
                })
                .on("sendMessage", args -> {
                    messages.add(String.valueOf(args[0]));
                    return null;
                })
                .build();
    }

    /** The player as the framework sees it. */
    public Player asPlayer() {
        return face;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public String getName() {
        return name;
    }

    public PlayerDouble withPermission(String permission) {
        permissions.add(permission);
        return this;
    }

    public PlayerDouble online(boolean online) {
        this.online = online;
        return this;
    }

    /** Plays the server that will not open the window - the refusal {@code open()} has to survive. */
    public PlayerDouble refuseOpens(boolean refuse) {
        this.refuseOpens = refuse;
        return this;
    }

    public PlayerDouble holding(ItemStack cursor) {
        this.cursor = cursor;
        return this;
    }

    public ItemStack getCursor() {
        return cursor;
    }

    public List<String> getMessages() {
        return new ArrayList<>(messages);
    }

    /** The window currently open, or {@code null}. */
    public SyntheticInventoryView getOpenView() {
        return openView;
    }

    public SurfaceDouble getPlayerInventory() {
        return playerInventory;
    }

    private InventoryView open(Inventory inventory) {
        SyntheticInventoryView view = new SyntheticInventoryView(inventory, playerInventory.asInventory(),
                face, InventoryType.CHEST);
        if (refuseOpens) {
            return view;
        }
        SyntheticInventoryView previous = openView;
        openView = view;
        if (previous != null) {
            //a real server closes the previous window before the new one exists
            events.fireClose(previous);
        }
        events.fireOpen(view);
        return view;
    }

    private void close() {
        SyntheticInventoryView closing = openView;
        openView = null;
        if (closing != null) {
            events.fireClose(closing);
        }
    }

    /** The two containers a click can land in, joined the way a server joins them. */
    public static final class SyntheticInventoryView extends InventoryView {

        private final Inventory top;
        private final Inventory bottom;
        private final HumanEntity player;
        private final InventoryType type;

        SyntheticInventoryView(Inventory top, Inventory bottom, HumanEntity player, InventoryType type) {
            this.top = top;
            this.bottom = bottom;
            this.player = player;
            this.type = type;
        }

        @Override
        public Inventory getTopInventory() {
            return top;
        }

        @Override
        public Inventory getBottomInventory() {
            return bottom;
        }

        @Override
        public HumanEntity getPlayer() {
            return player;
        }

        @Override
        public InventoryType getType() {
            return type;
        }

        @Override
        public String getTitle() {
            return "";
        }

    }

}
