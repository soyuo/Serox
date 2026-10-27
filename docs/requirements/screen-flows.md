# STEP 1: Requirements and screen flows

## Source and scope

- Product and technical requirements: [SPEC.md](../../SPEC.md)
- UI reference: [Serox Figma design](https://www.figma.com/design/IZiradnUF4Q4Ok0XGeTtPy/Serox?node-id=30-3&t=3KCOPM8O9iPz75EA-1)
- Delivery order and acceptance gates: [STEP.md](../../STEP.md)

This document turns the existing product specification and Figma UI reference
into traceable implementation requirements. A feature is not considered
supported until its STEP acceptance tests pass.

## 1.1 Feature scope classification

<!-- markdownlint-disable MD013 -->
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
<!-- markdownlint-enable MD013 -->

Foundation rows describe prerequisites; MVP rows define the initial usable
browser and evaluation prototype; post-MVP rows are the requested Figma-led
product expansion; long-term rows require separate compatibility or security
work. The labels do not remove any feature from the project roadmap.

## 1.2 Figma screen map and primary flows

<!-- markdownlint-disable MD013 -->
| Figma frame/group | User entry point | Main purpose | Primary flow |
| --- | --- | --- | --- |
| 1. 기본 화면 | App launch or new tab | Browser chrome, home search, recent searches, and AI/MCP connection summaries | Enter a query, choose a recent query, open a page, or open a product panel. |
| 2. 설정 메뉴 | Settings/menu control in browser chrome | Provide navigation to browser utilities and settings | Choose a utility or settings destination; close the menu without changing page state. |
| 3. 검색 화면 | Home or address/search input | Submit and present a search request | Enter query → submit → loading → results, empty result, cancellation, or failure. |
| 4. 설정 화면 | Settings destination | Configure browser and service preferences | Select a setting → preview temporary value where applicable → apply, cancel, or restore default. |
| 5-1. 검색 결과 | Search completion | Show answer summary, key points, source list, and original-content preview area | Select a source to open its page or inspect its original content. |
| 5-2. AI 제공자별 화면 | AI provider/settings destination | Show provider-specific configuration and connection state | View or edit supported provider settings → validate connection → ready or actionable failure. |
| 5-3–5-4. MCP | MCP settings or connection summary | Show MCP service and connection/tool status | Configure or inspect MCP → connect/disconnect → ready or actionable failure. |
| 5-5. 디자인 설정 변형 | Appearance settings | Present alternate theme, accent, typography, panel placement, and density choices | Change a value → preview → apply, cancel, or restore defaults. |
<!-- markdownlint-enable MD013 -->

The design groups map to these user journeys:

1. **Start and browse:** launch/new tab → enter URL or query → show page or
   search state.
2. **Search and inspect:** submit query → show results → select a result,
   citation, or original preview.
3. **Ask AI:** start AI search or page analysis → show progress → display an
   answer and verified sources, or keep ordinary results available on failure.
4. **Manage browser state:** open the settings menu → choose history,
   bookmarks, or settings → return to the active tab without losing its state.
5. **Configure services:** open AI or MCP settings → inspect/configure the
   connection → report its actual state and any error.
6. **Personalize appearance:** open appearance settings → change temporary
   values → preview → apply, cancel, or restore defaults.

The frame grouping defines the product flows, while later requirements specify
each control's exact state and module contract. A visual state is not evidence
that the associated service is already implemented or connected.

## 1.3 Control contracts and visible states

Each screen control follows the shared task states `IDLE`, `LOADING`,
`SUCCESS`, `FAILED`, and `CANCELLED` where the operation is asynchronous.
Search and AI views may also show `PARTIAL` when some sources fail but usable
results remain. A disabled control explains the missing prerequisite when the
reason is not self-evident.

<!-- markdownlint-disable MD013 -->
| ID | Control | Input or event | State | Action and outcome | Failure or empty behavior |
| --- | --- | --- | --- | --- | --- |
| NAV-01 | Address/search field and submit | User-entered URL or query; Enter or search action | Focused, idle, loading, page loaded, or search pending | NavigationManager classifies input and routes it to navigation or Search. | Reject unsupported/invalid schemes with an explanation; empty input does nothing. |
| NAV-02 | Back, forward, reload, stop | Click or documented keyboard shortcut | Enabled only when the selected tab has a history entry or active request | Navigate within the selected tab, repeat its current request, or cancel it. | No history means disabled; request failure leaves the tab open with an error state. |
| TAB-01 | New tab, tab selector, close tab | Create, select, or close action | Active, inactive, loading, or failed per tab | TabManager changes the active TabSession and preserves other tabs' state. | Closing an active tab selects a remaining tab; an empty tab shows the home state. |
| SEARCH-01 | Home search and query submit | Non-empty query; submit action | Idle, loading, partial, success, empty, failed, or cancelled | SearchCoordinator requests Brave results and presents the returned list. | Empty response gets a no-results state; API/timeout errors offer retry; cancel stops the current request. |
| SEARCH-02 | Search result row/card | Select result title or URL | Available, selected/opening, or unavailable | Open the result URL in the active or a new tab according to the selected action. | Missing/invalid URL cannot navigate; retain the result list and show the reason. |
| AI-01 | AI search or current-page analysis action | Query or user-approved current-page content | Idle, loading, streaming, success, partial, failed, or cancelled | ContextBuilder applies consent, filtering, and limits; AnswerGenerator streams an answer with validated citations. | Keep ordinary search/page content usable; show provider, timeout, limit, or cancellation status without secrets. |
| AI-02 | AI panel toggle and placement | Open/close or choose a placement | Open/closed; current placement | Toggle the panel or update its draft placement without resetting the active search. | If no AI result exists, show an explanatory empty panel; preserve the page when closed. |
| SRC-01 | Citation and original-content preview | Select citation/source or preview action | Available, loading, opened, or unavailable | CitationManager verifies the source ID; show its text or open its URL. | Invalid citation is not followed; unavailable source retains the answer and reports the issue. |
| SITE-01 | Site information/security control | Open details for the active page | Unknown, checking, verified HTTPS, plain HTTP, or failed | Show active URL and only the connection/security facts the network layer verified. | Do not imply a page is safe; TLS errors remain blocked and display the validation failure. |
| MENU-01 | Settings/menu and destination items | Open, select destination, or dismiss | Closed/open; selected destination | Navigate to settings, history, bookmarks, or service details while retaining active tab state. | Missing data shows an empty state; dismissal changes no browser or saved state. |
| PREF-01 | Theme, accent, font size, density, panel placement | Choose a supported option | Saved value or changed draft value | Preview draft appearance without writing it to storage. | Unsupported values are omitted or disabled; invalid draft changes do not replace the saved value. |
| PREF-02 | Apply, cancel, restore defaults | Explicit action | Clean, dirty, applying, or failed | Apply persists draft settings; cancel reverts to saved values; restore defaults resets the draft. | Storage failure preserves recoverable draft state and explains that the setting was not saved. |
| CONN-01 | AI provider configuration/status | Provider selection or connection check | Not configured, checking, connected, or failed | Read provider availability and show configuration/connection state; do not claim support before an adapter exists. | Missing key/configuration gives setup guidance; errors redact secret values. |
| MCP-01 | MCP connection/status | Connect, disconnect, or inspect action | Disconnected, connecting, connected, or failed | MCP lifecycle and AccessController report only the authorized connection state. | Permission/transport failures remain disconnected and explain the next action. |
| DATA-01 | History and bookmark entries/actions | Select, save, remove, or clear an entry | Populated, empty, loading, or failed | Repository reads/writes the requested item and opens selected URLs through NavigationManager. | Empty collections explain how to create entries; storage errors do not terminate browsing. |
<!-- markdownlint-enable MD013 -->

Search and AI request IDs must be tracked so an older response cannot replace a
newer view. These contracts describe expected UI behavior; module ownership is
mapped separately in section 1.4.

## 1.4 Requirement ownership and scenario links

<!-- markdownlint-disable MD013 -->
| Requirement ID | Primary owner | Collaborating modules | User scenario |
| --- | --- | --- | --- |
| BASE-001 | Presentation | Application Core | All scenarios; UI sends commands and renders events. |
| BASE-002 | Browser Engine | Application Core, Presentation | Start and browse; Search and inspect original pages. |
| BASE-003 | Application Core | Browser Engine, Storage, Presentation | Start and browse; manage browser state. |
| MVP-001 | Search Engine | Application Core, Storage, Presentation | Search and inspect. |
| MVP-002 | AI Engine | Search Engine, Browser Engine, Storage, Presentation | Ask AI; search and inspect citations. |
| MVP-003 | AI Engine | Browser Engine, Application Core, Storage, Presentation | Ask AI about the current page. |
| MVP-004 | Storage | Application Core, Presentation | Manage browser state and restore a session. |
| MVP-005 | MCP Server | AI Engine, Search Engine, Application Core | Configure services; external authorized search request. |
| EXP-001 | Presentation | Application Core, Browser Engine, Search Engine, AI Engine, MCP Server | Start and browse; configure services; inspect site information. |
| EXP-002 | Presentation | Application Core, Search Engine, AI Engine, Storage | Search and inspect; ask AI; personalize the AI panel. |
| EXP-003 | Presentation | Application Core, Storage | Manage browser state; personalize appearance. |
| LONG-001 | Browser Engine | Application Core, security boundary | Start and browse dynamic documents after separate process isolation is approved. |
| LONG-002 | Browser Engine | Presentation | Start and browse documents using later compatibility features. |
| LONG-003 | Application Core | AI Engine, MCP Server, Storage | Ask AI across selected tabs; use future profiles or authorized agent control. |
<!-- markdownlint-enable MD013 -->

Module boundaries follow SPEC 3 and 5–10: Presentation invokes public
Application Core commands, then consumes state events; it does not call engine
internals directly. Search, AI, MCP, and Storage remain independently
replaceable behind their public interfaces.

<!-- markdownlint-disable MD013 -->
| Scenario | Command path | State/data path | Required containment |
| --- | --- | --- | --- |
| Start and browse | Presentation → NavigationManager → Browser Engine | TabSession and NavigationHistory → page state → UI event | Invalid schemes, TLS failures, and a failed page remain scoped to the selected tab. |
| Search and inspect | Search field → NavigationManager → SearchCoordinator → SearchProvider | SearchResponse → deduplicated results → UI; cache through Storage | API failure or no results must not disable direct URL navigation. |
| Ask AI from search | Search result → PageFetcher/ContentExtractor → ContextBuilder → AiProvider | EvidenceStore and CitationManager → answer/citations → UI | Fetch or AI failure preserves available search results; reject unknown citation IDs. |
| Analyze current page | Explicit user action → consent check → bounded document context → AiProvider | Answer and cited current-page references → AI panel | Do not send unapproved page data; cancel and provider errors do not alter browser navigation. |
| Manage tabs and saved state | Tab command → TabManager/SessionManager | Session and tab repositories → restored TabSessions | A storage error cannot close the active page or terminate the app. |
| Personalize appearance | Settings view → draft setting command → settings repository on apply | Draft preview → saved setting event → updated UI | Cancel discards only draft values; persistence failure keeps the last saved value. |
| Configure services/MCP | Settings view → provider or MCP lifecycle interface → AccessController for tools | Connection event → status view | No connected/ready badge without a successful state event; unauthorized tools are rejected. |
<!-- markdownlint-enable MD013 -->

Every UI-visible state must be derived from its owner module's state or event.
Placeholder sample data is permitted only before a service is wired and must
not be presented as a live connection or successful result.

## 1.5 Unsupported, disabled, and recovery behavior

<!-- markdownlint-disable MD013 -->
| Condition | Visibility and action | User-facing Korean copy | Recovery |
| --- | --- | --- | --- |
| Feature belongs to a later roadmap phase | Keep it out of the active MVP flow. If shown to preserve the Figma layout, disable it and state the prerequisite. | `아직 지원하지 않는 기능입니다.` | Continue using available browser/search features. |
| Control requires a missing provider or connection | Keep the control visible only in its relevant view and disable the dependent action. | `서비스 연결을 완료하면 사용할 수 있습니다.` | Open the corresponding provider or MCP settings. |
| Search API is not configured | Search action reports configuration state; never show a false connected badge. | `검색 서비스를 설정한 뒤 다시 시도해 주세요.` | Configure credentials using the supported secret handling path. |
| Navigation or search is in progress | Show progress in the affected tab or search view; provide Cancel when the operation supports cancellation. | `페이지를 불러오는 중…` / `검색 중…` | Keep other tabs responsive; allow cancel and accept only the latest request result. |
| Search returned no results | Show a dedicated empty state, not an error card. | `검색 결과가 없습니다. 다른 검색어로 다시 시도해 주세요.` | Edit the query and submit again. |
| No history or bookmarks exist | Keep the list view and explain how items appear. | `방문 기록이 없습니다.` / `저장한 북마크가 없습니다.` | Browse a page or save a bookmark. |
| AI panel has no response yet | Show a neutral explanatory empty state; do not show fabricated summary text. | `검색 결과를 AI로 요약하면 여기에 표시됩니다.` | Start an AI search when an AI provider is configured. |
| Some search-result pages could not be fetched | Retain available results and mark the answer as partial. | `일부 출처의 내용을 가져오지 못했습니다. 검색 결과는 계속 확인할 수 있습니다.` | Open available results or retry the affected request. |
| Search API, network, or service request failed | Keep the current page and available results. Add Retry only when the request can safely be repeated. | `요청을 완료하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.` | Retry or continue browsing; do not clear user input. |
| AI provider is unavailable or request fails | Keep ordinary browsing and results active; do not replace them with an AI error page. | `AI 응답을 가져오지 못했습니다. 일반 검색 결과는 계속 사용할 수 있습니다.` | Retry after checking provider status or continue without AI. |
| User cancels a request | Stop the active request and return the affected view to an actionable state. | `요청을 취소했습니다.` | Submit again when ready. |
| URL scheme is invalid or unsupported | Do not navigate; keep the entered value available for correction. | `HTTP 또는 HTTPS 주소를 입력해 주세요.` | Correct the address or enter a search query. |
| TLS certificate validation fails | Block navigation and display the specific failure state; do not provide a bypass button in the MVP. | `보안 인증서를 확인할 수 없어 페이지를 열지 않았습니다.` | Return to the previous page or enter another address. |
| Citation ID is missing or does not match a retrieved source | Do not link the claim to an unverified source. Keep other valid citations. | `출처를 확인할 수 없어 이 인용은 표시하지 않았습니다.` | Review remaining verified sources. |
| Settings persistence fails | Keep the saved value unchanged and preserve the draft if possible. | `설정을 저장하지 못했습니다. 다시 시도해 주세요.` | Retry, cancel the draft, or continue with the saved setting. |
| MCP connection or permission check fails | Show disconnected/failed state and reject the unauthorized operation. | `MCP 연결을 완료하지 못했습니다. 연결 설정과 권한을 확인해 주세요.` | Correct settings or permissions and reconnect. |
<!-- markdownlint-enable MD013 -->

### State presentation rules

- Hide only functionality that is explicitly outside the active roadmap phase or
  would create an unsafe interaction. Keep relevant Figma-led expansion discoverable
  when its implementation phase begins.
- A visible but unavailable action uses a disabled control plus a concise reason;
  a tooltip is supplementary and must not be the only explanation.
- Loading, empty, partial, failed, and cancelled states use distinct visual
  treatments and accessible labels. Do not communicate state by color alone.
- Retry keeps the current query/page context. Stale asynchronous responses must
  not replace the state of a newer request.
- Never put API keys, cookies, authorization values, or sensitive page text in
  user-facing errors or status copy.
- Do not claim a site, AI provider, or MCP server is connected, secure, or ready
  without a successful state from its owning module.
