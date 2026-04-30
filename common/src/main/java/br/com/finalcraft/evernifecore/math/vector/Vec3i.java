package br.com.finalcraft.evernifecore.math.vector;

import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.util.FCHashUtil;
import lombok.Data;

@Data
public class Vec3i<VEC extends IVec3i<VEC>> implements IVec3i<VEC> {

    protected final int x;
    protected final int y;
    protected final int z;
    protected transient int hash;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public VEC at(int x, int y, int z) {
        return (VEC) new Vec3i(x, y, z);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof IVec3i<?> vec3i)){
            return false;
        }
        if (this == object){
            return true;
        }
        return x == vec3i.getX() && y == vec3i.getY() && z == vec3i.getZ();
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = this.calculateHashCode();
            if (hash == 0){
                hash = 1; // avoid sentinel
            }
        }
        return hash;
    }

    protected int calculateHashCode() {
        return (int) FCHashUtil.hash(x, y, z);
    }
}
