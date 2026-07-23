package vannon.syx.economy.core;

import util.text.D;

public final class EconTexts {

    // Window title
    public static final String ¤¤windowTitle = "WIRTSCHAFT";

    // Tab labels
    public static final String ¤¤tabWealth = "VERMÖGEN";
    public static final String ¤¤tabCitizens = "BÜRGER";
    public static final String ¤¤tabPrices = "PREISE";
    public static final String ¤¤tabWages = "LÖHNE";
    public static final String ¤¤tabSubsidies = "SUBVENTIONEN";
    public static final String ¤¤tabGranary = "LAGER";
    public static final String ¤¤tabMarket = "MARKT";
    public static final String ¤¤tabTaxes = "STEUERN";
    public static final String ¤¤tabFaith = "GLAUBE";
    public static final String ¤¤tabCorvee = "STAATSARBEIT";
    public static final String ¤¤tabRelief = "SPENDEN";
    public static final String ¤¤tabBooks = "BÜCHER";
    public static final String ¤¤tabDebug = "DEBUG";

    // Common / shared
    public static final String ¤¤uiDenari = " Denari";
    public static final String ¤¤uiPerUnit = " / Einheit";
    public static final String ¤¤uiPerUnitShort = "/Einheit";
    public static final String ¤¤uiPerSeason = " / Jahreszeit";
    public static final String ¤¤uiPerDay = "/Tag";
    public static final String ¤¤uiOf = " von ";
    public static final String ¤¤uiSlash = " / ";
    public static final String ¤¤uiSlashShort = "/";
    public static final String ¤¤uiRange = "-";
    public static final String ¤¤uiColon = " : ";
    public static final String ¤¤uiEquals = "  =  ";
    public static final String ¤¤uiMultiple = "x";
    public static final String ¤¤uiPercent = "%";
    public static final String ¤¤uiOpenParen = "(";
    public static final String ¤¤uiCloseParen = ")";

    // UI toggles
    public static final String ¤¤uiShowZeroRows = "Nullzeilen zeigen";

    // WEALTH tab
    public static final String ¤¤wealthNoSettlers = "Keine Siedler";
    public static final String ¤¤wealthSettlersMean = " Siedler   Durchschnitt ";
    public static final String ¤¤wealthMedian = "   Median ";
    public static final String ¤¤wealthRichest = "Reichste ";
    public static final String ¤¤wealthGini = "   Gini ";
    public static final String ¤¤wealthZeroDenari = "0 Denari";

    // CITIZENS tab
    public static final String ¤¤citNobodyMoney = "Noch hat niemand Geld";
    public static final String ¤¤citRichestHolds = "Der reichste Siedler hält ";
    public static final String ¤¤citTimesMedian = "x des Medians)";
    public static final String ¤¤citMedian = "Median ";
    public static final String ¤¤citMean = "   Durchschnitt ";
    public static final String ¤¤citGini = "   Gini ";
    public static final String ¤¤citBtnJump = "ZUM REICHSTEN SPRINGEN";
    public static final String ¤¤citJumpHint = "Öffnet Bürger-Fenster und zentriert Kamera";
    public static final String ¤¤citMedianPrefix = "   (";
    public static final String ¤¤housingRentCollected = "Mieteinnahmen (letzte Saison): ";
    public static final String ¤¤housingEvictions = "   Zwangsräumungen: ";

    // PRICES tab
    public static final String ¤¤pricesHeader = "Lokale Verrechnungspreise für Bau, Lager und Betriebsmittel.";
    public static final String ¤¤pricesCoverageHint = "Deckungsgrad 1,00 = Zielbestand; darunter Mangel, darüber Überschuss. Rot = 10x+ Anker.";
    public static final String ¤¤pricesColumnResource = "Ressource";
    public static final String ¤¤pricesColumnLocal = "lokal / Einheit";
    public static final String ¤¤pricesColumnAnchor = "Handelsanker";
    public static final String ¤¤pricesColumnMultiple = "Vielfaches";
    public static final String ¤¤pricesColumnCoverage = "Deckung";
    public static final String ¤¤pricesColumnStock = "Bestand";
    public static final String ¤¤pricesColumnSupplyDemand = "Angebot/Nachfrage/Tag";

