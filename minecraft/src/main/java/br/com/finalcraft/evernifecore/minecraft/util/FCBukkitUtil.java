package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFCommandSender;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCServerType;
import br.com.finalcraft.evernifecore.ontime.OntimeManager;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import jakarta.annotation.Nullable;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockIterator;

import java.util.*;
import java.util.function.Function;

public class FCBukkitUtil {

    private static final Function<String, Boolean> isModLoaded = detectModLoader(FCReflectionUtil.getClasses()::getClass);

    private static final List<Class<?>> fakePlayerTypes = resolveFakePlayerTypes(FCReflectionUtil.getClasses()::getClass);

    /**
     * The mod list of the Forge-family loader behind this server, or {@code null} when there is none.
     * Each loader generation moved the class that answers "is this mod loaded?", so each is asked by
     * the name it has in its own era, newest last.
     *
     * @param classes resolves a class name to the class, or {@code null} - a parameter so a test can
     *                pose a loader this JVM does not carry.
     */
    static Function<String, Boolean> detectModLoader(Function<String, Class<?>> classes) {
        try {
            Class<?> loader;
            if ((loader = classes.apply("cpw.mods.fml.common.Loader")) != null) {             //Forge 1.7.10
                return staticModQuery(loader);
            }
            if ((loader = classes.apply("net.minecraftforge.fml.common.Loader")) != null) {   //Forge 1.8 - 1.12.2
                return staticModQuery(loader);
            }
            if ((loader = classes.apply("net.minecraftforge.fml.ModList")) != null) {         //Forge 1.13+
                return modListQuery(loader);
            }
            if ((loader = classes.apply("net.neoforged.fml.ModList")) != null) {              //NeoForge
                return modListQuery(loader);
            }
        } catch (RuntimeException | LinkageError failure) {
            EverNifeCore.getLog().warning("[FCBukkitUtil] This server carries a Forge-family mod loader, but asking"
                    + " it which mods are loaded failed. isModded(), isModLoaded() and isFakePlayer() answer as on a"
                    + " plain Bukkit server for the rest of this run - report the server brand and version.", failure);
        }
        return null;
    }

    private static Function<String, Boolean> staticModQuery(Class<?> loader) {
        MethodInvoker<Boolean> isModLoaded = FCReflectionUtil.getMethods().getMethod(loader, "isModLoaded", String.class);
        Objects.requireNonNull(isModLoaded, () -> loader.getName() + " declares no isModLoaded(String)");
        return modId -> isModLoaded.invoke(null, modId);
    }

    private static Function<String, Boolean> modListQuery(Class<?> modListClass) {
        MethodInvoker<Object> get = FCReflectionUtil.getMethods().getMethod(modListClass, "get");
        MethodInvoker<Boolean> isLoaded = FCReflectionUtil.getMethods().getMethod(modListClass, "isLoaded", String.class);
        Objects.requireNonNull(get, () -> modListClass.getName() + " declares no get()");
        Objects.requireNonNull(isLoaded, () -> modListClass.getName() + " declares no isLoaded(String)");
        Object modList = get.invoke(null);
        return modId -> isLoaded.invoke(modList, modId);
    }

