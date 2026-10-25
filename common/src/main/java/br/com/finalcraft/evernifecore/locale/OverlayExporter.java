package br.com.finalcraft.evernifecore.locale;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everyconfig.config.Config;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;

/** Writes the overlay file a translator fills, for one plugin and one locale. */
public final class OverlayExporter {

    private OverlayExporter() {
    }

    /**
     * Writes {@code <data>/localization/overlay/<LOCALE>.yml} of {@code owner}: every text path of every registered
     * definition that the file does not have yet, valued with the text that locale reads today without the file (its
     * inline entry, else the plugin's language, else the author's text) - what the translator replaces. A value
     * already in the file is never touched. The overlay is re-read afterwards. Blocking file I/O.
     *
     * @throws IllegalStateException when {@code owner} registered no definition, so there is nothing to export
     */
    public static Report export(ECPluginData owner, String locale) {
        LocaleOverlay overlay = LocaleOverlay.of(owner);
        if (overlay.getDefinitionIds().isEmpty()) {
            throw new IllegalStateException(owner.getMetaInfo().getName() + " registered no definition in LocaleOverlay,"
                    + " so there is nothing to export. The plugin registers one with LocaleOverlay.of(plugin).register(id, root).");
        }
        File file = overlay.getFile(locale);
        file.getParentFile().mkdirs();
        Config config = ConfigFactory.open(file);
        //flat dotted keys: written on the root itself, the dotted-path API would nest them
        ObjectNode root = config.getRoot();
        int added = 0;
        int kept = 0;
        for (String definitionId : overlay.getDefinitionIds()) {
            for (String path : overlay.textPaths(definitionId)) {
                String key = definitionId + "." + path;
                if (root.has(key)) {
                    kept++;
                } else {
                    root.set(key, overlay.seed(definitionId, path, locale).deepCopy());
                    added++;
                }
            }
        }
        if (added > 0) {
            config.save();
        }
        overlay.reload();
        return new Report(added, kept, file);
    }

    public static final class Report {
        private final int added;
        private final int kept;
        private final File file;

        Report(int added, int kept, File file) {
            this.added = added;
            this.kept = kept;
            this.file = file;
        }

        /** Paths written now, with the text to translate. */
        public int getAdded() {
            return added;
        }

        /** Paths the file already had, left as they were. */
        public int getKept() {
            return kept;
        }

        public File getFile() {
            return file;
        }
    }
}