    // SUBSIDIES tab
    public static final String ¤¤subSeasonProd = "Produktion diese Jahreszeit: ";
    public static final String ¤¤subUnitsDue = " Einheiten   fällig ";
    public static final String ¤¤subPaid = "   bezahlt ";
    public static final String ¤¤subColumns = "Ressource     Output/Tag     Denari/Einheit";

    // GRANARY tab
    public static final String ¤¤granBought = "Kornspeicher: gekauft ";
    public static final String ¤¤granUnitsFor = " Einheiten für ";
    public static final String ¤¤granSold = "   verkauft ";
    public static final String ¤¤granFor = " für ";
    public static final String ¤¤granClerksPaid = "Bezahlte Angestellte: ";
    public static final String ¤¤granWages = "   Löhne ";
    public static final String ¤¤granUnpaid = "   UNBEZAHLT ";
    public static final String ¤¤granSalary = "Gehalt/Saison";
    public static final String ¤¤granStateCount = "Staatliche Lager: ";
    public static final String ¤¤granWarning = "   (Ein Speicher hält nur, was seine Kisten fassen sollen)";
    public static final String ¤¤granBtnLiqAll = "ALLES LIQUIDIEREN";
    public static final String ¤¤granBtnState = "STAATLICH";
    public static final String ¤¤granBtnPrivate = "privat";
    public static final String ¤¤granBtnLiq = "LIQUIDIEREN";
    public static final String ¤¤granBtnHoard = "HORTEN (gehalten)";
    public static final String ¤¤granBuyAt = "Kauf zu ";
    public static final String ¤¤granSellAt = "Verkauf zu ";
    public static final String ¤¤granStores = "Lagert ";
    public static final String ¤¤granInStateHands = " in staatlicher Hand";

    // CROWN MARKET tab
    public static final String ¤¤mrkSold = "Kronmarkt: verkauft ";
    public static final String ¤¤mrkHint = "Gefälltes Holz, abgebautes Gestein, Futter, Schutt und ähnliches Kronland-Eigentum. Standard 75/Einheit.";
    public static final String ¤¤mrkLiveMarket = "Freier Markt ";
    public static final String ¤¤mrkCrownUnits = "   Kroneinheiten ";

    // GUILDS / WAGES tab
    public static final String ¤¤wageSurplusDue = "Gilden-Überschuss   fällig ";
    public static final String ¤¤wagePaid = "   bezahlt ";
    public static final String ¤¤wageMeanMarginal = "   Ø Marginal ";
    public static final String ¤¤wageInsolvent = "INSOLVENT: ";
    public static final String ¤¤wageInsolHint = " Gilden-Anteile nicht von der Staatskasse gedeckt";
    public static final String ¤¤wageNoWorkplaces = "Es wurden noch keine Arbeitsplätze gebaut";
    public static final String ¤¤wageStateFunded = "STAATLICH FINANZIERT   Auszubildende ";
    public static final String ¤¤wageSalary = "   Gehalt ";
    public static final String ¤¤wageProfitDay = "Profit/Tag ";
    public static final String ¤¤wageMarginal = "   Marginal ";
    public static final String ¤¤wageWorkers = "   Arbeiter ";
    public static final String ¤¤wagePrio = "   Prio ";

    // FIRMS tab
    public static final String ¤¤firmsHeader = "Betriebsgewinn & Input/Output";
    public static final String ¤¤firmsProfit = "Profit/Tag: ";
    public static final String ¤¤firmsMargin = "Marginal: ";
    public static final String ¤¤firmsInstances = "Anzahl: ";
    public static final String ¤¤firmsInputs = "In: ";
    public static final String ¤¤firmsOutputs = "Out: ";
    public static final String ¤¤firmsOwnership = "Besitz: ";
    public static final String ¤¤firmsStaat = "Staat";
    public static final String ¤¤firmsDividend = "Div: ";

    // STATE WAGE ROW
    public static final String ¤¤wageRowLast = "letzte ";
    public static final String ¤¤wageRowDueTo = " fällig für ";
    public static final String ¤¤wageRowWorkers = " Arbeiter";
    public static final String ¤¤wageRowTreasuryShort = "   STAATSKASSE LEER";

