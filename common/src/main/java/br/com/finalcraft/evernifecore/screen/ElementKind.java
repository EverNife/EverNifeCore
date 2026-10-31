package br.com.finalcraft.evernifecore.screen;

/** What an element is, so a renderer that draws by element knows which widget it stands for. */
public enum ElementKind {
    /** An icon with no behaviour: background, a layout icon nobody bound. */
    DECORATION,
    BUTTON,
    LABEL,
    LIST_ENTRY,
    INPUT,
    PROGRESS,
    CHOICE
}
