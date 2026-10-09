#!/usr/bin/env python3
"""Convert OpenAPI 3.1 JSON to markdown RAG-friendly API docs."""

import json
from pathlib import Path

def load_json(path):
    with open(path, 'r') as f:
        return json.load(f)

def resolve_ref(ref, root):
    """Resolve a $ref string to the actual schema from the JSON root."""
    parts = ref.split('/')
    # ref like "#/components/schemas/Foo"
    obj = root
    for p in parts[1:]:
        obj = obj[p]
    return obj

def get_schema(schema_or_ref, root, depth=0):
    """Resolve a schema (which might be a $ref) and return (type_str, properties_dict)."""
    if depth > 5:
        return "object", {}

    if schema_or_ref is None:
        return "unknown", {}

    if '$ref' in schema_or_ref:
        resolved = resolve_ref(schema_or_ref['$ref'], root)
        return get_schema(resolved, root, depth + 1)

    schema_type = schema_or_ref.get('type', 'object')
    properties = schema_or_ref.get('properties', {})
    required = schema_or_ref.get('required', [])
    enum_values = schema_or_ref.get('enum')
    description = schema_or_ref.get('description', '')

    return schema_type, properties, required, enum_values, description

def resolve_properties(schema_or_ref, root, depth=0, prefix=""):
    """Resolve a schema and return flat list of (name, type, description, required).

    Recursively expands nested objects and arrays so the caller sees every
    leaf field.  Names use dots to show nesting (e.g. ``data.id``,
    ``products[].productName``).  Indentation is baked into the name string
    via the ``prefix`` parameter so that deeper levels accumulate leading
    spaces.
    """
    if depth > 5:
        return []

    if schema_or_ref is None:
        return []

    if '$ref' in schema_or_ref:
        resolved = resolve_ref(schema_or_ref['$ref'], root)
        return resolve_properties(resolved, root, depth + 1, prefix)

    props = schema_or_ref.get('properties', {})
    required_list = schema_or_ref.get('required', [])
    result = []

    for name, prop in props.items():
        is_required = name in required_list
        full_name = f"{prefix}{name}"

        if '$ref' in prop:
            resolved = resolve_ref(prop['$ref'], root)
            prop_desc = resolved.get('description', '')
            result.append((full_name, 'object', prop_desc, is_required))
            # Expand nested object fields
            if resolved.get('properties'):
                child_prefix = f"  {full_name}."
                nested = resolve_properties(resolved, root, depth + 1, child_prefix)
                result.extend(nested)
        elif prop.get('type') == 'object':
            prop_desc = prop.get('description', '')
            result.append((full_name, 'object', prop_desc, is_required))
            if prop.get('properties'):
                child_prefix = f"  {full_name}."
                nested = resolve_properties(prop, root, depth + 1, child_prefix)
                result.extend(nested)
        elif prop.get('type') == 'array' and 'items' in prop:
            items = prop['items']
            if '$ref' in items:
                item_resolved = resolve_ref(items['$ref'], root)
                item_desc = prop.get('description', '')
                result.append((full_name, f"array<{item_resolved.get('type', 'object')}>", item_desc, is_required))
                # Expand array item fields
                if item_resolved.get('properties'):
                    child_prefix = f"  {full_name}[]."
                    nested = resolve_properties(item_resolved, root, depth + 1, child_prefix)
                    result.extend(nested)
            else:
                result.append((full_name, f"array<{items.get('type', 'object')}>", prop.get('description', ''), is_required))
        else:
            result.append((full_name, prop.get('type', 'string'), prop.get('description', ''), is_required))

    return result

def get_description(schema_or_ref, root, depth=0):
    """Get the description from a schema, resolving refs."""
    if depth > 5:
        return ""
    if schema_or_ref is None:
        return ""
    if '$ref' in schema_or_ref:
        resolved = resolve_ref(schema_or_ref['$ref'], root)
        return get_description(resolved, root, depth + 1)
    return schema_or_ref.get('description', '')

def fmt_required(flag):
    return "必填" if flag else "可选"


