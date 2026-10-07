package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionId;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A region by {@code owner:name}, or by its bare name. Under a command that is a {@link RegionScope} a bare name
 * means that scope's owner and nobody else; with no scope it means the one region of any owner that has the name,
 * and a name two owners share is refused listing both full ids.
 */
public class ArgParserRegion extends ArgParser<Region> {

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is no region called [§e${region_name}§c]")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNão existe nenhuma região chamada [§e${region_name}§c]")
    public static LocaleMessage NO_REGION_CALLED;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cMore than one region is called [§e${region_name}§c] - name it as owner:name: §7${region_list}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cMais de uma região se chama [§e${region_name}§c] - informe como dono:nome: §7${region_list}")
    public static LocaleMessage MORE_THAN_ONE_REGION_CALLED;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is no region at your location!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNão existe nenhuma região na sua localização!")
    public static LocaleMessage NO_REGION_AT_YOUR_LOCATION;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThere is more than one region at your location - name one: §7${region_list}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cExiste mais de uma região na sua localização - informe uma: §7${region_list}")
    public static LocaleMessage MORE_THAN_ONE_REGION_AT_YOUR_LOCATION;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cOnly a player stands somewhere - name the region instead!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cApenas um jogador está em algum lugar - informe o nome da região!")
    public static LocaleMessage ONLY_A_PLAYER_STANDS_SOMEWHERE;

    public ArgParserRegion(ArgInfo argInfo) {
        super(argInfo);
    }

    /** The scope {@code executor} stands for, or null when the command is not one. */
    public static @Nullable RegionScope scopeOf(@Nullable Object executor) {
        return executor instanceof RegionScope ? (RegionScope) executor : null;
    }

    @Override
    public @Nonnull ParseResult<Region> parse(@Nonnull ParseCall call) {
        String text = call.getArgumento().toString();
        if (text.indexOf(':') >= 0) {
            //a full id reaches any owner's region; whether the verb may WRITE to it is the verb's call
            try {
                Region region = RegionLookup.get().byId(RegionId.parse(text));
                return region != null ? ParseResult.of(region) : noRegionCalled(text);
            } catch (IllegalArgumentException notAnId) {
                return noRegionCalled(text);
            }
        }
        RegionScope scope = scopeOf(call.getExecutor());
        if (scope != null) {
            Region region = scope.getManager().get(text);
            return region != null ? ParseResult.of(region) : noRegionCalled(text);
        }
        List<Region> named = new ArrayList<>();
        for (Region region : RegionLookup.get().all()) {
            if (region.getId().getName().equalsIgnoreCase(text)) {
                named.add(region);
            }
        }
        if (named.isEmpty()) {
            return noRegionCalled(text);
        }
        if (named.size() > 1) {
            return denied(MORE_THAN_ONE_REGION_CALLED.addPlaceholder("region_name", text).addPlaceholder("region_list", idsOf(named)));
        }
        return ParseResult.of(named.get(0));
    }

    /** The region the player stands in; under a scope, only that scope's owner's regions count. */
    @Override
    public @Nonnull ParseResult<Region> fromSender(@Nonnull ParseCall call) {
        if (!call.getSender().isPlayer()) {
            return unrecognized(ONLY_A_PLAYER_STANDS_SOMEWHERE);
        }
        FPlayer player = call.getSender().asFPlayer();
        FLocation location = player.getLocation();
        if (location == null) {
            return unrecognized(NO_REGION_AT_YOUR_LOCATION);
        }
        RegionScope scope = scopeOf(call.getExecutor());
        List<Region> here = new ArrayList<>();
        for (Region region : RegionLookup.get().at(location.getWorldLocPos())) {
            if (scope == null || region.getManager() == scope.getManager()) {
                here.add(region);
            }
        }
        if (here.isEmpty()) {
            return unrecognized(NO_REGION_AT_YOUR_LOCATION);
        }
        if (here.size() > 1) {
            return denied(MORE_THAN_ONE_REGION_AT_YOUR_LOCATION.addPlaceholder("region_list", idsOf(here)));
        }
        return ParseResult.of(here.get(0));
    }

    /** Under a scope, its owner's names, bare; otherwise every full id. */
    @Override
    public @Nonnull List<String> tabComplete(TabContext tabContext) {
        RegionScope scope = scopeOf(tabContext.getExecutor());
        return (scope != null ? scope.getManager().getAll() : RegionLookup.get().all()).stream()
                .map(region -> scope != null ? region.getId().getName() : region.getId().toString())
                .filter(candidate -> FCStringUtil.startsWithIgnoreCase(candidate, tabContext.getLastWord()))
                .sorted()
                .collect(Collectors.toList());
    }

    private ParseResult<Region> noRegionCalled(String text) {
        return unrecognized(NO_REGION_CALLED.addPlaceholder("region_name", text));
    }

    private static String idsOf(List<Region> regions) {
        return regions.stream().map(region -> region.getId().toString()).sorted().collect(Collectors.joining(", "));
    }
}
