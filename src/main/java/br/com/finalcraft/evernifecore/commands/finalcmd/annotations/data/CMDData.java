package br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data;

import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@AllArgsConstructor
public class CMDData {

    private String[] labels; //This means both command ALIASES or SubCommands names
    private String usage;
    private String desc;
    private String permission;
    private FCLocaleData[] locales;

}