    // TAXES tab
    public static final String ¤¤taxPerAdult = "Kopfsteuer / Saison";
    public static final String ¤¤taxMarketSkim = "Markt-Abschöpfung";
    public static final String ¤¤taxWarehouseStock = "Lagerrücklagen / Saison";
    public static final String ¤¤taxCollectedHead = "Eingenommen: Kopfsteuer ";
    public static final String ¤¤taxCollectedMarket = "   Markt ";
    public static final String ¤¤taxCollectedWarehouse = "   Lagerbestand ";
    public static final String ¤¤taxCollectedFrom = " von ";
    public static final String ¤¤taxCollectedMerchants = " Händlern";
    public static final String ¤¤taxPaidOut = "Ausgezahlt: Lebensmittelbeschaffung ";
    public static final String ¤¤taxPaidProducer = "   Produzenten-Einkommen ";
    public static final String ¤¤taxWealthOn = "VERMÖGENSSTEUER AN";
    public static final String ¤¤taxWealthOff = "Vermögenssteuer aus";
    public static final String ¤¤taxExemptBelow = "Befreit unter";
    public static final String ¤¤taxRateAboveFloor = "Satz über Freigrenze";
    public static final String ¤¤taxLastWealthTake = "Letzte Vermögenssteuer: ";
    public static final String ¤¤taxLastWealthPayers = " von ";
    public static final String ¤¤taxLastWealthPayersSuffix = " Steuerpflichtigen";
    public static final String ¤¤taxLiturgyOn = "LITURGIE AN";
    public static final String ¤¤taxLiturgyOff = "Liturgie aus";
    public static final String ¤¤taxRichestTaxed = "Reichste besteuert";
    public static final String ¤¤taxShareWealth = "Anteil am Vermögen";
    public static final String ¤¤taxEveryNSeasons = "Alle N Saisons";
    public static final String ¤¤taxLastLiturgy = "Letzte Liturgie: ";
    public static final String ¤¤taxLastLiturgyNamed = " von ";
    public static final String ¤¤taxLastLiturgyNamedSuffix = " Benannten";
    public static final String ¤¤taxDebtBondageOn = "SCHULDENKNECHTSCHAFT AN";
    public static final String ¤¤taxDebtBondageOff = "Schuldenknechtschaft aus";
    public static final String ¤¤taxEnslaveAtDebt = "Versklaven bei Schulden";
    public static final String ¤¤taxOutstandingDebt = "Ausstehende Steuerschulden: ";
    public static final String ¤¤taxEnslavedLastSeason = "   Letzte Saison versklavt: ";

    // FAITH tab
    public static final String ¤¤faithJizyaOn = "RELIGIONSSTEUER AN";
    public static final String ¤¤faithJizyaOff = "Religionssteuer aus";
    public static final String ¤¤faithCollected = "Eingenommen: ";
    public static final String ¤¤faithApostasies = "   Abtrünnige: ";
    public static final String ¤¤faithNewDebtors = "   Neue Schuldner: ";

    // LABOR / CORVEE tab
    public static final String ¤¤corveeOff = "Staatsarbeit aus";
    public static final String ¤¤corveeOn = "STAATSARBEIT AN";
    public static final String ¤¤corveeDraftUpTo = "Ausheben bis zu";
    public static final String ¤¤corveeButNoMoreThan = "aber nicht mehr als";
    public static final String ¤¤corveePeople = " Menschen";
    public static final String ¤¤corveeOrdinaryDay = "Heute ist ein gewöhnlicher Arbeitstag";
    public static final String ¤¤corveeSeasonDay = "Tag ";
    public static final String ¤¤corveeOddjobOn = "GELEGENHEITSARBEIT AN";
    public static final String ¤¤corveeOddjobOff = "Gelegenheitsarbeit aus";
    public static final String ¤¤corveeDenariPerTask = " Denari/Aufgabe";
    public static final String ¤¤corveeHaulageOn = "TRANSPORTPAUSCHALE AN";
    public static final String ¤¤corveeHaulageOff = "Transportpauschale aus";
    public static final String ¤¤corveeHaulageDesc = "Der Staat zahlt Denari pro 100 Kacheln Transportweg pro Tag, aufgeteilt auf die Teams.";
    public static final String ¤¤corveeHaulageRate = "Pauschale";
    public static final String ¤¤corveeHaulageSuffix = " /100t/Tag";
    public static final String ¤¤corveeThisSeason = "diese Jahreszeit: ";
    public static final String ¤¤corveeTasks = " Aufgaben";
    public static final String ¤¤corveeLast = "letzte: ";
    public static final String ¤¤corveeWorkingNow = "arbeiten jetzt: ";
    public static final String ¤¤corveeCycle = "Zyklus: ";
    public static final String ¤¤corveeBlocked = "   GESPERRT: STAATSKASSE IM DEFIZIT";
    public static final String ¤¤corveeLastTick = "letzter Tick: ";
    public static final String ¤¤corveeActiveStations = "aktive Stationen: ";
    public static final String ¤¤corveeMeanHaul = "Ø Transport: ";
    public static final String ¤¤corveeTiles = " Kacheln";
    public static final String ¤¤corveeGeoEstimate = "   (geometrische Schätzung)";
    public static final String ¤¤corveeStateWages = "STAATSGEHÄLTER";
    public static final String ¤¤corveeStateWagesDesc = "Leere Kasse = kein Lohn; Staatsarbeiter suchen bessere Arbeit.";

