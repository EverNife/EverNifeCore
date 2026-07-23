package br.com.finalcraft.evernifecore.storage;

/**
 * One enabled backend that failed to connect during {@link StorageRegistry#initAll()}, with the
 * target it was pointed at and the root cause. Carried by {@link StorageUnavailableException} - one
 * per broken backend, so an admin fixes every one of them in a single reboot instead of one at a time.
 */
public final class StorageInitFailure {

    private final String backendName;
    private final BackendType type;       // nullable when the registry has no BackendDefinition for it (tests)
    private final String target;          // already redacted, never carries a password
    private final Throwable cause;        // already unwrapped from CompletionException

    public StorageInitFailure(String backendName, BackendType type, String target, Throwable cause) {
        this.backendName = backendName;
        this.type = type;
        this.target = target;
        this.cause = cause;
    }

    public String getBackendName() {
        return backendName;
    }

    public BackendType getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public Throwable getCause() {
        return cause;
    }

    /** The deepest cause's "Type: message" - what actually says WHY (e.g. "ConnectException: Connection refused"). */
    public String getRootCauseSummary() {
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        // a driver message can carry its own paragraph; keep the first line, the rest is in the stack trace
        if (message != null) {
            int lineBreak = message.indexOf('\n');
            if (lineBreak >= 0) {
                message = message.substring(0, lineBreak).trim();
            }
        }
        return root.getClass().getSimpleName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }
}
