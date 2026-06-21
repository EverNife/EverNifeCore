package br.com.finalcraft.evernifecore.minecraft.api.events;

import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.minecraft.api.events.base.ECPlayerDataEvent;

public class ECFullyLoggedInEvent extends ECPlayerDataEvent {

    private final boolean authMeLogin;

    public ECFullyLoggedInEvent(PlayerData playerData, boolean authMeLogin) {
        super(playerData);
        this.authMeLogin = authMeLogin;
    }

    public boolean isAuthMeLogin() {
        return authMeLogin;
    }

}
