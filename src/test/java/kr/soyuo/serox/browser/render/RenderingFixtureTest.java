package kr.soyuo.serox.browser.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import kr.soyuo.serox.browser.css.CssStyleEngine;
import kr.soyuo.serox.browser.dom.Document;
import kr.soyuo.serox.browser.html.HtmlDocumentParser;
import kr.soyuo.serox.browser.layout.LayoutConstraints;
import kr.soyuo.serox.browser.layout.LayoutEngine;
import org.junit.jupiter.api.Test;

class RenderingFixtureTest {
  private static final URI FIXTURE_URI = URI.create("https://fixture.test/basic");

  @Test
  void fixedVisualFixtureProducesTheDocumentedDisplayList() throws IOException {
    Document document = parseFixture();
    var styles = new CssStyleEngine().compute(document);
    var layout = new LayoutEngine().layout(document, styles, new LayoutConstraints(240, 160, 0, 0));
    DisplayList displayList = new DisplayListBuilder().build(layout);

    assertTrue(styles.unsupportedDeclarations().contains("display: flex"));
    assertEquals(
        "block",
        styles.styleOf(document.getElementById("unsupported").orElseThrow()).value("display"));
    String actual = snapshot(displayList, styles.unsupportedDeclarations());
    assertEquals(readFixture("basic.expected"), actual);

    Document repeatedDocument = parseFixture();
    var repeatedStyles = new CssStyleEngine().compute(repeatedDocument);
    var repeatedLayout =
        new LayoutEngine()
            .layout(repeatedDocument, repeatedStyles, new LayoutConstraints(240, 160, 0, 0));
    assertEquals(
        actual,
        snapshot(
            new DisplayListBuilder().build(repeatedLayout),
            repeatedStyles.unsupportedDeclarations()));
  }

  @Test
  void layoutReflowsAtNewViewportSizeAndCarriesScrollOffsets() throws IOException {
    Document document = parseFixture();
    var styles = new CssStyleEngine().compute(document);
    var layout = new LayoutEngine().layout(document, styles, new LayoutConstraints(120, 80, 0, 12));
    DisplayList displayList = new DisplayListBuilder().build(layout);

    assertTrue(displayList.documentHeight() > displayList.viewportHeight());
    assertEquals(12, displayList.scrollY());
    assertTrue(displayList.commands().size() > 5);
  }

  private Document parseFixture() throws IOException {
    String html = readFixture("basic.html");
    return new HtmlDocumentParser().parse(FIXTURE_URI, html);
  }

  private String readFixture(String name) throws IOException {
    try (var input = getClass().getResourceAsStream("/fixtures/renderer/" + name)) {
      if (input == null) {
        throw new IOException("missing renderer fixture: " + name);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
  }

  private String snapshot(DisplayList displayList, java.util.List<String> unsupported) {
    StringBuilder output = new StringBuilder();
    output
        .append("viewport ")
        .append(format(displayList.viewportWidth()))
        .append('x')
        .append(format(displayList.viewportHeight()))
        .append(" document ")
        .append(format(displayList.documentWidth()))
        .append('x')
        .append(format(displayList.documentHeight()))
        .append('\n');
    for (PaintCommand command : displayList.commands()) {
      if (command instanceof PaintCommand.FillRect rect) {
        output
            .append("fill ")
            .append(format(rect.x()))
            .append(',')
            .append(format(rect.y()))
            .append(' ')
            .append(format(rect.width()))
            .append('x')
            .append(format(rect.height()))
            .append(' ')
            .append(hex(rect.color()))
            .append('\n');
      } else if (command instanceof PaintCommand.StrokeRect rect) {
        output
            .append("stroke ")
            .append(format(rect.x()))
            .append(',')
            .append(format(rect.y()))
            .append(' ')
            .append(format(rect.width()))
            .append('x')
            .append(format(rect.height()))
            .append(' ')
            .append(hex(rect.color()))
            .append(' ')
            .append(format(rect.lineWidth()))
            .append('\n');
      } else if (command instanceof PaintCommand.ImagePlaceholder image) {
        output
            .append("image ")
            .append(format(image.x()))
            .append(',')
            .append(format(image.y()))
            .append(' ')
            .append(format(image.width()))
            .append('x')
            .append(format(image.height()))
            .append(' ')
            .append(image.altText())
            .append('\n');
      } else if (command instanceof PaintCommand.FormControl control) {
        output
            .append("control ")
            .append(control.controlType())
            .append(' ')
            .append(format(control.x()))
            .append(',')
            .append(format(control.y()))
            .append(' ')
            .append(format(control.width()))
            .append('x')
            .append(format(control.height()))
            .append('\n');
      } else if (command instanceof PaintCommand.Text text) {
        output
            .append("text ")
            .append(format(text.x()))
            .append(',')
            .append(format(text.baselineY()))
            .append(' ')
            .append(text.value())
            .append(" link=")
            .append(text.link().map(URI::toString).orElse("-"))
            .append('\n');
      }
    }
    output.append("unsupported ").append(String.join("|", unsupported)).append('\n');
    return output.toString();
  }

  private String format(double number) {
    return String.format(Locale.ROOT, "%.1f", number);
  }

  private String hex(kr.soyuo.serox.browser.css.CssColor color) {
    return String.format(Locale.ROOT, "#%02x%02x%02x", color.red(), color.green(), color.blue());
  }
}
