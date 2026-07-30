package br.com.finalcraft.evernifecore.commands.finalcmd.tree;

import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CaptureData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ExecuteData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.NodeData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.SubCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.ICustomFinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.contexts.CustomizeContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.MethodData;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContextTemplate;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLineTemplate;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.FCMultiLocales;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.locale.scanner.FCLocaleScanner;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.everylibs.commons.Tuple;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns an annotated command class into the {@link CommandNode} tree the dispatcher walks.
 * <p>
 * Everything that can be wrong with the SHAPE of a tree is refused right here, before the server
 * opens, with a message that names the class, the member, the node path and the call that fixes it -
 * a malformed tree is never something a player discovers in game.
 */
public final class CommandTreeScanner {

    /** Past this, a help line is unreadable long before the framework has any trouble with it. */
    private static final int DEEP_TREE_WARNING_DEPTH = 6;

    private CommandTreeScanner() {
    }

    /** One command found on an executor, built into its tree and not yet registered anywhere. */
    public static final class ScannedCommand {

        private final FinalCMDData finalCMDData;
        private final CommandNode root;

        private ScannedCommand(FinalCMDData finalCMDData, CommandNode root) {
            this.finalCMDData = finalCMDData;
            this.root = root;
        }

        public FinalCMDData getFinalCMDData() {
            return finalCMDData;
        }

        public CommandNode getRoot() {
            return root;
        }
    }

