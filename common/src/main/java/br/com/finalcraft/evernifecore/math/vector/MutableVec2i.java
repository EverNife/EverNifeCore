package br.com.finalcraft.evernifecore.math.vector;

import br.com.finalcraft.evernifecore.math.vector.base.IVec2i;
import br.com.finalcraft.evernifecore.util.FCHashUtil;
import lombok.Data;

@Data
public class MutableVec2i<VEC extends IVec2i<VEC>> implements IVec2i<VEC> {

    protected int x;
    protected int z;
    protected transient int hash;

    public MutableVec2i(int x, int z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public VEC at(int x, int z) {
        this.x = x;
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
    public VEC setZ(int y) {
        this.z = y;
        this.hash = 0;
        return (VEC) this;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof IVec2i<?> vec2i)){
            return false;
        }
        if (this == object){
            return true;
        }
        return x == vec2i.getX() && z == vec2i.getZ();
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
        return (int) FCHashUtil.hash(x, z);
    }

}