    // RELIEF tab
    public static final String ¤¤reliefTitle = "KORNSPENDE";
    public static final String ¤¤reliefDesc = "Kostenloses Brot für die Ärmsten, begrenzt nach römischem Vorbild.";
    public static final String ¤¤reliefFreeIfWorthUnder = "Kostenlos bei Vermögen unter";
    public static final String ¤¤reliefGrainRollHolds = "Getreideliste umfasst";
    public static final String ¤¤reliefOnRoll = "Auf der Liste: ";
    public static final String ¤¤reliefFreeMeals = "Kostenlose Mahlzeiten: ";
    public static final String ¤¤reliefRevenueForegone = "Entgangene Einnahmen: ";
    public static final String ¤¤reliefAutoRations = "Autom. Sklaven-/Waisen-Rationen: ";
    public static final String ¤¤reliefQualifyButNotOnRoll = " qualifizieren sich, sind ABER NICHT auf der Liste - Ärmste zuerst.";

    // ADVISOR tab
    public static final String ¤¤tabAdvisor = "BERATER";
    public static final String ¤¤tabFirms = "BETRIEBE";
    public static final String ¤¤advEcosystem = "WIRTSCHAFTSZUSTAND";
    public static final String ¤¤advChains = "WARNKETTEN";
    public static final String ¤¤advTrends = "MAKRO-TRENDS (letzte 5 Snapshots)";
    public static final String ¤¤indMoney = "Geldmenge";
    public static final String ¤¤indProduction = "Produktion";
    public static final String ¤¤indBasics = "Nahrung";
    public static final String ¤¤indWelfare = "Wohlfahrt";
    public static final String ¤¤trendFood = "Nahrungskorb";
    public static final String ¤¤trendWage = "Ø Lohn";
    public static final String ¤¤trendGini = "Gini";
    public static final String ¤¤trendWageShare = "Lohn-Anteil";
    public static final String ¤¤chainScarcity = "Engpass >> Preise steigen >> Löhne sinken >> Auswanderung";
    public static final String ¤¤chainInsolvency = "Insolvenz >> Produktion sinkt >> Armut steigt";
    public static final String ¤¤chainInequality = "Gini steigt >> Unruhe >> Produktivität sinkt";
    public static final String ¤¤chainEmigration = "Abwanderungsspitze >> Arbeitskraft sinkt";
    public static final String ¤¤chainAllClear = "Keine aktiven Warnketten";

    // Chronik-Header (EventLog)
    public static final String ¤¤historyHeader = "Wirtschafts-Chronik - letzte Ereignisse";
    public static final String ¤¤historyNoEvents = "Noch keine Ereignisse aufgezeichnet.";

