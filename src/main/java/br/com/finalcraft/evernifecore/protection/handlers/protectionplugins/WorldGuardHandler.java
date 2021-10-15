package br.com.finalcraft.evernifecore.protection.handlers.protectionplugins;

import br.com.finalcraft.evernifecore.protection.handlers.ProtectionHandler;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class WorldGuardHandler implements ProtectionHandler {
	WorldGuardPlugin worldGuard;
	
	public WorldGuardHandler() {
		this.worldGuard=WorldGuardPlugin.inst();
	}
	
	@Override
	public boolean canBuild(Player player, Location location) {
		return this.check(player, location);
	}

	@Override
	public boolean canAccess(Player player, Location location) {
		return this.check(player, location);
	}

	@Override
	public boolean canUse(Player player, Location location) {
		return this.check(player, location);
	}

	@Override
	public boolean canOpenContainer(Player player, Block block) {
		return this.check(player, block.getLocation());
	}

	@Override
	public boolean canInteract(Player player, Location location) {
		return this.check(player, location);
	}

	@Override
	public boolean canAttack(Player player, Entity entity) {
		return this.check(player, entity.getLocation());
	}

	@Override
	public boolean canProjectileHit(Player player, Location location) {
		return this.check(player, location);
	}
	
	@Override
	public boolean canUseAoE(Player player, Location location, int range) {
		ProtectedCuboidRegion pcr = new ProtectedCuboidRegion("ForgeRestrictorWGAoETest", new BlockVector(location.getBlockX()-range, 0, location.getBlockZ()-range), new BlockVector(location.getBlockX()+range, 255, location.getBlockZ()+range));
		ApplicableRegionSet ars = this.worldGuard.getRegionManager(location.getWorld()).getApplicableRegions(pcr);
		for (ProtectedRegion pr : ars.getRegions()) {
			if (!this.worldGuard.canBuild(player, new Location(location.getWorld(), pr.getMaximumPoint().getBlockX(), pr.getMaximumPoint().getBlockY(), pr.getMaximumPoint().getBlockZ()))) {
				player.sendMessage("You don't have permission in this area.");
				return false;
			}
		}
		
		return true;
	}
	
	boolean check(Player player, Location location) {
		boolean perm=this.worldGuard.canBuild(player, location);
		if (!perm) {
			player.sendMessage("You don't have permission in this area.");
		}
		return perm;
	}

	@Override
	public String getName() {
		return "WorldGuard";
	}
}
