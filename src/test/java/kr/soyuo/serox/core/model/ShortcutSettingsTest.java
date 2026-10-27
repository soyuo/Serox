package kr.soyuo.serox.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShortcutSettingsTest {
  private static KeyChord chord(String text) {
    return KeyChord.parse(text).orElseThrow();
  }

  @Test
  void chordsRoundTripThroughText() {
    for (String text : List.of("Ctrl+L", "Ctrl+Shift+A", "Alt+Down", "Ctrl+,", "F5", "Ctrl+Alt+7")) {
      assertEquals(text, chord(text).toString());
    }
    assertEquals(chord("Ctrl+Shift+A"), chord("shift+ctrl+a"));
    assertEquals(KeyChord.UNASSIGNED, chord(""));
    assertEquals("", KeyChord.UNASSIGNED.toString());
  }

  @Test
  void unsafeOrUnknownChordsAreRefused() {
    for (String text :
        List.of(
            "L", "Shift+L", "Ctrl", "Ctrl+Shift", "Ctrl+C", "Ctrl+V", "Ctrl+A", "Alt+F4",
            "Ctrl+Tab", "Ctrl+L+K", "Ctrl+F13")) {
      assertEquals(Optional.empty(), KeyChord.parse(text), text);
    }
    assertTrue(KeyChord.parse("Ctrl+Shift+C").isPresent());
    assertTrue(KeyChord.parse("F11").isPresent());
  }

  @Test
  void everyActionStartsAtItsDefaultAndDefaultsDoNotConflict() {
    var settings = BrowserSettings.defaults();

    for (ShortcutAction action : ShortcutAction.values()) {
      assertEquals(action.defaultChord(), settings.shortcut(action));
      assertEquals(Optional.empty(), settings.actionUsing(action.defaultChord(), action));
    }
    assertEquals("Ctrl+L", settings.shortcut(ShortcutAction.FOCUS_SEARCH).toString());
    assertTrue(settings.shortcutOverrides().isEmpty());
  }

  @Test
  void changingAShortcutKeepsOnlyRealDifferences() {
    var changed = BrowserSettings.defaults().withShortcut(ShortcutAction.NEW_TAB, chord("Ctrl+N"));

    assertEquals(chord("Ctrl+N"), changed.shortcut(ShortcutAction.NEW_TAB));
    assertEquals(1, changed.shortcutOverrides().size());
    assertTrue(
        changed.withShortcut(ShortcutAction.NEW_TAB, chord("Ctrl+T")).shortcutOverrides().isEmpty());
  }

  @Test
  void aConflictIsFoundAndTakingTheKeyUnassignsTheOtherAction() {
    var settings = BrowserSettings.defaults();
    KeyChord next = chord("Alt+Down");

    assertEquals(
        Optional.of(ShortcutAction.NEXT_SOURCE),
        settings.actionUsing(next, ShortcutAction.PREVIOUS_SOURCE));

    var taken = settings.withShortcutTaken(ShortcutAction.PREVIOUS_SOURCE, next);

    assertEquals(next, taken.shortcut(ShortcutAction.PREVIOUS_SOURCE));
    assertFalse(taken.shortcut(ShortcutAction.NEXT_SOURCE).assigned());
    assertEquals(Optional.empty(), taken.actionUsing(chord("Ctrl+Q"), ShortcutAction.NEW_TAB));
  }

  @Test
  void anUnassignedKeyNeverConflicts() {
    var settings =
        BrowserSettings.defaults()
            .withShortcut(ShortcutAction.NEW_TAB, KeyChord.UNASSIGNED)
            .withShortcut(ShortcutAction.CLOSE_TAB, KeyChord.UNASSIGNED);

    assertEquals(
        Optional.empty(), settings.actionUsing(KeyChord.UNASSIGNED, ShortcutAction.OPEN_HISTORY));
  }

  @Test
  void otherChangesKeepTheShortcutsAndResetRestoresTheDefaults() {
    var custom = BrowserSettings.defaults().withShortcut(ShortcutAction.NEW_TAB, chord("Ctrl+N"));

    assertEquals(
        chord("Ctrl+N"), custom.withRetention(HistoryRetention.WEEK).shortcut(ShortcutAction.NEW_TAB));
    assertEquals(
        chord("Ctrl+N"),
        BrowserSettings.defaults().withShortcutsOf(custom).shortcut(ShortcutAction.NEW_TAB));
    assertTrue(custom.withDefaultShortcuts().shortcutOverrides().isEmpty());
  }
}
