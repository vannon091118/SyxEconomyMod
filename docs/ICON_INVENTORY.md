# Vanilla-Icon-Inventar für SyxEconomyMod

> **Version:** v1.7.2 | **Stand:** 2026-07-23 | **Spiel:** V71.44

> Übersicht aller im Songs-of-Syx-Client verfügbaren Icon-Quellen, die im Mod-UI genutzt werden können.

---

## 1. `UI.icons()` — die zentrale Icon-Bibliothek

Zugriff erfolgt über `import init.sprite.UI.UI;` und `UI.icons()`.
Das Objekt hat drei öffentliche Gruppen (`s`, `m`, `l`) für kleine, mittlere und große Icons.

### Verwendung im Code

```java
import init.sprite.UI.UI;
import init.sprite.UI.Icon;

Icon i = UI.icons().s.money;
i.render((SPRITE_RENDERER)r, x, y);
```

### `UI.icons().s` — kleine Icons (91 Felder)

> Geeignet für Tab-Reiter, kleine Buttons, Status-Anzeigen.

`all`, `i`, `magnifier`, `minifier`, `minimap`, `arrowUp`, `arrowDown`, `cancel`, `camera`, `crazy`, `menu`, `cog`, `question`, `storage`, `magnifierBig`, `minifierBig`, `human`, `hammer`, `column`, `vial`, `gift`, `plate`, `sword`, `money`, `crossheir`, `standard`, `temperature`, `eye`, `law`, `pickaxe`, `shield`, `capitol`, `sprout`, `trade`, `bow`, `fish`, `heart`, `citizen`, `slave`, `noble`, `world`, `admin`, `muster`, `time`, `ice`, `heat`, `pluses`, `squatter`, `fly`, `honor`, `bed`, `alert`, `arrow_right`, `arrow_left`, `plus`, `minus`, `allRight`, `circle`, `clock`, `death`, `dot`, `house`, `degrade`, `fist`, `armour`, `handOpen`, `speed`, `boom`, `drop`, `star`, `ship`, `chevrons`, `happy`, `soso`, `angry`, `faces`, `crown`, `flags`, `expand`, `wheel`, `flag`, `cameraBig`, `tolerence`, `headspike`, `jug`, `bars`, `shrine`, `temple`, `book`, `plus2`, `plusBig`, `copy`, `smallSkull`, `divWalk`, `divRun`, `typeCitizen`, `typeRetire`, `typeRecruit`, `typeSoldier`, `typeStudent`, `typePrison`, `typeTourist`, `typeRioter`, `typeCrazy`, `typeChild`, `typeParent`, `reproduction`, `typeGuard`, `trust`, `emissary`, `mask`.

### `UI.icons().m` — mittlere Icons (104 Felder)

> Geeignet für größere Buttons, Übersichten, Tooltips.

`i`, `clear_structure`, `capitol`, `furniture`, `raider`, `agriculture`, `fertility`, `cancel`, `terrain`, `storage_pullers`, `crossair`, `storage_pull`, `wall`, `anti`, `storage_push`, `noble`, `copy`, `wildlife`, `priority`, `foundation`, `baby`, `skull`, `descrimination`, `admin`, `familyTree`, `ok`, `questionmark`, `arrow_up`, `arrow_right`, `arrow_down`, `arrow_left`, `expand`, `shrink`, `citizen`, `rebellion`, `urn`, `stength`, `plus`, `minus`, `rotate`, `exit`, `repair`, `time`, `menu`, `wheel`, `city`, `flag`, `cog`, `openscroll`, `raw_materials`, `building`, `pickaxe`, `place_fill`, `shield`, `horn`, `clear_food`, `for_loose`, `for_tight`, `fast_forw`, `for_muster`, `circle_frame`, `circle_inner`, `cog_big`, `place_brush`, `place_rec`, `place_line`, `place_ellispse`, `place_rec_hollow`, `trash`, `menu2`, `law`, `overwrite`, `workshop`, `slave`, `water`, `sword`, `heart`, `lock`, `search`, `bow`, `fortification`, `disease`, `ceiling`, `wallceiling`, `chainsFree`, `coins`, `factions`, `place_ellispse_hollow`, `place_hex`, `place_hex_hollow`, `wall_opening`, `gov`, `advice`, `b_muster`, `b_for_tight`, `b_for_loose`, `b_run`, `b_guard`, `b_fire`, `b_fire_stop`, `b_chase`, `b_charge`, `b_stop`.

### `UI.icons().l` — große Icons (80 Felder)

> Geeignet für Panels, Berater-Dashboards, große Übersichten.

