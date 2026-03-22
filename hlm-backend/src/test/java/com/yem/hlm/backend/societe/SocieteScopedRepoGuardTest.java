package com.yem.hlm.backend.societe;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static guard test: business repositories that own société-scoped aggregates
 * must not be queried via bare findById() in API/service layers.
 *
 * Bare findById() on these repositories bypasses société filtering and makes
 * cross-société leakage much easier to introduce accidentally.
 */
class SocieteScopedRepoGuardTest {

    private static final List<String> GUARDED_REPOS = List.of(
            "contactRepository",
            "propertyRepository",
            "depositRepository",
            "notificationRepository",
            "contactInterestRepository",
            "projectRepository",
            "contractRepository"
    );

    private static final Pattern RISKY_PATTERN = Pattern.compile(
            "(" + String.join("|", GUARDED_REPOS) + ")\\.findById\\("
    );

    private static final List<String> SCAN_PACKAGES = List.of(
            "src/main/java/com/yem/hlm/backend/contact",
            "src/main/java/com/yem/hlm/backend/property",
            "src/main/java/com/yem/hlm/backend/deposit",
            "src/main/java/com/yem/hlm/backend/notification",
            "src/main/java/com/yem/hlm/backend/project",
            "src/main/java/com/yem/hlm/backend/contract",
            "src/main/java/com/yem/hlm/backend/user",
            "src/main/java/com/yem/hlm/backend/dashboard",
            "src/main/java/com/yem/hlm/backend/commission"
    );

    @Test
    void noBareFindByIdOnSocieteScopedRepos() throws IOException {
        Path root = findProjectRoot();
        List<String> violations = new ArrayList<>();

        for (String pkg : SCAN_PACKAGES) {
            Path pkgDir = root.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            try (Stream<Path> files = Files.walk(pkgDir)) {
                files.filter(p -> p.toString().endsWith(".java"))
                        .forEach(file -> scanFile(file, violations));
            }
        }

        assertThat(violations)
                .as("Société isolation guard: bare .findById() on guarded repos bypasses société scoping. "
                        + "Use findBySocieteIdAnd*() or fetch through an already scoped parent.")
                .isEmpty();
    }

    private void scanFile(Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                if (RISKY_PATTERN.matcher(lines.get(i)).find()) {
                    violations.add(file.getFileName() + ":" + (i + 1) + " -> " + lines.get(i).trim());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
    }

    private Path findProjectRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return Path.of("").toAbsolutePath();
    }
}
