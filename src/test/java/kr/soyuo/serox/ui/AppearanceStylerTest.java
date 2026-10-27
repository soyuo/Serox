package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.soyuo.serox.core.model.AiPanelPosition;
import kr.soyuo.serox.core.model.BrowserSettings;
import kr.soyuo.serox.core.model.DisplayDensity;
import kr.soyuo.serox.core.model.ThemeMode;
import org.junit.jupiter.api.Test;

class AppearanceStylerTest {
  @Test
  void styleCarriesAccentAndScaledFontSize() {
    var settings =
        new BrowserSettings(
            ThemeMode.DARK, "#7C3AED", 150, AiPanelPosition.LEFT, DisplayDensity.COMPACT);

    String style = AppearanceStyler.inlineStyle(settings);

    assertTrue(style.contains("-fx-font-size: 19.50px"));
    assertTrue(style.contains("serox-primary: #7c3aed"));
    assertTrue(AppearanceStyler.dark(settings));
    assertTrue(AppearanceStyler.compact(settings));
  }

  @Test
  void defaultsUseTheLightComfortablePalette() {
    var defaults = BrowserSettings.defaults();

    assertFalse(AppearanceStyler.dark(defaults));
    assertFalse(AppearanceStyler.compact(defaults));
    assertTrue(AppearanceStyler.inlineStyle(defaults).contains("-fx-font-size: 13.00px"));
  }
}
