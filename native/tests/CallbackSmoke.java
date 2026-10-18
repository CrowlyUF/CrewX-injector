import java.util.Arrays;
public class CallbackSmoke {
    static native int trc(Class<?> target, String logicalName);
    static native byte[] gcb(Class<?> target);
    static native int scb(Class<?> target, byte[] bytes);
    static int mode, calls;
    public static class Target { public int value() { return 1; } }
    public static byte[] transformForInjection(Class<?> target, String name, byte[] bytes) {
        calls++;
        if(mode==0)return null;
        if(mode==-1)throw new IllegalStateException("expected test exception");
        if(mode==-2)return new byte[]{0,1,2};
        byte[] result=bytes.clone(); int count=0;
        for(int i=0;i<result.length-1;i++)if(result[i]==4&&(result[i+1]&255)==172){
            result[i]=(byte)(3+mode);count++;
        }
        if(count!=1)throw new AssertionError("Unexpected target bytecode: "+count);
        return result;
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void run(){
        Target target=new Target();check(target.value()==1,"baseline");
        mode=0;check(trc(Target.class,"probe")!=0,"null callback status");check(target.value()==1,"null callback changed class");
        mode=-1;check(trc(Target.class,"probe")!=0,"exception callback status");check(target.value()==1,"exception changed class");
        mode=-2;check(trc(Target.class,"probe")!=0,"invalid bytecode status");check(target.value()==1,"invalid bytecode changed class");
        mode=2;calls=0;check(trc(Target.class,"probe")==0,"direct transformation failed");check(calls==1,"callback ran more than once");check(target.value()==2,"patch not applied");
        byte[] original=gcb(Target.class);check(original!=null,"original missing");check(target.value()==2,"patch lost on subsequent retransformation");
        mode=0;check(trc(Target.class,"probe")!=0,"null replacement status");check(target.value()==2,"failed replacement lost previous patch");
        mode=3;check(trc(Target.class,"probe")==0,"replacement failed");check(target.value()==3,"replacement not applied");
        check(scb(Target.class,original)==0,"restore failed");check(target.value()==1,"restore not applied");
        System.out.println("PASS: null, exception, invalid bytecode, single callback, persistence, failed replacement, replacement, restore");
    }
}
