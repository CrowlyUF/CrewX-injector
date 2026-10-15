package crewx.property;

import crewx.module.Module;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class PropertyManager {
    public LinkedHashMap<Module, ArrayList<Property<?>>> properties = new LinkedHashMap<>();

    public Property<?> getProperty(Module module, String string) {
        ArrayList<Property<?>> list = properties.get(module);
        if (list == null) return null;
        for (Property<?> property : list) {
            if (property.getName().replace("-", "").equalsIgnoreCase(string.replace("-", ""))) {
                return property;
            }
        }
        return null;
    }
}