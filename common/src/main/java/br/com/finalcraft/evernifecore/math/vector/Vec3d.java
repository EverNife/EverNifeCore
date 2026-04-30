package br.com.finalcraft.evernifecore.math.vector;

import br.com.finalcraft.evernifecore.math.vector.base.IVec3d;
import br.com.finalcraft.evernifecore.util.FCHashUtil;
import lombok.Data;

@Data
public class Vec3d<VEC extends IVec3d<VEC>> implements IVec3d<VEC> {

    protected final double x;
    protected final double y;
    protected final double z;
    protected transient int hash;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public VEC at(double x, double y, double z) {
        return (VEC) new Vec3d(x, y, z);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof IVec3d<?> vec3d)){
            return false;
        }
        if (this == object){
            return true;
        }
        return x == vec3d.getX() && y == vec3d.getY() && z == vec3d.getZ();
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            this.hash = (int) FCHashUtil.hash(
                    Double.doubleToLongBits(this.x),
                    Double.doubleToLongBits(this.y),
                    Double.doubleToLongBits(this.z)
            );
            if (hash == 0){
                hash = 1; // avoid sentinel
            }
        }
        return hash;
    }

}
