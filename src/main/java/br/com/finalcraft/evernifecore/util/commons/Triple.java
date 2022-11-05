package br.com.finalcraft.evernifecore.util.commons;

public class Triple<ALFA, BETA, GAMA> {

    public ALFA alfa;
    public BETA beta;
    public GAMA gama;

    private Triple(ALFA alfa, BETA beta, GAMA gama) {
        this.alfa = alfa;
        this.beta = beta;
        this.gama = gama;
    }

    public static <ALFA,BETA,GAMA> Triple<ALFA,BETA,GAMA> of(ALFA alfa, BETA beta, GAMA gama) {
        return new Triple<>(alfa, beta, gama);
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

    public GAMA getGama() {
        return gama;
    }

    public void setGama(GAMA gama) {
        this.gama = gama;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Triple<?, ?, ?> triple = (Triple<?, ?, ?>) o;

        if (alfa != null ? !alfa.equals(triple.alfa) : triple.alfa != null) return false;
        if (beta != null ? !beta.equals(triple.beta) : triple.beta != null) return false;
        return gama != null ? gama.equals(triple.gama) : triple.gama == null;
    }

    @Override
    public int hashCode() {
        int result = alfa != null ? alfa.hashCode() : 0;
        result = 31 * result + (beta != null ? beta.hashCode() : 0);
        result = 31 * result + (gama != null ? gama.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Triple{" +
                "alfa=" + alfa +
                ", beta=" + beta +
                ", gama=" + gama +
                '}';
    }
}
