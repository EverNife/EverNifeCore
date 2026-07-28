package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.minecraft.util.McTextMetrics;
import br.com.finalcraft.evernifecore.text.ITextMetrics;
import br.com.finalcraft.evernifecore.util.FCServerUtil;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

public class McPlatformChatAdapter implements IPlatformChatAdapter {

    @Override
    public ITextMetrics getTextMetrics() {
        return McTextMetrics.INSTANCE;
    }

    @Override
    public List<FCommandSender> getBroadcastAudience() {
        List<FCommandSender> senders = new ArrayList<>();

        for (FPlayer onlinePlayer : FCServerUtil.getOnlinePlayers()) {
            senders.add(onlinePlayer);
        }

        senders.add(FCBukkitUtil.adapt(Bukkit.getConsoleSender()));

        return senders;
    }

    @Override
    public boolean supportsHover(String typeId) {
        // Every hover kind renders through Adventure's HoverEvent and crosses to legacy BaseComponents
        // via FCComponentUtil; that pipeline never restricts which typeId produced the event, so Bukkit
        // supports whatever hover a registered FancyHoverType is able to build.
        return true;
    }
}