    /**
     * Finds every {@code @FinalCMD} declared by {@code executor} - on its methods, or on the class
     * itself - and builds each one's tree, without touching the platform.
     * <p>
     * A shape error leaves here as an {@link ArgMountException}: this is the one place the whole
     * catalog is raised, so a caller that only wants the server to boot logs it and loses the command,
     * while a caller that wants to read what the message teaches gets it straight.
     *
     * @return one entry per command found, in declaration order; empty when the class declares none
     */
    public static List<ScannedCommand> scanCommands(@Nonnull ECPluginData owningPlugin, @Nonnull Object executor) {
        List<Tuple<FinalCMD, Method>> finalCMDMainMethods = new ArrayList<>();

        //Add all declared-methods from the class and its supper-classes until Object
        Set<Method> methods = new HashSet<>();
        Class<?> father = executor.getClass();
        while (father != null && father != Object.class){
            methods.addAll(Arrays.asList(father.getDeclaredMethods()));
            father = father.getSuperclass();
        }

        //Checking for all methods that have a @FinalCMD
        for (Method declaredMethod : methods) {
            if (declaredMethod.isAnnotationPresent(FinalCMD.Ignore.class)){
                continue;
            }

            FinalCMD finalCMD = FCReflectionUtil.getAnnotations().getAnnotationDeeply(declaredMethod, FinalCMD.class);
            if (finalCMD != null){
                finalCMDMainMethods.add(Tuple.of(finalCMD, declaredMethod));
            }
        }

        //If there is no method with @FinalCMD annotation, maybe the class itself is annotated
        if (finalCMDMainMethods.isEmpty()){
            FinalCMD finalCMD = FCReflectionUtil.getAnnotations().getAnnotationDeeply(executor.getClass(), FinalCMD.class);
            if (finalCMD == null){
                owningPlugin.getLog().severe("Tried to register a FinalCMD(" + executor.getClass().getName() + ") without any @FinalCMD Annotation!");
                return Collections.emptyList();
            }
            finalCMDMainMethods.add(Tuple.of(finalCMD, null));
        }

        //Load this class's (and its superclasses') static LocaleMessage fields, mirroring the
        //method scan above, which also walks up to Object
        loadClassLocales(owningPlugin, executor.getClass());

        if (finalCMDMainMethods.size() == 1){ //Check for SubCommands, maybe this @FinalCMD is in the Class
            Tuple<FinalCMD, Method> tuple = finalCMDMainMethods.get(0);

            //Method is null if we have a @FinalCMD annotation to the class rather than the function
            FinalCMDData finalCMDData = new FinalCMDData(tuple.getLeft());
            MethodData<FinalCMDData> mainMethodData = new MethodData(finalCMDData, tuple.getRight());

            List<MethodData<SubCMDData>> subCommandsMethodData = new ArrayList<>();
            for (Method declaredMethod : methods) {
                FinalCMD.SubCMD subCMD = FCReflectionUtil.getAnnotations().getAnnotationDeeply(declaredMethod, FinalCMD.SubCMD.class);
                if (subCMD != null){
                    subCommandsMethodData.add(new MethodData(new SubCMDData(subCMD), declaredMethod));
                }
            }

            CustomizeContext customizeContext = customize(owningPlugin, executor, mainMethodData, subCommandsMethodData);

            CommandNode root = scanRoot(owningPlugin, finalCMDData, executor, customizeContext.getMainMethod(), customizeContext.getSubMethods());
            return Collections.singletonList(new ScannedCommand(finalCMDData, root));
        }

        // We have several @FinalCMD annotated methods on this class, lets scan all of them.
        // Each one is a different command without any SubCommand
        List<ScannedCommand> scannedCommands = new ArrayList<>();
        for (Tuple<FinalCMD, Method> tuple : finalCMDMainMethods) {
            try {
                FinalCMDData finalCMDData = new FinalCMDData(tuple.getLeft());
                MethodData<FinalCMDData> mainMethodData = new MethodData(finalCMDData, tuple.getRight());
                CustomizeContext customizeContext = customize(owningPlugin, executor, mainMethodData, Collections.<MethodData<SubCMDData>>emptyList());

                //Several commands share this class, so none of them may own the class's tree:
                //a @FinalCMD.Node here would be mounted once per command, under every one of them
                CommandNode root = scanRoot(owningPlugin, finalCMDData, executor, customizeContext.getMainMethod(), Collections.<MethodData<SubCMDData>>emptyList(), false);
                scannedCommands.add(new ScannedCommand(finalCMDData, root));
            }catch (Throwable e){
                owningPlugin.getLog().severe("Error registering a FinalCMD on the class [" + executor.getClass().getName() + "] method " + tuple.getRight().getName() + "!", e);
            }
        }

        //We are in a case where there are several @FinalCMD methods, we cannot allow SubCMDs in this class
        // lets check for it just to warn the developer
        for (Method declaredMethod : methods) {
            if (declaredMethod.isAnnotationPresent(FinalCMD.SubCMD.class)){
                owningPlugin.getLog().severe("Found a SubCMD on the class [" + executor.getClass().getName() + "] method " + declaredMethod.getName() + " but the class has more than one FinalCMD, this will be ignored!");
            }
        }
        for (Field declaredField : executor.getClass().getDeclaredFields()) {
            if (declaredField.isAnnotationPresent(FinalCMD.Node.class)){
                owningPlugin.getLog().severe("Found a @FinalCMD.Node on the class [" + executor.getClass().getName() + "] field " + declaredField.getName() + " but the class has more than one FinalCMD, this will be ignored!");
            }
        }
        for (Class<?> declaredClass : executor.getClass().getDeclaredClasses()) {
            if (declaredClass.isAnnotationPresent(FinalCMD.Node.class)){
                owningPlugin.getLog().severe("Found a @FinalCMD.Node on the class [" + executor.getClass().getName() + "] nested class " + declaredClass.getSimpleName() + " but the class has more than one FinalCMD, this will be ignored!");
            }
        }

        return scannedCommands;
    }

    /** Lets the executor rewrite its own labels/locales before anything is mounted, then loads the validators' locales. */
    private static CustomizeContext customize(ECPluginData owningPlugin, Object executor, MethodData<FinalCMDData> mainMethodData, List<MethodData<SubCMDData>> subMethodsData) {
        CustomizeContext customizeContext = new CustomizeContext(mainMethodData, subMethodsData);

        if (executor instanceof ICustomFinalCMD){
            ((ICustomFinalCMD) executor).customize(customizeContext);
        }

        for (CMDData<?> cmdData : customizeContext.getAllCMDData()) {
            for (CMDAccessValidation cmdAccessValidation : cmdData.getCmdAccessValidations()) {
                Class<?> validationClass = cmdAccessValidation.getClass();
                //Maybe the Validation class is not from this ECPlugin, so lets make sure its loaded on its proper owner
                FCLocaleManager.loadLocale(ECPluginManager.getProvidingPlugin(validationClass), true, validationClass);
            }
        }

        return customizeContext;
    }

