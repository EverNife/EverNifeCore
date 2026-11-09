package br.com.finalcraft.evernifecore.logic.command;

import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.audit.AuditLog;
import br.com.finalcraft.evernifecore.logic.audit.AuditQuery;
import br.com.finalcraft.evernifecore.logic.audit.LogicTrace;
import br.com.finalcraft.evernifecore.logic.audit.command.AuditCommandNode;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /ecore logic trace}: what an admin turns on to watch a player's gates, costs and effects as they run; and
 * {@code /ecore logic audit}, to read the audit log of a plugin that mounted no view of its own (debugging).
 */
public class CoreLogicCommands {

    @FCLocale(lang = LocaleType.EN_US, text = "&eLogic trace of &a${player}&e: &coff")
    @FCLocale(lang = LocaleType.PT_BR, text = "&eTrace de lógica de &a${player}&e: &cdesligado")
    private static LocaleMessage TRACE_OFF;

    @FCLocale(lang = LocaleType.EN_US, text = "&eLogic trace of &a${player}&e: &aon&7 (every plugin)")
    @FCLocale(lang = LocaleType.PT_BR, text = "&eTrace de lógica de &a${player}&e: &aligado&7 (todo plugin)")
    private static LocaleMessage TRACE_ON_EVERY_PLUGIN;

    @FCLocale(lang = LocaleType.EN_US, text = "&eLogic trace of &a${player}&e: &aon&7 (only ${plugin})")
    @FCLocale(lang = LocaleType.PT_BR, text = "&eTrace de lógica de &a${player}&e: &aligado&7 (só ${plugin})")
    private static LocaleMessage TRACE_ON_ONE_PLUGIN;

    @FCLocale(lang = LocaleType.EN_US, text = "&7 - run the same command again to stop it.")
    @FCLocale(lang = LocaleType.PT_BR, text = "&7 - rode o mesmo comando de novo para parar.")
    private static LocaleMessage TRACE_HOW_TO_STOP;

    @FCLocale(lang = LocaleType.EN_US, text = "&7 - run the same command again to stop it; it replaces the trace you had.")
    @FCLocale(lang = LocaleType.PT_BR, text = "&7 - rode o mesmo comando de novo para parar; ele substitui o trace que você tinha.")
    private static LocaleMessage TRACE_HOW_TO_STOP_REPLACED;

    @FCLocale(lang = LocaleType.EN_US, text = "&e${plugin}&c keeps no audit log: a plugin opens one with AuditLog.of(plugin, storage) on enable.")
    @FCLocale(lang = LocaleType.PT_BR, text = "&e${plugin}&c não mantém log de auditoria: um plugin abre o seu com AuditLog.of(plugin, storage) no enable.")
    private static LocaleMessage NO_AUDIT_LOG;

    @FinalCMD.SubCMD(subcmd = "trace", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Toggle a trace of every gate, cost and effect run for a player, optionally of one plugin"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Liga/desliga o trace de todo gate, custo e efeito de um jogador, opcionalmente de um plugin")
    })
    public void trace(FCommandSender sender, @Arg("<player>") FPlayer target, @Arg("[plugin]") String plugin) {
        String watcher = watcherId(sender);
        Optional<LogicTrace.Watch> current = LogicTrace.get(watcher);
        if (current.isPresent() && current.get().getPlayer().equals(target.getUniqueId()) && samePlugin(current.get().getPlugin(), plugin)) {
            LogicTrace.stop(watcher);
            TRACE_OFF.addPlaceholder("player", target.getName()).send(sender);
            return;
        }
        LogicTrace.start(watcher, target.getUniqueId(), plugin, sender::sendMessage);
        (plugin == null ? TRACE_ON_EVERY_PLUGIN : TRACE_ON_ONE_PLUGIN).custom().addPlaceholder("player", target.getName())
                .addPlaceholder("plugin", String.valueOf(plugin))
                .append(current.isPresent() ? TRACE_HOW_TO_STOP_REPLACED : TRACE_HOW_TO_STOP).send(sender);
    }

    @FinalCMD.SubCMD(subcmd = "audit", permission = PermissionNodes.EVERNIFECORE_COMMAND_LOGIC_AUDIT, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Read the audit log of a plugin, newest first, optionally of one player and since a time ago"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Lê o log de auditoria de um plugin, do mais novo, opcionalmente de um jogador e desde um tempo atrás")
    })
    public void audit(FCommandSender sender, @Arg("<plugin>") String plugin, @Arg("[player]") String player, @Arg("[since]") FCTimeFrame since,
                      @Arg.Flag("--page") Integer page) {
        Optional<AuditLog> log = AuditLog.ofIfPresent(plugin);
        if (!log.isPresent()) {
            NO_AUDIT_LOG.addPlaceholder("plugin", plugin).send(sender);
            return;
        }
        AuditQuery query = new AuditQuery().limit(AuditCommandNode.PAGE_SIZE * 25);
        if (player != null) {
            UUID uuid = AuditCommandNode.playerId(player);
            if (uuid == null) {
                AuditCommandNode.UNKNOWN_PLAYER.addPlaceholder("player", player).send(sender);
                return;
            }
            query.player(uuid);
        }
        if (since != null) {
            query.since(ECClock.now().minus(Duration.ofMillis(since.getMillis())));
        }
        AuditCommandNode.sendPage(sender, log.get(), query, page == null ? 1 : page);
    }

    /** A player watches by uuid, so its trace ends when it quits; the console by its name. */
    public static String watcherId(FCommandSender sender) {
        return sender.isPlayer() ? sender.getUniqueId().toString() : "console:" + sender.getName();
    }

    private static boolean samePlugin(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
