package br.com.finalcraft.evernifecore.notify;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;

import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What to tell a player, ready for any channel: each channel reads the parts it shows (the action bar the text, the
 * title the title and subtitle, the boss bar the text and the progress...). Immutable; built by
 * {@link #of(ECPluginData, NotifyKind)}. The plugin that sends it owns it: its id is scoped to that plugin, and its kind's
 * chain and duration come from that plugin's {@link NotifyProfile}. Placeholders are resolved before building it - the
 * {@link Notifier} reads no placeholder.
 */
public final class Notification {

    private final ECPluginData owner;
    private final NotifyKind kind;
    private final @Nullable String id;
    private final FancyText text;
    private final @Nullable FancyText title;
    private final @Nullable FancyText subtitle;
    private final List<FancyText> lines;
    private final List<TrackerLine> trackerLines;
    private final @Nullable Float progress;
    private final @Nullable Duration duration;
    private final int priority;
    private final @Nullable String sound;
    private final @Nullable String category;
    private final @Nullable NotifyChain channels;

    private Notification(Builder builder) {
        this.owner = builder.owner;
        this.kind = builder.kind;
        this.id = builder.id;
        this.text = builder.text;
        this.title = builder.title;
        this.subtitle = builder.subtitle;
        this.lines = Collections.unmodifiableList(new ArrayList<>(builder.lines));
        this.trackerLines = Collections.unmodifiableList(new ArrayList<>(builder.trackerLines));
        this.progress = builder.progress;
        this.duration = builder.duration;
        this.priority = builder.priority;
        this.sound = builder.sound;
        this.category = builder.category;
        this.channels = builder.channels;
    }

    /** A notification {@code owner} sends; {@code kind} picks the chain and duration of {@code owner}'s {@link NotifyProfile}. */
    public static Builder of(ECPluginData owner, NotifyKind kind) {
        return new Builder(owner, kind);
    }

    /**
     * The per-player key the channels and the platform keep {@code owner}'s notification {@code id} under: two plugins
     * that show the same id never touch each other's. A null id is {@code owner}'s anonymous one.
     */
    public static String handleOf(ECPluginData owner, @Nullable String id) {
        return owner.getMetaInfo().getName().toLowerCase(Locale.ROOT) + ":" + (id == null ? "" : id);
    }

    /** The plugin that sends it. */
    public ECPluginData getOwner() {
        return owner;
    }

    public NotifyKind getKind() {
        return kind;
    }

    /** What names it within its owner (a boss bar, a tracker, a queued action bar or title); null is anonymous. */
    public @Nullable String getId() {
        return id;
    }

    /** {@link #handleOf}{@code (getOwner(), getId())}: the key the channels and the platform keep it under. */
    public String getHandle() {
        return handleOf(owner, id);
    }

    /** Chat, action bar, boss bar and toast description; empty when not set. */
    public FancyText getText() {
        return text;
    }

    /** The title, the toast title and the tracker and sidebar heading; null means those channels use {@link #getText()}. */
    public @Nullable FancyText getTitle() {
        return title;
    }

    /** {@link #getTitle()}, or {@link #getText()} when there is none. */
    public FancyText getTitleOrText() {
        return title != null ? title : text;
    }

    public @Nullable FancyText getSubtitle() {
        return subtitle;
    }

    /** Sidebar lines, and the tracker lines when {@link #getTrackerLines()} is empty. */
    public List<FancyText> getLines() {
        return lines;
    }

    /** Tracker lines with their counts; empty means the tracker shows {@link #getLines()}. */
    public List<TrackerLine> getTrackerLines() {
        return trackerLines;
    }

    /** The boss bar's fill, 0..1; null is a full bar. */
    public @Nullable Float getProgress() {
        return progress;
    }

    /**
     * How long the action bar, the title and the boss bar stay: what the builder set, else the kind's duration in the
     * owner's {@link NotifyProfile}, read now. {@link Duration#ZERO} stays until cleared.
     */
    public Duration getDuration() {
        return duration != null ? duration : NotifyProfile.of(owner).getDuration(kind);
    }

    /** The action bar and title queues show the highest priority; the same id replaces. */
    public int getPriority() {
        return priority;
    }

    /** A platform sound id or an {@link HudSounds} alias; null plays nothing. */
    public @Nullable String getSound() {
        return sound;
    }

    /** A tag for a future inbox; the core never reads it. */
    public @Nullable String getCategory() {
        return category;
    }

    /** The chain set on this notification; null means the kind's in the owner's profile ({@link Notifier#getChain}). */
    public @Nullable NotifyChain getChannels() {
        return channels;
    }

    @Override
    public String toString() {
        return kind + "[" + getHandle() + "]" + " '" + text.getText() + "'";
    }

    public static final class Builder {

        //50 ms per game tick
        private static final long MILLIS_PER_TICK = 50L;

        private final ECPluginData owner;
        private final NotifyKind kind;
        private @Nullable String id;
        private FancyText text = FancyText.of("");
        private @Nullable FancyText title;
        private @Nullable FancyText subtitle;
        private final List<FancyText> lines = new ArrayList<>();
        private final List<TrackerLine> trackerLines = new ArrayList<>();
        private @Nullable Float progress;
        private @Nullable Duration duration;
        private int priority;
        private @Nullable String sound;
        private @Nullable String category;
        private @Nullable NotifyChain channels;

        private Builder(ECPluginData owner, NotifyKind kind) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.kind = Objects.requireNonNull(kind, "kind");
        }

        /** Scoped to the owner: another plugin's notification with the same id is another one. */
        public Builder id(@Nullable String id) {
            this.id = id;
            return this;
        }

        public Builder text(FancyText text) {
            this.text = Objects.requireNonNull(text, "text");
            return this;
        }

        public Builder text(String text) {
            return text(FancyText.of(text));
        }

        public Builder title(@Nullable FancyText title) {
            this.title = title;
            return this;
        }

        public Builder title(String title) {
            return title(FancyText.of(title));
        }

        public Builder subtitle(@Nullable FancyText subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public Builder subtitle(String subtitle) {
            return subtitle(FancyText.of(subtitle));
        }

        public Builder lines(List<FancyText> lines) {
            this.lines.clear();
            this.lines.addAll(lines);
            return this;
        }

        public Builder trackerLines(List<TrackerLine> trackerLines) {
            this.trackerLines.clear();
            this.trackerLines.addAll(trackerLines);
            return this;
        }

        /** @throws IllegalArgumentException when outside 0..1 */
        public Builder progress(@Nullable Float progress) {
            if (progress != null && (progress < 0f || progress > 1f)) {
                throw new IllegalArgumentException("A boss bar progress is a fraction from 0 to 1, not " + progress + ".");
            }
            this.progress = progress;
            return this;
        }

        /** {@link Duration#ZERO} stays until cleared. */
        public Builder duration(Duration duration) {
            if (duration.isNegative()) {
                throw new IllegalArgumentException("A notification cannot last " + duration + ": use Duration.ZERO to keep it until cleared.");
            }
            this.duration = duration;
            return this;
        }

        /** {@link #duration(Duration)} in game ticks, 20 to the second. */
        public Builder ticks(int ticks) {
            return duration(Duration.ofMillis(MILLIS_PER_TICK * ticks));
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder sound(@Nullable String sound) {
            this.sound = sound;
            return this;
        }

        public Builder category(@Nullable String category) {
            this.category = category;
            return this;
        }

        /** The chain to use instead of the kind's: {@code channels("title+sound", "chat")}. */
        public Builder channels(String... steps) {
            this.channels = NotifyChain.parse(Arrays.asList(steps));
            return this;
        }

        public Builder channels(@Nullable NotifyChain chain) {
            this.channels = chain;
            return this;
        }

        public Notification build() {
            return new Notification(this);
        }
    }
}
