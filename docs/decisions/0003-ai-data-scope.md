# 0003 — AI data scope and sensitive-data policy

- Status: accepted
- Date: 2026-10-27
- Related: STEP 10.2, STEP 13, SPEC 7.4

## Decision

Only the following leaves the machine for an AI request:

- the question the user typed;
- title, URL and snippet of the search results returned for that question;
- bounded plain text of at most five of those result pages, fetched by Serox
  (6,000 characters each).

Never sent: cookies, request headers, browsing history, bookmarks, settings,
sessions, other open tabs, local files and API keys.

## Sensitive-data exclusion

All page text and snippets pass through `SensitiveDataFilter` before they are placed in a
request. It replaces bearer tokens, API keys (`sk-`, GitHub, AWS, JWT), `password=` style
assignments, email addresses, card-like digit runs and phone numbers with `[redacted]`.
Redaction runs on every document and is not configurable.

## Untrusted input

Page text is data, not instructions. The prompt marks it as untrusted and every document is
wrapped in a delimiter that carries its source ID.

## Consequences

- Redaction is pattern based and can miss unusual formats; STEP 13 adds adversarial tests.
- Pages behind a login are not fetched because Serox sends no credentials.
