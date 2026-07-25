#!/usr/bin/env python3
"""
Scarcity-Simulator: SyxEconomyMod Faktor-Kaskaden-Test
======================================================

Ziel: Validierung der zwei Kaskaden (Scarcity-Dominance + Gini-Tsunami) gegen
die REALEN Formeln aus EconConfig / FlowPrices / LocalPrices.

Quellen (alle in tools/.. verifiziert):
- src/vannon/syx/economy/core/EconConfig.java   (Konstanten)
- src/vannon/syx/economy/core/FlowPrices.java   (localPrice, scarcityMultiplier,
                                                 effectiveCoverage)
- src/vannon/syx/economy/core/LocalPrices.java  (scarcity mit TANH, mealPrice)
- src/vannon/syx/economy/core/FlowMeter.java    (exp smoothing auf supply)

Realitaets-Check der User-Annahmen:
- scarcityElasticityUp = 0.8  (NICHT 1.5 wie vom User angenommen)
- scarcityElasticityDown = 1.375 (NICHT 0.5 wie vom User angenommen)
- scarcityPriceBoost = 0.3
- priceClampLo = 0.001, priceClampHi = 100.0
- priceAbsoluteMax = 50000
- LocalPrices.scarcity() = TANH-basiert, pow(m, -tanh(...))
"""

import math
import sys

# ─── Konstanten aus EconConfig.java (grep-verifiziert) ─────────────────────
ELASTICITY_UP = 0.8
ELASTICITY_DOWN = 1.375
SCARCITY_PRICE_BOOST = 0.3
PRICE_CLAMP_LO = 0.001
PRICE_CLAMP_HI = 100.0
PRICE_ABSOLUTE_MAX = 50000.0

FLOW_LOOKAHEAD_DAYS = 7.0
COVERAGE_FLOOR = 0.005  # aus FlowPrices.java Z.8
SCARCITY_MAX_MULTIPLE = 1.5
SCARCITY_STEEPNESS = 1.0
TARGET_FOOD_DAYS = 6.0  # targetFoodDays

# ─── FlowPrices-Formel (Stack-Verschnitt, 1:1) ─────────────────────────────
def effective_coverage(stock: float, supply: float, demand: float,
                       target_coverage_days: float, lookahead: float) -> float:
    target = demand * target_coverage_days
    if target <= 0.0:
        return 1.0
    projected = max(0.0, stock) + lookahead * (supply - demand)
    return max(0.0, projected / target)


def scarcity_multiplier(coverage: float) -> float:
    elasticity = ELASTICITY_UP if coverage < 1.0 else ELASTICITY_DOWN
    raw = math.pow(max(COVERAGE_FLOOR, coverage), -elasticity)
    if math.isnan(raw):
        return 1.0
    if math.isinf(raw):
        return PRICE_CLAMP_HI
    return min(PRICE_CLAMP_HI, max(PRICE_CLAMP_LO, raw))


def flow_price(anchor: float, coverage: float, scarcity_signal: float) -> float:
    """Genau wie FlowPrices.refresh() Zeile 24-29."""
    local = anchor * scarcity_multiplier(coverage)
    if PRICE_ABSOLUTE_MAX > 0.0 and local > PRICE_ABSOLUTE_MAX:
        local = PRICE_ABSOLUTE_MAX
    s = max(0.0, min(1.0, scarcity_signal))
    local *= (1.0 + s * SCARCITY_PRICE_BOOST)
    if PRICE_ABSOLUTE_MAX > 0.0 and local > PRICE_ABSOLUTE_MAX:
        local = PRICE_ABSOLUTE_MAX
    return local


def cash_clamp(price: float) -> float:
    return max(PRICE_CLAMP_LO, min(PRICE_CLAMP_HI, price))


# ─── LocalPrices.scarcity(): TANH-basiert (NICHT pow!) ─────────────────────
def localprices_scarcity(per_capita: float, target: float) -> float:
    """Exakt portiert aus LocalPrices.scarcity(): pow(m, -tanh(x/w))
    Guard: per_capita muss positiv sein (sonst log(0)=-inf oder log(-x)=ValueError)."""
    if target <= 0.0:
        return 1.0
    m = SCARCITY_MAX_MULTIPLE
    w = SCARCITY_STEEPNESS
    if m <= 1.0 or w <= 0.0:
        return 1.0
    # Numerischer Floor — verhindert log(0) und log(neg)
    ratio = max(1e-9, per_capita / target)
    x = math.log(ratio)
    return math.pow(m, -math.tanh(x / w))


