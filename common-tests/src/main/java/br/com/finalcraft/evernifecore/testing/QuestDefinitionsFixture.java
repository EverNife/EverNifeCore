package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestDefinitions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The example quests of the quest engine's reference, as YAML files under {@code quests/} on the classpath, read for the
 * plugin a test names: {@code mine-iron} (linear, gate, requires {@code tutorial}), {@code tutorial},
 * {@code daily-zombies} (a daily pool member), {@code lab-catch} (a param drawn at random over a plugin's own type),
 * {@code arena-night} (a group quest in the region {@code arena}, with a window, a fail-when and rewards for completion,
 * the window's end and the party's end) and {@code arena-warmup} (reach a point, stay in {@code arena}, cross three
 * {@code checkpoint}s; idling 5 min in {@code lobby} fails it). Region names are bare: the regions belong to the plugin
 * the quest is read for.
 */
public final class QuestDefinitionsFixture {

    private QuestDefinitionsFixture() {
    }

    /** The YAML text of the fixture {@code name}. */
    public static String yaml(String name) {
        String path = "quests/" + name + ".yml";
        try (InputStream in = QuestDefinitionsFixture.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("No quest fixture '" + name + "': the fixtures are mine-iron, tutorial, daily-zombies, lab-catch, arena-night and arena-warmup.");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int read; (read = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The fixture {@code name} read as a quest of {@code owner}. */
    public static QuestDefinition load(ECPluginData owner, String name) {
        return QuestDefinitions.parseYaml(owner, yaml(name));
    }
}
