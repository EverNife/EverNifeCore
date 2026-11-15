package br.com.finalcraft.evernifecore.logic.delivery.command;

import br.com.finalcraft.evernifecore.locale.LocaleScope;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.parsers.ArgParserString;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser.TabContext;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.locale.SendCustom;
import br.com.finalcraft.evernifecore.logic.delivery.ClaimResult;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.screen.ScreenKey;
import br.com.finalcraft.evernifecore.screen.ScreenRequest;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.util.FCStringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The pending deliveries of the player who runs it, as a plugin mounts it under its own command:
 * <pre>{@code
 * @FinalCMD.Node(subcmd = "deliveries", permission = "myplugin.deliveries")
 * private final DeliveriesCommandNode deliveries = new DeliveriesCommandNode();
 * }</pre>
 * {@code list} opens the deliveries screen ({@code ScreenKey.DELIVERIES}: click one to claim it, or claim them all), and
 * where no screen opens says them in the chat with origin, reason and age; {@code claim <id>|all} hands out what fits now. A delivery is
 * named by its id or the first characters of it; tab completes the ids {@code list} shows, and {@code all}. Runs on the dispatching thread; the answer comes when the data does.
 */
public class DeliveriesCommandNode {

    @FCLocale(lang = LocaleType.EN_US, text = "§cYour deliveries could not be read: ${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cSuas entregas não puderam ser lidas: ${reason}")
    private static LocaleMessage READ_FAILED;

    @FCLocale(lang = LocaleType.EN_US, text = "§7You have no pending delivery.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§7Você não tem entrega pendente.")
    private static LocaleMessage NONE_PENDING;

    @FCLocale(lang = LocaleType.EN_US, text = "§6Pending deliveries (§e${count}§6):")
    @FCLocale(lang = LocaleType.PT_BR, text = "§6Entregas pendentes (§e${count}§6):")
    private static LocaleMessage LIST_HEADER;

    @FCLocale(lang = LocaleType.EN_US, text = "§cNo pending delivery of yours starts with §e${id}§c. §7See them with list, or claim all.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cNenhuma entrega pendente sua começa com §e${id}§c. §7Veja-as com list, ou resgate todas com claim all.")
    private static LocaleMessage NO_MATCH;

    @FCLocale(lang = LocaleType.EN_US, text = "§c${count} of your deliveries start with §e${id}§c - write more of the id. §7See them with list, or claim all.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c${count} das suas entregas começam com §e${id}§c - escreva mais do id. §7Veja-as com list, ou resgate todas com claim all.")
    private static LocaleMessage AMBIGUOUS;

    @FCLocale(lang = LocaleType.EN_US, text = "§cThe claim did not go through: ${reason}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cO resgate não foi concluído: ${reason}")
    private static LocaleMessage CLAIM_FAILED;

    @FCLocale(lang = LocaleType.EN_US, text = "§aClaimed §e${delivered}§a; still pending §e${kept}§a.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§aResgatadas §e${delivered}§a; ainda pendentes §e${kept}§a.")
    private static LocaleMessage CLAIMED;

    @FCLocale(lang = LocaleType.EN_US, text = "§aClaimed §e${delivered}§a; still pending §e${kept}§a; expired §e${expired}§a.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§aResgatadas §e${delivered}§a; ainda pendentes §e${kept}§a; expiradas §e${expired}§a.")
    private static LocaleMessage CLAIMED_SOME_EXPIRED;

    @FCLocale(lang = LocaleType.EN_US, text = "§e${id} §7from §f${plugin} §8${age} ago")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e${id} §7de §f${plugin} §8há ${age}")
    private static LocaleMessage LINE;

    @FCLocale(lang = LocaleType.EN_US, text = "§e${id} §7from §f${plugin} §7(${reason}) §8${age} ago")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e${id} §7de §f${plugin} §7(${reason}) §8há ${age}")
    private static LocaleMessage LINE_WITH_REASON;

    @FCLocale(lang = LocaleType.EN_US, text = "§e${id} §7from §f${plugin} §8${age} ago §7- may wait for ${plugin}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e${id} §7de §f${plugin} §8há ${age} §7- pode esperar por ${plugin}")
    private static LocaleMessage LINE_MAY_WAIT;

    @FCLocale(lang = LocaleType.EN_US, text = "§e${id} §7from §f${plugin} §7(${reason}) §8${age} ago §7- may wait for ${plugin}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§e${id} §7de §f${plugin} §7(${reason}) §8há ${age} §7- pode esperar por ${plugin}")
    private static LocaleMessage LINE_WITH_REASON_MAY_WAIT;