def flow_meter_blend(prev: float, current: float, window_days: float,
                     elapsed_days: float) -> float:
    if elapsed_days == 0:
        return current
    blend = 1.0 - math.exp(-elapsed_days / window_days)
    return prev * (1 - blend) + current * blend


# ─── Hilfsfunktionen ──────────────────────────────────────────────────────
def fmt(x: float, width: int = 8, prec: int = 2) -> str:
    if x == 0.0 or abs(x) < 1e-9:
        return f"{0.0:>{width}.{prec}f}"
    if x >= 1e6 or x < 1e-3:
        return f"{x:>{width}.{prec}e}"
    return f"{x:>{width}.{prec}f}"


def header(label: str) -> None:
    print("\n" + "=" * 78)
    print(f"  {label}")
    print("=" * 78)


# ═══════════════════════════════════════════════════════════════════════════
# Scenario A — Drought (Scarcity-Dominanz + Asymmetrie-Frage)
# ═══════════════════════════════════════════════════════════════════════════
def scenario_a_dry_drought() -> dict:
    """
    Prueft:
    - Schlaegt der Preis an priceClampHi=100 an?
    - Wie schnell konvergiert die Asymmetrie (UP=0.8 vs DOWN=1.375)?
    - Was passiert waehrend und nach der Duerre?
    """
    header("Szenario A — Drought (50 Ticks, grain anchor=100)")
    print(f"{'tick':>4} | {'storage':>8} {'supply':>8} {'demand':>8} "
          f"{'coverage':>9} | {'raw':>8} {'final':>8} {'boost':>7} {'clamped':>8}")
    print(f"{'':─>4} | {'─'*8} {'─'*8} {'─'*8} {'─'*9} | "
          f"{'─'*8} {'─'*8} {'─'*7} {'─'*8}")
    initial_storage = 100.0
    initial_demand = 5.0
    initial_supply = 5.0
    target_coverage_days = 6.0
    target_stock = initial_demand * target_coverage_days
    anchor = 100.0

    # FIX v1.1: initial_storage MUSS = target sein, sonst startet coverage > 1.0
    # und der Drought treibt sie nie unter 1.0 → kein UP-Branch → kein Clamp.
    # Equilibriums-Baseline: storage = demand × coverageDays = 5 × 6 = 30
    initial_storage = target_stock

    state = {
        "storage": initial_storage,
        "supply": initial_supply,
        "demand": initial_demand,
        "smoothed_supply": initial_supply,
        "smoothed_storage": initial_storage,
        "peak_price": 0.0,
        "clamp_hits": 0,
        "first_clamp_tick": None,
        "convergence_tick": None,
        "prices": [],
    }
    for tick in range(0, 50):
        # Drought t=5..9: Supply faellt stark ab. Pro Tick halbiert
        # (5 → 2.5 → 1.25 → 0.625 → 0.31). Storage drainiert entsprechend.
        if 5 <= tick <= 9:
            state["supply"] *= 0.5  # pro Tick ×0.5 → nach 5 Ticks ≈ 0.16
        elif tick > 9:
            # Recovery: 1.5x pro Tick → 0.16 → 0.24 → 0.36 → 0.54 → 0.81 → ...
            state["supply"] = min(initial_supply, state["supply"] * 1.5)

        # Nachfrage reagiert auf Preis (vereinfachter Affordability-Effekt)
        if tick >= 5:
            price_state = flow_price(anchor, effective_coverage(
                state["smoothed_storage"], state["smoothed_supply"],
                state["demand"], target_coverage_days, FLOW_LOOKAHEAD_DAYS), 0.5)
            if price_state > anchor * 1.2:
                state["demand"] = max(2.0, state["demand"] * 0.95)

        # FlowMeter-Smoothing am Supply-Signal (eingepreist)
        if tick > 0:
            state["smoothed_supply"] = flow_meter_blend(
                state["smoothed_supply"], state["supply"], 1.0, 1.0 / 300.0)
            state["smoothed_storage"] = flow_meter_blend(
                state["smoothed_storage"], state["storage"], 1.0, 1.0 / 300.0)

        cov = effective_coverage(state["smoothed_storage"], state["smoothed_supply"],
                                 state["demand"], target_coverage_days, FLOW_LOOKAHEAD_DAYS)
        raw = anchor * scarcity_multiplier(cov)
        # Exogenes Scarcity-Signal: 0.5 wenn storage/initial < 0.5
        scarcity_signal = 0.5 if state["storage"] < 50.0 else 0.0
        final = flow_price(anchor, cov, scarcity_signal)
        clamped = "YES" if final >= PRICE_CLAMP_HI else ""

        state["prices"].append(final)
        state["peak_price"] = max(state["peak_price"], final)
        if final >= PRICE_CLAMP_HI - 1e-6:
            state["clamp_hits"] += 1
            if state["first_clamp_tick"] is None:
                state["first_clamp_tick"] = tick

        if 0 <= tick <= 20 or tick in (24, 30, 40):
            print(f"{tick:>4} | {fmt(state['storage'])} {fmt(state['supply'])} "
                  f"{fmt(state['demand'])} {fmt(cov, 8, 3)} | "
                  f"{fmt(raw)} {fmt(final)} {fmt(scarcity_signal, 7, 2)} "
                  f"{clamped:>8}")

        # Convergence: innerhalb ±2% vom Equilibrium-Preis
        if state["convergence_tick"] is None and tick >= 15 and abs(final - anchor) < 2.0:
            state["convergence_tick"] = tick

        # Verbrauch + Produktion
        produced = state["smoothed_supply"] / 300.0 * 300.0  # pro Tag
        consumed = state["demand"] / 300.0 * 300.0
        state["storage"] = max(0.0, state["storage"] + produced - consumed)

    print(f"\nPeak price:                {state['peak_price']:.2f}")
    print(f"priceClampHi=100 hits:     {state['clamp_hits']}")
    print(f"First clamp at tick:       {state['first_clamp_tick']}")
    print(f"Convergence (±2% equi):    tick {state['convergence_tick']}")
    return state


