#!/usr/bin/env python3
"""
SyxEconomyMod — Rebalancing Diagnostic Dashboard
================================================    Liest die CSV-Exporte von DiagnosticExporter ein und erzeugt sechs
    Analyse-Plots zur Rebalancing-Validierung.

Benötigt: pandas, matplotlib, numpy
Install:  pip install pandas matplotlib numpy

Usage:
    python rebalance_plots.py [--dir <diagnostics-dir>] [--out <output-dir>]

Wenn kein --dir angegeben wird, sucht das Skript automatisch in:
    ~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics/

CSV-Format (von DiagnosticExporter):
    rebalance_macro_<epoch>.csv   — eine Zeile pro Tag, 31 Spalten
    rebalance_resources_<epoch>.csv — Long-Format, eine Zeile pro Resource × Tag

Sentinel:
    days_of_supply = -1.0 → kein aktueller Bedarf (demand == 0)
    housing_*_last_tick  → 0 außerhalb des Saison-Ticks (4×/Jahr)
"""

import argparse
import glob
import os
import sys
from pathlib import Path

import matplotlib
import matplotlib.pyplot as plt
import matplotlib.ticker as mticker
import numpy as np
import pandas as pd
from numpy.polynomial import polynomial as NPoly

# ── Agg-Backend für Headless/Server (Jupyter überschreibt das selbst) ─────────
if "IPython" not in sys.modules:
    matplotlib.use("Agg")

# ── Styling ───────────────────────────────────────────────────────────────────
plt.rcParams.update({
    "figure.dpi": 150,
    "savefig.dpi": 150,
    "savefig.bbox": "tight",
    "font.family": "sans-serif",
    "font.size": 9,
    "axes.titlesize": 11,
    "axes.labelsize": 9,
    "legend.fontsize": 7,
    "xtick.labelsize": 7,
    "ytick.labelsize": 7,
})

# ── Farbskalen ────────────────────────────────────────────────────────────────
MACRO_COLORS = {
    "gini":           "#d62728",
    "treasury":       "#2ca02c",
    "population":     "#1f77b4",
    "total_money":    "#ff7f0e",
    "mean_wage":      "#9467bd",
    "wage_share":     "#8c564b",
    "food_days":      "#e377c2",
    "audit_delta":    "#7f7f7f",
}

SEASON_CMAP = plt.cm.Set2
RESOURCE_CMAP = plt.cm.viridis


# ═══════════════════════════════════════════════════════════════════════════════
#  DATA LOADING
# ═══════════════════════════════════════════════════════════════════════════════

def find_latest_csvs(diag_dir: str):
    """Findet das aktuellste Macro-/Resource-CSV-Paar im Diagnose-Verzeichnis."""
    macro_files = sorted(
        glob.glob(os.path.join(diag_dir, "rebalance_macro_*.csv")),
        reverse=True,
    )
    resource_files = sorted(
        glob.glob(os.path.join(diag_dir, "rebalance_resources_*.csv")),
        reverse=True,
    )

    if not macro_files:
        raise FileNotFoundError(f"Keine rebalance_macro_*.csv in {diag_dir}")
    if not resource_files:
        raise FileNotFoundError(f"Keine rebalance_resources_*.csv in {diag_dir}")

    return macro_files[0], resource_files[0]


def load_data(diag_dir: str | None = None):
    """Lädt Macro- und Resource-CSVs und reichert derivative Spalten an.

    Returns
    -------
    df_macro : pd.DataFrame
    df_res   : pd.DataFrame (Long-Format, mit extra columns: coverage_pct,
                              price_ratio, days_of_supply_clean)
    """
    if diag_dir is None:
        diag_dir = os.path.expanduser(
            "~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics"
        )

    macro_path, resource_path = find_latest_csvs(diag_dir)

    print(f"Macro:     {os.path.basename(macro_path)}")
    print(f"Resources: {os.path.basename(resource_path)}")

    df_macro = pd.read_csv(macro_path)
    df_res = pd.read_csv(resource_path)

    # ── Derivative Spalten ────────────────────────────────────────────────
    # Sentinel -1.0 → NaN (kein Bedarf = keine Aussage über Knappheit)
    df_res["days_of_supply_clean"] = df_res["days_of_supply"].where(
        df_res["days_of_supply"] >= 0, np.nan
    )
    df_res["coverage_pct"] = df_res["coverage"] * 100.0

    # Preis-Drift: market / anchor (wie stark weicht der Markt vom Anker ab?)
    df_res["price_ratio"] = np.where(
        df_res["anchor_price"] > 0,
        df_res["market_price"] / df_res["anchor_price"],
        np.nan,
    )

    # ── Macro-Derivate ───────────────────────────────────────────────────
    if "season" in df_macro.columns:
        season_names = {0: "Frühling", 1: "Sommer", 2: "Herbst", 3: "Winter"}
        df_macro["season_name"] = df_macro["season"].map(season_names)

    # housing_rent_last_tick ist per-Tick — für kumulierte Sicht:
    # cumsum über die Saison, dann Reset am Saison-Wechsel.
    # Vereinfachung: rollendes 4-Tage-Mittel für Visualisierung.
    df_macro["housing_rent_smooth"] = (
        df_macro["housing_rent_last_tick"].rolling(4, min_periods=1).mean()
    )

    return df_macro, df_res


def load_firms_data(diag_dir: str | None = None):
    """Lädt die Firms-CSV (rebalance_firms_*.csv).

    Returns None wenn keine Firms-CSV existiert (vor v1.8.0).
    """
    if diag_dir is None:
        diag_dir = os.path.expanduser(
            "~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics"
        )
    firms_files = sorted(
        glob.glob(os.path.join(diag_dir, "rebalance_firms_*.csv")),
        reverse=True,
    )
    if not firms_files:
        print("⚠ Keine rebalance_firms_*.csv gefunden — Firm-Plots werden übersprungen.")
        return None
    print(f"Firms:     {os.path.basename(firms_files[0])}")
    return pd.read_csv(firms_files[0])


