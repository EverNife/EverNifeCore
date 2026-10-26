package br.com.finalcraft.evernifecore.logic.delivery.command;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.delivery.ClaimResult;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.time.ECClock;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pending deliveries of the player who runs it, as a plugin mounts it under its own command:
 * <pre>{@code
 * @FinalCMD.Node(subcmd = "deliveries", permission = "myplugin.deliveries")
 * private final DeliveriesCommandNode deliveries = new DeliveriesCommandNode();
 * }</pre>
 * {@code list} shows them with origin, reason and age; {@code claim <id>|all} hands out what fits now. A delivery is
 * named by its id or the first characters of it. Runs on the dispatching thread; the answer comes when the data does.
 */
public class DeliveriesCommandNode {

    @FinalCMD.SubCMD(subcmd = "list", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Your pending deliveries, with where they come from"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Suas entregas pendentes, com a origem")
    })
    public void list(FPlayer player) {
        Deliveries.get().listOf(player.getUniqueId()).whenComplete((deliveries, failure) -> {
            if (failure != null) {
                player.sendMessage("§cYour deliveries could not be read: " + failure.getMessage());
                return;
            }
            if (deliveries.isEmpty()) {
                player.sendMessage("§7You have no pending delivery.");
                return;
            }
            player.sendMessage("§6Pending deliveries (§e" + deliveries.size() + "§6):");
            for (Delivery delivery : deliveries) {
                player.sendMessage(line(delivery));
            }
        });
    }

    @FinalCMD.SubCMD(subcmd = "claim", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Claim one pending delivery by its id, or all of them"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Resgata uma entrega pendente pelo id, ou todas")
    })
    public void claim(FPlayer player, @Arg("<id|all>") String which) {
        if (which.equalsIgnoreCase("all")) {
            Deliveries.get().claimAll(player).whenComplete((result, failure) -> report(player, result, failure));
            return;
        }
        Deliveries.get().listOf(player.getUniqueId()).whenComplete((deliveries, failure) -> {
            if (failure != null) {
                player.sendMessage("§cYour deliveries could not be read: " + failure.getMessage());
                return;
            }
            List<Delivery> matching = new ArrayList<>();
            for (Delivery delivery : deliveries) {
                if (delivery.getId().toString().startsWith(which.toLowerCase(Locale.ROOT))) {
                    matching.add(delivery);
                }
            }
            if (matching.size() != 1) {
                player.sendMessage("§c" + (matching.isEmpty() ? "No pending delivery of yours starts with §e" + which
                        : matching.size() + " of your deliveries start with §e" + which + "§c - write more of the id")
                        + "§c. §7See them with list, or claim all.");
                return;
            }
            Deliveries.get().claim(player, matching.get(0).getId()).whenComplete((result, error) -> report(player, result, error));
        });
    }

    private static void report(FPlayer player, @Nullable ClaimResult result, @Nullable Throwable failure) {
        if (failure != null) {
            player.sendMessage("§cThe claim did not go through: " + failure.getMessage());
            return;
        }
        player.sendMessage("§aClaimed §e" + result.getDelivered().size() + "§a; still pending §e" + result.getKept().size()
                + (result.getExpired().isEmpty() ? "" : "§a; expired §e" + result.getExpired().size()) + "§a.");
        for (Delivery kept : result.getKept()) {
            player.sendMessage(line(kept));
        }
    }

    /** {@code <id> from <plugin> (<reason>) <age> ago [waits for <plugin>]}. */
    public static String line(Delivery delivery) {
        String id = delivery.getId().toString().substring(0, 8);
        Duration age = Duration.between(delivery.getCreatedAt(), ECClock.now());
        String reason = delivery.getOrigin().getReason();
        return "§e" + id + " §7from §f" + delivery.getOrigin().getPlugin() + (reason.isEmpty() ? "" : " §7(" + reason + ")")
                + " §8" + age(age) + " ago" + (delivery.isSelfContained() ? "" : " §7- may wait for " + delivery.getOrigin().getPlugin());
    }

    private static String age(Duration age) {
        long minutes = Math.max(0, age.toMinutes());
        if (minutes < 60) {
            return minutes + "m";
        }
        return minutes < 60 * 24 ? (minutes / 60) + "h" : (minutes / (60 * 24)) + "d";
    }
}
