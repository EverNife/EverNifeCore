package br.com.finalcraft.evernifecore.dynamiccommand;

import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import org.apache.commons.lang.Validate;
import org.bukkit.command.CommandSender;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class DynamicCommand {

    private final UUID uuid;
    private final String identifier;
    private final Cooldown cooldown;
    private final Consumer<DynamicCommand.Context> action;
    private final Function<DynamicCommand.Context, Boolean> shouldRun;
    private final Function<DynamicCommand.Context, Boolean> shouldRemove;
    private int runs = 0;

    public DynamicCommand(UUID uuid, String identifier, Cooldown cooldown, Consumer<DynamicCommand.Context> action, Function<Context, Boolean> shouldRun, Function<Context, Boolean> shouldRemove) {
        this.uuid = uuid;
        this.identifier = identifier;
        this.cooldown = cooldown;
        this.action = action;
        this.shouldRun = shouldRun;
        this.shouldRemove = shouldRemove;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void runAction(CommandSender sender) {
        action.accept(new DynamicCommand.Context(sender, this));
    }

    public Cooldown getCooldown() {
        return cooldown;
    }

    public boolean shouldRemove(CommandSender sender) {
        return shouldRemove.apply(new DynamicCommand.Context(sender, this));
    }

    public boolean shouldRun(CommandSender sender) {
        return shouldRun.apply(new DynamicCommand.Context(sender, this));
    }

    public int getRuns() {
        return runs;
    }

    public void incrementRun(){
        this.runs++;
    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder {
        private UUID uuid;
        private String identifier;
        private Cooldown cooldown;
        private Consumer<DynamicCommand.Context> action;
        private Function<DynamicCommand.Context, Boolean> shouldRun;
        private Function<DynamicCommand.Context, Boolean> shouldRemove;

        protected Builder() {
            this.uuid = UUID.randomUUID();
            this.identifier = "";
            this.cooldown = new Cooldown.GenericCooldown("");
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

        public Builder setShouldRun(Function<DynamicCommand.Context, Boolean> shouldRun) {
            this.shouldRun = shouldRun;
            return this;
        }

        public Builder setShouldRemove(Function<DynamicCommand.Context, Boolean> shouldRemove) {
            this.shouldRemove = shouldRemove;
            return this;
        }

        public DynamicCommand createDynamicCommand() {
            Validate.notNull(action, "Action cannot be null");
            return new DynamicCommand(uuid, identifier, cooldown, action, shouldRun, shouldRemove);
        }
    }

    public static class Context{
        private CommandSender sender;
        private DynamicCommand dynamicCommand;

        protected Context(CommandSender sender, DynamicCommand dynamicCommand) {
            this.sender = sender;
            this.dynamicCommand = dynamicCommand;
        }

        public CommandSender getSender() {
            return sender;
        }

        public DynamicCommand getDynamicCommand() {
            return dynamicCommand;
        }
    }
}
