package br.com.finalcraft.evernifecore.api.platoverride.player;


import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;

public abstract class FPlayerAdapter<DELEGATE> extends MinecraftFPlayer<DELEGATE> {

    public FPlayerAdapter(DELEGATE delegate) {
        super(delegate);
    }

}
