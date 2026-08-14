package br.com.finalcraft.evernifecore.commands.misc;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.CooldownBucket;
import br.com.finalcraft.evernifecore.cooldown.CooldownEntry;
import br.com.finalcraft.evernifecore.cooldown.player.PlayerCooldown;
import br.com.finalcraft.evernifecore.cooldown.player.PlayerCooldownsLocal;
import br.com.finalcraft.evernifecore.cooldown.player.PlayerCooldownsNetwork;
import br.com.finalcraft.evernifecore.cooldown.server.ServerCooldownRow;
import br.com.finalcraft.evernifecore.cooldown.server.ServerCooldowns;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.everylibs.util.FCTimeUtil;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@FinalCMD(
        aliases = {"eccooldown", "eccooldowns"},
        permission = PermissionNodes.EVERNIFECORE_COMMAND_COOLDOWN
)
public class CMDECCooldown {

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe cooldown §7[§2${cooldown}§7]§c is not in cooldown!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO cooldown §7[§2${cooldown}§7]§c não está em cooldown!")
    private static LocaleMessage COOLDOWN_NOT_IN_COOLDOWN;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cThe cooldown §7[§2${cooldown}§7]§c was successfully removed!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cO cooldown §7[§2${cooldown}§7]§c foi removido com sucesso!")
    private static LocaleMessage COOLDOWN_REMOVED;

