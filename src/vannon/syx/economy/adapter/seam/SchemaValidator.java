package vannon.syx.economy.adapter.seam;

import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.Map;
import vannon.syx.economy.core.EventLog;

/**
 * Start-up validator: cross-checks the adapter field/method declarations
 * against the live engine BEFORE any adapter is built.
 *
 * <p>Every register call specifies a class, a field/method name, and the
 * access type. {@link #validate()} resolves each entry via BypassGate and
 * records pass/fail. The result is a {@link ValidationReport} that the
 * {@link AdapterDispatcher} reads to decide which adapters to enable.</p>
 *
 * <p>This is the runtime counterpart to {@code tools/vanilla-schema.yaml}.
 * At engine-update time (V71→V72) the validator catches renamed/missing
 * fields at construction time — Fail-Fast, not Laufzeit-NPE.</p>
 */
public final class SchemaValidator {

    public enum AccessType {
        /** Instance field, read-write via FieldAccessor.IntField/DoubleField/etc. */
        INSTANCE_FIELD,
        /** Instance method, invoked via MethodAccessor.VoidMethod/BooleanMethod. */
        INSTANCE_METHOD,
        /** Class existence only — used for ClassResolver plan-recognition classes. */
        CLASS_EXISTS
    }

    public static final class Entry {
        final String className;
        final String memberName;
        final AccessType accessType;

        Entry(String className, String memberName, AccessType accessType) {
            this.className = className;
            this.memberName = memberName;
            this.accessType = accessType;
        }
    }

    /** Registration-time ordering preserved for deterministic validation log output. */
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Adapter name prefix for EventLog entries. */
    private final String adapterName;

    /** Set to false by the first validation failure; gate.isAvailable() equivalent. */
    private boolean allOk = true;

    public SchemaValidator(String adapterName) {
        this.adapterName = adapterName;
    }

    /**
     * Register a class member (field/method/class) for validation.
     *
     * @param className  fully-qualified class name ($ for inner classes)
     * @param memberName field or method name (ignored for CLASS_EXISTS)
     * @param accessType how this member is accessed at runtime
     */
    public void register(String className, String memberName, AccessType accessType) {
        String key = className + "#" + memberName;
        if (entries.containsKey(key)) return;
        entries.put(key, new Entry(className, memberName, accessType));
    }

    /**
     * Shorthand: register a BypassGate instance field.
     */
    public void registerField(String className, String fieldName) {
        register(className, fieldName, AccessType.INSTANCE_FIELD);
    }

    /**
     * Shorthand: register a BypassGate instance method.
     */
    public void registerMethod(String className, String methodName) {
        register(className, methodName, AccessType.INSTANCE_METHOD);
    }

    /**
     * Shorthand: register a class that only needs to exist (ClassResolver plans).
     */
    public void registerClass(String className) {
        register(className, className, AccessType.CLASS_EXISTS);
    }

    /**
     * Validate all registered entries against the live engine.
     * @return a report summarising which entries passed and which failed.
     */
    public ValidationReport validate() {
        ValidationReport report = new ValidationReport();
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        for (Entry entry : entries.values()) {
            boolean ok = false;
            try {
                switch (entry.accessType) {
                    case CLASS_EXISTS:
                        Class.forName(entry.className, true,
                                settlement.entity.humanoid.Humanoid.class.getClassLoader());
                        ok = true;
                        break;
                    case INSTANCE_FIELD:
                    case INSTANCE_METHOD:
                        // Delegate to BypassGate for VarHandle/Reflection resolution.
                        // We don't need to create a real accessor — just test resolution.
                        ok = probeAccess(entry.className, entry.memberName, entry.accessType, lookup);
                        break;
                }
            } catch (Throwable t) {
                ok = false;
            }

            if (!ok) {
                this.allOk = false;
            }
            report.record(entry.className, entry.memberName, entry.accessType, ok);
        }

        if (!this.allOk) {
            EventLog.log("SEAM", adapterName + ": schema validation FAILED — "
                    + report.failedCount() + "/" + entries.size() + " entries unreachable");
        } else {
            EventLog.log("SEAM", adapterName + ": schema validation PASSED — "
                    + entries.size() + "/" + entries.size() + " entries verified");
        }

        return report;
    }

    /**
     * Probe whether a class can be loaded and (for fields/methods) whether
     * the member NAME exists. We do NOT check the exact type — that is the
     * adapter's responsibility via its typed BypassGate factories.
     * <p>ClassResolver handles package-private classes; public classes use
     * standard Class.forName.</p>
     */
    private boolean probeAccess(String className, String memberName, AccessType type,
                                MethodHandles.Lookup lookup) {
        try {
            Class<?> clazz = Class.forName(className, true,
                    settlement.entity.humanoid.Humanoid.class.getClassLoader());
            if (type == AccessType.CLASS_EXISTS) return true;
            // For fields: check existence via getDeclaredField (hierarchy-walk).
            // The adapter will create a typed accessor; we just confirm the field is there.
            if (type == AccessType.INSTANCE_FIELD) {
                Class<?> current = clazz;
                while (current != null) {
                    try {
                        current.getDeclaredField(memberName);
                        return true;
                    } catch (NoSuchFieldException e) {
                        current = current.getSuperclass();
                    }
                }
                return false;
            }
            // For methods: check existence via getDeclaredMethod.
            if (type == AccessType.INSTANCE_METHOD) {
                // We don't know the parameter types here, so try the no-arg variant
                // and also the single-boolean variant (common for storingSet).
                try {
                    clazz.getDeclaredMethod(memberName);
                    return true;
                } catch (NoSuchMethodException e1) {
                    try {
                        clazz.getDeclaredMethod(memberName, boolean.class);
                        return true;
                    } catch (NoSuchMethodException e2) {
                        return false;
                    }
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isAllOk() {
        return this.allOk;
    }

    /**
     * Immutable validation report: which entries passed/failed.
     */
    public static final class ValidationReport {
        private final Map<String, Boolean> results = new LinkedHashMap<>();
        private int failed;

        void record(String className, String memberName, AccessType type, boolean ok) {
            String label = className + "#" + memberName + " [" + type + "]";
            results.put(label, ok);
            if (!ok) failed++;
        }

        public int failedCount() { return failed; }
        public int totalCount() { return results.size(); }
        public boolean allPassed() { return failed == 0; }

        /** Per-entry status: true = validated, false = engine mismatch. */
        public Map<String, Boolean> entries() {
            return new LinkedHashMap<>(results);
        }
    }
}
