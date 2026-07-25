package vannon.syx.economy.adapter.seam;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

/**
 * Typed field accessors for private/package-private fields.
 *
 * <p>Every accessor holds either a {@link VarHandle} (preferred, 3–6× faster)
 * or a fallback {@link Field} with {@code setAccessible(true)}. The resolution
 * strategy is attempted once at construction time; {@code get}/{@code set}
 * calls incur no further reflection overhead.</p>
 *
 * <p>These classes are constructed exclusively by {@link BypassGate} factory
 * methods; they are not intended for direct instantiation.</p>
 */
public final class FieldAccessor {
    private FieldAccessor() {}

    // ─── IntField ─────────────────────────────────────────────────

    public static final class IntField {
        private final VarHandle vh;
        private final Field field;

        IntField(VarHandle vh, Field field) { this.vh = vh; this.field = field; }

        public int get(Object instance) {
            if (vh != null) return (int) vh.get(instance);
            if (field != null) return getReflective(instance);
            return 0;
        }

        public void set(Object instance, int value) {
            if (vh != null) vh.set(instance, value);
            else if (field != null) setReflective(instance, value);
        }

        public int getStatic() {
            if (vh != null) return (int) vh.get();
            if (field != null) return getStaticReflective();
            return 0;
        }

        public void setStatic(int value) {
            if (vh != null) vh.set(value);
            else if (field != null) setStaticReflective(value);
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

        DoubleField(VarHandle vh, Field field) { this.vh = vh; this.field = field; }

        public double get(Object instance) {
            if (vh != null) return (double) vh.get(instance);
            if (field != null) return getReflective(instance);
            return 0.0;
        }

        public void set(Object instance, double value) {
            if (vh != null) vh.set(instance, value);
            else if (field != null) setReflective(instance, value);
        }

        public double getStatic() {
            if (vh != null) return (double) vh.get();
            if (field != null) return getStaticReflective();
            return 0.0;
        }

        public void setStatic(double value) {
            if (vh != null) vh.set(value);
            else if (field != null) setStaticReflective(value);
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

        FloatField(VarHandle vh, Field field) { this.vh = vh; this.field = field; }

        public float get(Object instance) {
            if (vh != null) return (float) vh.get(instance);
            if (field != null) return getReflective(instance);
            return 0.0f;
        }

        public void set(Object instance, float value) {
            if (vh != null) vh.set(instance, value);
            else if (field != null) setReflective(instance, value);
        }

        public float getStatic() {
            if (vh != null) return (float) vh.get();
            if (field != null) return getStaticReflective();
            return 0.0f;
        }

        public void setStatic(float value) {
            if (vh != null) vh.set(value);
            else if (field != null) setStaticReflective(value);
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

        RefField(VarHandle vh, Field field) { this.vh = vh; this.field = field; }

        @SuppressWarnings("unchecked")
        public T get(Object instance) {
            if (vh != null) return (T) vh.get(instance);
            if (field != null) return getReflective(instance);
            return null;
        }

        public void set(Object instance, T value) {
            if (vh != null) vh.set(instance, value);
            else if (field != null) setReflective(instance, value);
        }

        @SuppressWarnings("unchecked")
        public T getStatic() {
            if (vh != null) return (T) vh.get();
            if (field != null) return getStaticReflective();
            return null;
        }

        public void setStatic(T value) {
            if (vh != null) vh.set(value);
            else if (field != null) setStaticReflective(value);
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

    // ─── Factory methods (called by BypassGate) ───────────────────

    static IntField createInt(Class<?> owner, String name,
                               MethodHandles.Lookup lookup, BypassGate gate) {
        try {
            VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVarHandle(owner, name, int.class);
            return new IntField(vh, null);
        } catch (Throwable vhFailed) {
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return new IntField(null, f);
            } catch (Throwable refFailed) {
                gate.markFailed(refFailed);
                return new IntField(null, null);
            }
        }
    }

    static DoubleField createDouble(Class<?> owner, String name,
                                     MethodHandles.Lookup lookup, BypassGate gate) {
        try {
            VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVarHandle(owner, name, double.class);
            return new DoubleField(vh, null);
        } catch (Throwable vhFailed) {
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return new DoubleField(null, f);
            } catch (Throwable refFailed) {
                gate.markFailed(refFailed);
                return new DoubleField(null, null);
            }
        }
    }

    static FloatField createFloat(Class<?> owner, String name,
                                   MethodHandles.Lookup lookup, BypassGate gate) {
        try {
            VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVarHandle(owner, name, float.class);
            return new FloatField(vh, null);
        } catch (Throwable vhFailed) {
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return new FloatField(null, f);
            } catch (Throwable refFailed) {
                gate.markFailed(refFailed);
                return new FloatField(null, null);
            }
        }
    }

    static <T> RefField<T> createRef(Class<?> owner, String name, Class<T> fieldType,
                                      MethodHandles.Lookup lookup, BypassGate gate) {
        try {
            VarHandle vh = MethodHandles.privateLookupIn(owner, lookup)
                    .findVarHandle(owner, name, fieldType);
            return new RefField<>(vh, null);
        } catch (Throwable vhFailed) {
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return new RefField<>(null, f);
            } catch (Throwable refFailed) {
                gate.markFailed(refFailed);
                return new RefField<>(null, null);
            }
        }
    }
}
