package vannon.syx.economy.adapter.seam;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Typed field accessors for private/package-private fields.
 *
 * <p>Every accessor holds either a {@link VarHandle} (preferred, 3–6× faster)
 * or a fallback {@link Field} with {@code setAccessible(true)}. The resolution
 * strategy is attempted once at construction time; {@code get}/{@code set}
 * calls incur no further reflection overhead.</p>
 *
 * <p>Static fields are detected automatically: {@code findStaticVarHandle}
 * is tried when {@code findVarHandle} fails for instance fields. The
 * {@link #isStatic} flag prevents accidental instance-method calls on
 * static VarHandles (and vice versa).</p>
 *
 * <p><b>Total-failure contract:</b> If both VarHandle and Reflection paths
 * fail, {@code get()}/{@code set()} throw {@link IllegalStateException}
 * with a clear message — they never silently return 0/null.</p>
 */
public final class FieldAccessor {
    private FieldAccessor() {}

    // ─── IntField ─────────────────────────────────────────────────

    public static final class IntField {
        private final VarHandle vh;
        private final Field field;
        final boolean isStatic;

        IntField(VarHandle vh, Field field, boolean isStatic) {
            this.vh = vh; this.field = field; this.isStatic = isStatic;
        }

        public int get(Object instance) {
            if (vh != null) {
                if (isStatic) return (int) vh.get();
                return (int) vh.get(instance);
            }
            if (field != null) return getReflective(instance);
            throw new IllegalStateException(
                    "IntField.get(): accessor was never resolved — gate isAvailable()=false was not checked");
        }

        public void set(Object instance, int value) {
            if (vh != null) {
                if (isStatic) vh.set(value);
                else vh.set(instance, value);
                return;
            }
            if (field != null) { setReflective(instance, value); return; }
            throw new IllegalStateException(
                    "IntField.set(): accessor was never resolved — gate isAvailable()=false was not checked");
        }

        public int getStatic() {
            if (vh != null) {
                if (!isStatic) throw new IllegalStateException(
                        "IntField.getStatic(): accessor was created for an INSTANCE field, not static");
                return (int) vh.get();
            }
            if (field != null) return getStaticReflective();
            throw new IllegalStateException(
                    "IntField.getStatic(): accessor was never resolved");
        }

        public void setStatic(int value) {
            if (vh != null) {
                if (!isStatic) throw new IllegalStateException(
                        "IntField.setStatic(): accessor was created for an INSTANCE field, not static");
                vh.set(value);
                return;
            }
            if (field != null) { setStaticReflective(value); return; }
            throw new IllegalStateException(
                    "IntField.setStatic(): accessor was never resolved");
        }

        private int getReflective(Object instance) {
            try { return field.getInt(instance); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        private void setReflective(Object instance, int value) {
            try { field.setInt(instance, value); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        private int getStaticReflective() {
            try { return field.getInt(null); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        private void setStaticReflective(int value) {
            try { field.setInt(null, value); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    // ─── DoubleField ──────────────────────────────────────────────

    public static final class DoubleField {
        private final VarHandle vh;
        private final Field field;
        final boolean isStatic;

        DoubleField(VarHandle vh, Field field, boolean isStatic) {
            this.vh = vh; this.field = field; this.isStatic = isStatic;
        }

        public double get(Object instance) {
            if (vh != null) {
                if (isStatic) return (double) vh.get();
                return (double) vh.get(instance);
            }
            if (field != null) return getReflective(instance);
            throw new IllegalStateException(
                    "DoubleField.get(): accessor was never resolved — gate isAvailable()=false was not checked");
        }

        public void set(Object instance, double value) {
            if (vh != null) {
                if (isStatic) vh.set(value);
                else vh.set(instance, value);
                return;
            }
            if (field != null) { setReflective(instance, value); return; }
            throw new IllegalStateException(
                    "DoubleField.set(): accessor was never resolved — gate isAvailable()=false was not checked");
        }

        public double getStatic() {
            if (vh != null) {
                if (!isStatic) throw new IllegalStateException(
                        "DoubleField.getStatic(): accessor was created for an INSTANCE field, not static");
                return (double) vh.get();
            }
            if (field != null) return getStaticReflective();
            throw new IllegalStateException(
                    "DoubleField.getStatic(): accessor was never resolved");
        }

        public void setStatic(double value) {
            if (vh != null) {
                if (!isStatic) throw new IllegalStateException(
                        "DoubleField.setStatic(): accessor was created for an INSTANCE field, not static");
                vh.set(value);
                return;
            }
            if (field != null) { setStaticReflective(value); return; }
            throw new IllegalStateException(
                    "DoubleField.setStatic(): accessor was never resolved");
        }

        private double getReflective(Object instance) {
            try { return field.getDouble(instance); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        private void setReflective(Object instance, double value) {
            try { field.setDouble(instance, value); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        private double getStaticReflective() {
            try { return field.getDouble(null); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        private void setStaticReflective(double value) {
            try { field.setDouble(null, value); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    // ─── FloatField ───────────────────────────────────────────────

    public static final class FloatField {
        private final VarHandle vh;
        private final Field field;
        final boolean isStatic;

        FloatField(VarHandle vh, Field field, boolean isStatic) {
            this.vh = vh; this.field = field; this.isStatic = isStatic;
        }

        public float get(Object instance) {
            if (vh != null) {
                if (isStatic) return (float) vh.get();
                return (float) vh.get(instance);
            }
            if (field != null) return getReflective(instance);
            throw new IllegalStateException(
                    "FloatField.get(): accessor was never resolved — gate isAvailable()=false was not checked");
        }

        public void set(Object instance, float value) {
            if (vh != null) {
                if (isStatic) vh.set(value);
                else vh.set(instance, value);
                return;
            }
            if (field != null) { setReflective(instance, value); return; }
            throw new IllegalStateException(
                    "FloatField.set(): accessor was never resolved — gate isAvailable()=false was not checked");
        }

        public float getStatic() {
            if (vh != null) {
                if (!isStatic) throw new IllegalStateException(
                        "FloatField.getStatic(): accessor was created for an INSTANCE field, not static");
                return (float) vh.get();
            }
            if (field != null) return getStaticReflective();
            throw new IllegalStateException(
                    "FloatField.getStatic(): accessor was never resolved");
        }

        public void setStatic(float value) {
            if (vh != null) {
                if (!isStatic) throw new IllegalStateException(
                        "FloatField.setStatic(): accessor was created for an INSTANCE field, not static");
                vh.set(value);
                return;
            }
            if (field != null) { setStaticReflective(value); return; }
            throw new IllegalStateException(
                    "FloatField.setStatic(): accessor was never resolved");
        }

        private float getReflective(Object instance) {
            try { return field.getFloat(instance); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        private void setReflective(Object instance, float value) {
            try { field.setFloat(instance, value); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        private float getStaticReflective() {
            try { return field.getFloat(null); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        private void setStaticReflective(float value) {
            try { field.setFloat(null, value); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    // ─── RefField<T> ──────────────────────────────────────────────

    public static final class RefField<T> {
        private final VarHandle vh;
        private final Field field;
        final boolean isStatic;

        RefField(VarHandle vh, Field field, boolean isStatic) {
            this.vh = vh; this.field = field; this.isStatic = isStatic;
        }

        @SuppressWarnings("unchecked")
        public T get(Object instance) {
            if (vh != null) {
                if (isStatic) return (T) vh.get();
                return (T) vh.get(instance);
            }
            if (field != null) return getReflective(instance);
            throw new IllegalStateException(
                    "RefField.get(): accessor was never resolved — gate isAvailable()=false was not checked");
        }

        public void set(Object instance, T value) {
            if (vh != null) {
                if (isStatic) vh.set(value);
                else vh.set(instance, value);
                return;
            }
            if (field != null) { setReflective(instance, value); return; }
            throw new IllegalStateException(
                    "RefField.set(): accessor was never resolved — gate isAvailable()=false was not checked");
        }

        @SuppressWarnings("unchecked")
        public T getStatic() {
            if (vh != null) {
                if (!isStatic) throw new IllegalStateException(
                        "RefField.getStatic(): accessor was created for an INSTANCE field, not static");
                return (T) vh.get();
            }
            if (field != null) return getStaticReflective();
            throw new IllegalStateException(
                    "RefField.getStatic(): accessor was never resolved");
        }

        public void setStatic(T value) {
            if (vh != null) {
                if (!isStatic) throw new IllegalStateException(
                        "RefField.setStatic(): accessor was created for an INSTANCE field, not static");
                vh.set(value);
                return;
            }
            if (field != null) { setStaticReflective(value); return; }
            throw new IllegalStateException(
                    "RefField.setStatic(): accessor was never resolved");
        }

        @SuppressWarnings("unchecked")
        private T getReflective(Object instance) {
            try { return (T) field.get(instance); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        private void setReflective(Object instance, T value) {
            try { field.set(instance, value); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        @SuppressWarnings("unchecked")
        private T getStaticReflective() {
            try { return (T) field.get(null); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
        private void setStaticReflective(T value) {
            try { field.set(null, value); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    // ─── Hierarchy-walking Reflection helper ──────────────────────

    /**
     * Walks the class hierarchy to find a declared field.
     * {@code getDeclaredField()} only finds fields declared directly on
     * the given class — if the caller passes a subclass of the declaring
     * class, we need to walk up.
     *
     * <p><b>Limitation:</b> uses {@code getSuperclass()} only, does not
     * search implemented interfaces.</p>
     */
    private static Field findFieldInHierarchy(Class<?> owner, String name)
            throws NoSuchFieldException {
        Class<?> current = owner;
        NoSuchFieldException last = null;
        while (current != null) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                last = e;
                current = current.getSuperclass();
            }
        }
        throw last != null ? last : new NoSuchFieldException(name);
    }

    // ─── Factory methods (called by BypassGate) ───────────────────

    static IntField createInt(Class<?> owner, String name,
                               MethodHandles.Lookup lookup, BypassGate gate) {
        // 1. Try instance VarHandle
        try {
            VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVarHandle(owner, name, int.class);
            return new IntField(vh, null, false);
        } catch (Throwable vhInstanceFailed) {
            // 2. Try static VarHandle
            try {
                VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                        .findStaticVarHandle(owner, name, int.class);
                return new IntField(vh, null, true);
            } catch (Throwable vhStaticFailed) {
                // 3. Reflection fallback (walks hierarchy)
                try {
                    Field f = findFieldInHierarchy(owner, name);
                    boolean isStatic = Modifier.isStatic(f.getModifiers());
                    return new IntField(null, f, isStatic);
                } catch (Throwable refFailed) {
                    gate.markFailed(refFailed);
                    return new IntField(null, null, false);
                }
            }
        }
    }

    static DoubleField createDouble(Class<?> owner, String name,
                                     MethodHandles.Lookup lookup, BypassGate gate) {
        try {
            VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVarHandle(owner, name, double.class);
            return new DoubleField(vh, null, false);
        } catch (Throwable vhInstanceFailed) {
            try {
                VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                        .findStaticVarHandle(owner, name, double.class);
                return new DoubleField(vh, null, true);
            } catch (Throwable vhStaticFailed) {
                try {
                    Field f = findFieldInHierarchy(owner, name);
                    boolean isStatic = Modifier.isStatic(f.getModifiers());
                    return new DoubleField(null, f, isStatic);
                } catch (Throwable refFailed) {
                    gate.markFailed(refFailed);
                    return new DoubleField(null, null, false);
                }
            }
        }
    }

    static FloatField createFloat(Class<?> owner, String name,
                                   MethodHandles.Lookup lookup, BypassGate gate) {
        try {
            VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVarHandle(owner, name, float.class);
            return new FloatField(vh, null, false);
        } catch (Throwable vhInstanceFailed) {
            try {
                VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                        .findStaticVarHandle(owner, name, float.class);
                return new FloatField(vh, null, true);
            } catch (Throwable vhStaticFailed) {
                try {
                    Field f = findFieldInHierarchy(owner, name);
                    boolean isStatic = Modifier.isStatic(f.getModifiers());
                    return new FloatField(null, f, isStatic);
                } catch (Throwable refFailed) {
                    gate.markFailed(refFailed);
                    return new FloatField(null, null, false);
                }
            }
        }
    }

    static <T> RefField<T> createRef(Class<?> owner, String name, Class<T> fieldType,
                                      MethodHandles.Lookup lookup, BypassGate gate) {
        try {
            VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVarHandle(owner, name, fieldType);
            return new RefField<>(vh, null, false);
        } catch (Throwable vhInstanceFailed) {
            try {
                VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                        .findStaticVarHandle(owner, name, fieldType);
                return new RefField<>(vh, null, true);
            } catch (Throwable vhStaticFailed) {
                try {
                    Field f = findFieldInHierarchy(owner, name);
                    boolean isStatic = Modifier.isStatic(f.getModifiers());
                    return new RefField<>(null, f, isStatic);
                } catch (Throwable refFailed) {
                    gate.markFailed(refFailed);
                    return new RefField<>(null, null, false);
                }
            }
        }
    }
}
