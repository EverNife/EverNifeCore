package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.gui.builders.ComplexGuiBuilder;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import dev.triumphteam.gui.builder.gui.BaseGuiBuilder;
import dev.triumphteam.gui.builder.gui.PaginatedBuilder;
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

    public static PaginatedBuilder paginated(){
        return new PaginatedBuilder();
    }

    public static <B extends BaseGuiBuilder> B from(Class<B> classBuilder){
        if (classBuilder == SimpleBuilder.class) return (B) simple();
        if (classBuilder == ComplexGuiBuilder.class) return (B) complex();
        if (classBuilder == StorageBuilder.class) return (B) storage();
        if (classBuilder == PaginatedBuilder.class) return (B) paginated();

        return FCReflectionUtil.getConstructor(classBuilder).invoke();
    }

}
