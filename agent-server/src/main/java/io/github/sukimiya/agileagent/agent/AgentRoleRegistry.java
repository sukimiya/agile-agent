package io.github.sukimiya.agileagent.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.knowledge.KnowledgeCapabilityProvider;
import io.github.sukimiya.agileagent.knowledge.KnowledgeCatalog;
import io.github.sukimiya.agileagent.service.ApiCatalog;
import io.github.sukimiya.agileagent.service.EnterpriseApiClient;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;

/**
 * Assembles the fixed set of {@link AgentRole}s the system knows about: one per
 * enterprise API service (index = that service's slice of {@link ApiCatalog}) plus
 * one for the knowledge base (index = {@link KnowledgeCatalog}).
 */
@Component
public class AgentRoleRegistry {

    private static final LlmClient.ToolDefinition GET_API_DETAIL_TOOL = new LlmClient.ToolDefinition(
            "getApiDetail",
            "查看某个企业服务接口的完整文档（参数、返回字段），在调用 callEnterpriseApi 前用于确认参数格式",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "service", Map.of("type", "string", "description", "服务代码，如 MES/CRM/WMS/QMS/SRM"),
                            "path", Map.of("type", "string", "description", "接口路径，如 /api/mes/five-s-records/create")
                    ),
                    "required", List.of("service", "path")));

    private static final LlmClient.ToolDefinition CALL_ENTERPRISE_API_TOOL = new LlmClient.ToolDefinition(
            "callEnterpriseApi",
            "调用企业服务的一个已注册接口。service/method/path 必须与索引或 getApiDetail 返回的完全一致",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "service", Map.of("type", "string", "description", "服务代码，如 MES/CRM/WMS/QMS/SRM"),
                            "method", Map.of("type", "string", "description", "HTTP 方法，如 GET/POST/PUT/DELETE"),
                            "path", Map.of("type", "string", "description", "接口路径"),
                            "params", Map.of("type", "object", "description", "请求参数（路径参数、查询参数或请求体字段）")
                    ),
                    "required", List.of("service", "method", "path")));

    private static final LlmClient.ToolDefinition READ_KNOWLEDGE_DOC_TOOL = new LlmClient.ToolDefinition(
            "readKnowledgeDoc",
            "读取一份已审核通过的知识库文档全文，在从索引中判断出哪份文档能回答用户问题后调用",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "id", Map.of("type", "string", "description", "文档ID，取自知识库索引")
                    ),
                    "required", List.of("id")));

    private static final String API_PROMPT_TEMPLATE = """
            你是企业内部 API 智能助手，帮助用户通过自然语言查询企业系统的数据。

            可用接口索引（格式：服务 | 接口名 | 方法 路径 | 功能说明）：
            %s

            规则：
            1. 先从索引中找到最匹配用户问题的接口；如需确认参数或返回字段，调用 getApiDetail 查看完整文档。
            2. 调用 callEnterpriseApi 时，service/method/path 必须与索引或 getApiDetail 返回的完全一致，不要臆造路径。
            3. 如果调用失败或返回为空，根据错误信息判断是否选错了接口或参数，可以换接口或修正参数后重试。
            4. 拿到数据后，用自然语言总结归纳，可用表格/分组呈现，不要原样输出 JSON。
            5. 如果多次尝试仍无法获取需要的数据，如实告知用户，不要编造结果。
            """;

    private static final String KNOWLEDGE_PROMPT_TEMPLATE = """
            你是企业内部知识库助手，帮助用户查阅已审核通过的业务/流程类文档（如低代码平台使用说明、操作规范等）。

            可用文档索引（格式：文档ID | 标题 | 摘要）：
            %s

            规则：
            1. 先从索引中找到最匹配用户问题的文档，调用 readKnowledgeDoc(id) 读取全文再作答。
            2. 如果索引中没有明显相关的文档，如实告知用户没有找到相关资料，不要编造内容。
            3. 回答时可直接引用文档中的代码示例或步骤，不要仅泛泛总结。
            """;

    private final Map<String, AgentRole> rolesByCode;
    private final ApiCatalog apiCatalog;
    private final KnowledgeCapabilityProvider knowledgeProvider;

    @Autowired
    public AgentRoleRegistry(ApiCatalog apiCatalog, KnowledgeCatalog knowledgeCatalog,
                             KnowledgeCapabilityProvider knowledgeProvider) {
        this.apiCatalog = apiCatalog;
        this.knowledgeProvider = knowledgeProvider;
        var roles = new LinkedHashMap<String, AgentRole>();
        addApiRole(roles, apiCatalog, "MES", "制造执行系统", "生产工单、设备、5S、质量检验等制造现场数据");
        addApiRole(roles, apiCatalog, "CRM", "客户关系管理系统", "客户、商机、合同、报价等销售数据");
        addApiRole(roles, apiCatalog, "WMS", "仓储管理系统", "入库、出库、库存、盘点等仓储数据");
        addApiRole(roles, apiCatalog, "QMS", "质量管理系统", "检验、不合格品、质量追溯等质量数据");
        addApiRole(roles, apiCatalog, "SRM", "供应商关系管理", "供应商、采购、寻源等供应链数据");
        roles.put("KNOWLEDGE", new AgentRole(knowledgeProvider.code(), knowledgeProvider.displayName(),
                knowledgeProvider.routingDescription(), knowledgeCatalog::compactIndex,
                List.of(READ_KNOWLEDGE_DOC_TOOL), KNOWLEDGE_PROMPT_TEMPLATE));
        this.rolesByCode = Map.copyOf(roles);
    }

    public AgentRoleRegistry(ApiCatalog apiCatalog, KnowledgeCatalog knowledgeCatalog) {
        this(apiCatalog, knowledgeCatalog, new KnowledgeCapabilityProvider(knowledgeCatalog));
    }

    private void addApiRole(Map<String, AgentRole> roles, ApiCatalog apiCatalog,
                             String code, String displayName, String routingBlurb) {
        roles.put(code, new AgentRole(code, displayName, routingBlurb,
                () -> apiCatalog.compactIndex(code),
                List.of(GET_API_DETAIL_TOOL, CALL_ENTERPRISE_API_TOOL),
                API_PROMPT_TEMPLATE));
    }

    public Optional<AgentRole> byCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(rolesByCode.get(code.toUpperCase()));
    }

    public List<AgentRole> all() {
        return List.copyOf(rolesByCode.values());
    }

    public List<AgentCapabilityProvider> apiCapabilityProviders(EnterpriseApiClient enterpriseApi) {
        var providers = new java.util.ArrayList<AgentCapabilityProvider>();
        rolesByCode.values().stream()
                .filter(role -> !"KNOWLEDGE".equals(role.code()))
                .map(role -> (AgentCapabilityProvider) new ApiRoleCapabilityProvider(role, apiCatalog, enterpriseApi))
                .forEach(providers::add);
        return List.copyOf(providers);
    }

    public CapabilityRegistry capabilityRegistry(EnterpriseApiClient enterpriseApi) {
        var providers = new java.util.ArrayList<>(apiCapabilityProviders(enterpriseApi));
        providers.add(knowledgeProvider);
        return new CapabilityRegistry(providers);
    }

    public String routingCatalog() {
        var sb = new StringBuilder();
        for (var role : rolesByCode.values()) {
            sb.append(role.code()).append(" | ").append(role.displayName())
                    .append(" | ").append(role.routingBlurb()).append('\n');
        }
        return sb.toString();
    }
}
