package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.quest.Objective;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import br.com.finalcraft.evernifecore.quest.RegionObjectiveType;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Persistence;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.RegionOptions;
import br.com.finalcraft.evernifecore.region.event.RegionCrossedEvent;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.types.ParamSpec;
import br.com.finalcraft.evernifecore.types.ParsedParams;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code reach-location}: "get to X, Y, Z". Registering the quest creates one {@code TRANSIENT} region per objective in
 * the quest plugin's {@link RegionManager} - a box of {@code radius} around the point, named
 * {@code quest-<quest>-<objective>} and tagged {@value #TAG} - so walking in is a border crossing; unregistering removes it.
 */
public final class ReachLocationObjectiveType implements RegionObjectiveType<ReachLocationObjectiveType.Target> {

    /** The tag of every region a reach-location objective made. */
    public static final String TAG = "evernifecore:quest-target";

    @Override
    public String getKey() {
        return "evernifecore:reach-location";
    }

    @Override
    public String getDescription() {
        return "Done when the player reaches a point: a temporary region around it, made when the quest is registered.";
    }

    @Override
    public List<ParamSpec> getParams() {
        return Arrays.asList(
                ParamSpec.required("world", ParamSpec.Kind.STRING, "The world, e.g. world."),
                ParamSpec.required("x", ParamSpec.Kind.NUMBER, "The point's x."),
                ParamSpec.required("y", ParamSpec.Kind.NUMBER, "The point's y."),
                ParamSpec.required("z", ParamSpec.Kind.NUMBER, "The point's z."),
                ParamSpec.optional("radius", ParamSpec.Kind.NUMBER, "2", "How close counts, in blocks."));
    }

    @Override
    public Target parseFilter(ParsedParams params) {
        return new Target(params.getString("world"), params.getNumber("x").intValue(), params.getNumber("y").intValue(),
                params.getNumber("z").intValue(), Math.max(0, params.getNumber("radius").intValue()));
    }

    @Override
    public long countOf(RegionCrossedEvent event, Target target) {
        WorldLocPos to = event.getTo();
        return to != null && target.contains(to) ? 1 : 0;
    }

    @Override
    public void onRegister(QuestEngine engine, QuestId quest, Objective objective, Target target) {
        RegionManager regions = RegionManager.of(engine.getOwner());
        String name = regionName(quest, objective);
        Region existing = regions.get(name);
        if (existing != null) {
            regions.remove(existing); //a new version of the quest may have moved the point
        }
        int r = target.radius;
        CreationResult result = regions.create(name, target.world,
                CuboidShape.of(BlockPos.of(target.x - r, target.y - r, target.z - r), BlockPos.of(target.x + r, target.y + r, target.z + r)),
                RegionOptions.defaults().withPersistence(Persistence.TRANSIENT).withTags(TAG));
        if (!result.isCreated()) {
            engine.getOwner().getLog().warning("The target region of objective '{}' of quest {} was not created ({}); reaching it will not count.",
                    objective.getId(), quest, result.getRefusal());
        }
    }

    @Override
    public void onUnregister(QuestEngine engine, QuestId quest, Objective objective, Target target) {
        RegionManager regions = RegionManager.ofIfPresent(engine.getOwner());
        Region region = regions == null || regions.isClosed() ? null : regions.get(regionName(quest, objective));
        if (region != null) {
            regions.remove(region);
        }
    }

    /** {@code quest-<quest>-<objective>}, in the characters a region name takes. */
    static String regionName(QuestId quest, Objective objective) {
        String name = ("quest-" + quest.getName() + "-" + objective.getId()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return name.length() > 64 ? name.substring(0, 64) : name;
    }

    /** The point and how close counts. */
    public static final class Target {

        final String world;
        final int x;
        final int y;
        final int z;
        final int radius;

        Target(String world, int x, int y, int z, int radius) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }

        boolean contains(WorldLocPos at) {
            return at.getWorldName().equals(world) && Math.abs(Math.floor(at.getX()) - x) <= radius
                    && Math.abs(Math.floor(at.getY()) - y) <= radius && Math.abs(Math.floor(at.getZ()) - z) <= radius;
        }

        /** The point, for a waypoint. */
        public WorldLocPos getCenter() {
            return WorldLocPos.of(x + 0.5, y, z + 0.5, world);
        }

        @Override
        public String toString() {
            return world + " " + x + ", " + y + ", " + z + " (radius " + radius + ")";
        }
    }
}
