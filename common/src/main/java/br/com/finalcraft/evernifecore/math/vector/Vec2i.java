package br.com.finalcraft.evernifecore.math.vector;

import br.com.finalcraft.evernifecore.math.vector.base.IVec2i;
import br.com.finalcraft.evernifecore.util.FCHashUtil;
import lombok.Data;

@Data
public class Vec2i<VEC extends IVec2i<VEC>> implements IVec2i<VEC> {

    protected final int x;
    protected final int z;
    protected transient int hash;

    public Vec2i(int x, int z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public VEC at(int x, int z) {
        return (VEC) new Vec2i<>(x, z);
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
