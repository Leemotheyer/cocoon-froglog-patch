import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder;
import com.android.tools.smali.dexlib2.builder.Label;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22t;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchElement;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction10t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction20t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction30t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.PackedSwitchPayload;
import com.android.tools.smali.dexlib2.iface.instruction.formats.SparseSwitchPayload;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableExceptionHandler;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10t;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction20t;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21t;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22t;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction30t;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31t;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutablePackedSwitchPayload;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableSparseSwitchPayload;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableSwitchElement;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference;

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
 * add followed Froglog users to the friends list.
 * Recently played is not rewritten. insertAll is not rewritten.
 */
public final class PatchCatalog {
    private static final String CATALOG = "Lmf/y1;";
    private static final String SESSION = "Lrip/moth/cocoonshell/data/local/GameSessionDao_Impl;";
    private static final String PODS = "Lxd/m0;";
    private static final String ROUTER = "Lrip/moth/cocoonshell/utils/u6;";
    private static final String FRIENDS = "Lef/d0;";
    private static final String FRIEND_CLICK = "Lef/q3;";
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
        ClassDef router = null;
        ClassDef friends = null;
        ClassDef friendClick = null;
        for (ClassDef cls : dex.getClasses()) {
            if (CATALOG.equals(cls.getType())) {
                catalog = cls;
            } else if (SESSION.equals(cls.getType())) {
                session = cls;
            } else if (PODS.equals(cls.getType())) {
                pods = cls;
            } else if (ROUTER.equals(cls.getType())) {
                router = cls;
            } else if (FRIENDS.equals(cls.getType())) {
                friends = cls;
            } else if (FRIEND_CLICK.equals(cls.getType())) {
                friendClick = cls;
            }
        }
        if (catalog == null || session == null || pods == null || router == null || friends == null || friendClick == null) {
            throw new IllegalStateException("catalog=" + (catalog != null) + " session=" + (session != null)
                    + " pods=" + (pods != null) + " router=" + (router != null)
                    + " friends=" + (friends != null) + " click=" + (friendClick != null));
        }
        final ClassDef catalogReplacement = patchCatalog(catalog);
        final ClassDef sessionReplacement = patchSession(session);
        final ClassDef podsReplacement = patchPods(pods);
        final ClassDef routerReplacement = patchRouterClass(router);
        final ClassDef friendsReplacement = patchFriends(friends);
        final ClassDef clickReplacement = patchFriendClick(friendClick);
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
                    } else if (ROUTER.equals(cls.getType())) {
                        classes.add(routerReplacement);
                    } else if (FRIENDS.equals(cls.getType())) {
                        classes.add(friendsReplacement);
                    } else if (FRIEND_CLICK.equals(cls.getType())) {
                        classes.add(clickReplacement);
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
        for (Method method : router.getDirectMethods()) {
            direct.add(method);
        }
        List<Method> virtual = new ArrayList<Method>();
        boolean patched = false;
        for (Method method : router.getVirtualMethods()) {
            if ("a".equals(method.getName()) && OPEN_POD.equals(signature(method))) {
                virtual.add(patchRouter(method));
                patched = true;
            } else {
                virtual.add(method);
            }
        }
        if (!patched) {
            throw new IllegalStateException("pod router not found");
        }
        return copyClass(router, direct, virtual);
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
        boolean patched = false;
        for (Method method : friends.getDirectMethods()) {
            if ("k0".equals(method.getName()) && "(Ljava/util/List;Ljava/util/Map;Z)Ljava/util/List;".equals(signature(method))) {
                direct.add(patchFriendList(method));
                patched = true;
            } else {
                direct.add(method);
            }
        }
        if (!patched) {
            throw new IllegalStateException("friend list builder not found");
        }
        List<Method> virtual = new ArrayList<Method>();
        for (Method method : friends.getVirtualMethods()) {
            virtual.add(method);
        }
        return copyClass(friends, direct, virtual);
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

    private static Method patchFriendList(Method method) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> instructions = new ArrayList<Instruction>();
        for (Instruction instruction : impl.getInstructions()) {
            instructions.add(instruction);
        }
        int ret = instructions.size() - 1;
        if (ret < 0 || instructions.get(ret).getOpcode() != Opcode.RETURN_OBJECT) {
            throw new IllegalStateException("friend list does not return an object");
        }
        instructions.add(ret, new ImmutableInstruction35c(
                Opcode.INVOKE_STATIC,
                1, 0, 0, 0, 0, 0,
                new ImmutableMethodReference(
                        "Lrip/moth/cocoonshell/froglog/FroglogSocial;",
                        "withFollows",
                        Collections.singletonList("Ljava/util/List;"),
                        "Ljava/util/List;")));
        instructions.add(ret + 1, new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
        System.out.println("friend list instructions " + instructions.size());
        return replace(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(),
                instructions,
                Collections.emptyList(),
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
