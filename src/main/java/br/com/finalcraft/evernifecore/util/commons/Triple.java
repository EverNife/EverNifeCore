package br.com.finalcraft.evernifecore.util.commons;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Triple<LEFT, MIDDLE, RIGHT> {

    public LEFT left;
    public MIDDLE middle;
    public RIGHT right;

    private Triple(LEFT left, MIDDLE middle, RIGHT right) {
        this.left = left;
        this.middle = middle;
        this.right = right;
    }

    public static <ALFA,BETA,GAMA> Triple<ALFA,BETA,GAMA> of(ALFA alfa, BETA beta, GAMA gama) {
        return new Triple<>(alfa, beta, gama);
    }

}
