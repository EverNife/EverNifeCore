package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.gui.builders.ComplexGuiBuilder;
import dev.triumphteam.gui.builder.gui.SimpleBuilder;
import dev.triumphteam.gui.builder.gui.StorageBuilder;
import dev.triumphteam.gui.components.GuiType;
import dev.triumphteam.gui.guis.Gui;

public class FCGuiFactory {
    
    public static SimpleBuilder simple(){
        return Gui.gui();
    }

    public static ComplexGuiBuilder complex(){
        return new ComplexGuiBuilder(GuiType.CHEST);
    }

    public static StorageBuilder storage(){
        return new StorageBuilder();
    }

}