# ═══════════════════════════════════════════════════════════════════════════════
#  PLOT 1: RESOURCE-SCARCITY-HEATMAP
# ═══════════════════════════════════════════════════════════════════════════════

def plot_scarcity_heatmap(
    df_res: pd.DataFrame,
    ax: plt.Axes | None = None,
    max_resources: int = 50,
    cmap: str = "RdYlGn_r",
    vmin: float = 0.0,
    vmax: float = 30.0,
) -> plt.Axes:
    """Resource × Spieltag Heatmap der days_of_supply (Knappheits-Indikator).

    Niedrige Werte (rot) = Engpass. Hohe Werte (grün) = Überschuss.
    Ressourcen ohne Bedarf (NaN) bleiben weiss.
    """
    if ax is None:
        _, ax = plt.subplots(figsize=(14, 8))

    # ── Top-N Ressourcen nach durchschnittlichem Supply auswählen ────────
    res_mean = (
        df_res.groupby("resource")["days_of_supply_clean"]
        .mean()
        .sort_values()
    )
    top_resources = res_mean.head(max_resources).index.tolist()

    df_plot = df_res[df_res["resource"].isin(top_resources)].copy()

    # Pivot: resource × game_day
    pivot = df_plot.pivot_table(
        index="resource",
        columns="game_day",
        values="days_of_supply_clean",
        aggfunc="mean",
    )

    # Clipping für bessere Farbtrennung
    pivot_clipped = pivot.clip(lower=vmin, upper=vmax)

    im = ax.imshow(
        pivot_clipped.values,
        aspect="auto",
        cmap=cmap,
        vmin=vmin,
        vmax=vmax,
        interpolation="nearest",
    )

    # Achsenbeschriftung
    ax.set_yticks(range(len(pivot_clipped.index)))
    ax.set_yticklabels(pivot_clipped.index, fontsize=6)

    # X-Achse: jeden 10. Tag labeln
    cols = pivot_clipped.columns
    step = max(1, len(cols) // 15)
    tick_positions = list(range(0, len(cols), step))
    tick_labels = [str(cols[i]) for i in tick_positions]
    ax.set_xticks(tick_positions)
    ax.set_xticklabels(tick_labels, rotation=45, ha="right", fontsize=6)

    cbar = plt.colorbar(im, ax=ax, shrink=0.82, pad=0.02)
    cbar.set_label("Days of Supply", fontsize=8)
    cbar.ax.tick_params(labelsize=6)

    ax.set_title(
        f"Resource Scarcity Heatmap (top {max_resources} nach Knappheit)\n"
        f"Rot = Engpass (< 3 Tage), Grün = Überschuss, Weiß = kein Bedarf",
        fontsize=10,
    )
    ax.set_xlabel("Game Day", fontsize=8)
    ax.set_ylabel("Resource", fontsize=8)

    return ax


# ═══════════════════════════════════════════════════════════════════════════════
#  PLOT 2: MACRO-TREND-STACKED
# ═══════════════════════════════════════════════════════════════════════════════

def plot_macro_trends(
    df_macro: pd.DataFrame,
    figsize: tuple = (16, 14),
) -> plt.Figure:
    """Stacked Subplots der wichtigsten Makro-Indikatoren über die Zeit.

    Subplots:
      A — Population (Fläche)
      B — Gini-Koeffizient (Linie)
      C — Treasury + Total Money (Flächen, gestapelt)
      D — Löhne: mean_wage + actual_mean_wage (Linien)
      E — Wage Share (Linie, Anteil der Löhne am BIP)
      F — Audit-Delta (Balken, grün=Überschuss, rot=Defizit)
    """
    fig, axes = plt.subplots(3, 2, figsize=figsize, sharex=True)
    (ax_pop, ax_gini), (ax_treasury, ax_wages), (ax_share, ax_audit) = axes

    x = df_macro["game_day"]

    # ── A: Population ────────────────────────────────────────────────────
    ax_pop.fill_between(x, df_macro["population"], alpha=0.3, color=MACRO_COLORS["population"])
    ax_pop.plot(x, df_macro["population"], color=MACRO_COLORS["population"], linewidth=1.2)
    if "deaths" in df_macro.columns:
        ax_pop.plot(x, df_macro["deaths"], color="#d62728", linewidth=0.6, alpha=0.7, label="Deaths")
    if "emigrations" in df_macro.columns:
        ax_pop.plot(x, df_macro["emigrations"], color="#ff7f0e", linewidth=0.6, alpha=0.7, label="Emigrations")
    ax_pop.set_ylabel("Population")
    ax_pop.set_title("A — Population & Demographics")
    ax_pop.legend(loc="upper left", framealpha=0.7)
    ax_pop.yaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"{v:,.0f}"))
    ax_pop.grid(True, alpha=0.3)

    # ── B: Gini ─────────────────────────────────────────────────────────
    ax_gini.plot(x, df_macro["gini"], color=MACRO_COLORS["gini"], linewidth=1.5)
    ax_gini.fill_between(x, df_macro["gini"], alpha=0.15, color=MACRO_COLORS["gini"])
    # Referenzlinien
    ax_gini.axhline(0.3, color="green", linestyle="--", alpha=0.4, linewidth=0.8, label="0.30 (niedrig)")
    ax_gini.axhline(0.5, color="orange", linestyle="--", alpha=0.4, linewidth=0.8, label="0.50 (mittel)")
    ax_gini.axhline(0.7, color="red", linestyle="--", alpha=0.4, linewidth=0.8, label="0.70 (hoch)")
    ax_gini.set_ylabel("Gini")
    ax_gini.set_title("B — Gini Coefficient (Ungleichheit)")
    ax_gini.legend(loc="upper left", framealpha=0.7)
    ax_gini.set_ylim(0, 1)
    ax_gini.grid(True, alpha=0.3)

    # ── C: Treasury + Total Money ───────────────────────────────────────
    ax_treasury.fill_between(
        x, df_macro["treasury"], alpha=0.35, color=MACRO_COLORS["treasury"], label="Treasury"
    )
    ax_treasury.plot(
        x, df_macro["treasury"], color=MACRO_COLORS["treasury"], linewidth=1.2
    )
    ax_treasury.plot(
        x, df_macro["total_money"], color=MACRO_COLORS["total_money"],
        linewidth=0.9, alpha=0.8, label="Total Money (Umlauf)"
    )
    ax_treasury.set_ylabel("Money")
    ax_treasury.set_title("C — Treasury & Money Supply")
    ax_treasury.legend(loc="upper left", framealpha=0.7)
    ax_treasury.yaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"{v:,.0f}"))
    ax_treasury.grid(True, alpha=0.3)

    # ── D: Wages ────────────────────────────────────────────────────────
    if "mean_wage" in df_macro.columns:
        ax_wages.plot(
            x, df_macro["mean_wage"], color=MACRO_COLORS["mean_wage"],
            linewidth=1.3, label="Mean Wage (theoretisch)"
        )
    if "actual_mean_wage" in df_macro.columns:
        ax_wages.plot(
            x, df_macro["actual_mean_wage"], color="#17becf",
            linewidth=1.1, alpha=0.8, linestyle="--", label="Actual Mean Wage"
        )
    if "wage_config_max" in df_macro.columns and "mean_wage" not in df_macro.columns:
        ax_wages.plot(
            x, df_macro["wage_config_max"], color="#9467bd",
            linewidth=1.0, alpha=0.7, linestyle=":", label="Wage Cap (config)"
        )
    ax_wages.set_ylabel("Wage")
    ax_wages.set_title("D — Wages (Mean)")
    ax_wages.legend(loc="upper left", framealpha=0.7)
    ax_wages.yaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"{v:,.1f}"))
    ax_wages.grid(True, alpha=0.3)

    # ── E: Wage Share ───────────────────────────────────────────────────
    ax_share.fill_between(
        x, df_macro["wage_share"], alpha=0.25, color=MACRO_COLORS["wage_share"]
    )
    ax_share.plot(
        x, df_macro["wage_share"], color=MACRO_COLORS["wage_share"], linewidth=1.3
    )
    ax_share.axhline(0.5, color="gray", linestyle="--", alpha=0.5, linewidth=0.8)
    ax_share.set_ylabel("Wage Share")
    ax_share.set_title("E — Wage Share (Lohnquote)")
    ax_share.set_ylim(0, 1)
    ax_share.grid(True, alpha=0.3)

    # ── F: Audit Delta ──────────────────────────────────────────────────
    colors_audit = np.where(
        df_macro["audit_delta"] >= 0, MACRO_COLORS["treasury"], MACRO_COLORS["gini"]
    )
    ax_audit.bar(x, df_macro["audit_delta"], color=colors_audit, width=1.0, alpha=0.7)
    ax_audit.axhline(0, color="black", linewidth=0.5)
    ax_audit.set_ylabel("Audit Δ")
    ax_audit.set_title("F — Audit Delta (Einnahmen − Ausgaben)")
    ax_audit.yaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"{v:,.0f}"))
    ax_audit.grid(True, alpha=0.3, axis="y")

    # X-Achsen-Label nur auf unterster Reihe
    for ax_row in axes[-1]:
        ax_row.set_xlabel("Game Day")

    fig.suptitle(
        "SyxEconomyMod — Macro Trend Dashboard",
        fontsize=13,
        fontweight="bold",
        y=1.01,
    )
    fig.tight_layout()

    return fig


