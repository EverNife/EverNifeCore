package br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.protection.worldguard.FCWorldGuardRegion;
import br.com.finalcraft.evernifecore.protection.worldguard.WGPlatform;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

public class ArgParserFCWorldGuardRegion extends ArgParser<FCWorldGuardRegion> {

    public ArgParserFCWorldGuardRegion(ArgInfo argInfo) {
        super(argInfo);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is no region called [§e%region_name%§c]")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cExiste uma arena chamada [§e%region_name%§c] mas ela pertence ao módulo: §b%arena_module%")
    public static LocaleMessage THERE_IS_NO_REGION_FOR_THIS_NAME;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is no region at your location!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNão existe nenhuma região na sua localização!")
    public static LocaleMessage THERE_IS_NO_REGION_AT_YOUR_LOCATION;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is more than one region at your location! §7§o%region_list%")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cExiste mais de uma região na sua localização! §7§o%region_list%")
    public static LocaleMessage THERE_IS_MORE_THAN_ONE_REGION_AT_YOUR_LOCATION;

    @Override
    public FCWorldGuardRegion parserArgument(@Nonnull CommandSender sender, @Nonnull Argumento argumento) throws ArgParseException {

        FCWorldGuardRegion fcWorldGuardRegion = WGPlatform.getInstance().getRegionByID(null, argumento.toString());

        if (fcWorldGuardRegion == null && getArgInfo().isProvidedByContext() && sender instanceof Player){
            getArgContext().setShouldMoveArgIndex(false);
            Player player = (Player) sender;
            List<FCWorldGuardRegion> regionAtPlayer = WGPlatform.getInstance().getApplicableRegions(player.getLocation()).getRegions().stream()
                    .filter(region -> region.contains(player))
                    .collect(Collectors.toList());

            if (regionAtPlayer.size() == 0){
                if (getArgInfo().isRequired()){
                    THERE_IS_NO_REGION_AT_YOUR_LOCATION.send(sender);
                    throw new ArgParseException();
                }else {
                    return null;
                }
            }

            if (regionAtPlayer.size() > 1){
                String regionList = regionAtPlayer.stream().map(region -> region.getId()).collect(Collectors.joining(", "));
                THERE_IS_MORE_THAN_ONE_REGION_AT_YOUR_LOCATION
                        .addPlaceholder("%region_list%", regionList)
                        .send(sender);
                throw new ArgParseException();
            }

            return regionAtPlayer.get(0);
        }

        if (fcWorldGuardRegion == null && this.getArgInfo().isRequired()){
            THERE_IS_NO_REGION_FOR_THIS_NAME
                    .addPlaceholder("%region_name%", argumento.toString())
                    .send(sender);
            throw new ArgParseException();
        }

        return fcWorldGuardRegion;
    }

    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {

        return Bukkit.getWorlds().stream()
                .flatMap(world -> WGPlatform.getInstance().getRegionManager(world).getRegions().values().stream())
                .map(FCWorldGuardRegion::getId)
                .filter(s -> !s.equals("__global__") && StringUtil.startsWithIgnoreCase(s, tabContext.getLastWord()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

    }
}
