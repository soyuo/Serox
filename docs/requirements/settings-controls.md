# Connected settings acceptance

Scope: STEP 11.3–11.7, issue #77. The existing general, privacy, shortcuts and MCP
pages remain connected to their current services. Figma references are search
`31:774`, AI `31:836`, summary `31:1098` and appearance `31:1161` in the supplied file.

| Requirement | Owner | Observable acceptance | Verification |
| --- | --- | --- | --- |
| S-AI-CONNECTION | `AiConnectionService`, provider adapters | Key testing and browser authorization change the provider used by later searches; credentials restore securely | ADR 0006, auth tests, AI service tests |
| S-SEARCH-KEY | Application composition, `SearchService` | Saving a key encrypts it with DPAPI, immediately replaces the Brave adapter and restores it on restart | Protected store and runtime-status tests; live key requests still require a user's key |
| S-SEARCH-PREFS | `SearchPreferences`, Brave adapter, `PageFetcher` | Safe-search mode reaches the API; selected page/text/time bounds apply to evidence gathering | `BraveSearchProviderTest`, `PageFetcherTest` |
| S-SUMMARY-PREFS | `SummaryPreferences`, `PromptBuilder`, AI panel | Language, redacted style instructions and presentation choices affect later summaries without disabling grounding | `SummaryInstructionsTest`, preference storage and UI tests |
| S-CONTENT-RECOVERY | SQLite settings repository | Choices survive reopen; older profiles keep their existing answer language; malformed new values do not erase general/appearance settings | `ContentSettingsStorageTest` |
| S-CANCEL-COLLECTION | `AiSearchService`, `PageFetcher` | Cancel and deadlines reach the original HTTP futures, including when a user chooses a longer collection timeout | `PageFetcherTest`, AI cancellation tests |

## Behavior and limits

- Evidence collection reads 1–10 result pages, with a selected deadline of 10–300
  seconds. Search still returns its normal result list independently. Each HTTP
  evidence response stays limited to 1 MiB; combined redacted evidence stays capped
  at 24,000 characters, even when a per-source limit of 20,000 is selected.
- The default summary language follows the question. Older saved profiles continue
  to use the language from General settings until changed. Choosing a language in
  General also selects the summary's explicit "일반 설정 언어" option.
- Hiding inline source chips does not disable citation validation or the source
  list. Hiding key points changes presentation, not evidence or normal search.
- Style instructions are limited to 2,000 characters. They are redacted and escaped
  before being appended as user preferences; the system's grounding rules remain.
- Brave key saving does not claim that a live search was tested. The next search
  reports provider authentication/network errors through the existing retry flow.
- `robots.txt` interpretation and multi-pass long-document summarization are not
  implemented. Those controls are disabled with an explanation. Current behavior
  reads a bounded prefix and skips unreadable pages. The always-on fallback rule
  preserves normal search and shows AI errors. Arbitrary compatible providers remain
  disabled until endpoint and credential boundaries can be validated.
- Desktop preferences and protected keys do not silently change externally launched
  MCP processes; their explicit environment configuration remains authoritative.

The reusable row layout stacks controls below labels at small widths. Provider cards
use three, two or one column according to available width. Static Figma icons are
bundled locally; switches, segmented choices and the slider are live JavaFX controls.