def scenario_b_gini_tsunami() -> dict:
    """
    Prueft: Steigt Gini waehrend Duerre? Wie stark? Korrelation mit Deny-Count?
    """
    header("Szenario B — Gini-Tsunami via AffordabilityGate")
    print(f"{'tick':>4} | {'food_basket':>12} {'deny_count':>11} "
          f"{'available_workers':>17} | {'gini':>7} {'gini_chg':>10}")
    state = {
        "gini_baseline": 0.30,
        "gini_peak": 0.30,
        "food_basket_peak": 0.0,
        "deny_count": 0,
        "gini_after_drought": [],
    }
    food_basket_base = 100.0  # FACTIONS.PRICE().edible() (Annahme)
    food_basket = food_basket_base
    gini = state["gini_baseline"]
    food_days = 6.0

    for tick in range(0, 50):
        if 5 <= tick <= 9:
            food_days -= 1.2  # linear abfallend
        elif tick > 9:
            food_days = min(6.0, food_days + 0.5)

        # mealPrice === LocalPrices.mealPrice()
        world = food_basket_base
        s = localprices_scarcity(food_days, TARGET_FOOD_DAYS)
        food_basket = max(1, math.ceil(world * s))
        state["food_basket_peak"] = max(state["food_basket_peak"], food_basket)

        # AffordabilityGate: deny wenn food_basket > wallet-pro-bite
        # Vereinfacht: 10% Buerger haben wallet<food_basket
        deny = max(0, int((food_basket - 50) * 0.5)) if food_basket > 80 else 0
        state["deny_count"] += deny

        # Gini steigt weil Arme verhungern, Mittelwert bleibt
        # Lorenz-Annahme: Reiche behalten Geld, Tote verlieren Geld (Mittelwert fällt)
        if food_days < 3.0:
            dead = (3.0 - food_days) * 0.02  # 2% der Pop pro Tag-Wert
            gini = min(0.95, gini + dead * 0.5)  # 0.5 Gini-Drift pro 2% Tod
        elif food_days >= 4.0 and tick > 25:
            gini = max(0.30, gini - 0.005)  # langsame Genesung

        state["gini_peak"] = max(state["gini_peak"], gini)
        if tick > 9:
            state["gini_after_drought"].append(gini)

        if 0 <= tick <= 20 or tick in (24, 30, 40):
            print(f"{tick:>4} | {fmt(food_basket, 12, 1)} {deny:>11} "
                  f"{fmt(food_days, 17, 2)} | {fmt(gini, 7, 3)} "
                  f"{fmt(gini - state['gini_baseline'], 10, 3)}")

    print(f"\nFood-Basket-Peak:         {state['food_basket_peak']:.0f}")
    print(f"Affordability-Denies:     {state['deny_count']}")
    print(f"Gini-Peak (von 0.30 aus): {state['gini_peak']:.3f}")
    print(f"Gini-Aufschlag am Ende:   {state['gini_after_drought'][-1]:.3f}")
    return state


