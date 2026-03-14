package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.hytale.HytaleFCommandSender;
import br.com.finalcraft.evernifecore.api.hytale.HytaleFPlayer;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class FCHytaleUtil {

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
    public static boolean isNotPlayer(FCommandSender sender) {
        if (!sender.isPlayer()) {
            ONLY_PLAYERS_CAN_USE_THIS_COMMAND
                    .send(sender);
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
    public static boolean hasThePermission(FCommandSender player, String permission) {

        if (!player.hasPermission(permission)) {
            FCMessageUtil.needsThePermission(player, permission);
            return false;
        }
        return true;
    }

    /**
     * Força o console a executar um comando!
     */
    public static void makeConsoleExecuteCommand(String theCommand) {
        CommandManager.get().handleCommand(ConsoleSender.INSTANCE, theCommand);
    }

    /**
     * Força o console a executar um comando!
     */
    public static void makeConsoleExecuteCommand(String... theCommands) {
        for (String theCommand : theCommands) {
            CommandManager.get().handleCommand(ConsoleSender.INSTANCE, theCommand);
        }
    }

    /**
     * Força o jogador a executar um comando!
     */
    public static void makePlayerExecuteCommand(FCommandSender player, String theCommand) {
        com.hypixel.hytale.server.core.command.system.CommandSender delegate = player.getDelegate(com.hypixel.hytale.server.core.command.system.CommandSender.class);
        CommandManager.get().handleCommand(delegate, theCommand);
    }

    public static FPlayer wrap(Player player){
        return HytaleFPlayer.of(player);
    }

    public static FPlayer wrap(PlayerRef playerRef){
        return HytaleFPlayer.of(playerRef);
    }

    public static FCommandSender wrap(CommandSender commandSender){
        return HytaleFCommandSender.of(commandSender);
    }

    public static Vector3i getTargetBlock(FPlayer player, double maxDistance){
        Player hyPlayer = ((HytaleFPlayer) player).getPlayer();

        Ref<EntityStore> reference = hyPlayer.getReference();

        Store<EntityStore> store = hyPlayer.getReference().getStore();

        return TargetUtil.getTargetBlock(reference, maxDistance, store);
    }

    /**
     * Coloca itens no inventário de um jogador, casos obre itens, eles serão dropados no chão!
     *
     * @param player Instancia do jogador que receberá os itens
     * @param itemStacks Itens que serão entregues.
     */
    public static void giveItemsTo(FPlayer player, List<ItemStack> itemStacks) {
        giveItemsTo(player, true, itemStacks);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §eYou received items but did not have inventory space. \n - §7(§6%itens_droped% §7x §6Itens dropped on the ground!§7)")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §eVocê recebeu itens mas não tinha espaço suficiente no inventário. \n - §7(§6%itens_droped% §7x §6Itens dropados no chão!§7)")
    private static LocaleMessage YOU_RECEIVED_EXTRA_ITEMS_THAT_WERE_DROPED;
    public static void giveItemsTo(FPlayer player, boolean dropIfExceeded, List<ItemStack> itemStacks) {
        HytaleFPlayer hytaleFPlayer = (HytaleFPlayer) player;

        final World world = hytaleFPlayer.getWorld();

        FCScheduler.SynchronizedAction.run(world, () -> {

            if (!hytaleFPlayer.isOnline()){
                return;
            }

            Player hytalePlayer = hytaleFPlayer.getPlayer();

            Location location = hytaleFPlayer.getLocation();

            ListTransaction<ItemStackTransaction> transactionList = hytalePlayer.getInventory()
                    .getCombinedHotbarFirst()
                    .addItemStacks(itemStacks);

            List<ItemStack> exceededItems = transactionList.getList().stream()
                    .map(ItemStackTransaction::getRemainder)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (exceededItems.size() > 0 && dropIfExceeded) {

                Store<EntityStore> store = world.getEntityStore().getStore();

                for (ItemStack remainder : exceededItems) {
                    float throwSpeed = 6F;

                    Vector3d throwPosition = location.getPosition();
                    throwPosition.add(0.0, 0.5, 0.0);
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    Holder<EntityStore> itemEntityHolder = ItemComponent.generateItemDrop(
                            store,
                            remainder,
                            throwPosition,
                            Vector3f.ZERO,
                            (float)random.nextGaussian() * throwSpeed,
                            0.5F,
                            (float)random.nextGaussian() * throwSpeed
                    );

                    ItemComponent itemEntityComponent = itemEntityHolder.getComponent(ItemComponent.getComponentType());
                    if (itemEntityComponent != null) {
                        itemEntityComponent.setPickupDelay(1.5F);
                    }

                    store.addEntity(itemEntityHolder, AddReason.SPAWN);
                }

                if (ECSettings.WARN_PLAYERS_WHEN_RECEIVED_ITEMS_WERE_SEND_TO_THE_GROUND){
                    YOU_RECEIVED_EXTRA_ITEMS_THAT_WERE_DROPED
                            .addPlaceholder("%itens_droped%", exceededItems.size())
                            .send(player);
                }
            }
        });

    }

    public static void broadcastMessage(String message) {
        FancyText.of(message).broadcast();
    }
}
