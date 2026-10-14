# 单建筑结构化图纸与历史档案 RAG 设计

日期：2026-10-14  
状态：设计已确认

## 1 背景

维保平台需要让用户通过智能大模型查询单栋建筑的图纸和历史档案。图纸不能按普通文档切块处理，因为一栋建筑的图纸具有明确的目录、专业、楼层、图号、页面、区域、OCR 层和识别 Tag，并且区域之间存在递归父子关系和业务引用。目录、图纸区域、OCR 结果及关联关系还需要持续新增、编辑、移动、审核和追溯。

历史档案同样需要进入建筑级 RAG，但其用户可见结构保持单层。每份历史档案作为一个节点归档，分页、OCR、表格和实体抽取作为档案版本内部的证据记录。

本设计在 Agile Agent 现有身份、能力路由、工具调用、对话编排和知识库基础上，增加单建筑结构化 RAG 子系统。该子系统负责权威数据、节点版本、目录关系、OCR 证据、检索索引和原子发布；Agile Agent 只负责理解问题、调用受控工具和基于证据生成回答。

## 2 目标

- 为每栋建筑建立独立的 `BuildingRagSpace`。
- 图纸按可编辑、多层嵌套的结构化知识树归档。
- 每个图纸区域保存归一化坐标、区域截图、识别提示、OCR 层、识别 Tag 和子节点引用。
- 内容节点只有一个规范结构位置，同时可以被任意多个目录引用。
- 历史档案使用单层档案节点，内部保存分页和 OCR 证据。
- 所有可编辑节点采用不可变版本。
- 多个节点变更通过变更集聚合，并以整栋建筑发布版本原子上线。
- RAG 查询先使用目录和字段收敛范围，再检索 OCR、Tag 和语义内容，随后沿节点关系补充上下文。
- 所有回答可以定位到图名、图号、页码、区域、坐标、档案号和节点版本。
- 权限在检索前过滤，引用关系不得扩大用户的数据访问范围。

## 3 非目标

- 不允许大模型直接执行任意 SQL。
- 不由大模型直接修改节点、审核 OCR 或发布建筑版本。
- 不在 Agile Agent 核心模块中实现图纸编辑器。
- 不把搜索引擎作为权威数据源。
- 不要求第一阶段自动理解全部 CAD 几何和拓扑语义。
- 不将历史档案设计成可编辑的多层目录树。

## 4 总体架构

```text
维保平台前端
  ├── 建筑与图纸编辑后台
  ├── OCR审核后台
  └── 智能问答界面
          │
          ▼
Agile Agent
  ├── 身份与会话
  ├── Orchestrator能力路由
  └── Heritage Building RAG Adapter
          │
          ▼
heritage-rag-domain
  ├── 建筑RAG空间
  ├── 节点与不可变版本
  ├── 父子关系与引用
  ├── 变更集与审核
  ├── 建筑发布版本
  └── 项目和建筑权限
          │
          ├── MySQL 权威元数据
          ├── 对象存储 原图 缩略图 区域截图
          └── heritage-rag-index
                ├── 结构化字段索引
                ├── OCR与Tag全文索引
                ├── 向量索引
                └── 图关系扩展
```

建议将新增能力拆成三个模块：

- `heritage-rag-domain`：节点、版本、引用、变更集、发布和权限。
- `heritage-rag-index`：索引构建、混合检索、关系扩展和索引重建。
- `heritage-agent-adapter`：向 Agile Agent 暴露只读、受控的 RAG 工具。

## 5 建筑 RAG 空间

每栋建筑对应一个 `BuildingRagSpace`：

```text
BuildingRagSpace
  ├── buildingId
  ├── currentReleaseId
  ├── rootNodeId
  ├── permissions
  └── releaseHistory
```

普通问答始终读取 `currentReleaseId`。编辑人员可以在明确标记的预览模式下查询草稿，但草稿证据不得混入线上答案。

`currentReleaseId` 是线上版本的唯一发布指针。搜索索引、对象存储和节点数据必须在切换该指针前准备完成。

## 6 节点模型

### 6.1 稳定节点身份

