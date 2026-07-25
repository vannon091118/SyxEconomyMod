package vannon.syx.economy.adapter.seam;

import java.lang.invoke.MethodHandles;
import vannon.syx.economy.core.EventLog;

/**
 * Central entry point for private-access bypasses.
 *
 * <p>Every BypassGate instance holds its own {@link MethodHandles.Lookup}
 * and provides typed factories for field accessors, method accessors, and
 * class resolvers. The Lookup is created in the unnamed module where the
 * caller lives — which is the same unnamed module as the engine — so
 * {@code privateLookupIn} works without {@code --add-opens}.</p>
 *
 * <p>All-or-nothing semantics: if ANY field or method fails to resolve,
 * {@link #isAvailable()} returns false. Adapters check this before
 * using any accessor.</p>
 */
public final class BypassGate {

    private final MethodHandles.Lookup lookup;
    private final String adapterName;
    private boolean initOk = true;
    private boolean initFailedLogged;

    /**
     * @param adapterName   human-readable name for EventLog entries
     * @param callerLookup  the {@link MethodHandles.Lookup} from the
     *                      adapter's own class, NOT from BypassGate.
     *                      The caller owns the lookup context;
     *                      BypassGate merely forwards it to accessors.
     */
    public BypassGate(String adapterName, MethodHandles.Lookup callerLookup) {
        this.adapterName = adapterName;
        this.lookup = callerLookup;
    }

    /** True if all registered fields/methods resolved successfully. */
    public boolean isAvailable() {
        return initOk;
    }

    // ─── Field factories ──────────────────────────────────────────

    public FieldAccessor.IntField intField(Class<?> owner, String name) {
        return FieldAccessor.createInt(owner, name, lookup, this);
    }

    public FieldAccessor.DoubleField doubleField(Class<?> owner, String name) {
        return FieldAccessor.createDouble(owner, name, lookup, this);
    }

    public FieldAccessor.FloatField floatField(Class<?> owner, String name) {
        return FieldAccessor.createFloat(owner, name, lookup, this);
    }

    public <T> FieldAccessor.RefField<T> refField(Class<?> owner, String name, Class<T> fieldType) {
        return FieldAccessor.createRef(owner, name, fieldType, lookup, this);
    }

    // ─── Method factories ─────────────────────────────────────────

    public MethodAccessor.VoidMethod voidMethod(Class<?> owner, String name,
                                                 Class<?>... argTypes) {
        return MethodAccessor.createVoid(owner, name, argTypes, lookup, this);
    }

    public MethodAccessor.BooleanMethod boolMethod(Class<?> owner, String name,
                                                    Class<?>... argTypes) {
        return MethodAccessor.createBool(owner, name, argTypes, lookup, this);
    }

    // ─── Class resolver factory ───────────────────────────────────

    public ClassResolver classResolver(ClassLoader gameClassLoader) {
        return new ClassResolver(gameClassLoader);
    }

    // ─── Internal: called by accessor constructors on failure ─────

    void markFailed(Throwable cause) {
        this.initOk = false;
        if (!initFailedLogged) {
            initFailedLogged = true;
            EventLog.log("SEAM", adapterName + ": init failed — "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }
}
