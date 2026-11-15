package br.com.finalcraft.evernifecore.booster.command;

import br.com.finalcraft.evernifecore.locale.LocaleScope;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.booster.Booster;
import br.com.finalcraft.evernifecore.booster.BoosterDomain;
import br.com.finalcraft.evernifecore.booster.BoosterManager;
import br.com.finalcraft.evernifecore.booster.BoosterResult;
import br.com.finalcraft.evernifecore.booster.Boosters;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.NodeVerbAccess;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.VerbPermissionBase;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserString;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser.TabContext;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The booster verbs a plugin mounts under its own command, over its own {@link BoosterDomain}s and {@link BoosterManager}:
 * a field of its command class annotated {@code @FinalCMD.Node(subcmd = {"boosters"}, permission = "duels.boosters")}
 * holds the node built with {@code (ecPluginData, "duels.boosters")}.
 * {@code give <player> <domain> <factor> <duration>} grants a booster ({@code 2} or {@code x2} multiplies by 2,
 * {@code +0.5} or {@code +50%} adds to the multiplier) through the plugin's manager, which audits it; {@code list [player]}
 * shows the active ones with their ids; {@code revoke <id>} takes one away; {@code explain <player> <domain>} prints the
 * multiplier, each parcel and what could not be read - an offline player's permissions are loaded first. A player is any
 * one the server has seen (completed offline too); a domain is one of the plugin's own, whole or by its short name. The core mounts it nowhere. The mount's permission is the door; a
 * {@code permissionBase} adds {@code <base>.<verb>} per verb.
 */
public final class BoosterCommandNode implements VerbPermissionBase {

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §e${plugin}§c has no booster domain §e${domain}§c - it has: §e${domains}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §e${plugin}§c não tem o domínio de booster §e${domain}§c - ele tem: §e${domains}")
    public static LocaleMessage NO_SUCH_DOMAIN;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §e${factor}§c is no factor: write a multiplier (§e2§c, §ex1.5§c) or a bonus (§e+0.5§c, §e+50%§c), never below zero.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §e${factor}§c não é um fator: escreva um multiplicador (§e2§c, §ex1.5§c) ou um bônus (§e+0.5§c, §e+50%§c), nunca abaixo de zero.")
    public static LocaleMessage BAD_FACTOR;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aBooster §e${booster}§a granted, id §e${id}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aBooster §e${booster}§a concedido, id §e${id}")
    public static LocaleMessage GRANTED;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThe booster was not kept: ${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cO booster não foi guardado: ${reason}")
    public static LocaleMessage FAILED;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §eNo active booster of §6${plugin}§e${for_player}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §eNenhum booster ativo de §6${plugin}§e${for_player}.")
    public static LocaleMessage NONE_ACTIVE;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Active boosters of §e${plugin}§6${for_player}:")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Boosters ativos de §e${plugin}§6${for_player}:")
    public static LocaleMessage LIST_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§7 - §f${id}§7: ${booster}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7 - §f${id}§7: ${booster}")
    public static LocaleMessage LIST_LINE;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §e${id}§c is not the id of a booster of §e${plugin}§c - §elist§c shows them.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §e${id}§c não é o id de um booster de §e${plugin}§c - §elist§c mostra os ids.")
    public static LocaleMessage NO_SUCH_BOOSTER;

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §aBooster §e${id}§a revoked.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §aBooster §e${id}§a revogado.")
    public static LocaleMessage REVOKED;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Boosters of §e${player}§6 on §e${domain}§6: §ex${multiplier}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Boosters de §e${player}§6 em §e${domain}§6: §ex${multiplier}")
    public static LocaleMessage EXPLAIN_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§7${line}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7${line}")
    public static LocaleMessage EXPLAIN_LINE;

    private final ECPluginData plugin;
    private final @Nullable String permissionBase;

    public BoosterCommandNode(ECPluginData plugin) {
        this(plugin, null);
    }

