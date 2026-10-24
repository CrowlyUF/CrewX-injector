package crewx.spotify;

import net.minecraft.client.Minecraft;
import net.montoyo.mcef.MCEF;
import net.montoyo.mcef.api.API;
import net.montoyo.mcef.api.IBrowser;
import net.montoyo.mcef.client.ClientProxy;
import net.montoyo.mcef.remote.RemoteConfig;
import net.montoyo.mcef.utilities.IProgressListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

public final class McefRuntime {
    public enum State { IDLE, INITIALIZING, READY, FAILED }
    public static volatile State state=State.IDLE;
    public static volatile String detail="";
    public static volatile double progress=-1;
    private static ClientProxy proxy;
    private static Method focus;
    private static long pumpRetryAfter;
    private static boolean pumpFailureLogged;
    private McefRuntime() {}

    public static synchronized void ensureStarted() {
        if(state!=State.IDLE)return;
        state=State.INITIALIZING;detail="Preparando navegador do FDP...";
        Thread worker=new Thread(new Runnable(){@Override public void run(){
            try {
                configure();
                if(!hasNatives()) download();
                if(!hasNatives()) throw new IOException("O download do navegador MCEF nao foi concluido");
                detail="Iniciando navegador do FDP...";
                Minecraft.getMinecraft().addScheduledTask(new Runnable(){@Override public void run(){initOnGameThread();}});
            }catch(Throwable e){fail(e);}
        }},"CrewX MCEF Installer");worker.setDaemon(true);worker.start();
    }
    private static void configure(){boolean ok=hasNatives();MCEF.ENABLE_EXAMPLE=false;MCEF.SKIP_UPDATES=ok;MCEF.WARN_UPDATES=false;MCEF.USE_FORGE_SPLASH=false;MCEF.SECURE_MIRRORS_ONLY=true;MCEF.FORCE_MIRROR="https://montoyo.net/jcef";MCEF.CEF_ARGS=new String[]{"--disable-gpu"};ClientProxy.ROOT=Minecraft.getMinecraft().mcDataDir.getAbsolutePath().replace('\\','/');}
    private static boolean hasNatives(){for(String n:new String[]{"jcef.dll","libcef.dll","icudtl.dat","cef.pak","natives_blob.bin","snapshot_blob.bin"})if(!new File(Minecraft.getMinecraft().mcDataDir,n).isFile())return false;return true;}
    private static void download(){detail="Baixando navegador MCEF (aprox. 160 MB)...";File config=new File(Minecraft.getMinecraft().mcDataDir,"config");if(!config.exists())config.mkdirs();RemoteConfig remote=new RemoteConfig();remote.load();remote.updateFileListing(config,false);boolean ok=remote.downloadMissing(new IProgressListener(){public void onProgressed(double value){progress=value;detail="Baixando navegador MCEF: "+(int)value+"%";}public void onTaskChanged(String task){detail="Baixando MCEF: "+task;}public void onProgressEnd(){progress=100;detail="Download concluido. Iniciando...";}});remote.updateFileListing(config,true);if(!ok)throw new IllegalStateException("O servidor de download do MCEF nao respondeu");}
    private static void initOnGameThread(){try{configure();proxy=new ClientProxy();MCEF.PROXY=proxy;proxy.onInit();if(proxy.isVirtual()||proxy.getCefApp()==null)throw new IllegalStateException("MCEF iniciou em modo virtual");Runtime.getRuntime().addShutdownHook(new Thread(() -> {try{proxy.onShutdown();}catch(Throwable ignored){}},"CrewX MCEF Shutdown"));progress=-1;state=State.READY;detail="Navegador pronto";}catch(Throwable e){fail(e);}}
    private static void fail(Throwable e){state=State.FAILED;Throwable c=e;while(c.getCause()!=null)c=c.getCause();detail="Falha no MCEF: "+(c.getMessage()==null?c.toString():c.getMessage());}
    public static API api(){return state==State.READY?proxy:null;}
    public static void pump(IBrowser b){
        if(proxy==null||System.currentTimeMillis()<pumpRetryAfter)return;
        try{
            proxy.getCefApp().N_DoMessageLoopWork();
            Method m=b.getClass().getMethod("mcefUpdate");
            m.invoke(b);
        }catch(Throwable error){
            pumpRetryAfter=System.currentTimeMillis()+1000L;
            if(!pumpFailureLogged){
                pumpFailureLogged=true;
                crewx.inject.CrewXBootstrap.log("MCEF browser pump failed: "+error);
            }
        }
    }
    public static void focus(IBrowser b,boolean value){try{if(focus==null)focus=b.getClass().getMethod("setFocus",Boolean.TYPE);focus.invoke(b,value);}catch(Throwable ignored){}}
}
