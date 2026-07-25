package vannon.syx.economy.adapter.seam;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Typed method accessors for private/package-private methods.
 *
 * <p>Each accessor holds either a {@link MethodHandle} (preferred, 3–5× faster)
 * or a fallback {@link Method} with {@code setAccessible(true)}. Resolution
 * happens once at construction time.</p>
 *
 * <p>Static methods are detected automatically: {@code findStatic} is tried
 * when {@code findVirtual} fails for instance methods.</p>
 *
 * <p><b>Total-failure contract:</b> If both MethodHandle and Reflection paths
 * fail, {@code invoke()} throws {@link IllegalStateException} with a clear
 * message — it never throws a wrapped NPE.</p>
 */
public final class MethodAccessor {
    private MethodAccessor() {}

    // ─── VoidMethod ───────────────────────────────────────────────

    public static final class VoidMethod {
        private final MethodHandle mh;
        private final Method method;
        private final boolean isStatic;

        VoidMethod(MethodHandle mh, Method method, boolean isStatic) {
            this.mh = mh; this.method = method; this.isStatic = isStatic;
        }

        public void invoke(Object instance, Object... args) {
            if (mh != null) {
                try {
                    if (isStatic) {
                        mh.invokeWithArguments(args);
                    } else {
                        Object[] allArgs = new Object[args.length + 1];
                        allArgs[0] = instance;
                        System.arraycopy(args, 0, allArgs, 1, args.length);
                        mh.invokeWithArguments(allArgs);
                    }
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            if (method != null) {
                try {
                    method.invoke(instance, args);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            throw new IllegalStateException(
                    "VoidMethod.invoke(): accessor was never resolved — gate isAvailable()=false was not checked");
        }
    }

    // ─── BooleanMethod ────────────────────────────────────────────

    public static final class BooleanMethod {
        private final MethodHandle mh;
        private final Method method;
        private final boolean isStatic;

        BooleanMethod(MethodHandle mh, Method method, boolean isStatic) {
            this.mh = mh; this.method = method; this.isStatic = isStatic;
        }

        public boolean invoke(Object instance, Object... args) {
            if (mh != null) {
                try {
                    if (isStatic) {
                        return (boolean) mh.invokeWithArguments(args);
                    } else {
                        Object[] allArgs = new Object[args.length + 1];
                        allArgs[0] = instance;
                        System.arraycopy(args, 0, allArgs, 1, args.length);
                        return (boolean) mh.invokeWithArguments(allArgs);
                    }
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
            if (method != null) {
                try {
                    return (boolean) method.invoke(instance, args);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            throw new IllegalStateException(
                    "BooleanMethod.invoke(): accessor was never resolved — gate isAvailable()=false was not checked");
        }
    }

    // ─── Hierarchy-walking Reflection helper ──────────────────────

    private static Method findMethodInHierarchy(Class<?> owner, String name,
                                                 Class<?>... argTypes)
            throws NoSuchMethodException {
        Class<?> current = owner;
        NoSuchMethodException last = null;
        while (current != null) {
            try {
                Method m = current.getDeclaredMethod(name, argTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                last = e;
                current = current.getSuperclass();
            }
        }
        throw last != null ? last : new NoSuchMethodException(name);
    }

    // ─── Factory methods (called by BypassGate) ───────────────────

    static VoidMethod createVoid(Class<?> owner, String name, Class<?>[] argTypes,
                                  MethodHandles.Lookup lookup, BypassGate gate) {
        // 1. Try instance MethodHandle
        try {
            MethodType mt = MethodType.methodType(void.class, argTypes);
            MethodHandle mh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVirtual(owner, name, mt);
            return new VoidMethod(mh, null, false);
        } catch (Throwable mhInstanceFailed) {
            // 2. Try static MethodHandle
            try {
                MethodType mt = MethodType.methodType(void.class, argTypes);
                MethodHandle mh = MethodHandles.privateLookupIn(owner, lookup)
                        .findStatic(owner, name, mt);
                return new VoidMethod(mh, null, true);
            } catch (Throwable mhStaticFailed) {
                // 3. Reflection fallback (walks hierarchy)
                try {
                    Method m = findMethodInHierarchy(owner, name, argTypes);
                    boolean isStatic = Modifier.isStatic(m.getModifiers());
                    return new VoidMethod(null, m, isStatic);
                } catch (Throwable refFailed) {
                    gate.markFailed(refFailed);
                    return new VoidMethod(null, null, false);
                }
            }
        }
    }

    static BooleanMethod createBool(Class<?> owner, String name, Class<?>[] argTypes,
                                     MethodHandles.Lookup lookup, BypassGate gate) {
        try {
            MethodType mt = MethodType.methodType(boolean.class, argTypes);
            MethodHandle mh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVirtual(owner, name, mt);
            return new BooleanMethod(mh, null, false);
        } catch (Throwable mhInstanceFailed) {
            try {
                MethodType mt = MethodType.methodType(boolean.class, argTypes);
                MethodHandle mh = MethodHandles.privateLookupIn(owner, lookup)
                        .findStatic(owner, name, mt);
                return new BooleanMethod(mh, null, true);
            } catch (Throwable mhStaticFailed) {
                try {
                    Method m = findMethodInHierarchy(owner, name, argTypes);
                    boolean isStatic = Modifier.isStatic(m.getModifiers());
                    return new BooleanMethod(null, m, isStatic);
                } catch (Throwable refFailed) {
                    gate.markFailed(refFailed);
                    return new BooleanMethod(null, null, false);
                }
            }
        }
    }
}
