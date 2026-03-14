package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.gui.layout.IHasLayout;
import br.com.finalcraft.evernifecore.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.gui.layout.LayoutIcon;
import br.com.finalcraft.evernifecore.placeholder.FCRegexReplacers;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import dev.triumphteam.gui.builder.gui.BaseGuiBuilder;
import dev.triumphteam.gui.guis.BaseGui;
import jakarta.annotation.Nonnull;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

public class PlayerGui<P extends IPlayerData, G extends BaseGui> {

    private Player player = null;
    private P playerData = null;
    private G gui = null;

    private transient PlayerGui previousGui = null;
    private transient Player viewer = null;

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

    protected void setupLayout(@Nonnull IHasLayout iHasLayout){
        if (iHasLayout.layout() == null) throw new NullPointerException(String.format("The layout() method for the class [%s] returned a null value!", this.getClass().getSimpleName()));
        this.setupLayout(iHasLayout.layout(), FCGuiFactory.from(iHasLayout.layout().getGuiBuilder()));
    }

    protected <B extends BaseGuiBuilder<G,?>, LB extends LayoutBase> void setupLayout(@Nonnull LB layoutBase, @Nonnull B baseBuilder){
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

    protected <LB extends LayoutBase> void setupLayout(@Nonnull LB layoutBase, @Nonnull CompoundReplacer compoundReplacer, @Nonnull Supplier<G> guiBuilder){
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

    @Nonnull
    public CompoundReplacer getReplacer() {

        CompoundReplacer compoundReplacer = new CompoundReplacer();

        if (shouldApplyPlayerDataReplacer()){
            compoundReplacer.appendReplacer(FCRegexReplacers.PLAYER_DATA.compound(getPlayerData()));
        }

        LayoutBase layoutBase = (this instanceof IHasLayout) ? ((IHasLayout) this).layout() : null;
        if (layoutBase != null && layoutBase.isIntegrateToPAPI() && this.getPlayer() != null){
            compoundReplacer.usePAPI(getPlayer());
        }

        return compoundReplacer;
    }

    protected boolean shouldApplyPlayerDataReplacer(){
        return getPlayerData() != null;
    }

    public P getPlayerData() {
        return playerData;
    }

    public Player getPlayer() {
        return player;
    }

    public Player getViewer(){
        return viewer != null ? viewer : player;
    }

    public void setViewer(Player viewer) {
        this.viewer = viewer;
    }

    public G getGui() {
        return gui;
    }

    public void open(){
        this.getGui().open(getViewer());
    }

    public void open(PlayerGui previousGui){
        this.previousGui = previousGui;
        this.open();
    }

    public void close(){
        this.getGui().close(getViewer());
    }

    public PlayerGui getPreviousGui() {
        return previousGui;
    }
}