# ═══════════════════════════════════════════════════════════════════════════════
#  PLOT 3: ANCHER-VS-MARKT-PREIS-DRIFT PRO RESOURCE
# ═══════════════════════════════════════════════════════════════════════════════

def plot_price_drift(
    df_res: pd.DataFrame,
    top_n: int = 9,
    figsize: tuple = (16, 12),
) -> plt.Figure:
    """Zeigt für die Top-N Ressourcen (nach durchschnittlichem Drift-Betrag)
    den Anchor-Preis und den Markt-Preis sowie das Ratio im Zeitverlauf.

    Subplot-Grid: 3 Spalten, ceil(top_n/3) Zeilen.
    """
    # ── Top-N Ressourcen nach Preis-Drift (absoluter Abstand) ────────────
    drift_abs = (
        df_res.groupby("resource")
        .apply(lambda g: (g["price_ratio"] - 1.0).abs().mean(), include_groups=False)
        .sort_values(ascending=False)
    )
    top_resources = drift_abs.head(top_n).index.tolist()

    n_cols = 3
    n_rows = (top_n + n_cols - 1) // n_cols

    fig, axes = plt.subplots(n_rows, n_cols, figsize=figsize, sharex=True)
    axes_flat = axes.flatten() if hasattr(axes, "flatten") else [axes]

    for i, resource in enumerate(top_resources):
        ax = axes_flat[i]
        df_r = df_res[df_res["resource"] == resource]

        x = df_r["game_day"]

        # Linke Achse: Preise
        ax.plot(
            x, df_r["anchor_price"],
            color="#1f77b4", linewidth=1.2, alpha=0.9, label="Anchor"
        )
        ax.plot(
            x, df_r["market_price"],
            color="#ff7f0e", linewidth=1.2, alpha=0.9, label="Market"
        )
        ax.set_ylabel("Price", fontsize=7, color="#1f77b4")
        ax.tick_params(axis="y", labelcolor="#1f77b4", labelsize=6)

        # Rechte Achse: Ratio
        ax2 = ax.twinx()
        ax2.plot(
            x, df_r["price_ratio"],
            color="#2ca02c", linewidth=0.8, alpha=0.7, linestyle=":",
            label="Ratio (M/A)"
        )
        ax2.axhline(1.0, color="gray", linestyle="--", alpha=0.3, linewidth=0.6)
        ax2.set_ylabel("Ratio", fontsize=7, color="#2ca02c")
        ax2.tick_params(axis="y", labelcolor="#2ca02c", labelsize=6)

        mean_ratio = df_r["price_ratio"].mean()
        ax.set_title(
            f"{resource}  (Ø-Ratio: {mean_ratio:.2f})",
            fontsize=8,
        )

        ax.grid(True, alpha=0.2)

        # Legend nur für erste Zeile
        if i < n_cols:
            lines1, labels1 = ax.get_legend_handles_labels()
            lines2, labels2 = ax2.get_legend_handles_labels()
            ax.legend(lines1 + lines2, labels1 + labels2, fontsize=6, loc="upper left")

    # Leere Subplots ausblenden
    for j in range(len(top_resources), len(axes_flat)):
        axes_flat[j].set_visible(False)

    for ax in axes_flat[-n_cols:]:
        if ax.get_visible():
            ax.set_xlabel("Game Day", fontsize=7)

    fig.suptitle(
        "Anchor vs Market Price Drift — Top Resources by Drift Magnitude",
        fontsize=12,
        fontweight="bold",
        y=1.01,
    )
    fig.tight_layout()

    return fig


