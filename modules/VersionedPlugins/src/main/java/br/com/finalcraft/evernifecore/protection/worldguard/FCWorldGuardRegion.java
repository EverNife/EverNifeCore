package br.com.finalcraft.evernifecore.protection.worldguard;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.vectors.CuboidSelection;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.apache.commons.lang3.Validate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface FCWorldGuardRegion {

    public static FCWorldGuardRegion of(String id, CuboidSelection selection){
        return WGPlatform.getInstance().createFCWorldGuardRegion(id, selection.getPos1(), selection.getPos2());
    }

    public static FCWorldGuardRegion of(String id, BlockPos pt1, BlockPos pt2){
        return WGPlatform.getInstance().createFCWorldGuardRegion(id, pt1, pt2);
    }

    public static FCWorldGuardRegion of(String id, boolean isTransient, BlockPos pt1, BlockPos pt2){
        return WGPlatform.getInstance().createFCWorldGuardRegion(id, isTransient, pt1, pt2);
    }

    public ProtectedRegion getProtectedRegion();

    public boolean contains(Location location);

    public World getWorld();

    public Location getMaximumPoint();

    public Location getMinimumPoint();

    //------------------------------------------------------------------------------------------------------------------
    // Defaults
    //------------------------------------------------------------------------------------------------------------------

    public default List<Player> getAllPlayersInside(){
        List<Player> list = new ArrayList<>();
        for (Player player : getWorld().getPlayers()) {
            if (this.contains(player)){
                list.add(player);
            }
        }
        return list;
    }

    public default String getId(){
        return this.getProtectedRegion().getId();
    }

    public default DefaultDomain getOwners() {
        return this.getProtectedRegion().getOwners();
    }

    public default DefaultDomain getMembers() {
        return this.getProtectedRegion().getMembers();
    }

    public default Map<Flag<?>, Object> getFlags(){
        return this.getProtectedRegion().getFlags();
    }

    public default boolean isDirty(){
        return this.getProtectedRegion().isDirty();
    }

    public default void setDirty(boolean dirty){
        this.getProtectedRegion().setDirty(dirty);
    }

    public default boolean contains(Player player){
        return contains(player.getLocation());
    }

    public default boolean contains(BlockPos blockPos){
        return contains(blockPos.getLocation(null));
    }

    public default <T extends Flag<V>, V> void setFlag(@Nonnull T flag, @Nullable V val) {
        Validate.notNull(flag);
        this.setDirty(true);
        if (val == null) {
            this.getFlags().remove(flag);
        } else {
            this.getFlags().put(flag, val);
        }
    }


}
