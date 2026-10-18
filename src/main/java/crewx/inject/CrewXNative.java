package crewx.inject;







public final class CrewXNative {

    private CrewXNative() {
    }


    public static native byte[] gcb(Class<?> targetClass);


    public static native int scb(Class<?> targetClass, byte[] bytecode);

    /** Applies ASM from the JVMTI callback in one retransformation, as in InjectMyau. */
    public static native int trc(Class<?> targetClass, String logicalName);

    public static byte[] transformForInjection(Class<?> targetClass, String logicalName, byte[] original) {
        return CrewXBootstrap.transformForInjection(targetClass, logicalName, original);
    }


    public static native int mbl(Class<?> targetClass, String name, String descriptor);


    public static native boolean urq();
}
