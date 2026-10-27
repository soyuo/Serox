package kr.soyuo.serox.browser.css;

import java.util.Map;

/** One parsed selector and declaration block with its source order. */
public record CssRule(String selector, Map<String, String> declarations, int sourceOrder) {}
