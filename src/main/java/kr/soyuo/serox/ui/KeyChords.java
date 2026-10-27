package kr.soyuo.serox.ui;

import java.util.Optional;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import kr.soyuo.serox.core.model.KeyChord;

/** Converts between key events, accelerators and the chords stored in the settings. */
final class KeyChords {
  private KeyChords() {}

  /** The chord a key press makes; empty for a bare modifier or a key a shortcut cannot use. */
  static Optional<KeyChord> fromEvent(KeyEvent event) {
    String key = keyName(event.getCode());
    if (key.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new KeyChord(event.isControlDown(), event.isShiftDown(), event.isAltDown(), key));
    } catch (IllegalArgumentException unusable) {
      return Optional.empty();
    }
  }

  /** Whether the press is only a modifier key, which should not end the wait for a shortcut. */
  static boolean isModifier(KeyCode code) {
    return code == KeyCode.CONTROL
        || code == KeyCode.SHIFT
        || code == KeyCode.ALT
        || code == KeyCode.ALT_GRAPH
        || code == KeyCode.META
        || code == KeyCode.WINDOWS;
  }

  /** The accelerator for an assigned chord. */
  static KeyCodeCombination toCombination(KeyChord chord) {
    return new KeyCodeCombination(
        keyCode(chord.key()),
        modifier(chord.shift()),
        modifier(chord.ctrl()),
        modifier(chord.alt()),
        KeyCombination.ModifierValue.UP,
        KeyCombination.ModifierValue.UP);
  }

  private static KeyCombination.ModifierValue modifier(boolean down) {
    return down ? KeyCombination.ModifierValue.DOWN : KeyCombination.ModifierValue.UP;
  }

  /** How one part of a chord is written on a key cap. */
  static String cap(String part) {
    return switch (part) {
      case "Up" -> "↑";
      case "Down" -> "↓";
      case "Left" -> "←";
      case "Right" -> "→";
      default -> part;
    };
  }

  /** A chord written for a sentence, such as {@code Alt+↓}. */
  static String describe(KeyChord chord) {
    return String.join("+", chord.parts().stream().map(KeyChords::cap).toList());
  }

  private static String keyName(KeyCode code) {
    if (code.isLetterKey() || code.isDigitKey() || code.isFunctionKey()) {
      return code.getName().toUpperCase(java.util.Locale.ROOT).replace("DIGIT", "");
    }
    return switch (code) {
      case COMMA -> ",";
      case PERIOD -> ".";
      case UP -> "Up";
      case DOWN -> "Down";
      case LEFT -> "Left";
      case RIGHT -> "Right";
      default -> "";
    };
  }

  private static KeyCode keyCode(String key) {
    return switch (key) {
      case "," -> KeyCode.COMMA;
      case "." -> KeyCode.PERIOD;
      case "Up", "Down", "Left", "Right" -> KeyCode.valueOf(key.toUpperCase(java.util.Locale.ROOT));
      default ->
          key.length() == 1 && Character.isDigit(key.charAt(0))
              ? KeyCode.valueOf("DIGIT" + key)
              : KeyCode.valueOf(key);
    };
  }
}
