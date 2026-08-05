package br.com.finalcraft.evernifecore.minecraft;

public class McPermissionNodes {

    public static final String EVERNIFECORE_COMMAND_BIOMEINFO           = "evernifecore.command.biomeinfo";
    public static final String EVERNIFECORE_COMMAND_BLOCKINFO           = "evernifecore.command.blockinfo";
    public static final String EVERNIFECORE_COMMAND_OREINFO             = "evernifecore.command.oreinfo";
    //unused while the oredict item grid is a chat listing: it gated taking a copy of a clicked item,
    //which only a gui can offer. Kept so the node servers already grant keeps its meaning when it returns
    public static final String EVERNIFECORE_COMMAND_OREINFO_CREATIVE    = "evernifecore.command.oreinfo.creative.getitems";
    public static final String EVERNIFECORE_COMMAND_ITEMINFO            = "evernifecore.command.iteminfo";
    public static final String EVERNIFECORE_COMMAND_ENTITYINFO          = "evernifecore.command.entityinfo";
    public static final String EVERNIFECORE_COMMAND_TESTPROTECTION      = "evernifecore.command.protectiontest";
    public static final String UPDATECHECK_PERMISSION_TEMPLATE          = "%plugin%.updatecheck.warn";

}