`rag_node` 表示逻辑节点，节点 ID 永久稳定：

```json
{
  "id": "node-drawing-a101",
  "buildingId": "building-001",
  "nodeType": "DRAWING",
  "createdAt": "2026-10-14T10:00:00+08:00",
  "deleted": false
}
```

节点本身不保存可变业务内容。所有标题、元数据、坐标、识别结果和引用都由不可变版本承载。

### 6.2 不可变节点版本

`rag_node_revision` 保存节点一次完整、不可变的状态：

```json
{
  "id": "revision-005",
  "nodeId": "node-drawing-a101",
  "version": 5,
  "title": "二层平面图",
  "payload": {
    "drawingType": "平面",
    "discipline": "建筑",
    "drawingNo": "A-101",
    "floor": "二层",
    "scale": "1:100",
    "revisionCode": "C",
    "issuedAt": "2018-06-20",
    "designer": "某设计院"
  },
  "previousRevisionId": "revision-004",
  "changeSetId": "change-120",
  "createdBy": "user-001",
  "createdAt": "2026-10-14T10:00:00+08:00"
}
```

旧版本永远不允许覆盖更新。编辑、移动、OCR 重识别、人工校订、权限调整和引用调整都创建新版本。

### 6.3 节点类型

节点分为三类：

| 类别 | 节点类型 |
| --- | --- |
| 容器 | `BUILDING_ROOT`、`DIRECTORY`、`DRAWING_REPOSITORY`、`HISTORICAL_ARCHIVES` |
| 内容 | `BUILDING_INFO`、`PROJECT`、`PLACE`、`NATURAL_BUILDING`、`DRAWING`、`DRAWING_PAGE`、`DRAWING_REGION`、`ASSET`、`ARCHIVE_DOCUMENT` |
| 识别 | `OCR_LAYER`、`OCR_TAG`、`MANUAL_TAG` |

节点类型由 Schema Registry 管理。每种类型定义允许的 payload 字段、必填字段和允许的父子类型。目录名称和目录层级由管理员自由维护，不需要预定义所有目录实例。

## 7 父子关系与引用关系

### 7.1 结构父子关系

`CHILD` 表示规范结构关系：

- 除根节点外，每个节点最多有一个结构父节点。
- 结构关系决定规范路径和默认权限继承。
- 结构关系必须属于同一栋建筑和同一发布快照。
- 结构关系禁止成环。
- 移动节点会改变结构关系并产生新版本。

规范的图纸内容树为：

```text
BUILDING_ROOT
└── DRAWING_REPOSITORY
    └── DRAWING
        ├── DRAWING_PAGE
        │   ├── DRAWING_REGION
        │   │   ├── OCR_LAYER
        │   │   │   ├── OCR_TAG
        │   │   │   └── OCR_TAG
        │   │   └── DRAWING_REGION
        │   └── DRAWING_REGION
        └── ASSET
```

### 7.2 目录引用关系

`DIRECTORY_ENTRY` 或通用 `REF` 表示目录展示和业务关联：

- 一个内容节点可以被多个目录引用。
- 引用不改变内容节点的规范路径。
- 引用不复制内容。
- 引用不扩大访问权限。
- 删除目录不删除被引用内容。

同一张 `A-101 二层平面图` 可以同时被以下目录引用：

```text
图纸类型 / 平面
专业 / 建筑
楼层 / 二层
版本 / 2018修缮版
```

### 7.3 引用数据

引用属于源节点版本：

```json
{
  "sourceRevisionId": "revision-005",
  "relation": "CHILD",
  "targetNodeId": "node-page-001",
  "targetRevisionPolicy": "RELEASE",
  "sortOrder": 10
}
```

`targetRevisionPolicy: RELEASE` 表示目标版本由查询使用的建筑发布版本决定。正式发布数据不允许使用“任意最新版本”语义。

支持的主要关系包括：

- `CHILD`
- `DIRECTORY_ENTRY`
- `CLASSIFIED_AS`
- `LOCATED_IN`
- `EXTRACTED_FROM`
- `REFERS_TO`
- `RELATED_TO`
- `SUPPORTED_BY`
- `SUPERSEDES`
- `EXTERNAL_REF`

