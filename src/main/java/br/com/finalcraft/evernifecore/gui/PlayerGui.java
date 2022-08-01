package br.com.finalcraft.evernifecore.gui;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.gui.layout.IHasLayout;
import br.com.finalcraft.evernifecore.gui.layout.LayoutIcon;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import dev.triumphteam.gui.builder.gui.BaseGuiBuilder;
import dev.triumphteam.gui.builder.gui.SimpleBuilder;
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

    @Deprecated
    public PlayerGui(Player player, P playerData, G gui) {
        this.player = player;
        this.playerData = playerData;
        this.gui = gui;
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

    protected void setupLayout(IHasLayout iHasLayout){
        this.setupLayout(iHasLayout, (Class<? extends BaseGuiBuilder<G,?>>) (Object) SimpleBuilder.class);
    }

    protected <B extends BaseGuiBuilder<G,?>> void setupLayout(IHasLayout iHasLayout, Class<B> baseBuilder){
        CompoundReplacer compoundReplacer = this.getReplacer();

        setupLayout(iHasLayout, compoundReplacer, () -> {
            String title = compoundReplacer.apply(iHasLayout.layout().getTitle());

            return FCGuiFactory.from(baseBuilder)
                    .rows(iHasLayout.layout().getRows())
                    .title(title)
                    .disableAllInteractions()
                    .create();
        });
    }

    protected void setupLayout(IHasLayout iHasLayout, CompoundReplacer compoundReplacer, Supplier<G> guiBuilder){
        setGui(guiBuilder.get());

        //Set Background
        for (LayoutIcon backgroundIcon : iHasLayout.layout().getBackgroundIcons()) {
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
        IHasLayout iHasLayout = this instanceof IHasLayout ? (IHasLayout) this : null;
        if (iHasLayout != null && iHasLayout.layout().isIntegrateToPAPI() && this.getPlayer() != null){
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
