# Druk3D Design Language — De-slop Extension

Extends the Blueprint system (teal accent, dark-first, Bricolage Grotesque +
Manrope). Rules for every screen:

## Typography
- Page titles: `font-size: var(--fs-title)`, Bricolage Grotesque 700,
  `line-height: var(--lh-tight)`.
- Section headings: `.section-label` with `.tick-num` numbering (`01 /`,
  `02 /` per page section, restart per page).
- Every spec value (material, mm, PLN, dates, counts): `.spec-mono`.
- Body text: Manrope, unchanged.

## De-carding
- Max ONE boxed layer (border + filled bg) per view.
- Lists of records: `.divided-list` > `.divided-row`, not card grids.
- Shadows: `--shadow-xs`/`--shadow-sm` only on content.
- Keep intentional Blueprint `border-left` status accents.

## Craft motifs
- `.layer-rule` between major page sections.
- `.blueprint-grid` backgrounds ONLY on hero and empty states.
- No other decoration.

## Spacing
- Between page sections: `var(--space-section)`.
- Between blocks in a section: `var(--space-block)`.
- Inside data rows: dense (12–20px). Contrast between zones is the point.

## Both themes, always
Dark is default. Check light via `[data-theme="light"]`. AA contrast on
every changed pair.
