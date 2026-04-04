package br.com.finalcraft.evernifecore.hytale.commands.debug;


import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCTextUtil;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

public class CMDTestScheduler {

    @FCLocale(lang = LocaleType.EN_US, text = "Testing Scheduler!!")
    private static LocaleMessage TEST_SCHEDULER;

    @FinalCMD(
            aliases = {"fcscheduler"}
    )
    public void iteminfo(FPlayer player, ItemStack heldItem, MultiArgumentos argumentos) {

        player.sendMessage("&7" + FCColorUtil.stripColor(FCTextUtil.straightLineOf("---------------------------------")));

        TEST_SCHEDULER.send(player);

        FCScheduler.runAsync(() -> {
            try {
                player.sendMessage("3");
                Thread.sleep(1000);
                player.sendMessage("2");
                Thread.sleep(1000);
                player.sendMessage("1");
                Thread.sleep(1000);

                Player hyPlayer = player.asHytaleFPlayer().getPlayer();

                String itemId = FCScheduler.getHytaleScheduler().getSynchronizedAction().runAndGet(hyPlayer.getWorld(), () -> {
                    player.sendMessage("TESTE");
                    return hyPlayer.getInventory().getActiveToolItem().getItemId();
                });

                player.sendMessage("ItemId: " + itemId);

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

    }

}
