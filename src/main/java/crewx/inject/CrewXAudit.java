package crewx.inject;

import crewx.CrewX;
import crewx.module.Module;
import crewx.event.EventTarget;
import crewx.events.KeyEvent;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public final class CrewXAudit {
    private static final boolean ENABLED = Boolean.getBoolean("crewx.audit");
    private static final Map<String, AtomicLong> events = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> calls = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> errors = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> runtime = new ConcurrentHashMap<>();
    private static final AtomicLong keys = new AtomicLong();
    private CrewXAudit() {}
    private static long increment(Map<String, AtomicLong> map, String name) {
        return map.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
    }
    private static String id(Object source, Method method) {
        return source.getClass().getName() + "#" + method.getName() + "(" + method.getParameterTypes()[0].getSimpleName() + ")";
    }
    public static void event(Object event) {
        try {
            increment(events, event.getClass().getSimpleName());
            if (event instanceof KeyEvent && keys.incrementAndGet() <= 300) {
                int code = ((KeyEvent) event).getKey();
                StringBuilder text = new StringBuilder("AUDIT key=").append(code).append(" matches=");
                if (CrewX.moduleManager != null) for (Module module : CrewX.moduleManager.modules.values()) {
                    if (module.getKey() == code) text.append(module.getName()).append("[enabled=").append(module.isEnabled()).append("] ");
                }
                CrewXBootstrap.log(text.toString());
            }
        } catch (Throwable ignored) {}
    }
    public static void handler(Object source, Method method, Object event) {
        try { increment(calls, id(source, method)); } catch (Throwable ignored) {}
    }
    public static void failure(Object source, Method method, Throwable error) {
        try {
            String name = id(source, method);
            long count = increment(errors, name);
            if (count <= 3 || count % 1000 == 0) CrewXBootstrap.log("AUDIT ERROR " + name + " count=" + count + " " + CrewXBootstrap.describeFailure(error));
        } catch (Throwable ignored) {}
    }
    public static void mark(String name) {
        if (!ENABLED) return;
        try { increment(runtime, name); } catch (Throwable ignored) {}
    }
    public static void snapshot() {
        if (!ENABLED) return;
        try {
            CrewXBootstrap.log("AUDIT diagnostic-3 events=" + events + " handlers=" + calls + " errors=" + errors + " runtime=" + runtime);
            if (CrewX.moduleManager == null) return;
            for (Module module : CrewX.moduleManager.modules.values()) {
                StringBuilder row = new StringBuilder("AUDIT module=").append(module.getName()).append(" enabled=").append(module.isEnabled()).append(" bind=").append(module.getKey());
                for (Method method : module.getClass().getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(EventTarget.class) || method.getParameterTypes().length != 1) continue;
                    String name = id(module, method);
                    row.append(' ').append(method.getName()).append(':').append(method.getParameterTypes()[0].getSimpleName()).append(" calls=").append(calls.containsKey(name) ? calls.get(name).get() : 0).append(" errors=").append(errors.containsKey(name) ? errors.get(name).get() : 0);
                }
                CrewXBootstrap.log(row.toString());
            }
        } catch (Throwable error) { CrewXBootstrap.log("AUDIT snapshot failed: " + error); }
    }
}
