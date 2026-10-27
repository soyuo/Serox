package kr.soyuo.serox.browser.layout;

/** Viewport dimensions and document scroll offsets for one layout pass. */
public record LayoutConstraints(
    double viewportWidth, double viewportHeight, double scrollX, double scrollY) {
  /** Validates non-negative viewport dimensions and scroll offsets. */
  public LayoutConstraints {
    if (viewportWidth < 0 || viewportHeight < 0 || scrollX < 0 || scrollY < 0) {
      throw new IllegalArgumentException(
          "viewport dimensions and scroll offsets must be non-negative");
    }
  }
}
