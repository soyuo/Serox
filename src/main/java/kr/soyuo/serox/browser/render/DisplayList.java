package kr.soyuo.serox.browser.render;

import java.util.List;

/** Immutable paint commands and viewport offsets for a page. */
public record DisplayList(
    List<PaintCommand> commands,
    double viewportWidth,
    double viewportHeight,
    double documentHeight,
    double scrollX,
    double scrollY) {
  public DisplayList {
    commands = List.copyOf(commands);
  }
}
