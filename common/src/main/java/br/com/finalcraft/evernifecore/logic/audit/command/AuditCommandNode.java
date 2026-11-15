package br.com.finalcraft.evernifecore.logic.audit.command;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.logic.audit.AuditQuery;
import br.com.finalcraft.evernifecore.pageviewer.PageViewer;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageTheme;
import br.com.finalcraft.evernifecore.ranking.CachePolicy;
import br.com.finalcraft.evernifecore.ranking.RankedList;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The view of one {@link AuditLog} a plugin mounts under its own command:
 * <pre>{@code
 * @FinalCMD.Node(subcmd = "log", permission = "awshop.command.awshop.log")
 * private final AuditCommandNode log = new AuditCommandNode(auditLog, "awshop.command.awshop.log");
 * }</pre>
 * {@code /awshop log [--player Steve] [--since 2h] [--kind transfer] [--page 2]}, or positionally
 * {@code /awshop log view [player] [since] [kind] [page]}, pages the entries, newest first, {@value #PAGE_SIZE} a page:
 * {@code at | kind | player | counterparty | subject amount | caller origin}. Every view needs
 * {@code <permissionBase>.view}, checked before any argument is read. Runs on the dispatching thread; the page is sent
 * when the storage answers.
 */
public class AuditCommandNode {

    @FCLocale(lang = LocaleType.EN_US, text = "&cNo player is known as &e${player}&c: give a name that joined this server, or the UUID.")
    @FCLocale(lang = LocaleType.PT_BR, text = "&cNenhum jogador conhecido como &e${player}&c: informe um nome que já entrou neste servidor, ou o UUID.")
    public static LocaleMessage UNKNOWN_PLAYER;

    @FCLocale(lang = LocaleType.EN_US, text = "&cNo audit kind &e${kind}&c: write one of ${kinds}.")
    @FCLocale(lang = LocaleType.PT_BR, text = "&cNão existe o tipo de auditoria &e${kind}&c: escreva um de ${kinds}.")
    private static LocaleMessage UNKNOWN_KIND;

    @FCLocale(lang = LocaleType.EN_US, text = "&cThe audit log of ${plugin} could not be read: ${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "&cO log de auditoria de ${plugin} não pôde ser lido: ${reason}")
    private static LocaleMessage READ_FAILED;

    @FCLocale(lang = LocaleType.EN_US, text = "&7The audit log of &e${plugin}&7 has no entry for that.")
    @FCLocale(lang = LocaleType.PT_BR, text = "&7O log de auditoria de &e${plugin}&7 não tem entrada para isso.")
    private static LocaleMessage NO_ENTRIES;

    @FCLocale(lang = LocaleType.EN_US, text = "&7${at} &8| &e${kind} &8| &f${player} &8| &f${counterparty} &8| &f${subject}${amount} &8| &7${origin}")
    @FCLocale(lang = LocaleType.PT_BR, text = "&7${at} &8| &e${kind} &8| &f${player} &8| &f${counterparty} &8| &f${subject}${amount} &8| &7${origin}")
    private static LocaleMessage LINE;

    public static final int PAGE_SIZE = 20;
    /** How many entries a view reads at most, newest first. */
    static final int VIEW_LIMIT = 500;

    private final AuditLog log;
    private final String viewPermission;

    public AuditCommandNode(AuditLog log, String permissionBase) {
        this.log = Objects.requireNonNull(log, "log");
        this.viewPermission = Objects.requireNonNull(permissionBase, "permissionBase") + ".view";
    }

    public AuditLog getLog() {
        return log;
    }

    /** {@code <permissionBase>.view}. */
    public String getViewPermission() {
        return viewPermission;
    }

    @FinalCMD.Execute(validation = AuditViewAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "The audit log, newest first: --player, --since 2h, --kind, --page"),
            @FCLocale(lang = LocaleType.PT_BR, text = "O log de auditoria, do mais novo: --player, --since 2h, --kind, --page")
    })
    public void root(FCommandSender sender, @Arg.Flag(value = "--player", locales = {
                            @FCLocale(lang = LocaleType.EN_US, text = "A player, by name or UUID"),
                            @FCLocale(lang = LocaleType.PT_BR, text = "Um jogador, pelo nome ou UUID")}) String player, @Arg.Flag(value = "--since", locales = {
                            @FCLocale(lang = LocaleType.EN_US, text = "How far back: 30m, 2h, 7d"),
                            @FCLocale(lang = LocaleType.PT_BR, text = "Até quando atrás: 30m, 2h, 7d")}) FCTimeFrame since,
                     @Arg.Flag(value = "--kind", locales = {
                            @FCLocale(lang = LocaleType.EN_US, text = "One kind of entry: transfer, gate-paid..."),
                            @FCLocale(lang = LocaleType.PT_BR, text = "Um tipo de linha: transfer, gate-paid...")}) String kind, @Arg.Flag(value = "--page", locales = {
                            @FCLocale(lang = LocaleType.EN_US, text = "Which page, from 1"),
                            @FCLocale(lang = LocaleType.PT_BR, text = "Qual página, a partir de 1")}) Integer page) {
        view(sender, player, since, kind, page);
    }

    @FinalCMD.SubCMD(subcmd = "view", validation = AuditViewAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "The audit log, newest first, of a player, since a time ago, of one kind"),
            @FCLocale(lang = LocaleType.PT_BR, text = "O log de auditoria, do mais novo, de um jogador, desde um tempo atrás, de um tipo")
    })
    public void view(FCommandSender sender, @Arg(value = "[player]", locales = {
                            @FCLocale(lang = LocaleType.EN_US, text = "A player, by name or UUID"),
                            @FCLocale(lang = LocaleType.PT_BR, text = "Um jogador, pelo nome ou UUID")}) String player, @Arg(value = "[since]", locales = {
                            @FCLocale(lang = LocaleType.EN_US, text = "How far back: 30m, 2h, 7d"),
                            @FCLocale(lang = LocaleType.PT_BR, text = "Até quando atrás: 30m, 2h, 7d")}) FCTimeFrame since, @Arg(value = "[kind]", locales = {
                            @FCLocale(lang = LocaleType.EN_US, text = "One kind of entry: transfer, gate-paid..."),
                            @FCLocale(lang = LocaleType.PT_BR, text = "Um tipo de linha: transfer, gate-paid...")}) String kind,
                     @Arg(value = "[page]", locales = {
                            @FCLocale(lang = LocaleType.EN_US, text = "Which page, from 1"),
                            @FCLocale(lang = LocaleType.PT_BR, text = "Qual página, a partir de 1")}) Integer page) {
        AuditQuery query = new AuditQuery().limit(VIEW_LIMIT);
        if (player != null) {
            UUID uuid = playerId(player);
            if (uuid == null) {
                UNKNOWN_PLAYER.addPlaceholder("player", player).send(sender);
                return;
            }
            query.player(uuid);
        }
        if (since != null) {
            query.since(ECClock.now().minus(Duration.ofMillis(since.getMillis())));
        }
        if (kind != null) {
            AuditKind parsed = kind(kind);
            if (parsed == null) {
                UNKNOWN_KIND.custom().addPlaceholder("kind", kind).addPlaceholder("kinds", kindNames()).send(sender);
                return;
            }
            query.kind(parsed);
        }
        sendPage(sender, log, query, page == null ? 1 : page);
    }

    /** The page of {@code query} over {@code log}: what this node and {@code /ecore logic audit} both send. */
    public static void sendPage(FCommandSender sender, AuditLog log, AuditQuery query, int page) {
        String plugin = log.getPlugin().getMetaInfo().getName();
        log.query(query).whenComplete((entries, failure) -> {
            if (failure != null) {
                READ_FAILED.custom().addPlaceholder("plugin", plugin).addPlaceholder("reason", String.valueOf(failure.getMessage())).send(sender);
            } else if (entries.isEmpty()) {
                NO_ENTRIES.addPlaceholder("plugin", plugin).send(sender);
            } else {
                pageOf(entries).send(page, sender);
            }
        });
    }

    private static PageViewer<AuditEntry> pageOf(List<AuditEntry> entries) {
        return PageViewer.of(RankedList.anonymous(AuditEntry.class)
                        .source(() -> entries)
                        .unlimitedEntries()
                        .orderBy(entry -> entry.getInstant().toEpochMilli()).descending()
                        .cache(CachePolicy.none())
                        .addValue("at", AuditEntry::getInstant)
                        .addValue("kind", entry -> kindName(entry.getKind()))
                        .addValue("player", entry -> name(entry.getPlayer()))
                        .addValue("counterparty", entry -> name(entry.getCounterparty()))
                        .addValue("subject", entry -> entry.getSubject() == null ? "-" : entry.getSubject())
                        .addValue("amount", entry -> entry.getAmount() == null ? "" : " " + entry.getAmount().toPlainString())
                        .addValue("origin", entry -> entry.getCaller().getOrigin())
                        .build())
                .setFormatLine(LINE)
                .setPageSize(PAGE_SIZE)
                .theme(PageTheme.classic().withTotalCount())
                .build();
    }

    private static String name(@Nullable UUID player) {
        if (player == null) {
            return "-";
        }
        String name = UUIDsController.getNameFromUUID(player);
        return name == null ? player.toString() : name;
    }

    /** A known name or a UUID; null when it is neither. */
    public static @Nullable UUID playerId(String text) {
        UUID byName = UUIDsController.getUUIDFromName(text);
        if (byName != null) {
            return byName;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    /** {@code transfer}, {@code gate-paid} or {@code GATE_PAID}; null when none matches. */
    public static @Nullable AuditKind kind(String text) {
        String name = text.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (AuditKind kind : AuditKind.values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return null;
    }

    private static String kindName(AuditKind kind) {
        return kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String kindNames() {
        List<String> names = new ArrayList<>();
        for (AuditKind kind : AuditKind.values()) {
            names.add(kindName(kind));
        }
        return String.join(", ", names);
    }
}