    @FinalCMD.SubCMD(subcmd = "list", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Opens your pending deliveries: click one to claim it"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Abre suas entregas pendentes: clique numa para resgatar")
    })
    public void list(FPlayer player) {
        Screens.open(player, ScreenKey.DELIVERIES, ScreenRequest.of(null)).whenComplete(LocaleScope.carry((view, refused) -> {
            if (refused != null) {
                say(player); //no renderer for this player (a platform without screens): the chat still lists them
            }
        }));
    }

    private static void say(FPlayer player) {
        Deliveries.get().listOf(player.getUniqueId()).whenComplete(LocaleScope.carry((deliveries, failure) -> {
            if (failure != null) {
                READ_FAILED.addPlaceholder("reason", String.valueOf(failure.getMessage())).send(player);
                return;
            }
            if (deliveries.isEmpty()) {
                NONE_PENDING.send(player);
                return;
            }
            LIST_HEADER.addPlaceholder("count", deliveries.size()).send(player);
            for (Delivery delivery : deliveries) {
                line(delivery).send(player);
            }
        }));
    }

    @FinalCMD.SubCMD(subcmd = "claim", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Claim one pending delivery by its id, or all of them"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Resgata uma entrega pendente pelo id, ou todas")
    })
    public void claim(FPlayer player, @Arg(value = "<id>", parser = PendingId.class, locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "The id list shows (its first characters are enough), or all"),
            @FCLocale(lang = LocaleType.PT_BR, text = "O id que o list mostra (os primeiros caracteres bastam), ou all")
    }) String which) {
        if (which.equalsIgnoreCase("all")) {
            Deliveries.get().claimAll(player).whenComplete(LocaleScope.carry((result, failure) -> report(player, result, failure)));
            return;
        }
        Deliveries.get().listOf(player.getUniqueId()).whenComplete(LocaleScope.carry((deliveries, failure) -> {
            if (failure != null) {
                READ_FAILED.addPlaceholder("reason", String.valueOf(failure.getMessage())).send(player);
                return;
            }
            List<Delivery> matching = new ArrayList<>();
            for (Delivery delivery : deliveries) {
                if (delivery.getId().toString().startsWith(which.toLowerCase(Locale.ROOT))) {
                    matching.add(delivery);
                }
            }
            if (matching.isEmpty()) {
                NO_MATCH.addPlaceholder("id", which).send(player);
                return;
            }
            if (matching.size() > 1) {
                AMBIGUOUS.custom().addPlaceholder("count", matching.size()).addPlaceholder("id", which).send(player);
                return;
            }
            Deliveries.get().claim(player, matching.get(0).getId()).whenComplete(LocaleScope.carry((result, error) -> report(player, result, error)));
        }));
    }

    private static void report(FPlayer player, @Nullable ClaimResult result, @Nullable Throwable failure) {
        if (failure != null) {
            CLAIM_FAILED.addPlaceholder("reason", String.valueOf(failure.getMessage())).send(player);
            return;
        }
        (result.getExpired().isEmpty() ? CLAIMED : CLAIMED_SOME_EXPIRED).custom()
                .addPlaceholder("delivered", result.getDelivered().size())
                .addPlaceholder("kept", result.getKept().size())
                .addPlaceholder("expired", result.getExpired().size())
                .send(player);
        for (Delivery kept : result.getKept()) {
            line(kept).send(player);
        }
    }

    private static SendCustom line(Delivery delivery) {
        String reason = delivery.getOrigin().getReason();
        LocaleMessage message = reason.isEmpty()
                ? (delivery.isSelfContained() ? LINE : LINE_MAY_WAIT)
                : (delivery.isSelfContained() ? LINE_WITH_REASON : LINE_WITH_REASON_MAY_WAIT);
        return message.custom()
                .addPlaceholder("id", shortId(delivery))
                .addPlaceholder("plugin", delivery.getOrigin().getPlugin())
                .addPlaceholder("reason", reason)
                .addPlaceholder("age", age(Duration.between(delivery.getCreatedAt(), ECClock.now())));
    }

    /** The id as {@code list} shows it and {@code claim} completes it. */
    private static String shortId(Delivery delivery) {
        return delivery.getId().toString().substring(0, 8);
    }

    private static String age(Duration age) {
        long minutes = Math.max(0, age.toMinutes());
        if (minutes < 60) {
            return minutes + "m";
        }
        return minutes < 60 * 24 ? (minutes / 60) + "h" : (minutes / (60 * 24)) + "d";
    }

    /** A pending delivery of the player who completes it, by the id {@code list} shows, or {@code all}. */
    public static final class PendingId extends ArgParserString {

        public PendingId(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull List<String> tabComplete(TabContext tabContext) {
            List<String> ids = new ArrayList<>();
            ids.add("all");
            FPlayer player = tabContext.getPlayer();
            if (player != null) {
                //tab runs on the server thread once per keystroke: answer from what is loaded, never wait for storage
                CompletableFuture<List<Delivery>> pending = Deliveries.get().listOf(player.getUniqueId());
                if (pending.isDone() && !pending.isCompletedExceptionally()) {
                    for (Delivery delivery : pending.join()) {
                        ids.add(shortId(delivery));
                    }
                }
            }
            ids.removeIf(id -> !FCStringUtil.startsWithIgnoreCase(id, tabContext.getLastWord()));
            return ids;
        }
    }
}