    /**
     * Builds the whole tree of one command. The root's own method data comes already customized (a
     * {@code ICustomFinalCMD} may have rewritten labels), everything below it is scanned here.
     */
    public static CommandNode scanRoot(@Nonnull ECPluginData owningPlugin,
                                       @Nonnull FinalCMDData finalCMDData,
                                       @Nonnull Object executor,
                                       @Nullable MethodData<FinalCMDData> mainMethodData,
                                       @Nonnull List<MethodData<SubCMDData>> subMethodsData) {
        return scanRoot(owningPlugin, finalCMDData, executor, mainMethodData, subMethodsData, true);
    }

    /**
     * @param scanChildNodes false when this class hosts several independent commands, so none of them
     * owns the {@code @FinalCMD.Node} members and no tree is mounted at all
     */
    public static CommandNode scanRoot(@Nonnull ECPluginData owningPlugin,
                                       @Nonnull FinalCMDData finalCMDData,
                                       @Nonnull Object executor,
                                       @Nullable MethodData<FinalCMDData> mainMethodData,
                                       @Nonnull List<MethodData<SubCMDData>> subMethodsData,
                                       boolean scanChildNodes) {

        CommandNode root = new CommandNode(null, finalCMDData, executor, ECPluginManager.getProvidingPlugin(executor.getClass()));

        if (mainMethodData != null && mainMethodData.getMethod() != null){
            root.setExecutable(new CMDMethodInterpreter(owningPlugin, mainMethodData, executor, root));
        }

        for (MethodData<SubCMDData> subMethodData : subMethodsData) {
            addLeaf(owningPlugin, root, subMethodData, executor);
        }

        if (scanChildNodes){
            Set<Class<?>> mountChain = new LinkedHashSet<>();
            mountChain.add(executor.getClass());
            scanChildNodes(owningPlugin, root, executor, mountChain);
        }

        root.sortChildren();
        return root;
    }

    private static void addLeaf(ECPluginData owningPlugin, CommandNode parent, MethodData<SubCMDData> subMethodData, Object instance) {
        SubCMDData subCMDData = subMethodData.getData();
        requireLiteralLabels(subCMDData.getLabels(), "@FinalCMD.SubCMD", instance.getClass().getName() + "#" + subMethodData.getMethod().getName(), parent);

        CommandNode leaf = new CommandNode(parent, subCMDData, instance, parent.getProvidingPlugin());
        String where = instance.getClass().getName() + "#" + subMethodData.getMethod().getName();
        rejectLabelClash(parent, leaf, where);
        parent.addChild(leaf);

        CMDMethodInterpreter interpreter = new CMDMethodInterpreter(owningPlugin, subMethodData, instance, leaf);
        //A leaf has no capture of its own, so its whole ancestry is what it inherits flags from
        rejectFlagSpellingClash(leaf.getCapturingAncestry(), interpreter, where, leaf.getNodePath());
        leaf.setExecutable(interpreter);
    }

    /**
     * Mounts every child node declared under {@code instance}: the inner classes carrying
     * {@code @FinalCMD.Node} (the segment is written where the class is) and the fields carrying it
     * (the segment is written at the mount point, so the same class can be mounted more than once).
     */
    private static void scanChildNodes(ECPluginData owningPlugin, CommandNode parent, Object instance, Set<Class<?>> mountChain) {
        for (Class<?> nestedClass : declaredClassesDeeply(instance.getClass())) {
            FinalCMD.Node node = nestedClass.getAnnotation(FinalCMD.Node.class);
            if (node == null){
                continue;
            }
            Object nodeInstance = instantiate(nestedClass, instance, parent, nestedClass.getName());
            scanNode(owningPlugin, parent, new NodeData(node), nodeInstance, nestedClass.getName(), mountChain);
        }

        for (Field field : declaredFieldsDeeply(instance.getClass())) {
            FinalCMD.Node node = field.getAnnotation(FinalCMD.Node.class);
            if (node == null){
                continue;
            }
            String where = field.getDeclaringClass().getName() + "#" + field.getName();

            if (field.getType().isAnnotationPresent(FinalCMD.Node.class)){
                throw new ArgMountException("The @FinalCMD.Node field [" + where + "] (node path \"" + parent.getNodePath() + "\") mounts [" + field.getType().getName() + "], " +
                        "which declares @FinalCMD.Node on itself. The segment would be declared twice - keep the annotation on the field and remove it from the class, or drop the field.");
            }

            if (!field.isAccessible()){
                field.setAccessible(true);
            }
            Object nodeInstance;
            try {
                nodeInstance = field.get(instance);
            }catch (IllegalAccessException e){
                throw new ArgMountException("The @FinalCMD.Node field [" + where + "] could not be read: " + e.getMessage());
            }
            if (nodeInstance == null){
                nodeInstance = instantiate(field.getType(), instance, parent, where);
            }

            scanNode(owningPlugin, parent, new NodeData(node), nodeInstance, where, mountChain);
        }
    }