### 7.4 结构校验

发布前必须验证：

- 根节点没有父节点。
- 一个节点不能成为自身的父节点。
- 新父节点不能位于当前节点的后代中。
- 结构深度不超过配置上限，默认 32 层。
- 父子节点类型符合类型矩阵。
- 跨建筑只能使用 `EXTERNAL_REF`，不能使用 `CHILD`。
- 引用目标在目标发布版本中存在。
- RAG 遍历引用时限制最大深度、节点总数并维护已访问集合。

## 8 图纸结构

### 8.1 顶层入口

单栋建筑可以提供以下可编辑目录视图：

- 建筑基本信息：项目、处、自然幢。
- 图纸类型：总平面、平面、立面、剖面、节点、设备等。
- 专业：建筑、结构、机电、消防等。
- 楼层。
- 版本和出图日期。
- 设计单位。

这些目录只组织和引用规范内容节点。图名、图号、比例、版本、出图日期、图签文字和设计单位同时作为图纸节点的结构化字段参与检索。

### 8.2 图纸区域

`DRAWING_REGION` 支持递归嵌套。区域保存：

```json
{
  "bbox": {
    "x": 0.125,
    "y": 0.208,
    "width": 0.31,
    "height": 0.18,
    "coordinateSystem": "NORMALIZED"
  },
  "pageNodeId": "node-page-001",
  "cropUrl": "oss://bucket/regions/region-123.png",
  "thumbnailUrl": "oss://bucket/regions/region-123-thumb.png",
  "prompt": "识别该区域内的轴号 构件名称 尺寸和材料标注",
  "regionType": "NODE_DETAIL"
}
```

坐标统一采用相对于页面宽高的 `0` 到 `1` 归一化坐标，同时保存源图像像素宽高、页旋转角度和文件校验值。

### 8.3 OCR 层与 Tag

OCR Tag 需要独立编辑、审核、引用和版本追踪，因此建模为节点：

```text
DRAWING_PAGE
└── DRAWING_REGION
    ├── OCR_LAYER
    │   ├── OCR_TAG 文字标注
    │   ├── OCR_TAG 构件标注
    │   └── OCR_TAG 尺寸标注
    └── DRAWING_REGION 子区域
        └── OCR_LAYER
```

Tag 版本保存：

```json
{
  "tagType": "COMPONENT_NAME",
  "rawText": "钢筋混凝土柱",
  "normalizedValue": "钢筋混凝土柱",
  "bbox": {
    "x": 0.21,
    "y": 0.34,
    "width": 0.08,
    "height": 0.025
  },
  "cropUrl": "oss://bucket/tags/tag-456.png",
  "prompt": "识别构件名称",
  "recognitionSource": "TRAINED_MODEL",
  "modelVersion": "drawing-ocr-3.2",
  "confidence": 0.94,
  "reviewStatus": "APPROVED"
}
```

识别结果还必须记录模型名称、模型版本、prompt 版本、源文件校验值、执行时间和人工审核信息。原图校验值变化后，旧识别结果自动标记为待重新确认。

## 9 历史档案

### 9.1 单层可见结构

历史档案保持单层：

```text
HISTORICAL_ARCHIVES
├── ARCHIVE_DOCUMENT 上海音乐厅建设档案
├── ARCHIVE_DOCUMENT 1959年修缮记录
├── ARCHIVE_DOCUMENT 1985年屋面改造记录
└── ARCHIVE_DOCUMENT 2018年保护修缮方案
```

每份档案是一个可排序、可引用、可版本管理的节点。档案节点保存档案号、题名、档案类型、时间范围、形成单位、作者、来源、语言、页数、人物、机构、事件、关键词、原件地址、缩略图、OCR 状态、审核状态和权限。

### 9.2 内部证据

分页、段落、表格、手写批注和实体抽取结果作为档案版本内部的证据记录，不作为用户可移动的树节点：

