package kr.soyuo.serox.ai.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** Windows profile secrets protected by DPAPI for the current Windows user. */
public final class ProtectedLocalStore {
  private final Path file;

  public ProtectedLocalStore(Path file) {
    this.file = file;
  }

  public synchronized String read() throws IOException {
    if (!Files.exists(file)) {
      return "{}";
    }
    if (Files.size(file) > 1024 * 1024) {
      throw new IOException("Credential store is too large");
    }
    return new String(transform(Files.readAllBytes(file), false), StandardCharsets.UTF_8);
  }

  public synchronized void write(String json) throws IOException {
    byte[] protectedBytes = transform(json.getBytes(StandardCharsets.UTF_8), true);
    Files.createDirectories(file.toAbsolutePath().getParent());
    Path temporary =
        Files.createTempFile(file.toAbsolutePath().getParent(), "credentials-", ".tmp");
    try {
      Files.write(temporary, protectedBytes);
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static byte[] transform(byte[] input, boolean protect) throws IOException {
    if (!System.getProperty("os.name", "").startsWith("Windows")) {
      throw new IOException("Windows secure storage is unavailable");
    }
    String script =
        "$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Security; "
            + "$bytes=[Convert]::FromBase64String([Console]::In.ReadToEnd()); "
            + "$result=[Security.Cryptography.ProtectedData]::"
            + (protect ? "Protect" : "Unprotect")
            + "($bytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser); "
            + "[Console]::Out.Write([Convert]::ToBase64String($result))";
    String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    Path executable =
        Path.of(
            System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
    Process process =
        new ProcessBuilder(
                executable.toString(),
                "-NoProfile",
                "-NonInteractive",
                "-WindowStyle",
                "Hidden",
                "-EncodedCommand",
                encoded)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    try {
      // Secret bytes travel only over a pipe, never as arguments or a temporary plaintext file.
      try (var output = process.getOutputStream()) {
        output.write(Base64.getEncoder().encode(input));
      }
      var result =
          java.util.concurrent.CompletableFuture.supplyAsync(
              () -> {
                try {
                  return process.getInputStream().readNBytes(2 * 1024 * 1024);
                } catch (IOException exception) {
                  throw new java.io.UncheckedIOException(exception);
                }
              });
      if (!process.waitFor(15, TimeUnit.SECONDS) || process.exitValue() != 0) {
        throw new IOException("Windows secure storage failed");
      }
      return Base64.getDecoder().decode(result.get(1, TimeUnit.SECONDS));
    } catch (Exception exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IOException("Windows secure storage failed");
    } finally {
      process.destroyForcibly();
    }
  }
}