    /**
     * The base classes a mod extends to act as a player that is not one - a machine breaking a block,
     * a turtle placing it. Forge kept one name from 1.7.10 on; NeoForge renamed its package.
     */
    static List<Class<?>> resolveFakePlayerTypes(Function<String, Class<?>> classes) {
        List<Class<?>> types = new ArrayList<>(2);
        for (String name : new String[]{
                "net.minecraftforge.common.util.FakePlayer",
                "net.neoforged.neoforge.common.util.FakePlayer"}) {
            Class<?> type = classes.apply(name);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    public static boolean isFakePlayer(String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        return player == null || isFakePlayer(player);
    }

    /**
     * Whether {@code player} is a mod's stand-in rather than someone connected - told by the class of
     * the server entity behind it, the same test Forge's own {@code instanceof FakePlayer} makes.
     * Always {@code false} on a server without a Forge-family loader, where no such entity exists.
     */
    public static boolean isFakePlayer(Player player) {
        return isFakePlayer(player, fakePlayerTypes);
    }

    static boolean isFakePlayer(Player player, List<Class<?>> fakePlayerTypes) {
        if (fakePlayerTypes.isEmpty() || player == null) {
            return false;
        }
        //CraftEntity.getHandle() is the door to the server entity on every CraftBukkit-derived server
        MethodInvoker<Object> getHandle = FCReflectionUtil.getMethods().getMethod(player.getClass(), "getHandle");
        if (getHandle == null) {
            return false; //not a server-backed player, so not a mod's entity either
        }
        Object handle = getHandle.invoke(player);
        for (Class<?> fakePlayerType : fakePlayerTypes) {
            if (fakePlayerType.isInstance(handle)) {
                return true;
            }
        }
        return false;
    }

    //===========================================================================================
    //  Documented Functions
    //===========================================================================================

    /**
     * Puts items into a player's inventory; any leftover items are dropped on the ground.
     *
     * @param player the player who will receive the items
     * @param itemStacks the items to deliver
     */
    public static void giveItemsTo(Player player, ItemStack... itemStacks) {
        giveItemsTo(player, true, itemStacks);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §eYou received items but did not have inventory space. The extra items were dropped on the ground!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §eVocê recebeu itens mas não tinha espaço suficiente no inventário. Os itens foram dropados no chão!")
    private static LocaleMessage YOU_RECEIVED_EXTRA_ITEMS_THAT_WERE_DROPED;
    public static void giveItemsTo(Player player, boolean dropIfExceeded, ItemStack... itemStacks) {
        HashMap<Integer, ItemStack> exceededItems = player.getInventory().addItem(itemStacks);
        if (exceededItems.size() > 0 && dropIfExceeded) {
            YOU_RECEIVED_EXTRA_ITEMS_THAT_WERE_DROPED
                    .sendIf(ECSettings.WARN_PLAYERS_WHEN_RECEIVED_ITEMS_WERE_SEND_TO_THE_GROUND, adapt(player));

            final World world = player.getWorld();
            final Location location = player.getLocation();
            for (Map.Entry<Integer, ItemStack> exceededItem : exceededItems.entrySet()) {
                world.dropItem(location, exceededItem.getValue());
            }
        }
        //Update the inventory, because the player might have a gui open!
        //And giving an item without updating the gui will create a visual glitch!
        player.updateInventory();
    }


    /**
     * If the playerName has never joined the server, return null.
     *
     * @param playerName The name of the player you want to get the UUID of.
     * @return The OfflinePlayer object of the player with the given name.
     */
    public static OfflinePlayer getOfflinePlayer(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;

        UUID offlineUUID = UUIDsController.getUUIDFromName(playerName);
        if (offlineUUID == null) return null;

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(offlineUUID);

        return offlinePlayer.getLastPlayed() != 0 ? offlinePlayer : null;
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cOnly players can use this command!.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cApenas jogadores podem usar esse comando!.")
    private static LocaleMessage ONLY_PLAYERS_CAN_USE_THIS_COMMAND;

    /**
     * If the sender is not a player, send the sender the message
     * "ONLY_PLAYERS_CAN_USE_THIS_COMMAND" and return true, otherwise
     * return false
     *
     * @param sender The CommandSender.
     * @return if the sender is a player.
     */
    public static boolean isNotPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            ONLY_PLAYERS_CAN_USE_THIS_COMMAND
                    .send(adapt(sender));
            return true;
        }
        return false;
    }

    /**
     * If the player does not have the permission, send them a message and return false. Otherwise, return true
     *
     * @param player The player who is trying to execute the command.
     * @param permission The permission you want to check.
     * @return A boolean value.
     */
    public static boolean hasThePermission(CommandSender player, String permission) {
        return FCMessageUtil.hasThePermission(adapt(player), permission);
    }

    /**
     * It returns a list of blocks in a radius of a location
     *
     * @param location The location of the center of the circle.
     * @param radius The radius of the circle.
     * @return A list of blocks in a radius of the location.
     */
    public static List<Block> getBlocksInRadius(Location location, int radius) {

        List<Block> blocks = new ArrayList<Block>();

        //Set the bounds of the region to loop over
        final int minX = location.getBlockX() - radius;
        final int minY = location.getBlockY() - radius;
        final int minZ = location.getBlockZ() - radius;
        final int maxX = location.getBlockX() + radius;
        final int maxY = location.getBlockY() + radius;
        final int maxZ = location.getBlockZ() + radius;

        for (int counterX = minX; counterX <= maxX; counterX++) {
            for (int counterY = minY; counterY <= maxY; counterY++) {
                for (int counterZ = minZ; counterZ <= maxZ; counterZ++) {
                    blocks.add(new Location(location.getWorld(), counterX, counterY, counterZ).getBlock());
                }
            }
        }

        return blocks;
    }

    /**
     * Forces the console to execute a command.
     */
    public static boolean makeConsoleExecuteCommand(String theCommand) {
        if (Bukkit.getServer().isPrimaryThread()) {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), theCommand);
        } else {
            EverNifeCore.getLog().warning("Calling [makeConsoleExecuteCommand(\"" + theCommand + "\")] out of Main Thread... i am fixing it for you, but... you may do your job!");
            makeConsoleExecuteCommandFromAsyncThread(theCommand);
            return false; // the deferred dispatch result is not known synchronously
        }
    }

