package com.jowo.agent.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class ApiCatalog {

    private static final List<String> SERVICE_CODES = List.of("MES", "CRM", "WMS", "QMS", "SRM");
    private static final Pattern INTERFACE_HEADING = Pattern.compile("^## 接口：(.+)$", Pattern.MULTILINE);
    private static final Pattern PATH_LINE = Pattern.compile("\\*\\*路径：\\*\\*\\s*(\\S+)\\s+(\\S+)");

    public record ApiEntry(String service, String name, String method, String path, String summary, String detail) {}

    private final List<ApiEntry> entries;

    public ApiCatalog() {
        this.entries = loadAll();
    }

    private static List<ApiEntry> loadAll() {
        var resolver = new PathMatchingResourcePatternResolver();
        var all = new ArrayList<ApiEntry>();
        for (String code : SERVICE_CODES) {
            try {
                var resources = resolver.getResources("classpath:api-docs/" + code.toLowerCase() + "/*.md");
                for (var resource : resources) {
                    String text = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
                    all.addAll(parse(code, text));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("failed to load api-docs for " + code, e);
            }
        }
        return all;
    }

    static List<ApiEntry> parse(String serviceCode, String markdown) {
        var headingMatcher = INTERFACE_HEADING.matcher(markdown);
        var starts = new ArrayList<Integer>();
        var names = new ArrayList<String>();
        while (headingMatcher.find()) {
            starts.add(headingMatcher.start());
            names.add(headingMatcher.group(1).trim());
        }

        var result = new ArrayList<ApiEntry>();
        for (int i = 0; i < starts.size(); i++) {
            int begin = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : markdown.length();
            String block = markdown.substring(begin, end).trim();

            var pathMatcher = PATH_LINE.matcher(block);
            if (!pathMatcher.find()) continue;
            String method = pathMatcher.group(1);
            String path = pathMatcher.group(2);
            String summary = extractSummary(block);

            result.add(new ApiEntry(serviceCode, names.get(i), method, path, summary, block));
        }
        return result;
    }

    private static String extractSummary(String block) {
        var lines = block.split("\n");
        var summary = new StringBuilder();
        boolean collecting = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("**功能说明：**")) {
                collecting = true;
                continue;
            }
            if (collecting) {
                if (trimmed.isEmpty()) break;
                summary.append(trimmed);
            }
        }
        return summary.toString();
    }

    public String compactIndex(String service) {
        var sb = new StringBuilder();
        for (var e : entries) {
            if (!e.service().equalsIgnoreCase(service)) continue;
            sb.append(e.service()).append(" | ").append(e.name()).append(" | ")
              .append(e.method()).append(' ').append(e.path()).append(" | ")
              .append(e.summary()).append('\n');
        }
        return sb.toString();
    }

    public boolean isWhitelisted(String service, String method, String path) {
        return entries.stream().anyMatch(e ->
                e.service().equalsIgnoreCase(service) &&
                e.method().equalsIgnoreCase(method) &&
                e.path().equals(path));
    }

    public Optional<String> detail(String service, String path) {
        return entries.stream()
                .filter(e -> e.service().equalsIgnoreCase(service) && e.path().equals(path))
                .map(ApiEntry::detail)
                .findFirst();
    }

    public int size() {
        return entries.size();
    }
}
