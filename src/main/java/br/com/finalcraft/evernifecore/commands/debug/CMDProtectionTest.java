package br.com.finalcraft.evernifecore.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.ICustomFinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.contexts.CustomizeContext;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.protection.ProtectionAll;
import br.com.finalcraft.evernifecore.protection.integration.ProtectionHandler;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import br.com.finalcraft.evernifecore.util.FCTextUtil;
import br.com.finalcraft.evernifecore.vectors.CuboidSelection;
import br.com.finalcraft.evernifecore.version.MCVersion;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CMDProtectionTest implements ICustomFinalCMD {

    private boolean atLeastOne = false;

    @Override
    public void customize(@NotNull CustomizeContext context) {
        context.replace("%protection_plugins%", "ALL|" +
                ProtectionAll.getInstance().getProtectionHandlers().stream().map(ProtectionHandler::getName).collect(Collectors.joining("|"))
        );
        atLeastOne = ProtectionAll.getInstance().getProtectionHandlers().size() > 0;
    }

    @FCLocale(lang = LocaleType.EN_US, text = "§c§l ▶ §eThere are no protection plugins detected by EverNifeCore's API!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c§l ▶ §eO EverNifeCore não detectou nenhum plugin de proteção!")
    private static LocaleMessage NO_PROTECTIONS_DETECTED;

    @FCLocale(lang = LocaleType.EN_US, text = "§c§l ▶ §eThere is no ProtectionPlugin called %protection_plugin% detected by EverNifeCore's API!")
    @FCLocale(lang = LocaleType.PT_BR, text = "§c§l ▶ §eO EverNifeCore não detectou nenhum plugin de proteção chamado %protection_plugin%!")
    private static LocaleMessage PROTECTION_PLUGIN_NOT_FOUND;

    @FinalCMD(
            aliases = {"protectiontest"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_TESTPROTECTION
    )
    public void onCommand(Player player, @Arg(name = "[%protection_plugins%]") String procteionHandlerName) {

        if (atLeastOne == false){
            NO_PROTECTIONS_DETECTED.send(player);
            return;
        }

        ProtectionHandler protectionHandler;

        if (procteionHandlerName == null || procteionHandlerName.equals("all")){
            protectionHandler = ProtectionAll.getInstance();
        }else {
            protectionHandler = ProtectionAll.getInstance().getProtectionHandlers().stream().filter(handler -> handler.getName().equalsIgnoreCase(procteionHandlerName)).findFirst().orElse(null);
            if (protectionHandler == null){
                PROTECTION_PLUGIN_NOT_FOUND
                        .addPlaceholder("%protection_plugin%", procteionHandlerName)
                        .send(player);
                return;
            }
        }

        FancyFormatter formatter = FancyFormatter.of("§7§m" + FCTextUtil.straightLineOf(" ") + "§r");
        formatter.append("\n§2§l ▶ §bProtection Plugin(s): §6").append(
                protectionHandler != ProtectionAll.getInstance()
                        ? protectionHandler.getName()
                        : ProtectionAll.getInstance().getProtectionHandlers()
                        .stream()
                        .map(handler -> handler.getName())
                        .collect(Collectors.joining(", "))
        );
        BlockPos blockPos = BlockPos.from(player.getLocation());

        String result;

        //Basic Checks
        result = protectionHandler.canBuild(player, player.getLocation()) ? "§eYes" : "§cNo";
        formatter.append(String.format("\n§2§l ▶ §aCan Build: %s", result))
                .setHoverText(String.format("§7§l [§e   Check Result §2Can You   §7§l]\n§d ◆ §bBuild Here: %s\n\n§7 - BlockPos: §7[§6%s§7]", result, blockPos));

        result = protectionHandler.canBreak(player, player.getLocation()) ? "§eYes" : "§cNo";
        formatter.append(String.format("\n§2§l ▶ §aCan Break: %s", result))
                .setHoverText(String.format("§7§l [§e   Check Result §2Can You   §7§l]\n§d ◆ §bBreak Here: %s\n\n§7 - BlockPos: §7[§6%s§7]", result, blockPos));

        result = protectionHandler.canInteract(player, player.getLocation()) ? "§eYes" : "§cNo";
        formatter.append(String.format("\n§2§l ▶ §aCan Interact: %s", result))
                .setHoverText(String.format("§7§l [§e   Check Result §2Can You   §7§l]\n§d ◆ §bInteract Here: %s\n\n§7 - BlockPos: §7[§6%s§7]", result, blockPos));

        result = protectionHandler.canAttack(player, player) ? "§eYes" : "§cNo";
        formatter.append(String.format("\n§2§l ▶ §aCan PvP §7§o(Self Hit)§a: %s", result))
                .setHoverText(String.format("§7§l [§e   Check Result §2Can You   §7§l]\n§d ◆ §bHit Yourself (PvP) Here: %s\n\n§7 - BlockPos: §7[§6%s§7]", result, blockPos));

        result = protectionHandler.canUseAoE(player, player.getLocation(), 5) ? "§eYes" : "§cNo";
        formatter.append(String.format("\n§2§l ▶ §aCan AOE Use/Break/Build §7§o(radius 5)§a: %s", result))
                .setHoverText(String.format("§7§l [§e   Check Result §2Can You   §7§l]\n§d ◆ §bUse Area of Effect Items in Here: %s\n\n§7 - With §6Radius=5\n§7 - BlockPos: §7[§6%s§7]", result, blockPos));

        //Regional Checks
        CuboidSelection cuboidSelection = new CuboidSelection(
                BlockPos.from(player).add(BlockPos.at(10,10,10)),
                BlockPos.from(player).subtract(BlockPos.at(10,10,10))
        );
        boolean canBreakOnRegion = protectionHandler.canBreakOnRegion(player, player.getLocation().getWorld(), cuboidSelection);
        boolean canBuildOnRegion = protectionHandler.canBuildOnRegion(player, player.getLocation().getWorld(), cuboidSelection);

        result = canBreakOnRegion ? "§eYes" : "§cNo";
        formatter.append(String.format("\n§2§l ▶ §aCan CuboidSelection Break §7§o(radius 10)§a: %s", result))
                .setHoverText(String.format("§7§l [§e   Check Result §2Can You   §7§l]\n§d ◆ §bBuild AROUND Here: %s\n\n§7 - With §6Radius=10\n§7 - CuboidSelection: §7[§6%s§7]", result, cuboidSelection));

        result = canBuildOnRegion ? "§eYes" : "§cNo";
        formatter.append(String.format("\n§2§l ▶ §aCan CuboidSelection Build §7§o(radius 10)§a: %s", result))
                .setHoverText(String.format("§7§l [§e   Check Result §2Can You   §7§l]\n§d ◆ §bBreak AROUND Here: %s\n\n§7 - With §6Radius=10\n§7 - CuboidSelection: §7[§6%s§7]", result, cuboidSelection));

        formatter.send(player);

        if (MCVersion.isHigherEquals(MCVersion.v1_13)){
            cuboidSelection.setPos1(cuboidSelection.getPos1().setY(player.getLocation().getBlockY()));
            cuboidSelection.setPos2(cuboidSelection.getPos2().setY(player.getLocation().getBlockY()));
            ParticleCompat.sendParticles(canBreakOnRegion, canBuildOnRegion, player, cuboidSelection);
        }
    }

    private static List<BlockPos> getSelectionWalls(BlockPos pos1, BlockPos pos2) {
        List<BlockPos> walls = new ArrayList<>();
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                walls.add(new BlockPos(minX, y, z));
                walls.add(new BlockPos(maxX, y, z));
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                walls.add(new BlockPos(x, minY, z));
                walls.add(new BlockPos(x, maxY, z));
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                walls.add(new BlockPos(x, y, minZ));
                walls.add(new BlockPos(x, y, maxZ));
            }
        }

        return walls;
    }

    private static class ParticleCompat{
        //This is necessary to keep this class compatible with older versions without wasting too much time
        private static void sendParticles(boolean canBreakOnRegion, boolean canBuildOnRegion, Player player, CuboidSelection cuboidSelection) {
            FCScheduler.runAssync(() -> {
                Particle.DustOptions GREEN_PARTICLE = new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.0F);
                Particle.DustOptions RED_PARTICLE = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.0F);

                Particle.DustOptions SELECTED_COLOR = canBreakOnRegion && canBuildOnRegion ? GREEN_PARTICLE : RED_PARTICLE;

                List<BlockPos> selectionWalls = getSelectionWalls(cuboidSelection.getMinium(), cuboidSelection.getMaximum());

                for (int i = 0; i < 4; i++) {
                    FCScheduler.scheduleAssync(() -> {
                        selectionWalls.forEach(pos -> {
                            player.spawnParticle(Particle.REDSTONE, pos.getLocation(player.getWorld()), 1, SELECTED_COLOR);
                        });
                    }, i * 5);
                }
            });
        }
    }

}
