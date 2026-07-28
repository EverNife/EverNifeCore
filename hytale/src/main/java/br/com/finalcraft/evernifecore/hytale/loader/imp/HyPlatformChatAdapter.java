package br.com.finalcraft.evernifecore.hytale.loader.imp;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleUtil;
import br.com.finalcraft.evernifecore.hytale.util.HyTextMetrics;
import br.com.finalcraft.evernifecore.text.ITextMetrics;
import br.com.finalcraft.evernifecore.util.FCServerUtil;

import java.util.ArrayList;
import java.util.List;

public class HyPlatformChatAdapter implements IPlatformChatAdapter {

    @Override
    public ITextMetrics getTextMetrics() {
        return HyTextMetrics.INSTANCE;
    }

    @Override
    public List<FCommandSender> getBroadcastAudience() {
        List<FCommandSender> senders = new ArrayList<>();

        for (FPlayer onlinePlayer : FCServerUtil.getOnlinePlayers()) {
            senders.add(onlinePlayer);
        }

        senders.add(FCHytaleUtil.getConsoleSender());

        return senders;
    }

    @Override
    public boolean supportsHover(String typeId) {
        // com.hypixel.hytale.server.core.Message has no hover concept at all (bold/italic/monospace/
        // color/link only, confirmed against the pinned server API) - see FCHytaleAdventureUtil,
        // whose hover-handling branch has always been commented out. No hover typeId renders here yet.
        return false;
    }
}
