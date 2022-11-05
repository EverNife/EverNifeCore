package br.com.finalcraft.evernifecore.util.commons;

import java.util.Objects;

public class Tuple<ALFA, BETA> {

    private ALFA alfa;
    private BETA beta;

    private Tuple(ALFA alfa, BETA beta) {
        this.alfa = alfa;
        this.beta = beta;
    }

    public ALFA getAlfa() {
        return alfa;
    }

    public void setAlfa(ALFA alfa) {
        this.alfa = alfa;
    }

    public BETA getBeta() {
        return beta;
    }

    public void setBeta(BETA beta) {
        this.beta = beta;
    }

    public static <ALFA, BETA> Tuple<ALFA, BETA> of(ALFA alfa, BETA beta){
        return new Tuple<>(alfa,beta);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuple<?, ?> tuple = (Tuple<?, ?>) o;
        return Objects.equals(alfa, tuple.alfa) && Objects.equals(beta, tuple.beta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alfa, beta);
    }

    @Override
    public String toString() {
        return "Tuple{" +
                "alfa=" + alfa +
                ", beta=" + beta +
                '}';
    }
}
