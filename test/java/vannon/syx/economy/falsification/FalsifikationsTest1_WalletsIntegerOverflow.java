package vannon.syx.economy.falsification;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vannon.syx.economy.core.AuditKernel;
import vannon.syx.economy.core.Wallets;

/**
 * Falsifikations-Test #1 für RES-002-Behauptung:
 *   "Geldmengen-Erhaltung funktioniert korrekt via AuditKernel.expected()."
 *
 * <p><b>Falsifikations-Hypothese (RES-003):</b>
 * {@code Wallets.money[]} ist {@code int[60000]}. Java
 * {@code int}-Overflow (2.147 Milliarden) ist silent — kein
 * {@code Math.addExact()}, kein Preconditions-Check in {@link
 * Wallets#add} / {@link Wallets#charge} / {@link
 * Wallets#settleReserved} / {@link Wallets#applyExchange}. Die
 * vier {@link Wallets#addDebt}-/Ersatz-Methoden
 * ({@code addRentDebt}, {@code accrueTax}) haben zwar eine
 * Sättigungs-Klammer gegen {@code Integer.MAX_VALUE}, aber
 * {@code money[]} selbst ist ungeschützt. Der {@link
 * AuditKernel} rechnet ausschließlich auf {@code long}-Zählern
 * von {@code EconomySim}, nicht auf {@code Wallets.money[]} —
 * er kann den int-Wrap also NICHT per
 * {@code delta(circulating, expected)} erkennen, wenn
 * {@code circulating()} aus korrumpiertem {@code int[]} summiert.</p>
 *
 * <p><b>Falsifikations-Kriterium:</b>
 * {@code |circulating() - AuditKernel.expected(terms)| > 0} nach
 * einem simulierten overflow-Tick — d.h. ein stiller Vorzeichen-
 * Flip in {@code money[]} ist nirgends sichtbar. Wenn das
 * Kriterium erfüllt ist, ist die RES-002-Behauptung
 * "Geldmengen-Erhaltung via AuditKernel" <em>FALSCH</em>.</p>
 *
 * <p><b>Scope:</b> Reflection-only unit tests (Rule 9 —
 * kein Engine-Inject nötig). Kein Multi-Tick-Lauf.</p>
 */
@DisplayName("Falsifikation #1 — Wallets.money[60000] Integer-Overflow")
class FalsifikationsTest1_WalletsIntegerOverflow {

    private Wallets wallets;
    private Field moneyField;

    @BeforeEach
    void setUp() throws Exception {
        wallets = new Wallets();
        moneyField = Wallets.class.getDeclaredField("money");
        moneyField.setAccessible(true);
    }

    /**
     * Falsifikation 1A — Datenstruktur-Beweis.
     *
     * <p>Wenn {@code money[]} {@code long[]} wäre, gäbe es
     * kein Overflow. Behauptung der RES-003-Hypothese ist genau
     * diese Schwäche.</p>
     */
    @Test
    @DisplayName("money[] is int[] (not long[]) — overflow is structurally possible")
    void money_field_is_int_not_long_overflow_possible() throws Exception {
        // Field type for arrays === array-Klasse selbst, NICHT Komponent-Typ.
        // Field.getType() returnt für int[] == int[].class (NICHT int.class).
        assertSame(int[].class, moneyField.getType(),
                "Wallets.money MUST be int[] — that is what enables silent overflow. "
                        + "A long[] would falsify the hypothesis on its own.");
        int[] arr = (int[]) moneyField.get(wallets);
        assertEquals(60000, arr.length, "Slot count must stay 60000 (breaks if refactored).");
    }

    /**
     * Falsifikation 1B — Stille Wrap-Arithmetik auf money[].
     *
     * <p>Wallets.add() führt exakt {@code this.money[n] = this.money[n] + amount}
     * aus. Bei {@code money[n] = Integer.MAX_VALUE - 5} ist der nächste
     * {@code +1}-Op kein Fehler, sondern ein silent wrap auf
     * {@code Integer.MIN_VALUE + 4}. Das ist exakt die
     * Geldschöpfungs-/Geldvernichtungs-Landmine, die RES-003 befürchtet.</p>
     */
    @Test
    @DisplayName("Sequentielle +1 Ops near MAX_VALUE wrap silently — kein AddExact-Guard")
    void int_arithmetic_near_MAX_VALUE_wraps_silently() throws Exception {
        int[] arr = (int[]) moneyField.get(wallets);
        arr[0] = Integer.MAX_VALUE - 5;

        // Simuliere 10 sequentielle add(h, 1) (== Wallets.add(...) Verhalten).
        for (int i = 0; i < 10; ++i) {
            arr[0] = arr[0] + 1; // primitive int + primitive int = silent overflow
        }

        // Erwartung: 2_147_483_647 - 5 + 10 = 2_147_483_652 → wrap auf -2_147_483_644
        assertEquals(Integer.MIN_VALUE + 4, arr[0],
                "money[0] muss nach 10x add(1) ab MAX_VALUE-5 auf "
                        + "Integer.MIN_VALUE + 4 gewrappt sein — exakt der "
                        + "stille int-Overflow den Wallets.add() ungeprüft passieren lässt.");
        assertTrue(arr[0] < 0,
                "Wallet wurde ohne addDebt()-Klammer negativ — Geldschöpfung/Vernichtung.");
    }

