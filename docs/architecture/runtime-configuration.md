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

- `AppConfigurationLoader` reads Brave, Anthropic, Gemini, OpenAI and xAI keys
  from the documented environment variables. `SEROX_AI_PROVIDER` and
  `SEROX_AI_MODEL` select the initial adapter. See the README for names.
- `SecretValue.toString()` is redacted. Request adapters and protected-store
  serialization may explicitly reveal the value; logs and ordinary settings may not.
- On Windows, user-entered keys and ChatGPT OAuth tokens are stored in DPAPI
  CurrentUser encrypted files. Environment keys are not copied into those files.
  UI selections and user-saved keys override the initial environment configuration.
- `BrowserSettings` contains typed general, appearance, privacy, shortcut, search
  and summary preferences in SQLite; it never contains provider credentials.
- Missing credentials or unavailable secure storage leave the browser usable.
  An OS file lock prevents two desktop instances from overwriting token records.
- See [secret handling](../SECURITY.md) and [browser authentication ADR](../decisions/0006-browser-authentication.md).

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
