package kr.soyuo.serox.core.error;

/** Stable category for a user-visible application failure. */
public enum ErrorCode {
  INVALID_ADDRESS,
  UNSUPPORTED_SCHEME,
  TLS_CERTIFICATE_INVALID,
  NETWORK_FAILURE,
  UNSUPPORTED_CONTENT,
  SEARCH_NOT_CONFIGURED,
  SEARCH_FAILURE,
  AI_NOT_CONFIGURED,
  AI_FAILURE,
  MCP_ACCESS_DENIED,
  STORAGE_FAILURE,
  UNKNOWN
}
