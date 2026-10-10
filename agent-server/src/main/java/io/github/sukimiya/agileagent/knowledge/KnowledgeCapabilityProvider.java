package io.github.sukimiya.agileagent.knowledge;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import reactor.core.publisher.Mono;

/** The built-in provider for approved knowledge documents. */
@Component
public class KnowledgeCapabilityProvider implements AgentCapabilityProvider {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeCapabilityProvider.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROMPT_TEMPLATE = """
            你是企业内部知识库助手，帮助用户查阅已审核通过的业务/流程类文档（如低代码平台使用说明、操作规范等）。

            可用文档索引（格式：文档ID | 标题 | 摘要）：
            %s

            规则：
            1. 先从索引中找到最匹配用户问题的文档，调用 readKnowledgeDoc(id) 读取全文再作答。
            2. 如果索引中没有明显相关的文档，如实告知用户没有找到相关资料，不要编造内容。
            3. 回答时可直接引用文档中的代码示例或步骤，不要仅泛泛总结。
            """;
    private static final String READ_TOOL_DESCRIPTION =
            "读取一份已审核通过的知识库文档全文，在从索引中判断出哪份文档能回答用户问题后调用";

    private final KnowledgeCatalog catalog;

    public KnowledgeCapabilityProvider(KnowledgeCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String code() {
        return "KNOWLEDGE";
    }

    @Override
    public String displayName() {
        return "知识库";
    }

    @Override
    public String routingDescription() {
        return "已审核通过的业务/流程类文档，如低代码平台使用说明";
    }

    @Override
    public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
        return Mono.fromSupplier(() -> {
            String index = catalog.compactIndex();
            return new CapabilityDefinition(PROMPT_TEMPLATE.formatted(index), List.of(readTool(index)));
        });
    }

    private static ToolDefinition readTool(String index) {
        var idSchema = new java.util.LinkedHashMap<String, Object>();
        idSchema.put("type", "string");
        idSchema.put("description", "文档ID，取自知识库索引");
        var ids = index.lines()
                .map(line -> line.split("\\s+\\|\\s+", 2)[0].trim())
                .filter(id -> !id.isEmpty())
                .toList();
        if (!ids.isEmpty()) idSchema.put("enum", ids);
        return new ToolDefinition("readKnowledgeDoc", READ_TOOL_DESCRIPTION,
                JSON.valueToTree(Map.of("type", "object",
                        "properties", Map.of("id", idSchema), "required", List.of("id"))));
    }

    @Override
    public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
        if (!"readKnowledgeDoc".equals(invocation.toolName())) {
            return Mono.just(new ToolResult(false, "unknown tool: " + invocation.toolName()));
        }
        String id = invocation.arguments().path("id").asText();
        return catalog.content(id)
                .defaultIfEmpty("")
                .doOnNext(content -> log.info("readKnowledgeDoc id={} found={} chars={}", id,
                        !content.isEmpty(), content.length()))
                .map(content -> content.isEmpty()
                        ? new ToolResult(false, "文档不存在: " + id)
                        : new ToolResult(true, content));
    }
}
