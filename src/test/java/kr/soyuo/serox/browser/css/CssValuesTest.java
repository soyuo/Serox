package kr.soyuo.serox.browser.css;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CssValuesTest {
  @Test
  void evaluatesMathOverAbsoluteLengths() {
    assertEquals("18px", CssValues.resolveFunctions("calc(14px + 4px)"));
    assertEquals("125px", CssValues.resolveFunctions("calc(250px * 0.5)"));
    assertEquals("-16px", CssValues.resolveFunctions("calc(16px*-1)"));
    assertEquals("26px", CssValues.resolveFunctions("calc((12px + 40px) - (18px + 8px))"));
    assertEquals("90px", CssValues.resolveFunctions("calc(5.625rem)"));
    assertEquals("130px", CssValues.resolveFunctions("round(250px * 0.5, 10px)"));
    assertEquals("10px", CssValues.resolveFunctions("min(10px, 2rem)"));
    assertEquals("20px", CssValues.resolveFunctions("clamp(20px, 1vw, 40px)"));
    assertEquals("0 18px", CssValues.resolveFunctions("0 calc(10px + 8px)"));
  }

  @Test
  void leavesRelativeMathAndPicksTheLightColor() {
    assertEquals("calc(100% - 2px)", CssValues.resolveFunctions("calc(100% - 2px)"));
    assertEquals("calc(1em + 2px)", CssValues.resolveFunctions("calc(1em + 2px)"));
    assertEquals("#000", CssValues.resolveFunctions("light-dark(#000,#fff)"));
  }
}
