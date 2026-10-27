package kr.soyuo.serox.core.event;

/** Connection/readiness state reported by a service owner. */
public enum ServiceStatus {
  DISCONNECTED,
  CONFIGURING,
  CONNECTED,
  FAILED
}
