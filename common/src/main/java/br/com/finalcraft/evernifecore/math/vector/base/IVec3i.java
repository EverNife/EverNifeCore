package br.com.finalcraft.evernifecore.math.vector.base;

import br.com.finalcraft.evernifecore.math.vecmath.VecMath;

public interface IVec3i<VEC extends IVec3i<VEC>> extends Comparable<VEC> {

    VEC at(int x, int y, int z);

    default VEC at(double x, double y, double z) {
        return at(VecMath.floor_double(x), VecMath.floor_double(y), VecMath.floor_double(z));
    }

    int getX();

    int getY();

    int getZ();

    // -- Set ------------------------------------------------------------------

    default VEC setX(int x) {
        return at(x, getY(), getZ());
    }

    default VEC setY(int y) {
        return at(getX(), y, getZ());
    }

    default VEC setZ(int z) {
        return at(getX(), getY(), z);
    }

    // -- Add ------------------------------------------------------------------

    default VEC add(int x, int y, int z) {
        return at(getX() + x, getY() + y, getZ() + z);
    }

    default VEC add(IVec3i<?> other) {
        return add(other.getX(), other.getY(), other.getZ());
    }

    default VEC add(int n) {
        return add(n, n, n);
    }

    default VEC addX(int x) {
        return at(getX() + x, getY(), getZ());
    }

    default VEC addY(int y) {
        return at(getX(), getY() + y, getZ());
    }

    default VEC addZ(int z) {
        return at(getX(), getY(), getZ() + z);
    }

    // -- Subtract -------------------------------------------------------------

    default VEC subtract(int x, int y, int z) {
        return at(getX() - x, getY() - y, getZ() - z);
    }

    default VEC subtract(IVec3i<?> other) {
        return subtract(other.getX(), other.getY(), other.getZ());
    }

    default VEC subtract(int n) {
        return subtract(n, n, n);
    }

    default VEC subtractX(int x) {
        return at(getX() - x, getY(), getZ());
    }

    default VEC subtractY(int y) {
        return at(getX(), getY() - y, getZ());
    }

    default VEC subtractZ(int z) {
        return at(getX(), getY(), getZ() - z);
    }

    // -- Multiply -------------------------------------------------------------

    default VEC multiply(int x, int y, int z) {
        return at(getX() * x, getY() * y, getZ() * z);
    }

    default VEC multiply(IVec3i<?> other) {
        return multiply(other.getX(), other.getY(), other.getZ());
    }

    default VEC multiply(int n) {
        return multiply(n, n, n);
    }

    default VEC multiplyX(int x) {
        return at(getX() * x, getY(), getZ());
    }

    default VEC multiplyY(int y) {
        return at(getX(), getY() * y, getZ());
    }

    default VEC multiplyZ(int z) {
        return at(getX(), getY(), getZ() * z);
    }

    // -- Divide ---------------------------------------------------------------

    default VEC divide(int x, int y, int z) {
        return at(getX() / x, getY() / y, getZ() / z);
    }

    default VEC divide(IVec3i<?> other) {
        return divide(other.getX(), other.getY(), other.getZ());
    }

    default VEC divide(int n) {
        return divide(n, n, n);
    }

    default VEC divideX(int x) {
        return at(getX() / x, getY(), getZ());
    }

    default VEC divideY(int y) {
        return at(getX(), getY() / y, getZ());
    }

    default VEC divideZ(int z) {
        return at(getX(), getY(), getZ() / z);
    }

    // -- Bound ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    default VEC boundX(int min, int max) {
        if (getX() < min) return setX(min);
        if (getX() > max) return setX(max);
        return (VEC) this;
    }

    @SuppressWarnings("unchecked")
    default VEC boundY(int min, int max) {
        if (getY() < min) return setY(min);
        if (getY() > max) return setY(max);
        return (VEC) this;
    }

    @SuppressWarnings("unchecked")
    default VEC boundZ(int min, int max) {
        if (getZ() < min) return setZ(min);
        if (getZ() > max) return setZ(max);
        return (VEC) this;
    }

    // -- Distance -------------------------------------------------------------

    default int distanceSq(IVec3i<?> other) {
        int dx = other.getX() - getX();
        int dy = other.getY() - getY();
        int dz = other.getZ() - getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    default double distance(IVec3i<?> other) {
        return Math.sqrt(distanceSq(other));
    }

    // -- Containment ----------------------------------------------------------

    default boolean containedWithin(IVec3i<?> min, IVec3i<?> max) {
        return getX() >= min.getX() && getX() <= max.getX()
                && getY() >= min.getY() && getY() <= max.getY()
                && getZ() >= min.getZ() && getZ() <= max.getZ();
    }

    // -- Comparable -----------------------------------------------------------

    @Override
    default int compareTo(VEC o) {
        if (getY() != o.getY()) return getY() - o.getY();
        if (getZ() != o.getZ()) return getZ() - o.getZ();
        return getX() - o.getX();
    }

}
