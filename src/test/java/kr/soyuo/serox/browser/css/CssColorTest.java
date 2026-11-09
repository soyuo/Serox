package kr.soyuo.serox.browser.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CssColorTest {
  private static CssColor color(String text) {
    return CssColor.parse(text).orElseThrow(() -> new AssertionError("not parsed: " + text));
  }

  @Test
  void parsesFunctionalAndHexForms() {
    assertEquals(new CssColor(255, 0, 0, 1), color("rgb(255, 0, 0)"));
    assertEquals(new CssColor(0, 128, 255, 0.5), color("rgba(0,128,255,.5)"));
    assertEquals(new CssColor(10, 20, 30, 0.25), color("rgb(10 20 30 / 25%)"));
    assertEquals(new CssColor(255, 0, 0, 1), color("hsl(0, 100%, 50%)"));
    assertEquals(new CssColor(0, 255, 0, 1), color("hsl(120deg 100% 50%)"));
    assertEquals(new CssColor(17, 34, 51, 68 / 255.0), color("#1234"));
    assertEquals(new CssColor(18, 52, 86, 120 / 255.0), color("#12345678"));
  }

  @Test
  void knowsTheNamedColors() {
    assertEquals(new CssColor(102, 51, 153, 1), color("RebeccaPurple"));
    assertEquals(new CssColor(245, 245, 245, 1), color("whitesmoke"));
    assertTrue(CssColor.parse("notacolor").isEmpty());
    assertTrue(CssColor.parse("rgb(1,2)").isEmpty());
  }
}
