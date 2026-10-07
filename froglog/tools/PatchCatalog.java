import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.builder.MethodImplementationBuilder;
import org.jf.dexlib2.builder.Label;
import org.jf.dexlib2.builder.instruction.BuilderInstruction10x;
import org.jf.dexlib2.builder.instruction.BuilderInstruction11n;
import org.jf.dexlib2.builder.instruction.BuilderInstruction11x;
import org.jf.dexlib2.builder.instruction.BuilderInstruction21c;
import org.jf.dexlib2.builder.instruction.BuilderInstruction21t;
import org.jf.dexlib2.builder.instruction.BuilderInstruction22c;
import org.jf.dexlib2.builder.instruction.BuilderInstruction22t;
import org.jf.dexlib2.builder.instruction.BuilderInstruction35c;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.ExceptionHandler;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.TryBlock;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.SwitchElement;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction10t;
import org.jf.dexlib2.iface.instruction.formats.Instruction20t;
import org.jf.dexlib2.iface.instruction.formats.Instruction21t;
import org.jf.dexlib2.iface.instruction.formats.Instruction22t;
import org.jf.dexlib2.iface.instruction.formats.Instruction30t;
import org.jf.dexlib2.iface.instruction.formats.Instruction31t;
import org.jf.dexlib2.iface.instruction.formats.PackedSwitchPayload;
import org.jf.dexlib2.iface.instruction.formats.SparseSwitchPayload;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableExceptionHandler;
import org.jf.dexlib2.immutable.ImmutableField;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.ImmutableTryBlock;
import org.jf.dexlib2.immutable.ImmutableMethodImplementation;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction20t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction30t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction31t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c;
import org.jf.dexlib2.immutable.instruction.ImmutablePackedSwitchPayload;
import org.jf.dexlib2.immutable.instruction.ImmutableSparseSwitchPayload;
import org.jf.dexlib2.immutable.instruction.ImmutableSwitchElement;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One write of classes4.dex:
 * append the Froglog catalog entries after the original list is built,
 * place those entries directly when chosen,
 * notify Froglog when one finished play session is inserted,
 * append the Froglog pod to the overlay list,
 * open that pod before Cocoon's router handles it, and
 * add a Froglog friends tab beside Steam and Android.
 * Recently played is not rewritten. insertAll is not rewritten.
 */
