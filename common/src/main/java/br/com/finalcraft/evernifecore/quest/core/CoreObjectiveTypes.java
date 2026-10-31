package br.com.finalcraft.evernifecore.quest.core;

import br.com.finalcraft.evernifecore.quest.ObjectiveType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The core's stock objective types: one per action source, plus {@code state}, {@code timer} and {@code quest-completed}. */
public final class CoreObjectiveTypes {

    private CoreObjectiveTypes() {
    }

    public static List<ObjectiveType<?>> all() {
        return Collections.unmodifiableList(Arrays.<ObjectiveType<?>>asList(
                new BreakBlockObjectiveType(), new PlaceBlockObjectiveType(), new InteractObjectiveType(), new DamageObjectiveType(),
                new SpawnObjectiveType(), new ExplosionObjectiveType(), new FluidObjectiveType(), new RespawnObjectiveType(),
                new PickupObjectiveType(), new DropObjectiveType(), new VehicleObjectiveType(), new FireSpreadObjectiveType(),
                new FlightToggleObjectiveType(), new KillObjectiveType(), new CraftObjectiveType(), new SmeltObjectiveType(),
                new FishObjectiveType(), new ConsumeObjectiveType(), new XpObjectiveType(), new EnchantObjectiveType(),
                new TameObjectiveType(), new BreedObjectiveType(), new ShearObjectiveType(), new CommandObjectiveType(),
                new InteractNpcObjectiveType(), new ExploreObjectiveType(),
                new StateObjectiveType(), new TimerObjectiveType(), new QuestCompletedObjectiveType()));
    }
}