    // Menu labels
    public static final String ¤¤menuOverview = "ÜBERSICHT";
    public static final String ¤¤menuEconomy = "WIRTSCHAFT";
    public static final String ¤¤menuStateAndSocial = "STAAT & SOZIALES";
    public static final String ¤¤advTitle = "WIRTSCHAFTS-BERATER";
    public static final String ¤¤advStage = "Aktuelle Stufe: ";
    public static final String ¤¤advPeople = "Bevölkerung: ";
    public static final String ¤¤advMoney = "Geld im Umlauf: ";
    public static final String ¤¤advMedian = "Vermögens-Median: ";
    public static final String ¤¤advGini = "Ungleichheit (Gini): ";
    public static final String ¤¤advWages = "Ø Lohn: ";
    public static final String ¤¤advWorkersUnpaid = "Unbezahlte Arbeiter: ";
    public static final String ¤¤advDeaths = "Tote: ";
    public static final String ¤¤advEmigrations = "Ausgewandert: ";
    public static final String ¤¤advInherited = "Erben: ";
    public static final String ¤¤advHeirless = "Erblos (verfallen): ";
    public static final String ¤¤advWarnInequality = "UNGLEICHHEIT STEIGT (Gini > 0.35)";
    public static final String ¤¤advWarnEmigration = "ABWANDERUNGSSPITZE";
    public static final String ¤¤advWarnInsolvent = "BETRIEBE INSOLVENT";
    public static final String ¤¤advWarnWagesFalling = "LÖHNE SINKEN";
    public static final String ¤¤advWarnScarcity = "ENGPÄSSE: ";
    public static final String ¤¤advAllClear = "Keine Warnungen - Wirtschaft stabil";
    public static final String ¤¤advWarnings = "WARNUNGEN";
    public static final String ¤¤advPolicy = "STEUERUNG";
    public static final String ¤¤advTrendsMissing = "Trends noch nicht verfügbar (mindestens 2 Snapshots nötig).";
    public static final String ¤¤advHeadTaxLabel = "Kopfsteuer/Saison:";
    public static final String ¤¤advMarketSkimLabel = "Markt-Abschoepfung:";
    public static final String ¤¤advOddjobLabel = "Gelegenheitslohn:";
    // Empfehlungen
    public static final String ¤¤advRecInequality = "  -> Pruefe Lohn-Verteilung im Lohn-Tab";
    public static final String ¤¤advRecInsolvent = "  -> Erhoehe Laedenlohn oder reduziere Steuern";
    public static final String ¤¤advRecEmigration = "  -> Pruefe Bevoelkerungszufriedenheit";
    public static final String ¤¤advRecScarcity = "  -> Baue mehr Lager oder subventioniere Produktion";
    public static final String ¤¤advRecWagesFalling = "  -> Pruefe Lohn-Tab fuer moegliche Erhoehung";

    // Stage-Namen kommen aus EconProgression.Stage.displayName — advStage*-Strings sind ungenutzt.

    // Mod metadata
    public static final String ¤¤modName = "SyxEconomyMod";
    public static final String ¤¤modDesc = "Wirtschafts-Overhaul für Songs of Syx V71: Mikro- und Makroökonomie mit Bürger-Brieftaschen, dynamischen Preisen, Betriebsbuchhaltung, Mietmarkt, 5 Wirtschaftsstufen, Gini->Loyalty, Schuldenknechtschaft und 15-Tab-UI.";

    // Seasons
    public static final String ¤¤seasonSpring = "Frühling";
    public static final String ¤¤seasonSummer = "Sommer";
    public static final String ¤¤seasonAutumn = "Herbst";
    public static final String ¤¤seasonWinter = "Winter";

    // State wage roles
    public static final String ¤¤roleMilitaryTrainees = "Militär-Rekruten";
    public static final String ¤¤roleExportDepot = "Exportlager";
    public static final String ¤¤roleHaulers = "Transportarbeiter";
    public static final String ¤¤roleArmySupply = "Armeeversorgung";
    public static final String ¤¤roleLaboratory = "Labor";
    public static final String ¤¤roleLibrary = "Bibliothek";
    public static final String ¤¤roleEmbassy = "Botschaft";
    public static final String ¤¤roleWaterWorks = "Wasserwerke";
    public static final String ¤¤roleCannibalHouse = "Kannibalenhaus";
    public static final String ¤¤roleSecretPolice = "Geheimpolizei";
    public static final String ¤¤roleGuards = "Wachen";
    public static final String ¤¤roleStockadeJailors = "Palisaden-Gefängniswärter";
    public static final String ¤¤rolePrisonJailors = "Gefängniswärter";

    // Booster / register strings
    public static final String ¤¤boosterInflationOff = "Wirtschaftsmod: Keine Inflation";
    public static final String ¤¤boosterMeticTax = "Metic-Steuer";
    public static final String ¤¤boostWealth = "Vermögen";
    public static final String ¤¤boostTaxes = "Steuern";
    public static final String ¤¤boostProperty = "Eigentum";
    public static final String ¤¤boostInequality = "Ungleichheit";
    public static final String ¤¤boostPoverty = "Armutsdruck";

