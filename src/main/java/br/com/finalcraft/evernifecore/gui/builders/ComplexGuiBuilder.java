package br.com.finalcraft.evernifecore.gui.builders;

import br.com.finalcraft.evernifecore.gui.custom.ComplexGui;
import dev.triumphteam.gui.builder.gui.BaseGuiBuilder;
import dev.triumphteam.gui.components.GuiType;
import dev.triumphteam.gui.guis.Gui;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class ComplexGuiBuilder extends BaseGuiBuilder<ComplexGui, ComplexGuiBuilder> {

    private GuiType guiType;

    /**
     * Main constructor
     *
     * @param guiType The {@link GuiType} to default to
     */
    public ComplexGuiBuilder(@NotNull final GuiType guiType) {
        this.guiType = guiType;
    }

    /**
     * Sets the {@link GuiType} to use on the GUI
     * This method is unique to the simple GUI
     *
     * @param guiType The {@link GuiType}
     * @return The current builder
     */
    @NotNull
    @Contract("_ -> this")
    public ComplexGuiBuilder type(@NotNull final GuiType guiType) {
        this.guiType = guiType;
        return this;
    }

    /**
     * Creates a new {@link Gui}
     *
     * @return A new {@link Gui}
     */
    @NotNull
    @Override
    @Contract(" -> new")
    public ComplexGui create() {
        final ComplexGui gui;
        final String title = getTitle();
        if (guiType == null || guiType == GuiType.CHEST) {
            gui = new ComplexGui(getRows(), title, getModifiers());
        } else {
            gui = new ComplexGui(guiType, title, getModifiers());
        }

        final Consumer<ComplexGui> consumer = getConsumer();
        if (consumer != null) consumer.accept(gui);

        return gui;
    }



}
