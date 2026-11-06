package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.logger.ECLogLevel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A plugin taking its own commands off the map (every stop) is routine; taking another's is worth a warning. */
class McPlatformCommandRemovalTest {

    private final Plugin self = plugin();
    private final Plugin other = plugin();

    @Test
    void removingTheNotifyingPluginsOwnCommandIsDebug() {
        assertEquals(ECLogLevel.DEBUG, McPlatform.removalLevel(commandOf(self), self));
    }

    @Test
    void removingAnotherPluginsOrAVanillaCommandStaysAWarning() {
        assertEquals(ECLogLevel.WARNING, McPlatform.removalLevel(commandOf(other), self));
        assertEquals(ECLogLevel.WARNING, McPlatform.removalLevel(new Command("me") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return true;
            }
        }, self));
    }

    private static Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> method.getName().equals("equals") ? proxy == args[0]
                        : method.getName().equals("hashCode") ? System.identityHashCode(proxy) : null);
    }

    private static Command commandOf(Plugin owner) {
        abstract class Owned extends Command implements PluginIdentifiableCommand {
            Owned() {
                super("duel");
            }
        }
        return new Owned() {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return true;
            }

            @Override
            public Plugin getPlugin() {
                return owner;
            }
        };
    }
}
