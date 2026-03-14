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

    public static <LEFT, MIDDLE, RIGHT> Triple<LEFT, MIDDLE, RIGHT> of(LEFT left, MIDDLE middle, RIGHT right) {
        return new Triple<>(left, middle, right);
    }

}
