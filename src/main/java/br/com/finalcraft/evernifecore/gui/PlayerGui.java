package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.gui.layout.IHasLayout;
import br.com.finalcraft.evernifecore.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.gui.layout.LayoutIcon;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import dev.triumphteam.gui.builder.gui.BaseGuiBuilder;
import dev.triumphteam.gui.guis.BaseGui;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class PlayerGui<P extends IPlayerData, G extends BaseGui> {

    private Player player = null;
    private P playerData = null;
    private G gui = null;
    private transient PlayerGui previousGui = null;

    public PlayerGui() {

    }

    public PlayerGui(P playerData) {
        this.player = playerData == null ? null : playerData.getPlayer();
        this.playerData = playerData;
    }

    public PlayerGui(P playerData, G gui) {
        this.player = playerData == null ? null : playerData.getPlayer();
        this.playerData = playerData;
        this.gui = gui;
    }

    public PlayerGui(G gui) {
        this.gui = gui;
    }

    protected void setupLayout(@NotNull IHasLayout iHasLayout){
        if (iHasLayout.layout() == null) throw new NullPointerException(String.format("The layout() method for the class [%s] returned a null value!", this.getClass().getSimpleName()));
        this.setupLayout(iHasLayout.layout(), FCGuiFactory.from(iHasLayout.layout().getGuiBuilder()));
    }

    protected <B extends BaseGuiBuilder<G,?>, LB extends LayoutBase> void setupLayout(@NotNull LB layoutBase, @NotNull B baseBuilder){
        if (layoutBase == null) throw new NullPointerException(String.format("The layoutBase cannot be null!", this.getClass().getSimpleName()));

        CompoundReplacer compoundReplacer = this.getReplacer();
        setupLayout(layoutBase, compoundReplacer, () -> {
            String title = compoundReplacer.apply(layoutBase.getTitle());

            return baseBuilder.rows(layoutBase.getRows())
                    .title(title)
                    .disableAllInteractions()
                    .create();
        });
    }

    protected <LB extends LayoutBase> void setupLayout(@NotNull LB layoutBase, @NotNull CompoundReplacer compoundReplacer, @NotNull Supplier<G> guiBuilder){
        if (layoutBase == null) throw new NullPointerException(String.format("The layoutBase cannot be null!", this.getClass().getSimpleName()));
        if (compoundReplacer == null) throw new NullPointerException(String.format("The compoundReplacer cannot be null!", this.getClass().getSimpleName()));

        setGui(guiBuilder.get());

        //Set Background
        for (LayoutIcon backgroundIcon : layoutBase.getBackgroundIcons()) {
            backgroundIcon.parse(compoundReplacer).applyTo(this);
        }
    }

    protected PlayerGui<P, G> setGui(G gui) {
        this.gui = gui;
        return this;
    }

    protected PlayerGui<P, G> setPlayer(Player player) {
        this.player = player;
        return this;
    }

    protected PlayerGui<P, G> setPlayerData(P playerData) {
        this.playerData = playerData;
        this.player = playerData == null ? null : playerData.getPlayer();
        return this;
    }

    @NotNull
    public CompoundReplacer getReplacer() {
        LayoutBase layoutBase = (this instanceof IHasLayout) ? ((IHasLayout) this).layout() : null;
        if (layoutBase != null && layoutBase.isIntegrateToPAPI() && this.getPlayer() != null){
            return new CompoundReplacer().usePAPI(getPlayer());
        }
        return new CompoundReplacer();
    }

    public P getPlayerData() {
        return playerData;
    }

    public Player getPlayer() {
        return player;
    }

    public G getGui() {
        return gui;
    }

    public void open(){
        this.getGui().open(getPlayer());
    }

    public void open(PlayerGui previousGui){
        this.previousGui = previousGui;
        this.open();
    }

    public void close(){
        this.getGui().close(getPlayer());
    }

    public PlayerGui getPreviousGui() {
        return previousGui;
    }
}