def enrich_description(summary, module, path, method):
    """Generate a richer business-context description for each endpoint.

    Uses the module context and summary to produce natural-language
    descriptions suitable for RAG.  Priorities: path-specific overrides,
    then pattern-based generation.
    """
    key = f"{method} {path}"

    # ── Business-context overrides for key endpoints ──────────────
    overrides = {
        "POST /api/qrcode/create":
            "用于向客户生成专属分享二维码，客户扫码后可进入客户H5页面，无需登录即可查看自己的"
            "反馈记录和销售订单信息。服务端通过JWT token机制完成客户身份认证和权限控制，"
            "token在设定天数后自动过期（默认7天），过期后二维码失效，需重新生成。",
        "POST /api/sales-order/create":
            "用于创建新的销售订单，记录订单名称、编号、客户信息、客户经理、产品明细及金额等"
            "关键信息。订单创建完成后进入「待审核(0)」状态，后续需通过审核流程流转。"
            "订单全生命周期涉及多个外部系统协同（MES生产/QMS质检/WMS仓储/ERP销售），"
            "各阶段状态变更通过RabbitMQ消息队列驱动。",
        "POST /api/sales-order/update":
            "编辑已存在的销售订单信息，可修改订单名称、产品明细、交货日期、金额等字段。"
            "注意：订单状态变更后部分字段可能不可修改。",
        "POST /api/sales-order/approve":
            "将处于「待审核(0)」状态的销售订单审核通过。旧流程：订单状态变更为「待生产(2)」；"
            "新流程：变更为「新增(1)」。审核通过后CRM系统自动向MES生产系统发送消息"
            "（Routing Key: `order.status.PENDING_PRODUCTION`），触发MES创建生产工单。"
            "该操作为订单全生命周期的起始流转节点，后续由MES/WMS等外部系统接力驱动状态变更。",
        "POST /api/sales-order/reject":
            "将处于「待审核(0)」状态的销售订单审核否决，订单状态变更为「被否决(8)」，"
            "订单流转终止。可填写否决原因供后续参考。否决后订单不再参与后续生产流程，"
            "且不会向MES等外部系统发送消息。",
        "POST /api/sales-order/publish-production":
            "将已审核通过（「新增(1)」/「已审核(1)」）的订单发布到生产环节，"
            "订单状态变更为「已建工单(2)」/「待生产(2)」。该操作在旧流程中为人工发布，"
            "新流程中审核通过后CRM自动通过RabbitMQ向MES发送消息"
            "（`order.status.PENDING_PRODUCTION`），MES创建工单后回调CRM触发此状态变更。",
        "POST /api/sales-order/start-production":
            "将订单状态从「待生产(2)」/「已建工单(2)」变更为「生产中(3)」。"
            "由MES生产系统通过专用通道消息（Routing Key: `order.status.IN_PRODUCTION`）"
            "触发CRM回调，表示生产订单已发布、工厂已开始生产。"
            "消息体包含工单号、产线信息等扩展数据。",
        "POST /api/sales-order/complete-production":
            "生产完成后确认完工。旧流程：订单状态从「生产中(3)」变更为「待发货(4)」；"
            "新流程：变更为「已完工(4)」。可由MES生产系统（`PRODUCTION_COMPLETE`）或"
            "QMS质检系统（`QUALITY_PASSED`）通过外部系统通用通道"
            "（Routing Key: `external.order.status.{system}.{event}`）触发。"
            "需填写实际生产数量用于库存和成本核算。QMS质检不通过时仅记录日志，需人工处理。",
        "POST /api/sales-order/confirm-shipment":
            "确认订单已发货出库，订单状态从「已发货前的状态」变更为「已发货(5)」。"
            "通常由WMS仓储系统通过RabbitMQ通用通道"
            "（Routing Key: `external.order.status.wms.shipment_confirm`）触发CRM回调，"
            "消息体中需包含发货单号。支持MES系统（`APPLY_SHIPMENT`）协同完成发货流程。",
        "POST /api/sales-order/confirm-receipt":
            "确认客户已签收订单货物，订单状态从「已发货(5)」变更为「已签收(6)」，"
            "为订单正向流转的终态。通常由WMS仓储系统通过RabbitMQ通用通道"
            "（Routing Key: `external.order.status.wms.inbound_complete`）触发CRM回调，"
            "需填写客户签收日期（可选，默认当天）。",
        "POST /api/sales-order/terminate":
            "终止处于任何状态的销售订单，需填写终止原因和生产进度说明（可选），"
            "终止后订单状态变为「已终止(7)」且不再参与任何流转。"
            "可由ERP销售系统通过RabbitMQ通用通道"
            "（Routing Key: `external.order.status.erp.order_terminate`）远程触发，"
            "消息中需携带终止原因和生产进度数据。",
        "POST /api/sales-order/delete":
            "软删除销售订单，将订单标记为删除状态，不会物理删除数据库记录。",
        "GET /api/sales-order/page":
            "按条件分页查询销售订单列表，支持按客户名称、订单编号、订单状态、"
            "优先级、交货日期范围等多维度筛选，用于订单管理列表展示。"
            "订单状态基于状态机流转：待审核(0)→已审核/新增(1)→待生产/已建工单(2)→"
            "生产中(3)→待发货/已完工(4)→已发货(5)→已签收(6)，"
            "另有被否决(8)和已终止(7)两个终态。旧流程和新流程的状态编码略有不同，"
            "查询时需注意版本兼容。",
        "GET /api/sales-order/detail":
            "根据订单ID获取销售订单的完整详情信息，包括产品明细、当前状态及状态描述等。"
            "订单状态基于RabbitMQ消息驱动的跨系统状态机，除CRM自身操作外，"
            "还可由MES（生产）、QMS（质检）、WMS（仓储）、ERP（销售）等外部系统触发变更。",
        "POST /api/customer/create":
            "创建新客户档案，记录客户基本信息（名称、状态、级别、行业）、联系信息"
            "（联系人、电话、邮箱）及财务信息（发票抬头、税号、银行账户等）。",
        "POST /api/customer/edit":
            "编辑修改已有客户档案信息，更新客户基本信息或联系信息等字段。",
        "POST /api/customer/delete/{id}":
            "逻辑删除指定客户，将客户标记为无效状态，不会物理删除数据库记录。",
        "POST /api/customer/page":
            "按条件分页查询客户列表，支持按客户名称、编码、状态、级别等多维度筛选。",
        "GET /api/customer/detail":
            "根据客户ID获取客户完整详情信息，包括基本信息、联系信息、财务信息、区域信息等。",
        "POST /api/customer/quick-search":
            "基于MySQL全文索引的客户快速搜索，输入关键字即可模糊匹配客户名称，"
            "按相关度排序返回最匹配的结果（默认前20个），支持百万级数据量的毫秒级搜索。",
        "POST /api/requirement/create":
            "创建客户需求记录，记录客户提出的产品需求、销售阶段、赢单概率、"
            "需求金额及期望日期等信息，用于商机跟踪和管理。",
        "POST /api/requirement/edit":
            "编辑修改已有客户需求信息，更新需求内容、销售阶段、金额等字段。",
        "POST /api/requirement/delete":
            "删除指定客户需求记录。",
        "GET /api/requirement/{id}":
            "根据ID获取客户需求的完整详情信息。",
        "GET /api/requirement/page":
            "按条件分页查询客户需求列表，支持按客户名称、销售阶段、客户经理等多维度筛选。",
        "POST /api/feedback/create":
            "创建反馈单，记录客户反馈的问题类型、问题描述和附件信息，"
            "用于客户投诉和建议的跟踪处理。",
        "POST /api/feedback/update":
            "更新反馈单信息，包括处理时间、处理结果等。",
        "GET /api/feedback/detail/{id}":
            "根据ID获取反馈单的完整详情，包括问题描述、处理进度和处理结果。",
        "POST /api/feedback/page":
            "按条件分页查询反馈单列表，支持按问题类型、工单号、时间段等筛选。",
        "DELETE /api/feedback/delete/{id}":
            "删除指定反馈单记录。",
        "GET /api/feedback/generate-code":
            "生成反馈单的唯一编号，用于反馈单的标识和跟踪。",
        "POST /api/feedback-solve":
            "根据反馈单创建问题工单（售后/处理工单），记录物料信息、责任人、"
            "生产订单号等，用于反馈问题的跟踪解决。",
        "POST /api/feedback-solve/update":
            "更新问题工单信息，包括处理状态、责任人、处理结果等。",
        "POST /api/feedback-solve/page":
            "按条件分页查询问题工单列表，支持按客户、工单号、状态等多维度筛选。",
        "POST /api/feedback-solve/delete/{id}":
            "删除指定问题工单。",
        "GET /api/feedback-solve/detail/{id}":
            "获取问题工单的完整详情信息。",
        "PUT /api/c/feedback/create":
            "客户H5端创建反馈，客户通过手机端提交问题反馈，无需登录系统即可操作。",
        "POST /api/c/feedback/update":
            "客户H5端修改已提交的反馈内容。",
        "GET /api/c/feedback/list":
            "客户H5端分页查询自己的反馈记录列表。",
        "GET /api/c/order/list":
            "客户H5端分页查询自己的销售订单列表。",
        "GET /api/c/order/detail":
            "客户H5端查看某个销售订单的详细信息。",
        "GET /api/c/contact-info":
            "客户H5端查看管理员联系方式，方便客户在有问题时联系管理人员。",
        "POST /api/admin/init-inverted-index":
            "初始化倒排索引，为所有已有客户数据建立全文搜索索引，"
            "首次启用搜索功能或数据修复时使用。",
        "POST /api/admin/rebuild-customer-index/{customerId}":
            "为指定客户重建倒排索引，用于修复单个客户在搜索中的数据问题。",
        "GET /api/admin/inverted-index-status":
            "检查倒排索引是否已初始化并包含有效数据。",
        "GET /api/admin/inverted-index-stats":
            "查看倒排索引的统计信息，包括已索引客户数、词项总数等。",
        "POST /api/file/request-upload":
            "请求文件上传凭证，返回Minio预签名上传URL，客户端可直接上传文件，"
            "支持批量上传和多目录管理。",
        "GET /api/file/request-delete":
            "请求文件删除凭证，返回Minio预签名删除URL，用于删除已上传的文件。",
        "GET /api/report/sales-trend":
            "查询所有类型的销售趋势数据，包括销售额和订单数的时间序列。",
        "GET /api/report/sales-statistics":
            "查询指定类型的销售统计数据（本月/上月/本年/去年/自定义），含趋势数据。",
        "GET /api/report/summary/current-month":
            "查询本月销售汇总数据，包括总销售额、订单数和签单客户数。",
        "GET /api/report/summary/last-month":
            "查询上月销售汇总数据。",
        "GET /api/report/summary/current-year":
            "查询本年销售汇总数据。",
        "GET /api/report/summary/last-year":
            "查询去年销售汇总数据。",
        "GET /api/report/rank/current-month":
            "查询本月客户销售额排名，支持限制返回数量。",
        "GET /api/report/rank/last-month":
            "查询上月客户销售额排名。",
        "GET /api/report/customer-rank":
            "查询自定义时间范围内的客户销售排名。",
        "GET /api/report/customer-rfm/latest":
            "查询最新的客户RFM分析报表，包括最近消费时间、消费频率和消费金额三个维度。",
        "GET /api/report/customer-status/latest":
            "查询最新的客户状态统计报表，包括客户总数、潜在客户、成交客户分布等。",
        "GET /api/report/daily-stats":
            "按日查询销售统计数据，支持按时间范围和客户筛选。",
        "GET /api/report/monthly-stats":
            "按月查询销售统计数据，支持按年月和客户筛选。",
        "POST /api/report/calculate-daily":
            "手动触发日销售统计的计算和更新。",
        "POST /api/report/calculate-order-rank":
            "手动触发客户销售排名统计的计算和更新。",
        "POST /api/report/calculate-rfm":
            "手动触发RFM报表统计的计算和更新。",
        "POST /api/report/calculate-summaries":
            "手动触发销售汇总统计的计算和更新。",
        "GET /api/requirement-report/summary":
            "获取需求分析看板的汇总统计数据，包括需求总数、赢单率、转化率等关键指标。",
        "GET /api/requirement-report/stage-funnel":
            "获取各销售阶段的需求数量漏斗分析，展示从线索到赢单的转化路径。",
        "GET /api/requirement-report/region-distribution":
            "获取需求按区域分布的统计分析，包括各区域的需求数量和金额。",
        "GET /api/requirement-report/monthly-amount-trend":
            "获取需求金额的月度趋势数据，用于分析需求变化趋势。",
        "GET /api/requirement-report/won-amount-trend":
            "获取赢单金额的月度趋势数据，用于分析赢单变化趋势。",
        "GET /api/requirement-report/loss-reason-analysis":
            "获取输单原因分析数据，统计各输单原因的数量和占比。",
        "GET /api/requirement-report/customer-manager-stats":
            "统计各客户经理的需求跟踪数据，包括商机数、赢单数、转化率等。",
        "GET /api/requirement-report/conversion-trend":
            "获取赢单转化率的月度趋势数据。",
        "GET /api/requirement-report/conversion-dashboard":
            "获取需求转化看板数据，展示客户经理维度的转化统计。",
        "GET /api/requirement-report/amount-distribution":
            "获取需求金额在各销售阶段的分布情况。",
        "POST /api/requirement-report/calculate-monthly":
            "手动触发需求月度统计数据的计算和更新。",
    }

    if key in overrides:
        return overrides[key]

    # ── Pattern-based generation ──────────────────────────────
    # Order create
    if summary.startswith("创建") and ("订单" in summary or "需求" in summary):
        thing = summary[2:]
        return f"用于创建新的{thing}记录，录入相关业务信息。"
    if summary.startswith("编辑") or summary.startswith("更新") or summary.startswith("修改"):
        thing = summary[2:] if len(summary) > 2 else ""
        if "订单" in summary or "需求" in summary:
            return f"用于编辑修改已有的{thing}信息，更新相关字段。"
    if summary.startswith("删除"):
        thing = summary[2:]
        return f"用于删除指定的{thing}记录。"
    if summary.startswith("分页查询"):
        thing = summary[4:]
        return f"按条件分页查询{thing}列表，支持多维度筛选，用于列表展示和搜索。"
    if summary.startswith("获取") or "详情" in summary:
        return f"根据ID获取{module}中指定记录的完整详情信息。"
    if "手动触发" in summary:
        action = summary.replace("手动触发", "")
        return f"手动触发{action}的实时计算和更新，用于数据同步和报表更新。"

    # Fallback: use the JSON description if available, else the summary
    return ""

