package com.snackcounter.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The layering rules, enforced on every build by reading each main source file's imports:
 *
 * <pre>
 *   controller ──> service ──> repository
 *       │            │  └────> client (chat tool, HR system)
 *       └──> dto <───┘   model is used by everyone and uses nobody
 *
 *   handler ──> exception    (turns rule violations into HTTP errors; nothing imports it)
 * </pre>
 */
class LayeringTest {

    private static final Path MAIN = Path.of("src/main/java/com/snackcounter");

    /** Layer -> layers it must never import. Nobody imports {@code handler}: Spring finds it by scanning. */
    private static final Map<String, List<String>> FORBIDDEN = Map.of(
            "controller", List.of("repository", "client", "model", "scheduler", "config", "handler"),
            "service", List.of("controller", "scheduler", "handler"),
            "repository", List.of("controller", "service", "client", "dto", "scheduler", "handler"),
            "model", List.of("controller", "service", "repository", "client", "dto", "scheduler", "exception", "config", "handler"),
            "dto", List.of("controller", "service", "repository", "client", "scheduler", "handler"),
            "client", List.of("controller", "service", "repository", "dto", "scheduler", "handler"),
            "exception", List.of("controller", "service", "repository", "client", "dto", "model", "scheduler", "handler"),
            "scheduler", List.of("controller", "repository", "client", "dto", "handler"),
            "config", List.of("controller", "service", "repository", "client", "dto", "scheduler", "handler"),
            "handler", List.of("controller", "service", "repository", "client", "dto", "model", "scheduler", "config"));

    static Stream<String> layers() {
        return FORBIDDEN.keySet().stream().sorted();
    }

    @ParameterizedTest(name = "{0} respects its layer boundaries")
    @MethodSource("layers")
    void layerOnlyDependsOnWhatItMay(String layer) throws IOException {
        assertThat(MAIN.resolve(layer)).as("layer package exists").isDirectory();
        List<String> violations;
        try (Stream<Path> files = Files.walk(MAIN.resolve(layer))) {
            violations = files.filter(p -> p.toString().endsWith(".java"))
                    .flatMap(file -> forbiddenImports(file, FORBIDDEN.get(layer)))
                    .toList();
        }
        assertThat(violations).as("imports that break the layering").isEmpty();
    }

    private static Stream<String> forbiddenImports(Path file, List<String> forbidden) {
        try {
            return Files.readAllLines(file).stream()
                    .filter(line -> line.startsWith("import "))
                    .filter(line -> forbidden.stream().anyMatch(target ->
                            line.matches("import (static )?com\\.snackcounter\\." + target + "\\..*")))
                    .map(line -> file.getFileName() + ": " + line);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
