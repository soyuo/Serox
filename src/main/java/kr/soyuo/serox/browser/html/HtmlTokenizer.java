package kr.soyuo.serox.browser.html;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tokenizes tags, attributes, text, and comments from an HTML string. */
public final class HtmlTokenizer {
  /** Converts HTML markup into an ordered token stream. */
  public List<HtmlToken> tokenize(String html) {
    List<HtmlToken> tokens = new ArrayList<>();
    int position = 0;
    while (position < html.length()) {
      if (html.startsWith("<!--", position)) {
        int end = html.indexOf("-->", position + 4);
        int contentEnd = end < 0 ? html.length() : end;
        tokens.add(new HtmlToken.Comment(html.substring(position + 4, contentEnd)));
        position = end < 0 ? html.length() : end + 3;
      } else if (html.startsWith("<!", position) || html.startsWith("<?", position)) {
        int end = findTagEnd(html, position + 2);
        position = end < 0 ? html.length() : end + 1;
      } else if (html.charAt(position) == '<') {
        int end = findTagEnd(html, position + 1);
        if (end < 0) {
          tokens.add(new HtmlToken.Text(decodeEntities(html.substring(position))));
          break;
        }
        HtmlToken token = parseTag(html.substring(position + 1, end));
        if (token == null) {
          tokens.add(new HtmlToken.Text("<"));
          position++;
        } else {
          tokens.add(token);
          position = end + 1;
          if (token instanceof HtmlToken.StartTag start
              && !start.selfClosing()
              && (start.name().equals("script") || start.name().equals("style"))) {
            // Script and style hold raw text: a < inside is code, not a tag.
            int close = indexOfIgnoreCase(html, "</" + start.name(), position);
            int rawEnd = close < 0 ? html.length() : close;
            if (rawEnd > position) {
              tokens.add(new HtmlToken.Text(html.substring(position, rawEnd)));
            }
            position = rawEnd;
          }
        }
      } else {
        int nextTag = html.indexOf('<', position);
        int end = nextTag < 0 ? html.length() : nextTag;
        tokens.add(new HtmlToken.Text(decodeEntities(html.substring(position, end))));
        position = end;
      }
    }
    return List.copyOf(tokens);
  }

  private static int indexOfIgnoreCase(String text, String needle, int from) {
    int last = text.length() - needle.length();
    for (int i = from; i <= last; i++) {
      if (text.regionMatches(true, i, needle, 0, needle.length())) {
        return i;
      }
    }
    return -1;
  }

  private int findTagEnd(String html, int position) {
    char quote = 0;
    for (int index = position; index < html.length(); index++) {
      char current = html.charAt(index);
      if (quote != 0) {
        if (current == quote) {
          quote = 0;
        }
      } else if (current == '\'' || current == '"') {
        quote = current;
      } else if (current == '>') {
        return index;
      }
    }
    return -1;
  }

  private HtmlToken parseTag(String source) {
    String content = source.trim();
    if (content.isEmpty() || content.charAt(0) == '!' || content.charAt(0) == '?') {
      return null;
    }
    if (content.charAt(0) == '/') {
      String name = readName(content, 1);
      return name.isEmpty() ? null : new HtmlToken.EndTag(name.toLowerCase(Locale.ROOT));
    }

    String name = readName(content, 0);
    if (name.isEmpty()) {
      return null;
    }
    int cursor = name.length();
    boolean selfClosing = false;
    Map<String, String> attributes = new LinkedHashMap<>();
    while (cursor < content.length()) {
      cursor = skipWhitespace(content, cursor);
      if (cursor >= content.length()) {
        break;
      }
      if (content.charAt(cursor) == '/') {
        selfClosing = true;
        break;
      }
      int attrStart = cursor;
      while (cursor < content.length() && !isAttributeDelimiter(content.charAt(cursor))) {
        cursor++;
      }
      String attrName = content.substring(attrStart, cursor).toLowerCase(Locale.ROOT);
      if (attrName.isEmpty()) {
        cursor++;
        continue;
      }
      cursor = skipWhitespace(content, cursor);
      String value = "";
      if (cursor < content.length() && content.charAt(cursor) == '=') {
        cursor = skipWhitespace(content, cursor + 1);
        if (cursor < content.length()
            && (content.charAt(cursor) == '\'' || content.charAt(cursor) == '"')) {
          char quote = content.charAt(cursor++);
          int valueStart = cursor;
          while (cursor < content.length() && content.charAt(cursor) != quote) {
            cursor++;
          }
          value = content.substring(valueStart, cursor);
          if (cursor < content.length()) {
            cursor++;
          }
        } else {
          int valueStart = cursor;
          while (cursor < content.length() && !Character.isWhitespace(content.charAt(cursor))) {
            cursor++;
          }
          value = content.substring(valueStart, cursor);
        }
      }
      attributes.putIfAbsent(attrName, decodeEntities(value));
    }
    return new HtmlToken.StartTag(name.toLowerCase(Locale.ROOT), attributes, selfClosing);
  }

  private String readName(String source, int start) {
    int end = start;
    while (end < source.length()
        && !Character.isWhitespace(source.charAt(end))
        && source.charAt(end) != '/') {
      end++;
    }
    return source.substring(start, end);
  }

  private int skipWhitespace(String source, int position) {
    while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
      position++;
    }
    return position;
  }

  private boolean isAttributeDelimiter(char value) {
    return Character.isWhitespace(value) || value == '=' || value == '/' || value == '>';
  }

  private String decodeEntities(String text) {
    StringBuilder decoded = new StringBuilder(text.length());
    for (int index = 0; index < text.length(); ) {
      if (text.charAt(index) != '&') {
        decoded.append(text.charAt(index++));
        continue;
      }
      int semicolon = text.indexOf(';', index + 1);
      if (semicolon < 0 || semicolon - index > 12) {
        decoded.append(text.charAt(index++));
        continue;
      }
      String entity = text.substring(index + 1, semicolon);
      String value = entityValue(entity);
      if (value == null) {
        decoded.append(text.charAt(index++));
      } else {
        decoded.append(value);
        index = semicolon + 1;
      }
    }
    return decoded.toString();
  }

  private String entityValue(String entity) {
    return switch (entity) {
      case "amp" -> "&";
      case "lt" -> "<";
      case "gt" -> ">";
      case "quot" -> "\"";
      case "apos" -> "'";
      case "nbsp" -> "\u00a0";
      default -> numericEntity(entity);
    };
  }

  private String numericEntity(String entity) {
    try {
      if (entity.startsWith("#x") || entity.startsWith("#X")) {
        return new String(Character.toChars(Integer.parseInt(entity.substring(2), 16)));
      }
      if (entity.startsWith("#")) {
        return new String(Character.toChars(Integer.parseInt(entity.substring(1))));
      }
    } catch (IllegalArgumentException ignored) {
      return null;
    }
    return null;
  }
}