```json
{
  "archiveNodeId": "archive-1959-021",
  "archiveRevisionId": "revision-006",
  "evidenceId": "evidence-023",
  "pageNo": 12,
  "evidenceType": "PARAGRAPH",
  "text": "本次修缮对屋面渗漏部位进行了...",
  "bbox": [0.12, 0.31, 0.76, 0.18],
  "cropUrl": "oss://bucket/archive/evidence-023.png",
  "ocrConfidence": 0.91,
  "entities": {
    "events": ["屋面修缮"],
    "components": ["屋面"],
    "problems": ["渗漏"]
  },
  "reviewStatus": "APPROVED"
}
```

档案元数据、原件、OCR、分页、证据切分或实体抽取发生变化时，创建整个档案节点的新版本。

### 9.3 与图纸关联

档案节点可以通过 `REFERS_TO`、`RELATED_TO` 或 `SUPPORTED_BY` 指向图纸、图纸区域、OCR Tag 和自然幢。引用具体档案页时，同时保存 `targetEvidenceId`。

## 10 双层版本机制

### 10.1 节点版本

每次编辑生成新的不可变节点版本。移动操作至少会生成：

- 被移动节点的新版本。
- 原父节点的新版本，用于移除 `CHILD`。
- 新父节点的新版本，用于增加 `CHILD`。

重新分类只修改目录引用，不改变内容节点的规范父节点。

### 10.2 变更集

一个变更集记录：

- 基础建筑发布版本。
- 新增、修改、移动和逻辑删除的节点。
- 新增、修改和删除的引用。
- OCR 或模型重识别结果。
- 创建人、审核人、修改原因和时间。
- 校验和发布状态。

变更集状态：

```text
OPEN → VALIDATING → READY_TO_PUBLISH → PUBLISHING → PUBLISHED
```

失败状态包括 `VALIDATION_FAILED` 和 `PUBLISH_FAILED`。

### 10.3 建筑发布版本

`BuildingRelease` 是一次完整、一致的建筑快照，保存稳定节点 ID 到具体节点版本 ID 的映射，以及该版本使用的引用集合、档案证据和索引标识。

```text
BuildingRelease R18
├── 建筑信息节点版本映射
├── 图纸节点版本映射
├── OCR Tag节点版本映射
├── 历史档案节点版本映射
└── 关系和引用版本映射
```

RAG 查询必须绑定一个明确的 `releaseId`。历史问题可以指定旧发布版本。

## 11 结构化 RAG 查询

### 11.1 查询上下文

每次查询首先锁定：

```json
{
  "buildingId": "building-001",
  "releaseId": "release-018",
  "identityId": "user-001",
  "allowedScopes": ["project-01"],
  "query": "二层平面图中有哪些钢筋混凝土柱"
}
```

同一次查询不能混用多个发布版本的节点。

### 11.2 查询计划

自然语言先转换为结构化查询计划：

```json
{
  "directoryFilters": [
    {"dimension": "drawingType", "value": "平面"},
    {"dimension": "discipline", "value": "建筑"}
  ],
  "metadataFilters": {
    "floor": "二层"
  },
  "targetNodeTypes": ["DRAWING", "DRAWING_REGION", "OCR_TAG"],
  "textQuery": "钢筋混凝土柱",
  "tagTypes": ["COMPONENT_NAME", "MATERIAL"],
  "traversal": {
    "descendantDepth": 5,
    "refTypes": ["CHILD", "REFERS_TO"]
  }
}
```

### 11.3 四级检索

1. **目录和字段定位**：按建筑、图纸类型、专业、楼层、图号、版本、日期、设计单位、节点类型和审核状态精确过滤。
2. **OCR 与 Tag 检索**：依次使用人工审核 Tag、规范化字段、OCR 关键词、BM25 全文和向量相似度。
3. **图结构扩展**：向上取得图纸、页和建筑信息；向下取得子区域和 Tag；沿允许的引用补充图纸或档案证据。
4. **图像复核**：仅当结构化信息和 OCR 无法回答空间或视觉问题时，读取 bbox 对应区域截图并调用视觉模型。

结构化精确结果优先于语义相似结果。区域保存的 prompt 是识别指令，不是事实证据。

### 11.4 引用格式

图纸答案至少引用：

