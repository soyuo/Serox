# STEP 1: Requirements and screen flows

## Source and scope

- Product and technical requirements: [SPEC.md](../../SPEC.md)
- UI reference: [Serox Figma design](https://www.figma.com/design/IZiradnUF4Q4Ok0XGeTtPy/Serox?node-id=30-3&t=3KCOPM8O9iPz75EA-1)
- Delivery order and acceptance gates: [STEP.md](../../STEP.md)

This document turns the existing product specification and Figma UI reference
into traceable implementation requirements. A feature is not considered
supported until its STEP acceptance tests pass.

## 1.1 Feature scope classification

| ID | Feature area | Classification | Source and boundary |
| --- | --- | --- | --- |
| BASE-001 | JavaFX desktop shell, application core, module interfaces, and background task/event flow | Foundation | SPEC 2–3, 5, 9; STEP 2–4. Required before feature work; not a user-facing completion claim. |
| BASE-002 | HTTP/HTTPS request, redirect/TLS checks, static HTML parsing, DOM, initial CSS subset, layout, and Canvas rendering | Foundation | SPEC 4, 11–13; ADR 0002. The MVP accepts static documents; script content is inert. |
| BASE-003 | URL validation, navigation history per tab, loading/error states, and tab management | MVP | SPEC 5, 12, 15.1; STEP 4, 7. Limited to approved schemes and supported documents. |
| MVP-001 | General web search through a replaceable Search API provider, result list, recent queries, empty/error/retry states | MVP | SPEC 6, 12.3, 15.2; ADR 0002 selects Brave Search API. Requires a user-supplied key. |
| MVP-002 | Search AI: gather permitted result content, summarize with Claude, and link claims to validated citations | MVP | SPEC 6–7, 12.4, 15.3; STEP 10. AI failure must leave ordinary search usable. |
| MVP-003 | User-requested analysis of the current page and follow-up conversation | MVP | SPEC 7, 15.4. Send only user-approved, bounded page content. |
| MVP-004 | Local history, bookmarks, settings, search cache, and session restoration | MVP | SPEC 5.4, 10; STEP 9. Secret/private data is excluded according to storage policy. |
| MVP-005 | Restricted MCP `search_and_summarize` tool with validation, permission boundary, and structured citations | MVP | SPEC 8, 15.5; STEP 12. Expose only the explicitly allowed search workflow. |
| EXP-001 | Figma browser shell refinements, site connection/security details, and user-facing connection summaries | Post-MVP expansion | Figma screens 1, 5-2–5-4; STEP 11. Status must reflect real service state when connected. |
| EXP-002 | AI panel visibility/placement, search result details, source preview, and settings preview/application | Post-MVP expansion | Figma screens 5-1 and 5-5; STEP 10–11. Keep temporary preview values separate from saved settings. |
| EXP-003 | Theme, accent color, font size, density, history, bookmark, and tab/session controls shown in Figma | Post-MVP expansion | Figma screens 1–4 and 5-5; SPEC 5, 10; STEP 9, 11. Wire to persisted application state. |
| LONG-001 | JavaScript execution and browser Web APIs | Long-term compatibility | SPEC 4.7, 13, 16. Requires a separate process boundary and security review before enablement. |
| LONG-002 | Broad web-standard compatibility: Flexbox, Grid, advanced CSS, media queries, animation, and dynamic sites | Long-term compatibility | SPEC 4.4–4.7, 16–17; not part of the initial renderer support claim. |
| LONG-003 | Multi-tab AI, private profiles, extensionable model providers, and wider agent browser control | Long-term compatibility | SPEC 1.3, 7.3, 8, 10, 16. Requires explicit privacy and permission design. |

Foundation rows describe prerequisites; MVP rows define the initial usable
browser and evaluation prototype; post-MVP rows are the requested Figma-led
product expansion; long-term rows require separate compatibility or security
work. The labels do not remove any feature from the project roadmap.
