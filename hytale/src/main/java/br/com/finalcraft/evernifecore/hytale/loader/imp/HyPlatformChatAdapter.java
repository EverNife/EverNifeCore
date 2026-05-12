package br.com.finalcraft.evernifecore.hytale.loader.imp;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.hytale.util.FCHytaleUtil;
import br.com.finalcraft.evernifecore.util.FCServerUtil;

import java.util.ArrayList;
import java.util.List;

public class HyPlatformChatAdapter implements IPlatformChatAdapter {

    @Override
    public String alignCenter(String stringToAlign) {
        return "";
    }

    @Override
    public String alignCenter(String stringToAlign, String borderFill) {
        return "";
    }

    @Override
    public String straightLineOf(String string) {
        return "";
    }

    @Override
    public void broadcast(FancyText fancyText) {
        List<FCommandSender> senders = new ArrayList<>();

        for (FPlayer onlinePlayer : FCServerUtil.getOnlinePlayers()) {
            senders.add(onlinePlayer);
        }

        senders.add(FCHytaleUtil.getConsoleSender());

        fancyText.send(senders.toArray(new FCommandSender[0]));
    }
}
