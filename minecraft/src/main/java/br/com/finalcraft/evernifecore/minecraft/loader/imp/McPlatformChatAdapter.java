package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.minecraft.util.FCTextUtil;

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

}
