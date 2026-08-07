package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiType;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The base of every layout class: the screen's own settings after the yml has had its say, plus the
 * icons the file resolved.
 *
 * <p>A subclass declares {@link IconData} fields and gets them filled in; everything on this class is
 * written by {@link LayoutScanner} and read by whoever opens the screen. Instances come from
 * {@link Layouts}, never from {@code new} - the file has to be read before the fields mean anything.</p>
 */
public abstract class LayoutBase {

    private ECPluginData plugin;
    private String layoutName;
    private String language;
    private GuiType type = GuiType.CHEST;
    private int rows = GuiType.MAX_CHEST_ROWS;
    private boolean integrateToPAPI = false;
    private String title = "";
    private final Map<String, String> titleByLang = new LinkedHashMap<>();
    private final Map<String, PlacedIcon> icons = new LinkedHashMap<>();

    @Nonnull
    public ECPluginData getPlugin() {
        return plugin;
    }

    /** The simple class name, which is also the yml file name and the name the commands take. */
    @Nonnull
    public String getLayoutName() {
        return layoutName;
    }

    /**
     * The language this copy was resolved for, or {@code null} for the copy every viewer with no
     * overlay of their own reads. It is half of the key {@link Layouts} caches under.
     */
    @Nullable
    public String getLanguage() {
        return language;
    }

    @Nonnull
    public GuiType getType() {
        return type;
    }

    public int getRows() {
        return rows;
    }

    public boolean isIntegrateToPAPI() {
        return integrateToPAPI;
    }

    /** The title in the owning plugin's language. */
    @Nonnull
    public String getTitle() {
        String pluginTitle = titleByLang.get(FCLocaleManager.getLangOf(plugin));
        return pluginTitle != null ? pluginTitle : title;
    }

    /**
     * The title in {@code viewer}'s own language, falling back to the plugin's and then to the one
     * the class declared. Each viewer has their own window, so the title costs nothing to vary.
     */
    @Nonnull
    public String getTitleFor(@Nullable Player viewer) {
        if (viewer == null) {
            return getTitle();
        }
        String viewerTitle = titleByLang.get(FCLocaleManager.getLangOf(FCBukkitUtil.adapt(viewer), plugin));
        return viewerTitle != null ? viewerTitle : getTitle();
    }

    @Nonnull
    public Map<String, String> getTitleByLang() {
        return Collections.unmodifiableMap(titleByLang);
    }

    /** Every icon this layout resolved, by the field name that is also its key in the yml. */
    @Nonnull
    public Map<String, PlacedIcon> getIcons() {
        return Collections.unmodifiableMap(icons);
    }

    /** The icon under {@code fieldName}, or {@code null} when it failed to load and was dropped. */
    @Nullable
    public Icon getIcon(@Nonnull String fieldName) {
        PlacedIcon placed = icons.get(fieldName);
        return placed == null ? null : placed.getIcon();
    }

    /**
     * The name {@code icon} is known by here, or {@code null} when it belongs to another layout.
     *
     * <p>A field of the class holds the icon the plugin DECLARED, while {@link #getIcons()} holds the
     * one the file resolved. Both answer, which is what lets a selector written against the class -
     * {@code l -> l.UPGRADE} - reach whatever the admin configured under that key.</p>
     */
    @Nullable
    public String getIconName(@Nullable Icon icon) {
        if (icon == null) {
            return null;
        }
        for (Map.Entry<String, PlacedIcon> entry : icons.entrySet()) {
            if (entry.getValue().getIcon() == icon) {
                return entry.getKey();
            }
        }
        for (Field field : LayoutScanner.iconFields(getClass())) {
            try {
                field.setAccessible(true);
                if (field.get(this) == icon) {
                    return field.getName();
                }
            } catch (IllegalAccessException unreadable) {
                //a field the jvm refuses to hand over cannot be the one the selector just read
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Written by the scanner - a layout is only meaningful once the file has been read into it
    // -----------------------------------------------------------------------------------------------------------------

    void applySettings(ECPluginData plugin, String layoutName, String language, GuiType type, int rows,
                       boolean integrateToPAPI, String title) {
        this.plugin = plugin;
        this.layoutName = layoutName;
        this.language = language;
        this.type = type;
        this.rows = rows;
        this.integrateToPAPI = integrateToPAPI;
        this.title = title;
    }

    void putTitle(String lang, String localizedTitle) {
        titleByLang.put(LocaleType.normalize(lang), localizedTitle);
    }

    void putIcon(String fieldName, Icon icon, SlotSet slots) {
        icons.put(fieldName, new PlacedIcon(fieldName, icon, slots));
    }

    /** One icon of a layout and where the file put it. An empty slot set is an icon deliberately switched off. */
    public static final class PlacedIcon {

        private final String name;
        private final Icon icon;
        private final SlotSet slots;

        PlacedIcon(String name, Icon icon, SlotSet slots) {
            this.name = name;
            this.icon = icon;
            this.slots = slots;
        }

        /** The field name, which is also the yml key. */
        @Nonnull
        public String getName() {
            return name;
        }

        @Nonnull
        public Icon getIcon() {
            return icon;
        }

        @Nonnull
        public SlotSet getSlots() {
            return slots;
        }

        /** Whether this icon is on screen at all. */
        public boolean isVisible() {
            return !slots.isEmpty();
        }

        @Override
        public String toString() {
            return name + slots;
        }

    }

}
