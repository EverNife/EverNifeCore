package br.com.finalcraft.evernifecore.logic.audit.command;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
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
    public void root(FCommandSender sender, @Arg.Flag("--player") String player, @Arg.Flag("--since") FCTimeFrame since,
                     @Arg.Flag("--kind") String kind, @Arg.Flag("--page") Integer page) {
        view(sender, player, since, kind, page);
    }

    @FinalCMD.SubCMD(subcmd = "view", validation = AuditViewAccess.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "The audit log, newest first, of a player, since a time ago, of one kind"),
            @FCLocale(lang = LocaleType.PT_BR, text = "O log de auditoria, do mais novo, de um jogador, desde um tempo atrás, de um tipo")
    })
    public void view(FCommandSender sender, @Arg("[player]") String player, @Arg("[since]") FCTimeFrame since, @Arg("[kind]") String kind,
                     @Arg("[page]") Integer page) {
        AuditQuery query = new AuditQuery().limit(VIEW_LIMIT);
        if (player != null) {
            UUID uuid = playerId(player);
            if (uuid == null) {
                sender.sendMessage("§cNo player is known as §e" + player + "§c: give a name that joined this server, or the UUID.");
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
                sender.sendMessage("§cNo audit kind §e" + kind + "§c: write one of " + kindNames() + ".");
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
                sender.sendMessage("§cThe audit log of " + plugin + " could not be read: " + failure.getMessage());
            } else if (entries.isEmpty()) {
                sender.sendMessage("§7The audit log of §e" + plugin + "§7 has no entry for that.");
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
                        .addValue("line", AuditCommandNode::line)
                        .build())
                .setFormatLine(FancyText.of("${line}"))
                .setPageSize(PAGE_SIZE)
                .theme(PageTheme.classic().withTotalCount())
                .build();
    }

    /** {@code at | kind | player | counterparty | subject amount | caller origin}. */
    public static String line(AuditEntry entry) {
        return "§7" + entry.getInstant() + " §8| §e" + kindName(entry.getKind())
                + " §8| §f" + name(entry.getPlayer()) + " §8| §f" + name(entry.getCounterparty())
                + " §8| §f" + (entry.getSubject() == null ? "-" : entry.getSubject())
                + (entry.getAmount() == null ? "" : " " + entry.getAmount().toPlainString())
                + " §8| §7" + entry.getCaller().getOrigin();
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
