package vannon.syx.economy.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Nicht-invasive Lokalisierungs-Brücke.
 *
 * <p>EconTexts-Konstanten bleiben unangetastet (Rückwärtskompatibilität
 * für alle bestehenden {@code EconTexts.¤¤xyz}-Referenzen in 5 UI-Dateien).
 * Neue UI-Features und schrittweise Migration nutzen
 * {@code LocaleStrings.t("tab.wealth", EconTexts.¤¤tabWealth)}.
 *
 * <p>English-Fallback via {@link #en} Map. Bei {@code EconConfig.locale = "en"}
 * wird der englische Wert verwendet, sonst der deutsche Default.
 */
public final class LocaleStrings {

    /** English translations. Key = stable dot-separated id, Value = English text. */
    public static final Map<String, String> en = new HashMap<>();

    static {
        // Tabs
        en.put("tab.wealth", "WEALTH");
        en.put("tab.citizens", "CITIZENS");
        en.put("tab.prices", "PRICES");
        en.put("tab.wages", "WAGES");
        en.put("tab.subsidies", "SUBSIDIES");
        en.put("tab.granary", "STATE STORES");
        en.put("tab.market", "MARKET");
        en.put("tab.taxes", "TAXES");
        en.put("tab.faith", "FAITH");
        en.put("tab.corvee", "STATE LABOR");
        en.put("tab.relief", "RELIEF");
        en.put("tab.books", "BOOKS");
        en.put("tab.debug", "DEBUG");
        en.put("tab.advisor", "ADVISOR");
        en.put("tab.firms", "FIRMS");
        en.put("tab.flows", "FLOWS");
        en.put("tab.dashboard", "DASHBOARD");
        en.put("tab.foreignTrade", "FOREIGN TRADE");

        // Common UI
        en.put("ui.denari", " Denari");
        en.put("ui.perUnit", " / Unit");
        en.put("ui.perSeason", " / Season");
        en.put("ui.perDay", "/Day");
        en.put("ui.of", " of ");
        en.put("ui.slash", " / ");

        // Window titles
        en.put("window.title", "ECONOMY");
        en.put("window.overview", "OVERVIEW");
        en.put("window.state", "STATE & SOCIAL");
    }

    /**
     * Returns the localized string for the given key.
     *
     * @param key stable dot-separated key (e.g. "tab.wealth")
     * @param de  German fallback (usually {@code EconTexts.¤¤xyz})
     * @return English string if {@code EconConfig.locale.equals("en")} and key exists,
     *         otherwise the German fallback
     */
    public static String t(String key, String de) {
        if (!"en".equals(EconConfig.locale)) {
            return de;
        }
        String eng = en.get(key);
        return eng != null ? eng : de;
    }

    private LocaleStrings() {}
}
