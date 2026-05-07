package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import jakarta.annotation.Nonnull;

public interface IHasLayout<LB extends LayoutBase> {

    @Nonnull LB layout();

}
