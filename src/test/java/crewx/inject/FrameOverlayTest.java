package crewx.inject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
public class FrameOverlayTest {
    @Before public void reset(){FrameOverlay.reset();}
    @Test public void hudDrawsAreDeferredAndOnlyOneFrameDrawIsAllowed(){
        FrameOverlay.beginFrame(true);assertTrue(FrameOverlay.deferNormalDraw());FrameOverlay.worldReady();
        assertTrue(FrameOverlay.beginDraw());assertFalse(FrameOverlay.deferNormalDraw());assertFalse(FrameOverlay.beginDraw());
        FrameOverlay.endDraw();assertTrue(FrameOverlay.deferNormalDraw());assertFalse(FrameOverlay.beginDraw());
    }
    @Test public void cachedHudRateDoesNotLimitTheNextWorldFrame(){
        for(int i=0;i<120;i++){FrameOverlay.beginFrame(true);FrameOverlay.worldReady();assertTrue(FrameOverlay.beginDraw());FrameOverlay.endDraw();}
    }
    @Test public void oldWorldProjectionsAreNotDrawnInANewFrame(){
        FrameOverlay.beginFrame(true);FrameOverlay.worldReady();assertTrue(FrameOverlay.beginDraw());FrameOverlay.endDraw();
        FrameOverlay.beginFrame(true);assertFalse(FrameOverlay.beginDraw());FrameOverlay.worldReady();assertTrue(FrameOverlay.beginDraw());
    }
    @Test public void menusAndUnloadKeepTheOriginalHudPath(){
        assertFalse(FrameOverlay.deferNormalDraw());FrameOverlay.beginFrame(false);FrameOverlay.worldReady();
        assertFalse(FrameOverlay.deferNormalDraw());assertFalse(FrameOverlay.beginDraw());FrameOverlay.reset();assertFalse(FrameOverlay.deferNormalDraw());
    }
}
