package br.com.finalcraft.evernifecore.protection.integration.imp;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.protection.integration.ProtectionHandler;
import br.com.finalcraft.evernifecore.reflection.MethodInvoker;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.vectors.CuboidSelection;
import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.claim.*;
import com.griefdefender.lib.flowpowered.math.vector.Vector3i;
import com.griefdefender.loader.BukkitLoaderPlugin;
import com.griefdefender.loader.LoaderBootstrap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.util.UUID;
import java.util.stream.Collectors;

public class GriefDefenderHandler implements ProtectionHandler {

    private Constructor GDClaim_constructor;
    private MethodInvoker<ClaimResult> checkArea;

    public GriefDefenderHandler() {
        try {
            //Fuck the JarInJarClassLoader, i need this...
            BukkitLoaderPlugin bukkitLoaderPlugin = (BukkitLoaderPlugin) Bukkit.getPluginManager().getPlugin(getName());
            LoaderBootstrap bootstrap = (LoaderBootstrap) FCReflectionUtil.getField(bukkitLoaderPlugin.getClass(), "plugin").get(bukkitLoaderPlugin);
            Class GDClaim_class = Class.forName("com.griefdefender.claim.GDClaim", false, bootstrap.getClass().getClassLoader());
            checkArea = FCReflectionUtil.getMethod(GDClaim_class, "checkArea");
            GDClaim_constructor = GDClaim_class.getDeclaredConstructor(
                    World.class,
                    Vector3i.class,
                    Vector3i.class,
                    ClaimType.class,
                    UUID.class,
                    boolean.class
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return "GriefDefender";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        Claim claim = GriefDefender.getCore().getClaimAt(location);
        if (claim == null || claim.isWilderness()) {
            return true;
        }

        return isOwnerOrTrusted(claim, player.getUniqueId(), TrustTypes.BUILDER);
    }

    @Override
    public boolean canBreak(Player player, Location location) {
        Claim claim = GriefDefender.getCore().getClaimAt(location);
        if (claim == null || claim.isWilderness()) {
            return true;
        }

        return isOwnerOrTrusted(claim, player.getUniqueId(), TrustTypes.BUILDER);
    }

    @Override
    public boolean canInteract(Player player, Location location) {
        Claim claim = GriefDefender.getCore().getClaimAt(location);
        if (claim == null || claim.isWilderness()) {
            return true;
        }

        return isOwnerOrTrusted(claim, player.getUniqueId(), TrustTypes.CONTAINER);
    }

    private final ItemStack STONE = new ItemStack(Material.STONE);
    @Override
    public boolean canAttack(Player player, Entity entity) {
        Claim claimAtVictim = GriefDefender.getCore().getClaimAt(entity.getLocation());

        return claimAtVictim.canHurtEntity(player, STONE, entity, null);
    }

    @Override
    public boolean canUseAoE(Player player, Location location, int range) {
        return canBuildOnRegion(
                player,
                location.getWorld(),
                CuboidSelection.of(
                        BlockPos.from(location).add(-range, 0, -range),
                        BlockPos.from(location).add(range, 255, range)
                )
        );

    }

    @Override
    public boolean canBuildOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        cuboidSelection = cuboidSelection.clone().expandVert();
        Location firstCorner = cuboidSelection.getPos1().getLocation(world);
        Claim claim = GriefDefender.getCore().getClaimAt(firstCorner);

        if (claim != null && !claim.isWilderness()){
            EverNifeCore.getLog().info("Claims [%s] INSIDE: result: %s",
                    claim.getUniqueId().toString().substring(0,8) + ":" + claim.getType(),
                    isOwnerOrTrusted(claim, player.getUniqueId(), TrustTypes.BUILDER)
            );
            if (!isOwnerOrTrusted(claim, player.getUniqueId(), TrustTypes.BUILDER)){
                return false; //He cannot even place blocks on the first block checked
            }

            if (claim.contains(cuboidSelection.getPos1().getX(), cuboidSelection.getPos1().getY(), cuboidSelection.getPos1().getZ())
                    && claim.contains(cuboidSelection.getPos2().getX(), cuboidSelection.getPos2().getY(), cuboidSelection.getPos2().getZ())){
                //If in the entire selection is inside only one claim, we already know he can build there!
                return true;
            }

            if (claim.getParent() != null) {
                Claim parentClaim = claim.getParent();
                // the player is in a sub-claim
                if (!isOwnerOrTrusted(parentClaim, player.getUniqueId(), TrustTypes.BUILDER)){
                    //not even on the father claim he has permission!
                    return false;
                }

                if (parentClaim.contains(cuboidSelection.getPos1().getX(), cuboidSelection.getPos1().getY(), cuboidSelection.getPos1().getZ())
                        && parentClaim.contains(cuboidSelection.getPos2().getX(), cuboidSelection.getPos2().getY(), cuboidSelection.getPos2().getZ())) {
                    //If in the entire selection is inside only one parent-claim, we already know he can build there!
                    return true;
                }
            }
        }

        //Well, as we are not inside a SINGLE_CLAIM, lest check if there are several claims inside the selection
        try {
            Object temporaryClaim = GDClaim_constructor.newInstance(
                    world,
                    new Vector3i(cuboidSelection.getMinium().getX(),cuboidSelection.getMinium().getY(),cuboidSelection.getMinium().getZ()),
                    new Vector3i(cuboidSelection.getMaximum().getX(),cuboidSelection.getMaximum().getY(),cuboidSelection.getMaximum().getZ()),
                    ClaimTypes.BASIC,
                    player.getUniqueId(),
                    false
            );

            ClaimResult claimResult = checkArea.invoke(temporaryClaim, false);

            if (claimResult.getResultType() == ClaimResultType.OVERLAPPING_CLAIM){

                for (Claim overlapedClaim : claimResult.getClaims()) {
                    if (!isOwnerOrTrusted(overlapedClaim, player.getUniqueId(), TrustTypes.BUILDER)){
                        EverNifeCore.getLog().info("Claims [%s] OVERLAPPING_CLAIM: result: %s",
                                claimResult.getClaims().stream().map(claim1 -> claim1.getUniqueId().toString().substring(0,8) + ":" + claim1.getType()).collect(Collectors.joining(", ")),
                                false
                        );
                        return false;
                    }
                }
            }

            EverNifeCore.getLog().info("Claims [%s] OVERLAPPING_CLAIM: result: %s",
                    claimResult.getClaims().stream().map(claim1 -> claim1.getUniqueId() + "").collect(Collectors.joining(", ")),
                    true
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    private boolean isOwnerOrTrusted(Claim claim, UUID playerUUID, TrustType trustType){
        return playerUUID.equals(claim.getOwnerUniqueId()) || claim.isUserTrusted(playerUUID, trustType);
    }

    @Override
    public boolean canBreakOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        return canBuildOnRegion(player, world, cuboidSelection);
    }

}