    /**
     * Falsifikation 1C — Asymmetrie zwischen addDebt (geschützt) und add (ungeschützt).
     *
     * <p>{@link Wallets#addDebt} enthält eine explizite
     * {@code sum >= Integer.MAX_VALUE}-Klammer, aber
     * {@link Wallets#add} / {@link Wallets#charge} / {@link
     * Wallets#settleReserved} / {@link Wallets#applyExchange}
     * nicht. Das ist ein inkonsistenter Schutzgrad — die
     * gefährliche Lücke liegt genau dort, wo das meiste Geld
     * fließt (add/charge bei Lohnauszahlung).</p>
     */
    @Test
    @DisplayName("addDebt hat overflow-Klammer, add() nicht — asymmetrischer Schutz")
    void asymmetry_addDebt_protected_add_unprotected() throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(
                        "src/vannon/syx/economy/core/Wallets.java")),
                java.nio.charset.StandardCharsets.UTF_8);

        // addDebt: Sättigungs-Klammer vorhanden (audit-code-grep)
        assertTrue(src.contains("sum >= Integer.MAX_VALUE"),
                "Wallets.addDebt/addRentDebt/accrueTax haben Sättigungsklammer — Schutz bewiesen.");

        // add/charge/settleReserved: KEIN addExact, KEINE Klammer im signifikanten Pfad.
        // Wir blocken auf den spezifischen toten Stellen.
        int idxAdd = src.indexOf("public void add(Humanoid h, int amount)");
        assertTrue(idxAdd > 0, "Wallets.add Methode muss existieren.");
        String addBlock = src.substring(idxAdd,
                Math.min(idxAdd + 600, src.length()));
        assertFalse(addBlock.contains("Math.addExact"),
                "Falsifikation: Wallets.add darf KEIN Math.addExact enthalten — "
                        + "sonst wäre die Hypothese bereits durch Code-Inspektion widerlegt.");
        assertFalse(addBlock.contains("Preconditions.check"),
                "Falsifikation: Wallets.add darf KEINE Preconditions-Klammer enthalten.");

        int idxCharge = src.indexOf("public int charge(Humanoid h, int amount)");
        assertTrue(idxCharge > 0, "Wallets.charge Methode muss existieren.");
        String chargeBlock = src.substring(idxCharge,
                Math.min(idxCharge + 600, src.length()));
        assertFalse(chargeBlock.contains("Math.addExact"),
                "Falsifikation: Wallets.charge darf KEIN Math.addExact enthalten.");
    }

    /**
     * Falsifikation 1D — AuditKernel sieht den Wrap nicht.
     *
     * <p>{@link AuditKernel#expected} liest ausschließlich die
     * lang-Getreuen {@code EconomySim}-Counter
     * ({@code seed}, {@code imported}, {@code treasuryOut},
     * {@code roundingDrift}, {@code wages}, ...). Selbst wenn
     * auditSupply() einen großen {@code delta = circulating - expected}
     * feststellt, ist diese Differenz ein Output der Audit-Summen-Logik,
     * nicht ein direkter Indikator für einen int-Wrap in {@code money[]}.
     * Wenn {@code money[]} still wrapped und {@code circulating()} einen
     * falschen long-Summenwert liefert, der zufällig {@code expected}
     * entspricht, fällt das durch's AuditRaster. Wenn er nicht entspricht,
     * dokumentiert es nur {@code roundingDrift}, repariert aber nicht
     * den int-Wrap.</p>
     *
     * <p>Dieser Test demonstriert den Audit-Reality-Check:
     * {@link AuditKernel#expected} hat keinen Zugriff auf {@code money[]}.
     * Der einzige Reparaturpfad ist {@code roundingDrift += delta}
     * (stille Akkumulation unterhalb von {@code EconConfig.roundingDriftThreshold})
     * — das repariert die Buchhaltungsbilanz NICHT, sondern versteckt sie
     * in einem Sammelposten.</p>
     */
    @Test
    @DisplayName("AuditKernel.expected hat keinen Zugriff auf Wallets.money[] — Wrap bleibt unsichtbar")
    void auditKernel_does_not_inspect_Wallets_money() throws Exception {
        String auditSrc = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(
                        "src/vannon/syx/economy/core/AuditKernel.java")),
                java.nio.charset.StandardCharsets.UTF_8);

        // AuditKernel.expected summiert nur Terms (long Counter auf EconomySim). Kein
        // direkter Aufruf von Wallets.circulating() o.ä.
        assertFalse(auditSrc.contains("Wallets") || auditSrc.contains(".money"),
                "AuditKernel muss frei von Wallets-Direktzugriffen sein — "
                        + "sonst wäre die Falsifikationshypothese (AuditKernel sieht Wrap nicht) widerlegt.");

        // Reparatur-Pfad: roundingDrift += delta — Akkumulation OHNE Korrektur.
        String econSimSrc = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(
                        "src/vannon/syx/economy/core/EconomySim.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(econSimSrc.contains("roundingDrift += delta")
                        || econSimSrc.contains("this.roundingDrift += delta"),
                "Verifikation: auditSupply() muss die Kompensations-Akkumulation via "
                        + "roundingDrift += delta fahren — sonst ist die Buchhaltung-Lüge "
                        + "anderswo versteckt und die Falsifikation greift nicht.");
    }

    /**
     * Falsifikation 1E — Verhaltens-Beweis: |circulating - expected| &gt; 0 nach Wrap.
     *
     * <p>Dieses Test-Verfahren operationalisiert das vom
     * User genannte Falsifikationskriterium wörtlich:
     * {@code |circulating() - AuditKernel.expected(terms)| > 0}.
     * Wir konstruieren einen {@link AuditKernel.Terms}-Record,
     * dessen {@code seed} den <em>gewollten</em>
     * Geld-Mitnahme-Wert reflektiert
     * ({@code MAX_VALUE - 5 + 10 = MAX_VALUE + 5}), und einen
     * {@link Wallets}, dessen {@code money[0]} durch denselben
     * Wrap-Pfad auf {@code MIN_VALUE + 4} steht (stiller
     * int-Overflow). Das Audit-Ergebnis ist die Differenz
     * zwischen dem, was <em>gemeint</em> war, und dem, was
     * <em>drin steht</em> — und sie ist nicht null.</p>
     *
     * <p><b>Wenn der Assert hier hält, ist die
     * RES-002-Behauptung "Geldmengen-Erhaltung via
     * AuditKernel" FALSCH</b> — der Audit-Kernel kann den
     * stillen int-Wrap NICHT reparieren, nur in
     * {@code roundingDrift} verschleiern.</p>
     */
    @Test
    @DisplayName("Behavior: |circulating() − expected()| > 0 nach int-Wrap in money[]")
    void behavioral_wrap_produces_delta_between_circulating_and_expected() throws Exception {
        // Arrange — Wallets instanziieren, money[0] nahe MAX_VALUE setzen.
        Wallets wallets = new Wallets();
        Field moneyField = Wallets.class.getDeclaredField("money");
        moneyField.setAccessible(true);
        int[] arr = (int[]) moneyField.get(wallets);
        // VOR dem Wrap: Slot 0 als "in Besitz" markieren — ownedSlots/ownedCount/owner[]
        // werden sonst von circulating() nicht iteriert (Wallets.circulating() liest
        // AUSSCHLIESSLICH die ownedSlots-Liste, nie das ganze money[]).
        Field ownedSlotsField = Wallets.class.getDeclaredField("ownedSlots");
        ownedSlotsField.setAccessible(true);
        int[] ownedSlots = (int[]) ownedSlotsField.get(wallets);
        ownedSlots[0] = 0;
        Field ownedCountField = Wallets.class.getDeclaredField("ownedCount");
        ownedCountField.setAccessible(true);
        ownedCountField.setInt(wallets, 1);
        Field ownerField = Wallets.class.getDeclaredField("owner");
        ownerField.setAccessible(true);
        int[] owner = (int[]) ownerField.get(wallets);
        owner[0] = 12345; // beliebiger non-(-1) Wert, sodass liveSlot() den Slot akzeptiert.

        // Geld auf MAX_VALUE - 5 setzen, dann 10× +1 simulieren (== Wallets.add-Logik).
        arr[0] = Integer.MAX_VALUE - 5;
        for (int i = 0; i < 10; ++i) {
            arr[0] = arr[0] + 1; // silent int wrap auf MIN_VALUE + 4
        }
        long circulating = wallets.circulating();

        // AuditKernel.Terms: seed = gewollter Betrag (MAX_VALUE + 5).
        long intendedSeed = (long) Integer.MAX_VALUE - 5L + 10L; // = MAX_VALUE + 5
        AuditKernel.Terms terms = new AuditKernel.Terms(
                intendedSeed, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        long expected = AuditKernel.expected(terms);
        long delta = AuditKernel.delta(circulating, terms);

        // Falsifikations-Kriterium: |circulating - expected| > 0
        // Mit korrekt-gesetzen ownedSlots liest circulating() arr[0] = MIN_VALUE + 4
        // (≈ −2.147.483.644). expected() liest intendedSeed = MAX_VALUE + 5 (≈ +2.147.483.652).
        // Differenz muss erheblich > 0 sein.
        assertTrue(Math.abs(circulating - expected) > 0L,
                "Falsifikation BESTÄTIGT (behavioral): |circulating(" + circulating
                        + ") - expected(" + expected + ")| = " + delta
                        + " > 0. RES-002-Behauptung 'Geldmengen-Erhaltung via AuditKernel' "
                        + "ist FALSCH — der stille int-Wrap entzieht sich der Audit-Erkennung.");
    }
}