    private static void scanNode(ECPluginData owningPlugin, CommandNode parent, NodeData nodeData, Object nodeInstance, String where, Set<Class<?>> mountChain) {
        Class<?> nodeClass = nodeInstance.getClass();

        if (nodeClass.isAnnotationPresent(FinalCMD.class)){
            throw new ArgMountException("The class [" + nodeClass.getName() + "] mounted at [" + where + "] declares @FinalCMD, but it is a node of another command. " +
                    "A node is declared with @FinalCMD.Node - only the root of a tree is a @FinalCMD.");
        }

        if (!mountChain.add(nodeClass)){
            List<String> chain = new ArrayList<>();
            for (Class<?> mounted : mountChain) {
                chain.add(mounted.getSimpleName());
            }
            chain.add(nodeClass.getSimpleName());
            throw new ArgMountException("The @FinalCMD.Node mounted at [" + where + "] closes a mount cycle: " + String.join(" -> ", chain) + ". " +
                    "A node cannot mount an ancestor of itself, the tree would never end - break the cycle by mounting a different class.");
        }

        requireLiteralLabels(nodeData.getLabels(), "@FinalCMD.Node", where, parent);

        ECPluginData nodePlugin = ECPluginManager.getProvidingPlugin(nodeClass);
        CommandNode node = new CommandNode(parent, nodeData, nodeInstance, nodePlugin);
        rejectLabelClash(parent, node, where);
        parent.addChild(node);

        loadClassLocales(nodePlugin, nodeClass);

        List<Method> methods = declaredMethodsDeeply(nodeClass);

        CaptureBinding capture = scanCapture(nodePlugin, node, methods, where);
        if (capture != null){
            //The node's own capture is not part of its ancestry yet - the parent's is what it inherits
            rejectFlagSpellingClash(parent.getCapturingAncestry(), capture.getInterpreter(), where, node.getNodePath());
        }
        node.setCapture(capture);

        CMDMethodInterpreter nodeExecutable = scanExecute(nodePlugin, node, methods, where);
        if (nodeExecutable != null){
            rejectFlagSpellingClash(node.getCapturingAncestry(), nodeExecutable, where, node.getNodePath());
        }
        node.setExecutable(nodeExecutable);

        for (Method method : methods) {
            FinalCMD.SubCMD subCMD = FCReflectionUtil.getAnnotations().getAnnotationDeeply(method, FinalCMD.SubCMD.class);
            if (subCMD != null){
                addLeaf(nodePlugin, node, new MethodData<>(new SubCMDData(subCMD), method), nodeInstance);
            }
        }

        scanChildNodes(nodePlugin, node, nodeInstance, mountChain);

        if (!node.hasChildren()){
            throw new ArgMountException("The @FinalCMD.Node mounted at [" + where + "] (node path \"" + node.getNodePath() + "\") has no child at all. " +
                    "A node exists to hold children - add a @FinalCMD.SubCMD method or a nested @FinalCMD.Node, or turn it into a plain @FinalCMD.SubCMD.");
        }

        node.sortChildren();
        node.setHelpLineTemplate(buildNodeHelpLineTemplate(nodePlugin, node));

        if (node.getDepth() > DEEP_TREE_WARNING_DEPTH){
            owningPlugin.getLog().warning("The command node \"" + node.getNodePath() + "\" is " + node.getDepth() + " levels deep; its help line is already unreadable in chat.");
        }

        mountChain.remove(nodeClass);
    }

