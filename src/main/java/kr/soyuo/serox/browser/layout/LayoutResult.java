package kr.soyuo.serox.browser.layout;

import java.util.List;

/** Immutable result from one deterministic document layout pass. */
public record LayoutResult(
    LayoutConstraints constraints,
    List<LayoutBox> boxes,
    List<TextRun> textRuns,
    double documentHeight) {
  public LayoutResult {
    boxes = List.copyOf(boxes);
    textRuns = List.copyOf(textRuns);
  }
}