    // BOOKS tab
    public static final String ¤¤booksTitle = "BÜCHER";
    public static final String ¤¤booksFoundingStock = "Gründungskapital";
    public static final String ¤¤booksImported = "+ importiert durch Einwanderer";
    public static final String ¤¤booksTreasuryIncome = "+ staatlich finanziertes Einkommen";
    public static final String ¤¤booksAnnona = "+ Annona/Ration-Produzentenzahlungen";
    public static final String ¤¤booksBuyerFood = "Käufer->Gilde Nahrung  (";
    public static final String ¤¤booksMeals = " Mahlzeiten)";
    public static final String ¤¤booksBuyerDrink = "Käufer->Gilde Getränke  (";
    public static final String ¤¤booksRounds = " Runden)";
    public static final String ¤¤booksBuyerGoods = "Käufer->Gilde Waren  (";
    public static final String ¤¤booksPurchases = " Käufe)";
    public static final String ¤¤booksTransfers = "   (Umbuchungen innerhalb der Zirkulation)";
    public static final String ¤¤booksGrainDoleMeals = "Kornspende: kostenlose Mahlzeiten ausgegeben";
    public static final String ¤¤booksGrainDoleRevenue = "Kornspende: entgangene Einnahmen";
    public static final String ¤¤booksWealthTax = "- Vermögenssteuer";
    public static final String ¤¤booksHeadTax = "- Kopfsteuer";
    public static final String ¤¤booksReligionTax = "- Religionssteuer";
    public static final String ¤¤booksLiturgy = "- Liturgie für die Reichen";
    public static final String ¤¤booksMarketSkim = "- Markt-Abschöpfung/Unbeansprucht";
    public static final String ¤¤booksLegacy = "Bürger-Konsum (Nahrung/Getränke/Güter)";
    public static final String ¤¤booksRoundingDrift = "Rundungsdrift-Puffer";
    public static final String ¤¤booksExported = "- exportiert durch Auswanderer";
    public static final String ¤¤booksHeirless = "- herrenloser Besitz beschlagnahmt";
    public static final String ¤¤booksInCirculation = "= im Umlauf: ";
    public static final String ¤¤booksDoNotBalance = "BÜCHER STIMMEN NICHT: ";
    public static final String ¤¤booksUnaccounted = " Denari nicht erklärt - das ist ein BUG, keine Mechanik";
    public static final String ¤¤booksBalance = "Bücher stimmen";
    public static final String ¤¤booksDebtors = "Steuerschuldner: ";
    public static final String ¤¤booksArrears = "   ausstehende Rückstände: ";
    public static final String ¤¤booksSoldBondage = "   verkauft zur Schuldknechtschaft: ";
    public static final String ¤¤booksFoodStock = "Nahrungsbestand ";
    public static final String ¤¤booksDaysOfFood = " Tage Nahrung";
    public static final String ¤¤booksTarget = "   (Ziel ";
    public static final String ¤¤booksLocalFoodBasket = "Lokaler Nahrungskorb ";
    public static final String ¤¤booksTradeAnchorBasket = "   (Handelsanker-Korb ";
    public static final String ¤¤booksReferenceFoodUnit = "   ->  Referenznahrungs-Einheit ";
    public static final String ¤¤booksLastOptimizedMeal = "Letzte Opt. Mahlzeit: ";
    public static final String ¤¤booksDenariFor = " Denari für ";
    public static final String ¤¤booksFoodUnits = " Nahrungs-Einheiten";
    public static final String ¤¤booksDrinkReserve = "Getränkereserve ";
    public static final String ¤¤booksDays = " Tage";
    public static final String ¤¤booksLocalDrinkBasket = "   Lokaler Getränkekorb ";
    public static final String ¤¤booksAnchor = "   (Anker ";

    // === Progression ===
    public static final String ¤¤advNextStage = "Naechste Stufe - fehlt noch:";
    public static final String ¤¤advMilestones = "Meilensteine:";

