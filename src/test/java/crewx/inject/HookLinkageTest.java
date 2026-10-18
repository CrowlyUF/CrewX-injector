package crewx.inject;
import org.junit.Test;
import org.objectweb.asm.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

public class HookLinkageTest {
    @Test public void actualMinecraftTargetsProduceHooksWithResolvableCallbacks() throws Exception {
        Field f=CrewXBootstrap.class.getDeclaredField("RUNTIME_HOOK_TARGETS");f.setAccessible(true);
        Set<String> methods=new HashSet<>();
        for(Method m:CrewXRuntimeHooks.class.getMethods())if(Modifier.isStatic(m.getModifiers()))methods.add(m.getName()+org.objectweb.asm.Type.getMethodDescriptor(m));
        List<String> failures=new ArrayList<>();int transformed=0;
        CrewXRuntimeTransformer.resetAfterUnload();
        try {
            for(String name:(String[])f.get(null)){
                String resource=name.replace('.','/')+".class";
                InputStream input=getClass().getClassLoader().getResourceAsStream(resource);
                if(input==null){if(!name.startsWith("net.minecraftforge."))failures.add("missing class: "+name);continue;}
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                try{while((n=input.read(buffer))>=0)out.write(buffer,0,n);}finally{input.close();}
                byte[] result=CrewXRuntimeTransformer.transform(name.replace('.','/'),out.toByteArray());
                if(result==null){failures.add("no transformation: "+name);continue;}
                transformed++;
                new ClassReader(result).accept(new ClassVisitor(Opcodes.ASM5){
                    @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return new MethodVisitor(Opcodes.ASM5){
                        @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){
                            if(owner.equals("crewx/inject/CrewXRuntimeHooks")&&!methods.contains(method+desc))failures.add("unresolved: "+method+desc);
                        }
                    };}
                },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
        } finally {CrewXRuntimeTransformer.resetAfterUnload();}
        assertTrue(failures.toString(),failures.isEmpty());assertTrue("Only "+transformed+" classes transformed",transformed>=30);
    }
}
