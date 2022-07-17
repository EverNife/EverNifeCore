package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.config.Config;

import java.util.ArrayList;
import java.util.List;

public abstract class LayoutBase {

    private List<LayoutIcon> layoutIcons = new ArrayList<>();
    private List<LayoutIcon> backgroundIcons = new ArrayList<>();
    protected Config config = null; //Populated on the Scanner

    public List<LayoutIcon> getLayoutIcons() {
        return layoutIcons;
    }

    public List<LayoutIcon> getBackgroundIcons() {
        return backgroundIcons;
    }

    public Config getConfig() {
        return config;
    }
}
