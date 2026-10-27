package kr.soyuo.serox.browser.layout;

import java.net.URI;
import java.util.Optional;
import kr.soyuo.serox.browser.css.ComputedStyle;

/** Positioned line of inline text with style and optional link destination. */
public record TextRun(
    double x,
    double baselineY,
    String text,
    ComputedStyle style,
    Optional<URI> link,
    int paintOrder) {}
