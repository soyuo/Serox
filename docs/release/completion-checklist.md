# Completion checklist

Cross-check of SPEC section 17.5 and the completion criteria of each STEP, done for STEP 14.6.
"Evidence" names automated tests or documents; "Gap" lists what was not proven.

## SPEC 17.5 — prototype criteria

| Criterion | Status | Evidence | Gap |
| --- | --- | --- | --- |
| JavaFX application runs | Done | App image started on the bundled runtime; `UiLayoutSmokeTest` | |
| Open HTML pages by URL | Done | `BrowserNavigationIntegrationTest`, `EndToEndScenarioTest` | |
| Show text, links and images | Done | Rendering golden files; images are placeholders with alt text | Real image decoding is not implemented |
| Search API search works | Done with stubs | `BraveSearchProviderTest` against a local server | Never run against the live Brave API; needs a key |
| Claude summarizes results | Done with stubs | `ClaudeProviderTest`, `AiSearchServiceTest` against a local server | Never run against the live Claude API; needs a key |
| Source links | Done | `CitationManager` validation tests, AI panel source list | |
| MCP search tool callable | Done | `StdioMcpServerTest`, `SecurityBoundaryTest`, packaged launcher check | Not tried with a real MCP client application |
| Errors do not end the app | Done | `ErrorRecoveryTest`, `BrowserIsolationAndPolicyTest` | |

The "full browser" criteria (JavaScript, site isolation, updates) are out of scope for this
release; see the roadmap in the README.

## Per-step criteria

| STEP | Criterion | Status | Evidence |
| --- | --- | --- | --- |
| 0 | Reproducible build and tests; decisions documented | Done | `mvn clean verify`, `docs/decisions` |
| 1 | Features linked to requirements; scope separated | Done | `docs/requirements/screen-flows.md`, ADR 0002 |
| 2 | Empty app builds; no cyclic modules | Done | `docs/architecture` |
| 3 | Window and screens display without network | Done | `UiLayoutSmokeTest` |
| 4 | Tab switching and navigation state with fake responses | Done | `BrowserNavigationIntegrationTest` |
| 5 | Documents parse to the defined DOM; network failure does not exit the app | Done | parser and network tests |
| 6 | Fixed pages lay out repeatably; supported range is clear | Done | `RenderingFixtureTest` golden files, `docs/rendering-support.md` |
| 7 | Open pages and switch tabs; one tab's error does not stop others | Done | `BrowserIsolationAndPolicyTest` |
| 8 | Search works with a key; errors and retry | Done with stubs | `SearchService` tests; no live key run |
| 9 | Settings and session survive a restart | Done | `SessionKeeperTest`, `EndToEndScenarioTest` |
| 10 | AI answers show verified sources; provider errors do not block browsing | Done with stubs | `AiSearchServiceTest`, `CitationManagerTest` |
| 11 | Each designed setting and status matches real state and persists | Partly | `SettingsServiceTest`, `SeroxRuntimeStatusTest`; design compared for home, results and settings, see [figma-comparison.md](figma-comparison.md) |
| 12 | Allowed MCP tools work; others are refused; browser keeps working | Done | `AccessPolicyTest`, `SecurityBoundaryTest`, `UnapprovedClientTest` |
| 13 | Security tests pass; limits and failure handling documented | Done | `GuardedTransportTest`, `UntrustedEvidenceTest`, `LogHygieneTest`, `docs/security` |
| 14 | Reproducible build, MSI and test results; limits stated | Done except install/uninstall on a clean machine | `scripts/package-windows.ps1`, `docs/release` |

## Known gaps to close before a public release

- **Figma comparison (STEP 3, 10, 11):** the home, search-result and design-settings screens were
  compared and the mismatches that fit the current layout were fixed. The source pane (it shows an excerpt, not the live page) and
  most other frames still differ or were not compared; see
  [figma-comparison.md](figma-comparison.md).
- **Live providers:** neither the Brave Search nor the Claude API has been called with a real key.
- **Installer:** a per-user variant was installed, run and uninstalled; the shipped per-machine MSI needs an administrator and was not installed, upgrades were not tried, and
  the MSI is unsigned.
- **Unsupported web features:** JavaScript, flexbox, grid and image decoding (see
  `docs/rendering-support.md`).
