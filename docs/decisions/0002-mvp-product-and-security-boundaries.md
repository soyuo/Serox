# ADR 0002: MVP product, compatibility, and security boundaries

- Status: Accepted
- Date: 2026-10-27
- Related: STEP 0.3, STEP 5, STEP 6, STEP 8, STEP 10, STEP 13, STEP 14

## Decisions

### Search provider

Use Brave Search API for the first general web-search provider behind the
`SearchProvider` interface. It returns web-search results suitable for the
browser results view. Brave documents a Search plan at $5 per 1,000 requests,
with $5 in monthly credits, prepaid credits, and API-key authentication via
`X-Subscription-Token`. No account, key, or paid credits are created by this
decision. The MVP uses a user-supplied key and makes no paid requests unless the
user has explicitly configured a funded plan. Do not persist result payloads
until the selected plan's storage rights have been checked.

Google Custom Search JSON API is not selected: Google says the API is closed to
new customers and will be discontinued on January 1, 2027. The provider adapter
keeps later replacement possible.

References: [Brave Search API plans](https://brave.com/search/api/),
[Brave API authentication and key handling](https://api-dashboard.search.brave.com/documentation/guides/authentication),
[Google Custom Search JSON API lifecycle](https://developers.google.com/custom-search/v1/overview).

### First HTML and CSS support scope

The HTML parser recognizes the element set already listed in SPEC.md section
4.2. It builds a static document; `<script>` content is inert and is never
executed in the MVP. Initial CSS support is limited to colors and backgrounds,
font size and weight, margin, padding, and border. Initial layout is block and
inline formatting, the box model, text wrapping, line breaks, viewport and
scroll offset, and basic image placement. Flexbox, Grid, media queries,
pseudo-classes, transforms, transitions, animations, and custom properties are
later compatibility work and require explicit tests before being claimed as
supported.

### Isolation boundary

In the MVP, HTML/CSS parsing and rendering run in the application process but
receive page content only as data. JavaScript execution is disabled. Network
access is limited to the browser's HTTP client; parsers and rendering code do
not receive file-system, process, or credential access. Enforce document and
resource size limits and URL/redirect policy in the navigation pipeline.
This is a capability boundary inside one process, not an OS sandbox; do not
describe the MVP as securely sandboxing hostile pages. Any future JavaScript
runtime must run out of process behind a narrow message interface and receive
no direct file-system or credential access before it can be enabled.

### Initial platform and distribution

Target Windows 11 x64 first. The release candidate is an MSI produced with
`jpackage`, containing its Java runtime and JavaFX modules. `app-image` is the
local smoke-test output. The MVP has no automatic updater; upgrades are
installed with a later MSI. Other operating systems and code signing are
separate release decisions.

## Consequences

- A Brave key is optional for the browser shell but required for live general
  search. Search UI must show an actionable unconfigured state.
- The desktop app uses user-provided credentials; no vendor credential ships
  in the installer. Development reads keys from environment variables, and
  production stores them in the operating system credential store.
- MVP compatibility claims must list only the HTML, CSS, and layout behavior
  above. Unsupported constructs must fail safely or be ignored without
  executing active content.
- STEP 13 must verify the URL, redirect, resource-size, and parser boundaries.

## Open decisions

- Select and validate the JavaScript runtime and out-of-process sandbox before
  the JavaScript phase.
- Set explicit per-user and project usage limits before enabling paid API
  requests in a release.
- Revisit OS support, signing, and update delivery before public distribution.