# ═══════════════════════════════════════════════════════════════════════════════
#  PLOT 4: GINI-VS-TREASURY DOPPEL-ACHSE
# ═══════════════════════════════════════════════════════════════════════════════

def plot_gini_treasury(
    df_macro: pd.DataFrame,
    figsize: tuple = (14, 6),
    include_food: bool = True,
    include_wages: bool = True,
) -> plt.Figure:
    """Gini (linke Achse) vs Treasury (rechte Achse) mit optionalen Zusatz-Linien.

    Zeigt den Trade-off zwischen Ungleichheit und Staatskasse.
    Saisonale Hintergrundbänder markieren die vier Jahreszeiten.
    """
    fig, ax1 = plt.subplots(figsize=figsize)

    x = df_macro["game_day"]

    # ── Saisonale Hintergrundbänder ──────────────────────────────────────
    if "season" in df_macro.columns:
        season_colors = {0: "#e8f5e9", 1: "#fff3e0", 2: "#fce4ec", 3: "#e3f2fd"}
        for season_val, color in season_colors.items():
            mask = df_macro["season"] == season_val
            if mask.any():
                # Finde kontinuierliche Blöcke
                blocks = df_macro.loc[mask, "game_day"]
                if len(blocks) > 0:
                    # Vereinfachung: fülle den gesamten x-Bereich der Season
                    start = blocks.min()
                    end = blocks.max()
                    ax1.axvspan(start, end, alpha=0.12, color=color, zorder=0)

    # ── Linke Achse: Gini ──────────────────────────────────────────────
    color_gini = MACRO_COLORS["gini"]
    ax1.plot(x, df_macro["gini"], color=color_gini, linewidth=1.8, label="Gini")
    ax1.set_ylabel("Gini Coefficient", color=color_gini, fontsize=10)
    ax1.tick_params(axis="y", labelcolor=color_gini)
    ax1.set_ylim(0, 1)

    # Gini-Referenzzonen
    ax1.axhspan(0.0, 0.3, alpha=0.06, color="green", zorder=0)
    ax1.axhspan(0.3, 0.5, alpha=0.06, color="yellow", zorder=0)
    ax1.axhspan(0.5, 1.0, alpha=0.08, color="red", zorder=0)

    # ── Rechte Achse: Treasury ─────────────────────────────────────────
    ax2 = ax1.twinx()
    color_treasury = MACRO_COLORS["treasury"]
    ax2.fill_between(
        x, df_macro["treasury"], alpha=0.2, color=color_treasury, zorder=1
    )
    ax2.plot(
        x, df_macro["treasury"],
        color=color_treasury, linewidth=1.5, label="Treasury",
        zorder=2,
    )
    ax2.set_ylabel("Treasury", color=color_treasury, fontsize=10)
    ax2.tick_params(axis="y", labelcolor=color_treasury)
    ax2.yaxis.set_major_formatter(
        mticker.FuncFormatter(lambda v, _: f"{v:,.0f}")
    )

    # ── Optionale Zusatz-Linien ─────────────────────────────────────────
    if include_food and "food_days" in df_macro.columns:
        ax3 = ax1.twinx()
        ax3.spines["right"].set_position(("outward", 60))
        color_food = MACRO_COLORS["food_days"]
        ax3.plot(
            x, df_macro["food_days"],
            color=color_food, linewidth=1.0, alpha=0.6, linestyle="--",
            zorder=1.5,
        )
        ax3.set_ylabel("Food Days", color=color_food, fontsize=8)
        ax3.tick_params(axis="y", labelcolor=color_food, labelsize=7)

    if include_wages and "mean_wage" in df_macro.columns:
        ax4 = ax1.twinx()
        ax4.spines["right"].set_position(("outward", 120))
        color_wage = MACRO_COLORS["mean_wage"]
        ax4.plot(
            x, df_macro["mean_wage"],
            color=color_wage, linewidth=0.8, alpha=0.5, linestyle="-.",
            zorder=1.5,
        )
        ax4.set_ylabel("Mean Wage", color=color_wage, fontsize=8)
        ax4.tick_params(axis="y", labelcolor=color_wage, labelsize=7)

    # ── Legende ─────────────────────────────────────────────────────────
    lines1, labels1 = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax1.legend(
        lines1 + lines2,
        labels1 + labels2,
        loc="upper left",
        framealpha=0.8,
        fontsize=8,
    )

    ax1.set_xlabel("Game Day")
    ax1.set_title(
        "Gini vs Treasury — Inequality / State Wealth Trade-off",
        fontsize=12,
        fontweight="bold",
    )
    ax1.grid(True, alpha=0.25)

    fig.tight_layout()
    return fig


