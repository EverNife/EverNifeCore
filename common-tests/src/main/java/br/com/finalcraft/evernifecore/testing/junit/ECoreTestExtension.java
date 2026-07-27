package br.com.finalcraft.evernifecore.testing.junit;

import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/** Drives {@link ECoreTest}: installs the chosen platform for the class and closes it afterwards. */
public class ECoreTestExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ECoreTestExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getStore(NAMESPACE).put(ECoreTestWorld.class, worldFor(context));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        ECoreTestWorld world = context.getStore(NAMESPACE).remove(ECoreTestWorld.class, ECoreTestWorld.class);
        if (world != null) {
            world.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return type == ECoreTestWorld.class || type == TestPlatform.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        ECoreTestWorld world = worldOf(extensionContext);
        return parameterContext.getParameter().getType() == TestPlatform.class ? world.platform() : world;
    }

    private static ECoreTestWorld worldOf(ExtensionContext context) {
        ExtensionContext current = context;
        while (current != null) {
            ECoreTestWorld world = current.getStore(NAMESPACE).get(ECoreTestWorld.class, ECoreTestWorld.class);
            if (world != null) {
                return world;
            }
            current = current.getParent().orElse(null);
        }
        throw new IllegalStateException("No @ECoreTest world installed for " + context.getDisplayName());
    }

    private static ECoreTestWorld worldFor(ExtensionContext context) {
        ECoreTest.Mode mode = context.getElement()
                .map(element -> element.getAnnotation(ECoreTest.class))
                .map(ECoreTest::value)
                .orElse(ECoreTest.Mode.LENIENT);

        return platformFor(mode).install();
    }

    private static Platforms platformFor(ECoreTest.Mode mode) {
        switch (mode) {
            case STRICT:
                return Platforms.strict();
            case COMMAND_CAPTURE:
                return Platforms.commandCapture();
            default:
                return Platforms.lenient();
        }
    }
}
