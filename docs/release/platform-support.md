# Platform support and data locations

Verified for STEP 14.5.

## Supported platform

| Platform | Status |
| --- | --- |
| Windows 10/11 (x64) | Supported. MSI built and its start, run and exit behavior checked. |
| macOS, Linux | Not packaged or tested. `mvn javafx:run` may work, but it is outside this release. |

## Install, run and exit

| Step | Behavior | Checked |
| --- | --- | --- |
| Install | MSI places `Serox` with its runtime, adds a Start menu entry and a desktop shortcut | Package contents only (see [runtime-bundle.md](runtime-bundle.md)) |
| Start | `Serox.exe` opens the window and creates the database on first start | Yes, from the app image |
| Exit | Closing the window stops the MCP server (if attached), drains pending database writes and closes the database | Yes (automated: `EndToEndScenarioTest`) |
| Headless MCP | `Serox.exe --mcp-headless` exits with code 0 when stdin closes | Yes |
| Uninstall | Removes the program files; the data directory below is kept | Not run |

## Where data lives

| Data | Location |
| --- | --- |
| Database (settings, history, bookmarks, recent searches, session) | `%LOCALAPPDATA%\Serox\serox.db` |
| Override for tests and portable use | environment variable `SEROX_DATA_DIR` |
| Fallback when `LOCALAPPDATA` is missing | `~/.serox/serox.db` |
| Logs | Standard error only; nothing is written to disk |
| API keys | Never stored; read from `SEROX_BRAVE_SEARCH_API_KEY` and `SEROX_ANTHROPIC_API_KEY` |

A corrupt database file is renamed aside and a new one is created; a database written by a
newer Serox version is left untouched and the app runs without persistence.

## Removing user data

Delete `%LOCALAPPDATA%\Serox` after uninstalling. The installer does not remove it.
