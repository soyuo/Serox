# Network policy

Reviewed for STEP 13.1.

| Area | Policy | Where |
| --- | --- | --- |
| Schemes | Only `http` and `https`; no user info in the address. | `PageLoadRequest`, `PublicAddressPolicy` |
| TLS | Platform certificate validation; there is no way to bypass errors. | JDK `HttpClient` |
| Redirects while browsing | Followed by the JDK client; HTTPS is never downgraded to HTTP. | `HttpRequestDispatcher` |
| Redirects for AI and MCP page reads | Followed manually, at most 5 hops, each hop re-checked. | `GuardedTransport` |
| Targets for AI and MCP page reads | Public internet only: no loopback, private, link-local, carrier-grade NAT, multicast or `.local`-style names. | `PublicAddressPolicy` |
| Response size | Browsing 8 MiB, AI and MCP reads 1 MiB; the transfer is cancelled at the limit. | `LimitedBodySubscriber` |
| Content type | Only HTML documents are processed. | `HttpResponseHandler` |
| Downloads | Not supported. Non-HTML responses are refused, nothing is written to disk. | `HttpResponseHandler` |
| API keys | Sent only to the provider endpoint; those clients never follow redirects. | `BraveSearchProvider`, `ClaudeProvider` |

## Known limits

- A page read resolves the host before connecting and the JDK resolves it again when it
  connects, so DNS rebinding between the two is not fully excluded. Responses are small,
  HTML-only and still pass the redaction filter before reaching an AI provider.
- The user can still type a local address into the address bar; that is deliberate and does
  not feed the AI or MCP features.
