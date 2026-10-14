package br.com.finalcraft.evernifecore.hytale.loader.imp;

import br.com.finalcraft.evernifecore.region.RegionId;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hytale names a plugin {@code Group:Name}; the core takes the bare {@code Name}, because that name becomes a
 * region and flag owner, where a {@code :} would split {@code owner:name} in two.
 */
class HyPluginMetaInfoTest {

    @Test
    void aGroupedPluginIsNamedByItsManifestNameAlone() {
        JavaPlugin plugin = pluginIdentifiedAs(new PluginIdentifier("EverNife", "EverNifeCore"));
        assertEquals("EverNife:EverNifeCore", plugin.getName(), "the server's own name keeps the group");

        String name = new HyPluginMetaInfo(plugin).getName();

        assertEquals("EverNifeCore", name);
        assertEquals(name, new HyECPluginExtractor().getPluginName(plugin), "the extractor keys the plugin by the same name");
        assertEquals("evernifecore:spawn", RegionId.of(name, "spawn").toString());
    }

    private static JavaPlugin pluginIdentifiedAs(PluginIdentifier identifier) {
        try {
            Constructor<?> allocator = ReflectionFactory.getReflectionFactory()
                    .newConstructorForSerialization(NamedJavaPlugin.class, Object.class.getDeclaredConstructor());
            JavaPlugin plugin = (JavaPlugin) allocator.newInstance();
            Field identifierField = PluginBase.class.getDeclaredField("identifier");
            identifierField.setAccessible(true);
            identifierField.set(plugin, identifier);
            return plugin;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not build a JavaPlugin carrying the identifier " + identifier
                    + " - PluginBase may have renamed its 'identifier' field.", e);
        }
    }

    private static final class NamedJavaPlugin extends JavaPlugin {
        private NamedJavaPlugin(JavaPluginInit init) {
            super(init);
        }
    }
}