    private static @Nullable CaptureBinding scanCapture(ECPluginData nodePlugin, CommandNode node, List<Method> methods, String where) {
        Method captureMethod = null;
        for (Method method : methods) {
            if (method.isAnnotationPresent(FinalCMD.Capture.class)){
                if (captureMethod != null){
                    throw new ArgMountException("The node mounted at [" + where + "] (node path \"" + node.getNodePath() + "\") declares two @FinalCMD.Capture methods " +
                            "([" + captureMethod.getName() + "] and [" + method.getName() + "]). A node has one entry point - keep one and delete the other.");
                }
                captureMethod = method;
            }
        }
        if (captureMethod == null){
            return null;
        }

        MethodData<CaptureData> captureMethodData = new MethodData<>(new CaptureData(node.getCmdData()), captureMethod);
        String methodWhere = node.getNodeInstance().getClass().getName() + "#" + captureMethod.getName();

        if (captureMethodData.getArgDataMap().isEmpty() && captureMethodData.getFlagArgDataMap().isEmpty()){
            throw new ArgMountException("The @FinalCMD.Capture [" + methodWhere + "] (node path \"" + node.getNodePath() + "\") declares no @Arg and no @Arg.Flag, " +
                    "so it consumes nothing and is not a capture. Add the @Arg tokens the node eats, or delete the method.");
        }

        boolean isVoid = captureMethod.getReturnType() == void.class || captureMethod.getReturnType() == Void.class;
        if (isVoid && captureMethodData.getFlagArgDataMap().isEmpty()){
            throw new ArgMountException("The @FinalCMD.Capture [" + methodWhere + "] (node path \"" + node.getNodePath() + "\") returns void and declares no @Arg.Flag, " +
                    "so it hands nothing to its subtree. Return the node's context object, or delete the method.");
        }

        CMDMethodInterpreter interpreter = new CMDMethodInterpreter(nodePlugin, captureMethodData, node.getNodeInstance(), node);

        for (ArgParser parser : interpreter.getCustomArguments().values()) {
            String argName = parser.getArgInfo().getArgData().getName();
            if (!parser.getArgInfo().isRequired()){
                throw new ArgMountException("The @Arg [" + argName + "] of the @FinalCMD.Capture [" + methodWhere + "] (node path \"" + node.getNodePath() + "\") is optional. " +
                        "A node capture always eats its tokens - write it as <" + trimBrackets(argName) + ">, or move the optional argument down to a leaf.");
            }
            if (parser.getArgInfo().isProvidedByContext()){
                throw new ArgMountException("The @Arg [" + argName + "] of the @FinalCMD.Capture [" + methodWhere + "] (node path \"" + node.getNodePath() + "\") resolves from the sender. " +
                        "A node capture always eats its tokens, so it cannot be omitted - write it as <" + trimBrackets(argName) + ">, declare it as a @Arg.Flag of the capture, " +
                        "or keep the sender-resolved argument on a leaf.");
            }
            if (parser.getArgInfo().isGreedy()){
                throw new ArgMountException("The @Arg [" + argName + "] of the @FinalCMD.Capture [" + methodWhere + "] (node path \"" + node.getNodePath() + "\") is variadic. " +
                        "A node capture eats a FIXED number of tokens - that is what lets the traversal find the leaf in one pass - so write it as " +
                        "<" + trimBrackets(argName).replace(CMDMethodInterpreter.GREEDY_SUFFIX, "") + ">, or keep the tail on a leaf.");
            }
        }

        return new CaptureBinding(captureMethod, interpreter, isVoid ? null : captureMethod.getReturnType());
    }

    private static @Nullable CMDMethodInterpreter scanExecute(ECPluginData nodePlugin, CommandNode node, List<Method> methods, String where) {
        Method executeMethod = null;
        for (Method method : methods) {
            if (method.isAnnotationPresent(FinalCMD.Execute.class)){
                if (executeMethod != null){
                    throw new ArgMountException("The node mounted at [" + where + "] (node path \"" + node.getNodePath() + "\") declares two @FinalCMD.Execute methods " +
                            "([" + executeMethod.getName() + "] and [" + method.getName() + "]). The node is the label, so it runs one method - keep one and turn the other into a @FinalCMD.SubCMD.");
                }
                executeMethod = method;
            }
        }
        if (executeMethod == null){
            return null;
        }

        ExecuteData executeData = new ExecuteData(executeMethod.getAnnotation(FinalCMD.Execute.class), node.getCmdData());
        MethodData<ExecuteData> executeMethodData = new MethodData<>(executeData, executeMethod);

        if (!executeMethodData.getArgDataMap().isEmpty()){
            String methodWhere = node.getNodeInstance().getClass().getName() + "#" + executeMethod.getName();
            throw new ArgMountException("The @FinalCMD.Execute [" + methodWhere + "] (node path \"" + node.getNodePath() + "\") declares @Arg parameters. " +
                    "A node executable takes no positional argument - every token after the node has to be a child's label. " +
                    "Use @Arg.Flag, @Arg.NodeCaptured or a contextual parameter, or move the method to a @FinalCMD.SubCMD that can take @Arg.");
        }

        return new CMDMethodInterpreter(nodePlugin, executeMethodData, node.getNodeInstance(), node);
    }

