---
target: home
total_score: 20
p0_count: 1
p1_count: 2
timestamp: 2026-06-16T23-49-43Z
slug: frontend-src-app-features-home
---
## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 2 | No loading states, no CTA click feedback |
| 2 | Match System / Real World | 3 | Polish copy natural. "Filament" assumes knowledge |
| 3 | User Control and Freedom | 2 | Limited exploration, no preview without signup |
| 4 | Consistency and Standards | 3 | Token system solid. btn-primary vs btn--primary split |
| 5 | Error Prevention | 1 | Hardcoded stats, no validation |
| 6 | Recognition Rather Than Recall | 3 | Materials upfront, steps explicit |
| 7 | Flexibility and Efficiency | 1 | No shortcuts, single linear scroll |
| 8 | Aesthetic and Minimalist Design | 3 | Clean palette. Ghost step nums and duplicate grids are noise |
| 9 | Error Recovery | 1 | No error states on homepage |
| 10 | Help and Documentation | 1 | No FAQ, no tooltips, no educational content |
| **Total** | | **20/40** | **Acceptable** |

## Anti-Patterns Verdict

3 active violations: identical card grids, uppercase tracked eyebrows (hero badge + material names), ghost step-number CSS (3.5rem/900/0.12 opacity).
Fixed: gradient text, hero-metric stats template.
Detector: 0 CLI findings. 2 manual contrast failures (--text-muted, --text-secondary borderline on --bg).

## Priority Issues

- [P0] Ghost step-number CSS — 3.5rem/900/0.12 opacity on inline number. AI tell.
- [P1] No mobile navigation — 6+ items wrap on phones, no hamburger/drawer.
- [P1] Uppercase tracked eyebrows — hero__badge and material-card__name.
- [P2] No supply-side content — zero printer-owner messaging.
- [P2] Contrast failures — --text-muted at 2.37:1 (light), 3.09:1 (dark).

## Persona Red Flags

- Jordan: no educational content, login without preview of value
- Casey: navbar overflow on mobile, hero SVG takes 69% viewport on iPhone SE
- Ania (printer owner): entire page buyer-facing, zero supply-side content

## Minor Observations

- Dead CSS: .stats media query references removed class
- Dead data: icon property in steps array never rendered
- Button class inconsistency: .btn-primary vs .btn--primary
- No :focus-visible on .btn
- user-chip ellipsis broken (missing white-space: nowrap)
- Hardcoded trust stats
- No footer (Polish legal requirements)
