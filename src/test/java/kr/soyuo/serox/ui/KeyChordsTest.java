package kr.soyuo.serox.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import kr.soyuo.serox.core.model.KeyChord;
import kr.soyuo.serox.core.model.ShortcutAction;
import org.junit.jupiter.api.Test;

class KeyChordsTest {
  private static KeyEvent press(KeyCode code, boolean shift, boolean ctrl, boolean alt) {
    return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, ctrl, alt, false);
  }

  @Test
  void eachDefaultChordMatchesItsOwnKeyPressAndNoOther() {
    for (ShortcutAction action : ShortcutAction.values()) {
      var chord = action.defaultChord();
      var combination = KeyChords.toCombination(chord);
      var event = press(KeyCode.valueOf(keyCodeName(chord)), chord.shift(), chord.ctrl(), chord.alt());

      assertTrue(combination.match(event), action.name());
      assertEquals(chord, KeyChords.fromEvent(event).orElseThrow(), action.name());
    }
  }

  @Test
  void anAcceleratorDoesNotFireWithExtraModifiers() {
    var combination = KeyChords.toCombination(KeyChord.parse("Ctrl+L").orElseThrow());

    assertTrue(combination.match(press(KeyCode.L, false, true, false)));
    assertFalse(combination.match(press(KeyCode.L, true, true, false)));
    assertFalse(combination.match(press(KeyCode.L, false, true, true)));
    assertFalse(combination.match(press(KeyCode.L, false, false, false)));
  }

  @Test
  void modifiersAndUnusableKeysMakeNoChord() {
    assertTrue(KeyChords.isModifier(KeyCode.SHIFT));
    assertTrue(KeyChords.fromEvent(press(KeyCode.CONTROL, false, true, false)).isEmpty());
    assertTrue(KeyChords.fromEvent(press(KeyCode.TAB, false, true, false)).isEmpty());
    assertTrue(KeyChords.fromEvent(press(KeyCode.L, false, false, false)).isEmpty());
    assertTrue(KeyChords.fromEvent(press(KeyCode.C, false, true, false)).isEmpty());
  }

  @Test
  void arrowsAreDrawnAsArrows() {
    assertEquals("Alt+↓", KeyChords.describe(KeyChord.parse("Alt+Down").orElseThrow()));
    assertEquals("↑", KeyChords.cap("Up"));
    assertEquals("Ctrl+,", KeyChords.describe(KeyChord.parse("Ctrl+,").orElseThrow()));
  }

  private static String keyCodeName(KeyChord chord) {
    return switch (chord.key()) {
      case "," -> "COMMA";
      case "Up", "Down" -> chord.key().toUpperCase(java.util.Locale.ROOT);
      default -> chord.key();
    };
  }
}
