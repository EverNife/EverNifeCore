package br.com.finalcraft.evernifecore.math.vector.base;

public interface IVec2i<VEC extends IVec2i<VEC>> extends Comparable<VEC> {

    VEC at(int x, int z);

    int getX();

    int getZ();

    // -- Set ------------------------------------------------------------------

    default VEC setX(int x) {
        return at(x, getZ());
    }

    default VEC setZ(int y) {
        return at(getX(), y);
    }

    // -- Add ------------------------------------------------------------------

    default VEC add(int x, int z) {
        return at(getX() + x, getZ() + z);
    }

    default VEC add(IVec2i<?> other) {
        return add(other.getX(), other.getZ());
    }

    default VEC add(int n) {
        return add(n, n);
    }

    default VEC addX(int x) {
        return at(getX() + x, getZ());
    }

    default VEC addZ(int z) {
        return at(getX(), getZ() + z);
    }

    // -- Subtract -------------------------------------------------------------

    default VEC subtract(int x, int z) {
        return at(getX() - x, getZ() - z);
    }

    default VEC subtract(IVec2i<?> other) {
        return subtract(other.getX(), other.getZ());
    }

    default VEC subtract(int n) {
        return subtract(n, n);
    }

    default VEC subtractX(int x) {
        return at(getX() - x, getZ());
    }

    default VEC subtractZ(int z) {
        return at(getX(), getZ() - z);
    }

    // -- Multiply -------------------------------------------------------------

    default VEC multiply(int x, int z) {
        return at(getX() * x, getZ() * z);
    }

    default VEC multiply(IVec2i<?> other) {
        return multiply(other.getX(), other.getZ());
    }

    default VEC multiply(int n) {
        return multiply(n, n);
    }

    default VEC multiplyX(int x) {
        return at(getX() * x, getZ());
    }

    default VEC multiplyZ(int z) {
        return at(getX(), getZ() * z);
    }

    // -- Divide ---------------------------------------------------------------

    default VEC divide(int x, int z) {
        return at(getX() / x, getZ() / z);
    }

    default VEC divide(IVec2i<?> other) {
        return divide(other.getX(), other.getZ());
    }

    default VEC divide(int n) {
        return divide(n, n);
    }

    default VEC divideX(int x) {
        return at(getX() / x, getZ());
    }

    default VEC divideZ(int z) {
        return at(getX(), getZ() / z);
    }

    // -- Bound ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    default VEC boundX(int min, int max) {
        if (getX() < min) return setX(min);
        if (getX() > max) return setX(max);
        return (VEC) this;
    }

    @SuppressWarnings("unchecked")
    default VEC boundZ(int min, int max) {
        if (getZ() < min) return setZ(min);
        if (getZ() > max) return setZ(max);
        return (VEC) this;
    }

    // -- Distance -------------------------------------------------------------

    default int distanceSq(IVec2i<?> other) {
        int dx = other.getX() - getX();
        int dy = other.getZ() - getZ();
        return dx * dx + dy * dy;
    }

    default double distance(IVec2i<?> other) {
        return Math.sqrt(distanceSq(other));
    }

    // -- Containment ----------------------------------------------------------

    default boolean containedWithin(IVec2i<?> min, IVec2i<?> max) {
        return getX() >= min.getX() && getX() <= max.getX()
            && getZ() >= min.getZ() && getZ() <= max.getZ();
    }

    // -- Comparable -----------------------------------------------------------

    @Override
    default int compareTo(VEC o) {
        if (getZ() != o.getZ()) return getZ() - o.getZ();
        return getX() - o.getX();
    }

}
