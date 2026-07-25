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
}
