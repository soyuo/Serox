package kr.soyuo.serox.core.model;

/** User-visible state of a tab's current navigation. */
public enum NavigationStatus {
  IDLE,
  LOADING,
  SUCCEEDED,
  FAILED,
  CANCELLED
}
