package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.util.FCTextUtil;
import br.com.finalcraft.evernifecore.util.FCServerUtil;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

public class McPlatformChatAdapter implements IPlatformChatAdapter {

    @Override
    public String alignCenter(String stringToAlign) {
        return FCTextUtil.alignCenter(stringToAlign);
    }

    @Override
    public String alignCenter(String stringToAlign, String borderFill) {
        return FCTextUtil.alignCenter(stringToAlign, borderFill);
    }

    @Override
    public String straightLineOf(String string) {
        return FCTextUtil.straightLineOf(string);
    }

    @Override
    public void broadcast(FancyText fancyText) {
        List<FCommandSender> senders = new ArrayList<>();

        for (FPlayer onlinePlayer : FCServerUtil.getOnlinePlayers()) {
            senders.add(onlinePlayer);
        }

        senders.add(FCBukkitUtil.adapt(Bukkit.getConsoleSender()));

        fancyText.send(senders.toArray(new FCommandSender[0]));
    }

    @Override
    public boolean supportsHover(String typeId) {
        // Every hover kind renders through Adventure's HoverEvent and crosses to legacy BaseComponents
        // via FCComponentUtil; that pipeline never restricts which typeId produced the event, so Bukkit
        // supports whatever hover a registered FancyHoverType is able to build.
        return true;
    }
}
