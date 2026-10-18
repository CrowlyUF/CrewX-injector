package crewx.inject;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ForgeCompatTest {
    @Test public void lunarStyleClasspathCanInitializeAccountsAndUseForgeFallbacks() throws Exception {
        ClassLoader isolated=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(name.startsWith("net.minecraftforge.")||name.equals("crewx.accountmanager.ForgeBinding"))throw new ClassNotFoundException(name);
                if(name.equals("crewx.inject.ForgeCompat")){
                    Class<?> c=findLoadedClass(name);if(c==null){
                        try(InputStream in=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                            byte[] bytes=out.toByteArray();c=defineClass(name,bytes,0,bytes.length);
                        }catch(IOException e){throw new ClassNotFoundException(name,e);}
                    }
                    if(resolve)resolveClass(c);return c;
                }
                return super.loadClass(name,resolve);
            }
        };
        Class<?> compat=isolated.loadClass("crewx.inject.ForgeCompat");assertEquals(false,compat.getMethod("isPresent").invoke(null));
        compat.getMethod("registerAccountEvents").invoke(null);
        compat.getMethod("setLightPipeline",boolean.class).invoke(null,false);
        assertEquals(true,compat.getMethod("allowPlayerAttack",net.minecraft.entity.player.EntityPlayer.class,net.minecraft.entity.Entity.class).invoke(null,new Object[]{null,null}));
    }
}
