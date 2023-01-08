package br.com.finalcraft.evernifecore.gui.layout;

import org.jetbrains.annotations.NotNull;

public interface IHasLayout<LB extends LayoutBase> {

    @NotNull LB layout();

}
