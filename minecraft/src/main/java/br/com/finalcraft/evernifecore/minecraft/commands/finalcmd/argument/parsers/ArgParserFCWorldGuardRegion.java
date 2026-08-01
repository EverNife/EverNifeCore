package br.com.finalcraft.evernifecore.minecraft.commands.finalcmd.argument.parsers;

import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.protection.worldguard.FCWorldGuardRegion;
import br.com.finalcraft.evernifecore.minecraft.protection.worldguard.WGPlatform;
import jakarta.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.List;
import java.util.stream.Collectors;

public class ArgParserFCWorldGuardRegion extends ArgParser<FCWorldGuardRegion> {

    public ArgParserFCWorldGuardRegion(ArgInfo argInfo) {
        super(argInfo);
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is no region called [§e${region_name}§c]")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNão existe nenhuma região chamada [§e${region_name}§c]")
    public static LocaleMessage THERE_IS_NO_REGION_FOR_THIS_NAME;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is no region at your location!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNão existe nenhuma região na sua localização!")
    public static LocaleMessage THERE_IS_NO_REGION_AT_YOUR_LOCATION;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is more than one region at your location! §7§o${region_list}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cExiste mais de uma região na sua localização! §7§o${region_list}")
    public static LocaleMessage THERE_IS_MORE_THAN_ONE_REGION_AT_YOUR_LOCATION;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cOnly a player stands somewhere - name the region instead!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cApenas um jogador está em algum lugar - informe o nome da região!")
    public static LocaleMessage ONLY_A_PLAYER_STANDS_SOMEWHERE;

    @Override
    public ParseResult<FCWorldGuardRegion> parse(@Nonnull ParseCall call) {
        FCWorldGuardRegion region = WGPlatform.getInstance().getRegionByID(null, call.getArgumento().toString());

        return region != null
                ? ParseResult.of(region)
                : unrecognized(THERE_IS_NO_REGION_FOR_THIS_NAME
                        .addPlaceholder("region_name", call.getArgumento().toString()));
    }

    /** The region the sender is standing in - which is why the console never gets one. */
    @Override
    public ParseResult<FCWorldGuardRegion> fromSender(@Nonnull ParseCall call) {
        if (!(call.getSender() instanceof Player)){
            return unrecognized(ONLY_A_PLAYER_STANDS_SOMEWHERE);
        }

        Player player = (Player) call.getSender();
        List<FCWorldGuardRegion> regionAtPlayer = WGPlatform.getInstance().getApplicableRegions(player.getLocation()).getRegions().stream()
                .filter(region -> region.contains(player))
                .collect(Collectors.toList());

        if (regionAtPlayer.isEmpty()){
            //"There is no region here" is a fact, not a policy: only a required argument turns it fatal
            return unrecognized(THERE_IS_NO_REGION_AT_YOUR_LOCATION);
        }

        if (regionAtPlayer.size() > 1){
            String regionList = regionAtPlayer.stream().map(FCWorldGuardRegion::getId).collect(Collectors.joining(", "));
            return denied(THERE_IS_MORE_THAN_ONE_REGION_AT_YOUR_LOCATION.addPlaceholder("region_list", regionList));
        }

        return ParseResult.of(regionAtPlayer.get(0));
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