- 建筑和发布版本。
- 图名、图号和图纸版本。
- 页码。
- 区域节点和 bbox。
- OCR Tag 或视觉复核来源。
- 节点版本。

历史档案答案至少引用：

- 档案号和题名。
- 档案节点版本。
- 页码和证据 ID。
- 原文片段或区域截图。

模型推断必须与原始事实分开标注。

## 12 检索索引

MySQL 保存权威节点、版本、关系、变更集和发布数据。对象存储保存原图、原始档案、缩略图和裁剪图。搜索引擎保存可重建的派生索引。

每个索引文档对应一个发布版本内的节点版本或档案证据，并包含：

- `buildingId`
- `releaseId`
- `nodeId`
- `revisionId`
- `nodeType`
- `ancestorIds`
- `directoryRefs`
- 图纸或档案结构化字段
- OCR 和规范化文本
- Tag 类型和值
- bbox 和受保护的资源标识
- 审核状态
- ACL 范围
- embedding

索引按照发布版本构建。线上查询先从数据库读取 `currentReleaseId`，再访问对应的已完成索引。索引可以从 MySQL 和对象存储完整重建。

## 13 权限

- 项目管理者只能检索其管辖项目内的建筑。
- 行业管理者可以按授权执行跨项目检索。
- 权限过滤发生在候选召回之前。
- 结构子节点默认继承父节点权限。
- 子节点可以缩小权限，普通编辑者不能扩大到父级范围之外。
- 目录引用和业务引用不得扩大目标节点权限。
- 对象存储资源通过后端授权或短时签名地址读取，不能向模型暴露永久公开 URL。
- 跨建筑引用必须同时满足源建筑和目标建筑权限。

## 14 编辑 审核与识别工作流

### 14.1 节点状态

```text
DRAFT → PROCESSING → REVIEW_REQUIRED → APPROVED
```

异常状态：

- `PROCESSING_FAILED`
- `REJECTED`
- `SUPERSEDED`

OCR 和训练模型产生的结果默认需要审核。低置信度结果可以保留，但不能伪装成人工确认结果。

### 14.2 图纸处理

```text
上传原图或CAD文件
→ 生成标准预览图和缩略图
→ 识别图签和图纸元数据
→ 识别页面区域
→ 按区域prompt执行OCR或训练模型
→ 生成OCR层 Tag和子区域
→ 人工校订
→ 节点审核
→ 加入待发布变更集
```

DWG 和 DXF 保留原件，同时转换为用于浏览和识别的稳定格式。转换失败不会破坏已发布版本。

### 14.3 历史档案处理

```text
上传档案
→ 分页和版面分析
→ OCR和表格识别
→ 证据片段切分
→ 人物 时间 事件 地点抽取
→ 人工校订
→ 档案节点审核
→ 加入待发布变更集
```

## 15 原子发布

发布流程：

1. 从变更集创建 `BuildingRelease` 草稿。
2. 固化节点版本映射和引用映射。
3. 验证 Schema、父子类型、循环、深度、引用、资产、bbox、OCR 审核、权限和档案证据。
4. 构建该发布版本的独立搜索索引。
5. 执行索引完整性检查和代表性测试查询。
6. 将发布版本标记为 `READY`。
7. 在数据库事务中原子切换 `building_rag_space.current_release_id`。
8. 新查询使用新版本，已开始的旧查询继续使用其锁定的旧版本。

如果任何准备步骤失败，`current_release_id` 保持不变，线上继续使用旧版本。失败发布保留日志和可诊断信息，修复后可以重新发布。

## 16 Agile Agent 工具

`heritage-agent-adapter` 暴露：

- `browseBuildingTree`：浏览建筑目录和节点结构。
- `searchBuildingNodes`：按目录、字段、OCR、Tag 和语义检索节点。
- `getNodeContext`：读取节点祖先、子节点和允许的引用。
- `inspectDrawingRegion`：读取受权区域截图并执行视觉复核。
- `compareNodeVersions`：比较两个建筑发布版本中的节点变化。
- `listHistoricalArchives`：列出单层历史档案。
- `searchHistoricalArchives`：按字段、全文、实体、时间和事件检索档案。
- `readArchiveEvidence`：读取档案页证据和区域截图。

