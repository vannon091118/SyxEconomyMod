package vannon.syx.economy.adapter.seam;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * Typed method accessors for private/package-private methods.
 *
 * <p>Each accessor holds either a {@link MethodHandle} (preferred, 3–5× faster)
 * or a fallback {@link Method} with {@code setAccessible(true)}. Resolution
 * happens once at construction time.</p>
 */
public final class MethodAccessor {
    private MethodAccessor() {}

    // ─── VoidMethod ───────────────────────────────────────────────

    public static final class VoidMethod {
        private final MethodHandle mh;
        private final Method method;

        VoidMethod(MethodHandle mh, Method method) { this.mh = mh; this.method = method; }

        public void invoke(Object instance, Object... args) {
            if (mh != null) {
                try {
                    // Prepend instance as first arg for virtual calls
                    Object[] allArgs = new Object[args.length + 1];
                    allArgs[0] = instance;
                    System.arraycopy(args, 0, allArgs, 1, args.length);
                    mh.invokeWithArguments(allArgs);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            } else {
                try {
                    method.invoke(instance, args);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // ─── BooleanMethod ────────────────────────────────────────────

    public static final class BooleanMethod {
        private final MethodHandle mh;
        private final Method method;

        BooleanMethod(MethodHandle mh, Method method) { this.mh = mh; this.method = method; }

        public boolean invoke(Object instance, Object... args) {
            if (mh != null) {
                try {
                    Object[] allArgs = new Object[args.length + 1];
                    allArgs[0] = instance;
                    System.arraycopy(args, 0, allArgs, 1, args.length);
                    return (boolean) mh.invokeWithArguments(allArgs);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            } else {
                try {
                    return (boolean) method.invoke(instance, args);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // ─── Factory methods (called by BypassGate) ───────────────────

    static VoidMethod createVoid(Class<?> owner, String name, Class<?>[] argTypes,
                                  MethodHandles.Lookup lookup, BypassGate gate) {
        try {
            MethodType mt = MethodType.methodType(void.class, argTypes);
            MethodHandle mh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVirtual(owner, name, mt);
            return new VoidMethod(mh, null);
        } catch (Throwable mhFailed) {
            try {
                Method m = owner.getDeclaredMethod(name, argTypes);
                m.setAccessible(true);
                return new VoidMethod(null, m);
            } catch (Throwable refFailed) {
                gate.markFailed(refFailed);
                return new VoidMethod(null, null);
            }
        }
    }

    static BooleanMethod createBool(Class<?> owner, String name, Class<?>[] argTypes,
                                     MethodHandles.Lookup lookup, BypassGate gate) {
        try {
            MethodType mt = MethodType.methodType(boolean.class, argTypes);
            MethodHandle mh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVirtual(owner, name, mt);
            return new BooleanMethod(mh, null);
        } catch (Throwable mhFailed) {
            try {
                Method m = owner.getDeclaredMethod(name, argTypes);
                m.setAccessible(true);
                return new BooleanMethod(null, m);
            } catch (Throwable refFailed) {
                gate.markFailed(refFailed);
                return new BooleanMethod(null, null);
            }
        }
    }
}