# ═══════════════════════════════════════════════════════════════════════════════
#  PLOT 5: FIRM PROFITABILITY (STRUCTURAL UNPROFITABLE DETECTION)
# ═══════════════════════════════════════════════════════════════════════════════

def plot_firm_profitability(
    df_firms: pd.DataFrame,
    figsize: tuple = (16, 12),
) -> plt.Figure:
    """2×2 Grid zur Firmen-Profitabilitätsanalyse.

    Subplots:
      A — Profit per Day by Blueprint (Boxplot, zeigt strukturell unprofitable)
      B — Employees vs Target (Scatter, zeigt Über-/Unterbesetzung)
      C — Output vs Input Value (Scatter mit Profit-Farbcodierung)
      D — Workers Unpaid per Blueprint (Bar, zeigt Lohnausfall-Hotspots)
    """
    fig, axes = plt.subplots(2, 2, figsize=figsize)
    (ax_profit, ax_emp), (ax_io, ax_unpaid) = axes

    # ── Aktuellsten Tag nehmen (letzter game_day) ─────────────────────
    last_day = df_firms["game_day"].max()
    df_now = df_firms[df_firms["game_day"] == last_day].copy()

    if len(df_now) == 0:
        for ax in axes.flat:
            ax.text(0.5, 0.5, "Keine Firm-Daten am letzten Tag",
                    ha="center", va="center", transform=ax.transAxes)
        return fig

    # ── A: Profit per Day by Blueprint (Boxplot) ──────────────────────
    blueprints_sorted = (
        df_now.groupby("blueprint")["profit_per_day"]
        .median()
        .sort_values()
        .index.tolist()
    )
    profit_data = [
        df_now[df_now["blueprint"] == bp]["profit_per_day"].values
        for bp in blueprints_sorted
    ]

    bp_colors = [
        "#d62728" if np.median(v) <= 0 else "#2ca02c"
        for v in profit_data
    ]

    bp = ax_profit.boxplot(
        profit_data,
        orientation="horizontal",
        patch_artist=True,
        widths=0.6,
        flierprops={"markersize": 2, "alpha": 0.4},
        medianprops={"color": "black", "linewidth": 1.2},
    )
    for patch, color in zip(bp["boxes"], bp_colors):
        patch.set_facecolor(color)
        patch.set_alpha(0.4)

    ax_profit.set_yticks(range(1, len(blueprints_sorted) + 1))
    ax_profit.set_yticklabels(blueprints_sorted, fontsize=6)
    ax_profit.axvline(0, color="black", linewidth=0.8, linestyle="--")
    ax_profit.set_xlabel("Profit per Day")
    ax_profit.set_title(
        "A — Profit per Day by Blueprint\n(rot = Median ≤ 0 = strukturell unprofitabel)",
        fontsize=9,
    )
    ax_profit.grid(True, alpha=0.2, axis="x")

    # ── B: Employees vs Target ────────────────────────────────────────
    ax_emp.scatter(
        df_now["employees"],
        df_now["employed_target"],
        c=np.where(df_now["profit_per_day"] <= 0, "#d62728", "#2ca02c"),
        alpha=0.5,
        s=12,
        edgecolors="none",
    )
    max_val = max(df_now["employees"].max(), df_now["employed_target"].max()) * 1.1
    ax_emp.plot([0, max_val], [0, max_val], "k--", alpha=0.3, linewidth=0.6)
    ax_emp.set_xlim(0, max_val)
    ax_emp.set_ylim(0, max_val)
    ax_emp.set_xlabel("Actual Employees")
    ax_emp.set_ylabel("Employed Target")
    ax_emp.set_title(
        "B — Employees vs Target\n(Diagonale = perfekt; rot = unprofitabel)",
        fontsize=9,
    )
    ax_emp.grid(True, alpha=0.2)

    # ── C: Output vs Input Value (mit Profit-Farbcodierung) ───────────
    profit_norm = plt.Normalize(
        vmin=df_now["profit_per_day"].quantile(0.05),
        vmax=df_now["profit_per_day"].quantile(0.95),
    )
    sc = ax_io.scatter(
        df_now["total_input_value_per_day"],
        df_now["total_output_value_per_day"],
        c=df_now["profit_per_day"],
        cmap="RdYlGn",
        norm=profit_norm,
        alpha=0.6,
        s=15,
        edgecolors="none",
    )
    max_io = max(
        df_now["total_input_value_per_day"].max(),
        df_now["total_output_value_per_day"].max(),
    ) * 1.1
    ax_io.plot([0, max_io], [0, max_io], "k--", alpha=0.3, linewidth=0.6)
    ax_io.set_xlim(0, max_io)
    ax_io.set_ylim(0, max_io)
    ax_io.set_xlabel("Input Value per Day")
    ax_io.set_ylabel("Output Value per Day")
    ax_io.set_title(
        "C — Output vs Input Value\n(Diagonale = Break-even; Farbe = Profit)",
        fontsize=9,
    )
    ax_io.grid(True, alpha=0.2)
    cbar = plt.colorbar(sc, ax=ax_io, shrink=0.8)
    cbar.set_label("Profit/Day", fontsize=7)
    cbar.ax.tick_params(labelsize=6)

    # ── D: Workers Unpaid per Blueprint ───────────────────────────────
    unpaid_by_bp = (
        df_now.groupby("blueprint")["workers_unpaid"]
        .sum()
        .sort_values(ascending=True)
    )
    unpaid_by_bp = unpaid_by_bp[unpaid_by_bp > 0]

    if len(unpaid_by_bp) > 0:
        colors_unpaid = ["#d62728"] * len(unpaid_by_bp)
        ax_unpaid.barh(
            range(len(unpaid_by_bp)),
            unpaid_by_bp.values,
            color=colors_unpaid,
            alpha=0.7,
            height=0.6,
        )
        ax_unpaid.set_yticks(range(len(unpaid_by_bp)))
        ax_unpaid.set_yticklabels(unpaid_by_bp.index, fontsize=6)
    else:
        ax_unpaid.text(
            0.5, 0.5, "Keine unbezahlten Arbeiter 🎉",
            ha="center", va="center", transform=ax_unpaid.transAxes,
            fontsize=12, color="#2ca02c",
        )

    ax_unpaid.set_xlabel("Workers Unpaid (letzter Tick)")
    ax_unpaid.set_title(
        "D — Workers Unpaid per Blueprint\n(Lohnausfall-Hotspots)",
        fontsize=9,
    )
    ax_unpaid.grid(True, alpha=0.2, axis="x")

    fig.suptitle(
        f"Firm Profitability — Day {last_day}",
        fontsize=12,
        fontweight="bold",
        y=1.01,
    )
    fig.tight_layout()

    return fig




