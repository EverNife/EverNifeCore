package br.com.finalcraft.evernifecore.action.event;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.BlockContext;
import br.com.finalcraft.evernifecore.action.CommandContext;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.EntityContext;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.ExploreContext;
import br.com.finalcraft.evernifecore.action.ExplosionContext;
import br.com.finalcraft.evernifecore.action.FluidContext;
import br.com.finalcraft.evernifecore.action.ItemContext;
import br.com.finalcraft.evernifecore.action.SimpleContext;
import br.com.finalcraft.evernifecore.action.SpawnContext;
import br.com.finalcraft.evernifecore.action.VehicleContext;
import br.com.finalcraft.evernifecore.action.XpContext;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The catalog that ties each core {@link EventSource} to its {@link ActionEvent} pair. Every core source that is an
 * action has one; {@link EventSource#MOVE} (region enter/leave events speak for it), {@link EventSource#TICK} and
 * {@link EventSource#NONE} have none, and neither has a plugin's own source.
 */
public final class ActionEvents {

    private static final Map<EventSource, Pair> CATALOG = new LinkedHashMap<>();

    static {
        add(EventSource.BREAK, BreakActionEvent.Pre.class, BreakActionEvent.Post.class,
                context -> new BreakActionEvent.Pre(context.as(BlockContext.class)), context -> new BreakActionEvent.Post(context.as(BlockContext.class)));
        add(EventSource.PLACE, PlaceActionEvent.Pre.class, PlaceActionEvent.Post.class,
                context -> new PlaceActionEvent.Pre(context.as(BlockContext.class)), context -> new PlaceActionEvent.Post(context.as(BlockContext.class)));
        add(EventSource.INTERACT, InteractActionEvent.Pre.class, InteractActionEvent.Post.class, InteractActionEvent.Pre::new, InteractActionEvent.Post::new);
        add(EventSource.DAMAGE, DamageActionEvent.Pre.class, DamageActionEvent.Post.class,
                context -> new DamageActionEvent.Pre(context.as(DamageContext.class)), context -> new DamageActionEvent.Post(context.as(DamageContext.class)));
        add(EventSource.SPAWN, SpawnActionEvent.Pre.class, SpawnActionEvent.Post.class,
                context -> new SpawnActionEvent.Pre(context.as(SpawnContext.class)), context -> new SpawnActionEvent.Post(context.as(SpawnContext.class)));
        add(EventSource.EXPLOSION, ExplosionActionEvent.Pre.class, ExplosionActionEvent.Post.class,
                context -> new ExplosionActionEvent.Pre(context.as(ExplosionContext.class)), context -> new ExplosionActionEvent.Post(context.as(ExplosionContext.class)));
        add(EventSource.FLUID, FluidActionEvent.Pre.class, FluidActionEvent.Post.class,
                context -> new FluidActionEvent.Pre(context.as(FluidContext.class)), context -> new FluidActionEvent.Post(context.as(FluidContext.class)));
        add(EventSource.RESPAWN, RespawnActionEvent.Pre.class, RespawnActionEvent.Post.class,
                context -> new RespawnActionEvent.Pre(context.as(SimpleContext.class)), context -> new RespawnActionEvent.Post(context.as(SimpleContext.class)));
        add(EventSource.ITEM_PICKUP, PickupActionEvent.Pre.class, PickupActionEvent.Post.class,
                context -> new PickupActionEvent.Pre(context.as(ItemContext.class)), context -> new PickupActionEvent.Post(context.as(ItemContext.class)));
        add(EventSource.ITEM_DROP, DropActionEvent.Pre.class, DropActionEvent.Post.class,
                context -> new DropActionEvent.Pre(context.as(ItemContext.class)), context -> new DropActionEvent.Post(context.as(ItemContext.class)));
        add(EventSource.VEHICLE, VehicleActionEvent.Pre.class, VehicleActionEvent.Post.class,
                context -> new VehicleActionEvent.Pre(context.as(VehicleContext.class)), context -> new VehicleActionEvent.Post(context.as(VehicleContext.class)));
        add(EventSource.FIRE_SPREAD, FireSpreadActionEvent.Pre.class, FireSpreadActionEvent.Post.class,
                context -> new FireSpreadActionEvent.Pre(context.as(BlockContext.class)), context -> new FireSpreadActionEvent.Post(context.as(BlockContext.class)));
        add(EventSource.FLIGHT_TOGGLE, FlightToggleActionEvent.Pre.class, FlightToggleActionEvent.Post.class,
                context -> new FlightToggleActionEvent.Pre(context.as(SimpleContext.class)), context -> new FlightToggleActionEvent.Post(context.as(SimpleContext.class)));
        add(EventSource.KILL, KillActionEvent.Pre.class, KillActionEvent.Post.class,
                context -> new KillActionEvent.Pre(context.as(DamageContext.class)), context -> new KillActionEvent.Post(context.as(DamageContext.class)));
        add(EventSource.CRAFT, CraftActionEvent.Pre.class, CraftActionEvent.Post.class,
                context -> new CraftActionEvent.Pre(context.as(ItemContext.class)), context -> new CraftActionEvent.Post(context.as(ItemContext.class)));
        add(EventSource.SMELT, SmeltActionEvent.Pre.class, SmeltActionEvent.Post.class,
                context -> new SmeltActionEvent.Pre(context.as(ItemContext.class)), context -> new SmeltActionEvent.Post(context.as(ItemContext.class)));
        add(EventSource.FISH, FishActionEvent.Pre.class, FishActionEvent.Post.class,
                context -> new FishActionEvent.Pre(context.as(ItemContext.class)), context -> new FishActionEvent.Post(context.as(ItemContext.class)));
        add(EventSource.CONSUME, ConsumeActionEvent.Pre.class, ConsumeActionEvent.Post.class,
                context -> new ConsumeActionEvent.Pre(context.as(ItemContext.class)), context -> new ConsumeActionEvent.Post(context.as(ItemContext.class)));
        add(EventSource.XP_GAIN, XpGainActionEvent.Pre.class, XpGainActionEvent.Post.class,
                context -> new XpGainActionEvent.Pre(context.as(XpContext.class)), context -> new XpGainActionEvent.Post(context.as(XpContext.class)));
        add(EventSource.ENCHANT, EnchantActionEvent.Pre.class, EnchantActionEvent.Post.class,
                context -> new EnchantActionEvent.Pre(context.as(ItemContext.class)), context -> new EnchantActionEvent.Post(context.as(ItemContext.class)));
        add(EventSource.TAME, TameActionEvent.Pre.class, TameActionEvent.Post.class,
                context -> new TameActionEvent.Pre(context.as(EntityContext.class)), context -> new TameActionEvent.Post(context.as(EntityContext.class)));
        add(EventSource.BREED, BreedActionEvent.Pre.class, BreedActionEvent.Post.class,
                context -> new BreedActionEvent.Pre(context.as(EntityContext.class)), context -> new BreedActionEvent.Post(context.as(EntityContext.class)));
        add(EventSource.SHEAR, ShearActionEvent.Pre.class, ShearActionEvent.Post.class,
                context -> new ShearActionEvent.Pre(context.as(EntityContext.class)), context -> new ShearActionEvent.Post(context.as(EntityContext.class)));
        add(EventSource.COMMAND, CommandActionEvent.Pre.class, CommandActionEvent.Post.class,
                context -> new CommandActionEvent.Pre(context.as(CommandContext.class)), context -> new CommandActionEvent.Post(context.as(CommandContext.class)));
        add(EventSource.INTERACT_NPC, InteractNpcActionEvent.Pre.class, InteractNpcActionEvent.Post.class,
                context -> new InteractNpcActionEvent.Pre(context.as(EntityContext.class)), context -> new InteractNpcActionEvent.Post(context.as(EntityContext.class)));
        add(EventSource.EXPLORE, ExploreActionEvent.Pre.class, ExploreActionEvent.Post.class,
                context -> new ExploreActionEvent.Pre(context.as(ExploreContext.class)), context -> new ExploreActionEvent.Post(context.as(ExploreContext.class)));
    }

    private ActionEvents() {
    }

    private static void add(EventSource source, Class<? extends ActionEvent<?>> pre, Class<? extends ActionEvent<?>> post,
                            Function<ActionContext, ActionEvent<?>> newPre, Function<ActionContext, ActionEvent<?>> newPost) {
        CATALOG.put(source, new Pair(pre, post, newPre, newPost));
    }

    /** The pair of {@code source}; null for a source with no action event. */
    public static @Nullable Pair of(EventSource source) {
        return CATALOG.get(source);
    }

    /** Every source with an action event, in catalog order. */
    public static Set<EventSource> all() {
        return Collections.unmodifiableSet(CATALOG.keySet());
    }

    /** The two halves of one source's event. */
    public static final class Pair {

        private final Class<? extends ActionEvent<?>> pre;
        private final Class<? extends ActionEvent<?>> post;
        private final Function<ActionContext, ActionEvent<?>> newPre;
        private final Function<ActionContext, ActionEvent<?>> newPost;

        private Pair(Class<? extends ActionEvent<?>> pre, Class<? extends ActionEvent<?>> post, Function<ActionContext, ActionEvent<?>> newPre,
                     Function<ActionContext, ActionEvent<?>> newPost) {
            this.pre = pre;
            this.post = post;
            this.newPre = newPre;
            this.newPost = newPost;
        }

        /** The cancellable half, posted before the outcome where the platform has such a point. */
        public Class<? extends ActionEvent<?>> getPre() {
            return pre;
        }

        /** The half posted after the outcome. */
        public Class<? extends ActionEvent<?>> getPost() {
            return post;
        }

        /**
         * A new {@code Pre} over {@code context}, for a platform or a plugin that posts the event itself.
         *
         * @throws IllegalArgumentException when {@code context} is of another source or kind than this pair's
         */
        public ActionEvent<?> newPre(ActionContext context) {
            return newPre.apply(context);
        }

        /** A new {@code Post} over {@code context}; the same refusals as {@link #newPre}. */
        public ActionEvent<?> newPost(ActionContext context) {
            return newPost.apply(context);
        }
    }
}
