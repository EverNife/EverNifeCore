package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.terrain.JournalPersistence;
import br.com.finalcraft.evernifecore.region.terrain.TerrainContent;
import br.com.finalcraft.everyconfig.annotation.Comment;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** Settings of {@code track-changes}: where the journal lives and what it keeps. */
public class TrackChangesSettings extends FlagSettings {

    @Comment("MEMORY keeps the journal only while the server runs; DISK also writes it next to the owner's data, so it survives a restart.")
    @JsonProperty
    private JournalPersistence persistence = JournalPersistence.MEMORY;

    @Comment("Keep the blocks themselves.")
    @JsonProperty
    private boolean blocks = true;

    @Comment("Keep what a block holds (a chest's items, a sign's text).")
    @JsonProperty
    private boolean nbt = true;

    @Comment("Keep the non-player entities a covered action removes (armor stands, item frames, paintings).")
    @JsonProperty
    private boolean entities = true;

    public JournalPersistence getPersistence() {
        return persistence;
    }

    public TrackChangesSettings setPersistence(JournalPersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        return this;
    }

    @JsonIgnore
    public TerrainContent getContent() {
        return TerrainContent.of(blocks, nbt, entities);
    }

    @JsonIgnore
    public TrackChangesSettings setContent(TerrainContent content) {
        this.blocks = content.isBlocks();
        this.nbt = content.isNbt();
        this.entities = content.isEntities();
        return this;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TrackChangesSettings)) return false;
        TrackChangesSettings that = (TrackChangesSettings) other;
        return persistence == that.persistence && getContent().equals(that.getContent())
                && getTriggerInterval().equals(that.getTriggerInterval());
    }

    @Override
    public int hashCode() {
        return Objects.hash(persistence, getContent());
    }
}
