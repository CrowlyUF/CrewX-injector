package crewx.config;
import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConfigNameTest {
    private boolean valid(String name) throws Exception {Method m=Config.class.getDeclaredMethod("validName",String.class);m.setAccessible(true);return (Boolean)m.invoke(null,name);}
    @Test public void importedCopyNamesRemainLoadableAndRememberable() throws Exception {assertTrue(valid("kaizen (1)"));assertTrue(valid("default"));assertTrue(valid("kaizen-2"));}
    @Test public void namesCannotEscapeTheConfigDirectory() throws Exception {for(String n:new String[]{"../kaizen","..\\kaizen","C:\\kaizen","kaizen/1","kaizen:1","","a.json"})assertFalse(n,valid(n));assertFalse(valid(null));}
    @Test public void nameLengthRemainsBounded() throws Exception {assertTrue(valid(new String(new char[80]).replace('\0','a')));assertFalse(valid(new String(new char[81]).replace('\0','a')));}
}
