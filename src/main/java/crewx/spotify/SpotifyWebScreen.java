package crewx.spotify;

import crewx.CrewX;
import crewx.module.Module;
import crewx.inject.CrewXRuntimeHooks;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.montoyo.mcef.api.IBrowser;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.io.IOException;

public final class SpotifyWebScreen extends GuiScreen {
    private final GuiScreen parent; private final SpotifyService service;
    private IBrowser browser; private String url=""; private int pressedMask; private boolean focused; private final long openedAt=System.currentTimeMillis();
    private String browserProblem=""; private boolean missingTextureLogged;
    public SpotifyWebScreen(GuiScreen parent,SpotifyService service){this.parent=parent;this.service=service;}
    @Override public void initGui(){Keyboard.enableRepeatEvents(true);pressedMask=0;focused=false;try{url=SpotifyWebServer.start(service).url();}catch(IOException e){url="";browserProblem="Servidor local: "+e.getMessage();crewx.inject.CrewXBootstrap.log("Spotify local server failed: "+e);}McefRuntime.ensureStarted();ensureBrowser();}
    private void ensureBrowser(){if(browser!=null||url.isEmpty()||McefRuntime.api()==null||!browserProblem.isEmpty())return;try{browser=McefRuntime.api().createBrowser(url,true);browser.resize(mc.displayWidth,mc.displayHeight);}catch(Throwable e){browser=null;browserProblem="Navegador: "+e.getClass().getSimpleName();crewx.inject.CrewXBootstrap.log("Spotify browser creation failed: "+e);}}
    @Override public void updateScreen(){ensureBrowser();if(browser!=null){McefRuntime.pump(browser);try{browser.resize(mc.displayWidth,mc.displayHeight);}catch(Throwable ignored){}if(!focused&&browser.getTextureID()>0){McefRuntime.focus(browser,true);focused=true;}}}
    @Override public void drawScreen(int mouseX,int mouseY,float partial){if(browser!=null){McefRuntime.pump(browser);try{if(browser.getTextureID()>0){drawBrowser(browser);return;}if(!missingTextureLogged&&System.currentTimeMillis()-openedAt>5000L){missingTextureLogged=true;crewx.inject.CrewXBootstrap.log("Spotify browser has no texture after 5s; class="+browser.getClass().getName()+" loading="+browser.isPageLoading());}}catch(Throwable error){browserProblem="Desenho: "+error.getClass().getSimpleName();crewx.inject.CrewXBootstrap.log("Spotify browser drawing failed: "+error);}}drawDefaultBackground();drawCenteredString(fontRendererObj,"Spotify",width/2,height/2-18,0x1ED760);drawCenteredString(fontRendererObj,browserProblem.isEmpty()?McefRuntime.detail:browserProblem,width/2,height/2+2,0xD0D0D0);drawCenteredString(fontRendererObj,"ESC para fechar",width/2,height/2+20,0x808080);}
    private void drawBrowser(IBrowser b){GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(1,771,1,771);GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);GlStateManager.bindTexture(b.getTextureID());Tessellator t=Tessellator.getInstance();t.getWorldRenderer().begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);t.getWorldRenderer().pos(0,height,0).tex(0,1).endVertex();t.getWorldRenderer().pos(width,height,0).tex(1,1).endVertex();t.getWorldRenderer().pos(width,0,0).tex(1,0).endVertex();t.getWorldRenderer().pos(0,0,0).tex(0,0).endVertex();t.draw();GlStateManager.disableBlend();GlStateManager.enableDepth();}
    @Override public void handleInput() throws IOException {if(browser==null){super.handleInput();return;}while(Keyboard.next()){int k=Keyboard.getEventKey();char c=Keyboard.getEventCharacter();boolean p=Keyboard.getEventKeyState();if(p&&(k==Keyboard.KEY_ESCAPE||isCloseBind(k))){if(k!=Keyboard.KEY_ESCAPE)CrewXRuntimeHooks.consumeBindPress(k);mc.displayGuiScreen(parent);return;}int m=mods();if(p){browser.injectKeyPressedByKeyCode(k,c,m);if(c!=0)browser.injectKeyTyped(c,m);}else browser.injectKeyReleasedByKeyCode(k,c,m);}while(Mouse.next()){if(mc.displayWidth==0||mc.displayHeight==0)continue;int x=Mouse.getEventX(),y=mc.displayHeight-Mouse.getEventY(),bt=Mouse.getEventButton(),wheel=Mouse.getEventDWheel();boolean p=Mouse.getEventButtonState();if(p&&bt>=0&&isCloseBind(bt-100)){CrewXRuntimeHooks.consumeBindPress(bt-100);mc.displayGuiScreen(parent);return;}if(wheel!=0)browser.injectMouseWheel(x,y,mods()|pressedMask,1,wheel);else if(bt==-1)browser.injectMouseMove(x,y,mods()|pressedMask,false);else{int awt=bt==0?1:bt==1?3:bt==2?2:0;if(awt==0)continue;int mask=awt==1?0x400:awt==2?0x800:0x1000;if(p){McefRuntime.focus(browser,true);focused=true;pressedMask|=mask;}else pressedMask&=~mask;browser.injectMouseButton(x,y,mods()|pressedMask,awt,p,1);}}}
    private boolean isCloseBind(int key){if(System.currentTimeMillis()-openedAt<300L||CrewX.moduleManager==null)return false;Module module=CrewX.moduleManager.getModule("Spotify");return module!=null&&module.getKey()!=0&&module.getKey()==key;}
    @Override protected void keyTyped(char typedChar,int keyCode) throws IOException {if(keyCode==Keyboard.KEY_ESCAPE||isCloseBind(keyCode)){if(keyCode!=Keyboard.KEY_ESCAPE)CrewXRuntimeHooks.consumeBindPress(keyCode);mc.displayGuiScreen(parent);return;}super.keyTyped(typedChar,keyCode);}
    @Override protected void mouseClicked(int mouseX,int mouseY,int mouseButton) throws IOException {if(isCloseBind(mouseButton-100)){CrewXRuntimeHooks.consumeBindPress(mouseButton-100);mc.displayGuiScreen(parent);return;}super.mouseClicked(mouseX,mouseY,mouseButton);}
    private int mods(){int m=0;if(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)||Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))m|=0x40;if(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)||Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))m|=0x80;if(Keyboard.isKeyDown(Keyboard.KEY_LMENU)||Keyboard.isKeyDown(Keyboard.KEY_RMENU))m|=0x200;return m;}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);if(browser!=null)try{browser.close();}catch(Throwable ignored){}browser=null;SpotifyWebServer.stop();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
