package crewx.inject;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;









public final class CrewXRuntimeTransformer implements Opcodes {



    private static final int ASM9 = asmVisitorApi();

    private static int asmVisitorApi() {
        try {
            return Opcodes.class.getField("ASM9").getInt(null);
        } catch (ReflectiveOperationException ignored) {
            try {
                return Opcodes.class.getField("ASM7").getInt(null);
            } catch (ReflectiveOperationException ignoredAgain) {
                return Opcodes.ASM5;
            }
        }
    }

    private static final String HOOKS = "crewx/inject/CrewXRuntimeHooks";






    private static final java.util.Set<String> CLAIMED =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private static boolean claim(String className, String method) {
        boolean fresh = CLAIMED.add(className + "#" + method);
        if (!fresh) {
            CrewXBootstrap.log("transform: " + className + "." + method + " already hooked, skip");
        }
        return fresh;
    }

    private CrewXRuntimeTransformer() {
    }






    public static byte[] transform(String internalName, byte[] original) {
        byte[] patched;
        try {
            switch (internalName) {
                case "net/minecraft/client/Minecraft":
                    patched = transformMinecraft(original);
                    break;
                case "net/minecraft/client/gui/GuiIngame":
                    patched = transformGuiIngame(original);
                    break;
                case "net/minecraftforge/client/GuiIngameForge":
                    patched = transformGuiIngameForge(original);
                    break;
                case "net/minecraft/client/gui/GuiChat":
                    patched = transformGuiChat(original);
                    break;
                case "net/minecraft/client/gui/GuiScreen":
                    patched = transformGuiScreen(original);
                    break;
                case "net/minecraft/client/gui/GuiTextField":
                    patched = transformGuiTextField(original);
                    break;
                case "net/minecraft/client/gui/GuiNewChat":
                    patched = transformGuiNewChat(original);
                    break;
                case "net/minecraft/client/network/NetHandlerPlayClient":
                    patched = transformNetHandlerPlayClient(original);
                    break;
                case "net/minecraft/client/renderer/chunk/VisGraph":
                    patched = transformVisGraph(original);
                    break;
                case "net/minecraft/client/renderer/WorldRenderer":
                    patched = transformWorldRenderer(original);
                    break;
                case "net/minecraft/client/settings/KeyBinding":
                    patched = transformKeyBinding(original);
                    break;
                case "net/minecraft/item/ItemStack":
                    patched = transformItemStack(original);
                    break;
                case "net/minecraft/client/renderer/BlockModelRenderer":
                    patched = transformBlockModelRenderer(original);
                    break;
                case "net/minecraft/client/renderer/entity/RenderManager":
                    patched = transformRenderManager(original);
                    break;
                case "net/minecraft/client/gui/GuiMainMenu":
                    patched = transformGuiMainMenu(original);
                    break;
                case "net/minecraft/client/gui/GuiButton":
                    patched = transformGuiButton(original);
                    break;
                case "net/minecraft/client/renderer/EntityRenderer":
                    patched = transformEntityRenderer(original);
                    break;
                case "net/minecraft/client/renderer/ItemRenderer":
                    patched = transformItemRenderer(original);
                    break;
                case "net/minecraft/client/gui/FontRenderer":
                    patched = transformFontRenderer(original);
                    break;
                case "net/minecraft/client/entity/AbstractClientPlayer":
                    patched = transformAbstractClientPlayer(original);
                    break;
                case "net/minecraft/client/entity/EntityPlayerSP":
                    patched = transformEntityPlayerSP(original);
                    break;
                case "net/minecraft/entity/player/EntityPlayer":
                    patched = transformEntityPlayer(original);
                    break;
                case "net/minecraft/util/MovementInputFromOptions":
                    patched = transformMovementInput(original);
                    break;
                case "net/minecraft/entity/EntityLivingBase":
                    patched = transformEntityLivingBase(original);
                    break;
                case "net/minecraft/client/multiplayer/PlayerControllerMP":
                    patched = transformPlayerControllerMP(original);
                    break;
                case "net/minecraft/network/NetworkManager":
                    patched = transformNetworkManager(original);
                    break;
                case "net/minecraft/entity/Entity":
                    patched = transformEntity(original);
                    break;
                case "net/minecraft/client/renderer/entity/RendererLivingEntity":
                    patched = transformRendererLivingEntity(original);
                    break;
                case "net/minecraft/world/World":
                    patched = transformWorld(original);
                    break;
                case "net/minecraft/block/Block":
                case "net/minecraft/block/BlockBush":
                case "net/minecraft/block/BlockGrass":
                case "net/minecraft/block/BlockLadder":
                case "net/minecraft/block/BlockLeaves":
                case "net/minecraft/block/BlockPane":
                case "net/minecraft/block/BlockWeb":
                    patched = transformBlock(original);
                    break;
                case "net/minecraft/client/renderer/BlockRendererDispatcher":
                    patched = transformBlockRendererDispatcher(original);
                    break;
                case "crewx/module/modules/misc/BedNuker":
                    patched = transformBedNuker(original);
                    break;
                default:
                    return null;
            }
        } catch (Throwable error) {
            CrewXBootstrap.log("transform FAILED for " + internalName + ": " + error);
            return null;
        }
        if (patched != null && !claim(internalName, "applied")) {
            return null;
        }
        return patched;
    }





    private static byte[] rewrite(byte[] original, ClassVisitorFactory factory) {
        ClassReader reader = new ClassReader(original);






        ClassLoader loader = CrewXRuntimeTransformer.class.getClassLoader();
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        ClassWriter writer = new LunarClassWriter(reader, ClassWriter.COMPUTE_FRAMES, loader);
        reader.accept(factory.create(writer), ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }





    private static final class LunarClassWriter extends ClassWriter {
        private final ClassLoader loader;

        LunarClassWriter(ClassReader reader, int flags, ClassLoader loader) {
            super(reader, flags);
            this.loader = loader;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            if (type1.equals(type2)) {
                return type1;
            }
            if (isArray(type1) || isArray(type2)) {
                if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
                    return "java/lang/Object";
                }
                if (isArray(type1) && isArray(type2)
                        && arrayDimensions(type1) == arrayDimensions(type2)) {
                    String common = getCommonSuperClass(
                            arrayComponent(type1), arrayComponent(type2));
                    StringBuilder prefix = new StringBuilder();
                    for (int i = 0; i < arrayDimensions(type1); i++) {
                        prefix.append('[');
                    }
                    return prefix + "L" + common + ";";
                }
                return "java/lang/Object";
            }
            try {
                if (isInterface(type1) || isInterface(type2)) {
                    return "java/lang/Object";
                }
                java.util.Set<String> supers = new java.util.HashSet<String>();
                String current = type1;
                while (current != null) {
                    supers.add(current);
                    current = superNameOf(current);
                }
                current = type2;
                while (current != null) {
                    if (supers.contains(current)) {
                        return current;
                    }
                    current = superNameOf(current);
                }
            } catch (Throwable ignored) {

            }
            return "java/lang/Object";
        }

        private boolean isArray(String internalName) {
            return internalName.charAt(0) == '[';
        }

        private int arrayDimensions(String internalName) {
            int dimensions = 0;
            while (dimensions < internalName.length()
                    && internalName.charAt(dimensions) == '[') {
                dimensions++;
            }
            return dimensions;
        }

        private String arrayComponent(String internalName) {
            int dimensions = arrayDimensions(internalName);
            String rest = internalName.substring(dimensions);
            if (rest.charAt(0) == 'L') {
                return rest.substring(1, rest.length() - 1);
            }
            return rest;
        }

        private byte[] bytesOf(String internalName) throws java.io.IOException {
            String resource = internalName + ".class";
            ClassLoader lookup = loader != null ? loader
                    : Thread.currentThread().getContextClassLoader();
            java.io.InputStream input = lookup != null
                    ? lookup.getResourceAsStream(resource) : null;
            if (input == null) {
                input = ClassLoader.getSystemResourceAsStream(resource);
            }
            if (input == null) {
                if (NotchMappings.isNotch() && lookup != null) {
                    byte[] named = NotchMappings.namedResourceBytes(internalName, lookup);
                    if (named != null) return named;
                }
                throw new java.io.IOException("no bytes for " + internalName);
            }
            try {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = input.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                return buffer.toByteArray();
            } finally {
                try {
                    input.close();
                } catch (java.io.IOException ignored) {

                }
            }
        }

        private boolean isInterface(String internalName) throws java.io.IOException {
            return (new ClassReader(bytesOf(internalName)).getAccess()
                    & 0x0200  ) != 0;
        }

        private String superNameOf(String internalName) throws java.io.IOException {
            String superName = new ClassReader(bytesOf(internalName)).getSuperName();
            if (superName == null || "java/lang/Object".equals(internalName)) {
                return null;
            }
            return superName;
        }
    }

