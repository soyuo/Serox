# Secret and log handling

## API credentials

Serox does not ship a shared Brave Search or Anthropic API key. Users supply
their own credentials. Development reads them from the current process
environment:

- `SEROX_BRAVE_SEARCH_API_KEY`
- `SEROX_ANTHROPIC_API_KEY`

Do not put real values in source files, `.env` files, Maven properties,
command-line arguments, screenshots, issue or PR text, or test fixtures. Keep
local secret files out of Git. In a release build, read credentials through an
OS-backed credential-store abstraction; on Windows, use Windows Credential
Manager. Do not persist keys in Java Preferences, SQLite, browser history, or
plain-text configuration.

If a credential is absent, the related integration remains unavailable and
the browser shell and ordinary navigation continue to work.

## Logs and diagnostics

Never log API keys, `Authorization` or `X-Subscription-Token` headers, raw
credential-store responses, or unredacted request bodies. Redact URL user-info
and sensitive query parameters before logging. Log provider name, operation,
elapsed time, status code, and a safe error category instead. User-visible
errors must not include response bodies that may echo secrets.

Tests use fake credentials and mock providers only. A test must never read
credentials from the developer's environment or make a paid provider request.

## Current implementation status

These are project requirements for the integration steps; the current JavaFX
scaffold does not yet read or persist API credentials.
