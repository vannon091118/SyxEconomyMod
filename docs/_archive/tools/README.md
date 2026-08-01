# Archived Tools — Sprint v0.13.131+ToolBoxSlim

Diese Scripts wurden im Sprint v0.13.131+ToolBoxSlim aus `tools/` nach `docs/_archive/tools/` verschoben, weil sie in den letzten 4 Sprints nicht aktiv gerufen wurden.

**Aktiv geblieben in `tools/`** (Stand v0.13.131): `gate.sh`, `install-hooks.sh`, `phase47-shield.sh`, `phase47-baseline.sh`, `post-commit-shield.sh`, `doku-sync.sh`, `bump-version.sh`, `build-gate.sh`, `verify-doc-sync.sh`, `scripts/pre-merge-doc-sync.sh`, `lib/`.

**Wiederbelebung**: `git mv` zurück nach `tools/` und im `gate.sh`-Skip-Block (Zeile 134–145) die `[archived]`-Kommentierung aufheben. Pom.xml ist nicht betroffen (keine Scripts im Maven-Build-Classpath).

**Warum archiviert statt gelöscht**: git-History bleibt via rename-detection erhalten. Falls eine zukünftige Sprint sie wieder braucht, sind sie nicht unwiederbringlich weg.
