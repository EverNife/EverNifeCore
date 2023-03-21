package br.com.finalcraft.evernifecore.protection.integration.imp;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.protection.integration.ProtectionHandler;
import br.com.finalcraft.evernifecore.vectors.CuboidSelection;
import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.claim.TrustTypes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GriefDefenderHandler implements ProtectionHandler {

    @Override
    public String getName() {
        return "GriefDefender";
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        Claim claim = GriefDefender.getCore().getClaimAt(location);
        if (claim == null) {
            return true;
        }

        return claim.canPlace(player, Material.STONE, location, null);
    }

    @Override
    public boolean canBreak(Player player, Location location) {
        Claim claim = GriefDefender.getCore().getClaimAt(location);
        if (claim == null) {
            return true;
        }

        return claim.canBreak(player, location, null);
    }

    @Override
    public boolean canInteract(Player player, Location location) {
        Claim claim = GriefDefender.getCore().getClaimAt(location);
        if (claim == null) {
            return true;
        }

        return claim.canUseBlock(player, location, null, TrustTypes.ACCESSOR);
    }

    private final ItemStack STONE = new ItemStack(Material.STONE);
    @Override
    public boolean canAttack(Player player, Entity entity) {
        Claim claimAtVictim = GriefDefender.getCore().getClaimAt(entity.getLocation());

        return claimAtVictim.canHurtEntity(player, STONE, entity, null);
    }

    @Override
    public boolean canUseAoE(Player player, Location location, int range) {
        return canBreakOnRegion(
                player,
                location.getWorld(),
                CuboidSelection.of(
                        BlockPos.from(location).add(-range, location.getBlockY(), -range),
                        BlockPos.from(location).add(range, 255 - location.getBlockY(), range)
                )
        );

    }

    @Override
    public boolean canBuildOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        Location firstCorner = cuboidSelection.getPos1().getLocation(world);
        Claim claim = GriefDefender.getCore().getClaimAt(firstCorner);

        if (claim != null){
            if (!claim.canPlace(player, Material.STONE, firstCorner, null)){
                return false;//He cannot even place blocks on the first block checked
            }

            if (claim.contains(cuboidSelection.getPos1().getX(), cuboidSelection.getPos1().getY(), cuboidSelection.getPos1().getZ())
                    && claim.contains(cuboidSelection.getPos2().getX(), cuboidSelection.getPos2().getY(), cuboidSelection.getPos2().getZ())){
                //If in the entire selection is inside only one claim, we already know he can build there!
                return true;
            }

            if (claim.getParent() != null) {
                Claim parentClaim = claim.getParent();
                // you're on a subdivision
                if (!parentClaim.canPlace(player, Material.STONE, firstCorner, null)) {
                    // you have no build permission on the top claim... disallow.
                    return false;
                }

                if (parentClaim.contains(cuboidSelection.getPos1().getX(), cuboidSelection.getPos1().getY(), cuboidSelection.getPos1().getZ())
                        && parentClaim.contains(cuboidSelection.getPos2().getX(), cuboidSelection.getPos2().getY(), cuboidSelection.getPos2().getZ())) {
                    //If in the entire selection is inside only one parent-claim, we already know he can build there!
                    return true;
                }
            }
        }

        return true;
    }

    @Override
    public boolean canBreakOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
        Location firstCorner = cuboidSelection.getPos1().getLocation(world);
        Claim claim = GriefDefender.getCore().getClaimAt(firstCorner);

        if (claim != null){
            if (!claim.canBreak(player, firstCorner, null)){
                return false;//He cannot even break blocks on the first block checked
            }

            if (claim.contains(cuboidSelection.getPos1().getX(), cuboidSelection.getPos1().getY(), cuboidSelection.getPos1().getZ())
                    && claim.contains(cuboidSelection.getPos2().getX(), cuboidSelection.getPos2().getY(), cuboidSelection.getPos2().getZ())){
                //If in the entire selection is inside only one claim, we already know he can break there!
                return true;
            }

            if (claim.getParent() != null) {
                Claim parentClaim = claim.getParent();
                // you're on a subdivision
                if (!parentClaim.canBreak(player, firstCorner, null)) {
                    // you have no break permission on the top claim... disallow.
                    return false;
                }

                if (parentClaim.contains(cuboidSelection.getPos1().getX(), cuboidSelection.getPos1().getY(), cuboidSelection.getPos1().getZ())
                        && parentClaim.contains(cuboidSelection.getPos2().getX(), cuboidSelection.getPos2().getY(), cuboidSelection.getPos2().getZ())) {
                    //If in the entire selection is inside only one claim, we already know he can break there!
                    return true;
                }
            }
        }

        return true;
    }

}
