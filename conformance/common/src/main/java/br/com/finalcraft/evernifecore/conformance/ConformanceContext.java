package br.com.finalcraft.evernifecore.conformance;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;

import java.io.File;

/** What a suite may ask of the run that invoked it, without naming the platform. */
public final class ConformanceContext {

    private final FPlayer player;
    private final File dataFolder;
    private final String versionLabel;

    public ConformanceContext(FPlayer player, File dataFolder, String versionLabel) {
        this.player = player;
        this.dataFolder = dataFolder;
        this.versionLabel = versionLabel;
    }

    /** The {@code --player} of the command; a case that needs one answers {@link #NEEDS_A_PLAYER} without it. */
    public FPlayer getPlayerOrNull() {
        return player;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    /** Only for the human summary line. */
    public String getVersionLabel() {
        return versionLabel;
    }

    public static final String NEEDS_A_PLAYER = "needs a player";
}
