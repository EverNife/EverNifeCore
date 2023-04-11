package br.com.finalcraft.evernifecore.commands.finalcmd.argument;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum ArgRequirementType {
    REQUIRED_OR_PROVIDED_BY_CONTEXT("<[", "]>", true),
    OPTIONAL("[", "]", false),
    REQUIRED("<", ">", true);

    private final String start;
    private final String end;
    private final boolean required;

    ArgRequirementType(String start, String end, boolean required) {
        this.start = start;
        this.end = end;
        this.required = required;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    public boolean isRequired() {
        return required;
    }

    public static @Nullable ArgRequirementType getArgumentType(@NotNull String argument){

        if (argument.length() >= 2) {
            for (ArgRequirementType requirementType : values()) {
                if (argument.startsWith(requirementType.getStart()) && argument.endsWith(requirementType.getEnd())) {
                    return requirementType;
                }
            }
        }

        return null;
    }

}
