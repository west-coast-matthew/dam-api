package com.twofourseven.dam.services.thumbnails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs small POSIX commands (sh, printf, sleep); no image tools are needed. */
@DisabledOnOs(OS.WINDOWS)
class CommandRunnerTest {

    private final CommandRunner runner = new CommandRunner();

    @Test
    void returnsStandardOutput() {
        byte[] out = runner.run(List.of("printf", "hello"), Duration.ofSeconds(10));

        assertThat(new String(out, StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void doesNotInterpretArgumentsWithAShell() {
        byte[] out = runner.run(List.of("printf", "%s", "320x320> $(id) ; *"), Duration.ofSeconds(10));

        assertThat(new String(out, StandardCharsets.UTF_8)).isEqualTo("320x320> $(id) ; *");
    }

    @Test
    void failsWithTheErrorOutputOnANonZeroExit() {
        assertThatThrownBy(() -> runner.run(List.of("sh", "-c", "echo broken >&2; exit 3"), Duration.ofSeconds(10)))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("exit code 3")
                .hasMessageContaining("broken");
    }

    @Test
    void failsWhenTheCommandIsMissing() {
        assertThatThrownBy(() -> runner.run(List.of("dam-no-such-command"), Duration.ofSeconds(10)))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("installed");
    }

    @Test
    void stopsCommandsThatRunTooLong() {
        long start = System.nanoTime();

        assertThatThrownBy(() -> runner.run(List.of("sleep", "10"), Duration.ofMillis(200)))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("did not finish");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }
}
