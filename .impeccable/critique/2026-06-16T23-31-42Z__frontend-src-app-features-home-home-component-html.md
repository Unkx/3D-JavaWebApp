---
target: home
total_score: 18
p0_count: 2
p1_count: 3
timestamp: 2026-06-16T23-31-42Z
slug: frontend-src-app-features-home-home-component-html
---
## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 2 | No loading states; auth-conditional CTAs switch without feedback |
| 2 | Match System / Real World | 3 | Polish language correct; 3D printer SVG uses real mechanical metaphors |
| 3 | User Control and Freedom | 2 | Dead-end funnel; no breadcrumbs or back signals |
| 4 | Consistency and Standards | 3 | Token system well-structured; consistent button patterns |
| 5 | Error Prevention | 1 | No preview of what registration entails before committing |
| 6 | Recognition Rather Than Recall | 2 | Materials help recognition but steps are abstract text with no visual differentiation |
| 7 | Flexibility and Efficiency | 1 | Single linear scroll for all users; no shortcuts, search, or jump links |
| 8 | Aesthetic and Minimalist Design | 2 | Minimal but uniform — every section uses same divider-grid visual treatment |
| 9 | Error Recovery | 1 | No error states visible on homepage |
| 10 | Help and Documentation | 1 | No FAQ, no contact info, no trust signals for a transactional marketplace |
| **Total** | | **18/40** | **Poor** |

## Anti-Patterns Verdict

5 of 8 absolute bans violated: gradient text, hero-metric template, identical card grids, tiny uppercase tracked eyebrows, numbered section markers. Outfit font on reflex-reject list. Zero product imagery. Timid palette.

Deterministic scan: 1 finding — gradient-text on home.component.css:40.

## Overall Impression

Solid engineering, weak design identity. Token system and component architecture well-built, but visual output is textbook AI landing page. Five absolute bans violated simultaneously. Biggest miss: 3D printing marketplace shows zero printed objects.

## What's Working

1. Methodical token system with complete light/dark themes
2. Auth-aware CTA switching with no layout shift
3. Thematically relevant hero SVG depicting actual 3D printer mechanics

## Priority Issues

### [P0] Gradient text must be removed
background-clip: text with gradient on .hero__accent and .navbar__title. Most recognizable AI-slop pattern.

### [P0] Outfit font must go
On reflex-reject list. Typography is 80% of brand impression.

### [P1] Hero-metric stats bar is AI template pattern
4-stat row (big number / small label) combined with other violations cements AI impression.

### [P1] Zero product imagery on a physical-product marketplace
No photos of printed objects. Restaurant without food photos.

### [P1] WCAG AA contrast failures in both themes
--text-muted fails AA (~2.8:1). .hero__badge fails (~4.1:1). No prefers-reduced-motion.

## Persona Red Flags

Jordan (First-Timer): Materials assume domain knowledge. Steps too abstract. No pricing anchor.
Riley (Stress-Tester): Stats likely fabricated. No search. No shortcut for returning users.
Casey (Mobile): No hamburger menu. Hero SVG pushes CTAs below fold. No reduced-motion support.

## Minor Observations

- Steps grid repeat(3) but 4 items — orphaned 4th step
- Dead icon property on steps objects
- letter-spacing -0.04em at ban floor
- No text-wrap: balance on headings
- CTA radial gradient glow adds to AI gestalt
- Dark mode --text-muted fails AA
- No skip-nav link
- No :focus-visible styles
- Google Fonts @import is render-blocking