    private interface ClassVisitorFactory {
        ClassVisitor create(ClassVisitor output);
    }


    private abstract static class HeadPatch extends ClassVisitor {
        HeadPatch(ClassVisitor output) {
            super(ASM9, output);
        }

        abstract boolean matches(String name, String descriptor);

        abstract void emit(MethodVisitor out);

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (!matches(name, descriptor)) {
                return base;
            }
            return new MethodVisitor(ASM9, base) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    emit(mv);
                }
            };
        }
    }


    private abstract static class TailPatch extends ClassVisitor {
        TailPatch(ClassVisitor output) {
            super(ASM9, output);
        }

        abstract boolean matches(String name, String descriptor);

        abstract void emit(MethodVisitor out);

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (!matches(name, descriptor)) {
                return base;
            }
            return new MethodVisitor(ASM9, base) {
                @Override
                public void visitInsn(int opcode) {
                    if (opcode >= IRETURN && opcode <= RETURN) {
                        emit(mv);
                    }
                    super.visitInsn(opcode);
                }
            };
        }
    }





    private abstract static class GuardPatch extends ClassVisitor {
        GuardPatch(ClassVisitor output) {
            super(ASM9, output);
        }

        abstract boolean matches(String name, String descriptor);

        abstract void loadArguments(MethodVisitor out);

        abstract String hookName();

        abstract String hookDescriptor();

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (!matches(name, descriptor)) {
                return base;
            }
            final boolean isStatic = (access & ACC_STATIC) != 0;
            return new MethodVisitor(ASM9, base) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    loadArguments(mv);
                    mv.visitMethodInsn(INVOKESTATIC, HOOKS, hookName(), hookDescriptor(), false);
                    Label resume = new Label();
                    mv.visitJumpInsn(IFEQ, resume);
                    mv.visitInsn(RETURN);
                    mv.visitLabel(resume);
                }
            };
        }
    }

    private static void callHook(MethodVisitor out, String name, String descriptor) {
        out.visitMethodInsn(INVOKESTATIC, HOOKS, name, descriptor, false);
    }





    private static byte[] transformMinecraft(byte[] original) {
        final boolean[] tick = new boolean[1];
        final boolean[] click = new boolean[1];
        final boolean[] rightClick = new boolean[1];
        final boolean[] loadWorld = new boolean[1];
        final boolean[] hitBlock = new boolean[1];
        final boolean[] resize = new boolean[1];
        final int[] gameLoopTickCalls = new int[1];
        final int[] keyDispatchCalls = new int[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("runGameLoop".equals(name) && "()V".equals(descriptor)) {
                    return new MethodVisitor(ASM9, base) {
                        @Override public void visitCode() {
                            super.visitCode();
                            callHook(mv, "onGameLoopFrame", "()V");
                        }
                        @Override public void visitMethodInsn(int opcode, String owner,
                                String called, String desc, boolean isInterface) {
                            if ("net/minecraft/client/Minecraft".equals(owner)
                                    && "runTick".equals(called) && "()V".equals(desc)) {
                                gameLoopTickCalls[0]++;
                            }
                            super.visitMethodInsn(opcode, owner, called, desc, isInterface);
                        }
                    };
                }
                if ("runTick".equals(name) && "()V".equals(descriptor)) {
                    tick[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            callHook(mv, "onTickPre", "(Ljava/lang/Object;)V");
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode >= IRETURN && opcode <= RETURN) {
                                mv.visitVarInsn(ALOAD, 0);
                                callHook(mv, "onTickPost", "(Ljava/lang/Object;)V");
                            }
                            super.visitInsn(opcode);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {



                            if (opcode == INVOKESTATIC
                                    && "net/minecraft/client/settings/KeyBinding".equals(owner)
                                    && "setKeyBindState".equals(name)
                                    && "(IZ)V".equals(descriptor)) {
                                keyDispatchCalls[0]++;
                                super.visitMethodInsn(INVOKESTATIC, HOOKS, "onKeyDispatch",
                                        "(IZ)V", false);
                                return;
                            }
                            if (opcode == INVOKEVIRTUAL
                                    && "net/minecraft/entity/player/InventoryPlayer".equals(owner)
                                    && "changeCurrentItem".equals(name)
                                    && "(I)V".equals(descriptor)) {
                                super.visitMethodInsn(INVOKESTATIC, HOOKS, "onChangeCurrentItem",
                                        "(Ljava/lang/Object;I)V", false);
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }
                    };
                }
                if ("loadWorld".equals(name)
                        && "(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V"
                                .equals(descriptor)) {
                    loadWorld[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            callHook(mv, "onLoadWorld", "()V");
                        }
                    };
                }
                if ("sendClickBlockToController".equals(name) && "(Z)V".equals(descriptor)) {
                    hitBlock[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            callHook(mv, "onHitBlock", "()V");
                        }
                    };
                }
                if ("updateFramebufferSize".equals(name) && "()V".equals(descriptor)) {
                    resize[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            callHook(mv, "onResize", "()V");
                        }
                    };
                }
                if ("clickMouse".equals(name) && "()V".equals(descriptor)) {
                    click[0] = true;
                    return cancellableVoid(base, false);
                }
                if ("rightClickMouse".equals(name) && "()V".equals(descriptor)) {
                    rightClick[0] = true;
                    return cancellableVoid(base, true);
                }
                return base;
            }

            private MethodVisitor cancellableVoid(MethodVisitor base, final boolean right) {
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (!right) {
                            mv.visitVarInsn(ALOAD, 0);
                            callHook(mv, "onLeftClick", "(Ljava/lang/Object;)Z");
                        } else {
                            callHook(mv, "onRightClick", "()Z");
                        }
                        Label resume = new Label();
                        mv.visitJumpInsn(IFEQ, resume);
                        mv.visitInsn(RETURN);
                        mv.visitLabel(resume);



                        mv.visitFrame(F_SAME, 0, null, 0, null);
                    }
                };
            }
        });
        if (!tick[0]) {
            CrewXBootstrap.log("transform: runTick()V not found in Minecraft");
            return null;
        }
        CrewXBootstrap.log("transform: Minecraft runGameLoop calls runTick "
                + gameLoopTickCalls[0] + " time(s); key dispatch sites="
                + keyDispatchCalls[0]);
        if (!click[0] || !rightClick[0] || !loadWorld[0] || !hitBlock[0] || !resize[0]) {
            CrewXBootstrap.log("transform: Minecraft partial (click=" + click[0]
                    + " right=" + rightClick[0] + " load=" + loadWorld[0]
                    + " hit=" + hitBlock[0] + " resize=" + resize[0] + ")");
        }
        return current;
    }


    private static byte[] transformGuiIngame(byte[] original) {
        byte[] current = rewrite(original, output -> new TailPatch(output) {
            @Override
            boolean matches(String name, String descriptor) {
                return "renderGameOverlay".equals(name) && "(F)V".equals(descriptor);
            }

            @Override
            void emit(MethodVisitor out) {
                out.visitVarInsn(FLOAD, 1);
                callHook(out, "onRenderOverlay", "(F)V");
            }
        });
        current = rewrite(current, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                boolean tick = "updateTick".equals(name) && "()V".equals(descriptor);
                boolean scoreboard = "renderScoreboard".equals(name)
                        && "(Lnet/minecraft/scoreboard/ScoreObjective;Lnet/minecraft/client/gui/ScaledResolution;)V".equals(descriptor);
                if (!tick && !scoreboard) return base;
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (scoreboard) {
                            mv.visitVarInsn(ALOAD, 1);
                            callHook(mv, "onScoreboardBegin", "(Lnet/minecraft/scoreboard/ScoreObjective;)V");
                        }
                    }
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDesc, boolean isInterface) {
                        if (tick && opcode == INVOKEVIRTUAL
                                && "net/minecraft/entity/player/InventoryPlayer".equals(owner)
                                && "getCurrentItem".equals(methodName)
                                && "()Lnet/minecraft/item/ItemStack;".equals(methodDesc)) {
                            callHook(mv, "onHudCurrentItem",
                                    "(Lnet/minecraft/entity/player/InventoryPlayer;)Lnet/minecraft/item/ItemStack;");
                            return;
                        }
                        if (scoreboard && opcode == INVOKEVIRTUAL
                                && "net/minecraft/client/gui/FontRenderer".equals(owner)
                                && "drawString".equals(methodName)
                                && "(Ljava/lang/String;III)I".equals(methodDesc)) {
                            callHook(mv, "onScoreboardText",
                                    "(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)I");
                            return;
                        }
                        if (scoreboard && opcode == INVOKESTATIC && "drawRect".equals(methodName)
                                && "(IIIII)V".equals(methodDesc)
                                && owner.startsWith("net/minecraft/client/gui/")) {
                            callHook(mv, "onScoreboardBackground", "(IIIII)V");
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                    }
                };
            }
        });
        return current;
    }

    private static byte[] transformGuiIngameForge(byte[] original) {


        final boolean[] overlayFound = new boolean[1];
        byte[] current = rewrite(original, output -> new HeadPatch(output) {
            @Override
            boolean matches(String name, String descriptor) {
                boolean match = ("renderGameOverlay".equals(name)
                        || "func_175180_a".equals(name) || "a".equals(name))
                        && "(F)V".equals(descriptor);
                if (match) overlayFound[0] = true;
                return match;
            }

            @Override
            void emit(MethodVisitor out) {
                callHook(out, "onBeginOverlayFrame", "()V");
            }
        });
        current = rewrite(current, output -> new TailPatch(output) {
            @Override
            boolean matches(String name, String descriptor) {
                return ("renderGameOverlay".equals(name)
                        || "func_175180_a".equals(name) || "a".equals(name))
                        && "(F)V".equals(descriptor);
            }

            @Override
            void emit(MethodVisitor out) {
                out.visitVarInsn(FLOAD, 1);
                callHook(out, "onRenderOverlay", "(F)V");
            }
        });
        current = rewrite(current, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"renderExperience".equals(name)) return base;
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDesc) {
                        if (opcode == GETFIELD && "net/minecraft/client/entity/EntityPlayerSP".equals(owner)
                                && "experience".equals(fieldName) && "F".equals(fieldDesc)) {
                            callHook(mv, "onHudExperience",
                                    "(Lnet/minecraft/client/entity/EntityPlayerSP;)F");
                            return;
                        }
                        if (opcode == GETFIELD && "net/minecraft/client/entity/EntityPlayerSP".equals(owner)
                                && "experienceLevel".equals(fieldName) && "I".equals(fieldDesc)) {
                            callHook(mv, "onHudExperienceLevel",
                                    "(Lnet/minecraft/client/entity/EntityPlayerSP;)I");
                            return;
                        }
                        super.visitFieldInsn(opcode, owner, fieldName, fieldDesc);
                    }
                };
            }
        });
        if (!overlayFound[0]) {
            final StringBuilder methods = new StringBuilder();
            new ClassReader(original).accept(new ClassVisitor(ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name,
                        String descriptor, String signature, String[] exceptions) {
                    if ("(F)V".equals(descriptor)) methods.append(name).append(' ');
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            CrewXBootstrap.log("transform: Forge overlay method not found; float methods=" + methods);
            return null;
        }
        return current;
    }

    private static byte[] transformGuiChat(byte[] original) {
        byte[] current = rewrite(original, output -> new GuardPatch(output) {
            @Override boolean matches(String name, String descriptor) {
                return "mouseClicked".equals(name) && "(III)V".equals(descriptor);
            }
            @Override void loadArguments(MethodVisitor out) {
                out.visitVarInsn(ILOAD, 1);
                out.visitVarInsn(ILOAD, 2);
                out.visitVarInsn(ILOAD, 3);
            }
            @Override String hookName() { return "onChatMouseClicked"; }
            @Override String hookDescriptor() { return "(III)Z"; }
        });
        current = rewrite(current, output -> new TailPatch(output) {
            @Override boolean matches(String name, String descriptor) {
                return "drawScreen".equals(name) && "(IIF)V".equals(descriptor);
            }
            @Override void emit(MethodVisitor out) { callHook(out, "onChatDrawScreen", "()V"); }
        });
        current = rewrite(current, output -> new TailPatch(output) {
            @Override boolean matches(String name, String descriptor) {
                return "onGuiClosed".equals(name) && "()V".equals(descriptor);
            }
            @Override void emit(MethodVisitor out) { callHook(out, "onChatClosed", "()V"); }
        });
        return current;
    }

    private static byte[] transformGuiScreen(byte[] original) {
        byte[] current = rewrite(original, output -> new GuardPatch(output) {
            @Override boolean matches(String name, String descriptor) {
                return "mouseClickMove".equals(name) && "(IIIJ)V".equals(descriptor);
            }
            @Override void loadArguments(MethodVisitor out) {
                out.visitVarInsn(ILOAD, 1);
                out.visitVarInsn(ILOAD, 2);
            }
            @Override String hookName() { return "onChatMouseDragged"; }
            @Override String hookDescriptor() { return "(II)Z"; }
        });
        current = rewrite(current, output -> new GuardPatch(output) {
            @Override boolean matches(String name, String descriptor) {
                return "mouseReleased".equals(name) && "(III)V".equals(descriptor);
            }
            @Override void loadArguments(MethodVisitor out) { out.visitVarInsn(ILOAD, 3); }
            @Override String hookName() { return "onChatMouseReleased"; }
            @Override String hookDescriptor() { return "(I)Z"; }
        });
        current = rewrite(current, output -> new GuardPatch(output) {
            @Override boolean matches(String name, String descriptor) {
                return "drawDefaultBackground".equals(name) && "()V".equals(descriptor);
            }
            @Override void loadArguments(MethodVisitor out) { out.visitVarInsn(ALOAD, 0); }
            @Override String hookName() { return "onDefaultBackground"; }
            @Override String hookDescriptor() { return "(Ljava/lang/Object;)Z"; }
        });
        return current;
    }

    private static byte[] transformGuiNewChat(byte[] original) {
        final boolean[] found = new boolean[4];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"drawChat".equals(name) || !"(I)V".equals(descriptor)) return base;
                return new MethodVisitor(ASM9, base) {
                    private int translateOrdinal;
                    private int backgroundOrdinal;
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDesc, boolean isInterface) {
                        if (opcode == INVOKESTATIC && "net/minecraft/client/renderer/GlStateManager".equals(owner)
                                && "translate".equals(methodName) && "(FFF)V".equals(methodDesc)
                                && translateOrdinal++ == 0) {
                            found[0] = true;
                            callHook(mv, "onChatTranslate", "(FFF)V");
                            return;
                        }
                        if (opcode == INVOKESTATIC && "drawRect".equals(methodName)
                                && "(IIIII)V".equals(methodDesc)
                                && owner.startsWith("net/minecraft/client/gui/")
                                && backgroundOrdinal++ == 0) {
                            found[1] = true;
                            callHook(mv, "onChatLineBackground", "(IIIII)V");
                            return;
                        }
                        if (opcode == INVOKEVIRTUAL && "net/minecraft/client/gui/FontRenderer".equals(owner)
                                && "drawStringWithShadow".equals(methodName)
                                && "(Ljava/lang/String;FFI)I".equals(methodDesc)) {
                            found[2] = true;
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitVarInsn(ILOAD, 1);
                            callHook(mv, "onChatDrawString",
                                    "(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;FFI"
                                    + "Lnet/minecraft/client/gui/GuiNewChat;I)I");
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                        if (opcode == INVOKESTATIC && "net/minecraft/client/renderer/GlStateManager".equals(owner)
                                && "scale".equals(methodName) && "(FFF)V".equals(methodDesc)) {
                            found[3] = true;
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitVarInsn(ILOAD, 1);
                            callHook(mv, "onChatPanel", "(Lnet/minecraft/client/gui/GuiNewChat;I)V");
                        }
                    }
                };
            }
        });
        CrewXBootstrap.log("transform: GuiNewChat translate=" + found[0] + " background=" + found[1]
                + " text=" + found[2] + " panel=" + found[3]);
        return (found[0] || found[1] || found[2] || found[3]) ? current : null;
    }

    private static byte[] transformGuiTextField(byte[] original) {
        final boolean[] hooked = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"drawTextBox".equals(name) || !"()V".equals(descriptor)) return base;
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDesc, boolean isInterface) {
                        if (opcode == INVOKESTATIC && "drawRect".equals(methodName)
                                && "(IIIII)V".equals(methodDesc)
                                && owner.startsWith("net/minecraft/client/gui/")) {
                            hooked[0] = true;
                            callHook(mv, "onTextFieldDrawRect", "(IIIII)V");
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                    }
                };
            }
        });
        if (!hooked[0]) {
            CrewXBootstrap.log("transform: GuiTextField.drawTextBox drawRect not found");
            return null;
        }
        return current;
    }

    private static byte[] transformNetHandlerPlayClient(byte[] original) {
        return rewrite(original, output -> new GuardPatch(output) {
            @Override boolean matches(String name, String descriptor) {
                return "handleSpawnMob".equals(name)
                        && "(Lnet/minecraft/network/play/server/S0FPacketSpawnMob;)V".equals(descriptor);
            }
            @Override void loadArguments(MethodVisitor out) { out.visitVarInsn(ALOAD, 1); }
            @Override String hookName() { return "onSpawnMobPacket"; }
            @Override String hookDescriptor() { return "(Ljava/lang/Object;)Z"; }
        });
    }

    private static byte[] transformVisGraph(byte[] original) {
        final boolean[] discovered = new boolean[2];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                boolean markOpaque = "func_178606_a".equals(name) && descriptor.endsWith(")V");
                boolean visibility = "computeVisibility".equals(name)
                        && "()Lnet/minecraft/client/renderer/chunk/SetVisibility;".equals(descriptor);
                if (!markOpaque && !visibility) return base;
                discovered[markOpaque ? 0 : 1] = true;
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (markOpaque) {
                            callHook(mv, "shouldOverrideChunkVisibility", "()Z");
                            Label vanilla = new Label();
                            mv.visitJumpInsn(IFEQ, vanilla);
                            mv.visitInsn(RETURN);
                            mv.visitLabel(vanilla);
                        } else {
                            callHook(mv, "overrideChunkVisibility", "()Lnet/minecraft/client/renderer/chunk/SetVisibility;");
                            mv.visitInsn(DUP);
                            Label vanilla = new Label();
                            mv.visitJumpInsn(IFNULL, vanilla);
                            mv.visitInsn(ARETURN);
                            mv.visitLabel(vanilla);
                            mv.visitInsn(POP);
                        }
                    }
                };
            }
        });
        if (!discovered[0] || !discovered[1]) {
            CrewXBootstrap.log("transform: VisGraph partial (mark=" + discovered[0]
                    + " compute=" + discovered[1] + ")");
        }
        return (!discovered[0] && !discovered[1]) ? null : current;
    }

    private static byte[] transformWorldRenderer(byte[] original) {
        final boolean[] hooked = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"putColorMultiplier".equals(name)) return base;
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDesc, boolean isInterface) {
                        if (opcode == INVOKEVIRTUAL && "java/nio/IntBuffer".equals(owner)
                                && "put".equals(methodName)
                                && "(II)Ljava/nio/IntBuffer;".equals(methodDesc)) {
                            hooked[0] = true;
                            callHook(mv, "adjustWorldVertexColor", "(I)I");
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                    }
                };
            }
        });
        if (!hooked[0]) {
            CrewXBootstrap.log("transform: WorldRenderer.putColorMultiplier IntBuffer.put not found");
            return null;
        }
        return current;
    }

    private static byte[] transformKeyBinding(byte[] original) {
        final boolean[] hooked = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"isPressed".equals(name) || !"()Z".equals(descriptor)) return base;
                hooked[0] = true;
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == IRETURN) {
                            mv.visitVarInsn(ALOAD, 0);
                            callHook(mv, "onKeyBindingPressed", "(ZLjava/lang/Object;)Z");
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        });
        return hooked[0] ? current : null;
    }

    private static byte[] transformItemStack(byte[] original) {
        return rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"hasEffect".equals(name) || !"()Z".equals(descriptor)) return base;
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        callHook(mv, "suppressItemEffect", "()Z");
                        Label vanilla = new Label();
                        mv.visitJumpInsn(IFEQ, vanilla);
                        mv.visitInsn(ICONST_0);
                        mv.visitInsn(IRETURN);
                        mv.visitLabel(vanilla);
                    }
                };
            }
        });
    }

    private static byte[] transformBlockModelRenderer(byte[] original) {
        final boolean[] hooked = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"renderModel".equals(name) || !descriptor.equals(
                        "(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/resources/model/IBakedModel;"
                        + "Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;"
                        + "Lnet/minecraft/client/renderer/WorldRenderer;Z)Z")) return base;
                hooked[0] = true;
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        mv.visitVarInsn(ALOAD, 0);
                        for (int slot = 1; slot <= 5; slot++) mv.visitVarInsn(ALOAD, slot);
                        mv.visitVarInsn(ILOAD, 6);
                        callHook(mv, "onBlockRenderModel",
                                "(Ljava/lang/Object;Lnet/minecraft/world/IBlockAccess;"
                                + "Lnet/minecraft/client/resources/model/IBakedModel;"
                                + "Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;"
                                + "Lnet/minecraft/client/renderer/WorldRenderer;Z)Ljava/lang/Boolean;");
                        mv.visitInsn(DUP);
                        Label vanilla = new Label();
                        mv.visitJumpInsn(IFNULL, vanilla);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                        mv.visitInsn(IRETURN);
                        mv.visitLabel(vanilla);
                        mv.visitInsn(POP);
                    }
                };
            }
        });
        return hooked[0] ? current : null;
    }

    private static byte[] transformRenderManager(byte[] original) {
        final boolean[] hooked = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"renderEntityStatic".equals(name)
                        || !"(Lnet/minecraft/entity/Entity;FZ)Z".equals(descriptor)) return base;
                hooked[0] = true;
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        mv.visitVarInsn(ALOAD, 1);
                        callHook(mv, "onRenderManagerEntityPre", "(Ljava/lang/Object;)V");
                    }
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == IRETURN) {
                            mv.visitVarInsn(ALOAD, 1);
                            callHook(mv, "onRenderManagerEntityPost", "(Ljava/lang/Object;)V");
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        });
        return hooked[0] ? current : null;
    }





    private static byte[] transformEntityRenderer(byte[] original) {
        final boolean[] hooked = new boolean[1];
        final boolean[] pick = new boolean[1];
        final boolean[] ray = new boolean[1];
        final int[] d0Index = new int[]{-1};
        try {
            ClassReader probe = new ClassReader(original);
            probe.accept(new ClassVisitor(ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                    if (!"getMouseOver".equals(name) || !"(F)V".equals(descriptor)) {
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    }
                    return new MethodVisitor(ASM9,
                            super.visitMethod(access, name, descriptor, signature, exceptions)) {
                        @Override
                        public void visitLocalVariable(String varName, String varDesc,
                                                       String varSig, Label start, Label end,
                                                       int index) {
                            if ("d0".equals(varName) && "D".equals(varDesc)
                                    && d0Index[0] < 0) {
                                d0Index[0] = index;
                            }
                            super.visitLocalVariable(varName, varDesc, varSig, start, end, index);
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
        } catch (Throwable error) {
            CrewXBootstrap.log("transform: getMouseOver local scan FAILED: " + error);
        }
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("getMouseOver".equals(name) && "(F)V".equals(descriptor)) {
                    final int d0 = d0Index[0];
                    final int[] ldcCount = new int[1];
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            super.visitLdcInsn(value);
                            if (value instanceof Double && ((Double) value).doubleValue() == 3.0D) {
                                ldcCount[0]++;
                                if (ldcCount[0] == 2) {
                                    pick[0] = true;
                                    callHook(mv, "onPickRange", "(D)D");
                                }
                            }
                        }

                        @Override
                        public void visitVarInsn(int opcode, int var) {
                            super.visitVarInsn(opcode, var);
                            if (opcode == DSTORE && d0 >= 0 && var == d0) {
                                ray[0] = true;
                                mv.visitVarInsn(DLOAD, d0);
                                callHook(mv, "onRaytraceRange", "(D)D");
                                mv.visitVarInsn(DSTORE, d0);
                            }
                        }
                    };
                }
                if ("renderWorldPass".equals(name) && "(IFJ)V".equals(descriptor)) {


                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name,
                                                   String descriptor) {
                            if (opcode == GETFIELD
                                    && "net/minecraft/client/renderer/EntityRenderer"
                                            .equals(owner)
                                    && "renderHand".equals(name)
                                    && "Z".equals(descriptor)) {
                                hooked[0] = true;
                                mv.visitVarInsn(FLOAD, 2);
                                callHook(mv, "onRenderWorld", "(F)V");
                            }
                            super.visitFieldInsn(opcode, owner, name, descriptor);
                        }
                    };
                }
                if (("updateFogColor".equals(name)
                        || "setupFog".equals(name)
                        || "setupCameraTransform".equals(name))) {
                    return redirectPotionChecks(base);
                }
                if ("hurtCameraEffect".equals(name) && "(F)V".equals(descriptor)) {
                    return new MethodVisitor(ASM9, base) {
                        private boolean adjusted;
                        @Override
                        public void visitLdcInsn(Object value) {
                            super.visitLdcInsn(value);
                            if (!adjusted && value instanceof Float
                                    && ((Float) value).floatValue() == 14.0F) {
                                adjusted = true;
                                callHook(mv, "adjustHurtCameraConstant", "(F)F");
                            }
                        }
                    };
                }
                return base;
            }
        });
        if (!hooked[0]) {
            CrewXBootstrap.log("transform: renderHand read not found in EntityRenderer.renderWorldPass");
            return null;
        }
        current = rewrite(current, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("updateCameraAndRender".equals(name) && "(FJ)V".equals(descriptor)) {
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            callHook(mv, "onFreeLookFramePrepare", "()V");
                            callHook(mv, "onClickGuiFrameBegin", "()V");
                        }
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == RETURN) {
                                mv.visitVarInsn(FLOAD, 1);
                                callHook(mv, "onClickGuiRenderFallback", "(F)V");
                            }
                            super.visitInsn(opcode);
                        }
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDesc, boolean isInterface) {
                            if (opcode == INVOKEVIRTUAL
                                    && "net/minecraft/client/entity/EntityPlayerSP".equals(owner)
                                    && "setAngles".equals(methodName) && "(FF)V".equals(methodDesc)) {
                                callHook(mv, "onFreeLookMouse",
                                        "(Lnet/minecraft/client/entity/EntityPlayerSP;FF)V");
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                        }
                    };
                }
                if ("orientCamera".equals(name) && "(F)V".equals(descriptor)) {
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            callHook(mv, "onFreeLookCameraPre", "()V");
                        }
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == RETURN) callHook(mv, "onFreeLookCameraPost", "()V");
                            super.visitInsn(opcode);
                        }
                    };
                }
                return base;
            }
        });
        return current;
    }

    private static byte[] transformGuiMainMenu(byte[] original) {
        final boolean[] init = new boolean[1];
        final boolean[] action = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("initGui".equals(name) && "()V".equals(descriptor)) {
                    init[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == RETURN) {
                                mv.visitVarInsn(ALOAD, 0);
                                callHook(mv, "onMainMenuInit", "(Ljava/lang/Object;)V");
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                if ("actionPerformed".equals(name)
                        && "(Lnet/minecraft/client/gui/GuiButton;)V".equals(descriptor)) {
                    action[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitVarInsn(ALOAD, 1);
                            callHook(mv, "onMainMenuAction",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)Z");
                            Label vanilla = new Label();
                            mv.visitJumpInsn(IFEQ, vanilla);
                            mv.visitInsn(RETURN);
                            mv.visitLabel(vanilla);
                            mv.visitFrame(F_SAME, 0, null, 0, null);
                        }
                    };
                }
                return base;
            }
        });
        current = rewrite(current, output -> new GuardPatch(output) {
            @Override boolean matches(String name, String descriptor) {
                return "drawScreen".equals(name) && "(IIF)V".equals(descriptor);
            }
            @Override void loadArguments(MethodVisitor out) {
                out.visitVarInsn(ALOAD, 0);
                out.visitVarInsn(ILOAD, 1);
                out.visitVarInsn(ILOAD, 2);
            }
            @Override String hookName() { return "onMainMenuDraw"; }
            @Override String hookDescriptor() { return "(Ljava/lang/Object;II)Z"; }
        });
        if (!init[0] || !action[0]) {
            CrewXBootstrap.log("transform: GuiMainMenu partial (init=" + init[0]
                    + " action=" + action[0] + ")");
        }
        return (!init[0] && !action[0]) ? null : current;
    }

    private static byte[] transformGuiButton(byte[] original) {
        return rewrite(original, output -> new GuardPatch(output) {
            @Override boolean matches(String name, String descriptor) {
                return "drawButton".equals(name)
                        && "(Lnet/minecraft/client/Minecraft;II)V".equals(descriptor);
            }
            @Override void loadArguments(MethodVisitor out) {
                out.visitVarInsn(ALOAD, 0);
                out.visitVarInsn(ILOAD, 2);
                out.visitVarInsn(ILOAD, 3);
            }
            @Override String hookName() { return "onDrawButton"; }
            @Override String hookDescriptor() { return "(Ljava/lang/Object;II)Z"; }
        });
    }

    private static byte[] transformBedNuker(byte[] original) {
        final boolean[] fixed = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDesc, boolean isInterface) {
                        if (opcode == INVOKEVIRTUAL
                                && "net/minecraft/item/Item".equals(owner)
                                && "getDigSpeed".equals(methodName)
                                && "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/state/IBlockState;)F".equals(methodDesc)) {
                            fixed[0] = true;
                            mv.visitMethodInsn(INVOKEINTERFACE,
                                    "net/minecraft/block/state/IBlockState", "getBlock",
                                    "()Lnet/minecraft/block/Block;", true);
                            mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/item/Item",
                                    "getStrVsBlock",
                                    "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/Block;)F", false);
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                    }
                };
            }
        });
        if (!fixed[0]) CrewXBootstrap.log("transform: BedNuker getDigSpeed call not found");
        return fixed[0] ? current : null;
    }

    private static byte[] transformWorld(byte[] original) {
        final boolean[] fixed = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"handleMaterialAcceleration".equals(name)) return base;
                return new MethodVisitor(ASM9, base) {
                    @Override public void visitMethodInsn(int opcode, String owner, String methodName,
                            String methodDesc, boolean itf) {
                        if (opcode == INVOKEVIRTUAL && "isPushedByWater".equals(methodName)
                                && "()Z".equals(methodDesc)) {
                            fixed[0] = true;
                            super.visitMethodInsn(INVOKESTATIC, HOOKS, "isPushedByWater",
                                    "(Ljava/lang/Object;)Z", false);
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDesc, itf);
                    }
                };
            }
        });
        if (!fixed[0]) CrewXBootstrap.log("transform: Jesus water redirect not found");
        return fixed[0] ? current : null;
    }

    private static byte[] transformBlock(byte[] original) {
        final boolean[] side = new boolean[1], layer = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("shouldSideBeRendered".equals(name)
                        && descriptor.endsWith("Lnet/minecraft/util/EnumFacing;)Z")) {
                    side[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ALOAD, 2); mv.visitVarInsn(ALOAD, 3);
                            callHook(mv, "xrayForceSide", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z");
                            Label vanilla = new Label(); mv.visitJumpInsn(IFEQ, vanilla);
                            mv.visitInsn(ICONST_1); mv.visitInsn(IRETURN);
                            mv.visitLabel(vanilla); mv.visitFrame(F_SAME, 0, null, 0, null);
                        }
                    };
                }
                if ("getBlockLayer".equals(name) && "()Lnet/minecraft/util/EnumWorldBlockLayer;".equals(descriptor)) {
                    layer[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override public void visitCode() {
                            super.visitCode(); mv.visitVarInsn(ALOAD, 0);
                            callHook(mv, "xrayBlockLayer", "(Ljava/lang/Object;)Ljava/lang/Object;");
                            mv.visitInsn(DUP); Label vanilla = new Label(); mv.visitJumpInsn(IFNULL, vanilla);
                            mv.visitTypeInsn(CHECKCAST, "net/minecraft/util/EnumWorldBlockLayer"); mv.visitInsn(ARETURN);
                            mv.visitLabel(vanilla); mv.visitFrame(F_SAME1, 0, null, 1, new Object[]{"java/lang/Object"});
                            mv.visitInsn(POP);
                        }
                    };
                }
                return base;
            }
        });
        CrewXBootstrap.log("transform: Xray Block side=" + side[0] + " layer=" + layer[0]);
        return (side[0] || layer[0]) ? current : null;
    }

    private static byte[] transformBlockRendererDispatcher(byte[] original) {
        final boolean[] render = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("renderBlock".equals(name) && descriptor.startsWith("(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockPos;")) {
                    render[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override public void visitCode() {
                            super.visitCode(); mv.visitVarInsn(ALOAD, 1); mv.visitVarInsn(ALOAD, 2);
                            callHook(mv, "onBlockRendered", "(Ljava/lang/Object;Ljava/lang/Object;)V");
                        }
                    };
                }
                return base;
            }
        });
        if (!render[0]) CrewXBootstrap.log("transform: BedESP/Xray renderBlock not found");
        return render[0] ? current : null;
    }

    private static byte[] transformEntityPlayerSP(byte[] original) {
        final boolean[] walking = new boolean[1];
        final boolean[] update = new boolean[1];
        final int[] rotationApplySites = new int[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("onUpdateWalkingPlayer".equals(name) && "()V".equals(descriptor)) {
                    walking[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                        }
                    };
                }
                if ("onUpdate".equals(name) && "()V".equals(descriptor)) {
                    update[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            callHook(mv, "onUpdatePre", "(Ljava/lang/Object;)V");
                        }
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String calledName,
                                                    String calledDescriptor, boolean isInterface) {
                            if ("isRiding".equals(calledName) && "()Z".equals(calledDescriptor)) {
                                rotationApplySites[0]++;
                                mv.visitVarInsn(ALOAD, 0);
                                callHook(mv, "onUpdateApply", "(Ljava/lang/Object;)V");
                            }
                            if ("onUpdateWalkingPlayer".equals(calledName)
                                    && "()V".equals(calledDescriptor)) {
                                callHook(mv, "onPlayerUpdate", "()V");
                            }
                            super.visitMethodInsn(opcode, owner, calledName, calledDescriptor,
                                    isInterface);
                        }
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode >= IRETURN && opcode <= RETURN) {
                                mv.visitVarInsn(ALOAD, 0);
                                callHook(mv, "onUpdatePost", "(Ljava/lang/Object;)V");
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                if ("onLivingUpdate".equals(name) && "()V".equals(descriptor)) {
                    return redirectNoSlow(redirectPotionChecks(base));
                }
                return base;
            }
        });
        if (!walking[0] || !update[0]) {
            CrewXBootstrap.log("transform: EntityPlayerSP partial (walking=" + walking[0]
                    + " update=" + update[0] + ")");
            if (!walking[0] && !update[0]) {
                return null;
            }
        }
        CrewXBootstrap.log("transform: EntityPlayerSP rotation apply sites="
                + rotationApplySites[0]);
        return current;
    }

    private static MethodVisitor redirectPotionChecks(MethodVisitor base) {
        return new MethodVisitor(ASM9, base) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name,
                                        String descriptor, boolean isInterface) {
                if ((opcode == INVOKEVIRTUAL || opcode == INVOKEINTERFACE)
                        && "isPotionActive".equals(name)
                        && "(Lnet/minecraft/potion/Potion;)Z".equals(descriptor)
                        && ("net/minecraft/entity/EntityLivingBase".equals(owner)
                            || "net/minecraft/client/entity/EntityPlayerSP".equals(owner))) {
                    callHook(mv, "isPotionActiveForRender",
                            "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/potion/Potion;)Z");
                    return;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }

    private static MethodVisitor redirectNoSlow(MethodVisitor base) {
        return new MethodVisitor(ASM9, base) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name,
                                        String descriptor, boolean isInterface) {
                if (opcode == INVOKEVIRTUAL
                        && "net/minecraft/client/entity/EntityPlayerSP".equals(owner)
                        && "isUsingItem".equals(name) && "()Z".equals(descriptor)) {
                    callHook(mv, "isUsingItemForNoSlow",
                            "(Lnet/minecraft/client/entity/EntityPlayerSP;)Z");
                    return;
                }
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        };
    }

    private static byte[] transformEntityPlayer(byte[] original) {
        final boolean[] living = new boolean[1];
        final boolean[] attack = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("onLivingUpdate".equals(name) && "()V".equals(descriptor)) {
                    living[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            callHook(mv, "onLivingUpdate", "()V");
                        }
                    };
                }
                if ("attackTargetEntityWithCurrentItem".equals(name)
                        && "(Lnet/minecraft/entity/Entity;)V".equals(descriptor)) {
                    attack[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            super.visitLdcInsn(value);
                            if (value instanceof Double
                                    && ((Double) value).doubleValue() == 0.6D) {
                                callHook(mv, "adjustKeepSprintSlowdown", "(D)D");
                            }
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDesc, boolean isInterface) {
                            if (opcode == INVOKEVIRTUAL
                                    && "net/minecraft/entity/player/EntityPlayer".equals(owner)
                                    && "setSprinting".equals(methodName)
                                    && "(Z)V".equals(methodDesc)) {
                                callHook(mv, "setSprintingForKeepSprint",
                                        "(Lnet/minecraft/entity/player/EntityPlayer;Z)V");
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface);
                        }
                    };
                }
                return base;
            }
        });
        if (!living[0] || !attack[0]) {
            CrewXBootstrap.log("transform: EntityPlayer partial (living=" + living[0]
                    + " attack=" + attack[0] + ")");
        }
        if (!living[0] && !attack[0]) {
            return null;
        }
        return current;
    }

    private static byte[] transformMovementInput(byte[] original) {
        byte[] current = rewrite(original, output -> new TailPatch(output) {
            @Override
            boolean matches(String name, String descriptor) {
                return "updatePlayerMoveState".equals(name) && "()V".equals(descriptor);
            }

            @Override
            void emit(MethodVisitor out) {
                callHook(out, "onMoveInput", "()V");
            }
        });
        if (current.length == original.length) {
            CrewXBootstrap.log("transform: updatePlayerMoveState()V not found in MovementInput");
            return null;
        }
        return current;
    }

    private static byte[] transformEntityLivingBase(byte[] original) {
        final boolean[] redirected = new boolean[1];
        final boolean[] swingDuration = new boolean[1];
        final boolean[] jesusSpeed = new boolean[1];
        final StringBuilder seen = new StringBuilder();
        final int[] calls = new int[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("getArmSwingAnimationEnd".equals(name) && "()I".equals(descriptor)) {
                    swingDuration[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == IRETURN) {
                                mv.visitVarInsn(ALOAD, 0);
                                mv.visitInsn(SWAP);
                                callHook(mv, "adjustSwingDuration", "(Ljava/lang/Object;I)I");
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                if ("moveEntityWithHeading".equals(name) && "(FF)V".equals(descriptor)) {
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {
                            if (opcode == INVOKESTATIC
                                    && "net/minecraft/enchantment/EnchantmentHelper".equals(owner)
                                    && "getDepthStriderModifier".equals(name)
                                    && descriptor.endsWith(")I")) {
                                jesusSpeed[0] = true;
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                mv.visitVarInsn(ALOAD, 0);
                                callHook(mv, "adjustDepthStrider", "(ILjava/lang/Object;)I");
                                return;
                            }


                            if (opcode == INVOKEVIRTUAL && "moveFlying".equals(name)
                                    && "(FFF)V".equals(descriptor)) {
                                redirected[0] = true;
                                super.visitMethodInsn(INVOKESTATIC, HOOKS, "onStrafeMove",
                                        "(Ljava/lang/Object;FFF)V", false);
                                return;
                            }
                            if (calls[0] < 12 && (opcode == INVOKEVIRTUAL
                                    || opcode == INVOKESPECIAL || opcode == INVOKESTATIC)) {
                                calls[0]++;
                                seen.append(owner).append('.').append(name)
                                        .append(descriptor).append(' ');
                            }
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }
                    };
                }
                return base;
            }
        });
        if (!redirected[0]) {
            CrewXBootstrap.log("transform: moveFlying redirect not found in EntityLivingBase"
                    + "; calls=" + seen);
            return null;
        }
        if (!swingDuration[0]) {
            CrewXBootstrap.log("transform: getArmSwingAnimationEnd()I not found in EntityLivingBase");
        }
        if (!jesusSpeed[0]) {
            CrewXBootstrap.log("transform: Jesus depth-strider speed hook not found");
        }
        return current;
    }

    private static byte[] transformPlayerControllerMP(byte[] original) {
        final boolean[] attack = new boolean[1];
        final boolean[] windowClick = new boolean[1];
        final boolean[] stoppedUsing = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("attackEntity".equals(name)
                        && "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V"
                                .equals(descriptor)) {
                    attack[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 2);
                            callHook(mv, "onAttack", "(Ljava/lang/Object;)V");
                        }
                    };
                }
                if ("windowClick".equals(name)
                        && "(IIIILnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;"
                                .equals(descriptor)) {
                    windowClick[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ILOAD, 1);
                            mv.visitVarInsn(ILOAD, 2);
                            mv.visitVarInsn(ILOAD, 3);
                            mv.visitVarInsn(ILOAD, 4);
                            callHook(mv, "onWindowClick", "(IIII)Z");
                            Label resume = new Label();
                            mv.visitJumpInsn(IFEQ, resume);
                            mv.visitInsn(ACONST_NULL);
                            mv.visitInsn(ARETURN);
                            mv.visitLabel(resume);
                            mv.visitFrame(F_SAME, 0, null, 0, null);
                        }
                    };
                }
                if ("onStoppedUsingItem".equals(name)
                        && "(Lnet/minecraft/entity/player/EntityPlayer;)V".equals(descriptor)) {
                    stoppedUsing[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            callHook(mv, "onStoppedUsing", "()Z");
                            Label resume = new Label();
                            mv.visitJumpInsn(IFEQ, resume);
                            mv.visitInsn(RETURN);
                            mv.visitLabel(resume);
                            mv.visitFrame(F_SAME, 0, null, 0, null);
                        }
                    };
                }
                return base;
            }
        });
        if (!attack[0] || !windowClick[0] || !stoppedUsing[0]) {
            CrewXBootstrap.log("transform: PlayerControllerMP partial (attack=" + attack[0]
                    + " window=" + windowClick[0] + " stopped=" + stoppedUsing[0] + ")");
            if (!attack[0] && !windowClick[0] && !stoppedUsing[0]) {
                return null;
            }
        }
        return current;
    }

    private static byte[] transformNetworkManager(byte[] original) {
        final boolean[] read = new boolean[1];
        final boolean[] send1 = new boolean[1];
        final boolean[] send2 = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("channelRead0".equals(name)
                        && "(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V"
                                .equals(descriptor)) {
                    read[0] = true;
                    return guardPacket(base, 2, "onPacketReceive");
                }
                if ("sendPacket".equals(name)
                        && "(Lnet/minecraft/network/Packet;)V".equals(descriptor)) {
                    send1[0] = true;
                    return guardPacket(base, 1, "onPacketSend");
                }
                if ("sendPacket".equals(name) && descriptor.startsWith(
                        "(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;")) {
                    send2[0] = true;
                    return guardPacket(base, 1, "onPacketSend2");
                }
                return base;
            }

            private MethodVisitor guardPacket(MethodVisitor base, final int slot,
                                              final String hook) {
                return new MethodVisitor(ASM9, base) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitVarInsn(ALOAD, slot);

                        mv.visitTypeInsn(CHECKCAST, "java/lang/Object");
                        callHook(mv, hook, "(Ljava/lang/Object;Ljava/lang/Object;)Z");
                        Label resume = new Label();
                        mv.visitJumpInsn(IFEQ, resume);
                        mv.visitInsn(RETURN);
                        mv.visitLabel(resume);
                        mv.visitFrame(F_SAME, 0, null, 0, null);
                    }
                };
            }
        });
        if (!read[0] || !send1[0] || !send2[0]) {
            CrewXBootstrap.log("transform: NetworkManager partial (read=" + read[0]
                    + " send1=" + send1[0] + " send2=" + send2[0] + ")");
            if (!read[0] && !send1[0] && !send2[0]) {
                return null;
            }
        }
        return current;
    }

    private static byte[] transformEntity(byte[] original) {
        final boolean[] velocity = new boolean[1];
        final boolean[] safewalk = new boolean[1];
        final boolean[] chamsRange = new boolean[1];
        final int[] flagIndex = new int[]{-1};

        try {
            ClassReader probe = new ClassReader(original);
            probe.accept(new ClassVisitor(ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!"moveEntity".equals(name) || !"(DDD)V".equals(descriptor)) {
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    }
                    return new MethodVisitor(ASM9,
                            super.visitMethod(access, name, descriptor, signature, exceptions)) {
                        @Override
                        public void visitLocalVariable(String varName, String varDesc,
                                                       String varSig, Label start, Label end,
                                                       int index) {
                            if ("flag".equals(varName) && "Z".equals(varDesc)
                                    && flagIndex[0] < 0) {
                                flagIndex[0] = index;
                            }
                            super.visitLocalVariable(varName, varDesc, varSig, start, end, index);
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
        } catch (Throwable error) {
            CrewXBootstrap.log("transform: Entity local scan FAILED: " + error);
        }
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("isInRangeToRenderDist".equals(name) && "(D)Z".equals(descriptor)) {
                    chamsRange[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitVarInsn(DLOAD, 1);
                            callHook(mv, "forceChamsRenderRange", "(Ljava/lang/Object;D)Z");
                            Label originalRange = new Label();
                            mv.visitJumpInsn(IFEQ, originalRange);
                            mv.visitInsn(ICONST_1);
                            mv.visitInsn(IRETURN);
                            mv.visitLabel(originalRange);
                            mv.visitFrame(F_SAME, 0, null, 0, null);
                        }
                    };
                }
                if ("setVelocity".equals(name) && "(DDD)V".equals(descriptor)) {
                    velocity[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitVarInsn(DLOAD, 1);
                            mv.visitVarInsn(DLOAD, 3);
                            mv.visitVarInsn(DLOAD, 5);
                            callHook(mv, "onKnockbackVelocity",
                                    "(Ljava/lang/Object;DDD)Z");
                            Label resume = new Label();
                            mv.visitJumpInsn(IFEQ, resume);
                            mv.visitInsn(RETURN);
                            mv.visitLabel(resume);
                            mv.visitFrame(F_SAME, 0, null, 0, null);
                        }
                    };
                }
                if ("moveEntity".equals(name) && "(DDD)V".equals(descriptor)) {
                    safewalk[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitVarInsn(DLOAD, 1);
                            mv.visitVarInsn(DLOAD, 3);
                            mv.visitVarInsn(DLOAD, 5);
                            callHook(mv, "onSafeWalkMotion", "(Ljava/lang/Object;DDD)[D");
                            mv.visitInsn(DUP);
                            mv.visitInsn(ICONST_0);
                            mv.visitInsn(DALOAD);
                            mv.visitVarInsn(DSTORE, 1);
                            mv.visitInsn(DUP);
                            mv.visitInsn(ICONST_1);
                            mv.visitInsn(DALOAD);
                            mv.visitVarInsn(DSTORE, 3);
                            mv.visitInsn(ICONST_2);
                            mv.visitInsn(DALOAD);
                            mv.visitVarInsn(DSTORE, 5);
                        }
                    };
                }
                return base;
            }
        });
        if (!velocity[0] || !safewalk[0]) {
            CrewXBootstrap.log("transform: Entity partial (velocity=" + velocity[0]
                    + " safewalk=" + safewalk[0] + " flagIdx=" + flagIndex[0] + ")");
            if (!velocity[0] && !safewalk[0]) {
                return null;
            }
        }
        if (!chamsRange[0]) {
            CrewXBootstrap.log("transform: Entity.isInRangeToRenderDist missing; Chams uses vanilla range");
        }
        return current;
    }

    private static byte[] transformItemRenderer(byte[] original) {
        final boolean[] hooked = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("renderItemInFirstPerson".equals(name) && "(F)V".equals(descriptor)) {
                    hooked[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitVarInsn(FLOAD, 1);
                            callHook(mv, "onRenderItemFirstPerson", "(Ljava/lang/Object;F)Z");
                            Label resume = new Label();
                            mv.visitJumpInsn(IFEQ, resume);
                            mv.visitInsn(RETURN);
                            mv.visitLabel(resume);
                            mv.visitFrame(F_SAME, 0, null, 0, null);
                        }
                    };
                }
                return base;
            }
        });
        if (!hooked[0]) {
            CrewXBootstrap.log("transform: renderItemInFirstPerson(F)V not found in ItemRenderer");
            return null;
        }
        return current;
    }

    private static byte[] transformFontRenderer(byte[] original) {
        final boolean[] hooked = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (("renderString".equals(name) || "getStringWidth".equals(name))
                        && descriptor.startsWith("(Ljava/lang/String;")) {
                    hooked[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 1);
                            callHook(mv, "processFontString", "(Ljava/lang/String;)Ljava/lang/String;");
                            mv.visitVarInsn(ASTORE, 1);
                        }
                    };
                }
                return base;
            }
        });
        if (!hooked[0]) {
            CrewXBootstrap.log("transform: FontRenderer string methods not found");
            return null;
        }
        return current;
    }

    private static byte[] transformAbstractClientPlayer(byte[] original) {
        final boolean[] hooked = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("getLocationCape".equals(name)
                        && "()Lnet/minecraft/util/ResourceLocation;".equals(descriptor)) {
                    hooked[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            callHook(mv, "getCustomCape",
                                    "(Ljava/lang/Object;)Lnet/minecraft/util/ResourceLocation;");
                            mv.visitInsn(DUP);
                            Label vanilla = new Label();
                            mv.visitJumpInsn(IFNULL, vanilla);
                            mv.visitInsn(ARETURN);
                            mv.visitLabel(vanilla);
                            mv.visitInsn(POP);
                            mv.visitFrame(F_SAME, 0, null, 0, null);
                        }
                    };
                }
                return base;
            }
        });
        if (!hooked[0]) {
            CrewXBootstrap.log("transform: getLocationCape not found in AbstractClientPlayer");
            return null;
        }
        return current;
    }

    private static byte[] transformRendererLivingEntity(byte[] original) {
        final boolean[] hooked = new boolean[1];
        final boolean[] names = new boolean[1];
        byte[] current = rewrite(original, output -> new ClassVisitor(ASM9, output) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ("canRenderName".equals(name)
                        && "(Lnet/minecraft/entity/EntityLivingBase;)Z".equals(descriptor)) {
                    names[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 1);
                            callHook(mv, "suppressVanillaName", "(Ljava/lang/Object;)Z");
                            Label originalName = new Label();
                            mv.visitJumpInsn(IFEQ, originalName);
                            mv.visitInsn(ICONST_0);
                            mv.visitInsn(IRETURN);
                            mv.visitLabel(originalName);
                        }
                    };
                }
                if ("doRender".equals(name)
                        && "(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V".equals(descriptor)
                        && (access & ACC_BRIDGE) == 0) {
                    hooked[0] = true;
                    return new MethodVisitor(ASM9, base) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            mv.visitVarInsn(ALOAD, 1);
                            callHook(mv, "onRenderLivingPre", "(Ljava/lang/Object;)V");
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode >= IRETURN && opcode <= RETURN) {
                                mv.visitVarInsn(ALOAD, 1);
                                callHook(mv, "onRenderLivingPost", "(Ljava/lang/Object;)V");
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                return base;
            }
        });
        if (!hooked[0]) {
            CrewXBootstrap.log("transform: doRender not found in RendererLivingEntity");
            return null;
        }
        if (!names[0]) CrewXBootstrap.log("transform: canRenderName not found in RendererLivingEntity");
        return current;
    }



    @SuppressWarnings("unused")
    private static Type erased(String descriptor) {
        return Type.getType(descriptor);
    }
}
