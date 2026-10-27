# STEP 2.3: Public interfaces and shared models

The types below define compile-time boundaries inside the single Maven module.
They do not implement network, provider, persistence, MCP, or UI behavior.

## Application and browser contracts

- `BrowserCommandService` is the only command surface exposed to Presentation.
  It accepts tab IDs and user-entered address/query text, returns asynchronous
  snapshots/results, and never exposes JavaFX or engine implementation types.
- `BrowserEngine` accepts a `PageLoadRequest` and returns a `PageSnapshot`.
  The request accepts only absolute HTTP/HTTPS URIs. The page snapshot exposes
  normalized visible text and metadata, not DOM nodes or renderer internals.
- `TabId`, `TabSnapshot`, and `NavigationStatus` are immutable shared values.
  Tab snapshots contain only the current URI, title, navigation state, and
  history availability; content and session-private state are excluded.

## Search and AI contracts

- `SearchProvider` accepts a bounded `SearchRequest` and returns a normalized
  `SearchResponse`. Provider-specific HTTP types and JSON stay inside the
  provider adapter. Results require absolute HTTP/HTTPS URLs and a positive rank.
- `AiProvider` accepts a question and immutable `EvidenceDocument` list, then
  publishes `AiEvent` values using Java `Flow.Publisher`. Subscribers control
  demand and cancellation through `Flow.Subscription`.
- AI context contains explicit source IDs, source URLs, titles, bounded text,
  and retrieval times. It contains no API key or direct access to browser state.
  The application use case is responsible for consent, input limits, and
  attaching verified evidence before creating `AiRequest`.
- A response emits text chunks and one terminal `AiCompleted` or `AiFailed`
  event. `AiCitation.sourceId` is untrusted until CitationManager validates it
  against the submitted evidence.

## Storage and MCP contracts

- Repository ports are under `core.port.storage`; SQLite implementations are
  under `storage`. All methods are asynchronous so the JavaFX Application
  Thread never performs persistence work.
- `HistoryEntry` and `Bookmark` store URI/title/timestamps only. `SessionSnapshot`
  stores tab metadata and the active tab, never page content. `BrowserSettings`
  contains typed appearance values only. API credentials use the separate
  secret-storage path in [SECURITY.md](../../docs/SECURITY.md).
- `SeroxMcpServer` exposes lifecycle only. Tool schemas, request authorization,
  and transport configuration remain private to MCP and are implemented in
  STEP 12.

## Error and threading contract

`CompletionStage` methods do not block their caller. Failures complete the
stage exceptionally until the typed error mapping is added in STEP 2.4. The AI
publisher sends sanitized `AiFailed` events. Application Core converts module
results into application events; Presentation applies state only on the JavaFX
Application Thread. Detailed error codes and event payloads are specified in
STEP 2.4 before implementations depend on them.
