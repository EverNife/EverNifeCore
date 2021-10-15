package br.com.finalcraft.evernifecore.config.playerinventory.extrainvs;

import br.com.finalcraft.evernifecore.config.playerinventory.extrainvs.wrappers.BaublesInv;
import br.com.finalcraft.evernifecore.config.playerinventory.extrainvs.wrappers.TinkersInv;
import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;

public enum ExtraInvType {
    BAUBLES{
        @Override
        public boolean isEnabled() {
            return EverForgeLibIntegration.baublesLoaded;
        }

        @Override
        public ExtraInv createExtraInv() {
            return new BaublesInv();
        }
    },
    TINKERS {
        @Override
        public boolean isEnabled() {
            return EverForgeLibIntegration.tinkersLoaded;
        }

        @Override
        public ExtraInv createExtraInv() {
            return new TinkersInv();
        }
    };


    public abstract ExtraInv createExtraInv();
    public abstract boolean isEnabled();
}
