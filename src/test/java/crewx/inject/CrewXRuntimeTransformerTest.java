package crewx.inject;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CrewXRuntimeTransformerTest {
    @Test
    public void chamsHooksBracketModelRender() throws Exception {
        byte[] original;
        try (InputStream input = getClass().getResourceAsStream(
                "/net/minecraft/client/renderer/entity/RendererLivingEntity.class")) {
            assertNotNull(input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) output.write(buffer, 0, read);
            original = output.toByteArray();
        }
        byte[] transformed = CrewXRuntimeTransformer.transform(
                "net/minecraft/client/renderer/entity/RendererLivingEntity", original);
        assertNotNull(transformed);
        final int[] pre = {0};
        final int[] model = {0};
        final int[] post = {0};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"doRender".equals(name)
                        || !"(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V".equals(descriptor)) return null;
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String descriptor, boolean isInterface) {
                        if ("crewx/inject/CrewXRuntimeHooks".equals(owner)) {
                            if ("onRenderLivingPre".equals(method)) pre[0]++;
                            if ("onRenderLivingPost".equals(method)) post[0]++;
                        } else if ("renderModel".equals(method)) {
                            assertEquals(model[0] + 1, pre[0]);
                            model[0]++;
                        }
                    }
                };
            }
        }, 0);
        org.junit.Assert.assertTrue(model[0] > 0);
        assertEquals(model[0], post[0]);
    }

    @Test
    public void autoToolSilentHooksBothMiningEntryPoints() throws Exception {
        String resource = "/net/minecraft/client/multiplayer/PlayerControllerMP.class";
        byte[] original;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) output.write(buffer, 0, read);
            original = output.toByteArray();
        }
        byte[] transformed = CrewXRuntimeTransformer.transform(
                "net/minecraft/client/multiplayer/PlayerControllerMP", original);
        assertNotNull(transformed);
        final int[] starts = {0};
        final int[] ends = {0};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"clickBlock".equals(name) && !"onPlayerDamageBlock".equals(name)) return null;
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String descriptor, boolean isInterface) {
                        if (!"crewx/inject/CrewXRuntimeHooks".equals(owner)) return;
                        if ("beginSilentMining".equals(method)) starts[0]++;
                        if ("endSilentMining".equals(method)) ends[0]++;
                    }
                };
            }
        }, 0);
        assertEquals(2, starts[0]);

        org.junit.Assert.assertTrue(ends[0] >= 2);
    }
}
