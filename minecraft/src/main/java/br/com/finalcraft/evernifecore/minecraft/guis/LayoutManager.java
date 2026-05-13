package br.com.finalcraft.evernifecore.minecraft.guis;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.FCLayoutScanner;
import br.com.finalcraft.evernifecore.minecraft.guis.loyalt.OredictViewerLayout;
import br.com.finalcraft.evernifecore.minecraft.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;

public class LayoutManager {

    public static OredictViewerLayout OREDICT_VIEWER_LAYOUT;

    public static void initialize(){
        if (FCBukkitUtil.isModded()){
            try {
                //Right now, only load the layout if we have the right NMS
                NMSUtils.get().getOreRegistry();
                OREDICT_VIEWER_LAYOUT = FCLayoutScanner.loadLayout(OredictViewerLayout.class);
            }catch (Exception ignored){

            }
        }
    }

}
