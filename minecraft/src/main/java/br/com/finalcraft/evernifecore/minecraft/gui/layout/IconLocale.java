package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.RegisteredPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.StandardParts;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The part of an {@link Icon} that changes with who is looking: its name and its lore, one block per
 * language.
 *
 * <p>Each block is written in the same item-data vocabulary the {@code DisplayItem} uses, restricted
 * to the two text keys - {@code name:} and {@code lore:} - because material, nbt and durability do
 * not depend on the reader. A language nobody wrote falls back to the first block declared, so a
 * screen never renders a missing translation.</p>
 */
public final class IconLocale {

    private final Map<String, List<String>> linesByLang = new LinkedHashMap<>();

    /** Whether {@code line} carries text, which is all a language block is allowed to change. */
    public static boolean isTextLine(@Nullable String line) {
        if (line == null) {
            return false;
        }
        RegisteredPart part = ItemEngine.get().findByLine(line);
        return part != null && (StandardParts.NAME.equals(part.getKey())
                || StandardParts.LORE.equals(part.getKey()));
    }

    /** The item-data lines a name plus a lore make, which is what a language block holds on disk. */
    @Nonnull
    public static List<String> linesOf(@Nullable String name, @Nullable List<String> lore) {
        List<String> lines = new ArrayList<>();
        if (name != null) {
            lines.add("name:" + name);
        }
        if (lore != null) {
            for (String loreLine : lore) {
                lines.add("lore:" + loreLine);
            }
        }
        return lines;
    }

    /** Declares (or replaces) the block of {@code lang}. An empty list clears it. */
    public void put(@Nonnull String lang, @Nonnull List<String> lines) {
        String key = LocaleType.normalize(lang);
        if (lines.isEmpty()) {
            linesByLang.remove(key);
            return;
        }
        linesByLang.put(key, new ArrayList<>(lines));
    }

    /**
     * The block that answers for {@code lang}: its own when it has one, the first declared otherwise.
     * {@code null} only when nothing at all was declared.
     */
    @Nullable
    public List<String> resolve(@Nullable String lang) {
        if (lang != null) {
            List<String> lines = linesByLang.get(LocaleType.normalize(lang));
            if (lines != null) {
                return Collections.unmodifiableList(lines);
            }
        }
        for (List<String> first : linesByLang.values()) {
            return Collections.unmodifiableList(first);
        }
        return null;
    }

    /** The block of {@code lang} and nothing else - {@code null} when that language was not declared. */
    @Nullable
    public List<String> get(@Nonnull String lang) {
        List<String> lines = linesByLang.get(LocaleType.normalize(lang));
        return lines == null ? null : Collections.unmodifiableList(lines);
    }

    @Nonnull
    public Set<String> getLanguages() {
        return Collections.unmodifiableSet(linesByLang.keySet());
    }

    public boolean isEmpty() {
        return linesByLang.isEmpty();
    }

    @Nonnull
    public IconLocale copy() {
        IconLocale copy = new IconLocale();
        copy.linesByLang.putAll(linesByLang);
        return copy;
    }

    @Override
    public String toString() {
        return "IconLocale" + linesByLang.keySet();
    }

}
