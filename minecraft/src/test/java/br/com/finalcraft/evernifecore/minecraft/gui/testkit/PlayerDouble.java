package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * A viewer: an identity, a set of permissions, a cursor, and the window they currently have open.
 *
 * <p>Opening is the interesting part. A real server answers {@code openInventory} by firing
 * {@code InventoryOpenEvent}, and the framework treats that event - not the call - as the moment the
 * window exists. {@link #refuseOpens(boolean)} is how a test plays the server that says no: the call
 * returns, no event follows, and nothing is supposed to be registered or scheduled.</p>
 *
 * <p>The event does not carry the container it was handed, either. A server builds its own window over
 * that storage and names ITS wrapper from then on, so this double asks for one too - and a screen that
 * kept hold of the object it created would find every later event pointing at a container it does not
 * recognise.</p>
 *
 * <p>They are also standing somewhere, in a world that can be given an item. That only matters once
 * their inventory is full - see {@link #withFullInventory(ItemStack)} - because the ground is where the
 * framework puts what it could not hand over.</p>
 */
public final class PlayerDouble {

    /** What a stack holds here. The real number comes from a server registry this rig does not have. */
    private static final int STACK = 64;

    private final UUID uniqueId = UUID.randomUUID();
    private final String name;
    private final Set<String> permissions = new LinkedHashSet<>();
    private final SurfaceDouble playerInventory = new SurfaceDouble(36);
    private final List<String> messages = new ArrayList<>();
    private final List<Drop> drops = new ArrayList<>();
    private final World world;
    private final Location standingAt;
    private final Player face;
    private final GuiEventBus events;
    private final Function<Inventory, Inventory> serverWindowOver;

    private ItemStack cursor;
    private InventoryView openView;
    private PlayerInventory inventoryFace;
    private boolean online = true;
    private boolean refuseOpens = false;

    PlayerDouble(String name, GuiEventBus events, Function<Inventory, Inventory> serverWindowOver) {
        this.name = name;
        this.events = events;
        this.serverWindowOver = serverWindowOver;
        this.world = Doubles.of(World.class)
                .on("getName", args -> "world")
                .on("dropItem", args -> {
                    drops.add(new Drop((Location) args[0], (ItemStack) args[1]));
                    return null;
                })
                .build();
        this.standingAt = new Location(world, 0.5D, 64D, 0.5D);
        this.face = Doubles.of(Player.class)
                .on("getUniqueId", args -> uniqueId)
                .on("getName", args -> name)
                .on("isOnline", args -> online)
                .on("hasPermission", args -> args[0] instanceof String && permissions.contains(args[0]))
                .on("getWorld", args -> world)
                .on("getLocation", args -> standingAt)
                .on("getItemOnCursor", args -> cursor)
                .on("setItemOnCursor", args -> {
                    cursor = (ItemStack) args[0];
                    return null;
                })
                .on("getInventory", args -> playerInventoryFace())
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

    /**
     * Fills every slot of their own inventory with a full stack of {@code filler}, so that the next
     * item handed to them has nowhere left to go.
     */
    public PlayerDouble withFullInventory(ItemStack filler) {
        for (int slot = 0; slot < playerInventory.getSize(); slot++) {
            ItemStack stack = filler.clone();
            stack.setAmount(STACK);
            playerInventory.placeWithoutRecording(slot, stack);
        }
        return this;
    }

    public ItemStack getCursor() {
        return cursor;
    }

    /** Where this player is standing - the spot an item they could not hold lands on. */
    public Location getStandingAt() {
        return standingAt;
    }

    /** Everything the platform dropped on the ground for this player, in order. */
    public List<Drop> getDrops() {
        return new ArrayList<>(drops);
    }

    public List<String> getMessages() {
        return new ArrayList<>(messages);
    }

    /** The window currently open, or {@code null}. */
    public InventoryView getOpenView() {
        return openView;
    }

    public SurfaceDouble getPlayerInventory() {
        return playerInventory;
    }

    /**
     * The player's own inventory, as much of it as a gui ever asks for: one slot read, one slot written,
     * and {@code addItem}, which is how the framework gives an item back.
     *
     * <p>{@code addItem} tops up the stacks already there before filling an empty slot and answers
     * whatever did not fit, the same shape the platform answers with. A stack is 64 here whatever the
     * material is - measuring one needs a server registry, and no gui asks about that.</p>
     */
    private PlayerInventory playerInventoryFace() {
        if (inventoryFace == null) {
            inventoryFace = Doubles.of(PlayerInventory.class)
                    .on("getSize", args -> playerInventory.getSize())
                    .on("getItem", args -> playerInventory.getItem((Integer) args[0]))
                    .on("setItem", args -> {
                        playerInventory.set((Integer) args[0], (ItemStack) args[1]);
                        return null;
                    })
                    .on("addItem", args -> addItems((ItemStack[]) args[0]))
                    .build();
        }
        return inventoryFace;
    }

    private Map<Integer, ItemStack> addItems(ItemStack[] items) {
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        for (int index = 0; index < items.length; index++) {
            ItemStack left = pourIn(items[index]);
            if (left != null) {
                leftovers.put(index, left);
            }
        }
        return leftovers;
    }

    private ItemStack pourIn(ItemStack item) {
        if (item == null || item.getAmount() <= 0) {
            return null;
        }
        int left = item.getAmount();
        for (int slot = 0; slot < playerInventory.getSize() && left > 0; slot++) {
            ItemStack held = playerInventory.getItem(slot);
            if (held == null) {
                int given = Math.min(left, STACK);
                ItemStack put = item.clone();
                put.setAmount(given);
                playerInventory.set(slot, put);
                left -= given;
            } else if (held.isSimilar(item) && held.getAmount() < STACK) {
                int given = Math.min(left, STACK - held.getAmount());
                ItemStack merged = held.clone();
                merged.setAmount(held.getAmount() + given);
                playerInventory.set(slot, merged);
                left -= given;
            }
        }
        if (left <= 0) {
            return null;
        }
        ItemStack over = item.clone();
        over.setAmount(left);
        return over;
    }

    private InventoryView open(Inventory inventory) {
        InventoryView view = joinedView(serverWindowOver.apply(inventory), playerInventory.asInventory(), face);
        if (refuseOpens) {
            return view;
        }
        InventoryView previous = openView;
        openView = view;
        if (previous != null) {
            //a real server closes the previous window before the new one exists
            events.fireClose(previous);
        }
        events.fireOpen(view);
        return view;
    }

    private void close() {
        InventoryView closing = openView;
        openView = null;
        if (closing != null) {
            events.fireClose(closing);
        }
    }

    /**
     * The two containers a click can land in, joined the way a server joins them.
     *
     * <p>The join is the view's whole job, and every raw-slot question an event asks - which
     * container was clicked, what is in the slot, what replaces it - comes back through it.</p>
     */
    private static InventoryView joinedView(Inventory top, Inventory bottom, HumanEntity player) {
        return Doubles.of(InventoryView.class)
                .returning("getTopInventory", top)
                .returning("getBottomInventory", bottom)
                .returning("getPlayer", player)
                .returning("getType", InventoryType.CHEST)
                .returning("getTitle", "")
                .on("getInventory", args -> containerOf(top, bottom, (Integer) args[0]))
                .on("convertSlot", args -> localSlot(top, (Integer) args[0]))
                .on("getCursor", args -> player.getItemOnCursor())
                .on("getItem", args -> {
                    Inventory container = containerOf(top, bottom, (Integer) args[0]);
                    return container == null ? null : container.getItem(localSlot(top, (Integer) args[0]));
                })
                .on("setItem", args -> {
                    Inventory container = containerOf(top, bottom, (Integer) args[0]);
                    if (container != null) {
                        container.setItem(localSlot(top, (Integer) args[0]), (ItemStack) args[1]);
                    }
                    return null;
                })
                .build();
    }

    /** Which container a raw slot lands in; {@code null} is the client saying "outside the window". */
    private static Inventory containerOf(Inventory top, Inventory bottom, int rawSlot) {
        if (rawSlot < 0) {
            return null;
        }
        return rawSlot < top.getSize() ? top : bottom;
    }

    /**
     * The same raw slot as an index into the container it landed in.
     *
     * <p>Inside the screen the two numbers are equal. Below it they are not: the player's own
     * inventory is sent with its main rows before its hotbar, while it numbers the hotbar first - so
     * raw slot 31 of a 27-slot screen is local slot 13, a number the screen also has. That collision
     * is why the framework judges a click by its raw slot and never by this one.</p>
     */
    private static int localSlot(Inventory top, int rawSlot) {
        if (rawSlot < top.getSize()) {
            return rawSlot;
        }
        int slot = rawSlot - top.getSize();
        return slot >= 27 ? slot - 27 : slot + 9;
    }

    /** One item that ended up on the ground: where it landed, and what it was. */
    public static final class Drop {

        public final Location location;
        public final ItemStack item;

        Drop(Location location, ItemStack item) {
            this.location = location;
            this.item = item;
        }

        @Override
        public String toString() {
            return "Drop{" + (item == null ? "nothing" : item.getType() + " x" + item.getAmount())
                    + " at " + location + "}";
        }
    }

}
