package crewx.inject;







public final class CrewXNative {

    private CrewXNative() {
    }


    public static native byte[] gcb(Class<?> targetClass);


    public static native int scb(Class<?> targetClass, byte[] bytecode);


    public static native int mbl(Class<?> targetClass, String name, String descriptor);
}