    @FinalCMD.SubCMD(
            subcmd = "reset",
            usage = "<CooldownID>",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Reset an specific cooldown!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Reseta um cooldown especifico!")
            }
    )
    public void reset(FCommandSender sender, MultiArgumentos argumentos, HelpLine helpLine) {

        if (argumentos.emptyArgs(0)){
            helpLine.sendTo(sender);
            return;
        }

        Cooldown cooldown = Cooldown.of(argumentos.getStringArg(0));
        if (!cooldown.isInCooldown()){
            COOLDOWN_NOT_IN_COOLDOWN.addPlaceholder("cooldown", cooldown.getIdentifier()).send(sender);
            return;
        }

        cooldown.stop();
        COOLDOWN_REMOVED.addPlaceholder("cooldown", cooldown.getIdentifier()).send(sender);
    }

    @FinalCMD.SubCMD(
            subcmd = "resetplayer",
            usage = "<player> <CooldownID>",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Reset an specific player cooldown!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Reseta um cooldown especifico de um jogador!")
            }
    )
    public void resetPlayer(FCommandSender sender, MultiArgumentos argumentos, HelpLine helpLine) {

        if (argumentos.emptyArgs(0,1)){
            helpLine.sendTo(sender);
            return;
        }

        PlayerData playerData = argumentos.get(0).getPlayerData();

        if (playerData == null){
            FCMessageUtil.playerDataNotFound(sender, argumentos.getStringArg(0));
            return;
        }

        //the target may be offline, so its cooldown bucket may have to be read from the backend: resolve
        //async and bridge back to the main thread instead of blocking it with a .join()
        PlayerController.whenCompleteOnMainThread(playerData.getCooldown(argumentos.getStringArg(1)), (cooldown, error) -> {
            if (error != null){
                EverNifeCore.getLog().severe("Failed to read the cooldowns of {}", playerData.getName(), error);
                return;
            }
            if (!cooldown.isInCooldown()){
                COOLDOWN_NOT_IN_COOLDOWN.addPlaceholder("cooldown", cooldown.getIdentifier()).send(sender);
                return;
            }
            cooldown.stop();
            COOLDOWN_REMOVED.addPlaceholder("cooldown", cooldown.getIdentifier()).send(sender);
        });
    }


    @FCLocale(lang = LocaleType.EN_US, text = "§2§l ▶ §aAll NonPlayer Cooldowns have been reloaded!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§2§l ▶ §aTodos os NonPLayer Cooldowns foram recarregados!")
    private static LocaleMessage COOLDOWN_RELOAD;

    @FinalCMD.SubCMD(
            subcmd = "reload",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Reload all generic cooldown (NonPlayer) !"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Recarrega todos os cooldowns genéricos (NonPlayer)!")
            }
    )
    public void reload(FCommandSender sender) {
        ConfigManager.reloadCooldownConfig();
        COOLDOWN_RELOAD.send(sender);
    }


    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §7Cooldowns of §a${player}§7:")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §7Cooldowns de §a${player}§7:")
    private static LocaleMessage COOLDOWN_VIEW_PLAYER_HEADER;

    @FinalCMD.SubCMD(
            subcmd = "viewplayer",
            usage = "<player>",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "List a player's cooldowns (local and network)!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Lista os cooldowns de um jogador (local e de rede)!")
            }
    )
    public void viewPlayer(FCommandSender sender, MultiArgumentos argumentos, HelpLine helpLine) {

        if (argumentos.emptyArgs(0)){
            helpLine.sendTo(sender);
            return;
        }

        PlayerData playerData = argumentos.get(0).getPlayerData();

        if (playerData == null){
            FCMessageUtil.playerDataNotFound(sender, argumentos.getStringArg(0));
            return;
        }

        //an offline target's buckets may still have to be read from the backend: resolve both async and
        //render back on the main thread instead of blocking it
        CompletableFuture<PlayerCooldownsLocal> localFuture = playerData.getPDSection(PlayerCooldownsLocal.class);
        CompletableFuture<PlayerCooldownsNetwork> networkFuture = playerData.getAccountSection(PlayerCooldownsNetwork.class);

        PlayerController.whenCompleteOnMainThread(
                localFuture.thenCombine(networkFuture, AbstractMap.SimpleImmutableEntry::new),
                (buckets, error) -> {
                    if (error != null){
                        EverNifeCore.getLog().severe("Failed to read the cooldowns of {}", playerData.getName(), error);
                        return;
                    }
                    COOLDOWN_VIEW_PLAYER_HEADER.addPlaceholder("player", playerData.getName()).send(sender);
                    sendBucketLines(sender, "LOCAL", buckets.getKey());
                    sendBucketLines(sender, "NETWORK", buckets.getValue());
                });
    }


    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §7Server cooldowns:")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §7Cooldowns do servidor:")
    private static LocaleMessage COOLDOWN_VIEW_SERVER_HEADER;

    @FinalCMD.SubCMD(
            subcmd = "viewserver",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "List the server-owned cooldowns (local and network)!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Lista os cooldowns do servidor (local e de rede)!")
            }
    )
    public void viewServer(FCommandSender sender) {
        COOLDOWN_VIEW_SERVER_HEADER.send(sender);

        sender.sendMessage("§7 §oLOCAL§7:");
        boolean anyLocal = false;
        for (Cooldown cooldown : Cooldown.getMapOfCooldowns().values()){
            sender.sendMessage(describeCooldown(cooldown.getIdentifier(), cooldown.getEntry()));
            anyLocal = true;
        }
        if (!anyLocal) sender.sendMessage("§8   (none)");

        sender.sendMessage("§7 §oNETWORK§7:");
        ServerCooldowns serverCooldowns = ServerCooldowns.get();
        boolean anyNetwork = false;
        if (serverCooldowns != null){
            for (ServerCooldownRow row : serverCooldowns.getManager().cachedValues()){
                sender.sendMessage(describeCooldown(row.getIdentifier(), row.getEntry()));
                anyNetwork = true;
            }
        }
        if (!anyNetwork) sender.sendMessage("§8   (none)");
    }

    private void sendBucketLines(FCommandSender sender, String reach, CooldownBucket bucket) {
        Map<String, CooldownEntry> persisted = bucket.getPersistedCooldowns();
        Map<String, CooldownEntry> memoryOnly = bucket.getTransientCooldowns();
        sender.sendMessage("§7 §o" + reach + "§7:");
        if (persisted.isEmpty() && memoryOnly.isEmpty()){
            sender.sendMessage("§8   (none)");
            return;
        }
        for (Map.Entry<String, CooldownEntry> entry : persisted.entrySet()){
            sender.sendMessage(describeCooldown(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, CooldownEntry> entry : memoryOnly.entrySet()){
            sender.sendMessage(describeCooldown(entry.getKey(), entry.getValue()));
        }
    }

    //One diagnostic line for a stored cooldown: a zeroed anchor is a stop tombstone, a past deadline is
    //an entry still kept by the retention horizon, otherwise the remaining time.
    private static String describeCooldown(String identifier, CooldownEntry entry) {
        String status;
        if (entry.getTimeStart() == 0){
            status = "§8stopped";
        } else {
            long timeLeft = entry.getTimeStart() + entry.getTimeDuration() - System.currentTimeMillis();
            status = timeLeft >= 1
                    ? "§e" + FCTimeFrame.of(timeLeft).getFormattedDiscursive("§6", "§e")
                    : "§8expired";
        }
        String kind = entry.isPersist() ? "§2persistent" : "§8memory";
        return "§7   - §a" + identifier + " §7» " + status + " §7[" + kind + "§7]";
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§a§l ▶ §7Set §b${reach} §7cooldown §a${cooldown} §7for §e${time}§7.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§a§l ▶ §7Cooldown §b${reach} §a${cooldown} §7definido por §e${time}§7.")
    private static LocaleMessage COOLDOWN_SET;

    @FCLocale(lang = LocaleType.EN_US, text = "§e§l ▶ §cInvalid duration §7'${input}'§c. Use e.g. §a5d§7, §a3h30m§7, §a90s §7or a plain number of seconds.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e§l ▶ §cDuração inválida §7'${input}'§c. Use ex.: §a5d§7, §a3h30m§7, §a90s §7ou um número de segundos.")
    private static LocaleMessage COOLDOWN_INVALID_DURATION;

    @FinalCMD.SubCMD(
            subcmd = "set",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Start a server cooldown for a given duration!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Inicia um cooldown do servidor por uma duração!")
            }
    )
    public void set(FCommandSender sender,
                     @Arg("<CooldownID>") String cooldownId,
                     @Arg("<duration>") String duration,
                     @Arg.Flag(value = "--network", aliases = "-n", def = "false", locales = {
                             @FCLocale(lang = LocaleType.EN_US, text = "Apply network-wide (follows the account across servers)."),
                             @FCLocale(lang = LocaleType.PT_BR, text = "Aplica na rede inteira (segue a conta entre servidores).")
                     })
                     Boolean network) {

        Long millis = parseDurationMillis(duration);
        if (millis == null){
            COOLDOWN_INVALID_DURATION.addPlaceholder("input", duration).send(sender);
            return;
        }

        Cooldown cooldown = network ? Cooldown.network(cooldownId) : Cooldown.of(cooldownId);
        cooldown.setPersist(true).startWith(millis, TimeUnit.MILLISECONDS);
        sendSetConfirmation(sender, network ? "SERVER · NETWORK" : "SERVER · LOCAL", cooldown.getIdentifier(), cooldown.getEntry(), "viewserver");
    }

    @FinalCMD.SubCMD(
            subcmd = "setplayer",
            locales = {
                    @FCLocale(lang = LocaleType.EN_US, text = "Start a player cooldown for a given duration!"),
                    @FCLocale(lang = LocaleType.PT_BR, text = "Inicia um cooldown de um jogador por uma duração!")
            }
    )
    public void setPlayer(FCommandSender sender,
                           @Arg("<player>") PlayerData playerData,
                           @Arg("<CooldownID>") String cooldownId,
                           @Arg("<duration>") String duration,
                           @Arg.Flag(value = "--network", aliases = "-n", def = "false", locales = {
                                   @FCLocale(lang = LocaleType.EN_US, text = "Apply network-wide (follows the account across servers)."),
                                   @FCLocale(lang = LocaleType.PT_BR, text = "Aplica na rede inteira (segue a conta entre servidores).")
                           })
                           Boolean network) {

        Long millis = parseDurationMillis(duration);
        if (millis == null){
            COOLDOWN_INVALID_DURATION.addPlaceholder("input", duration).send(sender);
            return;
        }

        //the target may be offline, so the bucket may have to be read from the backend: resolve async
        CompletableFuture<PlayerCooldown> cooldownFuture = network ? playerData.getNetworkCooldown(cooldownId) : playerData.getCooldown(cooldownId);
        PlayerController.whenCompleteOnMainThread(cooldownFuture, (cooldown, error) -> {
            if (error != null){
                EverNifeCore.getLog().severe("Failed to read the cooldowns of {}", playerData.getName(), error);
                return;
            }
            if (network){
                //network handles are born persistent, so a bare startWith already reaches the shared backend
                cooldown.startWith(millis, TimeUnit.MILLISECONDS);
            }else {
                cooldown.setPersist(true).startWith(millis, TimeUnit.MILLISECONDS);
            }
            sendSetConfirmation(sender, network ? "PLAYER · NETWORK" : "PLAYER · LOCAL", cooldown.getIdentifier(), cooldown.getEntry(), "viewplayer " + playerData.getName());
        });
    }

    //A hover-and-click confirmation: the localized line names the reach and the discursive duration; the
    //hover repeats it with the reach, and clicking the line runs the matching view command.
    private void sendSetConfirmation(FCommandSender sender, String reach, String identifier, CooldownEntry entry, String viewCommand) {
        String discursive = FCTimeFrame.of(entry.getTimeStart() + entry.getTimeDuration() - System.currentTimeMillis())
                .getFormattedDiscursive("§6", "§e");
        String hover = "§b" + reach + "\n§7» §e" + discursive + "\n§8/eccooldown " + viewCommand;
        COOLDOWN_SET
                .addPlaceholder("reach", reach)
                .addPlaceholder("cooldown", identifier)
                .addPlaceholder("time", discursive)
                .setHover(hover)
                .setClickCommand("/eccooldown " + viewCommand)
                .send(sender);
    }

    //A plain number is read as seconds (cooldowns are almost always seconds); anything else goes through
    //the human-duration parser (5d, 3h30m, 90s, ...). Returns null when the input is not a valid duration.
    private static Long parseDurationMillis(String raw) {
        try {
            long millis = raw.matches("\\d+")
                    ? TimeUnit.SECONDS.toMillis(Long.parseLong(raw))
                    : FCTimeUtil.toMillis(raw);
            return millis > 0 ? millis : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