def convert(input_path, output_path):
    data = load_json(input_path)

    # Extract service name from JSON (info.title) or filename
    info_title = data.get("info", {}).get("title", "")
    service_name = info_title if info_title else "Unknown Service"

    lines = []
    lines.append(f"# 服务：{service_name}\n")

    # Group paths by tags for organization
    paths = data.get('paths', {})

    # Collect all endpoints
    endpoints = []
    for path, methods in paths.items():
        for method, details in methods.items():
            method_upper = method.upper()
            tags = details.get('tags', [])
            module = tags[0] if tags else '未分类'
            summary = details.get('summary', '')
            description = details.get('description', '')
            parameters = details.get('parameters', [])
            request_body = details.get('requestBody', {})
            responses = details.get('responses', {})

            endpoints.append({
                'path': path,
                'method': method_upper,
                'module': module,
                'summary': summary,
                'description': description,
                'parameters': parameters,
                'request_body': request_body,
                'responses': responses,
            })

    # Sort by module then path
    endpoints.sort(key=lambda e: (e['module'], e['path']))

    for ep in endpoints:
        lines.append(f"## 接口：{ep['summary']}\n")
        lines.append(f"- **路径：** {ep['method']} {ep['path']}")
        lines.append(f"- **所属模块：** {ep['module']}")
        lines.append(f"- **HTTP 方法：** {ep['method']}\n")

        # Description
        enriched = enrich_description(ep['summary'], ep['module'], ep['path'], ep['method'])
        desc_text = enriched or ep['description'] or ep['summary']
        lines.append(f"**功能说明：**")
        lines.append(f"{desc_text}\n")

        # Request parameters
        lines.append(f"**请求参数：**")
        has_params = False

        # Path/query parameters
        for param in ep['parameters']:
            has_params = True
            pname = param.get('name', '')
            pin = param.get('in', '')
            preq = param.get('required', False)
            pschema = param.get('schema', {})
            ptype = pschema.get('type', 'string')
            pdesc = pschema.get('description', '')
            pdefault = pschema.get('default')

            loc_map = {'query': '（查询参数）', 'path': '（路径参数）', 'header': '（请求头）'}
            loc_str = loc_map.get(pin, f'（{pin}）')

            if pdefault is not None:
                lines.append(f"- {pname} {loc_str}: {pdesc}（类型: {ptype}，{fmt_required(preq)}，默认: {pdefault}）")
            else:
                lines.append(f"- {pname} {loc_str}: {pdesc}（类型: {ptype}，{fmt_required(preq)}）")

        # Request body
        if ep['request_body']:
            content = ep['request_body'].get('content', {})
            if 'application/json' in content:
                schema = content['application/json'].get('schema', {})
                req_required = ep['request_body'].get('required', False)

                if schema:
                    props = resolve_properties(schema, data)
                    if props:
                        has_params = True
                        for pname, ptype, pdesc, preq in props:
                            if pname.startswith('  '):
                                lines.append(f"- {pname}: {pdesc}（类型: {ptype}）")
                            else:
                                lines.append(f"- {pname}: {pdesc}（类型: {ptype}，{fmt_required(preq)}）")
                    else:
                        # Just mention the schema name
                        if '$ref' in schema:
                            ref_name = schema['$ref'].split('/')[-1]
                            has_params = True
                            lines.append(f"- request body: {ref_name}（请求体，{fmt_required(req_required)}）")

        if not has_params:
            lines.append("- 无")

        lines.append("")

        # Response fields
        lines.append(f"**返回字段：**")
        has_response = False

        for status_code, resp in ep['responses'].items():
            content = resp.get('content', {})
            if '*/*' in content or 'application/json' in content:
                media = content.get('*/*') or content.get('application/json', {})
                schema = media.get('schema', {})

                if schema:
                    # resolve_properties now recursively expands nested objects,
                    # so every leaf field is visible in the flat list.
                    props = resolve_properties(schema, data)
                    if props:
                        has_response = True
                        for pname, ptype, pdesc, preq in props:
                            lines.append(f"- {pname}: {pdesc}（类型: {ptype}）")
                    elif '$ref' in schema:
                        has_response = True
                        ref_name = schema['$ref'].split('/')[-1]
                        lines.append(f"- {ref_name}: 响应包装")
                    else:
                        has_response = True
                        lines.append(f"- 响应状态码 {status_code}")

        if not has_response:
            lines.append("- 无")

        lines.append("")

        # Example questions (generated from summary)
        lines.append(f"**适用问题示例：**")
        examples = generate_examples(ep['summary'], ep['module'], enriched or ep.get('description', ''))
        for ex in examples:
            lines.append(f"- \"{ex}\"")

        lines.append("")

    with open(output_path, 'w') as f:
        f.write('\n'.join(lines))

    print(f"Done! Output written to {output_path}")