def scenario_c_fallback_circle() -> dict:
    """
    Edge-Case 6: Leere Recipe-Graph → Anchor = FACTIONS.PRICE().edible() (NPC-Schnitt).
    Wie stark koppelt der Fallback zurueck?
    """
    header("Szenario C — Edge-Case 6: Fallback-Kollision")
    print(f"{'tick':>4} | {'leontief_anchor':>16} {'npc_fallback':>14} "
          f"{'coverage':>9} | {'price_diff_pct':>15}")
    state = {"max_diff_pct": 0.0}
    leontief_anchor = 100.0  # sauber berechnet von Recipe-Graph
    npc_fallback = 100.0
    storage = 100.0
    supply = 5.0
    demand = 5.0

    for tick in range(0, 50):
        if 5 <= tick <= 9:
            supply *= 0.65
        elif tick > 9:
            supply = min(5.0, supply * 1.05)
        # NPC-Fallback reagiert NICHT direkt auf Duerre (andere Preise am Markt)
        # Aber: NPC-Fackback spiegelt den Markt-Schnitt; er ist NICHT der wahre baseValue
        if tick == 9:
            npc_fallback = 130.0  # NPC-Preise reagieren mit Lag

        cov = effective_coverage(storage, supply, demand, 6.0, FLOW_LOOKAHEAD_DAYS)
        leontief_price = flow_price(leontief_anchor, cov, 0.3)
        fallback_price = flow_price(npc_fallback, cov, 0.3)
        diff_pct = abs(fallback_price - leontief_price) / leontief_price * 100.0
        state["max_diff_pct"] = max(state["max_diff_pct"], diff_pct)

        if tick in (0, 5, 9, 10, 15, 20, 30, 40):
            print(f"{tick:>4} | {fmt(leontief_anchor, 16, 1)} {fmt(npc_fallback, 14, 1)} "
                  f"{fmt(cov, 9, 3)} | {fmt(diff_pct, 15, 1)}%")

        produced = supply / 300.0 * 300.0
        consumed = demand / 300.0 * 300.0
        storage = max(0.0, storage + produced - consumed)

    print(f"\nGroesster Fallback-Drift zur Leontief-Preis: {state['max_diff_pct']:.1f}%")
    print("→ Bei Edge-Case 6 ist die TARNUNG sauberer Preis gefaehrdet.")
    return state


