package br.com.finalcraft.evernifecore.util.commons;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Tuple<LEFT, RIGHT> {

    private LEFT left;
    private RIGHT right;

    private Tuple(LEFT left, RIGHT right) {
        this.left = left;
        this.right = right;
    }

    public static <LEFT, RIGHT> Tuple<LEFT, RIGHT> of(LEFT left, RIGHT right){
        return new Tuple<>(left, right);
    }

}
