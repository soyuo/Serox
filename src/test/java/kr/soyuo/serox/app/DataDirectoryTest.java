package kr.soyuo.serox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataDirectoryTest {
  @Test
  void expandsUserProfileDataFromUserProfile() {
    var environment =
        Map.of("SEROX_DATA_DIR", "%USERPROFILEDATA%\\Serox", "USERPROFILE", "C:\\Users\\kim");

    assertEquals(
        Path.of("C:\\Users\\kim\\Serox"), AppBootstrap.resolveDataDirectory(environment));
  }

  @Test
  void expandsNamedVariableIgnoringCase() {
    var environment = Map.of("SEROX_DATA_DIR", "%localappdata%\\Mine", "LOCALAPPDATA", "D:\\L");

    assertEquals(Path.of("D:\\L\\Mine"), AppBootstrap.resolveDataDirectory(environment));
  }

  @Test
  void keepsUnknownVariablesAsWritten() {
    assertEquals("%NOPE%\\x", AppBootstrap.expandVariables("%NOPE%\\x", Map.of()));
  }

  @Test
  void fallsBackToLocalAppData() {
    assertEquals(
        Path.of("D:\\L", "Serox"), AppBootstrap.resolveDataDirectory(Map.of("LOCALAPPDATA", "D:\\L")));
  }
}
