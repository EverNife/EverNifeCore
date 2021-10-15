package br.com.finalcraft.evernifecore.util.commons;

public class Triple<ALFA, BETA, GAMA> {

    public ALFA alfa;
    public BETA beta;
    public GAMA gama;

    public Triple(ALFA alfa, BETA beta, GAMA gama) {
        this.alfa = alfa;
        this.beta = beta;
        this.gama = gama;
    }

    public static <ALFA,BETA,GAMA> Triple<ALFA,BETA,GAMA> from(ALFA alfa, BETA beta, GAMA gama) {
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

}
