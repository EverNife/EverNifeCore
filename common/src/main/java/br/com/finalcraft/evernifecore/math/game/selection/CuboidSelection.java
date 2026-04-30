package br.com.finalcraft.evernifecore.math.game.selection;

import br.com.finalcraft.evernifecore.math.game.options.RegionGridOptions;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.chunkpos.ChunkPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.LocPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

    protected CuboidSelection(BlockPos pos1, BlockPos pos2, BlockPos minium, BlockPos maximum) {
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.minium = minium;
        this.maximum = maximum;
    }

    public static CuboidSelection of(BlockPos center){
        return new CuboidSelection(center, center);
    }

    public static CuboidSelection of(BlockPos pos1, BlockPos pos2){
        return new CuboidSelection(pos1, pos2);
    }

    protected void recalculate(){
        int minHeight = RegionGridOptions.getCurrent().getMinHeight();
        int maxHeight = RegionGridOptions.getCurrent().getMaxHeight();

        pos1 = pos1.boundY(minHeight, maxHeight);
        pos2 = pos2.boundY(minHeight, maxHeight);
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

    public LocPos getCenter(){
        return new LocPos(
                (minium.getX() + maximum.getX()) / 2D,
                (minium.getY() + maximum.getY()) / 2D,
                (minium.getZ() + maximum.getZ()) / 2D
        );
    }

    public int getVolume(){
        return (maximum.getX() - minium.getX() + 1) * (maximum.getY() - minium.getY() + 1) * (maximum.getZ() - minium.getZ() + 1);
    }

    public int getArea(){
        return (maximum.getX() - minium.getX() + 1) * (maximum.getZ() - minium.getZ() + 1);
    }

    public Iterable<BlockPos> getBlocksIterator() {
        BlockPos min = getMinium();
        BlockPos max = getMaximum();

        final int lowerX = min.getX();
        final int lowerY = min.getY();
        final int lowerZ = min.getZ();

        final int upperX = max.getX();
        final int upperY = max.getY();
        final int upperZ = max.getZ();

        return () -> new Iterator<>() {
            int x = lowerX;
            int y = lowerY;
            int z = lowerZ;

            @Override
            public boolean hasNext() {
                return x <= upperX;
            }

            @Override
            public BlockPos next() {
                BlockPos pos = new BlockPos(x, y, z);

                // advance (z → y → x)
                z++;
                if (z > upperZ) {
                    z = lowerZ;
                    y++;
                    if (y > upperY) {
                        y = lowerY;
                        x++;
                    }
                }

                return pos;
            }
        };
    }

    public List<BlockPos> getBlocks() {
        BlockPos min = getMinium();
        BlockPos max = getMaximum();

        final int lowerX = min.getX();
        final int lowerY = min.getY();
        final int lowerZ = min.getZ();

        final int upperX = max.getX();
        final int upperY = max.getY();
        final int upperZ = max.getZ();

        // total size = (dx * dy * dz)
        final int sizeX = upperX - lowerX + 1;
        final int sizeY = upperY - lowerY + 1;
        final int sizeZ = upperZ - lowerZ + 1;

        final int total = sizeX * sizeY * sizeZ;

        List<BlockPos> blocks = new ArrayList<>(total);

        for (int x = lowerX; x <= upperX; x++) {
            for (int y = lowerY; y <= upperY; y++) {
                for (int z = lowerZ; z <= upperZ; z++) {
                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }

        return blocks;
    }

    public Iterable<ChunkPos> getChunksIterator() {
        ChunkPos minChunk = getMinium().getChunkPos();
        ChunkPos maxChunk = getMaximum().getChunkPos();

        final int lowerX = minChunk.getX();
        final int lowerZ = minChunk.getZ();
        final int upperX = maxChunk.getX();
        final int upperZ = maxChunk.getZ();

        return () -> new Iterator<>() {
            int x = lowerX;
            int z = lowerZ;

            @Override
            public boolean hasNext() {
                return x <= upperX;
            }

            @Override
            public ChunkPos next() {
                ChunkPos pos = new ChunkPos(x, z);

                z++;
                if (z > upperZ) {
                    z = lowerZ;
                    x++;
                }

                return pos;
            }
        };
    }

    public List<ChunkPos> getChunks(){
        ChunkPos minChunk = getMinium().getChunkPos();
        ChunkPos maxChunk = getMaximum().getChunkPos();

        final int lowerX = minChunk.getX();
        final int lowerZ = minChunk.getZ();
        final int upperX = maxChunk.getX();
        final int upperZ = maxChunk.getZ();

        List<ChunkPos> allChunks = new ArrayList<>();

        for (int x = lowerX; x <= upperX; x++) {
            for (int z = lowerZ; z <= upperZ; z++) {
                allChunks.add(new ChunkPos(x,z));
            }
        }

        return allChunks;
    }

    public List<BlockPos> getCorners2D() {
        List<BlockPos> corners = new ArrayList<>(4);
        int minX = minium.getX();
        int minZ = minium.getZ();
        int maxX = maximum.getX();
        int maxZ = maximum.getZ();

        int minY = minium.getY();

        corners.add(new BlockPos(minX, minY, minZ));
        corners.add(new BlockPos(minX, minY, maxZ));
        corners.add(new BlockPos(maxX, minY, minZ));
        corners.add(new BlockPos(maxX, minY, maxZ));

        return corners;
    }

    public List<BlockPos> getCorners3D() {
        List<BlockPos> corners = new ArrayList<>(8);
        int minX = minium.getX();
        int minZ = minium.getZ();
        int maxX = maximum.getX();
        int maxZ = maximum.getZ();

        int minY = minium.getY();
        int maxY = maximum.getY();

        for (int y : new int[]{minY, maxY}) {
            corners.add(new BlockPos(minX, y, minZ));
            corners.add(new BlockPos(minX, y, maxZ));
            corners.add(new BlockPos(maxX, y, minZ));
            corners.add(new BlockPos(maxX, y, maxZ));
        }

        return corners;
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

    public CuboidSelection contract(int amount){
        return this.contract(new BlockPos(amount,amount,amount), new BlockPos(-amount,-amount,-amount));
    }

    public CuboidSelection contract(BlockPos blockPos, BlockPos... otherBlockPos) {

        for (int i = -1; i < otherBlockPos.length; i++) {
            BlockPos change = i == -1 ? blockPos : otherBlockPos[i];
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

    public CuboidSelection expand(int amount){
        return this.expand(new BlockPos(amount,amount,amount), new BlockPos(-amount,-amount,-amount));
    }

    public CuboidSelection expand(BlockPos blockPos, BlockPos... otherBlockPos) {

        for (int i = -1; i < otherBlockPos.length; i++) {
            BlockPos change = i == -1 ? blockPos : otherBlockPos[i];
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
        return new CuboidSelection(this.pos1, this.pos2, this.minium, this.maximum);
    }

    public CuboidSelection expandHoriz(int amount){
        expand(new BlockPos(amount, 0, amount), new BlockPos(-amount, 0, -amount));
        this.recalculate();
        return this;
    }

    public CuboidSelection expandVert(int amount){
        expand(new BlockPos(0, amount, 0), new BlockPos(0, -amount, 0));
        this.recalculate();
        return this;
    }

    public CuboidSelection expandVert(){
        int minHeight = RegionGridOptions.getCurrent().getMinHeight();
        int maxHeight = RegionGridOptions.getCurrent().getMaxHeight();
        if (this.pos1.getY() < this.pos2.getY()){
            this.pos1 = this.pos1.setY(minHeight);
            this.pos2 = this.pos2.setY(maxHeight);
        } else{
            this.pos1 = this.pos1.setY(maxHeight);
            this.pos2 = this.pos2.setY(minHeight);
        }
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
        return serialize();
    }

    public String serialize() {
        return minium.serialize() + " <> " + maximum.serialize();
    }

    /**
     * Deserializes patterns like:
     *
     *   x1|y1|z1 <> x2|y2|z2
     *
     */
    public static CuboidSelection deserialize(String s) {
        int sep = s.indexOf(" <> ");
        if (sep == -1) {
            throw new IllegalArgumentException("Invalid cuboid format. Should be something like 'x1|y1|z1 <> x2|y2|z2' but this was found: " + s);
        }

        BlockPos min = BlockPos.deserialize(s.substring(0, sep));
        BlockPos max = BlockPos.deserialize(s.substring(sep + 4, s.length()));

        return new CuboidSelection(min, max);
    }

}
