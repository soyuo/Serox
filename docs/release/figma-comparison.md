# Figma design comparison

Done after the STEP 14 release candidate, once the Figma file
([Serox](https://www.figma.com/design/IZiradnUF4Q4Ok0XGeTtPy/Serox?node-id=30-3&t=3KCOPM8O9iPz75EA-1))
became readable. The canvas holds about 100 frames; only the screens below were compared.

## Compared

| Design frame | Result |
| --- | --- |
| Home (`30:5`) | Same structure and tokens as the implementation (logo, tagline, search pill, recent searches and connection cards, colors `#eef0f4`, `#dde1e8`, `#2563eb`, `#0f7b5f`). Read from the design code, not checked pixel by pixel. |
| Search result with AI summary (`30:106`) | Fixed in this change, see below. Layout differs, see "Still different". |
| Settings, design section (`31:1161`, `31:1193`) | Fixed in this change, see below. Sidebar and preview are still missing. |

## Fixed

- Address bar radius 22 px, and a cyan ring (`#22d3ee`) while the shown results carry an AI summary.
- AI summary card: light-blue `#e6effe` card with the sparkle label, 16 px text, 14 px radius.
- Citation markers `[S1]` are drawn as small numbered chips (`#2563eb`, 6 px radius) inside the text.
- Sources are cards with a numbered badge, title and URL, and the "AI summaries may contain errors" note.
- Settings: segmented options (selected `#e6effe` with a `#3e81f4` border) instead of radio buttons,
  44 px accent swatches with a check mark, and the design's labels (밝게 / 어둡게 / 시스템,
  작게 / 보통 / 크게, 여유롭게 / 촘촘하게) and accent palette.
- Design assets used: AI sparkle `df725.svg` and check mark `3f63a.svg`.

## Still different (not done)

- **Result layout.** The design puts the summary and sources on the left and a 532 px source
  preview pane on the right (a dashed "click a source to open the page" placeholder). Serox shows
  the result list with the AI panel beside it. Moving to the design layout is a larger change.
- **"Claude 요약" chip** inside the address bar and the **inactive tab** styling in the result frame.
- **Settings sidebar and live preview card.** The design has eight sections (일반, 검색, AI 제공자, 요약,
  디자인, 기록과 개인정보, MCP 서버, 단축키) and a preview. Serox has one appearance page; keys are read from
  environment variables, so the provider pages have nothing to edit.
- **Font size** has three steps (90 / 100 / 115 %) instead of a free slider, as in the design; an
  older saved value outside these steps shows no selected option until the user picks one.
- **Default accent.** The design's first swatch is `#3e81f4`, while the buttons use `#2563eb`. Serox
  keeps `#2563EB` as the default and uses it as the first swatch.
- **Not compared at all:** search states 1-16, error and loading frames, site information and
  permission frames, the other settings sections, and the keyboard shortcuts page.
