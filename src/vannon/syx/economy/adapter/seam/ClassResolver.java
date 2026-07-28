package vannon.syx.economy.adapter.seam;

/**
 * Resolves package-private engine classes via the game {@link ClassLoader}.
 *
 * <p>The caller must provide the ClassLoader obtained from a public engine
 * class (e.g. {@code Humanoid.class.getClassLoader()}). This is the same
 * loader that the engine itself uses, making package-private classes
 * visible to {@link Class#forName(String, boolean, ClassLoader)}.</p>
 *
 * <p>Constructed via {@link BypassGate#classResolver(ClassLoader)}.</p>
 */
public final class ClassResolver {

    private final ClassLoader gameClassLoader;

    public ClassResolver(ClassLoader gameClassLoader) {
        this.gameClassLoader = gameClassLoader != null
                ? gameClassLoader
                : ClassLoader.getSystemClassLoader();
    }

    /**
     * Resolves a class that may be package-private in the engine.
     *
     * @param fqcn fully qualified class name
     * @return the resolved {@link Class}
     * @throws ClassNotFoundException if the class cannot be found
     */
    public Class<?> resolve(String fqcn) throws ClassNotFoundException {
        return Class.forName(fqcn, true, gameClassLoader);
    }

    /**
     * Checks whether an object is an instance of a package-private engine class.
     *
     * @param obj  the object to check (may be null)
     * @param fqcn fully qualified class name of the target type
     * @return true if {@code obj} is an instance of the resolved class
     */
    public boolean isInstance(Object obj, String fqcn) {
        if (obj == null) return false;
        try {
            return resolve(fqcn).isInstance(obj);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves a static field from a class.
     *
     * @param ownerClass the class owning the field
     * @param fieldName  the field name
     * @return the field value or null if not accessible
     */
    public Object getStaticField(Class<?> ownerClass, String fieldName) {
        try {
            java.lang.reflect.Field f = ownerClass.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Invokes a static or instance method on an object.
     * Walks the class hierarchy to find inherited methods.
     *
     * @param instance the target object (null for static methods)
     * @param methodName the method name
     * @param args method arguments
     * @return the method result or null
     */
    public Object invokeMethod(Object instance, String methodName, Object... args) {
        try {
            Class<?> clazz = instance != null ? instance.getClass() : null;
            if (clazz == null) return null;

            Class<?>[] argTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }

            // Walk the class hierarchy to find inherited methods
            java.lang.reflect.Method m = findMethodInHierarchy(clazz, methodName, argTypes);
            if (m == null) return null;
            m.setAccessible(true);
            return m.invoke(instance, args);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Walks the class hierarchy to find a declared method.
     * Unlike getDeclaredMethod, this checks superclasses.
     */
    private static java.lang.reflect.Method findMethodInHierarchy(
            Class<?> clazz, String methodName, Class<?>... argTypes) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, argTypes);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}