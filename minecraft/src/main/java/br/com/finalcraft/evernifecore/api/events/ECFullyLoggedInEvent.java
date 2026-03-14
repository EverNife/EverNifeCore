package br.com.finalcraft.evernifecore.api.events;

import br.com.finalcraft.evernifecore.api.events.base.ECPlayerDataEvent;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;

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
