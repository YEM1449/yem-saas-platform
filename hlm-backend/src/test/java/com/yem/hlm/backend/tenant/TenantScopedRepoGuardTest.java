package com.yem.hlm.backend.tenant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static guard test: ensures that business-entity repositories are always
 * queried through tenant-scoped methods (findByTenant_IdAnd*) in service
 * and controller layers.
 *
 * Bare .findById() on tenant-scoped repos would bypass tenant isolation.
 * This test fails fast with file + line number if a risky pattern is found.
 *
 * Allowlisted repos: TenantRepository, ClientDetailRepository,
 * ProspectDetailRepository (lookup by FK after tenant-scoped parent fetch),
 * UserRepository in auth/cache context only.
 */
class TenantScopedRepoGuardTest {

    // Repos that MUST always be called with tenant-scoped methods
    private static final List<String> GUARDED_REPOS = List.of(
            "contactRepository",
            "propertyRepository",
            "depositRepository",
            "notificationRepository",
            "contactInterestRepository"
    );

    // Pattern: any of the guarded repos followed by .findById(
    private static final Pattern RISKY_PATTERN = Pattern.compile(
            "(" + String.join("|", GUARDED_REPOS) + ")\\.findById\\("
    );

    // Packages to scan (service + api layers)
    private static final List<String> SCAN_PACKAGES = List.of(
            "src/main/java/com/yem/hlm/backend/contact",
            "src/main/java/com/yem/hlm/backend/property",
            "src/main/java/com/yem/hlm/backend/deposit",
            "src/main/java/com/yem/hlm/backend/notification",
            "src/main/java/com/yem/hlm/backend/user"
    );

    @Test
    void noBareFindByIdOnGuardedRepos() throws IOException {
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
                .as("Tenant isolation guard: bare .findById() on guarded repos bypasses tenant scoping. "
                        + "Use findByTenant_IdAnd*() instead.")
                .isEmpty();
    }

    private void scanFile(Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                if (RISKY_PATTERN.matcher(lines.get(i)).find()) {
                    violations.add(file.getFileName() + ":" + (i + 1) + " → " + lines.get(i).trim());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
    }

    private Path findProjectRoot() {
        // Walk up from test class output dir to find hlm-backend root
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        // Fallback: assume CWD is project root
        return Path.of("").toAbsolutePath();
    }
}
