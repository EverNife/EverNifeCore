package br.com.finalcraft.evernifecore.gui.builders;

import br.com.finalcraft.evernifecore.gui.custom.PaginatedGuiComplex;
import dev.triumphteam.gui.builder.gui.BaseGuiBuilder;
import dev.triumphteam.gui.components.GuiType;
import dev.triumphteam.gui.guis.Gui;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class PaginatedComplexGuiBuilder extends BaseGuiBuilder<PaginatedGuiComplex, PaginatedComplexGuiBuilder> {

    private GuiType guiType;

    /**
     * Main constructor
     *
     * @param guiType The {@link GuiType} to default to
     */
    public PaginatedComplexGuiBuilder(final GuiType guiType) {
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
    public PaginatedComplexGuiBuilder type(final GuiType guiType) {
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
    public PaginatedGuiComplex create() {
        final PaginatedGuiComplex gui;
        final String title = getTitle();
        if (guiType == GuiType.CHEST) {
            gui = new PaginatedGuiComplex(getRows(), title, getModifiers());
        } else {
            gui = new PaginatedGuiComplex(guiType, title, getModifiers());
        }

        final Consumer<PaginatedGuiComplex> consumer = getConsumer();
        if (consumer != null) consumer.accept(gui);

        return gui;
    }



}
