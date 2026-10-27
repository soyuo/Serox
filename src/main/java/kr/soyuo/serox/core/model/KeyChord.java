package kr.soyuo.serox.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A key with modifiers, such as Ctrl+Shift+A. {@link #UNASSIGNED} stands for no key at all.
 *
 * <p>A chord must use Ctrl or Alt (or be a function key) so it cannot swallow ordinary typing, and
 * it may not take over the editing shortcuts or closing the window.
 */
public record KeyChord(boolean ctrl, boolean shift, boolean alt, String key) {
  /** No key assigned. */
  public static final KeyChord UNASSIGNED = new KeyChord(false, false, false, "");

  private static final Pattern KEY =
      Pattern.compile("[A-Z0-9]|,|\\.|Up|Down|Left|Right|F([1-9]|1[0-2])");
  private static final Pattern FUNCTION_KEY = Pattern.compile("F([1-9]|1[0-2])");
  private static final Set<String> EDITING_KEYS = Set.of("C", "V", "X", "A", "Z", "Y");

  /** Validates the key and the modifiers. */
  public KeyChord {
    Objects.requireNonNull(key, "key");
    if (key.isEmpty()) {
      if (ctrl || shift || alt) {
        throw new IllegalArgumentException("modifiers need a key");
      }
    } else {
      if (!KEY.matcher(key).matches()) {
        throw new IllegalArgumentException("unsupported key: " + key);
      }
      if (!ctrl && !alt && !FUNCTION_KEY.matcher(key).matches()) {
        throw new IllegalArgumentException("a shortcut needs Ctrl or Alt");
      }
      if (ctrl && !alt && !shift && EDITING_KEYS.contains(key)) {
        throw new IllegalArgumentException("Ctrl+" + key + " is used for editing text");
      }
      if (alt && !ctrl && !shift && key.equals("F4")) {
        throw new IllegalArgumentException("Alt+F4 closes the window");
      }
    }
  }

  /** Whether a key is assigned. */
  public boolean assigned() {
    return !key.isEmpty();
  }

  /** The parts shown as separate keys: modifiers in the order Ctrl, Alt, Shift, then the key. */
  public List<String> parts() {
    List<String> parts = new ArrayList<>();
    if (ctrl) {
      parts.add("Ctrl");
    }
    if (alt) {
      parts.add("Alt");
    }
    if (shift) {
      parts.add("Shift");
    }
    if (assigned()) {
      parts.add(key);
    }
    return parts;
  }

  /** Reads text such as {@code Ctrl+Shift+A}; blank is {@link #UNASSIGNED}. */
  public static Optional<KeyChord> parse(String text) {
    Objects.requireNonNull(text, "text");
    String trimmed = text.strip();
    if (trimmed.isEmpty()) {
      return Optional.of(UNASSIGNED);
    }
    boolean ctrl = false;
    boolean shift = false;
    boolean alt = false;
    String key = null;
    // A trailing "+" would be an empty token, so a comma or period key is written as is.
    for (String token : trimmed.split("\\+", -1)) {
      switch (token.strip().toLowerCase(Locale.ROOT)) {
        case "ctrl" -> ctrl = true;
        case "shift" -> shift = true;
        case "alt" -> alt = true;
        default -> {
          if (key != null) {
            return Optional.empty();
          }
          String name = token.strip();
          key = name.length() == 1 ? name.toUpperCase(Locale.ROOT) : capitalized(name);
        }
      }
    }
    if (key == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(new KeyChord(ctrl, shift, alt, key));
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
  }

  private static String capitalized(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }

  /** The text {@link #parse} reads back, such as {@code Ctrl+Shift+A}. */
  @Override
  public String toString() {
    return String.join("+", parts());
  }
}
