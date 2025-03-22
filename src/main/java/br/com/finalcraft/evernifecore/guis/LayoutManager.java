package br.com.finalcraft.evernifecore.guis;

import br.com.finalcraft.evernifecore.gui.layout.FCLayoutScanner;
import br.com.finalcraft.evernifecore.guis.loyalt.OredictViewerLayout;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;

public class LayoutManager {

    public static OredictViewerLayout OREDICT_VIEWER_LAYOUT;

    public static void initialize(){
        if (FCBukkitUtil.isForge()){
            OREDICT_VIEWER_LAYOUT = FCLayoutScanner.loadLayout(OredictViewerLayout.class);
        }
    }

}
