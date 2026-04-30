package br.com.finalcraft.evernifecore.math.vector.base;

public interface IVec3d<VEC extends IVec3d<VEC>> extends Comparable<VEC> {

    VEC at(double x, double y, double z);

    double getX();

    double getY();

    double getZ();

    // -- Set ------------------------------------------------------------------

    default VEC setX(double x) {
        return at(x, getY(), getZ());
    }

    default VEC setY(double y) {
        return at(getX(), y, getZ());
    }

    default VEC setZ(double z) {
        return at(getX(), getY(), z);
    }

    // -- Add ------------------------------------------------------------------

    default VEC add(double x, double y, double z) {
        return at(getX() + x, getY() + y, getZ() + z);
    }

    default VEC add(IVec3d<?> other) {
        return add(other.getX(), other.getY(), other.getZ());
    }

    default VEC add(double n) {
        return add(n, n, n);
    }

    default VEC addX(double x) {
        return at(getX() + x, getY(), getZ());
    }

    default VEC addY(double y) {
        return at(getX(), getY() + y, getZ());
    }

    default VEC addZ(double z) {
        return at(getX(), getY(), getZ() + z);
    }

    // -- Subtract -------------------------------------------------------------

    default VEC subtract(double x, double y, double z) {
        return at(getX() - x, getY() - y, getZ() - z);
    }

    default VEC subtract(IVec3d<?> other) {
        return subtract(other.getX(), other.getY(), other.getZ());
    }

    default VEC subtract(double n) {
        return subtract(n, n, n);
    }

    default VEC subtractX(double x) {
        return at(getX() - x, getY(), getZ());
    }

    default VEC subtractY(double y) {
        return at(getX(), getY() - y, getZ());
    }

    default VEC subtractZ(double z) {
        return at(getX(), getY(), getZ() - z);
    }

    // -- Multiply -------------------------------------------------------------

    default VEC multiply(double x, double y, double z) {
        return at(getX() * x, getY() * y, getZ() * z);
    }

    default VEC multiply(IVec3d<?> other) {
        return multiply(other.getX(), other.getY(), other.getZ());
    }

    default VEC multiply(double n) {
        return multiply(n, n, n);
    }

    default VEC multiplyX(double x) {
        return at(getX() * x, getY(), getZ());
    }

    default VEC multiplyY(double y) {
        return at(getX(), getY() * y, getZ());
    }

    default VEC multiplyZ(double z) {
        return at(getX(), getY(), getZ() * z);
    }

    // -- Divide ---------------------------------------------------------------

    default VEC divide(double x, double y, double z) {
        return at(getX() / x, getY() / y, getZ() / z);
    }

    default VEC divide(IVec3d<?> other) {
        return divide(other.getX(), other.getY(), other.getZ());
    }

    default VEC divide(double n) {
        return divide(n, n, n);
    }

    default VEC divideX(double x) {
        return at(getX() / x, getY(), getZ());
    }

    default VEC divideY(double y) {
        return at(getX(), getY() / y, getZ());
    }

    default VEC divideZ(double z) {
        return at(getX(), getY(), getZ() / z);
    }

    // -- Bound ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    default VEC boundX(double min, double max) {
        if (getX() < min) return setX(min);
        if (getX() > max) return setX(max);
        return (VEC) this;
    }

    @SuppressWarnings("unchecked")
    default VEC boundY(double min, double max) {
        if (getY() < min) return setY(min);
        if (getY() > max) return setY(max);
        return (VEC) this;
    }

    @SuppressWarnings("unchecked")
    default VEC boundZ(double min, double max) {
        if (getZ() < min) return setZ(min);
        if (getZ() > max) return setZ(max);
        return (VEC) this;
    }

    // -- Distance -------------------------------------------------------------

    default double distanceSq(IVec3d<?> other) {
        double dx = other.getX() - getX();
        double dy = other.getY() - getY();
        double dz = other.getZ() - getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    default double distance(IVec3d<?> other) {
        return Math.sqrt(distanceSq(other));
    }

    // -- Length ----------------------------------------------------------------

    default double lengthSq() {
        return getX() * getX() + getY() * getY() + getZ() * getZ();
    }

    default double length() {
        return Math.sqrt(lengthSq());
    }

    // -- Normalize ------------------------------------------------------------

    default VEC normalize() {
        double len = length();
        return at(getX() / len, getY() / len, getZ() / len);
    }

    // -- Containment ----------------------------------------------------------

    default boolean containedWithin(IVec3d<?> min, IVec3d<?> max) {
        return getX() >= min.getX() && getX() <= max.getX()
            && getY() >= min.getY() && getY() <= max.getY()
            && getZ() >= min.getZ() && getZ() <= max.getZ();
    }

    // -- Comparable -----------------------------------------------------------

    @Override
    default int compareTo(VEC o) {
        int cmp = Double.compare(getY(), o.getY());
        if (cmp != 0) return cmp;
        cmp = Double.compare(getZ(), o.getZ());
        if (cmp != 0) return cmp;
        return Double.compare(getX(), o.getX());
    }

}