public final class PatchCatalog {
    private static final String CATALOG = "Lmf/y1;";
    private static final String SESSION = "Lrip/moth/cocoonshell/data/local/GameSessionDao_Impl;";
    private static final String PODS = "Lxd/m0;";
    private static final String POD_ACTION = "Lxd/k0;";
    private static final String ROUTER = "Lrip/moth/cocoonshell/utils/u6;";
    private static final String OPEN_BY_ACTION =
            "(Lxd/k0;Landroid/content/Context;Lza/i;I)Ljava/lang/Object;";
    private static final String FRIENDS = "Lef/d0;";
    private static final String FRIEND_TABS = "Lef/w0;";
    private static final String FRIEND_MAPS = "Lef/s5;";
    private static final String FRIEND_CLICK = "Lef/q3;";
    private static final String THEME = "Lrip/moth/cocoonshell/ui/theme/ThemeSettings;";
    private static final String SURFACE_PREFS = "Lfe/q1;";
    private static final String GLASS_DRAW = "Lkf/n2;";
    private static final String GLASS_HOST = "Ldg/m3;";
    private static final String WIDGET_HOST = "Ltf/i1;";
    private static final String STATUS_BAR = "Ldg/h4;";
    private static final String ICONS = "Lef/b;";
    private static final int FROGLOG_ICON = 0x7F06021A;
    private static final String INSERT =
            "(Lrip/moth/cocoonshell/data/model/GameSession;Lxa/c;)Ljava/lang/Object;";
    private static final String OPEN_POD =
            "(Lxd/l0;Landroid/content/Context;Ljava/lang/Boolean;Lrip/moth/cocoonshell/data/model/Game;Lza/c;)Ljava/lang/Object;";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: PatchCatalog <in.dex> <out.dex>");
        }
        // Keep dex version 037. forApi(35) rewrites the header as dex 041, which baksmali rejects.
        DexBackedDexFile dex = DexFileFactory.loadDexFile(new File(args[0]), Opcodes.forDexVersion(37));
        ClassDef catalog = null;
        ClassDef session = null;
        ClassDef pods = null;
        ClassDef podAction = null;
        ClassDef router = null;
        ClassDef friends = null;
        ClassDef friendTabs = null;
        ClassDef friendMaps = null;
        ClassDef friendClick = null;
        ClassDef theme = null;
        ClassDef surfacePrefs = null;
        ClassDef glassDraw = null;
        ClassDef glassHost = null;
        ClassDef widgetHost = null;
        ClassDef statusBar = null;
        ClassDef icons = null;
        for (ClassDef cls : dex.getClasses()) {
            if (CATALOG.equals(cls.getType())) {
                catalog = cls;
            } else if (SESSION.equals(cls.getType())) {
                session = cls;
            } else if (PODS.equals(cls.getType())) {
                pods = cls;
            } else if (POD_ACTION.equals(cls.getType())) {
                podAction = cls;
            } else if (ROUTER.equals(cls.getType())) {
                router = cls;
            } else if (FRIENDS.equals(cls.getType())) {
                friends = cls;
            } else if (FRIEND_TABS.equals(cls.getType())) {
                friendTabs = cls;
            } else if (FRIEND_MAPS.equals(cls.getType())) {
                friendMaps = cls;
            } else if (FRIEND_CLICK.equals(cls.getType())) {
                friendClick = cls;
            } else if (THEME.equals(cls.getType())) {
                theme = cls;
            } else if (SURFACE_PREFS.equals(cls.getType())) {
                surfacePrefs = cls;
            } else if (GLASS_DRAW.equals(cls.getType())) {
                glassDraw = cls;
            } else if (GLASS_HOST.equals(cls.getType())) {
                glassHost = cls;
            } else if (WIDGET_HOST.equals(cls.getType())) {
                widgetHost = cls;
            } else if (STATUS_BAR.equals(cls.getType())) {
                statusBar = cls;
            } else if (ICONS.equals(cls.getType())) {
                icons = cls;
            }
        }
        if (catalog == null || session == null || pods == null || podAction == null || router == null
                || friends == null || friendTabs == null || friendMaps == null || friendClick == null
                || theme == null || surfacePrefs == null
                || glassDraw == null || glassHost == null || widgetHost == null || statusBar == null || icons == null) {
            throw new IllegalStateException("catalog=" + (catalog != null) + " session=" + (session != null)
                    + " pods=" + (pods != null) + " podAction=" + (podAction != null)
                    + " router=" + (router != null)
                    + " friends=" + (friends != null) + " tabs=" + (friendTabs != null)
                    + " maps=" + (friendMaps != null) + " click=" + (friendClick != null)
                    + " theme=" + (theme != null) + " surfacePrefs=" + (surfacePrefs != null)
                    + " glassDraw=" + (glassDraw != null) + " glassHost=" + (glassHost != null)
                    + " widgetHost=" + (widgetHost != null) + " statusBar=" + (statusBar != null)
                    + " icons=" + (icons != null));
        }
        final ClassDef catalogReplacement = patchCatalog(catalog);
        final ClassDef sessionReplacement = patchSession(session);
        final ClassDef podsReplacement = patchPods(pods);
        final ClassDef podActionReplacement = patchPodAction(podAction);
        final ClassDef routerReplacement = patchRouterClass(router);
        final ClassDef friendTabsReplacement = patchFriendTabEnum(friendTabs);
        final ClassDef friendMapsReplacement = patchFriendTabMaps(friendMaps);
        final ClassDef friendsReplacement = patchFriends(friends);
        final ClassDef clickReplacement = patchFriendClick(friendClick);
        final ClassDef themeReplacement = patchTheme(theme);
        final ClassDef surfacePrefsReplacement = patchSurfacePrefs(surfacePrefs);
        final ClassDef glassDrawReplacement = prefixGlassMethods(glassDraw, "b");
        final ClassDef glassHostReplacement = prefixGlassMethods(glassHost, "h", "A0");
        final ClassDef widgetHostReplacement = patchWidgetHost(widgetHost);
        final ClassDef statusBarReplacement = patchStatusBar(statusBar);
        final ClassDef iconsReplacement = patchIcons(icons);
        final DexBackedDexFile source = dex;
        DexFileFactory.writeDexFile(args[1], new DexFile() {
            @Override
            public Set<? extends ClassDef> getClasses() {
                LinkedHashSet<ClassDef> classes = new LinkedHashSet<ClassDef>();
                for (ClassDef cls : source.getClasses()) {
                    if (CATALOG.equals(cls.getType())) {
                        classes.add(catalogReplacement);
                    } else if (SESSION.equals(cls.getType())) {
                        classes.add(sessionReplacement);
                    } else if (PODS.equals(cls.getType())) {
                        classes.add(podsReplacement);
                    } else if (POD_ACTION.equals(cls.getType())) {
                        classes.add(podActionReplacement);
                    } else if (ROUTER.equals(cls.getType())) {
                        classes.add(routerReplacement);
                    } else if (FRIEND_TABS.equals(cls.getType())) {
                        classes.add(friendTabsReplacement);
                    } else if (FRIEND_MAPS.equals(cls.getType())) {
                        classes.add(friendMapsReplacement);
                    } else if (FRIENDS.equals(cls.getType())) {
                        classes.add(friendsReplacement);
                    } else if (FRIEND_CLICK.equals(cls.getType())) {
                        classes.add(clickReplacement);
                    } else if (THEME.equals(cls.getType())) {
                        classes.add(themeReplacement);
                    } else if (SURFACE_PREFS.equals(cls.getType())) {
                        classes.add(surfacePrefsReplacement);
                    } else if (GLASS_DRAW.equals(cls.getType())) {
                        classes.add(glassDrawReplacement);
                    } else if (GLASS_HOST.equals(cls.getType())) {
                        classes.add(glassHostReplacement);
                    } else if (WIDGET_HOST.equals(cls.getType())) {
                        classes.add(widgetHostReplacement);
                    } else if (STATUS_BAR.equals(cls.getType())) {
                        classes.add(statusBarReplacement);
                    } else if (ICONS.equals(cls.getType())) {
                        classes.add(iconsReplacement);
                    } else {
                        classes.add(cls);
                    }
                }
                return classes;
            }

            @Override
            public Opcodes getOpcodes() {
                return source.getOpcodes();
            }
        });
        System.out.println("patched " + args[1]);
    }

    private static ClassDef patchCatalog(ClassDef catalog) {
        List<Method> direct = new ArrayList<Method>();
        boolean patchedClinit = false;
        boolean patchedOpen = false;
        for (Method method : catalog.getDirectMethods()) {
            if ("<clinit>".equals(method.getName())) {
                direct.add(patchClinit(method));
                patchedClinit = true;
            } else if ("h".equals(method.getName())
                    && "(Lz0/a1;Lz0/a1;Lz0/u0;Ljb/g;I)V".equals(signature(method))) {
                direct.add(patchOpen(method));
                patchedOpen = true;
            } else {
                direct.add(method);
            }
        }
        if (!patchedClinit || !patchedOpen) {
            throw new IllegalStateException("clinit=" + patchedClinit + " open=" + patchedOpen);
        }
        List<Method> virtual = new ArrayList<Method>();
        for (Method method : catalog.getVirtualMethods()) {
            virtual.add(method);
        }
        return copyClass(catalog, direct, virtual);
    }

    private static ClassDef patchSession(ClassDef session) {
        List<Method> direct = new ArrayList<Method>();
        for (Method method : session.getDirectMethods()) {
            direct.add(method);
        }
        List<Method> virtual = new ArrayList<Method>();
        boolean patched = false;
        for (Method method : session.getVirtualMethods()) {
            if ("insert".equals(method.getName()) && INSERT.equals(signature(method))) {
                virtual.add(patchInsert(method));
                patched = true;
            } else {
                virtual.add(method);
            }
        }
        if (!patched) {
            throw new IllegalStateException("session insert not found");
        }
        return copyClass(session, direct, virtual);
    }

    private static ClassDef patchPods(ClassDef pods) {
        List<Method> direct = new ArrayList<Method>();
        boolean patched = false;
        for (Method method : pods.getDirectMethods()) {
            if ("<clinit>".equals(method.getName())) {
                direct.add(patchPodClinit(method));
                patched = true;
            } else {
                direct.add(method);
            }
        }
        if (!patched) {
            throw new IllegalStateException("pod list initializer not found");
        }
        List<Method> virtual = new ArrayList<Method>();
        for (Method method : pods.getVirtualMethods()) {
            virtual.add(method);
        }
        return copyClass(pods, direct, virtual);
    }

    private static ClassDef patchRouterClass(ClassDef router) {
        List<Method> direct = new ArrayList<Method>();
        boolean byAction = false;
        for (Method method : router.getDirectMethods()) {
            if ("b".equals(method.getName()) && OPEN_BY_ACTION.equals(signature(method))) {
                direct.add(patchRouterByAction(method));
                byAction = true;
            } else {
                direct.add(method);
            }
        }
        List<Method> virtual = new ArrayList<Method>();
        boolean patched = false;
        for (Method method : router.getVirtualMethods()) {
            if ("a".equals(method.getName()) && OPEN_POD.equals(signature(method))) {
                virtual.add(patchRouter(method));
                patched = true;
            } else if ("b".equals(method.getName()) && OPEN_BY_ACTION.equals(signature(method))) {
                virtual.add(patchRouterByAction(method));
                byAction = true;
            } else {
                virtual.add(method);
            }
        }
        if (!patched || !byAction) {
            throw new IllegalStateException("pod router a=" + patched + " b=" + byAction);
        }
        return copyClass(router, direct, virtual);
    }

    private static ClassDef patchPodAction(ClassDef k0) {
        List<Field> statics = new ArrayList<Field>();
        boolean hasFroglog = false;
        for (Field field : k0.getStaticFields()) {
            if ("FROGLOG".equals(field.getName())) {
                hasFroglog = true;
            }
            statics.add(field);
        }
        if (!hasFroglog) {
            statics.add(new ImmutableField(
                    POD_ACTION,
                    "FROGLOG",
                    POD_ACTION,
                    0x4019,
                    null,
                    Collections.emptySet(),
                    Collections.emptySet()));
        }
        List<Field> instance = new ArrayList<Field>();
        for (Field field : k0.getInstanceFields()) {
            instance.add(field);
        }
        List<Method> direct = new ArrayList<Method>();
        boolean patched = false;
        for (Method method : k0.getDirectMethods()) {
            if ("<clinit>".equals(method.getName())) {
                direct.add(patchK0Clinit(method));
                patched = true;
            } else {
                direct.add(method);
            }
        }
        if (!patched) {
            throw new IllegalStateException("pod action initializer not found");
        }
        List<Method> virtual = new ArrayList<Method>();
        for (Method method : k0.getVirtualMethods()) {
            virtual.add(method);
        }
        return new ImmutableClassDef(
                k0.getType(),
                k0.getAccessFlags(),
                k0.getSuperclass(),
                k0.getInterfaces(),
                k0.getSourceFile(),
                k0.getAnnotations(),
                statics,
                instance,
                direct,
                virtual);
    }

    private static ClassDef copyClass(ClassDef cls, List<Method> direct, List<Method> virtual) {
        return new ImmutableClassDef(
                cls.getType(),
                cls.getAccessFlags(),
                cls.getSuperclass(),
                cls.getInterfaces(),
                cls.getSourceFile(),
                cls.getAnnotations(),
                cls.getStaticFields(),
                cls.getInstanceFields(),
                direct,
                virtual);
    }

    private static String signature(Method method) {
        StringBuilder out = new StringBuilder("(");
        for (CharSequence type : method.getParameterTypes()) {
            out.append(type);
        }
        return out.append(")").append(method.getReturnType()).toString();
    }

    private static Method patchClinit(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> instructions = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        int sput = -1;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if (instruction.getOpcode() != Opcode.SPUT_OBJECT || !(instruction instanceof ReferenceInstruction)) {
                continue;
            }
            Reference ref = ((ReferenceInstruction) instruction).getReference();
            if (ref instanceof FieldReference
                    && CATALOG.equals(((FieldReference) ref).getDefiningClass())
                    && "f".equals(((FieldReference) ref).getName())) {
                sput = i;
            }
        }
        if (sput < 0) {
            throw new IllegalStateException("catalog field store not found");
        }
        instructions.add(sput, new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                1, 0, 0, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/CatalogHook;",
                        "withFroglog",
                        Collections.singletonList("Ljava/util/List;"),
                        "Ljava/util/List;")));
        instructions.add(sput + 1, new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
        System.out.println("clinit instructions " + instructions.size());
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                instructions,
                Collections.emptyList(),
                Collections.emptyList()));
    }

    private static Method patchInsert(Method method) {
        MethodImplementation impl = method.getImplementation();
        int session = impl.getRegisterCount() - parameterWords(method) + 1;
        if (session != 4) {
            throw new IllegalStateException("session register " + session);
        }
        List<Instruction> instructions = new ArrayList<Instruction>();
        instructions.add(new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                1, session, 0, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/FroglogSessionBridge;",
                        "onInserted",
                        Collections.singletonList("Lrip/moth/cocoonshell/data/model/GameSession;"),
                        "V")));
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        System.out.println("insert instructions " + instructions.size());
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                instructions,
                Collections.emptyList(),
                Collections.emptyList()));
    }

    private static Method patchPodClinit(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> instructions = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        int sput = -1;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if (instruction.getOpcode() != Opcode.SPUT_OBJECT || !(instruction instanceof ReferenceInstruction)) {
                continue;
            }
            Reference ref = ((ReferenceInstruction) instruction).getReference();
            if (ref instanceof FieldReference
                    && PODS.equals(((FieldReference) ref).getDefiningClass())
                    && "a".equals(((FieldReference) ref).getName())) {
                sput = i;
            }
        }
        if (sput < 0) {
            throw new IllegalStateException("pod list field store not found");
        }
        instructions.add(sput, new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                1, 0, 0, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/FroglogPods;",
                        "include",
                        Collections.singletonList("Ljava/util/List;"),
                        "Ljava/util/List;")));
        instructions.add(sput + 1, new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
        System.out.println("pod clinit instructions " + instructions.size());
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                instructions,
                Collections.emptyList(),
                Collections.emptyList()));
    }

    private static Method patchK0Clinit(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> original = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            original.add(instruction);
        }
        int filled = -1;
        int start = -1;
        int count = -1;
        for (int i = 0; i < original.size(); i++) {
            Instruction instruction = original.get(i);
            if (instruction.getOpcode() != Opcode.FILLED_NEW_ARRAY_RANGE
                    || !(instruction instanceof ReferenceInstruction)
                    || !(instruction instanceof org.jf.dexlib2.iface.instruction.RegisterRangeInstruction)) {
                continue;
            }
            Reference ref = ((ReferenceInstruction) instruction).getReference();
            if (!(ref instanceof TypeReference) || !"[Lxd/k0;".equals(((TypeReference) ref).getType())) {
                continue;
            }
            org.jf.dexlib2.iface.instruction.RegisterRangeInstruction range =
                    (org.jf.dexlib2.iface.instruction.RegisterRangeInstruction) instruction;
            filled = i;
            start = range.getStartRegister();
            count = range.getRegisterCount();
        }
        if (filled < 0 || start != 0 || count != 6) {
            throw new IllegalStateException("pod enum array start=" + start + " count=" + count);
        }
        List<Instruction> extra = new ArrayList<Instruction>();
        extra.add(new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 6, new ImmutableTypeReference(POD_ACTION)));
        extra.add(new ImmutableInstruction21c(Opcode.CONST_STRING, 7, new ImmutableStringReference("FROGLOG")));
        extra.add(new org.jf.dexlib2.immutable.instruction.ImmutableInstruction21s(Opcode.CONST_16, 8, 6));
        extra.add(new ImmutableInstruction35c(
                Opcode.INVOKE_DIRECT,
                3, 6, 7, 8, 0, 0,
                new ImmutableMethodReference(
                        "Ljava/lang/Enum;",
                        "<init>",
                        Arrays.asList("Ljava/lang/String;", "I"),
                        "V")));
        extra.add(new ImmutableInstruction21c(
                Opcode.SPUT_OBJECT,
                6,
                new ImmutableFieldReference(POD_ACTION, "FROGLOG", POD_ACTION)));
        List<Instruction> rewritten = new ArrayList<Instruction>();
        rewritten.addAll(original.subList(0, filled));
        rewritten.addAll(extra);
        rewritten.add(new ImmutableInstruction3rc(
                Opcode.FILLED_NEW_ARRAY_RANGE,
                start,
                7,
                new ImmutableTypeReference("[Lxd/k0;")));
        rewritten.addAll(original.subList(filled + 1, original.size()));
        int regs = Math.max(impl.getRegisterCount(), 9);
        System.out.println("pod action values " + (count + 1));
        return replace(method, new ImmutableMethodImplementation(
                regs,
                rewritten,
                Collections.emptyList(),
                Collections.emptyList()));
    }

    private static Method patchRouterByAction(Method method) {
        MethodImplementation impl = method.getImplementation();
        int first = impl.getRegisterCount() - parameterWords(method);
        int action = (method.getAccessFlags() & 0x8) != 0 ? first : first + 1;
        int context = action + 1;
        if (action != 8) {
            throw new IllegalStateException("router action register " + action);
        }
        List<Instruction> instructions = new ArrayList<Instruction>();
        instructions.add(new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                2, action, context, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/FroglogPods;",
                        "openIfAction",
                        Arrays.asList("Lxd/k0;", "Landroid/content/Context;"),
                        "Z")));
        instructions.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT, 0));
        instructions.add(new ImmutableInstruction21t(Opcode.IF_EQZ, 0, 5));
        instructions.add(new ImmutableInstruction21c(Opcode.SGET_OBJECT, 0,
                new ImmutableFieldReference("Lta/z;", "a", "Lta/z;")));
        instructions.add(new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
        int prefix = 0;
        for (int i = 0; i < instructions.size(); i++) {
            prefix += instructions.get(i).getCodeUnits();
        }
        if (prefix != 9) {
            throw new IllegalStateException("action router prefix " + prefix);
        }
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        System.out.println("action router instructions " + instructions.size() + " actionReg=" + action);
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                instructions,
                Collections.emptyList(),
                Collections.emptyList()));
    }

    private static Method patchRouter(Method method) {
        MethodImplementation impl = method.getImplementation();
        int entry = impl.getRegisterCount() - parameterWords(method) + 1;
        if (entry != 13) {
            throw new IllegalStateException("router entry register " + entry);
        }
        // Prefix is 9 code units. if-eqz sits at address 4 and jumps to the original method at 9.
        List<Instruction> instructions = new ArrayList<Instruction>();
        instructions.add(new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                2, entry, entry + 1, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/FroglogPods;",
                        "openIfFroglog",
                        Arrays.asList("Lxd/l0;", "Landroid/content/Context;"),
                        "Z")));
        instructions.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT, 0));
        instructions.add(new ImmutableInstruction21t(Opcode.IF_EQZ, 0, 5));
        instructions.add(new ImmutableInstruction21c(Opcode.SGET_OBJECT, 0,
                new ImmutableFieldReference("Lta/z;", "a", "Lta/z;")));
        instructions.add(new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
        int prefix = 0;
        for (int i = 0; i < instructions.size(); i++) {
            prefix += instructions.get(i).getCodeUnits();
        }
        if (prefix != 9) {
            throw new IllegalStateException("router prefix " + prefix);
        }
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        System.out.println("router instructions " + instructions.size());
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                instructions,
                Collections.emptyList(),
                Collections.emptyList()));
    }

    private static ClassDef patchFriends(ClassDef friends) {
        List<Method> direct = new ArrayList<Method>();
        boolean patchedTabs = false;
        boolean patchedPanel = false;
        boolean patchedIcon = false;
        for (Method method : friends.getDirectMethods()) {
            if ("c0".equals(method.getName())
                    && "(Lp1/o;FFLz0/e0;I)V".equals(signature(method))) {
                direct.add(patchFriendCount(patchFriendGate(patchFriendTabs(method)), "size", 3));
                patchedTabs = true;
            } else if ("b0".equals(method.getName())
                    && signature(method).startsWith("(Ljava/util/List;Leg/l0;Lfe/u;Ljava/util/List;Lef/w0;")) {
                direct.add(patchFriendPanel(method));
                patchedPanel = true;
            } else if ("s".equals(method.getName())
                    && "(Ljava/util/List;Lef/w0;Leg/l0;Ljb/c;Lz0/e0;I)V".equals(signature(method))) {
                direct.add(patchFriendTabIcon(method));
                patchedIcon = true;
            } else {
                direct.add(method);
            }
        }
        if (!patchedTabs) {
            throw new IllegalStateException("friend tabs builder not found");
        }
        if (!patchedPanel) {
            throw new IllegalStateException("friend tab panel not found");
        }
        if (!patchedIcon) {
            throw new IllegalStateException("friend tab chips not found");
        }
        List<Method> virtual = new ArrayList<Method>();
        for (Method method : friends.getVirtualMethods()) {
            virtual.add(method);
        }
        return copyClass(friends, direct, virtual);
    }

    /**
     * The status-bar friends pill shows {@code ef.d0.k0(steam).size()}. Right after that count
     * is read, FroglogSocial adds live Froglog follows. It reads a StateFlow through Cocoon's
     * collectAsState, so the pill recomposes when Froglog presence changes.
     */
    private static ClassDef patchStatusBar(ClassDef bar) {
        List<Method> direct = new ArrayList<Method>();
        boolean patched = false;
        for (Method method : bar.getDirectMethods()) {
            if ("k".equals(method.getName()) && "(ILp1/o;Lz0/e0;Z)V".equals(signature(method))) {
                direct.add(patchFriendCount(method, "intValue", 2));
                patched = true;
            } else {
                direct.add(method);
            }
        }
        if (!patched) {
            throw new IllegalStateException("status bar friend count not found");
        }
        List<Method> virtual = new ArrayList<Method>();
        for (Method method : bar.getVirtualMethods()) {
            virtual.add(method);
        }
        return copyClass(bar, direct, virtual);
    }

    /**
     * {@code readName} is the call that yields the count outside any remember block
     * ({@code intValue} or {@code size}). {@code composerParam} is the Composer's parameter
     * index. Every earlier parameter is one word.
     */
    private static Method patchFriendCount(Method method, String readName, int composerParam) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> instructions = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        int k0 = -1;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if (instruction.getOpcode() == Opcode.INVOKE_STATIC && instruction instanceof ReferenceInstruction) {
                Reference ref = ((ReferenceInstruction) instruction).getReference();
                if (ref instanceof MethodReference && FRIENDS.equals(((MethodReference) ref).getDefiningClass())
                        && "k0".equals(((MethodReference) ref).getName())) {
                    k0 = i;
                    break;
                }
            }
        }
        if (k0 < 0) {
            throw new IllegalStateException("status bar k0 call not found");
        }
        int insert = -1;
        for (int i = k0; i < instructions.size() - 1; i++) {
            Instruction instruction = instructions.get(i);
            if ((instruction.getOpcode() != Opcode.INVOKE_VIRTUAL && instruction.getOpcode() != Opcode.INVOKE_INTERFACE)
                    || !(instruction instanceof ReferenceInstruction)) {
                continue;
            }
            Reference ref = ((ReferenceInstruction) instruction).getReference();
            if (!(ref instanceof MethodReference) || instructions.get(i + 1).getOpcode() != Opcode.MOVE_RESULT) {
                continue;
            }
            MethodReference read = (MethodReference) ref;
            if (readName.equals(read.getName())
                    && ("Ljava/lang/Number;".equals(read.getDefiningClass()) || "Ljava/util/List;".equals(read.getDefiningClass()))) {
                insert = i + 2;
                break;
            }
        }
        if (insert < 0) {
            throw new IllegalStateException("friend count read not found");
        }
        int count = ((OneRegisterInstruction) instructions.get(insert - 1)).getRegisterA();
        int composer = impl.getRegisterCount() - parameterWords(method) + composerParam;
        if (composer > 15) {
            // Large Compose methods copy the Composer into a low register first.
            for (int i = 0; i < Math.min(8, instructions.size()); i++) {
                Instruction instruction = instructions.get(i);
                if (instruction.getOpcode() == Opcode.MOVE_OBJECT_FROM16
                        && ((TwoRegisterInstruction) instruction).getRegisterB() == composer) {
                    composer = ((TwoRegisterInstruction) instruction).getRegisterA();
                    break;
                }
            }
        }
        if (count > 15 || composer > 15) {
            throw new IllegalStateException("status bar registers count=" + count + " composer=" + composer);
        }
        List<Instruction> extra = new ArrayList<Instruction>();
        extra.add(new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                2, count, composer, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/FroglogSocial;",
                        "withLiveCount",
                        Arrays.asList("I", "Ljava/lang/Object;"),
                        "I")));
        extra.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT, count));
        int added = 0;
        for (Instruction instruction : extra) {
            added += instruction.getCodeUnits();
        }
        if (added != 4) {
            throw new IllegalStateException("status bar insert " + added);
        }
        int[] addresses = addresses(instructions);
        int insertAt = addresses[insert];
        int[] switchAt = switchAddresses(instructions, addresses);
        List<Instruction> rewritten = new ArrayList<Instruction>();
        for (int i = 0; i < instructions.size(); i++) {
            if (i == insert) {
                rewritten.addAll(extra);
            }
            rewritten.add(retarget(instructions.get(i), addresses[i], switchAt[i], insertAt, added));
        }
        System.out.println("friend count " + method.getDefiningClass() + "->" + method.getName()
                + " v" + count + " composer v" + composer);
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                rewritten,
                shiftTries(impl.getTryBlocks(), insertAt, added),
                Collections.emptyList()));
    }

    private static ClassDef patchFriendClick(ClassDef click) {
        List<Method> direct = new ArrayList<Method>();
        for (Method method : click.getDirectMethods()) {
            direct.add(method);
        }
        List<Method> virtual = new ArrayList<Method>();
        boolean patched = false;
        for (Method method : click.getVirtualMethods()) {
            if ("invoke".equals(method.getName()) && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(signature(method))) {
                virtual.add(patchFriendOpen(method));
                patched = true;
            } else {
                virtual.add(method);
            }
        }
        if (!patched) {
            throw new IllegalStateException("friend click not found");
        }
        return copyClass(click, direct, virtual);
    }

    /** Each chip picks Steam or Android art. Let Froglog swap the art for its own tab. */
    private static Method patchFriendTabIcon(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> instructions = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        int steam = -1;
        for (int i = 0; i < instructions.size() - 1; i++) {
            Instruction instruction = instructions.get(i);
            if (instruction.getOpcode() != Opcode.SGET_OBJECT || !(instruction instanceof ReferenceInstruction)) {
                continue;
            }
            Reference ref = ((ReferenceInstruction) instruction).getReference();
            if (ref instanceof FieldReference && ICONS.equals(((FieldReference) ref).getDefiningClass())
                    && "STEAM".equals(((FieldReference) ref).getName())
                    && instructions.get(i + 1).getOpcode() == Opcode.CONST_16) {
                steam = i;
                break;
            }
        }
        if (steam < 0) {
            throw new IllegalStateException("friend chip icon not found");
        }
        int icon = ((OneRegisterInstruction) instructions.get(steam)).getRegisterA();
        int tab = -1;
        for (int i = steam; i >= 0; i--) {
            Instruction instruction = instructions.get(i);
            if (instruction.getOpcode() == Opcode.INVOKE_VIRTUAL && instruction instanceof ReferenceInstruction) {
                Reference ref = ((ReferenceInstruction) instruction).getReference();
                if (ref instanceof MethodReference && "ordinal".equals(((MethodReference) ref).getName())) {
                    tab = ((org.jf.dexlib2.iface.instruction.FiveRegisterInstruction) instruction).getRegisterC();
                    break;
                }
            }
        }
        if (tab < 0 || tab > 15 || icon > 15) {
            throw new IllegalStateException("friend chip registers tab=v" + tab + " icon=v" + icon);
        }
        int insert = steam + 1;
        List<Instruction> extra = new ArrayList<Instruction>();
        extra.add(new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                2, tab, icon, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/FroglogSocial;",
                        "tabIcon",
                        Arrays.asList("Ljava/lang/Object;", "Ljava/lang/Object;"),
                        "Ljava/lang/Object;")));
        extra.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, icon));
        extra.add(new ImmutableInstruction21c(Opcode.CHECK_CAST, icon, new ImmutableTypeReference(ICONS)));
        int added = 0;
        for (Instruction instruction : extra) {
            added += instruction.getCodeUnits();
        }
        if (added != 6) {
            throw new IllegalStateException("friend chip icon insert " + added);
        }
        int[] addresses = addresses(instructions);
        int insertAt = addresses[insert];
        int[] switchAt = switchAddresses(instructions, addresses);
        List<Instruction> rewritten = new ArrayList<Instruction>();
        for (int i = 0; i < instructions.size(); i++) {
            if (i == insert) {
                rewritten.addAll(extra);
            }
            rewritten.add(retarget(instructions.get(i), addresses[i], switchAt[i], insertAt, added));
        }
        System.out.println("friend chip icon v" + tab + " -> v" + icon);
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                rewritten,
                shiftTries(impl.getTryBlocks(), insertAt, added),
                Collections.emptyList()));
    }

    /** Appends FROGLOG to Cocoon's icon enum, drawn from froglog_friends_icon. */
    private static ClassDef patchIcons(ClassDef icons) {
        List<Field> statics = new ArrayList<Field>();
        for (Field field : icons.getStaticFields()) {
            if ("FROGLOG".equals(field.getName())) {
                throw new IllegalStateException("icon enum already has FROGLOG");
            }
            statics.add(field);
        }
        statics.add(new ImmutableField(ICONS, "FROGLOG", ICONS, 0x4019, null,
                Collections.emptySet(), Collections.emptySet()));
        List<Field> instance = new ArrayList<Field>();
        for (Field field : icons.getInstanceFields()) {
            instance.add(field);
        }
        List<Method> direct = new ArrayList<Method>();
        boolean clinit = false;
        boolean values = false;
        for (Method method : icons.getDirectMethods()) {
            if ("<clinit>".equals(method.getName())) {
                direct.add(patchIconClinit(method));
                clinit = true;
            } else if ("a".equals(method.getName()) && "()[Lef/b;".equals(signature(method))) {
                direct.add(patchIconValues(method));
                values = true;
            } else {
                direct.add(method);
            }
        }
        if (!clinit || !values) {
            throw new IllegalStateException("icon enum clinit=" + clinit + " values=" + values);
        }
        List<Method> virtual = new ArrayList<Method>();
        for (Method method : icons.getVirtualMethods()) {
            virtual.add(method);
        }
        return new ImmutableClassDef(icons.getType(), icons.getAccessFlags(), icons.getSuperclass(),
                icons.getInterfaces(), icons.getSourceFile(), icons.getAnnotations(),
                statics, instance, direct, virtual);
    }

    private static Method patchIconClinit(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> original = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            original.add(instruction);
        }
        int values = -1;
        for (int i = 0; i < original.size(); i++) {
            Instruction instruction = original.get(i);
            if (instruction.getOpcode() == Opcode.INVOKE_STATIC && instruction instanceof ReferenceInstruction) {
                Reference ref = ((ReferenceInstruction) instruction).getReference();
                if (ref instanceof MethodReference && ICONS.equals(((MethodReference) ref).getDefiningClass())
                        && "a".equals(((MethodReference) ref).getName())) {
                    values = i;
                    break;
                }
            }
        }
        if (values < 0) {
            throw new IllegalStateException("icon values call not found");
        }
        List<Instruction> extra = new ArrayList<Instruction>();
        extra.add(new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 0, new ImmutableTypeReference(ICONS)));
        extra.add(new ImmutableInstruction21c(Opcode.CONST_STRING, 1, new ImmutableStringReference("FROGLOG")));
        extra.add(new org.jf.dexlib2.immutable.instruction.ImmutableInstruction21s(Opcode.CONST_16, 2, 0xd3));
        extra.add(new org.jf.dexlib2.immutable.instruction.ImmutableInstruction31i(Opcode.CONST, 3, FROGLOG_ICON));
        extra.add(new org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n(Opcode.CONST_4, 4, 0));
        extra.add(new org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n(Opcode.CONST_4, 5, 0));
        extra.add(new org.jf.dexlib2.immutable.instruction.ImmutableInstruction21s(Opcode.CONST_16, 6, 0x1c));
        extra.add(new ImmutableInstruction3rc(
                Opcode.INVOKE_DIRECT_RANGE, 0, 7,
                new ImmutableMethodReference(ICONS, "<init>",
                        Arrays.asList("Ljava/lang/String;", "I", "I", "Lw1/v;", "Lw1/v;", "I"), "V")));
        extra.add(new ImmutableInstruction21c(Opcode.SPUT_OBJECT, 0,
                new ImmutableFieldReference(ICONS, "FROGLOG", ICONS)));
        List<Instruction> rewritten = new ArrayList<Instruction>();
        rewritten.addAll(original.subList(0, values));
        rewritten.addAll(extra);
        rewritten.addAll(original.subList(values, original.size()));
        System.out.println("icon enum FROGLOG ordinal 211");
        return replace(method, new ImmutableMethodImplementation(
                Math.max(impl.getRegisterCount(), 7),
                rewritten,
                Collections.emptyList(),
                Collections.emptyList()));
    }

    private static Method patchIconValues(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> original = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            original.add(instruction);
        }
        Instruction size = original.get(0);
        if (size.getOpcode() != Opcode.CONST_16
                || ((org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction) size).getNarrowLiteral() != 0xd3
                || original.get(original.size() - 1).getOpcode() != Opcode.RETURN_OBJECT) {
            throw new IllegalStateException("icon values shape changed");
        }
        int array = ((OneRegisterInstruction) size).getRegisterA();
        List<Instruction> rewritten = new ArrayList<Instruction>();
        rewritten.add(new org.jf.dexlib2.immutable.instruction.ImmutableInstruction21s(Opcode.CONST_16, array, 0xd4));
        rewritten.addAll(original.subList(1, original.size() - 1));
        rewritten.add(new ImmutableInstruction21c(Opcode.SGET_OBJECT, 1,
                new ImmutableFieldReference(ICONS, "FROGLOG", ICONS)));
        rewritten.add(new org.jf.dexlib2.immutable.instruction.ImmutableInstruction21s(Opcode.CONST_16, 2, 0xd3));
        rewritten.add(new org.jf.dexlib2.immutable.instruction.ImmutableInstruction23x(Opcode.APUT_OBJECT, 1, array, 2));
        rewritten.add(original.get(original.size() - 1));
        return replace(method, new ImmutableMethodImplementation(
                Math.max(impl.getRegisterCount(), 3),
                rewritten,
                Collections.emptyList(),
                Collections.emptyList()));
    }

    private static ClassDef patchFriendTabEnum(ClassDef tabs) {
        List<Field> statics = new ArrayList<Field>();
        boolean hasFroglog = false;
        for (Field field : tabs.getStaticFields()) {
            if ("FROGLOG".equals(field.getName())) {
                hasFroglog = true;
            }
            statics.add(field);
        }
        if (!hasFroglog) {
            statics.add(new ImmutableField(
                    FRIEND_TABS,
                    "FROGLOG",
                    FRIEND_TABS,
                    0x4019,
                    null,
                    Collections.emptySet(),
                    Collections.emptySet()));
        }
        List<Field> instance = new ArrayList<Field>();
        for (Field field : tabs.getInstanceFields()) {
            instance.add(field);
        }
        List<Method> direct = new ArrayList<Method>();
        boolean patched = false;
        for (Method method : tabs.getDirectMethods()) {
            if ("<clinit>".equals(method.getName())) {
                direct.add(patchFriendTabClinit(method));
                patched = true;
            } else {
                direct.add(method);
            }
        }
        if (!patched) {
            throw new IllegalStateException("friend tab initializer not found");
        }
        List<Method> virtual = new ArrayList<Method>();
        for (Method method : tabs.getVirtualMethods()) {
            virtual.add(method);
        }
        return new ImmutableClassDef(
                tabs.getType(),
                tabs.getAccessFlags(),
                tabs.getSuperclass(),
                tabs.getInterfaces(),
                tabs.getSourceFile(),
                tabs.getAnnotations(),
                statics,
                instance,
                direct,
                virtual);
    }

    private static Method patchFriendTabClinit(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> original = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            original.add(instruction);
        }
        int filled = -1;
        for (int i = 0; i < original.size(); i++) {
            if (original.get(i).getOpcode() == Opcode.FILLED_NEW_ARRAY) {
                filled = i;
                break;
            }
        }
        if (filled < 0) {
            throw new IllegalStateException("friend tab values not found");
        }
        List<Instruction> extra = new ArrayList<Instruction>();
        extra.add(new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 2, new ImmutableTypeReference(FRIEND_TABS)));
        extra.add(new ImmutableInstruction21c(Opcode.CONST_STRING, 3, new ImmutableStringReference("FROGLOG")));
        extra.add(new org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n(Opcode.CONST_4, 4, 2));
        extra.add(new ImmutableInstruction21c(Opcode.CONST_STRING, 5, new ImmutableStringReference("Froglog")));
        extra.add(new ImmutableInstruction35c(
                Opcode.INVOKE_DIRECT,
                4, 2, 3, 4, 5, 0,
                new ImmutableMethodReference(
                        FRIEND_TABS,
                        "<init>",
                        Arrays.asList("Ljava/lang/String;", "I", "Ljava/lang/String;"),
                        "V")));
        extra.add(new ImmutableInstruction21c(
                Opcode.SPUT_OBJECT,
                2,
                new ImmutableFieldReference(FRIEND_TABS, "FROGLOG", FRIEND_TABS)));
        List<Instruction> rewritten = new ArrayList<Instruction>();
        rewritten.addAll(original.subList(0, filled));
        rewritten.addAll(extra);
        rewritten.add(new ImmutableInstruction35c(
                Opcode.FILLED_NEW_ARRAY,
                3, 0, 1, 2, 0, 0,
                new ImmutableTypeReference("[Lef/w0;")));
        rewritten.addAll(original.subList(filled + 1, original.size()));
        System.out.println("friend tab values 3");
        return replace(method, new ImmutableMethodImplementation(
                Math.max(impl.getRegisterCount(), 6),
                rewritten,
                Collections.emptyList(),
                Collections.emptyList()));
    }

    private static ClassDef patchFriendTabMaps(ClassDef maps) {
        List<Method> direct = new ArrayList<Method>();
        boolean patched = false;
        for (Method method : maps.getDirectMethods()) {
            if ("<clinit>".equals(method.getName())) {
                direct.add(patchFriendTabMapClinit(method));
                patched = true;
            } else {
                direct.add(method);
            }
        }
        if (!patched) {
            throw new IllegalStateException("friend tab map initializer not found");
        }
        List<Method> virtual = new ArrayList<Method>();
        for (Method method : maps.getVirtualMethods()) {
            virtual.add(method);
        }
        return copyClass(maps, direct, virtual);
    }

    private static Method patchFriendTabMapClinit(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> original = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            original.add(instruction);
        }
        int sput = -1;
        for (int i = 0; i < original.size(); i++) {
            Instruction instruction = original.get(i);
            if (instruction.getOpcode() != Opcode.SPUT_OBJECT || !(instruction instanceof ReferenceInstruction)) {
                continue;
            }
            Reference ref = ((ReferenceInstruction) instruction).getReference();
            if (ref instanceof FieldReference && "a".equals(((FieldReference) ref).getName())
                    && FRIEND_MAPS.equals(((FieldReference) ref).getDefiningClass())) {
                sput = i;
                break;
            }
        }
        if (sput < 0) {
            throw new IllegalStateException("friend tab map store not found");
        }
        List<Instruction> extra = new ArrayList<Instruction>();
        extra.add(new ImmutableInstruction21c(
                Opcode.SGET_OBJECT,
                3,
                new ImmutableFieldReference(FRIEND_TABS, "FROGLOG", FRIEND_TABS)));
        extra.add(new ImmutableInstruction35c(
                Opcode.INVOKE_VIRTUAL,
                1, 3, 0, 0, 0, 0,
                new ImmutableMethodReference("Ljava/lang/Enum;", "ordinal", Collections.<String>emptyList(), "I")));
        extra.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT, 3));
        extra.add(new org.jf.dexlib2.immutable.instruction.ImmutableInstruction23x(Opcode.APUT, 1, 0, 3));
        List<Instruction> rewritten = new ArrayList<Instruction>();
        rewritten.addAll(original.subList(0, sput));
        rewritten.addAll(extra);
        rewritten.addAll(original.subList(sput, original.size()));
        System.out.println("friend tab map includes Froglog");
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                rewritten,
                Collections.emptyList(),
                Collections.emptyList()));
    }

    private static Method patchFriendPanel(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> original = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            original.add(instruction);
        }
        List<Instruction> prefix = new ArrayList<Instruction>();
        prefix.add(new ImmutableInstruction22x(Opcode.MOVE_OBJECT_FROM16, 0, 72));
        prefix.add(new ImmutableInstruction22x(Opcode.MOVE_OBJECT_FROM16, 1, 68));
        prefix.add(new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                2, 0, 1, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/FroglogSocial;",
                        "listForTab",
                        Arrays.asList("Ljava/lang/Object;", "Ljava/util/List;"),
                        "Ljava/util/List;")));
        prefix.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 68));
        int added = 0;
        for (Instruction instruction : prefix) {
            added += instruction.getCodeUnits();
        }
        if (added != 8) {
            throw new IllegalStateException("friend panel prefix " + added);
        }
        int[] addresses = addresses(original);
        int[] switchAt = switchAddresses(original, addresses);
        List<Instruction> rewritten = new ArrayList<Instruction>(prefix);
        for (int i = 0; i < original.size(); i++) {
            rewritten.add(retarget(original.get(i), addresses[i], switchAt[i], 0, added));
        }
        System.out.println("friend panel instructions " + rewritten.size());
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                rewritten,
                shiftTries(impl.getTryBlocks(), 0, added),
                Collections.emptyList()));
    }

    private static Method patchFriendTabs(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> instructions = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        int insert = -1;
        for (int i = 0; i < instructions.size() - 1; i++) {
            Instruction instruction = instructions.get(i);
            if (instruction.getOpcode() != Opcode.INVOKE_STATIC || !(instruction instanceof ReferenceInstruction)) {
                continue;
            }
            Reference ref = ((ReferenceInstruction) instruction).getReference();
            if (!(ref instanceof MethodReference)) {
                continue;
            }
            MethodReference methodRef = (MethodReference) ref;
            if (!"Lr3/a;".equals(methodRef.getDefiningClass()) || !"k".equals(methodRef.getName())) {
                continue;
            }
            if (instructions.get(i + 1).getOpcode() != Opcode.MOVE_RESULT_OBJECT) {
                continue;
            }
            insert = i + 2;
            break;
        }
        if (insert < 0) {
            throw new IllegalStateException("friend tabs list freeze not found");
        }
        int tab = 6;
        if (instructions.get(insert - 1) instanceof OneRegisterInstruction) {
            tab = ((OneRegisterInstruction) instructions.get(insert - 1)).getRegisterA();
        }
        if (tab > 15) {
            throw new IllegalStateException("friend tabs register " + tab);
        }
        List<Instruction> extra = new ArrayList<Instruction>();
        extra.add(new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                1, tab, 0, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/FroglogSocial;",
                        "withFriendsTabs",
                        Collections.singletonList("Ljava/util/List;"),
                        "Ljava/util/List;")));
        extra.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, tab));
        int added = 0;
        for (Instruction instruction : extra) {
            added += instruction.getCodeUnits();
        }
        if (added != 4) {
            throw new IllegalStateException("friend tabs insert " + added);
        }
        int[] addresses = addresses(instructions);
        int insertAt = addresses[insert];
        int[] switchAt = switchAddresses(instructions, addresses);
        List<Instruction> rewritten = new ArrayList<Instruction>();
        for (int i = 0; i < instructions.size(); i++) {
            if (i == insert) {
                rewritten.addAll(extra);
            }
            rewritten.add(retarget(instructions.get(i), addresses[i], switchAt[i], insertAt, added));
        }
        System.out.println("friend tabs instructions " + rewritten.size());
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                rewritten,
                shiftTries(impl.getTryBlocks(), insertAt, added),
                Collections.emptyList()));
    }

    /**
     * The friends panel returns early unless Steam is available or Android social
     * conversations exist. The Steam flag is copied to a high register right before
     * that check, so FroglogSocial can also open it for a signed-in Froglog account.
     */
    private static Method patchFriendGate(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> instructions = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        int steamCheck = -1;
        for (int i = 0; i < instructions.size() - 1; i++) {
            Instruction instruction = instructions.get(i);
            if (instruction.getOpcode() == Opcode.INVOKE_STATIC && instruction instanceof ReferenceInstruction) {
                Reference ref = ((ReferenceInstruction) instruction).getReference();
                if (ref instanceof MethodReference
                        && "Lrip/moth/cocoonshell/data/api/z;".equals(((MethodReference) ref).getDefiningClass())
                        && "m".equals(((MethodReference) ref).getName())
                        && instructions.get(i + 1).getOpcode() == Opcode.MOVE_RESULT) {
                    steamCheck = i;
                    break;
                }
            }
        }
        if (steamCheck < 0) {
            throw new IllegalStateException("friend panel Steam check not found");
        }
        int steam = ((OneRegisterInstruction) instructions.get(steamCheck + 1)).getRegisterA();
        int tabs = -1;
        for (int i = steamCheck; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if (instruction.getOpcode() == Opcode.INVOKE_STATIC && instruction instanceof ReferenceInstruction) {
                Reference ref = ((ReferenceInstruction) instruction).getReference();
                if (ref instanceof MethodReference && "withFriendsTabs".equals(((MethodReference) ref).getName())) {
                    tabs = i;
                    break;
                }
            }
        }
        int insert = -1;
        for (int i = Math.max(tabs, steamCheck); i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if (instruction.getOpcode() == Opcode.MOVE_FROM16
                    && ((TwoRegisterInstruction) instruction).getRegisterB() == steam
                    && ((TwoRegisterInstruction) instruction).getRegisterA() > 15) {
                insert = i;
                break;
            }
        }
        if (tabs < 0 || insert < 0 || steam > 15) {
            throw new IllegalStateException("friend panel gate tabs=" + tabs + " insert=" + insert + " steam=v" + steam);
        }
        List<Instruction> extra = new ArrayList<Instruction>();
        extra.add(new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                1, steam, 0, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/FroglogSocial;",
                        "showFriendsPanel",
                        Collections.singletonList("Z"),
                        "Z")));
        extra.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT, steam));
        int added = 0;
        for (Instruction instruction : extra) {
            added += instruction.getCodeUnits();
        }
        if (added != 4) {
            throw new IllegalStateException("friend panel gate insert " + added);
        }
        int[] addresses = addresses(instructions);
        int insertAt = addresses[insert];
        int[] switchAt = switchAddresses(instructions, addresses);
        List<Instruction> rewritten = new ArrayList<Instruction>();
        for (int i = 0; i < instructions.size(); i++) {
            if (i == insert) {
                rewritten.addAll(extra);
            }
            rewritten.add(retarget(instructions.get(i), addresses[i], switchAt[i], insertAt, added));
        }
        System.out.println("friend panel gate v" + steam + " -> v"
                + ((TwoRegisterInstruction) instructions.get(insert)).getRegisterA());
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                rewritten,
                shiftTries(impl.getTryBlocks(), insertAt, added),
                Collections.emptyList()));
    }

    private static Method patchFriendOpen(Method method) {
        MethodImplementation impl = method.getImplementation();
        int friend = impl.getRegisterCount() - parameterWords(method) + 1;
        if (friend != 25) {
            throw new IllegalStateException("friend register " + friend);
        }
        List<Instruction> instructions = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        int insert = -1;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if (instruction.getOpcode() != Opcode.CHECK_CAST || !(instruction instanceof ReferenceInstruction)) {
                continue;
            }
            Reference ref = ((ReferenceInstruction) instruction).getReference();
            if (ref instanceof TypeReference && "Lef/d6;".equals(((TypeReference) ref).getType())) {
                insert = i > 0 && instructions.get(i - 1).getOpcode() == Opcode.MOVE_OBJECT_FROM16 ? i - 1 : i;
                break;
            }
        }
        if (insert < 0) {
            throw new IllegalStateException("friend click case not found");
        }
        // invoke-static only accepts v0-v15, and the friend arrives in v25.
        List<Instruction> prefix = new ArrayList<Instruction>();
        prefix.add(new ImmutableInstruction22x(Opcode.MOVE_OBJECT_FROM16, 0, friend));
        prefix.add(new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                1, 0, 0, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/FroglogSocial;",
                        "openIfFriend",
                        Collections.singletonList("Ljava/lang/Object;"),
                        "Z")));
        prefix.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT, 0));
        prefix.add(new ImmutableInstruction21t(Opcode.IF_EQZ, 0, 5));
        prefix.add(new ImmutableInstruction21c(Opcode.SGET_OBJECT, 0,
                new ImmutableFieldReference("Lta/z;", "a", "Lta/z;")));
        prefix.add(new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
        // packed-switch payloads must stay 4-byte aligned. The friend case sits
        // between the switch and its payload, so the insert has to be an even
        // number of code units. The nop is the fall-through after the early return.
        prefix.add(new ImmutableInstruction10x(Opcode.NOP));
        int added = 0;
        for (Instruction instruction : prefix) {
            added += instruction.getCodeUnits();
        }
        if (added != 12 || (added & 1) != 0) {
            throw new IllegalStateException("friend click prefix " + added);
        }
        int[] addresses = addresses(instructions);
        int insertAt = addresses[insert];
        int[] switchAt = switchAddresses(instructions, addresses);
        List<Instruction> rewritten = new ArrayList<Instruction>();
        for (int i = 0; i < instructions.size(); i++) {
            if (i == insert) {
                rewritten.addAll(prefix);
            }
            rewritten.add(retarget(instructions.get(i), addresses[i], switchAt[i], insertAt, added));
        }
        System.out.println("friend click instructions " + rewritten.size());
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                rewritten,
                shiftTries(impl.getTryBlocks(), insertAt, added),
                Collections.emptyList()));
    }

    private static int[] addresses(List<Instruction> instructions) {
        int[] addresses = new int[instructions.size()];
        int address = 0;
        for (int i = 0; i < instructions.size(); i++) {
            addresses[i] = address;
            address += instructions.get(i).getCodeUnits();
        }
        return addresses;
    }

    /** Payload index to the address of the switch instruction that jumps there. -1 otherwise. */
    private static int[] switchAddresses(List<Instruction> instructions, int[] addresses) {
        int[] pointed = new int[instructions.size()];
        java.util.Arrays.fill(pointed, -1);
        for (int i = 0; i < instructions.size(); i++) {
            if (!(instructions.get(i) instanceof Instruction31t)) {
                continue;
            }
            int payload = addresses[i] + ((Instruction31t) instructions.get(i)).getCodeOffset();
            for (int j = 0; j < addresses.length; j++) {
                if (addresses[j] == payload) {
                    pointed[j] = addresses[i];
                }
            }
        }
        return pointed;
    }

    private static Instruction retarget(Instruction instruction, int address, int switchAddress, int insertAt, int added) {
        if (instruction instanceof Instruction21t) {
            Instruction21t branch = (Instruction21t) instruction;
            return new ImmutableInstruction21t(branch.getOpcode(), branch.getRegisterA(),
                    retargetOffset(address, branch.getCodeOffset(), insertAt, added, false));
        }
        if (instruction instanceof Instruction22t) {
            Instruction22t branch = (Instruction22t) instruction;
            return new ImmutableInstruction22t(branch.getOpcode(), branch.getRegisterA(), branch.getRegisterB(),
                    retargetOffset(address, branch.getCodeOffset(), insertAt, added, false));
        }
        if (instruction instanceof Instruction10t) {
            Instruction10t branch = (Instruction10t) instruction;
            return new ImmutableInstruction10t(branch.getOpcode(),
                    retargetOffset(address, branch.getCodeOffset(), insertAt, added, false));
        }
        if (instruction instanceof Instruction20t) {
            Instruction20t branch = (Instruction20t) instruction;
            return new ImmutableInstruction20t(branch.getOpcode(),
                    retargetOffset(address, branch.getCodeOffset(), insertAt, added, false));
        }
        if (instruction instanceof Instruction30t) {
            Instruction30t branch = (Instruction30t) instruction;
            return new ImmutableInstruction30t(branch.getOpcode(),
                    retargetOffset(address, branch.getCodeOffset(), insertAt, added, false));
        }
        if (instruction instanceof Instruction31t) {
            Instruction31t branch = (Instruction31t) instruction;
            return new ImmutableInstruction31t(branch.getOpcode(), branch.getRegisterA(),
                    retargetOffset(address, branch.getCodeOffset(), insertAt, added, true));
        }
        if (instruction instanceof PackedSwitchPayload) {
            if (switchAddress < 0) {
                throw new IllegalStateException("packed switch payload has no switch");
            }
            return new ImmutablePackedSwitchPayload(retargetElements(
                    ((PackedSwitchPayload) instruction).getSwitchElements(), switchAddress, insertAt, added));
        }
        if (instruction instanceof SparseSwitchPayload) {
            if (switchAddress < 0) {
                throw new IllegalStateException("sparse switch payload has no switch");
            }
            return new ImmutableSparseSwitchPayload(retargetElements(
                    ((SparseSwitchPayload) instruction).getSwitchElements(), switchAddress, insertAt, added));
        }
        return instruction;
    }

    private static List<SwitchElement> retargetElements(List<? extends SwitchElement> elements, int switchAddress,
            int insertAt, int added) {
        List<SwitchElement> next = new ArrayList<SwitchElement>();
        for (SwitchElement element : elements) {
            int target = switchAddress + element.getOffset();
            int newSwitch = switchAddress >= insertAt ? switchAddress + added : switchAddress;
            int newTarget = target > insertAt ? target + added : target;
            next.add(new ImmutableSwitchElement(element.getKey(), newTarget - newSwitch));
        }
        return next;
    }

    private static int retargetOffset(int address, int offset, int insertAt, int added, boolean codeLanding) {
        int target = address + offset;
        int newAddress = address >= insertAt ? address + added : address;
        int newTarget = codeLanding
                ? (target >= insertAt ? target + added : target)
                : (target > insertAt ? target + added : target);
        return newTarget - newAddress;
    }

    private static List<TryBlock<? extends ExceptionHandler>> shiftTries(
            Iterable<? extends TryBlock<? extends ExceptionHandler>> tries, int insertAt, int added) {
        List<TryBlock<? extends ExceptionHandler>> next = new ArrayList<TryBlock<? extends ExceptionHandler>>();
        for (TryBlock<? extends ExceptionHandler> block : tries) {
            int start = block.getStartCodeAddress();
            int count = block.getCodeUnitCount();
            int newStart = start >= insertAt ? start + added : start;
            int newCount = start < insertAt && insertAt < start + count ? count + added : count;
            List<ExceptionHandler> handlers = new ArrayList<ExceptionHandler>();
            for (ExceptionHandler handler : block.getExceptionHandlers()) {
                int at = handler.getHandlerCodeAddress();
                handlers.add(new ImmutableExceptionHandler(
                        handler.getExceptionType(),
                        at >= insertAt ? at + added : at));
            }
            next.add(new ImmutableTryBlock(newStart, newCount, handlers));
        }
        return next;
    }

    private static int parameterWords(Method method) {
        int words = (method.getAccessFlags() & 0x8) == 0 ? 1 : 0;
        for (CharSequence type : method.getParameterTypes()) {
            char c = type.charAt(0);
            words += (c == 'J' || c == 'D') ? 2 : 1;
        }
        return words;
    }

    private static Method patchOpen(Method method) {
        // registers 11, static, 5 params. p0=v6 p1=v7 p2=v8 p3=v9 p4=v10
        MethodImplementationBuilder code = new MethodImplementationBuilder(11);
        Label notAndroid = code.getLabel("not_android");
        Label placed = code.getLabel("placed");

        code.addInstruction(new BuilderInstruction21c(Opcode.SGET_OBJECT, 0,
                field(CATALOG, "f", "Ljava/util/List;")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_INTERFACE, 2, 0, 10, 0, 0, 0,
                method("Ljava/util/List;", "get", Arrays.asList("I"), "Ljava/lang/Object;")));
        code.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1));
        code.addInstruction(new BuilderInstruction21c(Opcode.CHECK_CAST, 1, new ImmutableTypeReference("Lmf/o1;")));
        code.addInstruction(new BuilderInstruction21c(Opcode.SGET_OBJECT, 2,
                field("Lrip/moth/cocoonshell/utils/e2;", "a", "Lrip/moth/cocoonshell/utils/e2;")));
        code.addInstruction(new BuilderInstruction22c(Opcode.IGET_OBJECT, 2, 1,
                field("Lmf/o1;", "a", "Lrip/moth/cocoonshell/data/model/Widget$WidgetType;")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 6, 0, 0, 0, 0,
                method("Lz0/a1;", "k", Collections.<String>emptyList(), "I")));
        code.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT, 3));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_INTERFACE, 1, 0, 0, 0, 0, 0,
                method("Ljava/util/List;", "size", Collections.<String>emptyList(), "I")));
        code.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT, 0));
        code.addInstruction(new BuilderInstruction21c(Opcode.NEW_INSTANCE, 4,
                new ImmutableTypeReference("Ljava/lang/StringBuilder;")));
        code.addInstruction(new BuilderInstruction21c(Opcode.CONST_STRING, 5,
                new ImmutableStringReference("WDBG picker openType: index=")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_DIRECT, 2, 4, 5, 0, 0, 0,
                method("Ljava/lang/StringBuilder;", "<init>", Arrays.asList("Ljava/lang/String;"), "V")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 4, 10, 0, 0, 0,
                method("Ljava/lang/StringBuilder;", "append", Arrays.asList("I"), "Ljava/lang/StringBuilder;")));
        code.addInstruction(new BuilderInstruction21c(Opcode.CONST_STRING, 5,
                new ImmutableStringReference(" type=")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 4, 5, 0, 0, 0,
                method("Ljava/lang/StringBuilder;", "append", Arrays.asList("Ljava/lang/String;"), "Ljava/lang/StringBuilder;")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 4, 2, 0, 0, 0,
                method("Ljava/lang/StringBuilder;", "append", Arrays.asList("Ljava/lang/Object;"), "Ljava/lang/StringBuilder;")));
        code.addInstruction(new BuilderInstruction21c(Opcode.CONST_STRING, 2,
                new ImmutableStringReference(" (selectedTypeIdx=")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 4, 2, 0, 0, 0,
                method("Ljava/lang/StringBuilder;", "append", Arrays.asList("Ljava/lang/String;"), "Ljava/lang/StringBuilder;")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 4, 3, 0, 0, 0,
                method("Ljava/lang/StringBuilder;", "append", Arrays.asList("I"), "Ljava/lang/StringBuilder;")));
        code.addInstruction(new BuilderInstruction21c(Opcode.CONST_STRING, 2,
                new ImmutableStringReference(" catalogSize=")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 4, 2, 0, 0, 0,
                method("Ljava/lang/StringBuilder;", "append", Arrays.asList("Ljava/lang/String;"), "Ljava/lang/StringBuilder;")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 4, 0, 0, 0, 0,
                method("Ljava/lang/StringBuilder;", "append", Arrays.asList("I"), "Ljava/lang/StringBuilder;")));
        code.addInstruction(new BuilderInstruction21c(Opcode.CONST_STRING, 0,
                new ImmutableStringReference(")")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 4, 0, 0, 0, 0,
                method("Ljava/lang/StringBuilder;", "append", Arrays.asList("Ljava/lang/String;"), "Ljava/lang/StringBuilder;")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 4, 0, 0, 0, 0,
                method("Ljava/lang/StringBuilder;", "toString", Collections.<String>emptyList(), "Ljava/lang/String;")));
        code.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0,
                method("Lrip/moth/cocoonshell/utils/e2;", "b", Arrays.asList("Ljava/lang/String;"), "V")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 6, 10, 0, 0, 0,
                method("Lz0/a1;", "m", Arrays.asList("I"), "V")));
        code.addInstruction(new BuilderInstruction22c(Opcode.IGET_OBJECT, 6, 1,
                field("Lmf/o1;", "a", "Lrip/moth/cocoonshell/data/model/Widget$WidgetType;")));
        code.addInstruction(new BuilderInstruction21c(Opcode.SGET_OBJECT, 10,
                field("Lrip/moth/cocoonshell/data/model/Widget$WidgetType;", "ANDROID_WIDGET",
                        "Lrip/moth/cocoonshell/data/model/Widget$WidgetType;")));
        code.addInstruction(new BuilderInstruction22t(Opcode.IF_NE, 6, 10, notAndroid));

        // Froglog tile only. A false result falls through to the original Android-widget picker.
        code.addInstruction(new BuilderInstruction22c(Opcode.IGET, 0, 1, field("Lmf/o1;", "b", "I")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_STATIC, 2, 0, 9, 0, 0, 0,
                method("Lrip/moth/cocoonshell/froglog/CatalogHook;", "maybeConfirm",
                        Arrays.asList("I", "Ljb/g;"), "Z")));
        code.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT, 0));
        code.addInstruction(new BuilderInstruction21t(Opcode.IF_NEZ, 0, placed));

        code.addInstruction(new BuilderInstruction11n(Opcode.CONST_4, 6, 0));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 7, 6, 0, 0, 0,
                method("Lz0/a1;", "m", Arrays.asList("I"), "V")));
        code.addInstruction(new BuilderInstruction21c(Opcode.SGET_OBJECT, 6,
                field("Lmf/q;", "PROVIDER", "Lmf/q;")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_INTERFACE, 2, 8, 6, 0, 0, 0,
                method("Lz0/u0;", "setValue", Arrays.asList("Ljava/lang/Object;"), "V")));
        code.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));

        code.addLabel("placed");
        code.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));

        code.addLabel("not_android");
        code.addInstruction(new BuilderInstruction11n(Opcode.CONST_4, 7, 0));
        code.addInstruction(new BuilderInstruction22c(Opcode.IGET_OBJECT, 8, 1, field("Lmf/o1;", "e", "Ljava/util/List;")));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_STATIC, 4, 9, 6, 7, 8, 0,
                method(CATALOG, "f", Arrays.asList(
                        "Ljb/g;",
                        "Lrip/moth/cocoonshell/data/model/Widget$WidgetType;",
                        "Landroid/appwidget/AppWidgetProviderInfo;",
                        "Ljava/util/List;"), "V")));
        code.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
        return replace(method, code.getMethodImplementation());
    }

    private static ClassDef prefixGlassMethods(ClassDef cls, String... names) {
        java.util.HashSet<String> want = new java.util.HashSet<String>(Arrays.asList(names));
        int patched = 0;
        List<Method> direct = new ArrayList<Method>();
        for (Method method : cls.getDirectMethods()) {
            if (want.contains(method.getName()) && "V".equals(method.getReturnType())
                    && method.getImplementation() != null) {
                direct.add(prefixSkipGlass(method));
                patched++;
            } else {
                direct.add(method);
            }
        }
        List<Method> virtual = new ArrayList<Method>();
        for (Method method : cls.getVirtualMethods()) {
            if (want.contains(method.getName()) && "V".equals(method.getReturnType())
                    && method.getImplementation() != null) {
                virtual.add(prefixSkipGlass(method));
                patched++;
            } else {
                virtual.add(method);
            }
        }
        if (patched != names.length) {
            throw new IllegalStateException(cls.getType() + " glass methods " + patched);
        }
        return copyClass(cls, direct, virtual);
    }

    /** Return before any RuntimeShader use when the device is older than Android 13. */
    private static Method prefixSkipGlass(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> original = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            original.add(instruction);
        }
        List<Instruction> prefix = new ArrayList<Instruction>();
        prefix.add(new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                0, 0, 0, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/GlassCompat;",
                        "runtimeShadersAvailable",
                        Collections.<String>emptyList(),
                        "Z")));
        prefix.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT, 0));
        prefix.add(new ImmutableInstruction21t(Opcode.IF_NEZ, 0, 4));
        prefix.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        prefix.add(new ImmutableInstruction10x(Opcode.NOP));
        int added = 0;
        for (Instruction instruction : prefix) {
            added += instruction.getCodeUnits();
        }
        if (added != 8) {
            throw new IllegalStateException("glass prefix " + added);
        }
        int[] addresses = addresses(original);
        int[] switchAt = switchAddresses(original, addresses);
        List<Instruction> rewritten = new ArrayList<Instruction>(prefix);
        for (int i = 0; i < original.size(); i++) {
            rewritten.add(retarget(original.get(i), addresses[i], switchAt[i], 0, added));
        }
        System.out.println("glass skip " + method.getDefiningClass() + "->" + method.getName()
                + " instructions " + rewritten.size());
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                rewritten,
                shiftTries(impl.getTryBlocks(), 0, added),
                Collections.emptyList()));
    }

    /**
     * Android widgets cannot keep Compose drop shadows while the home screen
     * pans. Style the host view instead so the squircle and elevation stay put.
     */
    private static ClassDef patchWidgetHost(ClassDef host) {
        List<Method> direct = new ArrayList<Method>();
        boolean clear = false;
        for (Method method : host.getDirectMethods()) {
            if ("f".equals(method.getName()) && "(Landroid/view/View;)V".equals(signature(method))) {
                direct.add(patchAndroidWidgetHostView(method));
                clear = true;
            } else {
                direct.add(method);
            }
        }
        if (!clear) {
            throw new IllegalStateException("widget host style not found");
        }
        List<Method> virtual = new ArrayList<Method>();
        for (Method method : host.getVirtualMethods()) {
            virtual.add(method);
        }
        return copyClass(host, direct, virtual);
    }

    private static Method patchAndroidWidgetHostView(Method method) {
        MethodImplementationBuilder code = new MethodImplementationBuilder(1);
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0,
                method("Lrip/moth/cocoonshell/froglog/GlassCompat;", "styleAndroidWidget",
                        Collections.singletonList("Landroid/view/View;"), "V")));
        code.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
        System.out.println("android widget host uses stable tile face");
        return replace(method, code.getMethodImplementation());
    }

    private static ClassDef patchTheme(ClassDef theme) {
        List<Method> direct = new ArrayList<Method>();
        for (Method method : theme.getDirectMethods()) {
            direct.add(method);
        }
        List<Method> virtual = new ArrayList<Method>();
        boolean material = false;
        boolean tiles = false;
        for (Method method : theme.getVirtualMethods()) {
            if ("getSurfaceMaterial".equals(method.getName())
                    && "()Ljava/lang/String;".equals(signature(method))) {
                virtual.add(wrapThemeString(method, "surfaceMaterial", "safeSurfaceMaterial"));
                material = true;
            } else if ("getGlassOnTiles".equals(method.getName())
                    && "()Ljava/lang/Boolean;".equals(signature(method))) {
                virtual.add(wrapThemeString(method, "glassOnTiles", "safeGlassOnTiles"));
                tiles = true;
            } else {
                virtual.add(method);
            }
        }
        if (!material || !tiles) {
            throw new IllegalStateException("theme material=" + material + " tiles=" + tiles);
        }
        return copyClass(theme, direct, virtual);
    }

    private static Method wrapThemeString(Method method, String fieldName, String helper) {
        String fieldType = method.getReturnType();
        MethodImplementationBuilder code = new MethodImplementationBuilder(2);
        code.addInstruction(new BuilderInstruction22c(Opcode.IGET_OBJECT, 0, 1,
                field(THEME, fieldName, fieldType)));
        code.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0,
                method("Lrip/moth/cocoonshell/froglog/GlassCompat;", helper,
                        Collections.singletonList(fieldType), fieldType)));
        code.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
        code.addInstruction(new BuilderInstruction11x(Opcode.RETURN_OBJECT, 0));
        System.out.println("theme " + fieldName + " wrapped");
        return replace(method, code.getMethodImplementation());
    }

    private static ClassDef patchSurfacePrefs(ClassDef prefs) {
        List<Method> direct = new ArrayList<Method>();
        for (Method method : prefs.getDirectMethods()) {
            direct.add(method);
        }
        List<Method> virtual = new ArrayList<Method>();
        boolean patched = false;
        for (Method method : prefs.getVirtualMethods()) {
            if ("J0".equals(method.getName()) && "()Lfe/s1;".equals(signature(method))) {
                virtual.add(patchSurfaceName(method));
                patched = true;
            } else {
                virtual.add(method);
            }
        }
        if (!patched) {
            throw new IllegalStateException("surface material preference reader not found");
        }
        return copyClass(prefs, direct, virtual);
    }

    private static Method patchSurfaceName(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> instructions = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        int insert = -1;
        for (int i = 0; i < instructions.size(); i++) {
            if (instructions.get(i).getOpcode() == Opcode.INVOKE_STATIC
                    && instructions.get(i) instanceof ReferenceInstruction) {
                Reference ref = ((ReferenceInstruction) instructions.get(i)).getReference();
                if (ref instanceof org.jf.dexlib2.iface.reference.MethodReference) {
                    org.jf.dexlib2.iface.reference.MethodReference mr =
                            (org.jf.dexlib2.iface.reference.MethodReference) ref;
                    if ("valueOf".equals(mr.getName()) && "Lfe/s1;".equals(mr.getDefiningClass())) {
                        insert = i;
                        break;
                    }
                }
            }
        }
        if (insert < 0) {
            throw new IllegalStateException("surface valueOf not found");
        }
        int[] addresses = addresses(instructions);
        int insertAt = addresses[insert];
        instructions.add(insert, new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                1, 0, 0, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/GlassCompat;",
                        "safeSurfaceName",
                        Collections.singletonList("Ljava/lang/String;"),
                        "Ljava/lang/String;")));
        instructions.add(insert + 1, new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
        System.out.println("surface prefs instructions " + instructions.size());
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                instructions,
                shiftTries(impl.getTryBlocks(), insertAt, 4),
                Collections.emptyList()));
    }

    private static ImmutableFieldReference field(String owner, String name, String type) {
        return new ImmutableFieldReference(owner, name, type);
    }

    private static ImmutableMethodReference method(String owner, String name, List<String> params, String ret) {
        return new ImmutableMethodReference(owner, name, params, ret);
    }

    private static Method replace(Method method, MethodImplementation implementation) {
        return new ImmutableMethod(
                method.getDefiningClass(),
                method.getName(),
                method.getParameters(),
                method.getReturnType(),
                method.getAccessFlags(),
                method.getAnnotations(),
                method.getHiddenApiRestrictions(),
                implementation);
    }
}