`i`, `agri`, `work`, `service`, `jobs`, `gov`, `thumbsDown`, `rebel`, `menu`, `world`, `battle`, `city`, `coin`, `flags`, `vial`, `tourist`, `book`, `up`, `infra`, `crate`, `crown`, `crossheir`, `swords`, `star`, `bannerPole`, `banners`, `clear_all`, `copy`, `copyRoom`, `repair`, `suspend`, `dia`, `square`, `prints`, `upgrade`, `mine`, `pasture`, `farm`, `fish`, `refiner`, `workshop`, `law`, `trainig`, `admin`, `breeding`, `decor`, `logistics`, `water`, `religion`, `dist`, `health`, `entertain`, `death`, `home`, `demolish`, `event`, `eventInactive`, `demolishRoad`.

---

## 2. `RESOURCE.icon()` — Ressourcen-Icons

Jede `RESOURCE`-Instanz (z. B. Holz, Stein, Brot) hat ein Icon, das direkt aus der Klasse abgerufen werden kann.

```java
RESOURCE res = RESOURCES.EDI().all().get(0).resource;
Icon icon = res.icon();
icon.render((SPRITE_RENDERER)r, x, y);
```

### Verwendungsideen in SyxEconomyMod

- **Preise-Tab:** Vor jeder Ressourcenzeile das Ressourcen-Icon anzeigen.
- **Lager-Tab:** Icon neben der Lagerbestand-Zeile.
- **Subventionen-Tab:** Icon pro subventioniertem Gut.
- **Berater-Tab:** Sparkline- oder Warnketten-Icons durch Ressourcen-Icons ergänzen.

---

## 3. `RoomBlueprintImp.icon` — Gebäude-Icons

Jedes Gebäude-Blueprint hat ein `icon`-Feld (und teilweise `iconBig`), das das entsprechende Gebäude-Icon repräsentiert.

```java
RoomBlueprintImp bp = ...; // z. B. aus SETT.ROOMS()
Icon icon = bp.icon;
icon.render((SPRITE_RENDERER)r, x, y);
```

### Verwendungsideen in SyxEconomyMod

- **Betriebe-Tab:** Icon vor jedem Firmen-/Betriebseintrag.
- **Löhne-Tab:** Icon pro Arbeitsplatz-Blueprint.
- **Berater-Tab:** Wenn eine Warnkette ein bestimmtes Gebäude betrifft, kann das passende Blueprint-Icon die Warnung visuell unterstützen.

---

## 4. UI-Spritesheets (`UIConses`, `UIDecor`, `UICons`)

Neben einzelnen Icons existieren Spritesheet-Wrapper, aus denen beliebige Sub-Icons gezeichnet werden können.

| Klasse | Zweck |
|---|---|
| `UICons` | Konsolen-/Control-Elemente |
| `UIDecor` | Rahmen, Boxen, Hintergründe |
| `UIConses` | Gruppierte Sprite-Sets (`Small`, `Big`, `Rotaters`, `Icons`) |

Diese werden typischerweise intern von `Icon` genutzt. Für Mod-UI-Zwecke reichen in der Regel die fertigen `Icon`-Objekte aus `UI.icons()`.

---

## 5. Beispiel-Icon-Zuordnung für SyxEconomyMod (bereits implementiert)

| Tab/Element | Verwendetes Icon |
|---|---|
| Hauptmenü Übersicht | `UI.icons().s.eye` |
| Hauptmenü Wirtschaft | `UI.icons().s.trade` |
| Hauptmenü Staat & Soziales | `UI.icons().s.capitol` |
| Vermögen | `UI.icons().s.money` |
| Bürger | `UI.icons().s.citizen` |
| Bücher | `UI.icons().s.book` |
| Berater | `UI.icons().s.question` |
| Preise | `UI.icons().s.trade` |
| Löhne | `UI.icons().s.money` |
| Subventionen | `UI.icons().s.gift` |
| Lager | `UI.icons().s.storage` |
| Markt | `UI.icons().s.trade` |
| Betriebe | `UI.icons().s.hammer` |
| Steuern | `UI.icons().s.money` |
| Glaube | `UI.icons().s.temple` |
| Arbeit | `UI.icons().s.pickaxe` |
| Spenden | `UI.icons().s.heart` |

---

## 6. Nächste Erweiterungsmöglichkeiten

- **Ressourcen-Icons in Preise/Lager/Subventionen-Tab:** `res.icon().render(...)` pro Zeile.
- **Gebäude-Icons im Betriebe- und Lohn-Tab:** `blueprint.icon.render(...)` pro Zeile.
- **Große Icons (`UI.icons().l`) im Berater-Dashboard:** Panel-Header oder Warnketten vergrößert darstellen.
- **Roter/weißer Hover-Effekt:** Icon-Färbung an Tab-Auswahl/Hover anpassen, falls Vanilla-Icon keine eigene Hover-Farbe besitzt.
