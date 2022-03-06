package br.com.finalcraft.evernifecore.protection.handlers.protectionplugins;

import br.com.finalcraft.evernifecore.protection.handlers.ProtectionHandler;
import net.kaikk.mc.gpp.Claim;
import net.kaikk.mc.gpp.DataStore;
import net.kaikk.mc.gpp.GriefPreventionPlus;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;


public class GriefPreventionPlusHandler implements ProtectionHandler {

	public GriefPreventionPlusHandler() {
	}

	public DataStore getDataStore(){
		return GriefPreventionPlus.getInstance().getDataStore();
	}

	@Override
	public boolean canBuild(Player player, Location location) {
		Claim claim = this.getDataStore().getClaimAt(location, false);
		if (claim==null) {
			return true;
		}
		
		String reason=claim.canBuild(player);
		
		if (reason==null) {
			return true;
		}
		
		player.sendMessage(reason);
		
		return false;
	}

	@Override
	public boolean canAccess(Player player, Location location) {
		Claim claim = this.getDataStore().getClaimAt(location, false);
		if (claim==null) {
			return true;
		}
		
		String reason=claim.canAccess(player);
		
		if (reason==null) {
			return true;
		}
		
		player.sendMessage(reason);
		
		return false;
	}

	@Override
	public boolean canUse(Player player, Location location) {
		Claim claim = this.getDataStore().getClaimAt(location, false);
		if (claim==null) {
			return true;
		}
		
		String reason=claim.canBuild(player);
		
		if (reason==null) {
			return true;
		}
		
		player.sendMessage(reason);
		
		return false;
	}
	
	@Override
	public boolean canOpenContainer(Player player, Block block) {
		Claim claim = this.getDataStore().getClaimAt(block.getLocation(), false);
		if (claim==null) {
			return true;
		}
		
		String reason=claim.canOpenContainers(player);
		
		if (reason==null) {
			return true;
		}
		
		player.sendMessage(reason);
		
		return false;
	}

	@Override
	public boolean canInteract(Player player, Location location) {
		Claim claim = this.getDataStore().getClaimAt(location, false);
		if (claim==null) {
			return true;
		}
		
		String reason=claim.canBuild(player);
		
		if (reason==null) {
			return true;
		}
		
		player.sendMessage(reason);
		
		return false;
	}


	@Override
	public boolean canAttack(Player damager, Entity damaged) {
		Claim claim = this.getDataStore().getClaimAt(damaged.getLocation(), false);
		if (claim==null) {
			return true;
		}
		
		String reason=claim.canBuild(damager);
		
		if (reason==null) {
			return true;
		}
		
		damager.sendMessage(reason);
		
		return false;
	}

	@Override
	public boolean canProjectileHit(Player player, Location location) {
		Claim claim = this.getDataStore().getClaimAt(location, false);
		if (claim==null) {
			return true;
		}
		
		String reason=claim.canBuild(player);
		
		if (reason==null) {
			return true;
		}
		
		player.sendMessage(reason);
		
		return false;
	}
	
	@Override
	public boolean canUseAoE(Player player, Location location, int range) {
		Claim claim = this.getDataStore().getClaimAt(location, false);
		if (claim!=null) {
			if (claim.canBuild(player)!=null) {
				// you have no perms on this claim, disallow.
				return false;
			}
			
			if (claimContains(claim, location, range)) {
				// the item's range is in this claim's boundaries. You're allowed to use this item.
				return true;
			}
			
			if (claim.getParent()!=null) {
				// you're on a subdivision
				if (claim.getParent().canBuild(player)!=null) {
					// you have no build permission on the top claim... disallow.
					return false;
				}
			
				if (claimContains(claim, location, range)) {
				    // the restricted item's range is in the top claim's boundaries. you're allowed to use this item.
					return true;
				}
			}
		}
		
		// the range is not entirely on a claim you're trusted in... we need to search for nearby claims too.
		for (Claim nClaim : this.getDataStore().posClaimsGet(location, range).values()) {
			if (nClaim.canBuild(player)!=null) {
				// if not allowed on claims in range, disallow.
				return false;
			}
		}
		return true;
	}
	
	static boolean claimContains(Claim claim, Location location, int range) {
		return (claim.contains(new Location(location.getWorld(), location.getBlockX()+range, 0, location.getBlockZ()+range), true, false) &&
				claim.contains(new Location(location.getWorld(), location.getBlockX()-range, 0, location.getBlockZ()-range), true, false));
	}

	@Override
	public String getName() {
		return "GriefPreventionPlus";
	}
}
