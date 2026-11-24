package com.twofourseven.dam.services.thumbnails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs a command line tool (no shell, so arguments are never interpreted) and returns what it writes
 * to standard output. Kept separate so generators can be unit tested without the tools installed.
 */
@Component
public class CommandRunner {

    private static final Logger log = LoggerFactory.getLogger(CommandRunner.class);
    private static final int MAX_ERROR_CHARS = 2000;

    /**
     * @return everything the command wrote to standard output
     * @throws ThumbnailGenerationException if the command can't be started, exits with a non-zero
     *                                      status or runs longer than {@code timeout}
     */
    public byte[] run(List<String> command, Duration timeout) {
        String name = command.getFirst();
        log.debug("Running {}", command);
        Path stderr = null;
        Process process = null;
        try {
            stderr = Files.createTempFile("dam-cmd-", ".err");
            process = new ProcessBuilder(command).redirectError(stderr.toFile()).start();
            process.getOutputStream().close();
            InputStream stdout = process.getInputStream();
            CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> readAll(stdout),
                    task -> Thread.ofVirtual().start(task));

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ThumbnailGenerationException("'" + name + "' did not finish within " + timeout);
            }
            byte[] result = output.get(10, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new ThumbnailGenerationException("'" + name + "' failed with exit code " + process.exitValue()
                        + ": " + errorText(stderr));
            }
            return result;
        } catch (IOException e) {
            throw new ThumbnailGenerationException("Could not run '" + name + "'; is it installed and on the PATH?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ThumbnailGenerationException("Interrupted while running '" + name + "'", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new ThumbnailGenerationException("Could not read the output of '" + name + "'", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (stderr != null) {
                try {
                    Files.deleteIfExists(stderr);
                } catch (IOException e) {
                    log.warn("Could not delete {}", stderr, e);
                }
            }
        }
    }

    private static byte[] readAll(InputStream in) {
        try (in) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String errorText(Path stderr) throws IOException {
        String text = Files.readString(stderr, StandardCharsets.UTF_8).strip();
        if (text.isEmpty()) {
            return "(no error output)";
        }
        return text.length() <= MAX_ERROR_CHARS ? text : "..." + text.substring(text.length() - MAX_ERROR_CHARS);
    }
}
