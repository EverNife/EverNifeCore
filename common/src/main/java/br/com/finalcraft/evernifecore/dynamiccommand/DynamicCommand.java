package br.com.finalcraft.evernifecore.dynamiccommand;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.Validate;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A one-off command behind a clickable link ({@code /ecdcmd <uuid>}), owned by the plugin that made it: the link stops
 * working when that plugin leaves, and its action runs in that plugin's language.
 */
public class DynamicCommand {

    private final ECPluginData owner;
    private final UUID uuid;
    private final String identifier;
    private final Cooldown cooldown;
    private final Consumer<Context> action;
    private final Function<Context, Boolean> shouldRun;
    private final Function<Context, Boolean> shouldRemove;
    private int runs = 0;

    public DynamicCommand(@Nonnull ECPluginData owner, UUID uuid, String identifier, Cooldown cooldown, Consumer<Context> action, Function<Context, Boolean> shouldRun, Function<Context, Boolean> shouldRemove) {
        this.owner = Objects.requireNonNull(owner, "'owner' cannot be null for a DynamicCommand!");
        this.uuid = uuid;
        this.identifier = identifier;
        this.cooldown = cooldown;
        this.action = action;
        this.shouldRun = shouldRun;
        this.shouldRemove = shouldRemove;
    }

    /** The plugin that made this link; it leaves with that plugin. */
    @Nonnull
    public ECPluginData getOwner() {
        return owner;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void runAction(FCommandSender sender) {
        action.accept(new Context(sender, this));
    }

    public Cooldown getCooldown() {
        return cooldown;
    }

    public boolean shouldRemove(FCommandSender sender) {
        return shouldRemove.apply(new Context(sender, this));
    }

    public boolean shouldRun(FCommandSender sender) {
        return shouldRun.apply(new Context(sender, this));
    }

    public int getRuns() {
        return runs;
    }

    public String scheduleAndReturnCommandString(){
        return DynamicCommandManager.scheduleDynamicCommand(this);
    }

    public void incrementRun(){
        this.runs++;
    }

    /** A builder for a link owned by {@code owner}. */
    public static Builder builder(@Nonnull ECPluginData owner){
        return new Builder(owner);
    }

    public static class Builder {
        private final ECPluginData owner;
        private UUID uuid;
        private String identifier;
        private Cooldown cooldown;
        private Consumer<Context> action;
        private Function<Context, Boolean> shouldRun;
        private Function<Context, Boolean> shouldRemove;

        protected Builder(@Nonnull ECPluginData owner) {
            this.owner = Objects.requireNonNull(owner, "'owner' cannot be null for a DynamicCommand!");
            this.uuid = UUID.randomUUID();
            this.identifier = "";
            this.cooldown = Cooldown.inMemory("");
            this.cooldown.setDuration(1200);//20 min
            this.action = null;
            this.shouldRun = context -> true; //By Default, run for anyone
            this.shouldRemove = context -> true; //By Default, run only once
        }

        public Builder setUuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder setIdentifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder setAction(Consumer<Context> action) {
            this.action = action;
            return this;
        }

        public Builder setCooldown(Cooldown cooldown) {
            this.cooldown = cooldown;
            return this;
        }

        public Builder setCooldown(long seconds) {
            this.cooldown.setDuration(TimeUnit.SECONDS.toMillis(seconds));
            return this;
        }

        public Builder setShouldRun(Function<Context, Boolean> shouldRun) {
            this.shouldRun = shouldRun;
            return this;
        }

        public Builder setShouldRemove(Function<Context, Boolean> shouldRemove) {
            this.shouldRemove = shouldRemove;
            return this;
        }

        public Builder setRunOnlyOnce(boolean runOnlyOnce) {
            this.shouldRemove = context -> runOnlyOnce;
            return this;
        }

        public DynamicCommand createDynamicCommand() {
            Validate.notNull(action, "Action cannot be null");
            return new DynamicCommand(owner, uuid, identifier, cooldown, action, shouldRun, shouldRemove);
        }
    }

    public static class Context{
        private FCommandSender sender;
        private DynamicCommand dynamicCommand;

        protected Context(FCommandSender sender, DynamicCommand dynamicCommand) {
            this.sender = sender;
            this.dynamicCommand = dynamicCommand;
        }

        public FCommandSender getSender() {
            return sender;
        }

        public DynamicCommand getDynamicCommand() {
            return dynamicCommand;
        }
    }
}
