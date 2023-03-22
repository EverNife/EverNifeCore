package br.com.finalcraft.evernifecore.vectors;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;

import java.util.Objects;

public class CuboidSelection {

    private BlockPos pos1;
    private BlockPos pos2;

    private transient BlockPos minium;
    private transient BlockPos maximum;

    public CuboidSelection(BlockPos pos1, BlockPos pos2) {
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.recalculate();
    }

    public static CuboidSelection of(BlockPos pos1, BlockPos pos2){
        return new CuboidSelection(pos1, pos2);
    }

    private void recalculate(){
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        this.minium = new BlockPos(minX,minY,minZ);
        this.maximum = new BlockPos(maxX,maxY,maxZ);
    }

    public BlockPos getPos1() {
        return pos1;
    }

    public BlockPos getPos2() {
        return pos2;
    }

    public CuboidSelection setPos1(BlockPos pos1) {
        this.pos1 = pos1;
        this.recalculate();
        return this;
    }

    public CuboidSelection setPos2(BlockPos pos2) {
        this.pos2 = pos2;
        this.recalculate();
        return this;
    }

    public BlockPos getMinium() {
        return minium;
    }

    public BlockPos getMaximum() {
        return maximum;
    }

    public boolean contains(BlockPos other){
        return other.containedWithin(this.getMinium(), this.getMaximum());
    }

    public CuboidSelection shift(BlockPos change) {
        this.pos1 = this.pos1.add(change);
        this.pos2 = this.pos2.add(change);
        this.recalculate();
        return this;
    }

    public CuboidSelection contract(BlockPos... changes) {

        for (BlockPos change : changes) {
            if (change.getX() < 0) {
                if (Math.max(pos1.getX(), pos2.getX()) == pos1.getX()) {
                    pos1 = pos1.add(change.getX(), 0, 0);
                } else {
                    pos2 = pos2.add(change.getX(), 0, 0);
                }
            } else {
                if (Math.min(pos1.getX(), pos2.getX()) == pos1.getX()) {
                    pos1 = pos1.add(change.getX(), 0, 0);
                } else {
                    pos2 = pos2.add(change.getX(), 0, 0);
                }
            }

            if (change.getY() < 0) {
                if (Math.max(pos1.getY(), pos2.getY()) == pos1.getY()) {
                    pos1 = pos1.add(0, change.getY(), 0);
                } else {
                    pos2 = pos2.add(0, change.getY(), 0);
                }
            } else {
                if (Math.min(pos1.getY(), pos2.getY()) == pos1.getY()) {
                    pos1 = pos1.add(0, change.getY(), 0);
                } else {
                    pos2 = pos2.add(0, change.getY(), 0);
                }
            }

            if (change.getZ() < 0) {
                if (Math.max(pos1.getZ(), pos2.getZ()) == pos1.getZ()) {
                    pos1 = pos1.add(0, 0, change.getZ());
                } else {
                    pos2 = pos2.add(0, 0, change.getZ());
                }
            } else {
                if (Math.min(pos1.getZ(), pos2.getZ()) == pos1.getZ()) {
                    pos1 = pos1.add(0, 0, change.getZ());
                } else {
                    pos2 = pos2.add(0, 0, change.getZ());
                }
            }
        }

        this.recalculate();
        return this;
    }

    public CuboidSelection expand(BlockPos... changes) {
        for (BlockPos change : changes) {
            if (change.getX() > 0) {
                if (Math.max(pos1.getX(), pos2.getX()) == pos1.getX()) {
                    pos1 = pos1.add(change.getX(), 0, 0);
                } else {
                    pos2 = pos2.add(change.getX(), 0, 0);
                }
            } else {
                if (Math.min(pos1.getX(), pos2.getX()) == pos1.getX()) {
                    pos1 = pos1.add(change.getX(), 0, 0);
                } else {
                    pos2 = pos2.add(change.getX(), 0, 0);
                }
            }

            if (change.getY() > 0) {
                if (Math.max(pos1.getY(), pos2.getY()) == pos1.getY()) {
                    pos1 = pos1.add(0, change.getY(), 0);
                } else {
                    pos2 = pos2.add(0, change.getY(), 0);
                }
            } else {
                if (Math.min(pos1.getY(), pos2.getY()) == pos1.getY()) {
                    pos1 = pos1.add(0, change.getY(), 0);
                } else {
                    pos2 = pos2.add(0, change.getY(), 0);
                }
            }

            if (change.getZ() > 0) {
                if (Math.max(pos1.getZ(), pos2.getZ()) == pos1.getZ()) {
                    pos1 = pos1.add(0, 0, change.getZ());
                } else {
                    pos2 = pos2.add(0, 0, change.getZ());
                }
            } else {
                if (Math.min(pos1.getZ(), pos2.getZ()) == pos1.getZ()) {
                    pos1 = pos1.add(0, 0, change.getZ());
                } else {
                    pos2 = pos2.add(0, 0, change.getZ());
                }
            }
        }

        this.recalculate();
        return this;
    }

    public CuboidSelection clone(){
        return new CuboidSelection(this.pos1, this.pos2);
    }

    public CuboidSelection expandVert(){
        this.pos1 = this.pos1.setY(0);
        this.pos2 = this.pos2.setY(255);
        this.recalculate();
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CuboidSelection)) return false;
        CuboidSelection that = (CuboidSelection) o;
        return getMinium().equals(that.getMinium()) && getMaximum().equals(that.getMaximum());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMinium(), getMaximum());
    }

    @Override
    public String toString() {
        return minium + " # " + maximum;
    }
}
