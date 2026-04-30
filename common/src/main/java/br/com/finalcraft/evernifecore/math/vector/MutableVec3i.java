package br.com.finalcraft.evernifecore.math.vector;

import br.com.finalcraft.evernifecore.math.vector.base.IVec3i;
import br.com.finalcraft.evernifecore.util.FCHashUtil;
import lombok.Data;

@Data
public class MutableVec3i<VEC extends IVec3i<VEC>> implements IVec3i<VEC> {

    protected int x;
    protected int y;
    protected int z;
    protected transient int hash;

    public MutableVec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public VEC at(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.hash = 0;
        return (VEC) this;
    }

    @Override
    public VEC setX(int x) {
        this.x = x;
        this.hash = 0;
        return (VEC) this;
    }

    @Override
    public VEC setY(int y) {
        this.y = y;
        this.hash = 0;
        return (VEC) this;
    }

    @Override
    public VEC setZ(int z) {
        this.z = z;
        this.hash = 0;
        return (VEC) this;
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
            hash = (int) FCHashUtil.hash(x, y, z);
            if (hash == 0){
                hash = 1; // avoid sentinel
            }
        }
        return hash;
    }

}
