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

## Install, run and uninstall

Run on the developer machine after the user approved it.

| Step | Result |
| --- | --- |
| Install the shipped per-machine MSI (`msiexec /i Serox-0.1.0.msi /qn`) | **Failed with 1603 / error 1925** (needs an administrator). The session was not elevated and UAC was not bypassed. |
| Build a per-user variant of the same payload (`jpackage --win-per-user-install`) and install it | Pass: exit 0, `Serox.exe` under `%LOCALAPPDATA%\SeroxApp`, Start menu and desktop shortcuts created |
| Run the installed app with no JDK on `PATH` | Pass: GUI stayed up for 10 s; `--mcp-headless` answered `initialize` and exited with code 0 |
| Uninstall (`msiexec /x`) | Pass: exit 0; install folder, desktop shortcut and Start menu entry removed; the data folder was kept |

## Not verified

- The shipped per-machine MSI was not installed, so its Program Files location, the `Serox` Start menu
  group and the registry entries are unchecked. Run it from an elevated session or a clean machine
  before a public release.
- Upgrade over an older version was not tried.
- The MSI is unsigned, so Windows SmartScreen will warn on first run.
