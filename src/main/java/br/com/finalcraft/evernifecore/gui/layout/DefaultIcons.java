package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.gui.util.EnumWool;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Material;

public class DefaultIcons {

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l<§e<§l<§7 Previous Page", hover = "\n§7§l§o Previous Page!\n ")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l<§e<§l<§7 Página Anterior", hover = "\n§7§l§o Página Anterior!\n ")
    private static LocaleMessage PREVIOUS_PAGE;

    public static LayoutIcon getPreviousPageButton() {
        return FCItemFactory
                .from(Material.REDSTONE)
                .applyIf(() -> MCVersion.isHigherEquals(MCVersion.v1_12), builder -> builder.material(Material.SPECTRAL_ARROW))
                .applyMaterialIfExists("PIXELMON_TRADE_HOLDER_LEFT")
                .applyMaterialIfExists("EVERPOKEUTILS_BACK")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:1")
                .displayName(PREVIOUS_PAGE.getDefaultFancyText().getText())
                .lore(PREVIOUS_PAGE.getDefaultFancyText().getHoverText())
                .asLayout();
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§7Next Page §a§l>§a>§l>", hover = "\n§7§l§o Next Page!\n ")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7Próxima Página §a§l>§a>§l>", hover = "\n§7§l§o Próxima Página!\n ")
    private static LocaleMessage NEXT_PAGE;

    public static LayoutIcon getNextPageButton() {
        return FCItemFactory
                .from(Material.REDSTONE)
                .applyIf(() -> MCVersion.isHigherEquals(MCVersion.v1_16), builder -> builder.material(Material.SPECTRAL_ARROW))
                .applyMaterialIfExists("PIXELMON_TRADE_HOLDER_RIGHT")
                .applyMaterialIfExists("EVERPOKEUTILS_FORWARD")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:2")
                .displayName(NEXT_PAGE.getDefaultFancyText().getText())
                .lore(NEXT_PAGE.getDefaultFancyText().getHoverText())
                .asLayout();
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§6§l<§6<§l<", hover = "\n§7§l§o Back!\n ")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6§l<§6<§l<", hover = "\n§7§l§o Voltar!\n ")
    private static LocaleMessage BACK_BUTTON;

    public static LayoutIcon getBackButton() {
        return FCItemFactory
                .from(Material.REDSTONE)
                .applyIf(() -> MCVersion.isHigherEquals(MCVersion.v1_16), builder -> builder.material(Material.SPECTRAL_ARROW))
                .applyMaterialIfExists("EVERPOKEUTILS_BACK")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:1")
                .displayName(BACK_BUTTON.getDefaultFancyText().getText())
                .lore(BACK_BUTTON.getDefaultFancyText().getHoverText())
                .asLayout();
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§a§lConfirm", hover = "" +
            "§2§m------------------------------------§r" +
            "\n" +
            "\n§a ✎ Click here to CONFIRM this operation!" +
            "\n" +
            "\n§2§m------------------------------------§r")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§lConfirmar", hover = "" +
            "§2§m------------------------------------§r" +
            "\n" +
            "\n§a ✎ Clique aqui para CONFIRMAR operação!" +
            "\n" +
            "\n§2§m------------------------------------§r")
    private static LocaleMessage CONFIRM_BUTTON;

    public static LayoutIcon getConfirmButton() {
        return EnumWool.LIME
                .asFactory()
                .applyMaterialIfExists("EVERPOKEUTILS_POSITIVO")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:13")
                .displayName(CONFIRM_BUTTON.getDefaultFancyText().getText())
                .lore(CONFIRM_BUTTON.getDefaultFancyText().getHoverText())
                .asLayout();
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§c§lDeny", hover = "" +
            "§2§m------------------------------------§r" +
            "\n" +
            "\n§c ✎ Click here to DENY this operation!" +
            "\n" +
            "\n§2§m------------------------------------§r")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c§lCancelar", hover = "" +
            "§2§m------------------------------------§r" +
            "\n" +
            "\n§c ✎ Clique aqui para CANCELAR operação!" +
            "\n" +
            "\n§2§m------------------------------------§r")
    private static LocaleMessage DENY_BUTTON;

    public static LayoutIcon getDenyButton() {
        return EnumWool.RED
                .asFactory()
                .applyMaterialIfExists("EVERPOKEUTILS_NEGATIVO")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:12")
                .displayName(DENY_BUTTON.getDefaultFancyText().getText())
                .lore(DENY_BUTTON.getDefaultFancyText().getHoverText())
                .asLayout();
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§a§lInfo", hover = "" +
            "§2§m------------------------------------§r" +
            "\n" +
            "\n%information%" +
            "\n" +
            "\n§2§m------------------------------------§r")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§lInfo", hover = "" +
            "§2§m------------------------------------§r" +
            "\n" +
            "\n%information%" +
            "\n" +
            "\n§2§m------------------------------------§r")
    private static LocaleMessage INFORMATION_BUTTON;

    public static LayoutIcon getInformationButton() {
        return FCItemFactory.from(Material.PAPER)
                .applyMaterialIfExists("EVERPOKEUTILS_CARTAZ")
                .applyMaterialIfExists("EVERNIFEWORLDRPG_CUSTOMICON:20")
                .displayName(INFORMATION_BUTTON.getDefaultFancyText().getText())
                .lore(INFORMATION_BUTTON.getDefaultFancyText().getHoverText())
                .asLayout();
    }
}
