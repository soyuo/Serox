package kr.soyuo.serox.browser.html;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Token produced by the initial HTML tokenizer. */
public sealed interface HtmlToken
    permits HtmlToken.StartTag, HtmlToken.EndTag, HtmlToken.Text, HtmlToken.Comment {
  /** Start tag with parsed attributes. */
  record StartTag(String name, Map<String, String> attributes, boolean selfClosing)
      implements HtmlToken {
    /** Normalizes the token's attributes to an immutable map. */
    public StartTag {
      Objects.requireNonNull(name, "name");
      attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
  }

  /** Closing tag token. */
  record EndTag(String name) implements HtmlToken {
    /** Validates the closing tag name. */
    public EndTag {
      Objects.requireNonNull(name, "name");
    }
  }

  /** Character data token. */
  record Text(String value) implements HtmlToken {
    /** Validates the text value. */
    public Text {
      Objects.requireNonNull(value, "value");
    }
  }

  /** Comment data token. */
  record Comment(String value) implements HtmlToken {
    /** Validates the comment value. */
    public Comment {
      Objects.requireNonNull(value, "value");
    }
  }
}