节点修改、OCR 审核、建立编辑引用和发布版本属于管理后台能力，不暴露给普通问答 Agent。

## 17 并发与冲突

- 变更集记录基础发布版本。
- 节点编辑使用期望版本号进行乐观锁校验。
- 两名编辑者修改同一节点时，后提交者必须基于最新版本合并。
- 不相交节点可以在同一变更集中合并。
- 发布期间可以继续创建新变更集，但新变更集不会进入正在发布的快照。
- 删除使用逻辑删除；旧发布版本仍可恢复被删除节点。

## 18 错误处理与恢复

- OCR 或模型识别失败：节点进入 `PROCESSING_FAILED`，支持幂等重试，已发布版本不受影响。
- 文件上传不完整：不创建可审核版本，临时文件由清理任务回收。
- 关系校验失败：变更集保持可编辑，返回精确的节点和关系错误。
- 索引构建失败：发布版本不进入 `READY`，不切换线上指针。
- 发布指针切换失败：旧版本继续服务。
- 对象存储资产缺失：阻止包含关键资产的版本发布。
- 搜索索引损坏：从权威数据库和对象存储重建指定发布版本。
- 模型超时：返回已有结构化证据或明确告知视觉复核未完成，不编造结果。

## 19 审计与可观测性

记录以下事件：

- 节点版本创建和修改原因。
- 节点移动前后的父节点。
- 引用新增和删除。
- OCR 模型、prompt、输入文件和输出版本。
- 人工审核决定。
- 变更集验证结果。
- 建筑版本发布和回退。
- RAG 查询使用的建筑版本、过滤条件、命中节点、证据和耗时。

日志和指标不得记录访问令牌、永久资源地址或未授权的 OCR 内容。

## 20 测试策略

### 20.1 领域测试

- 节点旧版本不可修改。
- 节点移动正确生成被移动节点、原父节点和新父节点版本。
- 循环父子关系和非法类型关系被拒绝。
- 多目录引用不复制内容。
- 删除目录不删除被引用图纸。
- 逻辑删除不影响历史发布版本恢复。
- 跨建筑引用遵守权限。

### 20.2 OCR 与坐标测试

- bbox 在不同预览分辨率下定位一致。
- 页面旋转后坐标转换正确。
- 源文件变化使旧识别结果进入待确认状态。
- 人工审核结果优先于未经审核的模型结果。
- 模型和 prompt 版本可以完整追溯。

### 20.3 检索测试

- 目录和字段过滤先于全文和向量召回。
- OCR Tag 精确命中优先于语义相似结果。
- 关系扩展不越过深度、节点数和权限限制。
- 草稿和未审核节点不会进入线上结果。
- 图纸和历史档案联合查询使用同一建筑发布版本。
- 答案引用可以定位到原图区域或档案页。

### 20.4 发布测试

- 索引构建失败不会切换当前发布版本。
- 并发查询只能看到完整旧版本或完整新版本。
- 发布指针切换具有事务性。
- 旧发布版本可以查询和恢复。
- 搜索索引可以从权威数据完整重建。

### 20.5 安全测试

- 项目管理者不能检索其他项目建筑。
- 无权限用户不能通过目录引用或反向引用读取受限节点。
- 对象存储资源地址不能绕过服务端授权。
- 模型工具不能修改节点、执行任意 SQL 或扩大查询范围。

## 21 关键设计决策

- 图纸使用多层结构化知识树，历史档案使用单层档案节点。
- 内容节点只有一个规范结构父节点，可以被多个目录引用。
- OCR Tag 建模为可独立编辑、审核和版本管理的节点。
- 历史档案分页和 OCR 是档案版本内部证据，不是可移动目录节点。
- 节点版本不可变，多个变更通过变更集聚合。
- 整栋建筑通过 `BuildingRelease` 原子发布。
- MySQL 和对象存储是权威来源，搜索索引是可重建派生数据。
- RAG 采用结构和字段优先、OCR 与 Tag 次之、关系扩展随后、视觉复核最后的四级策略。
- Agile Agent 只调用受控只读工具，不承担领域编辑和发布职责。
