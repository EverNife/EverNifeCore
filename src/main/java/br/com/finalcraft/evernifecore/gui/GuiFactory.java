package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.gui.custom.ComplexGui;

public class GuiFactory {

    public static void create(){

        ComplexGui complexGui = ComplexGui
                .complex()
                .disableAllInteractions()
                .title("Xablau")
                .rows(6)
                .create();



    }


}
