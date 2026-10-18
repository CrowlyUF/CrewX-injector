package crewx.inject;

import org.junit.Test;
import org.objectweb.asm.*;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.Assert.*;

public class Java23HookParityTest implements Opcodes {
    public static final class Recorder {
        public static final List<String> events = new ArrayList<>();
        public static void onUpdatePre(Object p) { events.add("pre"); }
        public static void onUpdatePost(Object p) { events.add("post"); }
        public static void onPlayerUpdate() { events.add("motion"); }
        public static void onLivingUpdate() { events.add("living"); }
        public static void onMoveInput() { events.add("input"); }
        public static boolean onSafeWalkCheck(Object p, boolean original) { events.add("safewalk"); return !original; }
        public static float adjustJumpYaw(Object p, float yaw) { events.add("jump"); return yaw + 2; }
        public static boolean isRidingDuringUpdate(Object p) throws Exception {
            events.add("apply"); return p.getClass().getField("riding").getBoolean(p);
        }
        public static void onUpdateApply(Object p) { events.add("apply"); }
        public static void mark(String value) { events.add(value); }
    }
    private static final String REC = Type.getInternalName(Recorder.class);
    private static final String BASE = "fixture/rec/Base", PLAYER = "fixture/rec/Player", INPUT = "fixture/rec/Input";