    /**
     * Forces the console to execute a command.
     */
    public static void makeConsoleExecuteCommand(String... theCommands) {
        if (Bukkit.getServer().isPrimaryThread()) {
            for (String theCommand : theCommands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), theCommand);
            }
        } else {
            EverNifeCore.getLog().warning("Calling [makeConsoleExecuteCommand(\"" + String.join("|", theCommands) + "\")] out of Main Thread... i am fixing it for you, but... you may do your job!");
            makeConsoleExecuteCommandFromAsyncThread(theCommands);
        }
    }

    /**
     * Forces the console to execute a command.
     */
    public static void makeConsoleExecuteCommandFromAsyncThread(String theCommand) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), theCommand);
            }
        }.runTask(EverNifeCoreBukkitPlugin.instance);
    }

    /**
     * Forces the console to execute a command.
     */
    public static void makeConsoleExecuteCommandFromAsyncThread(String... theCommands) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (String theCommand : theCommands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), theCommand);
                }
            }
        }.runTask(EverNifeCoreBukkitPlugin.instance);
    }

    /**
     * Forces the player to execute a command.
     */
    public static boolean makePlayerExecuteCommand(CommandSender player, String theCommand) {
        return Bukkit.dispatchCommand(player, theCommand);
    }

    /**
     * Checks whether a player is holding a given item.
     * The item must match the given itemTypeName (example: minecraft:chest)
     * and the given itemDisplayName (example: "Bau do milenio").
     * <p>
     * If itemDisplayName is empty (an empty string) the DisplayName is ignored.
     * <p>
     * Returns TRUE if the item matches the expectation and FALSE otherwise.
     * <p>
     * Note:  itemTypeName is CaseSensitive
     * itemDisplayName is not CaseSensitive (Ignoring Colors)
     */
    public static boolean playerIsHoldingTheItem(Player player, String itemTypeName) {
        return playerIsHoldingTheItem(player, itemTypeName, null);
    }

    public static boolean playerIsHoldingTheItem(Player player, String itemTypeName, @Nullable String itemDisplayName) {

        ItemStack itemStack = getPlayersHeldItem(player);
        if (itemStack != null) {
            if (itemStack.getType().name().equalsIgnoreCase(itemTypeName)) {

                if (itemDisplayName == null || itemDisplayName.isEmpty()) {
                    return true;
                }

                String displayName = FCItemUtils.getDisplayName(itemStack);
                return displayName != null && displayName.equalsIgnoreCase(itemDisplayName);
            }
        }
        return false;
    }

    public static boolean removePlayersHeldItem(Player player, int amountToRemove) {
        ItemStack heldItem = getPlayersHeldItem(player);
        if (heldItem != null) {
            int amoutLeft = heldItem.getAmount() - amountToRemove;
            if (amoutLeft >= 0) {
                if (amoutLeft == 0) {
                    setPlayersHeldItem(player, null);
                } else {
                    heldItem.setAmount(amoutLeft);
                }
                return true;
            }
        }
        return false;
    }

    public static void setPlayersHeldItem(Player player, ItemStack itemStack) {
        //the main-hand pair arrived with the off hand in 1.9; before that the held item IS the hand
        if (MCVersion.isLower(MCDetailedVersion.v1_9_R1)) {
            player.setItemInHand(itemStack);
        } else {
            player.getInventory().setItemInMainHand(itemStack);
        }
    }

    public static ItemStack getPlayersHeldItem(Player player) {
        final ItemStack heldItem;

        if (MCVersion.isLower(MCDetailedVersion.v1_9_R1)) {
            heldItem = player.getItemInHand();
        } else {
            heldItem = player.getInventory().getItemInMainHand();
        }

        return heldItem != null && heldItem.getType() == Material.AIR ? null : heldItem;
    }

    public static void feedPlayer(Player player, int amount) {
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + amount));
    }

    public static Block getTargetBlock(Player player, int maxDistance) {
        if (MCVersion.isLowerEquals(MCVersion.v1_7_10)) {
            final BlockIterator iterator = new BlockIterator(player.getLocation(), player.getEyeHeight(), maxDistance);
            Block result;
            while (iterator.hasNext()) {
                result = iterator.next();
                if (result.getType() != Material.AIR) {
                    return result;
                }
            }
        } else {
            return player.getTargetBlock(null, maxDistance);
        }
        return null;
    }

    public static boolean isModded(){
        return isForge();
    }

    public static boolean isForge(){
        return isModLoaded != null;
    }

    public static boolean isModLoaded(String modname){
        if (isModLoaded == null) return false;
        return isModLoaded.apply(modname);
    }

    public static long getOntime(IPlayerData playerData){
        return OntimeManager.getProvider().getOntime(playerData); //Ontime provider might be overridden by the OnTime plugin
    }

    //
    //This is meanted to be only by my private network plugins, don't use this on any public plugin!
    //
    public static String getPlayerStaffRank(Player player) {
        if (MCServerType.isEverNifePersonalServer()){
            if (player.getName().equalsIgnoreCase("EverNife")) return "Dono";
            if (player.hasPermission("be.diretor")) return "Diretor";
            if (player.hasPermission("be.admin")) return "Admin";
            if (player.hasPermission("be.moderador")) return "Moderador";
            if (player.hasPermission("be.ajudante")) return "Ajudante";
            return "Jogador";
        }
        return "Player";
    }

    public static boolean isMainThread() {
        return Bukkit.getServer().isPrimaryThread();
    }

    public static FPlayer adapt(Player player){
        return MinecraftFPlayer.of(player);
    }

    public static FPlayer adapt(OfflinePlayer player){
        return MinecraftFPlayer.of(player);
    }

    public static FCommandSender adapt(CommandSender commandSender){
        return MinecraftFCommandSender.of(commandSender);
    }

    public static FLocation adapt(Location location){
        WorldLocPos worldLocPos = WorldLocPos.of(
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getWorld().getName()
        );

        return new FLocation(worldLocPos, location);
    }

    public static BlockPos adaptBlockPos(Location location){
        return BlockPos.of(
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    public static LocPos adaptLocPos(Location location){
        return LocPos.of(
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

}
