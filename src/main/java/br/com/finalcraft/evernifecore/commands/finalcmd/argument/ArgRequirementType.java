package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum ArgRequirementType {
    OPTIONAL('[', ']'),
    REQUIRED('<', '>');

    ArgRequirementType(char start, char end) {
        this.start = start;
        this.end = end;
    }

    private final char start;
    private final char end;

    public char getStart() {
        return start;
    }

    public char getEnd() {
        return end;
    }

    public static @Nullable ArgRequirementType getArgumentType(@NotNull String argument){

        if (argument.length() >= 2) {
            for (ArgRequirementType requirementType : values()) {
                if (argument.charAt(0) == requirementType.start && argument.charAt(argument.length() - 1) == requirementType.end){
                    return requirementType;
                }
            }
        }

        return null;
    }

}