    // CitizenClass Panel
    public static final String ¤¤classHeader = "BÜRGER-KLASSEN (CitizenClass · Kaufverhalten)";
    public static final String ¤¤advMs50People = "50 Siedler";
    public static final String ¤¤advMsWarehouse = "Erstes Lagerhaus";
    public static final String ¤¤advMsFoodStable = "Nahrung 3 Tage gesichert";
    public static final String ¤¤advMs100People = "100 Siedler";
    public static final String ¤¤advMsFirstExport = "Erster Export";
    public static final String ¤¤advMsFirstService = "Erste Taverne/Markt";
    public static final String ¤¤advMsWages = "Loehne > 50";
    public static final String ¤¤advMs200People = "200 Siedler";
    public static final String ¤¤advMsNoInsolvency = "100 Tage ohne Insolvenz";
    public static final String ¤¤advMsGiniStable = "Gini < 0.35 fuer 30 Tage";
    public static final String ¤¤advMsStableWages = "Stabile Loehne (100 Tage)";
    public static final String ¤¤advMsLowInequality = "Niedrige Ungleichheit (50 Tage Gini < 0.30)";
    public static final String ¤¤advMsFirstLab = "Erste Forschungsanlage";
    public static final String ¤¤advMsFirstMilitary = "Erstes Militaertraining";
    public static final String ¤¤advMsFirstTemple = "Erster Tempel";
    public static final String ¤¤advMsFirstEmbassy = "Erste Botschaft";

    // FLOWS tab (neue Visualisierung)
    public static final String ¤¤tabFlows = "FLÜSSE";
    public static final String ¤¤tabDashboard = "DASHBOARD";
    public static final String ¤¤tabForeignTrade = "AUSLAND";

    // FOREIGN-TRADE tab body
    public static final String ¤¤foreignTitle = "AUSLÄNDISCHE FRAKTIONEN";
    public static final String ¤¤foreignSubtitle = "Tages-Inflow aller aktiven NPCs";
    public static final String ¤¤foreignNoData = "Noch keine Daten — läuft ab dem ersten Tagesabschluss.";
    public static final String ¤¤foreignActiveCount = "Aktive Fraktionen";
    public static final String ¤¤foreignInflowToday = "Inflow heute";
    public static final String ¤¤foreignSnapshot = "Letzter Credit-Snapshot";
    public static final String ¤¤dashboardHeader = "Wirtschafts-Dashboard";
    public static final String ¤¤dashboardTreasury = "STAATSKASSE";
    public static final String ¤¤dashboardGini = "GINI-KOEFF.";
    public static final String ¤¤dashboardCitizens = "BÜRGER";
    public static final String ¤¤dashboardStage = "STUFE";
    public static final String ¤¤dashboardTreasuryChart = "Staatskasse-Verlauf";
    public static final String ¤¤dashboardGiniChart = "Ungleichheit (Gini)";
    public static final String ¤¤indTreasury = "Kasse";
    public static final String ¤¤indGini = "Gini";
    public static final String ¤¤indStage = "Stufe";
    public static final String ¤¤flowsHeader = "Ressourcen-Fluss: Angebot · Nachfrage · Bestand";
    public static final String ¤¤flowsColBar = "Angebot / Nachfrage";
    public static final String ¤¤flowsColStock = "Tage";
    public static final String ¤¤flowsNoGoods = "Keine Güter erfasst.";

    // KPI-Box-Labels (ADVISOR-Redesign)
    public static final String ¤¤kpiPeople = "BEVÖLKERUNG";
    public static final String ¤¤kpiMoney = "GELD (UMLAUF)";
    public static final String ¤¤kpiGini = "GINI KOEFF.";
    public static final String ¤¤kpiWage = "Ø LOHN / TAG";
    public static final String ¤¤kpiUnpaid = "UNBEZAHLTE";
    public static final String ¤¤kpiFood = "NAHRUNGSKORB";

    // One-Screen-Story Ampel-Labels
    public static final String ¤¤ampelFinanzen = "FINANZEN";
    public static final String ¤¤ampelArbeit = "ARBEIT";
    public static final String ¤¤ampelVersorgung = "VERSORGUNG";
    public static final String ¤¤ampelGleichheit = "GLEICHHEIT";
    public static final String ¤¤ampelWachstum = "WACHSTUM";

    // All text is hardcoded in Java field initializers — no runtime D.ts() injection.
    // The vanilla util.text.D / Dic.txt system is not used by this mod.
    static {}

    private EconTexts() {
    }
}
