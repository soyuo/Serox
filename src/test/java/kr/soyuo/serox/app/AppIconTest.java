package kr.soyuo.serox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AppIconTest {
  @Test
  void windowIconIsBundledAsASquarePng() throws Exception {
    var resource = SeroxApplication.class.getResourceAsStream("/kr/soyuo/serox/ui/assets/serox-icon.png");
    assertNotNull(resource);
    var image = ImageIO.read(resource);
    assertEquals(image.getWidth(), image.getHeight());
  }

  @Test
  void installerIconIsAnIcoFile() throws Exception {
    byte[] header = Files.readAllBytes(Path.of("scripts/icon/serox.ico"));
    assertEquals(0, header[0]);
    assertEquals(1, header[2]);
  }
}