    /**
     * Refuses a flag spelling an ancestor of the path already claims. The whole path shares ONE
     * extraction pass over the tail, so two levels claiming {@code --force} do not make two flags: the
     * deeper one would quietly win, and the ancestor's would read as unknown to nobody.
     *
     * @param capturingAncestry the captures whose flags this member already inherits
     */
    private static void rejectFlagSpellingClash(List<CommandNode> capturingAncestry, CMDMethodInterpreter declaring, String where, String nodePath) {
        if (declaring.getFlagExtractionBindings().isEmpty() || capturingAncestry.isEmpty()){
            return;
        }

        Map<String, CommandNode> inherited = new LinkedHashMap<>();
        for (CommandNode ancestor : capturingAncestry) {
            for (String spelling : ancestor.getCapture().getInterpreter().getFlagExtractionBindings().keySet()) {
                if (!inherited.containsKey(spelling)){
                    inherited.put(spelling, ancestor);
                }
            }
        }

        for (String spelling : declaring.getFlagExtractionBindings().keySet()) {
            CommandNode owner = inherited.get(spelling);
            if (owner != null){
                throw new ArgMountException("The @Arg.Flag spelling [--" + spelling + "] declared at [" + where + "] (node path \"" + nodePath + "\") " +
                        "is already claimed by the @FinalCMD.Capture of the node \"" + owner.getNodePath() + "\", which this path inherits from. " +
                        "The whole path shares one flag namespace - rename one of them, or drop this one and read the ancestor's value through the node's context object.");
            }
        }
    }

    private static void rejectLabelClash(CommandNode parent, CommandNode child, String where) {
        CommandNode clash = parent.findLabelClash(child);
        if (clash != null){
            throw new ArgMountException("The child [" + where + "] of node \"" + (parent.getNodePath().isEmpty() ? parent.getPrimaryLabel() : parent.getNodePath()) + "\" " +
                    "claims the label [" + child.getPrimaryLabel() + "], already taken by [" + clash.getPrimaryLabel() + "]. Two children cannot answer the same word - rename one of them.");
        }
    }

    private static void requireLiteralLabels(String[] labels, String annotation, String where, CommandNode parent) {
        for (String label : labels) {
            if (label.isEmpty()){
                throw new ArgMountException("The " + annotation + " at [" + where + "] declares an empty label. A segment is a literal word the sender types.");
            }
            if (label.indexOf('<') >= 0 || label.indexOf('>') >= 0 || label.indexOf('[') >= 0 || label.indexOf(']') >= 0){
                throw new ArgMountException("The " + annotation + " at [" + where + "] declares the label [" + label + "] with argument brackets. " +
                        "A label is a literal the sender types, not an argument - write it as [" + trimBrackets(label) + "], and declare the value as an @Arg " +
                        (parent == null ? "" : "of the method, or as a @FinalCMD.Capture of a node") + ".");
            }
        }
    }

    private static String trimBrackets(String name) {
        return name.replace("<", "").replace(">", "").replace("[", "").replace("]", "").replace("(", "").replace(")", "");
    }

    private static Object instantiate(Class<?> nodeClass, Object outerInstance, CommandNode parent, String where) {
        try {
            if (nodeClass.isMemberClass() && !java.lang.reflect.Modifier.isStatic(nodeClass.getModifiers())){
                Constructor<?> constructor = nodeClass.getDeclaredConstructor(nodeClass.getEnclosingClass());
                constructor.setAccessible(true);
                return constructor.newInstance(outerInstance);
            }
            Constructor<?> constructor = nodeClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }catch (Exception e){
            throw new ArgMountException("The @FinalCMD.Node at [" + where + "] (under node path \"" + parent.getNodePath() + "\") could not be instantiated: " +
                    "[" + nodeClass.getName() + "] has no usable no-arg constructor. Give it one, or assign the field yourself so the framework does not have to build it.");
        }
    }

