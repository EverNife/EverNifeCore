package br.com.finalcraft.evernifecore.protection.integration.imp;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.protection.integration.ProtectionHandler;
import br.com.finalcraft.evernifecore.vectors.CuboidSelection;
import net.kaikk.mc.gpp.Claim;
import net.kaikk.mc.gpp.GriefPreventionPlus;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

public class GriefPreventionPlusHandler implements ProtectionHandler {

	@Override
	public String getName() {
		return "GriefPreventionPlus";
	}

	@Override
	public boolean canBuild(Player player, Location location) {
		Claim claim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(location, false);
		if (claim == null) {
			return true;
		}

		return claim.canBuild(player) == null;
	}

	@Override
	public boolean canBreak(Player player, Location location) {
		return canBuild(player, location);
	}

	@Override
	public boolean canInteract(Player player, Location location) {
		Claim claim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(location, false);
		if (claim == null) {
			return true;
		}

		return claim.canOpenContainers(player) == null;
	}

	@Override
	public boolean canAttack(Player damager, Entity damaged) {
		if (damaged instanceof Player) {
			if (!GriefPreventionPlus.getInstance().config.pvp_enabledWorlds.contains(damaged.getWorld().getUID())) {
				return false;
			}
			
			Claim claim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(damaged.getLocation(), false);
			if (claim == null) {
				return true;
			}
			
			if (claim.isAdminClaim()) {
				if (claim.getParent() == null) {
					if (GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminLandClaims) {
						return false;
					}
				} else {
					if (GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminSubdivisions) {
						return false;
					}
				}
			} else {
				if (GriefPreventionPlus.getInstance().config.pvp_noCombatInPlayerLandClaims) {
					return false;
				}
			}
			
			String reason = claim.canBuild(damager);
			if (reason == null) {
				return true;
			}
			
			damager.sendMessage(reason);
		} else if (damaged instanceof Animals || damaged instanceof Villager) {
			Claim claim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(damaged.getLocation(), false);
			if (claim == null) {
				return true;
			}

			String reason = claim.canOpenContainers(damager); // allow farming with /containertrust

			if (reason == null) {
				return true;
			}

			damager.sendMessage(reason);
		} else {
			return true;
		}
		
		return false;
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

		Claim claim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(firstCorner, false);
		if (claim != null) {
			if (claim.canBuild(player) != null) {
				// you have no perms on this claim, disallow.
				return false;
			}
			
			if (claimContains(claim, cuboidSelection)) {
				// the item's range is in this claim's boundaries. You're allowed to use this item.
				return true;
			}
			
			if (claim.getParent() != null) {
				// you're on a subdivision
				if (claim.getParent().canBuild(player) != null) {
					// you have no build permission on the top claim... disallow.
					return false;
				}
			
				if (claimContains(claim, cuboidSelection)) {
				    // the restricted item's range is in the top claim's boundaries. you're allowed to use this item.
					return true;
				}
			}
		}
		
		// the range is not entirely on a claim you're trusted in... we need to search for nearby claims too.
		Location center = cuboidSelection.getCenter().getLocation(world);
		int radiusXZ = (int) (Math.max(cuboidSelection.getMaximum().getX() - cuboidSelection.getMinium().getX(), cuboidSelection.getMaximum().getZ() - cuboidSelection.getMinium().getZ()) / 2D);
		for (Claim nClaim : GriefPreventionPlus.getInstance().getDataStore().posClaimsGet(center, radiusXZ).values()) {
			if (nClaim.canBuild(player) != null) {
				// if not allowed on claims in range, disallow.
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean canBreakOnRegion(Player player, World world, CuboidSelection cuboidSelection) {
		return canBuildOnRegion(player, world, cuboidSelection);
	}

	private boolean claimContains(Claim claim, CuboidSelection selection) {
		return (claim.contains(new Location(claim.getWorld(), selection.getMinium().getX(), 0, selection.getMinium().getZ()), true, false) &&
				claim.contains(new Location(claim.getWorld(), selection.getMaximum().getX(), 0, selection.getMaximum().getZ()), true, false));
	}

}
