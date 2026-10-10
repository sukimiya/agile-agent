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
    private static final List<Pattern> FORBIDDEN_REDIS_CONFIGURATION = List.of(
            Pattern.compile("(?i)new\\s+RedisStandaloneConfiguration\\s*\\(\\s*[\\\"'](?!localhost[\\\"']|127\\.0\\.0\\.1[\\\"']|\\$\\{)[^\\\"']+[\\\"']"),
            Pattern.compile("(?i)setPassword\\s*\\(\\s*[\\\"'](?!\\$\\{)[^\\\"']+[\\\"']\\s*\\)"),
            Pattern.compile("(?im)^\\s*(?:spring(?:\\.data)?\\.redis\\.host|(?:(?:SPRING_)?DATA_REDIS_HOST|REDIS_HOST))\\s*[:=]\\s*(?!localhost(?=\\s|#|$)|127\\.0\\.0\\.1(?=\\s|#|$)|redis(?=\\s|#|$)|\\$\\{)[^\\s#]+"),
            Pattern.compile("(?im)^\\s*(?:spring(?:\\.data)?\\.redis\\.password|(?:SPRING_)?DATA_REDIS_PASSWORD)\\s*[:=]\\s*(?!\\$\\{)[^\\s#]+"),
            Pattern.compile("(?i)rediss?://[^\\s/@:]*:[^\\s/@]+@"),
            Pattern.compile("(?i)rediss?://(?!localhost\\b|127\\.0\\.0\\.1\\b|\\[::1\\]|\\$\\{)[^\\s/:]+"));

    @Test
    void trackedProductRootsContainNoEnterpriseBrandingOrBusinessDomainArtifacts() throws Exception {
        Path root = findRepositoryRoot(Path.of("").toAbsolutePath());
        List<String> violations = new ArrayList<>();

        for (String relativePath : trackedProductFiles(root)) {
            if (excluded(relativePath) || BOUNDARY_TEST.equals(relativePath)) continue;
            forbiddenPathMarkers(relativePath).forEach(marker -> violations.add(relativePath + " " + marker));
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
            for (Pattern forbidden : FORBIDDEN_REDIS_CONFIGURATION) {
                if (forbidden.matcher(content).find()) {
                    violations.add(relativePath + " [private Redis endpoint or inline credential]");
                }
            }
        }

        assertThat(violations).as("open-source product boundary violations").isEmpty();
    }

    @Test
    void defaultContainerConfigurationDoesNotRequireNacos() throws IOException {
        Path root = findRepositoryRoot(Path.of("").toAbsolutePath());
        String dockerfile = Files.readString(root.resolve("docker/Dockerfile"), StandardCharsets.UTF_8);
        String compose = Files.readString(root.resolve("docker/docker-compose.yml"), StandardCharsets.UTF_8);
        String application = Files.readString(
                root.resolve("agent-server/src/main/resources/application.yml"), StandardCharsets.UTF_8);

        assertThat(dockerfile).doesNotContain("-Dspring.profiles.active=dev");
        assertThat(compose).contains(
                "image: ${DOCKER_REGISTRY_URL}/agile-agent-server:${IMAGE_TAG}",
                "- APP_DATA_DIR=/data", "- DEBUG=${DEBUG:-false}",
                "- SPRING_PROFILES_ACTIVE", "- NACOS_SERVER_ADDR");
        assertThat(compose).contains(
                "LLM_CONFIG_SECRET=${LLM_CONFIG_SECRET:?LLM_CONFIG_SECRET must be set}",
                "LLM_CONFIG_SECRET: ${LLM_CONFIG_SECRET:?LLM_CONFIG_SECRET must be set}")
                .doesNotContain("${LLM_CONFIG_SECRET:-");
        assertThat(composeServiceSection(compose, "agile-agent-server"))
                .contains("LLM_CONFIG_SECRET=${LLM_CONFIG_SECRET:?LLM_CONFIG_SECRET must be set}");
        assertThat(composeServiceSection(compose, "agile-agent-server-local-mock"))
                .contains("LLM_CONFIG_SECRET: ${LLM_CONFIG_SECRET:?LLM_CONFIG_SECRET must be set}");
        assertThat(application).contains("server-addr: ${NACOS_SERVER_ADDR:localhost:8848}");
        assertThat(application).contains("secret: ${LLM_CONFIG_SECRET:}");
        assertThat(Files.readString(root.resolve("README.md"), StandardCharsets.UTF_8))
                .contains("openssl rand -base64 32", "never commit it");
        assertThat(application.indexOf("on-profile: dev"))
                .isLessThan(application.indexOf("nacos:${spring.application.name}"));
    }

    @Test
    void localMockHostPortsAreLoopbackOnlyAndDefaultServerPublishIsUnchanged() throws IOException {
        Path root = findRepositoryRoot(Path.of("").toAbsolutePath());
        String compose = Files.readString(root.resolve("docker/docker-compose.yml"), StandardCharsets.UTF_8);
        String defaultServer = composeServiceSection(compose, "agile-agent-server");
        String localServer = composeServiceSection(compose, "agile-agent-server-local-mock");
        String mockProvider = composeServiceSection(compose, "agile-agent-mock-oauth2");

        assertThat(defaultServer).contains("- \"8088:8088\"").doesNotContain("127.0.0.1:");
        assertThat(localServer).contains("- \"127.0.0.1:8088:8088\"").doesNotContain("- \"8088:8088\"");
        assertThat(mockProvider)
                .contains("- \"127.0.0.1:8090:8090\"", "SERVER_ADDRESS: 0.0.0.0")
                .doesNotContain("- \"8090:8090\"");
        String readme = Files.readString(root.resolve("README.md"), StandardCharsets.UTF_8);
        assertThat(readme).contains("publishes the server and mock provider ports on `127.0.0.1` only");
    }

    @Test
    void redisBoundaryRulesRejectPrivateEndpointsAndInlineCredentials() {
        assertThat(matchesAny(FORBIDDEN_REDIS_CONFIGURATION,
                "new RedisStandaloneConfiguration(\"redis.internal.example\", 6379)")).isTrue();
        assertThat(matchesAny(FORBIDDEN_REDIS_CONFIGURATION,
                "redis://user:password@redis.internal.example:6379")).isTrue();
        assertThat(matchesAny(FORBIDDEN_REDIS_CONFIGURATION,
                "config.setPassword(\"plaintext-secret\")")).isTrue();
        assertThat(matchesAny(FORBIDDEN_REDIS_CONFIGURATION,
                "spring.data.redis.password: plaintext-secret")).isTrue();
        assertThat(matchesAny(FORBIDDEN_REDIS_CONFIGURATION,
                "spring.data.redis.host: redis.internal.example")).isTrue();
        assertThat(matchesAny(FORBIDDEN_REDIS_CONFIGURATION,
                "new RedisStandaloneConfiguration(\"${TEST_REDIS_HOST}\", 6379)")).isFalse();
        assertThat(matchesAny(FORBIDDEN_REDIS_CONFIGURATION,
                "config.setPassword(\"${TEST_REDIS_PASSWORD}\")")).isFalse();
        assertThat(matchesAny(FORBIDDEN_REDIS_CONFIGURATION,
                "SPRING_DATA_REDIS_HOST: redis")).isFalse();
        assertThat(matchesAny(FORBIDDEN_REDIS_CONFIGURATION,
                "spring.data.redis.host: ${TEST_REDIS_HOST}")).isFalse();
        assertThat(matchesAny(FORBIDDEN_REDIS_CONFIGURATION,
                "new RedisStandaloneConfiguration(\"127.0.0.1\", 49152)")).isFalse();
    }

    @Test
    void relativePathsReceivePreciseDomainChecks() {
        assertThat(forbiddenPathMarkers("agent-server/src/main/java/com/jowo/agileagent/Helper.java"))
                .isNotEmpty();
        assertThat(forbiddenPathMarkers("agent-server/src/main/resources/meshing-guide.md"))
                .isEmpty();
        assertThat(forbiddenPathMarkers("agent-server/src/main/resources/crmish-guide.md"))
                .isEmpty();
    }

    private static List<String> forbiddenPathMarkers(String relativePath) {
        List<String> markers = new ArrayList<>();
        String fileName = Path.of(relativePath).getFileName().toString();
        if (FORBIDDEN_FILES.contains(fileName)) {
            markers.add("[forbidden enterprise component]");
        }
        for (Pattern forbidden : FORBIDDEN_TEXT) {
            if (forbidden.matcher(relativePath).find()) {
                markers.add("[matches " + forbidden.pattern() + "]");
            }
        }
        return markers;
    }

    private static boolean matchesAny(List<Pattern> patterns, String content) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(content).find());
    }

    private static String composeServiceSection(String compose, String serviceName) {
        StringBuilder section = new StringBuilder();
        boolean found = false;
        for (String line : compose.split("\\R")) {
            if (!found && line.equals("  " + serviceName + ":")) {
                found = true;
            } else if (found && line.matches("^  [^ ].*:$")) {
                break;
            }
            if (found) section.append(line).append(System.lineSeparator());
        }
        return section.toString();
    }

    private static List<String> trackedProductFiles(Path root) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "ls-files", "--cached", "--others", "--exclude-standard", "-z")
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new IOException("git ls-files failed: " + output);

        return java.util.Arrays.stream(output.split(Pattern.quote(String.valueOf((char) 0))))
                .filter(path -> !path.isBlank())
                .filter(path -> Files.exists(root.resolve(path)))
                .filter(OpenSourceBoundaryTest::isProductFile)
                .collect(Collectors.toList());
    }

    private static boolean isProductFile(String path) {
        if (excluded(path)) return false;
        return path.equals("pom.xml")
                || path.matches("[^/]+/pom\\.xml")
                || path.matches("agent-[^/]+/src/.*")
                || path.matches("mock-oauth2/src/.*")
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