    /**
     * A node's own line inside its parent's help. Unlike a leaf's, it never renders arguments: the
     * ancestors' captured tokens are already baked into the node's usage path, and everything else
     * belongs to the children.
     */
    private static HelpLineTemplate buildNodeHelpLineTemplate(ECPluginData nodePlugin, CommandNode node) {
        String localeMessageKey = node.getParent().getLocaleKeyPrefix() + "." + node.getPrimaryLabel().toUpperCase(Locale.ROOT);
        FCLocaleData[] locales = node.getCmdData().getLocales();

        LocaleMessageImp localeMessage;
        if (locales.length > 0){
            localeMessage = FCLocaleScanner.scanForLocale(nodePlugin, localeMessageKey, true, locales);
        }else {
            localeMessage = new LocaleMessageImp(nodePlugin, localeMessageKey, false);
            localeMessage.addLocale(ECPluginManager.getOrCreateECorePluginData(nodePlugin).getPluginLanguage(), new FancySegment(null, null));
        }

        for (Map.Entry<String, FancyText> entry : new ArrayList<>(localeMessage.getFancyTextMap().entrySet())) {
            FancyText fancyText = entry.getValue();

            String textOrHover = fancyText.getHoverText() != null && !fancyText.getHoverText().isEmpty() ? fancyText.getHoverText() : fancyText.getText();
            String description = textOrHover != null && !FCColorUtil.stripColor(textOrHover).trim().isEmpty() ? "§b" + textOrHover : null;

            fancyText.setText("§3§l ▶ §a/§e${label} ${path}");
            fancyText.setHover(description);
            fancyText.setClickSuggest("/${label} ${path}");
        }

        return new HelpLineTemplate(localeMessage, node.getCmdData().getPermission());
    }

    /**
     * Loads the static {@code @FCLocale} fields of a command class AND of its superclasses - the same
     * hierarchy the method scan already walks, so a {@code LocaleMessage} inherited from a base class
     * is not silently left null while its {@code @SubCMD} siblings load fine.
     */
    public static void loadClassLocales(ECPluginData owningPlugin, Class<?> clazz) {
        List<Class<?>> classesWithLocales = new ArrayList<>();
        for (Class<?> father = clazz; father != null && father != Object.class; father = father.getSuperclass()) {
            for (Field declaredField : father.getDeclaredFields()) {
                if (declaredField.isAnnotationPresent(FCLocale.class) || declaredField.isAnnotationPresent(FCMultiLocales.class)){
                    if (!java.lang.reflect.Modifier.isStatic(declaredField.getModifiers())){
                        owningPlugin.getLog().severe("The LocaleMessage [" + declaredField.getName() + "] found at [" + declaredField.getDeclaringClass().getName() + "] is not static! This is an error, it will be ignored!");
                    }else {
                        classesWithLocales.add(father);
                        break;
                    }
                }
            }
        }
        if (!classesWithLocales.isEmpty()){
            FCLocaleManager.loadLocale(owningPlugin, classesWithLocales.toArray(new Class<?>[0]));
        }
    }

    /** Builds the help of every node that has children, root included, after the tree is complete. */
    public static void buildHelpContexts(CommandNode root, String rootHelpHeader) {
        root.setHelpContext(new HelpContextTemplate(rootHelpHeader, root));
        buildChildHelpContexts(root);
    }

    private static void buildChildHelpContexts(CommandNode node) {
        for (CommandNode child : node.getChildren()) {
            if (child.hasChildren()){
                child.setHelpContext(new HelpContextTemplate("", child));
                buildChildHelpContexts(child);
            }
        }
    }

    public static List<Method> declaredMethodsDeeply(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> father = clazz; father != null && father != Object.class; father = father.getSuperclass()) {
            for (Method method : father.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(FinalCMD.Ignore.class)){
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private static List<Field> declaredFieldsDeeply(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> father = clazz; father != null && father != Object.class; father = father.getSuperclass()) {
            fields.addAll(Arrays.asList(father.getDeclaredFields()));
        }
        return fields;
    }

    private static List<Class<?>> declaredClassesDeeply(Class<?> clazz) {
        List<Class<?>> classes = new ArrayList<>();
        for (Class<?> father = clazz; father != null && father != Object.class; father = father.getSuperclass()) {
            classes.addAll(Arrays.asList(father.getDeclaredClasses()));
        }
        return classes;
    }
}
