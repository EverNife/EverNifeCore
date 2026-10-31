package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The buttons every screen ends up needing, already multi-language and already surviving the version
 * range this framework runs on.
 *
 * <p>Two things are recovered here that a plugin should not have to write again. The material chain:
 * each button names a floor that exists everywhere and then the better items above it, so the same jar
 * shows a spectral arrow on 1.12+, a mod's own icon on a modpack that has one, and redstone on 1.7.10.
 * And the text: the buttons carry {@code pt_BR} and {@code en_US} out of the box, so what a player
 * reads follows the player, and the admin still sees both blocks in the generated yml.</p>
 */
public final class DefaultIcons {

    private DefaultIcons() {

    }

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l<§e<§l<§7 Previous Page", hover = "\n§7§l§o Previous Page!\n ")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l<§e<§l<§7 Página Anterior", hover = "\n§7§l§o Página Anterior!\n ")
    private static LocaleMessage PREVIOUS_PAGE;

    @FCLocale(lang = LocaleType.EN_US, text = "§7Next Page §a§l>§a>§l>", hover = "\n§7§l§o Next Page!\n ")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7Próxima Página §a§l>§a>§l>", hover = "\n§7§l§o Próxima Página!\n ")
    private static LocaleMessage NEXT_PAGE;

    @FCLocale(lang = LocaleType.EN_US, text = "§6§l<§6<§l<", hover = "\n§7§l§o Back!\n ")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6§l<§6<§l<", hover = "\n§7§l§o Voltar!\n ")
    private static LocaleMessage BACK_BUTTON;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§lConfirm", hover = ""
            + "§2§m------------------------------------§r"
            + "\n"
            + "\n§a ✎ Click here to CONFIRM this operation!"
            + "\n"
            + "\n§2§m------------------------------------§r")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§lConfirmar", hover = ""
            + "§2§m------------------------------------§r"
            + "\n"
            + "\n§a ✎ Clique aqui para CONFIRMAR operação!"
            + "\n"
            + "\n§2§m------------------------------------§r")
    private static LocaleMessage CONFIRM_BUTTON;

    @FCLocale(lang = LocaleType.EN_US, text = "§c§lDeny", hover = ""
            + "§2§m------------------------------------§r"
            + "\n"
            + "\n§c ✎ Click here to DENY this operation!"
            + "\n"
            + "\n§2§m------------------------------------§r")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c§lCancelar", hover = ""
            + "§2§m------------------------------------§r"
            + "\n"
            + "\n§c ✎ Clique aqui para CANCELAR operação!"
            + "\n"
            + "\n§2§m------------------------------------§r")
    private static LocaleMessage DENY_BUTTON;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§lInfo", hover = ""
            + "§2§m------------------------------------§r"
            + "\n"
            + "\n%information%"
            + "\n"
            + "\n§2§m------------------------------------§r")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§lInfo", hover = ""
            + "§2§m------------------------------------§r"
            + "\n"
            + "\n%information%"
            + "\n"
            + "\n§2§m------------------------------------§r")
    private static LocaleMessage INFORMATION_BUTTON;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§lEdit", hover = "\n§7§l§o Edit this screen\n ")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§lEditar", hover = "\n§7§l§o Editar esta tela\n ")
    private static LocaleMessage WRENCH_BUTTON;

    /** The sixteen dye colours, the way {@link #wool(Color)} and {@link #pane(Color)} name their items. */
    public enum Color {
        WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK, GRAY, LIGHT_GRAY, CYAN, PURPLE, BLUE, BROWN, GREEN, RED, BLACK;

        /** The id prefix: {@code light_blue} for {@code minecraft:light_blue_wool}. */
        @Nonnull
        public String getId() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** {@code minecraft:<color>_wool}; a renderer on a server before 1.13 maps it to the old wool and its data value. */
    @Nonnull
    public static ItemSpec wool(@Nonnull Color color) {
        return ItemSpec.of("minecraft:" + color.getId() + "_wool", 1);
    }

    /** {@code minecraft:<color>_stained_glass_pane}, mapped the same way as {@link #wool(Color)}. */
    @Nonnull
    public static ItemSpec pane(@Nonnull Color color) {
        return ItemSpec.of("minecraft:" + color.getId() + "_stained_glass_pane", 1);
    }

    @Nonnull
    public static Icon previousPage() {
        return localized(Icon.of(item("EVERNIFEWORLDRPG_CUSTOMICON:1"), item("EVERPOKEUTILS_BACK"),
                item("PIXELMON_TRADE_HOLDER_LEFT"), item("minecraft:spectral_arrow"), item("minecraft:redstone")), PREVIOUS_PAGE);
    }

    @Nonnull
    public static Icon nextPage() {
        return localized(Icon.of(item("EVERNIFEWORLDRPG_CUSTOMICON:2"), item("EVERPOKEUTILS_FORWARD"),
                item("PIXELMON_TRADE_HOLDER_RIGHT"), item("minecraft:spectral_arrow"), item("minecraft:redstone")), NEXT_PAGE);
    }

    @Nonnull
    public static Icon back() {
        return localized(Icon.of(item("EVERNIFEWORLDRPG_CUSTOMICON:1"), item("EVERPOKEUTILS_BACK"),
                item("minecraft:spectral_arrow"), item("minecraft:redstone")), BACK_BUTTON);
    }

    @Nonnull
    public static Icon confirm() {
        return localized(Icon.of(item("EVERNIFEWORLDRPG_CUSTOMICON:13"), item("EVERPOKEUTILS_POSITIVO"),
                wool(Color.LIME)), CONFIRM_BUTTON);
    }

    @Nonnull
    public static Icon deny() {
        return localized(Icon.of(item("EVERNIFEWORLDRPG_CUSTOMICON:12"), item("EVERPOKEUTILS_NEGATIVO"),
                wool(Color.RED)), DENY_BUTTON);
    }

    @Nonnull
    public static Icon info() {
        return localized(Icon.of(item("EVERNIFEWORLDRPG_CUSTOMICON:20"), item("EVERPOKEUTILS_CARTAZ"),
                item("minecraft:paper")), INFORMATION_BUTTON);
    }

    @Nonnull
    public static Icon wrench() {
        return localized(Icon.of(item("minecraft:anvil")), WRENCH_BUTTON);
    }

    //the mod items first: a renderer draws the first candidate its server knows, the vanilla item being the last resort
    private static ItemSpec item(String id) {
        return ItemSpec.of(id, 1);
    }

    private static Icon localized(Icon icon, @Nullable LocaleMessage message) {
        if (message == null) {
            return icon; //asked for before the core registered its own locales: the material still answers
        }
        //These texts are the core's own, and without an owner an icon has no language to resolve
        //against - it would hand every viewer whichever block happened to be declared first
        icon.setLocaleOwner(EverNifeCore.getEcPluginData());
        for (String lang : LocaleType.values()) {
            FancyText text = message.getFancyText(lang);
            if (text != null) {
                icon.addLocale(lang, text.getText(), loreOf(text.getHoverText()));
            }
        }
        return icon;
    }

    private static List<String> loreOf(String hover) {
        return hover == null || hover.isEmpty() ? Collections.<String>emptyList()
                : Arrays.asList(hover.split("\n", -1));
    }

}
