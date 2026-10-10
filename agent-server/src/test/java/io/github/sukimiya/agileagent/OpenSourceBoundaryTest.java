package io.github.sukimiya.agileagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class OpenSourceBoundaryTest {

    private static final String BOUNDARY_TEST = "agent-server/src/test/java/io/github/sukimiya/agileagent/OpenSourceBoundaryTest.java";
    private static final Set<String> FORBIDDEN_FILES = Set.of(
            "ApiCatalog.java", "ApiCatalogTest.java", "EnterpriseApiClient.java",
            "HttpEnterpriseApiClient.java", "HttpEnterpriseApiClientTest.java",
            "EnterpriseApiProperties.java", "EnterpriseApiPropertiesTest.java",
            "ApiRoleCapabilityProvider.java", "AgentRole.java", "AgentRoleRegistry.java",
            "AgentRoleRegistryTest.java");
    private static final List<Pattern> FORBIDDEN_TEXT = List.of(
            Pattern.compile("(?i)com\\." + "jowo"),
            Pattern.compile("(?i)\\b" + "jowo" + "\\b"),
            Pattern.compile("(?i)l7[-_]" + "skill"),
            Pattern.compile("(?i)/api/" + "services\\b"),
            Pattern.compile("(?i)(?<![A-Za-z0-9])(?:MES|CRM|WMS|QMS|SRM|MDM|EAM|EMS)(?![A-Za-z0-9])"));

    @Test
    void trackedProductRootsContainNoEnterpriseBrandingOrBusinessDomainArtifacts() throws Exception {
        Path root = findRepositoryRoot(Path.of("").toAbsolutePath());
        List<String> violations = new ArrayList<>();

        for (String relativePath : trackedProductFiles(root)) {
            if (excluded(relativePath) || BOUNDARY_TEST.equals(relativePath)) continue;
            String fileName = Path.of(relativePath).getFileName().toString();
            if (FORBIDDEN_FILES.contains(fileName)) {
                violations.add(relativePath + " [forbidden enterprise component]");
            }
            if (relativePath.startsWith("api-docs/")
                    || relativePath.startsWith("agent-server/src/main/resources/api-docs/")) {
                violations.add(relativePath + " [bundled API documentation]");
            }

            String content = Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8);
            if (relativePath.equals("agent-server/src/main/resources/application.yml")) {
                content = content.replace("    table: l7_skill_flyway_schema_history" + System.lineSeparator(), "");
            }
            for (Pattern forbidden : FORBIDDEN_TEXT) {
                if (forbidden.matcher(content).find()) {
                    violations.add(relativePath + " [matches " + forbidden.pattern() + "]");
                }
            }
        }

        assertThat(violations).as("open-source product boundary violations").isEmpty();
    }

    private static List<String> trackedProductFiles(Path root) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "ls-files", "--cached", "-z")
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new IOException("git ls-files failed: " + output);

        return java.util.Arrays.stream(output.split(Pattern.quote(String.valueOf((char) 0))))
                .filter(path -> !path.isBlank())
                .filter(OpenSourceBoundaryTest::isProductFile)
                .collect(Collectors.toList());
    }

    private static boolean isProductFile(String path) {
        if (excluded(path)) return false;
        return path.equals("pom.xml")
                || path.matches("[^/]+/pom\\.xml")
                || path.matches("agent-[^/]+/src/.*")
                || path.matches("docker/.*")
                || path.equals("Jenkinsfile")
                || path.equals("start.sh")
                || path.matches("README(?:\\..*)?");
    }

    private static boolean excluded(String path) {
        return path.startsWith("docs/spec/") || path.startsWith("docs/superpowers/")
                || path.contains("/.git/") || path.startsWith(".git/")
                || path.contains("/target/") || path.startsWith("target/")
                || path.startsWith(".superpowers/sdd/") || path.startsWith(".superpowers/reviews/");
    }

    private static Path findRepositoryRoot(Path start) {
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("agent-server"))) return candidate;
        }
        throw new IllegalStateException("Could not locate repository root from " + start);
    }
}