def main():
    base_dir = Path(__file__).resolve().parent.parent
    services = [
        ('crm', 'crm-service-api-docs.json', 'crm-service-api-docs.md'),
        ('mes', 'mes-api-docs.json', 'mes-api-docs.md'),
        ('wms', 'wms-service-api-docs.json', 'wms-service-api-docs.md'),
        ('qms', 'qms-service-api-docs.json', 'qms-service-api-docs.md'),
        ('srm', 'srm-service-api-docs.json', 'srm-service-api-docs.md'),
    ]
    for service_dir, json_file, md_file in services:
        input_path = base_dir / 'api-docs' / json_file
        output_path = base_dir / 'agent-server' / 'src' / 'main' / 'resources' / 'api-docs' / service_dir / md_file
        convert(str(input_path), str(output_path))


def generate_examples(summary, module, description=""):
    """Generate diverse natural-language example questions based on the API summary, module, and description.

    The key insight: embedding models match user questions against these examples, not against
    technical descriptions. So examples must use varied, conversational phrasings that real
    users would actually type.
    """
    examples = []

    def add(*qs):
        for q in qs:
            if q not in examples:
                examples.append(q)

    # ── Extract key entities from summary and description ──
    # e.g. "查询最新客户状态统计报表" -> entities like 客户, 统计, 报表
    # e.g. "包括客户总数、潜在客户、成交客户分布" -> count-related terms

    desc_has_count = any(w in description for w in ['总数', '数量', '统计', '汇总', '排名', '分布', '占比'])
    desc_has_status = any(w in summary + description for w in ['状态', '进度'])
    desc_has_date = any(w in summary + description for w in ['日期', '时间', '趋势', '月度', '年度'])

    # ── Pattern 1: CRUD ──
    if summary.startswith('创建') or summary.startswith('新增'):
        thing = summary[2:] if summary.startswith('创建') else summary[2:]
        add(f"新建一个{thing}", f"新增{thing}记录", f"创建一个新的{thing}", f"帮我添加{thing}")
    elif summary.startswith('编辑') or summary.startswith('更新') or summary.startswith('修改'):
        for prefix in ['编辑', '更新', '修改']:
            if prefix in summary:
                _, thing = summary.split(prefix, 1)
                break
        else:
            thing = summary
        add(f"修改{thing}信息", f"更新{thing}数据", f"编辑{thing}")
    elif summary.startswith('删除') or summary.startswith('移除'):
        thing = summary[2:]
        add(f"删除{thing}", f"移除某个{thing}")

    # ── Pattern 2: Query / List / Page ──
    elif any(w in summary for w in ['分页查询', '查询', '列表', '搜索']):
        thing = summary
        for w in ['分页查询', '查询', '搜索']:
            if w in thing:
                thing = thing.split(w, 1)[1] if w in thing else thing
        add(f"查询{thing}", f"查看{thing}列表", f"有哪些{thing}？", f"列出{thing}")

        # Add count/quantity variants for list-type APIs
        if desc_has_count or '统计' in summary or '汇总' in summary:
            # Extract what's being counted
            if '客户' in summary + description: add("有多少个客户？", "当前客户数量", "客户总数是多少？")
            if '订单' in summary + description: add("有多少个订单？", "当前订单数量", "订单总数是多少？")
            if '工单' in summary + description: add("有多少个工单？", "当前工单数量")
            if '产品' in summary + description: add("有多少个产品？", "产品数量")
            if '物料' in summary + description: add("有多少物料？", "物料数量")

        if desc_has_status: add("哪些{thing}还未完成？", f"{thing}进展如何？")
        if desc_has_date: add("最近一周的{thing}", f"本月{thing}情况")

    # ── Pattern 3: Detail ──
    elif '详情' in summary or 'detail' in summary.lower():
        thing = summary.replace('详情', '').replace('获取', '').replace('查看', '').replace('销售订单', '订单')
        add(f"查看{thing}的详细信息", f"{thing}详情", f"{thing}的具体情况")

    # ── Pattern 4: Statistics / Reports ──
    elif '报表' in module or '统计' in summary or '报表' in summary or '看板' in summary:
        add(f"查看{summary}")
        if '客户' in summary + description:
            add("有多少个客户？", "当前客户数量", "客户总数", "客户情况怎么样？")
        if '销售' in summary + description:
            add("销售额怎么样？", "最近销售情况如何？", "销售额有多少？")
        if '需求' in summary + description:
            add("需求情况怎么样？", "有多少需求？", "需求转化如何？")
        if '汇总' in summary: add("当前汇总数据", "最新的汇总统计")
        if '排名' in summary: add("排名情况", "哪些排在前面？")
        if '趋势' in summary: add("最近趋势怎么样？", "变化趋势如何？")
        if '分布' in summary: add("分布情况怎么样？", "各区域的分布")
        if '漏斗' in summary: add("各阶段的转化情况", "哪个阶段需求最多？")
        if '转化' in summary: add("转化率是多少？", "赢单率怎么样？")
        if '状态' in summary + description: add("客户状态分布", "当前状态统计")

    # ── Pattern 5: Get / View ──
    elif summary.startswith('获取') or summary.startswith('查看') or summary.startswith('检查'):
        thing = summary
        for w in ['获取', '查看', '检查']:
            thing = thing.replace(w, '', 1) if thing.startswith(w) else thing
        add(f"查看{thing}", f"{thing}是什么？")

    # ── Pattern 6: Status operations (approve, reject, publish, etc.) ──
    elif '审核' in summary:
        if '通过' in summary: add("审核通过", "通过审核", "审批通过")
        elif '否决' in summary: add("审核否决", "驳回订单", "审核不通过")
    elif '发布' in summary: add("发布到生产", "提交生产")
    elif '开始' in summary: add("开始生产", "启动生产")
    elif '完成' in summary: add("完成生产", "生产完成")
    elif '发货' in summary: add("确认发货", "发货出库")
    elif '签收' in summary: add("确认签收", "客户签收")
    elif '终止' in summary: add("终止订单", "取消订单")
    elif '生成' in summary and '编号' in summary: add("生成编号", "获取新编号")

    # ── Pattern 7: Feedback / Issues ──
    elif '反馈' in module:
        add(f"{summary}操作", f"反馈处理")
    elif '工单' in module:
        add(f"查看问题工单", f"工单处理情况", f"有哪些问题工单？")

    # ── Pattern 8: QR code ──
    elif '二维码' in module:
        add("生成分享二维码", "创建客户分享链接", "客户扫码怎么弄？")

    # ── Fallback ──
    if len(examples) < 2:
        add(f"怎么{summary}？", f"{summary}怎么做？", f"帮我{summary}")

    return examples[:6]

if __name__ == '__main__':
    main()
