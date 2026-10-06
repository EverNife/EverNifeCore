package br.com.finalcraft.evernifecore.region.flag;

/**
 * A plugin's own code for one flag in one region, set with {@code Region.setFlagHandler}: it hears every
 * {@link FlagOutcome} of that flag there, after the flag's own {@link RegionFlag#onApplied}. On a denied movement it
 * may also choose where the player goes back to; everywhere else it only reacts. Never stored: set it again on every
 * enable. Runs on the thread of the event; what it throws propagates.
 */
@FunctionalInterface
public interface FlagHandler {

    void handle(FlagOutcomeContext context);
}
