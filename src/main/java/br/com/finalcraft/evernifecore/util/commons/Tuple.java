package br.com.finalcraft.evernifecore.util.commons;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Tuple<ALFA, BETA> {

    private ALFA left;
    private BETA right;

    private Tuple(ALFA left, BETA right) {
        this.left = left;
        this.right = right;
    }

    public static <ALFA, BETA> Tuple<ALFA, BETA> of(ALFA alfa, BETA beta){
        return new Tuple<>(alfa,beta);
    }

}
