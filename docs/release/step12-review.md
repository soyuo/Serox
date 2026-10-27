# STEP 12 follow-up: MCP boundary review

Reviewed 2026-10-27 on the STEP 11 follow-up (`b4fe7ca`), issue #79.
This verifies existing STEP 12 behavior after the settings/provider changes;
it does not add a second implementation of the already merged numbered items.

| Item | Evidence inspected and exercised | Result |
| --- | --- | --- |
| 12.1 lifecycle/transports | `HttpMcpServerTest`, `StdioMcpServerTest`, `McpLauncherTest` | Start/stop, sessions, occupied port and stdio launcher pass |
| 12.2 registry/input | `ToolRegistryTest`, `SecurityBoundaryTest` | Only registered tools exist; arguments are validated |
| 12.3 cancellation/results | `CancellationAndTimeoutTest`, `SecurityBoundaryTest` | Cancel/timeout and structured redacted source output pass |
| 12.4 approval/access | `AccessPolicyTest`, `UnapprovedClientTest`, HTTP tests | No approved tools means denial; rate/concurrency permits and bearer-token checks pass |
| 12.5 denied capabilities | `SecurityBoundaryTest`, `GuardedTransportTest` | Browser control/file capabilities absent; private targets and unsafe redirects denied |
| 12.6 status/settings | `McpHttpControllerTest`, `McpSettingsStorageTest`, `McpPageTest`, `McpConnectionGuideTest` | Real start/stop/failure status, persisted choices and connection snippets pass |

## Verification

The combined MCP/security command ran 23 test classes, 77 tests: zero failures,
errors or skipped tests. The exact selected classes and the security evidence are
recorded in the companion STEP 13 report. No live MCP client application was used.

The default listener is loopback. HTTP rejects Origin-bearing browser requests;
loopback requests require a loopback Host. Network mode requires an explicit bearer
token. Approval remains explicit; the stdio snippets include
`SEROX_MCP_APPROVED_TOOLS`. Desktop credentials/preferences do not silently transfer
to separately launched stdio processes. Network HTTP is plaintext and should stay
on a trusted network; this review does not certify internet exposure. The server
caps bodies and sessions but does not implement a request-body read deadline.

The design's recent-call log and per-tool toggle remain outside the current
implementation. The shipped tool is `search_and_summarize`; no additional browser,
file or account access was enabled by the settings work.