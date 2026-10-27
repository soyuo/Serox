# STEP 13 follow-up: security and recovery review

Reviewed 2026-10-27, issue #80, after STEP 11 (`b4fe7ca`). Existing numbered
security requirements were rechecked in one batch with the STEP 12 audit.

| Item | Evidence | Result and boundary |
| --- | --- | --- |
| 13.1 network | `GuardedTransportTest`, `BrowserIsolationAndPolicyTest`; network policy and OAuth clients inspected | Private targets, unsafe redirects and unsupported schemes denied; provider/OAuth clients do not follow redirects |
| 13.2 untrusted input | `UntrustedTextTest`, `UntrustedEvidenceTest` | Evidence remains escaped/redacted and separated from instructions |
| 13.3 prompt/auth/access | `SecurityBoundaryTest`, `UnapprovedClientTest`, `OpenAiIdentityTest`, `ChatGptAccountsTest` | Tools require approval; signed identities bind issuer, audience and nonce; callback/state/PKCE checks exercised |
| 13.4 recovery | `ErrorRecoveryTest`, `SqliteRecoveryTest`, browser isolation tests | Failed requests and damaged storage recover without ending unrelated navigation |
| 13.5 cancel/resources | `PageFetcherTest`, `CancellationAndTimeoutTest`; auth lifecycle inspected | Selected evidence limits reach raw transport futures; cancellation/deadline paths pass; auth attempts close their loopback listener |
| 13.6 secrets/logs | `ProtectedLocalStoreTest`, `LogHygieneTest`; protected-store/auth code inspected | Windows DPAPI round-trip/corruption tests pass; no secrets or remote bodies in log calls; helper receives secret bytes through stdin |

## Reproduction and outcome

Run from the repository in PowerShell:

```powershell
mvn -q -o '-Dtest=HttpMcpServerTest,StdioMcpServerTest,AccessPolicyTest,SecurityBoundaryTest,UnapprovedClientTest,ToolRegistryTest,CancellationAndTimeoutTest,McpHttpControllerTest,McpLauncherTest,McpSettingsStorageTest,McpConnectionGuideTest,McpPageTest,OpenAiIdentityTest,ChatGptAccountsTest,ProtectedLocalStoreTest,GuardedTransportTest,UntrustedEvidenceTest,LogHygieneTest,BrowserIsolationAndPolicyTest,ErrorRecoveryTest,UntrustedTextTest,SqliteRecoveryTest,PageFetcherTest' test
```

77 tests, zero failures, errors or skips. Test assertions deliberately provoke
sanitized recovery warnings. JavaFX/SQLite emit native-access and unnamed-module
warnings in the test harness. These warnings did not fail the tests.

## Limits retained

- Live ChatGPT authorization, model access, token rotation/revocation and paid
  provider inference were not exercised. Signed-token and loopback tests use local
  fixtures; they do not establish a successful real-account login.
- The public-address check and JDK connection resolve DNS separately. DNS rebinding
  is not fully excluded; see [network-policy.md](../security/network-policy.md).
- MCP network mode uses plaintext HTTP with bearer authentication, and there is no
  request-body read deadline. Keep the default loopback/trusted-network scope;
  this audit is not a hostile-network or denial-of-service certification.
- DPAPI protects at rest against other Windows users, not malware running as the
  same user. A process lock prevents concurrent desktop token-store writers.
- Cancellation is tested for bounded tasks. No long-duration memory soak or clean
  elevated MSI installation was run in this follow-up.

Integration tests, MSI creation and installation are intentionally left for the
user to execute, per the latest scope instruction. Existing earlier results are
not relabeled as verification of a newly built installer.