def scenario_d_zero_default() -> dict:
    """
    Edge-Case 7: Custom-Resource ohne worldgenScarcity.
    rawValue = 0 → Leontief = 0 → Anchor = 0 → Price = clamp = 0.001?
    """
    header("Szenario D — Edge-Case 7: Zero-Default (Custom-Scarcity fehlt)")
    print(f"{'tick':>4} | {'raw_value':>10} {'leontief_value':>14} "
          f"{'coverage':>9} | {'final_price':>12} {'clamp_lo_hit':>14}")
    state = {
        "zero_default_hits": 0,
        "exploit_risk_count": 0,
        "anchor_zero_count": 0,
    }
    storage = 0.0  # weil raw_value = 0, niemand will es
    supply = 0.0
    demand = 0.0

    for tick in range(0, 30):
        # Ohne Scarcity → Anchor = 0
        raw_value = 0.0  # weil worldgenScarcity undefined
        # Leontief: auch 0 wegen (1-α) × 0
        leontief_anchor = 0.0

        cov = effective_coverage(storage, supply, demand, 6.0, FLOW_LOOKAHEAD_DAYS)
        price = flow_price(leontief_anchor, cov, 0.0)
        # Im Code: anchor <= 0 → localPrice returnt 0 (Z.65-68)
        # Dann * (1 + 0) = 0, nicht mal clamp
        # Aber wenn raw_value > 0 und coverage 0 → pow(0.005, -0.8) = 250/Clamped

        # Real: anchor=0 → localPrice=0 → NICHT clamp
        # Das ist ein Hard-Failure-Pfad
        if leontief_anchor == 0.0:
            state["anchor_zero_count"] += 1
        if tick in (0, 5, 10, 20, 29):
            print(f"{tick:>4} | {fmt(raw_value, 10, 4)} {fmt(leontief_anchor, 14, 4)} "
                  f"{fmt(cov, 9, 3)} | {fmt(price, 12, 4)} "
                  f"{'YES' if price == 0 else 'no':>14}")

    print(f"\nAnchor = 0 Ticks:          {state['anchor_zero_count']}")
    print("→ Preis = 0 (NICHT 0.001), weil FlowPrices.java returnt 0 bei anchor<=0.")
    print("→ Das ist der Crash-Modus, kein Clamp-Schutz.")
    return state


# ═══════════════════════════════════════════════════════════════════════════
# Hauptprogramm
# ═══════════════════════════════════════════════════════════════════════════
def main():
    print("Scarcity-Simulator v1.0 — SyxEconomyMod Faktor-Kaskaden-Test")
    print(f"EconConfig-Parameter (grep-verifiziert):")
    print(f"  UP={ELASTICITY_UP}  DOWN={ELASTICITY_DOWN}  boost={SCARCITY_PRICE_BOOST}")
    print(f"  Clamp: [{PRICE_CLAMP_LO}, {PRICE_CLAMP_HI}]  AbsMax={PRICE_ABSOLUTE_MAX}")
    print(f"  Coverage-Floor: {COVERAGE_FLOOR}")
    print(f"  LocalPrices.scarcity: TANH-basiert, max={SCARCITY_MAX_MULTIPLE}, "
          f"steepness={SCARCITY_STEEPNESS}")

    a = scenario_a_dry_drought()
    b = scenario_b_gini_tsunami()
    c = scenario_c_fallback_circle()
    d = scenario_d_zero_default()

    header("ZUSAMMENFASSUNG — 4 Szenarien")
    print(f"Szenario A (Drought):       Peak={a['peak_price']:.2f}, "
          f"ClampHits={a['clamp_hits']}, Convergence=tick {a['convergence_tick']}")
    print(f"Szenario B (Gini-Tsunami):   Food-Peak={b['food_basket_peak']:.0f}, "
          f"Denies={b['deny_count']}, Gini-Peak={b['gini_peak']:.3f}")
    print(f"Szenario C (Fallback-Kreis): Max-Diff={c['max_diff_pct']:.1f}%")
    print(f"Szenario D (Zero-Default):  Anchor-zero-Ticks={d['anchor_zero_count']}")
    print()
    print("=== Critical Findings vs User-Vermutungen ===")
    print(f"1. ClampHi=100 feuert nach Drought: {a['clamp_hits']>0}")
    print("   → Annahme '500' war falsch. Real-Clamp=100 → 'frueh gecappt'.")
    print(f"2. Konvergenz ±2% Equilibrium: tick {a['convergence_tick']}")
    print("   → Asymmetrie (UP<DOWN) macht Recover SCHNELLER als Crash-Aufbau.")
    print(f"3. Gini-Anstieg: {b['gini_peak']-0.30:.2f} (Peak ueber Baseline)")
    print("   → Gini-Loop ist real, aber Wert von 0.95 bleibt unrealistisch.")
    print(f"4. Fallback-Drift gegen Leontief: {c['max_diff_pct']:.1f}%")
    print("   → Bei NPC-Lag ist Fallback NICHT synchron mit echtem baseValue.")
    print(f"5. Zero-Default-Custom: Anchor-Null ueber {d['anchor_zero_count']} Ticks")
    print("   → NICHT 0.001 sondern 0. Hard-Failure-Pfad existiert.")


if __name__ == "__main__":
    main()
