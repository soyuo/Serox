# ADR 0005: selectable AI summary providers

Decision date: 2026-10-27. Scope: STEP 10.3 and 10.7, followed by the STEP 11 settings UI.

Serox keeps the existing `AiProvider` contract and citation validation. A request goes
only to the selected provider. Missing credentials never cause a silent switch to a
different provider.

| `SEROX_AI_PROVIDER` | Credential environment variable | Default model | Protocol |
| --- | --- | --- | --- |
| `claude` (default) | `SEROX_ANTHROPIC_API_KEY` | `claude-sonnet-5-5` | Anthropic Messages |
| `gemini` | `SEROX_GEMINI_API_KEY` | `gemini-3.8-flash` | Gemini streamGenerateContent |
| `codex` | `SEROX_OPENAI_API_KEY` | `gpt-6-luna` | OpenAI Responses |
| `grok` | `SEROX_XAI_API_KEY` | `grok-4.7` | xAI Responses |

`SEROX_AI_MODEL` overrides the selected provider's model. The Codex name follows the
Figma provider card; this integration calls the OpenAI API for text summaries. It does
not invoke a Codex agent or gain access to a ChatGPT subscription.

The new adapters send the same bounded evidence and system instructions as Claude.
Responses requests set `store: false`. Streams have a 60-second total deadline,
64 KiB frame and answer limits, safe failure messages, and cancellable response bodies.
Gemini thought parts are excluded from visible answers. Truncated, blocked or failed
answers are failures; they do not remove normal search results.

Credentials remain process secrets. They are sent in headers, never URLs or persisted
preferences. Browser sign-in requires a provider-supported client registration and
authorization flow; a website login alone must not be reported as an API connection.

## Sources and verification

- [OpenAI streaming](https://developers.openai.com/api/docs/guides/streaming-responses)
  defines Responses text deltas and completion events.
- [OpenAI model reference](https://developers.openai.com/api/docs/models/gpt-6-luna)
  documents the default OpenAI model.
- [Gemini content API](https://ai.google.dev/api/generate-content) defines contents,
  system instructions, streamed candidates and finish reasons.
- [Gemini models](https://ai.google.dev/gemini-api/docs/models) lists the Flash model.
- [xAI Responses](https://docs.x.ai/developers/rest-api-reference/inference/responses)
  documents the shared protocol; [text best practices](https://docs.x.ai/developers/model-capabilities/text/best-practices)
  documents the model used here.

`StreamingAiProviderTest` uses a local HTTP server to verify both wire formats,
credential headers, storage opt-out, blocked/truncated/malformed responses, limits and
cancellation. Configuration and AI-search tests cover redaction, explicit selection,
missing credentials and preservation of search results. Live provider requests still
require user credentials and were not performed.
