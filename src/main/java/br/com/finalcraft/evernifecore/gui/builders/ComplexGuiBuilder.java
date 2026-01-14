package br.com.finalcraft.evernifecore.gui.builders;

import br.com.finalcraft.evernifecore.gui.custom.GuiComplex;
import dev.triumphteam.gui.builder.gui.BaseGuiBuilder;
import dev.triumphteam.gui.components.GuiType;
import dev.triumphteam.gui.guis.Gui;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public class ComplexGuiBuilder extends BaseGuiBuilder<GuiComplex, ComplexGuiBuilder> {

    private GuiType guiType;

    /**
     * Main constructor
     *
     * @param guiType The {@link GuiType} to default to
     */
    public ComplexGuiBuilder(@Nonnull final GuiType guiType) {
        this.guiType = guiType;
    }

    /**
     * Sets the {@link GuiType} to use on the GUI
     * This method is unique to the simple GUI
     *
     * @param guiType The {@link GuiType}
     * @return The current builder
     */
    @Nonnull
    @Contract("_ -> this")
    public ComplexGuiBuilder type(@Nonnull final GuiType guiType) {
        this.guiType = guiType;
        return this;
    }

    /**
     * Creates a new {@link Gui}
     *
     * @return A new {@link Gui}
     */
    @Nonnull
    @Override
    @Contract(" -> new")
    public GuiComplex create() {
        final GuiComplex gui;
        final String title = getTitle();
        if (guiType == GuiType.CHEST) {
            gui = new GuiComplex(getRows(), title, getModifiers());
        } else {
            gui = new GuiComplex(guiType, title, getModifiers());
        }

        final Consumer<GuiComplex> consumer = getConsumer();
        if (consumer != null) consumer.accept(gui);

        return gui;
    }



}
