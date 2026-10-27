# Bundled runtime check

Verified for STEP 14.4 on Windows 11 with JDK 25.0.5 and WiX 3.14.1.

## What was checked

| Check | Method | Result |
| --- | --- | --- |
| The package contains its own Java runtime | `dist\Serox\runtime\lib\modules` (about 93 MiB) exists in the app image and in the MSI | Pass |
| The MSI payload matches the app image | `msiexec /a Serox-0.1.0.msi /qn TARGETDIR=...` (administrative extract, nothing installed) lists `Serox\Serox.exe` and `Serox\runtime\lib\modules` | Pass |
| The app starts without a separate JDK | `Serox.exe` started with `PATH=C:\WINDOWS\system32;C:\WINDOWS` and no `JAVA_HOME` | Pass |
| JavaFX is bundled | The GUI window process stayed alive for 10 s and created `serox.db` | Pass |
| The packaged launcher serves MCP | `Serox.exe --mcp-headless` answered an `initialize` request on stdout (an unapproved client is denied with `-32001`, as designed) and exited with code 0 when stdin closed | Pass |

## Problems found and fixed while checking

- `scripts/package-windows.ps1` aborted on harmless JVM warnings because Windows PowerShell 5.1
  turns native stderr output into a terminating error. The script now relies on exit codes.
- The dependency copy filtered on the `win` classifier and dropped gson, sqlite-jdbc and jsvg, so
  `jlink` failed. The filter is gone, and the platform-neutral JavaFX jars (which hold no native
  libraries) are removed before packaging.
- `--mcp-headless` left the JVM running after the session ended. It now exits explicitly.
- JavaFX printed native-access warnings. The launcher now passes
  `--enable-native-access=javafx.graphics`.

## Not verified

- A real install and uninstall through the Windows Installer UI was not run, because it changes
  the machine (Program Files, Start menu, registry). The administrative extract above only proves
  the package contents. Run the MSI on a clean machine before a public release.
- The MSI is unsigned, so Windows SmartScreen will warn on first run.