    @Test public void movementAndRotationUseTheJava23MixinOrder() throws Exception {
        Object p = player(); Recorder.events.clear();
        p.getClass().getMethod("onUpdate").invoke(p);
        assertEquals(Arrays.asList("pre", "input-body", "input", "living", "living-body", "apply", "motion", "walking", "post"), Recorder.events);
    }
    @Test public void ridingStillAppliesTheRotationAndSkipsWalkingEvent() throws Exception {
        Object p = player(); p.getClass().getField("riding").setBoolean(p, true); Recorder.events.clear();
        p.getClass().getMethod("onUpdate").invoke(p);
        assertEquals(Arrays.asList("pre", "input-body", "input", "living", "living-body", "apply", "post"), Recorder.events);
    }
    @Test public void clientWithoutRidingCallAppliesRotationBeforeWalkingUpdate() throws Exception {
        Object p = player(false); Recorder.events.clear();
        p.getClass().getMethod("onUpdate").invoke(p);
        assertEquals(Arrays.asList("pre", "input-body", "input", "living", "living-body", "apply", "motion", "walking", "post"), Recorder.events);
    }
    @Test public void theJvmVerifiesAndExecutesTheJumpYawStoreAdapter() throws Exception {
        String name = "fixture/rec/Living";
        ClassWriter w = type(name, "java/lang/Object");
        w.visitField(ACC_PUBLIC, "observed", "F", null, null).visitEnd();
        MethodVisitor m = method(w,"jump","()V");
        m.visitInsn(FCONST_1); m.visitVarInsn(FSTORE,1); m.visitVarInsn(ALOAD,0); m.visitVarInsn(FLOAD,1);
        m.visitFieldInsn(PUTFIELD,name,"observed","F"); done(m);
        m=method(w,"moveFlying","(FFF)V");done(m);
        m=method(w,"moveEntityWithHeading","(FF)V");m.visitVarInsn(ALOAD,0);
        m.visitInsn(FCONST_0);m.visitInsn(FCONST_0);m.visitInsn(FCONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL,name,"moveFlying","(FFF)V",false);done(m);w.visitEnd();
        Map<String,byte[]> definitions = new HashMap<>();definitions.put(name,adapt("transformEntityLivingBase",w.toByteArray()));
        Class<?> c=new FixtureLoader(definitions).loadClass(name.replace('/','.')); Object p=c.newInstance();Recorder.events.clear();
        c.getMethod("jump").invoke(p);assertEquals(3,c.getField("observed").getFloat(p),0);assertEquals(Arrays.asList("jump"),Recorder.events);
    }
    @Test public void safewalkCanEnableAndDisableTheVanillaFlagWithoutChangingTheMotionArguments() throws Exception {
        String name="fixture/rec/Entity"; ClassWriter w=type(name,"java/lang/Object");
        w.visitField(ACC_PUBLIC,"sneak","Z",null,null).visitEnd();w.visitField(ACC_PUBLIC,"observed","Z",null,null).visitEnd();
        MethodVisitor m=method(w,"moveEntity","(DDD)V");Label start=new Label(),end=new Label();m.visitLabel(start);
        m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,name,"sneak","Z");m.visitVarInsn(ISTORE,7);
        m.visitVarInsn(ALOAD,0);m.visitVarInsn(ILOAD,7);m.visitFieldInsn(PUTFIELD,name,"observed","Z");m.visitLabel(end);
        m.visitLocalVariable("flag","Z",null,start,end,7);done(m);w.visitEnd();
        Map<String,byte[]> defs=new HashMap<>();defs.put(name,adapt("transformEntity",w.toByteArray()));
        Class<?> c=new FixtureLoader(defs).loadClass(name.replace('/','.'));Object p=c.newInstance();Recorder.events.clear();
        c.getMethod("moveEntity",double.class,double.class,double.class).invoke(p,1D,0D,1D);
        assertTrue(c.getField("observed").getBoolean(p));c.getField("sneak").setBoolean(p,true);
        c.getMethod("moveEntity",double.class,double.class,double.class).invoke(p,1D,0D,1D);
        assertFalse(c.getField("observed").getBoolean(p));assertEquals(Arrays.asList("safewalk","safewalk"),Recorder.events);
    }

    private Object player() throws Exception { return player(true); }
    private Object player(boolean ridingCall) throws Exception {
        Map<String,byte[]> defs = new HashMap<>();
        ClassWriter w=type(BASE,"java/lang/Object");
        MethodVisitor m=method(w,"onUpdate","()V");m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKEVIRTUAL,BASE,"onLivingUpdate","()V",false);done(m);
        m=method(w,"onLivingUpdate","()V");mark(m,"living-body");done(m);w.visitEnd();defs.put(BASE,w.toByteArray());
        w=type(INPUT,"java/lang/Object");m=method(w,"updatePlayerMoveState","()V");mark(m,"input-body");done(m);w.visitEnd();defs.put(INPUT,w.toByteArray());
        w=type(PLAYER,BASE);w.visitField(ACC_PUBLIC,"riding","Z",null,null).visitEnd();
        m=method(w,"isRiding","()Z");m.visitVarInsn(ALOAD,0);m.visitFieldInsn(GETFIELD,PLAYER,"riding","Z");m.visitInsn(IRETURN);m.visitMaxs(0,0);m.visitEnd();
        m=method(w,"onUpdateWalkingPlayer","()V");mark(m,"walking");done(m);
        m=method(w,"onLivingUpdate","()V");m.visitTypeInsn(NEW,INPUT);m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,INPUT,"<init>","()V",false);
        m.visitMethodInsn(INVOKEVIRTUAL,INPUT,"updatePlayerMoveState","()V",false);m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESPECIAL,BASE,"onLivingUpdate","()V",false);done(m);
        m=method(w,"onUpdate","()V");m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESPECIAL,BASE,"onUpdate","()V",false);
        Label end=new Label();
        if(ridingCall){m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKEVIRTUAL,PLAYER,"isRiding","()Z",false);m.visitJumpInsn(IFNE,end);}
        m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKEVIRTUAL,PLAYER,"onUpdateWalkingPlayer","()V",false);m.visitLabel(end);done(m);w.visitEnd();
        defs.put(PLAYER,adapt("transformEntityPlayerSP",w.toByteArray()));return new FixtureLoader(defs).loadClass(PLAYER.replace('/','.')).newInstance();
    }
    private byte[] adapt(String method,byte[] bytes) throws Exception {
        Method transform=CrewXRuntimeTransformer.class.getDeclaredMethod(method,byte[].class);transform.setAccessible(true);
        byte[] patched=(byte[])transform.invoke(null,(Object)bytes);assertNotNull(patched);
        ClassWriter writer=new ClassWriter(0);
        new ClassReader(patched).accept(new ClassVisitor(ASM5,writer){
            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return new MethodVisitor(ASM5,super.visitMethod(a,n,d,s,e)){
                @Override public void visitMethodInsn(int o,String owner,String name,String desc,boolean itf){super.visitMethodInsn(o,owner.equals("crewx/inject/CrewXRuntimeHooks")?REC:owner,name,desc,itf);}
            };}
        },0);return writer.toByteArray();
    }
    private static ClassWriter type(String name,String parent){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);w.visit(V1_8,ACC_PUBLIC,name,null,parent,null);
        MethodVisitor m=method(w,"<init>","()V");m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESPECIAL,parent,"<init>","()V",false);done(m);return w;}
    private static MethodVisitor method(ClassWriter w,String name,String desc){MethodVisitor m=w.visitMethod(ACC_PUBLIC,name,desc,null,null);m.visitCode();return m;}
    private static void done(MethodVisitor m){m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();}
    private static void mark(MethodVisitor m,String value){m.visitLdcInsn(value);m.visitMethodInsn(INVOKESTATIC,REC,"mark","(Ljava/lang/String;)V",false);}
    private static final class FixtureLoader extends ClassLoader {private final Map<String,byte[]> defs;FixtureLoader(Map<String,byte[]> d){super(Java23HookParityTest.class.getClassLoader());defs=d;}
        @Override protected Class<?> findClass(String n)throws ClassNotFoundException{byte[] b=defs.get(n.replace('.','/'));if(b==null)throw new ClassNotFoundException(n);return defineClass(n,b,0,b.length);}}
}
