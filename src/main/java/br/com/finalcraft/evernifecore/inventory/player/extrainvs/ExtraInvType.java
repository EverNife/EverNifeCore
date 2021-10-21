package br.com.finalcraft.evernifecore.inventory.player.extrainvs;

import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.inventory.player.extrainvs.wrappers.BaublesInv;
import br.com.finalcraft.evernifecore.inventory.player.extrainvs.wrappers.TinkersInv;

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
