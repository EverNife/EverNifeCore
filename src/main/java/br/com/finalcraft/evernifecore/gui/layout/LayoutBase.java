package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.config.Config;
import dev.triumphteam.gui.builder.gui.BaseGuiBuilder;
import dev.triumphteam.gui.builder.gui.SimpleBuilder;
import dev.triumphteam.gui.guis.Gui;

import java.util.ArrayList;
import java.util.List;

public abstract class LayoutBase {

    private List<LayoutIcon> layoutIcons = new ArrayList<>();
    private List<LayoutIcon> backgroundIcons = new ArrayList<>();

    protected Config config = null; //Populated on the Scanner
    protected String title = "➲  §0§l%layout_name%"; //Populated on the Scanner
    protected int rows = 6; //Populated on the Scanner
    protected Class<? extends BaseGuiBuilder> guiBuilder = SimpleBuilder.class; //Populated on the Scanner
    protected boolean integrateToPAPI = false; //Populated on the Scanner

    public LayoutBase() {
        //Execute "PRIOR" ALL Layout AND configs has been loaded
    }

    // =================================================================================================================
    protected void onLayoutLoad(){
        //Execute "AFTER" ALL Layout AND configs has been loaded
    }

    // =================================================================================================================

    public Class<? extends BaseGuiBuilder> getGuiBuilder() {
        return guiBuilder;
    }

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

    public int getRows() {
        return rows;
    }

    public boolean isIntegrateToPAPI() {
        return integrateToPAPI;
    }
}
