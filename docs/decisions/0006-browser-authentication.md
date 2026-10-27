# ADR 0006: browser authorization and protected provider settings

Decision date: 2026-10-27. Scope: STEP 11.3, requirement S-AI-CONNECTION.

The provider settings page must change the actual summary provider, preserve normal
search on failure, and restore the user's selection after restart. Keys entered in
the UI are checked against provider model metadata before saving. Environment keys
remain supported and are not copied into the local credential file.

## ChatGPT browser flow

Serox uses the official open-source-app registration flow under its own name. It does
not borrow a Codex client ID, read CLI credentials, invoke an agent, or scrape a web
session. The system browser returns to an ephemeral `127.0.0.1` HTTP listener at
`/auth/callback`. Every attempt uses fresh state, nonce and S256 PKCE. Returning
accounts retain the issued client ID and installation host ID. A new registration is
retained even when its one-use authorization code expires before exchange.

The app verifies RS256 signature, issuer, audience, expiration, nonce, subject and
authorized party before accepting an identity. Returning registrations must match
the saved subject. Duplicate callback parameters, unexpected hosts, changed client
IDs and state mismatches are refused. Cancellation and a three-minute timeout stop
the callback listener. Browser completion alone is not an AI connection: the granted
token scopes must include `chatgpt.tokens.use.direct` and the model catalog must load.

Inference uses the public Responses endpoint with `store: false` and `stream: true`.
The model picker uses the signed-in account's catalog. Tokens refresh near expiry;
rotating refresh tokens are saved together. A profile directory lock prevents two
Serox instances from racing refresh or overwriting the same registrations. Signing
out attempts revocation, then clears local tokens while retaining the registration.
If remote revocation cannot be confirmed, the UI tells the user to disconnect Serox
in ChatGPT settings.

## Local storage and other providers

On Windows, DPAPI CurrentUser encrypts provider keys and OAuth token records before
an atomic file replacement under the Serox profile's `credentials` directory. The
fixed PowerShell helper receives bytes through a pipe, never arguments or plaintext
temporary files. Helper failures are bounded and produce safe messages. Other
operating systems keep environment-key support; persistent UI credentials require
a supported OS secret store. A locked or unreadable store does not stop browsing.

The Figma Codex card retains its name; its browser action is explicitly labeled
`Continue with ChatGPT`. The page explains plan usage, offers a first-use notice and
links to usage management. The extra account/model controls reflect actual OAuth
registrations rather than sample connection states.

Claude, Gemini and Grok API-key adapters remain usable. Claude.ai subscription login
is not offered as a third-party-app authentication method. Google OAuth requires
Serox's registered client configuration, which is not present. A Grok website login
does not grant xAI API access. The UI explains these provider-specific differences
instead of marking a website visit as connected. Arbitrary compatible endpoints are
disabled because provider-specific validation and credential boundaries are not yet
implemented.

## Verification and sources

`OpenAiIdentityTest` signs local test JWTs and rejects tampering, missing keys, wrong
claims and unsigned tokens. `ChatGptAccountsTest` exercises the real loopback
listener, state/duplicate validation, cancellation, denial, fresh PKCE parameters and
stable host identity without contacting an identity provider. `ProtectedLocalStoreTest`
performs a real Windows DPAPI round trip and rejects corrupted ciphertext. Existing
AI search and runtime-status tests exercise the changed composition and fallback.
Live account authorization, token refresh and provider inference still need a user
account and were not executed by the implementation tests.

- [OpenAI registration and sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
- [OpenAI accounts and sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)
- [OpenAI models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
- [OpenAI UI guidance](https://developers.openai.com/siwc/ui-ux-guidelines)
- [Claude authentication and credential use](https://code.claude.com/docs/en/legal-and-compliance#authentication-and-credential-use)
- [Gemini API authentication](https://ai.google.dev/gemini-api/docs/api-key)
- [xAI API authentication](https://docs.x.ai/developers/rest-api-reference)
