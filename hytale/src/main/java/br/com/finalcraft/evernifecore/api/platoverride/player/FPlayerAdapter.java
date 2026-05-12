package br.com.finalcraft.evernifecore.api.platoverride.player;

import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;

public abstract class FPlayerAdapter<DELEGATE> extends HytaleFPlayer<DELEGATE> {

    public FPlayerAdapter(DELEGATE delegate) {
        super(delegate);
    }

}
