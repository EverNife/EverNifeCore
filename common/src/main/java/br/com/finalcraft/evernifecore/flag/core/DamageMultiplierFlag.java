package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.ActionContext;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.flag.FlagResolver;
import br.com.finalcraft.evernifecore.flag.FlagResolvers;
import br.com.finalcraft.evernifecore.flag.FlagScope;
import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.evernifecore.flag.FlagTypes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * {@code damage-multiplier}: scales the damage a player takes. The layers multiply ({@code 2.0} in the region and
 * {@code 3.0} on the party is {@code 6.0}), kept within the settings' floor and cap. It never denies: it writes its
 * factor on the hit ({@link DamageContext#multiplyDamage}) for the damage listener to apply - and an {@code invincible}
 * player is denied before, so there is nothing to multiply.
 */
public final class DamageMultiplierFlag extends Flag<Double> {

    public DamageMultiplierFlag() {
        super(FlagKey.of(CoreFlags.OWNER, "damage-multiplier"), FlagTypes.DOUBLE, Collections.singleton(EventSource.DAMAGE), null);
    }

    @Override
    public boolean applies(ActionContext context) {
        return DenyFlag.hurts(context, ECEntityType.PLAYER, ECEntityType.FAKE_PLAYER);
    }

    @Override
    public Decision decide(ActionContext context, Double value) {
        if (applies(context)) {
            ((DamageContext) context).multiplyDamage(this, value);
        }
        return Decision.PASS;
    }

    @Override
    public String getDescription() {
        return "Multiplies the damage a player takes in the region (2.0 doubles it, 0.5 halves it); layers multiply, within the"
                + " settings' floor and cap.";
    }

    /** It follows the player: a holder that follows them may carry it. */
    @Override
    public FlagScope getScope() {
        return FlagScope.ACTOR;
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return DamageMultiplierSettings.class;
    }

    /** The product of the layers within the server settings' bounds. */
    @Override
    public FlagResolver<Double> getResolver() {
        DamageMultiplierSettings settings = getSettings();
        return FlagResolvers.productCapped(settings.getFloor(), settings.getCap());
    }

    @Override
    public List<FlagResolver<Double>> getAcceptedResolvers() {
        return Arrays.asList(getResolver(), FlagResolvers.<Double>max(), FlagResolvers.<Double>firstDefined());
    }

    /** {@code product-capped} reads the bounds of the settings in effect - a region may set its own. */
    @Override
    public FlagResolver<Double> getResolver(FlagSettings settings) {
        FlagResolver<Double> named = super.getResolver(settings);
        if (named.getId().equals("product-capped") && settings instanceof DamageMultiplierSettings) {
            DamageMultiplierSettings bounds = (DamageMultiplierSettings) settings;
            return FlagResolvers.productCapped(bounds.getFloor(), bounds.getCap());
        }
        return named;
    }

    @Override
    public Set<String> getChannels() {
        return Collections.singleton("damage:player-victim");
    }
}
