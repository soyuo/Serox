# STEP 2.4: Runtime wiring, configuration, logging, errors, and events

## Dependency wiring

Use manual constructor injection. `AppBootstrap` is the composition root: it
configures logging, loads process configuration, creates infrastructure
adapters, and returns an application-scoped `SeroxRuntime`. Views and use cases
receive their dependencies through constructors. Do not add a service locator
or dependency injection framework while the project is a single module.

`SeroxApplication.start` creates the runtime and passes its event bus to the
JavaFX shell. `stop` closes view subscriptions first, then closes the runtime.
Future browser, search, AI, MCP, and storage implementations are added to the
same composition root and injected through their public interfaces.

## Settings and secret loading

- `AppConfigurationLoader` reads only `SEROX_BRAVE_SEARCH_API_KEY` and
  `SEROX_ANTHROPIC_API_KEY` from the process environment.
- Values are wrapped by `SecretValue`. Its string representation is always
  `[REDACTED]`; only provider request adapters may call `revealForRequest()`.
- Missing values remain absent. The browser starts without those services and
  reports them as unconfigured.
- `BrowserSettings` contains typed appearance preferences and cannot hold
  provider credentials. User preferences are loaded through `SettingsRepository`
  when the SQLite storage step is implemented.
- Production secret persistence should use the OS credential store described in
  [SECURITY.md](../../docs/SECURITY.md). Environment variables are the initial
  development input; neither source is copied to settings, logs, or arguments.

## Logging

Use JDK `java.util.logging` configured by `SeroxLogging`. Log operation names,
stable error codes, and correlation IDs. Do not log API keys, authorization
headers, cookies, page text, raw search queries, or exception messages from an
untrusted provider. When logging an exception, log only after removing request
and response bodies and secret-bearing fields.

## Error model

`ApplicationError` contains an `ErrorCode`, sanitized user-facing message, and
retryability. It deliberately excludes raw exception text, request data, and
credentials. Asynchronous service methods may complete exceptionally at their
module boundary; Application Core maps expected failures to `ApplicationError`
and emits `OperationFailed`. Unknown failures map to `UNKNOWN` with a generic
safe message while diagnostic logging follows the rules above.

## Event delivery and JavaFX threading

`BrowserEventBus` is an in-process typed event boundary. The initial
`InProcessBrowserEventBus` is thread-safe, delivers on the publishing thread,
and isolates listener exceptions so one subscriber cannot prevent other
listeners from receiving an event. Subscribers own an `EventSubscription` and
must close it with their view/runtime lifecycle.

Events carry immutable snapshots and sanitized messages. A JavaFX subscriber
must marshal state changes through `Platform.runLater` when the publisher is
not on the JavaFX Application Thread. `BrowserShellView` applies this rule for
service status and operation errors. Network, parsing, AI, and storage work
remain outside that UI thread.
