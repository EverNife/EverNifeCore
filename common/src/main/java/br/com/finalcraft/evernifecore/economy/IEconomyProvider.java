package br.com.finalcraft.evernifecore.economy;

import java.math.BigDecimal;
import java.util.UUID;


public interface IEconomyProvider {

    public boolean ecoGive(UUID playerUUID, BigDecimal amount);

    public boolean ecoGive(UUID playerUUID, double amount);

    public boolean ecoTake(UUID playerUUID, BigDecimal amount);

    public boolean ecoTake(UUID playerUUID, double amount);

    public boolean ecoSet(UUID playerUUID, BigDecimal amount);

    public boolean ecoSet(UUID playerUUID, double amount);

    public double ecoGet(UUID playerUUID);

    public BigDecimal ecoGetInBigDecimal(UUID playerUUID);

    public boolean ecoHasEnough(UUID playerUUID, BigDecimal amount);

    public boolean ecoHasEnough(UUID playerUUID, double amount);

}
