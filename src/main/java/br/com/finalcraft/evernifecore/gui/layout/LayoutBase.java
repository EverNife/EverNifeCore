package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.config.Config;

import java.util.ArrayList;
import java.util.List;

public abstract class LayoutBase {

    private List<LayoutIcon> layoutIcons = new ArrayList<>();
    private List<LayoutIcon> backgroundIcons = new ArrayList<>();
    protected Config config = null; //Populated on the Scanner
    protected String title = null; //Populated on the Scanner

    // =================================================================================================================
    protected void onLayoutLoad(){

    }

    protected String defaultTitle(){
        return "➲  §0§l%layout_name%";
    }
    // =================================================================================================================


    public List<LayoutIcon> getLayoutIcons() {
        return layoutIcons;
    }

    public List<LayoutIcon> getBackgroundIcons() {
        return backgroundIcons;
    }

    public Config getConfig() {
        return config;
    }

    public String getTitle() {
        return title;
    }
}