# ═══════════════════════════════════════════════════════════════════════════════
#  PLOT 6: EXPANSION SIGNALS vs MACRO INDICATORS
# ═══════════════════════════════════════════════════════════════════════════════

def plot_expansion_signals_correlation(
    df_macro: pd.DataFrame,
    df_firms: pd.DataFrame | None,
    has_macro_col: bool = False,
    has_firms_col: bool = False,
    figsize: tuple = (16, 12),
) -> plt.Figure:
    """Zeigt Korrelation zwischen Expansion-Druck (PriorityRegistry-Hints) und
    Wirtschaftslage (Treasury, Gini).

    Subplots:
      A — Signals + Treasury (Dual-Achse): Korreliert Expansion-Druck mit Staatskasse?
      B — Signals + Gini (Dual-Achse): Korreliert Expansion-Druck mit Ungleichheit?
      C — Per-Blueprint Heatmap: Welche Blueprints produzieren die meisten Hints?
      D — Scatter: Signals vs Treasury-Änderung am Folgetag (Lead/Lag-Analyse)
    """
    fig, axes = plt.subplots(2, 2, figsize=figsize)
    (ax_st, ax_sg), (ax_heat, ax_scatter) = axes

    x = df_macro["game_day"]

    # ── Fallback wenn Macro-Spalte fehlt ────────────────────────────────
    if not has_macro_col:
        for ax in axes.flat:
            ax.text(
                0.5, 0.5,
                "Spalte 'priority_expansion_signals'\nfehlt in der Macro-CSV.\n"
                "Neuer Spiel-Lauf mit aktivierter Diagnostik nötig.",
                ha="center", va="center", transform=ax.transAxes,
                fontsize=10, color="gray", style="italic",
            )
        fig.suptitle("Expansion Signals Analysis — Daten fehlen", fontsize=13, fontweight="bold")
        fig.tight_layout()
        return fig

    signals = df_macro["priority_expansion_signals"]

    # ── A: Signals + Treasury (Dual-Achse) ─────────────────────────────
    color_signals = "#e74c3c"
    ax_st.fill_between(x, signals, alpha=0.25, color=color_signals, zorder=1)
    ax_st.plot(x, signals, color=color_signals, linewidth=1.3, label="Expansion Signals", zorder=2)
    ax_st.set_ylabel("Signals / Tag", color=color_signals, fontsize=9)
    ax_st.tick_params(axis="y", labelcolor=color_signals)
    ax_st.yaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"{v:,.0f}"))

    ax_st2 = ax_st.twinx()
    color_t = MACRO_COLORS["treasury"]
    ax_st2.plot(x, df_macro["treasury"], color=color_t, linewidth=1.5, alpha=0.85, label="Treasury")
    ax_st2.set_ylabel("Treasury", color=color_t, fontsize=9)
    ax_st2.tick_params(axis="y", labelcolor=color_t)
    ax_st2.yaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"{v:,.0f}"))

    ax_st.set_title("A — Expansion Signals vs Treasury", fontsize=10)
    ax_st.grid(True, alpha=0.2)
    lines_a, labels_a = ax_st.get_legend_handles_labels()
    lines_b, labels_b = ax_st2.get_legend_handles_labels()
    ax_st.legend(lines_a + lines_b, labels_a + labels_b, loc="upper left", fontsize=7, framealpha=0.7)

    # ── B: Signals + Gini (Dual-Achse) ────────────────────────────────
    ax_sg.fill_between(x, signals, alpha=0.25, color=color_signals, zorder=1)
    ax_sg.plot(x, signals, color=color_signals, linewidth=1.3, label="Expansion Signals", zorder=2)
    ax_sg.set_ylabel("Signals / Tag", color=color_signals, fontsize=9)
    ax_sg.tick_params(axis="y", labelcolor=color_signals)

    ax_sg2 = ax_sg.twinx()
    color_g = MACRO_COLORS["gini"]
    ax_sg2.plot(x, df_macro["gini"], color=color_g, linewidth=1.5, alpha=0.85, label="Gini")
    ax_sg2.set_ylabel("Gini", color=color_g, fontsize=9)
    ax_sg2.tick_params(axis="y", labelcolor=color_g)
    ax_sg2.set_ylim(0, 1)

    ax_sg.set_title("B — Expansion Signals vs Gini", fontsize=10)
    ax_sg.grid(True, alpha=0.2)
    lines_c, labels_c = ax_sg.get_legend_handles_labels()
    lines_d, labels_d = ax_sg2.get_legend_handles_labels()
    ax_sg.legend(lines_c + lines_d, labels_c + labels_d, loc="upper left", fontsize=7, framealpha=0.7)

    # ── C: Per-Blueprint Heatmap (Firms-CSV) ──────────────────────────
    if has_firms_col and df_firms is not None and len(df_firms) > 0:
        # Top-15 Blueprints nach gesamten Signals auswählen
        bp_signal_totals = (
            df_firms.groupby("blueprint")["expansion_signals"]
            .sum()
            .sort_values(ascending=False)
            .head(15)
        )
        top_bps = bp_signal_totals.index.tolist()

        df_plot = df_firms[df_firms["blueprint"].isin(top_bps)].copy()

        pivot = df_plot.pivot_table(
            index="blueprint",
            columns="game_day",
            values="expansion_signals",
            aggfunc="sum",
        )

        if pivot.shape[1] > 0:
            # Log-Scale für bessere Unterscheidung (0 ist am häufigsten)
            pivot_log = np.log1p(pivot)
            im = ax_heat.imshow(
                pivot_log.values,
                aspect="auto",
                cmap="YlOrRd",
                interpolation="nearest",
            )
            ax_heat.set_yticks(range(len(pivot.index)))
            ax_heat.set_yticklabels(pivot.index, fontsize=6)
            cols = pivot.columns
            step = max(1, len(cols) // 12)
            tick_pos = list(range(0, len(cols), step))
            ax_heat.set_xticks(tick_pos)
            ax_heat.set_xticklabels(
                [str(cols[i]) for i in tick_pos], rotation=45, ha="right", fontsize=6
            )
            cbar = plt.colorbar(im, ax=ax_heat, shrink=0.82, pad=0.02)
            cbar.set_label("log(1 + signals)", fontsize=7)
            cbar.ax.tick_params(labelsize=6)
            ax_heat.set_title(
                "C — Per-Blueprint Expansion Signals (Top 15)\n"
                f"rot = viele Hints (wer hat Druck?)",
                fontsize=9,
            )
            ax_heat.set_xlabel("Game Day", fontsize=8)
            ax_heat.set_ylabel("Blueprint", fontsize=8)
        else:
            ax_heat.text(0.5, 0.5, "Keine Signal-Daten in Firms-CSV",
                         ha="center", va="center", transform=ax_heat.transAxes)
            ax_heat.set_title("C — Per-Blueprint Expansion Signals", fontsize=9)
    else:
        ax_heat.text(
            0.5, 0.5,
            "expansion_signals-Spalte fehlt\nin rebalance_firms CSV.",
            ha="center", va="center", transform=ax_heat.transAxes,
            fontsize=10, color="gray", style="italic",
        )
        ax_heat.set_title("C — Per-Blueprint Expansion Signals", fontsize=9)
    # ── D: Scatter — Signals vs Treasury-Änderung (Lead/Lag) ──────────
    # Idee: Korrelieren Signals HOHEM Druck mit Treasury-DROP am Folgetag?
    # Oder: bei hohem Druck steigt Treasury (wegen Expansion → mehr Output)?
    df_scatter = df_macro[["game_day", "priority_expansion_signals", "treasury"]].copy()
    df_scatter["treasury_delta_next"] = df_scatter["treasury"].diff().shift(-1)

    valid = df_scatter.dropna(subset=["treasury_delta_next"])
    if len(valid) > 5:
        # Farbcodierung nach Gini (falls vorhanden)
        if "gini" in df_macro.columns:
            colors_scatter = df_macro.loc[valid.index, "gini"]
            cmap_scatter = plt.cm.RdYlGn_r
            norm_scatter = plt.Normalize(vmin=0.2, vmax=0.8)
            label_cbar = "Gini"
        else:
            colors_scatter = color_signals
            cmap_scatter = None
            norm_scatter = None
            label_cbar = ""

        sc = ax_scatter.scatter(
            valid["priority_expansion_signals"],
            valid["treasury_delta_next"],
            c=colors_scatter,
            cmap=cmap_scatter,
            norm=norm_scatter,
            alpha=0.5,
            s=18,
            edgecolors="none",
        )
        if cmap_scatter is not None:
            cbar_sc = plt.colorbar(sc, ax=ax_scatter, shrink=0.8, pad=0.02)
            cbar_sc.set_label(label_cbar, fontsize=7)
            cbar_sc.ax.tick_params(labelsize=6)

        # Trendlinie
        try:
            coeffs = NPoly.polyfit(
                valid["priority_expansion_signals"].values,
                valid["treasury_delta_next"].values,
                deg=1,
            )
            x_trend = np.linspace(
                valid["priority_expansion_signals"].min(),
                valid["priority_expansion_signals"].max(), 50,
            )
            y_trend = NPoly.polyval(x_trend, coeffs)
            ax_scatter.plot(
                x_trend, y_trend, color="black", linewidth=1.2, alpha=0.5,
                linestyle="--", label=f"Trend: y={coeffs[1]:+.1f}x+{coeffs[0]:+.0f}",
            )
            ax_scatter.legend(fontsize=7, loc="best", framealpha=0.7)
        except Exception as e:
            ax_scatter.text(0.5, 0.5, f"Trendlinie fehlgeschlagen: {e}",
                           ha="center", va="center", transform=ax_scatter.transAxes,
                           fontsize=7, color="gray")

        ax_scatter.axhline(0, color="gray", linewidth=0.5, alpha=0.5)
        ax_scatter.set_xlabel("Expansion Signals (pro Tag)", fontsize=9)
        ax_scatter.set_ylabel("Treasury Δ am Folgetag", fontsize=9)
        ax_scatter.set_title(
            "D — Signals → Treasury-Änderung (Lead+1)\n"
            "Rot=hoher Gini; steigt Treasury mit Signals?",
            fontsize=9,
        )
        ax_scatter.yaxis.set_major_formatter(
            mticker.FuncFormatter(lambda v, _: f"{v:,.0f}")
        )
        ax_scatter.grid(True, alpha=0.2)
    else:
        ax_scatter.text(
            0.5, 0.5, "Zu wenig Daten für Scatter",
            ha="center", va="center", transform=ax_scatter.transAxes,
        )

    # ── X-Achse für oberen Subplot-Row ────────────────────────────────
    ax_st.set_xlabel("Game Day", fontsize=8)
    ax_sg.set_xlabel("Game Day", fontsize=8)

    fig.suptitle(
        "Expansion Signals Analysis — PriorityRegistry-Druck vs Wirtschaftslage",
        fontsize=13,
        fontweight="bold",
        y=1.01,
    )
    fig.tight_layout()
    return fig


def main():
    parser = argparse.ArgumentParser(
        description="SyxEconomyMod Rebalancing Diagnostic Dashboard"
    )
    parser.add_argument(
        "--dir",
        default=None,
        help="Pfad zum diagnostics-Verzeichnis (default: ~/.local/share/.../diagnostics)",
    )
    parser.add_argument(
        "--out",
        default="./diagnostics_plots",
        help="Ausgabeverzeichnis für PNG-Dateien (default: ./diagnostics_plots)",
    )
    parser.add_argument(
        "--max-resources",
        type=int,
        default=50,
        help="Maximale Anzahl Ressourcen in der Heatmap (default: 50)",
    )
    args = parser.parse_args()

    # ── Daten laden ─────────────────────────────────────────────────────
    try:
        df_macro, df_res = load_data(args.dir)
    except FileNotFoundError as e:
        print(f"FEHLER: {e}", file=sys.stderr)
        print(
            "Tipp: Exportiere zuerst CSV-Daten im Spiel mit "
            "EconConfig.diagnosticsExportEnabled = true",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"Macro:  {len(df_macro)} Tage, {len(df_macro.columns)} Spalten")
    print(f"Res:    {len(df_res)} Zeilen, {df_res['resource'].nunique()} Ressourcen")
    print(f"Tage:   {df_macro['game_day'].min()} – {df_macro['game_day'].max()}")
    print(f"Gini:   {df_macro['gini'].mean():.3f} (ø)")
    print()

    # ── Output-Verzeichnis ──────────────────────────────────────────────
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    # ── Plot 1: Scarcity Heatmap ────────────────────────────────────────
    print("[1/6] Resource Scarcity Heatmap …")
    fig1, ax1 = plt.subplots(figsize=(16, 9))
    plot_scarcity_heatmap(df_res, ax=ax1, max_resources=args.max_resources)
    fig1.tight_layout()
    path1 = out_dir / "01_scarcity_heatmap.png"
    fig1.savefig(path1)
    plt.close(fig1)
    print(f"      → {path1}")

    # ── Plot 2: Macro Trends ────────────────────────────────────────────
    print("[2/6] Macro Trend Stacked …")
    fig2 = plot_macro_trends(df_macro)
    path2 = out_dir / "02_macro_trends.png"
    fig2.savefig(path2)
    plt.close(fig2)
    print(f"      → {path2}")

    # ── Plot 3: Price Drift ─────────────────────────────────────────────
    print("[3/6] Anchor vs Market Price Drift …")
    fig3 = plot_price_drift(df_res, top_n=9)
    path3 = out_dir / "03_price_drift.png"
    fig3.savefig(path3)
    plt.close(fig3)
    print(f"      → {path3}")

    # ── Plot 4: Gini vs Treasury ────────────────────────────────────────
    print("[4/6] Gini vs Treasury …")
    fig4 = plot_gini_treasury(df_macro)
    path4 = out_dir / "04_gini_vs_treasury.png"
    fig4.savefig(path4)
    plt.close(fig4)
    print(f"      → {path4}")

    # ── Plot 5: Firm Profitability ─────────────────────────────────────
    df_firms = load_firms_data(args.dir)
    if df_firms is not None:
        print("[5/6] Firm Profitability …")
        print(f"Firms:   {len(df_firms)} Zeilen, {df_firms['blueprint'].nunique()} Blueprints")
        fig5 = plot_firm_profitability(df_firms)
        path5 = out_dir / "05_firm_profitability.png"
        fig5.savefig(path5)
        plt.close(fig5)
        print(f"      → {path5}")
    else:
        print("[5/6] Firm Profitability … übersprungen (keine CSV)")

    # ── Plot 6: Expansion Signals vs Macro ────────────────────────────
    has_macro_signals = "priority_expansion_signals" in df_macro.columns
    has_firms_signals = df_firms is not None and "expansion_signals" in df_firms.columns

    if has_macro_signals or has_firms_signals:
        print("[6/6] Expansion Signals Analysis …")
        fig6 = plot_expansion_signals_correlation(
            df_macro, df_firms,
            has_macro_col=has_macro_signals, has_firms_col=has_firms_signals,
        )
        path6 = out_dir / "06_expansion_signals.png"
        fig6.savefig(path6)
        plt.close(fig6)
        print(f"      → {path6}")
    else:
        print("[6/6] Expansion Signals Analysis … übersprungen (Spalten fehlen — neuer Spiel-Lauf nötig)")

    print(f"\nFertig — {len(list(out_dir.glob('*.png')))} Plots in {out_dir.resolve()}/")


if __name__ == "__main__":
    main()
