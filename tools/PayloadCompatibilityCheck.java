import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/** Build gate for the optional Forge integration in all delivered payload mappings. */
public final class PayloadCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        for(String path:args) {
            int[] counts=new int[4];
            try(ZipFile zip=new ZipFile(path)) {
                for(String name:new String[]{"crewx/accountmanager/AccountManager.class","crewx/util/PlayerUtil.class","crewx/module/modules/render/Xray.class"}) {
                    ZipEntry entry=zip.getEntry(name);if(entry==null)throw new IllegalStateException("Missing "+name);
                    try(InputStream in=zip.getInputStream(entry)) {
                        new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                            @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return new MethodVisitor(Opcodes.ASM9){
                                @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){
                                    if(owner.equals("crewx/accountmanager/ForgeBinding")&&method.equals("register"))counts[3]++;
                                    if(owner.equals("net/minecraftforge/common/ForgeHooks"))counts[3]++;
                                    if(owner.equals("crewx/inject/ForgeCompat")){
                                        if(method.equals("registerAccountEvents"))counts[0]++;
                                        if(method.equals("allowPlayerAttack"))counts[1]++;
                                        if(method.equals("setLightPipeline"))counts[2]++;
                                    }
                                }
                                @Override public void visitFieldInsn(int op,String owner,String field,String desc){if(owner.equals("net/minecraftforge/common/ForgeModContainer"))counts[3]++;}
                            };}
                        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                    }
                }
            }
            if(counts[0]!=1||counts[1]<1||counts[2]<2||counts[3]!=0)throw new IllegalStateException("Forge compatibility checks failed: "+path+" "+java.util.Arrays.toString(counts));
            System.out.println("Forge compatibility payload verified: "+new File(path).getName());
        }
    }
}
