package br.com.finalcraft.evernifecore.gui.layout;

import javax.annotation.Nonnull;

public interface IHasLayout<LB extends LayoutBase> {

    @Nonnull LB layout();

}
