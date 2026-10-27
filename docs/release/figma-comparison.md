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
- "Claude 요약" chip in the address bar (opens the AI panel).
- AI not connected, failed or cancelled: a banner above the result list with a retry button instead of a side panel (checked in a rendered image against frames 31:2809 and 31:2639).
- Key points as a label column plus text (the prompt now asks for "label: text" bullets; a bullet without a label still shows as plain text) (checked in a rendered image).
- Result screen with a finished summary: wide summary, fixed source pane with the dashed placeholder.
- Privacy page: recent searches with delete buttons, "clear all searches" and "clear visits" behind a confirm dialog, and the empty state (frames 31:5204, 31:5346).
- Settings: the sidebar with eight sections and a live preview card; segmented options (selected `#e6effe` with a `#3e81f4` border) instead of radio buttons,
  44 px accent swatches with a check mark, and the design's labels (밝게 / 어둡게 / 시스템,
  작게 / 보통 / 크게, 여유롭게 / 촘촘하게) and accent palette.
- Design assets used: AI sparkle `df725.svg` and check mark `3f63a.svg`.

## Still different (not done)

- **Result layout partly.** Once an AI summary has finished, the screen now matches the design (wide
  summary on the left, 532 px dashed source pane on the right; checked in a rendered image). The
  source pane still shows the stored excerpt of the source, not the live page. Before a summary is
  ready, without an AI key, or after a failure, Serox keeps the result list and the preview, because
  the design only shows the finished state. Key points are bullet rows, not the design's
  "label + text" table, since the model's answer has no labels.
- **Inactive tab styling** in the result frame.
- **Settings pages other than the design page.** The sidebar and live preview now exist (checked in a
  rendered image against the design). Only appearance is editable. The other seven pages show real
  status and explanations; the design's provider, summary, history and shortcut pages contain controls
  (sign-in, clear history, remap keys) that Serox does not have, because keys come from environment
  variables. The preview follows the accent color but not the theme.
- **Font size** has three steps (90 / 100 / 115 %) instead of a free slider, as in the design; an
  older saved value outside these steps shows no selected option until the user picks one.
- **Default accent.** The design's first swatch is `#3e81f4`, while the buttons use `#2563eb`. Serox
  keeps `#2563EB` as the default and uses it as the first swatch.
- **Not compared at all:** search states other than the two above, error and loading frames, site information and
  permission frames, the other settings sections, and the keyboard shortcuts page.
- Numbered result rows with an open-row marker and a result count heading.
- Source pane header and read count above the summary; address-error pages for connection failures.
- Privacy settings include search history and visit retention choices.
