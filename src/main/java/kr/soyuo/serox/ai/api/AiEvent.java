package kr.soyuo.serox.ai.api;

/**
 * One event in a provider's streamed response.
 *
 * <p>A stream emits text deltas followed by exactly one terminal completion or failure event.
 */
public sealed interface AiEvent permits AiTextDelta, AiCompleted, AiFailed {}