    /** @param permissionBase when not null, each verb also needs {@code <permissionBase>.<verb>} */
    public BoosterCommandNode(ECPluginData plugin, @Nullable String permissionBase) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.permissionBase = permissionBase;
    }

    public ECPluginData getPlugin() {
        return plugin;
    }

    @Override
    public @Nullable String getPermissionBase() {
        return permissionBase;
    }

    // ------------------------------------------------------------------
    //  Verbs
    // ------------------------------------------------------------------

    @FinalCMD.SubCMD(subcmd = "give", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Grant a player a booster of a domain of this plugin: 2 multiplies, +0.5 adds, for a duration"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Concede a um jogador um booster de um domínio deste plugin: 2 multiplica, +0.5 soma, por um tempo")
    })
    public void give(FCommandSender sender,
                     @Arg(value = "<player>", locales = {
                             @FCLocale(lang = LocaleType.EN_US, text = "A player the server has seen, online or not, by name or UUID"),
                             @FCLocale(lang = LocaleType.PT_BR, text = "Um jogador que o servidor já viu, online ou não, pelo nome ou UUID")
                     }) PlayerData player,
                     @Arg(value = "<domain>", parser = DomainName.class, locales = {
                             @FCLocale(lang = LocaleType.EN_US, text = "A booster domain of this plugin, whole (duels:win) or short (win)"),
                             @FCLocale(lang = LocaleType.PT_BR, text = "Um domínio de booster deste plugin, inteiro (duels:win) ou curto (win)")
                     }) String domainId,
                     @Arg(value = "<factor>", locales = {
                             @FCLocale(lang = LocaleType.EN_US, text = "2 or x2 multiplies by 2; +0.5 or +50% adds 50% to the multiplier"),
                             @FCLocale(lang = LocaleType.PT_BR, text = "2 ou x2 multiplica por 2; +0.5 ou +50% soma 50% ao multiplicador")
                     }) String factor,
                     @Arg(value = "<duration>", locales = {
                             @FCLocale(lang = LocaleType.EN_US, text = "How long it lasts: 30m, 2h, 7d"),
                             @FCLocale(lang = LocaleType.PT_BR, text = "Quanto tempo dura: 30m, 2h, 7d")
                     }) FCTimeFrame duration) {
        BoosterDomain domain = domain(sender, domainId);
        if (domain == null) {
            return;
        }
        Booster.Builder builder = Booster.on(domain).forOwner(ProgressOwner.player(player.getUniqueId()))
                .lasting(Duration.ofMillis(duration.getMillis()))
                .from(Caller.of(plugin, "boosters give by " + sender.getName()));
        Booster booster;
        try {
            booster = factor(builder, factor);
        } catch (IllegalArgumentException refused) {
            BAD_FACTOR.addPlaceholder("factor", factor).send(sender);
            return;
        }
        BoosterManager.of(plugin).grant(booster).whenComplete(LocaleScope.carry((kept, failure) -> {
            if (failure != null) {
                FAILED.addPlaceholder("reason", String.valueOf(failure.getMessage())).send(sender);
            } else {
                GRANTED.addPlaceholder("booster", kept.toString()).addPlaceholder("id", kept.getId()).send(sender);
            }
        }));
    }

    @FinalCMD.SubCMD(subcmd = "list", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "List the active boosters this plugin granted, of everyone or of one player, with their ids"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Lista os boosters ativos que este plugin concedeu, de todos ou de um jogador, com os ids")
    })
    public void list(FCommandSender sender, @Arg(value = "[player]", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Only this player's boosters; everyone's when left out"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Só os boosters deste jogador; os de todos quando omitido")
    }) PlayerData player) {
        List<Booster> shown = new ArrayList<>(player == null ? activeOfEveryone() : BoosterManager.of(plugin).getActive(ProgressOwner.player(player.getUniqueId())));
        shown.sort(Comparator.comparing(Booster::getDomainId).thenComparing(each -> String.valueOf(each.getId())));
        String forPlayer = player == null ? "" : " - " + player.getName();
        if (shown.isEmpty()) {
            NONE_ACTIVE.addPlaceholder("plugin", pluginName()).addPlaceholder("for_player", forPlayer).send(sender);
            return;
        }
        LIST_HEADER.addPlaceholder("plugin", pluginName()).addPlaceholder("for_player", forPlayer).send(sender);
        for (Booster booster : shown) {
            LIST_LINE.addPlaceholder("id", booster.getId()).addPlaceholder("booster", booster.toString()).send(sender);
        }
    }

    @FinalCMD.SubCMD(subcmd = "revoke", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Take away a booster this plugin granted, by the id list shows"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Tira um booster que este plugin concedeu, pelo id que o list mostra")
    })
    public void revoke(FCommandSender sender, @Arg(value = "<id>", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "The booster's id, as list shows it"),
            @FCLocale(lang = LocaleType.PT_BR, text = "O id do booster, como o list mostra")
    }) String id) {
        UUID boosterId;
        try {
            boosterId = UUID.fromString(id.trim());
        } catch (IllegalArgumentException notAnId) {
            NO_SUCH_BOOSTER.addPlaceholder("id", id).addPlaceholder("plugin", pluginName()).send(sender);
            return;
        }
        BoosterManager.of(plugin).revoke(boosterId).whenComplete(LocaleScope.carry((had, failure) -> {
            if (failure != null) {
                FAILED.addPlaceholder("reason", String.valueOf(failure.getMessage())).send(sender);
            } else if (had) {
                REVOKED.addPlaceholder("id", boosterId).send(sender);
            } else {
                NO_SUCH_BOOSTER.addPlaceholder("id", id).addPlaceholder("plugin", pluginName()).send(sender);
            }
        }));
    }

    @FinalCMD.SubCMD(subcmd = "explain", validation = NodeVerbAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Show a player's multiplier on a domain of this plugin, each booster behind it and what was not read"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Mostra o multiplicador de um jogador num domínio deste plugin, cada booster por trás dele e o que não foi lido")
    })
    public void explain(FCommandSender sender,
                        @Arg(value = "<player>", locales = {
                                @FCLocale(lang = LocaleType.EN_US, text = "A player the server has seen, online or not, by name or UUID"),
                                @FCLocale(lang = LocaleType.PT_BR, text = "Um jogador que o servidor já viu, online ou não, pelo nome ou UUID")
                        }) PlayerData player,
                        @Arg(value = "<domain>", parser = DomainName.class, locales = {
                                @FCLocale(lang = LocaleType.EN_US, text = "A booster domain of this plugin, whole (duels:win) or short (win)"),
                                @FCLocale(lang = LocaleType.PT_BR, text = "Um domínio de booster deste plugin, inteiro (duels:win) ou curto (win)")
                        }) String domainId) {
        BoosterDomain domain = domain(sender, domainId);
        if (domain == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        //an offline player's permission booster is read from memory: load it first, never on this thread
        Boosters.loadPermissions(Collections.singletonList(domain), Collections.singletonList(playerId)).whenComplete(LocaleScope.carry((loaded, failure) -> {
            BoosterResult result = domain.resolve(playerId);
            EXPLAIN_HEADER.addPlaceholder("player", player.getName()).addPlaceholder("domain", domain.getId())
                    .addPlaceholder("multiplier", result.getMultiplier().stripTrailingZeros().toPlainString()).send(sender);
            String[] lines = result.explain().getText().split("\n");
            for (int i = 1; i < lines.length; i++) { //the first line repeats the header
                EXPLAIN_LINE.addPlaceholder("line", lines[i]).send(sender);
            }
        }));
    }

    // ------------------------------------------------------------------

    /** {@code 2}/{@code x2} multiplies, {@code +0.5}/{@code +50%} is a bonus. */
    private static Booster factor(Booster.Builder builder, String written) {
        String boost = written.trim().toLowerCase(Locale.ROOT);
        if (boost.startsWith("+")) {
            boolean percent = boost.endsWith("%");
            BigDecimal bonus = new BigDecimal(boost.substring(1, percent ? boost.length() - 1 : boost.length()));
            return builder.bonus(percent ? bonus.movePointLeft(2) : bonus).build();
        }
        return builder.factor(new BigDecimal(boost.startsWith("x") ? boost.substring(1) : boost)).build();
    }

    private List<Booster> activeOfEveryone() {
        List<Booster> active = new ArrayList<>();
        Instant now = ECClock.now();
        for (Booster booster : BoosterManager.of(plugin).getAll()) {
            if (booster.isActiveAt(now)) {
                active.add(booster);
            }
        }
        return active;
    }

    private @Nullable BoosterDomain domain(FCommandSender sender, String domainId) {
        String id = domainId.contains(":") ? domainId : pluginName() + ":" + domainId;
        BoosterDomain domain = Boosters.findDomain(id).orElse(null);
        if (domain == null || domain.getOwner() != plugin) {
            String owned = Boosters.getDomains(plugin).stream().map(BoosterDomain::getId).collect(Collectors.joining(", "));
            NO_SUCH_DOMAIN.addPlaceholder("plugin", pluginName()).addPlaceholder("domain", domainId)
                    .addPlaceholder("domains", owned.isEmpty() ? "-" : owned).send(sender);
            return null;
        }
        return domain;
    }

    private String pluginName() {
        return plugin.getMetaInfo().getName();
    }

    /** A domain of the plugin of the node that runs the verb, completed with its ids and its short names. */
    public static final class DomainName extends ArgParserString {

        public DomainName(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull List<String> tabComplete(TabContext tabContext) {
            if (!(tabContext.getExecutor() instanceof BoosterCommandNode)) {
                return Collections.emptyList();
            }
            return Boosters.getDomains(((BoosterCommandNode) tabContext.getExecutor()).plugin).stream()
                    .flatMap(domain -> Stream.of(domain.getId(), domain.getName()))
                    .filter(id -> FCStringUtil.startsWithIgnoreCase(id, tabContext.getLastWord()))
                    .collect(Collectors.toList());
        }
    }
}
