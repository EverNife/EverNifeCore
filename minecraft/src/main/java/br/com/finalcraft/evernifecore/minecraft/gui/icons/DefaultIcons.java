package br.com.finalcraft.evernifecore.minecraft.gui.icons;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.itemstack.itembuilder.FCItemBuilder;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
public class DefaultIcons {

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

    @Nonnull
    public static Icon previousPage() {
        return localized(FCItemFactory
                .from(Material.REDSTONE)
                .applyIf(() -> MCVersion.isHigherEquals(MCVersion.v1_12),
                        builder -> builder.material(Material.SPECTRAL_ARROW))
                .applyMaterialIfExists("PIXELMON_TRADE_HOLDER_LEFT")
                .applyMaterialIfExists("EVERPOKEUTILS_BACK")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:1"), PREVIOUS_PAGE);
    }

    @Nonnull
    public static Icon nextPage() {
        return localized(FCItemFactory
                .from(Material.REDSTONE)
                .applyIf(() -> MCVersion.isHigherEquals(MCVersion.v1_12),
                        builder -> builder.material(Material.SPECTRAL_ARROW))
                .applyMaterialIfExists("PIXELMON_TRADE_HOLDER_RIGHT")
                .applyMaterialIfExists("EVERPOKEUTILS_FORWARD")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:2"), NEXT_PAGE);
    }

    @Nonnull
    public static Icon back() {
        return localized(FCItemFactory
                .from(Material.REDSTONE)
                .applyIf(() -> MCVersion.isHigherEquals(MCVersion.v1_12),
                        builder -> builder.material(Material.SPECTRAL_ARROW))
                .applyMaterialIfExists("EVERPOKEUTILS_BACK")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:1"), BACK_BUTTON);
    }

    @Nonnull
    public static Icon confirm() {
        return localized(EnumWool.LIME
                .asFactory()
                .applyMaterialIfExists("EVERPOKEUTILS_POSITIVO")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:13"), CONFIRM_BUTTON);
    }

    @Nonnull
    public static Icon deny() {
        return localized(EnumWool.RED
                .asFactory()
                .applyMaterialIfExists("EVERPOKEUTILS_NEGATIVO")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:12"), DENY_BUTTON);
    }

    @Nonnull
    public static Icon info() {
        return localized(FCItemFactory
                .from(Material.PAPER)
                .applyMaterialIfExists("EVERPOKEUTILS_CARTAZ")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:20"), INFORMATION_BUTTON);
    }

    /** The admin's way into a screen: the button a layout puts behind a permission. */
    @Nonnull
    public static Icon wrench() {
        return localized(FCItemFactory.from(Material.ANVIL), WRENCH_BUTTON);
    }

    /**
     * Turns a built item into an icon carrying every language the message was registered in, so the
     * text is chosen when someone looks at it rather than when the button was created.
     */
    private static Icon localized(FCItemBuilder builder, @Nullable LocaleMessage message) {
        Icon icon = builder.asIcon();
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
