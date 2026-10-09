# 服务：qms-service APIs document

## 接口：查询分页

- **路径：** GET /api/v1/eight-ds
- **所属模块：** 8D问题管理
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/eight-ds
- **所属模块：** 8D问题管理
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键ID（类型: integer，可选）
- code: 8D编号（类型: string，可选）
- d1ProblemDesc: D1问题描述（类型: string，可选）
- d2Team: D2管理小组（类型: array<string>，可选）
- d3TempAction: D3临时措施（类型: string，可选）
- d4RootCause: D4原因分析（类型: string，可选）
- d5Solution: D5根本解决方案（类型: string，可选）
- d6Execute: D6执行根本解决方案（类型: string，可选）
- d7Prevent: D7预防措施（类型: string，可选）
- d8Reward: D8表彰小组（类型: array<string>，可选）
- status: 状态：D1~D8（类型: string，可选）
- responsible: 负责人（类型: string，可选）
- finishTime: 完成时间（类型: string，可选）
- tenantId: 组织id（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 8D问题管理 DTO（类型: object）
-   data.id: 主键ID（类型: integer）
-   data.code: 8D编号（类型: string）
-   data.d1ProblemDesc: D1问题描述（类型: string）
-   data.d2Team: D2管理小组（类型: array<string>）
-   data.d3TempAction: D3临时措施（类型: string）
-   data.d4RootCause: D4原因分析（类型: string）
-   data.d5Solution: D5根本解决方案（类型: string）
-   data.d6Execute: D6执行根本解决方案（类型: string）
-   data.d7Prevent: D7预防措施（类型: string）
-   data.d8Reward: D8表彰小组（类型: array<string>）
-   data.status: 状态：D1~D8（类型: string）
-   data.responsible: 负责人（类型: string）
-   data.finishTime: 完成时间（类型: string）
-   data.tenantId: 组织id（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/eight-ds/{id}
- **所属模块：** 8D问题管理
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 8D问题管理 DTO（类型: object）
-   data.id: 主键ID（类型: integer）
-   data.code: 8D编号（类型: string）
-   data.d1ProblemDesc: D1问题描述（类型: string）
-   data.d2Team: D2管理小组（类型: array<string>）
-   data.d3TempAction: D3临时措施（类型: string）
-   data.d4RootCause: D4原因分析（类型: string）
-   data.d5Solution: D5根本解决方案（类型: string）
-   data.d6Execute: D6执行根本解决方案（类型: string）
-   data.d7Prevent: D7预防措施（类型: string）
-   data.d8Reward: D8表彰小组（类型: array<string>）
-   data.status: 状态：D1~D8（类型: string）
-   data.responsible: 负责人（类型: string）
-   data.finishTime: 完成时间（类型: string）
-   data.tenantId: 组织id（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/eight-ds/{id}
- **所属模块：** 8D问题管理
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键ID（类型: integer，可选）
- code: 8D编号（类型: string，可选）
- d1ProblemDesc: D1问题描述（类型: string，可选）
- d2Team: D2管理小组（类型: array<string>，可选）
- d3TempAction: D3临时措施（类型: string，可选）
- d4RootCause: D4原因分析（类型: string，可选）
- d5Solution: D5根本解决方案（类型: string，可选）
- d6Execute: D6执行根本解决方案（类型: string，可选）
- d7Prevent: D7预防措施（类型: string，可选）
- d8Reward: D8表彰小组（类型: array<string>，可选）
- status: 状态：D1~D8（类型: string，可选）
- responsible: 负责人（类型: string，可选）
- finishTime: 完成时间（类型: string，可选）
- tenantId: 组织id（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 8D问题管理 DTO（类型: object）
-   data.id: 主键ID（类型: integer）
-   data.code: 8D编号（类型: string）
-   data.d1ProblemDesc: D1问题描述（类型: string）
-   data.d2Team: D2管理小组（类型: array<string>）
-   data.d3TempAction: D3临时措施（类型: string）
-   data.d4RootCause: D4原因分析（类型: string）
-   data.d5Solution: D5根本解决方案（类型: string）
-   data.d6Execute: D6执行根本解决方案（类型: string）
-   data.d7Prevent: D7预防措施（类型: string）
-   data.d8Reward: D8表彰小组（类型: array<string>）
-   data.status: 状态：D1~D8（类型: string）
-   data.responsible: 负责人（类型: string）
-   data.finishTime: 完成时间（类型: string）
-   data.tenantId: 组织id（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/eight-ds/{id}
- **所属模块：** 8D问题管理
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：未开始/检验中任务统计（柱状图）

- **路径：** GET /api/v1/dashboard/in-progress-task-stat
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
未开始/检验中任务统计（柱状图）

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].orderType: 检验类型：IQC/IPQC/FQC/OQC（类型: string）
-   data[].notStart: 未开始数量（类型: integer）
-   data[].inspecting: 检验中数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看未开始/检验中任务统计（柱状图）"
- "怎么未开始/检验中任务统计（柱状图）？"
- "未开始/检验中任务统计（柱状图）怎么做？"
- "帮我未开始/检验中任务统计（柱状图）"

## 接口：本月质检任务完成数排行

- **路径：** GET /api/v1/dashboard/inspector-month-rank
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
本月质检任务完成数排行

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].inspector: 检验员（类型: string）
-   data[].finishedCount: 完成任务数（类型: integer）
-   data[].totalCount: 任务总数（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "完成生产"
- "生产完成"

## 接口：人员任务统计（总数/完成数/完成率）

- **路径：** GET /api/v1/dashboard/inspector-summary
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
人员任务统计（总数/完成数/完成率）

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].inspector: 检验员（类型: string）
-   data[].finishedCount: 完成任务数（类型: integer）
-   data[].totalCount: 任务总数（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看人员任务统计（总数/完成数/完成率）"
- "怎么人员任务统计（总数/完成数/完成率）？"
- "人员任务统计（总数/完成数/完成率）怎么做？"
- "帮我人员任务统计（总数/完成数/完成率）"

## 接口：人员任务统计（未开始/检验中）

- **路径：** GET /api/v1/dashboard/inspector-task-stat
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
人员任务统计（未开始/检验中）

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].inspector: 检验员（类型: string）
-   data[].orderType: 检验类型：IQC/IPQC/FQC/OQC（类型: string）
-   data[].notStart: 未开始数量（类型: integer）
-   data[].inspecting: 检验中数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看人员任务统计（未开始/检验中）"
- "怎么人员任务统计（未开始/检验中）？"
- "人员任务统计（未开始/检验中）怎么做？"
- "帮我人员任务统计（未开始/检验中）"

## 接口：检验员任务完成数排行（最近30天）TOP10

- **路径：** GET /api/v1/dashboard/inspector-top10
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
检验员任务完成数排行（最近30天）TOP10

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].inspector: 检验员（类型: string）
-   data[].finishedCount: 完成任务数（类型: integer）
-   data[].totalCount: 任务总数（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "完成生产"
- "生产完成"

## 接口：物料月度统计（趋势）

- **路径：** GET /api/v1/dashboard/item-monthly-stat
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
物料月度统计（趋势）

**请求参数：**
- startMonth （查询参数）: （类型: string，必填）
- endMonth （查询参数）: （类型: string，必填）
- materialCode （查询参数）: （类型: string，可选）
- materialName （查询参数）: （类型: string，可选）
- pageNum （查询参数）: （类型: integer，可选，默认: 1）
- pageSize （查询参数）: （类型: integer，可选，默认: 10）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查看物料月度统计（趋势）"
- "最近趋势怎么样？"
- "变化趋势如何？"

## 接口：物料月度统计（趋势）导出

- **路径：** GET /api/v1/dashboard/item-monthly-stat/export
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
物料月度统计（趋势）导出

**请求参数：**
- startMonth （查询参数）: （类型: string，必填）
- endMonth （查询参数）: （类型: string，必填）
- materialCode （查询参数）: （类型: string，可选）
- materialName （查询参数）: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看物料月度统计（趋势）导出"
- "最近趋势怎么样？"
- "变化趋势如何？"

## 接口：按月任务统计（IQC/IPQC/FQC/OQC）

- **路径：** GET /api/v1/dashboard/monthly-task-stat
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
按月任务统计（IQC/IPQC/FQC/OQC）

**请求参数：**
- startMonth （查询参数）: （类型: string，必填）
- endMonth （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].month: （类型: string）
-   data[].totalCount: （类型: integer）
-   data[].completedCount: （类型: integer）
-   data[].iqcTotal: （类型: integer）
-   data[].iqcCompleted: （类型: integer）
-   data[].ipqcTotal: （类型: integer）
-   data[].ipqcCompleted: （类型: integer）
-   data[].fqcTotal: （类型: integer）
-   data[].fqcCompleted: （类型: integer）
-   data[].oqcTotal: （类型: integer）
-   data[].oqcCompleted: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看按月任务统计（IQC/IPQC/FQC/OQC）"
- "怎么按月任务统计（IQC/IPQC/FQC/OQC）？"
- "按月任务统计（IQC/IPQC/FQC/OQC）怎么做？"
- "帮我按月任务统计（IQC/IPQC/FQC/OQC）"

## 接口：按月任务统计（IQC/IPQC/FQC/OQC）导出

- **路径：** GET /api/v1/dashboard/monthly-task-stat/export
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
按月任务统计（IQC/IPQC/FQC/OQC）导出

**请求参数：**
- startMonth （查询参数）: （类型: string，必填）
- endMonth （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看按月任务统计（IQC/IPQC/FQC/OQC）导出"
- "怎么按月任务统计（IQC/IPQC/FQC/OQC）导出？"
- "按月任务统计（IQC/IPQC/FQC/OQC）导出怎么做？"
- "帮我按月任务统计（IQC/IPQC/FQC/OQC）导出"

## 接口：待检任务列表

- **路径：** GET /api/v1/dashboard/pending-task-list
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
待检任务列表

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].orderNo: （类型: string）
-   data[].orderType: （类型: string）
-   data[].materialType: （类型: string）
-   data[].materialId: （类型: integer）
-   data[].materialCode: （类型: string）
-   data[].materialName: （类型: string）
-   data[].spec: （类型: string）
-   data[].unit: （类型: string）
-   data[].standardId: （类型: integer）
-   data[].standardName: （类型: string）
-   data[].supplier: （类型: string）
-   data[].customerName: （类型: string）
-   data[].deliveryNo: （类型: string）
-   data[].salesOrderNo: （类型: string）
-   data[].batchNo: （类型: string）
-   data[].workOrderNo: （类型: string）
-   data[].productionOrderNo: （类型: string）
-   data[].processNo: （类型: string）
-   data[].workCenter: （类型: string）
-   data[].station: （类型: string）
-   data[].quantity: （类型: number）
-   data[].status: （类型: string）
-   data[].result: （类型: string）
-   data[].inspector: （类型: string）
-   data[].initiator: （类型: string）
-   data[].requestTime: （类型: string）
-   data[].inspectTime: （类型: string）
-   data[].deliveryTime: （类型: string）
-   data[].incomingTime: （类型: string）
-   data[].initiateTime: （类型: string）
-   data[].finalInspectionMethod: （类型: string）
-   data[].finalSampleQty: （类型: number）
-   data[].finalDefectQty: （类型: number）
-   data[].finalDefectRate: （类型: number）
-   data[].finalResult: （类型: string）
-   data[].finalInspectTime: （类型: string）
-   data[].finalInspector: （类型: string）
-   data[].finalRemark: （类型: string）
-   data[].items: （类型: array<object>）
-     data[].items[].id: （类型: integer）
-     data[].items[].orderId: （类型: integer）
-     data[].items[].itemId: （类型: integer）
-     data[].items[].deviceCode: （类型: string）
-     data[].items[].pointCode: （类型: string）
-     data[].items[].pointName: （类型: string）
-     data[].items[].sl: （类型: number）
-     data[].items[].usl: （类型: number）
-     data[].items[].lsl: （类型: number）
-     data[].items[].positiveTol: （类型: number）
-     data[].items[].negativeTol: （类型: number）
-     data[].items[].inspectValue: （类型: string）
-     data[].items[].result: （类型: string）
-     data[].items[].inspectionItem: （类型: object）
-       data[].items[].inspectionItem.id: （类型: integer）
-       data[].items[].inspectionItem.itemCode: （类型: string）
-       data[].items[].inspectionItem.itemName: （类型: string）
-       data[].items[].inspectionItem.itemCategoryId: （类型: integer）
-       data[].items[].inspectionItem.itemCategoryName: （类型: string）
-       data[].items[].inspectionItem.inspectionType: （类型: string）
-       data[].items[].inspectionItem.inspectionMethod: （类型: string）
-       data[].items[].inspectionItem.toolId: （类型: integer）
-       data[].items[].inspectionItem.toolName: （类型: string）
-       data[].items[].inspectionItem.inspectionStandardText: （类型: string）
-       data[].items[].inspectionItem.unit: （类型: string）
-       data[].items[].inspectionItem.remark: （类型: string）
-       data[].items[].inspectionItem.tenantId: （类型: string）
-       data[].items[].inspectionItem.createBy: （类型: string）
-       data[].items[].inspectionItem.createTime: （类型: string）
-       data[].items[].inspectionItem.updateBy: （类型: string）
-       data[].items[].inspectionItem.updateTime: （类型: string）
-       data[].items[].inspectionItem.isValid: （类型: integer）
-       data[].items[].inspectionItem.points: （类型: array<object>）
-         data[].items[].inspectionItem.points[].id: （类型: integer）
-         data[].items[].inspectionItem.points[].itemId: （类型: integer）
-         data[].items[].inspectionItem.points[].deviceCode: （类型: string）
-         data[].items[].inspectionItem.points[].pointCode: （类型: string）
-         data[].items[].inspectionItem.points[].pointName: （类型: string）
-         data[].items[].inspectionItem.points[].tenantId: （类型: string）
-         data[].items[].inspectionItem.points[].createBy: （类型: string）
-         data[].items[].inspectionItem.points[].createTime: （类型: string）
-         data[].items[].inspectionItem.points[].updateBy: （类型: string）
-         data[].items[].inspectionItem.points[].updateTime: （类型: string）
-         data[].items[].inspectionItem.points[].isValid: （类型: integer）
-     data[].items[].defects: （类型: array<object>）
-       data[].items[].defects[].id: （类型: integer）
-       data[].items[].defects[].orderId: （类型: integer）
-       data[].items[].defects[].itemId: （类型: integer）
-       data[].items[].defects[].defectId: （类型: integer）
-       data[].items[].defects[].defectName: （类型: string）
-       data[].items[].defects[].tenantId: （类型: string）
-       data[].items[].defects[].createBy: （类型: string）
-       data[].items[].defects[].createTime: （类型: string）
-       data[].items[].defects[].updateBy: （类型: string）
-       data[].items[].defects[].updateTime: （类型: string）
-       data[].items[].defects[].isValid: （类型: integer）
-   data[].waiting: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询待检任务列表"
- "查看待检任务列表列表"
- "有哪些待检任务列表？"
- "列出待检任务列表"

## 接口：工单过程质量分析

- **路径：** GET /api/v1/dashboard/quality-analysis
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
工单过程质量分析

**请求参数：**
- workOrderNo （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.material: （类型: object）
-     data.material.materialCode: （类型: string）
-     data.material.materialName: （类型: string）
-   data.currentTrend: （类型: array<object>）
-     data.currentTrend[].processName: （类型: string）
-     data.currentTrend[].defectRate: （类型: number）
-     data.currentTrend[].time: （类型: integer）
-   data.historyTrend: （类型: array<object>）
-     data.historyTrend[].processName: （类型: string）
-     data.historyTrend[].defectRate: （类型: number）
-     data.historyTrend[].time: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "怎么工单过程质量分析？"
- "工单过程质量分析怎么做？"
- "帮我工单过程质量分析"

## 接口：看板汇总指标（工具/体系文件/手册/设备）

- **路径：** GET /api/v1/dashboard/summary
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
看板汇总指标（工具/体系文件/手册/设备）

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.toolCount: （类型: integer）
-   data.systemDocCount: （类型: integer）
-   data.manualCount: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看看板汇总指标（工具/体系文件/手册/设备）"
- "当前汇总数据"
- "最新的汇总统计"

## 接口：供应商月度明细统计（分页）

- **路径：** GET /api/v1/dashboard/supplier-monthly-detail
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
供应商月度明细统计（分页）

**请求参数：**
- month （查询参数）: （类型: string，必填）
- supplier （查询参数）: （类型: string，可选）
- pageNum （查询参数）: （类型: integer，可选，默认: 1）
- pageSize （查询参数）: （类型: integer，可选，默认: 10）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查看供应商月度明细统计（分页）"
- "怎么供应商月度明细统计（分页）？"
- "供应商月度明细统计（分页）怎么做？"
- "帮我供应商月度明细统计（分页）"

## 接口：供应商月度明细统计（分页）

- **路径：** GET /api/v1/dashboard/supplier-monthly-detail/export
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
供应商月度明细统计（分页）

**请求参数：**
- month （查询参数）: （类型: string，必填）
- supplier （查询参数）: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看供应商月度明细统计（分页）"
- "怎么供应商月度明细统计（分页）？"
- "供应商月度明细统计（分页）怎么做？"
- "帮我供应商月度明细统计（分页）"

## 接口：供应商月度质量统计

- **路径：** GET /api/v1/dashboard/supplier-monthly-stat
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
供应商月度质量统计

**请求参数：**
- month （查询参数）: （类型: string，必填）
- supplier （查询参数）: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].month: （类型: string）
-   data[].supplier: （类型: string）
-   data[].materialTotal: （类型: integer）
-   data[].materialNgTotal: （类型: integer）
-   data[].orderTotal: （类型: integer）
-   data[].orderNgTotal: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看供应商月度质量统计"
- "怎么供应商月度质量统计？"
- "供应商月度质量统计怎么做？"
- "帮我供应商月度质量统计"

## 接口：任务统计（饼图）

- **路径：** GET /api/v1/dashboard/task-stat
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
任务统计（饼图）

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].orderType: 检验类型：IQC/IPQC/FQC/OQC（类型: string）
-   data[].num: 数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看任务统计（饼图）"
- "怎么任务统计（饼图）？"
- "任务统计（饼图）怎么做？"
- "帮我任务统计（饼图）"

## 接口：任务汇总（检验中/待检/今日完成）

- **路径：** GET /api/v1/dashboard/task-summary
- **所属模块：** QMS看板
- **HTTP 方法：** GET

**功能说明：**
任务汇总（检验中/待检/今日完成）

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.inProgressCount: （类型: integer）
-   data.pendingCount: （类型: integer）
-   data.todayCompletedCount: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "完成生产"
- "生产完成"

## 接口：查询分页

- **路径：** GET /api/v1/supplier-quality-files
- **所属模块：** 供应商质检文件表
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/supplier-quality-files
- **所属模块：** 供应商质检文件表
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- supplierId: （类型: string，可选）
- supplierName: （类型: string，可选）
- materialType: （类型: string，可选）
- materialTypeName: （类型: string，可选）
- materialId: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- spec: （类型: string，可选）
- batchNo: （类型: string，可选）
- fileName: （类型: string，可选）
- filePath: （类型: string，可选）
- fileType: （类型: string，可选）
- remark: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.supplierId: （类型: string）
-   data.supplierName: （类型: string）
-   data.materialType: （类型: string）
-   data.materialTypeName: （类型: string）
-   data.materialId: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.spec: （类型: string）
-   data.batchNo: （类型: string）
-   data.fileName: （类型: string）
-   data.filePath: （类型: string）
-   data.fileType: （类型: string）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/supplier-quality-files/{id}
- **所属模块：** 供应商质检文件表
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.supplierId: （类型: string）
-   data.supplierName: （类型: string）
-   data.materialType: （类型: string）
-   data.materialTypeName: （类型: string）
-   data.materialId: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.spec: （类型: string）
-   data.batchNo: （类型: string）
-   data.fileName: （类型: string）
-   data.filePath: （类型: string）
-   data.fileType: （类型: string）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/supplier-quality-files/{id}
- **所属模块：** 供应商质检文件表
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- supplierId: （类型: string，可选）
- supplierName: （类型: string，可选）
- materialType: （类型: string，可选）
- materialTypeName: （类型: string，可选）
- materialId: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- spec: （类型: string，可选）
- batchNo: （类型: string，可选）
- fileName: （类型: string，可选）
- filePath: （类型: string，可选）
- fileType: （类型: string，可选）
- remark: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.supplierId: （类型: string）
-   data.supplierName: （类型: string）
-   data.materialType: （类型: string）
-   data.materialTypeName: （类型: string）
-   data.materialId: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.spec: （类型: string）
-   data.batchNo: （类型: string）
-   data.fileName: （类型: string）
-   data.filePath: （类型: string）
-   data.fileType: （类型: string）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/supplier-quality-files/{id}
- **所属模块：** 供应商质检文件表
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/standard-items
- **所属模块：** 标准检验项
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/standard-items
- **所属模块：** 标准检验项
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- standardId: （类型: integer，可选）
- itemId: （类型: integer，可选）
- sl: （类型: number，可选）
- usl: （类型: number，可选）
- lsl: （类型: number，可选）
- positiveTol: （类型: number，可选）
- negativeTol: （类型: number，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）
- inspectionItem: （类型: object，可选）
-   inspectionItem.id: （类型: integer）
-   inspectionItem.itemCode: （类型: string）
-   inspectionItem.itemName: （类型: string）
-   inspectionItem.itemCategoryId: （类型: integer）
-   inspectionItem.itemCategoryName: （类型: string）
-   inspectionItem.inspectionType: （类型: string）
-   inspectionItem.inspectionMethod: （类型: string）
-   inspectionItem.toolId: （类型: integer）
-   inspectionItem.toolName: （类型: string）
-   inspectionItem.inspectionStandardText: （类型: string）
-   inspectionItem.unit: （类型: string）
-   inspectionItem.remark: （类型: string）
-   inspectionItem.tenantId: （类型: string）
-   inspectionItem.createBy: （类型: string）
-   inspectionItem.createTime: （类型: string）
-   inspectionItem.updateBy: （类型: string）
-   inspectionItem.updateTime: （类型: string）
-   inspectionItem.isValid: （类型: integer）
-   inspectionItem.points: （类型: array<object>）
-     inspectionItem.points[].id: （类型: integer）
-     inspectionItem.points[].itemId: （类型: integer）
-     inspectionItem.points[].deviceCode: （类型: string）
-     inspectionItem.points[].pointCode: （类型: string）
-     inspectionItem.points[].pointName: （类型: string）
-     inspectionItem.points[].tenantId: （类型: string）
-     inspectionItem.points[].createBy: （类型: string）
-     inspectionItem.points[].createTime: （类型: string）
-     inspectionItem.points[].updateBy: （类型: string）
-     inspectionItem.points[].updateTime: （类型: string）
-     inspectionItem.points[].isValid: （类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.sl: （类型: number）
-   data.usl: （类型: number）
-   data.lsl: （类型: number）
-   data.positiveTol: （类型: number）
-   data.negativeTol: （类型: number）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.inspectionItem: （类型: object）
-     data.inspectionItem.id: （类型: integer）
-     data.inspectionItem.itemCode: （类型: string）
-     data.inspectionItem.itemName: （类型: string）
-     data.inspectionItem.itemCategoryId: （类型: integer）
-     data.inspectionItem.itemCategoryName: （类型: string）
-     data.inspectionItem.inspectionType: （类型: string）
-     data.inspectionItem.inspectionMethod: （类型: string）
-     data.inspectionItem.toolId: （类型: integer）
-     data.inspectionItem.toolName: （类型: string）
-     data.inspectionItem.inspectionStandardText: （类型: string）
-     data.inspectionItem.unit: （类型: string）
-     data.inspectionItem.remark: （类型: string）
-     data.inspectionItem.tenantId: （类型: string）
-     data.inspectionItem.createBy: （类型: string）
-     data.inspectionItem.createTime: （类型: string）
-     data.inspectionItem.updateBy: （类型: string）
-     data.inspectionItem.updateTime: （类型: string）
-     data.inspectionItem.isValid: （类型: integer）
-     data.inspectionItem.points: （类型: array<object>）
-       data.inspectionItem.points[].id: （类型: integer）
-       data.inspectionItem.points[].itemId: （类型: integer）
-       data.inspectionItem.points[].deviceCode: （类型: string）
-       data.inspectionItem.points[].pointCode: （类型: string）
-       data.inspectionItem.points[].pointName: （类型: string）
-       data.inspectionItem.points[].tenantId: （类型: string）
-       data.inspectionItem.points[].createBy: （类型: string）
-       data.inspectionItem.points[].createTime: （类型: string）
-       data.inspectionItem.points[].updateBy: （类型: string）
-       data.inspectionItem.points[].updateTime: （类型: string）
-       data.inspectionItem.points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/standard-items/{id}
- **所属模块：** 标准检验项
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.sl: （类型: number）
-   data.usl: （类型: number）
-   data.lsl: （类型: number）
-   data.positiveTol: （类型: number）
-   data.negativeTol: （类型: number）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.inspectionItem: （类型: object）
-     data.inspectionItem.id: （类型: integer）
-     data.inspectionItem.itemCode: （类型: string）
-     data.inspectionItem.itemName: （类型: string）
-     data.inspectionItem.itemCategoryId: （类型: integer）
-     data.inspectionItem.itemCategoryName: （类型: string）
-     data.inspectionItem.inspectionType: （类型: string）
-     data.inspectionItem.inspectionMethod: （类型: string）
-     data.inspectionItem.toolId: （类型: integer）
-     data.inspectionItem.toolName: （类型: string）
-     data.inspectionItem.inspectionStandardText: （类型: string）
-     data.inspectionItem.unit: （类型: string）
-     data.inspectionItem.remark: （类型: string）
-     data.inspectionItem.tenantId: （类型: string）
-     data.inspectionItem.createBy: （类型: string）
-     data.inspectionItem.createTime: （类型: string）
-     data.inspectionItem.updateBy: （类型: string）
-     data.inspectionItem.updateTime: （类型: string）
-     data.inspectionItem.isValid: （类型: integer）
-     data.inspectionItem.points: （类型: array<object>）
-       data.inspectionItem.points[].id: （类型: integer）
-       data.inspectionItem.points[].itemId: （类型: integer）
-       data.inspectionItem.points[].deviceCode: （类型: string）
-       data.inspectionItem.points[].pointCode: （类型: string）
-       data.inspectionItem.points[].pointName: （类型: string）
-       data.inspectionItem.points[].tenantId: （类型: string）
-       data.inspectionItem.points[].createBy: （类型: string）
-       data.inspectionItem.points[].createTime: （类型: string）
-       data.inspectionItem.points[].updateBy: （类型: string）
-       data.inspectionItem.points[].updateTime: （类型: string）
-       data.inspectionItem.points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/standard-items/{id}
- **所属模块：** 标准检验项
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- standardId: （类型: integer，可选）
- itemId: （类型: integer，可选）
- sl: （类型: number，可选）
- usl: （类型: number，可选）
- lsl: （类型: number，可选）
- positiveTol: （类型: number，可选）
- negativeTol: （类型: number，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）
- inspectionItem: （类型: object，可选）
-   inspectionItem.id: （类型: integer）
-   inspectionItem.itemCode: （类型: string）
-   inspectionItem.itemName: （类型: string）
-   inspectionItem.itemCategoryId: （类型: integer）
-   inspectionItem.itemCategoryName: （类型: string）
-   inspectionItem.inspectionType: （类型: string）
-   inspectionItem.inspectionMethod: （类型: string）
-   inspectionItem.toolId: （类型: integer）
-   inspectionItem.toolName: （类型: string）
-   inspectionItem.inspectionStandardText: （类型: string）
-   inspectionItem.unit: （类型: string）
-   inspectionItem.remark: （类型: string）
-   inspectionItem.tenantId: （类型: string）
-   inspectionItem.createBy: （类型: string）
-   inspectionItem.createTime: （类型: string）
-   inspectionItem.updateBy: （类型: string）
-   inspectionItem.updateTime: （类型: string）
-   inspectionItem.isValid: （类型: integer）
-   inspectionItem.points: （类型: array<object>）
-     inspectionItem.points[].id: （类型: integer）
-     inspectionItem.points[].itemId: （类型: integer）
-     inspectionItem.points[].deviceCode: （类型: string）
-     inspectionItem.points[].pointCode: （类型: string）
-     inspectionItem.points[].pointName: （类型: string）
-     inspectionItem.points[].tenantId: （类型: string）
-     inspectionItem.points[].createBy: （类型: string）
-     inspectionItem.points[].createTime: （类型: string）
-     inspectionItem.points[].updateBy: （类型: string）
-     inspectionItem.points[].updateTime: （类型: string）
-     inspectionItem.points[].isValid: （类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.sl: （类型: number）
-   data.usl: （类型: number）
-   data.lsl: （类型: number）
-   data.positiveTol: （类型: number）
-   data.negativeTol: （类型: number）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.inspectionItem: （类型: object）
-     data.inspectionItem.id: （类型: integer）
-     data.inspectionItem.itemCode: （类型: string）
-     data.inspectionItem.itemName: （类型: string）
-     data.inspectionItem.itemCategoryId: （类型: integer）
-     data.inspectionItem.itemCategoryName: （类型: string）
-     data.inspectionItem.inspectionType: （类型: string）
-     data.inspectionItem.inspectionMethod: （类型: string）
-     data.inspectionItem.toolId: （类型: integer）
-     data.inspectionItem.toolName: （类型: string）
-     data.inspectionItem.inspectionStandardText: （类型: string）
-     data.inspectionItem.unit: （类型: string）
-     data.inspectionItem.remark: （类型: string）
-     data.inspectionItem.tenantId: （类型: string）
-     data.inspectionItem.createBy: （类型: string）
-     data.inspectionItem.createTime: （类型: string）
-     data.inspectionItem.updateBy: （类型: string）
-     data.inspectionItem.updateTime: （类型: string）
-     data.inspectionItem.isValid: （类型: integer）
-     data.inspectionItem.points: （类型: array<object>）
-       data.inspectionItem.points[].id: （类型: integer）
-       data.inspectionItem.points[].itemId: （类型: integer）
-       data.inspectionItem.points[].deviceCode: （类型: string）
-       data.inspectionItem.points[].pointCode: （类型: string）
-       data.inspectionItem.points[].pointName: （类型: string）
-       data.inspectionItem.points[].tenantId: （类型: string）
-       data.inspectionItem.points[].createBy: （类型: string）
-       data.inspectionItem.points[].createTime: （类型: string）
-       data.inspectionItem.points[].updateBy: （类型: string）
-       data.inspectionItem.points[].updateTime: （类型: string）
-       data.inspectionItem.points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/standard-items/{id}
- **所属模块：** 标准检验项
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/standard-materials
- **所属模块：** 标准物料关系
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/standard-materials
- **所属模块：** 标准物料关系
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- standardId: （类型: integer，可选）
- materialCode: （类型: string，可选）
- materialType: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardId: （类型: integer）
-   data.materialCode: （类型: string）
-   data.materialType: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/standard-materials/{id}
- **所属模块：** 标准物料关系
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardId: （类型: integer）
-   data.materialCode: （类型: string）
-   data.materialType: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/standard-materials/{id}
- **所属模块：** 标准物料关系
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- standardId: （类型: integer，可选）
- materialCode: （类型: string，可选）
- materialType: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardId: （类型: integer）
-   data.materialCode: （类型: string）
-   data.materialType: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/standard-materials/{id}
- **所属模块：** 标准物料关系
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/inspection-order-item-defects
- **所属模块：** 检验单-检验项结果表（记录每个检验项的检测数据及判定结果）
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/inspection-order-item-defects
- **所属模块：** 检验单-检验项结果表（记录每个检验项的检测数据及判定结果）
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- orderId: （类型: integer，可选）
- itemId: （类型: integer，可选）
- defectId: （类型: integer，可选）
- defectName: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.orderId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.defectId: （类型: integer）
-   data.defectName: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/inspection-order-item-defects/{id}
- **所属模块：** 检验单-检验项结果表（记录每个检验项的检测数据及判定结果）
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.orderId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.defectId: （类型: integer）
-   data.defectName: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/inspection-order-item-defects/{id}
- **所属模块：** 检验单-检验项结果表（记录每个检验项的检测数据及判定结果）
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- orderId: （类型: integer，可选）
- itemId: （类型: integer，可选）
- defectId: （类型: integer，可选）
- defectName: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.orderId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.defectId: （类型: integer）
-   data.defectName: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/inspection-order-item-defects/{id}
- **所属模块：** 检验单-检验项结果表（记录每个检验项的检测数据及判定结果）
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/inspection-order-items
- **所属模块：** 检验单-检验项结果表（记录每个检验项的检测数据及判定结果）
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/inspection-order-items
- **所属模块：** 检验单-检验项结果表（记录每个检验项的检测数据及判定结果）
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- orderId: （类型: integer，可选）
- itemId: （类型: integer，可选）
- deviceCode: （类型: string，可选）
- pointCode: （类型: string，可选）
- pointName: （类型: string，可选）
- sl: （类型: number，可选）
- usl: （类型: number，可选）
- lsl: （类型: number，可选）
- positiveTol: （类型: number，可选）
- negativeTol: （类型: number，可选）
- inspectValue: （类型: string，可选）
- result: （类型: string，可选）
- inspectionItem: （类型: object，可选）
-   inspectionItem.id: （类型: integer）
-   inspectionItem.itemCode: （类型: string）
-   inspectionItem.itemName: （类型: string）
-   inspectionItem.itemCategoryId: （类型: integer）
-   inspectionItem.itemCategoryName: （类型: string）
-   inspectionItem.inspectionType: （类型: string）
-   inspectionItem.inspectionMethod: （类型: string）
-   inspectionItem.toolId: （类型: integer）
-   inspectionItem.toolName: （类型: string）
-   inspectionItem.inspectionStandardText: （类型: string）
-   inspectionItem.unit: （类型: string）
-   inspectionItem.remark: （类型: string）
-   inspectionItem.tenantId: （类型: string）
-   inspectionItem.createBy: （类型: string）
-   inspectionItem.createTime: （类型: string）
-   inspectionItem.updateBy: （类型: string）
-   inspectionItem.updateTime: （类型: string）
-   inspectionItem.isValid: （类型: integer）
-   inspectionItem.points: （类型: array<object>）
-     inspectionItem.points[].id: （类型: integer）
-     inspectionItem.points[].itemId: （类型: integer）
-     inspectionItem.points[].deviceCode: （类型: string）
-     inspectionItem.points[].pointCode: （类型: string）
-     inspectionItem.points[].pointName: （类型: string）
-     inspectionItem.points[].tenantId: （类型: string）
-     inspectionItem.points[].createBy: （类型: string）
-     inspectionItem.points[].createTime: （类型: string）
-     inspectionItem.points[].updateBy: （类型: string）
-     inspectionItem.points[].updateTime: （类型: string）
-     inspectionItem.points[].isValid: （类型: integer）
- defects: （类型: array<object>，可选）
-   defects[].id: （类型: integer）
-   defects[].orderId: （类型: integer）
-   defects[].itemId: （类型: integer）
-   defects[].defectId: （类型: integer）
-   defects[].defectName: （类型: string）
-   defects[].tenantId: （类型: string）
-   defects[].createBy: （类型: string）
-   defects[].createTime: （类型: string）
-   defects[].updateBy: （类型: string）
-   defects[].updateTime: （类型: string）
-   defects[].isValid: （类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.orderId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.deviceCode: （类型: string）
-   data.pointCode: （类型: string）
-   data.pointName: （类型: string）
-   data.sl: （类型: number）
-   data.usl: （类型: number）
-   data.lsl: （类型: number）
-   data.positiveTol: （类型: number）
-   data.negativeTol: （类型: number）
-   data.inspectValue: （类型: string）
-   data.result: （类型: string）
-   data.inspectionItem: （类型: object）
-     data.inspectionItem.id: （类型: integer）
-     data.inspectionItem.itemCode: （类型: string）
-     data.inspectionItem.itemName: （类型: string）
-     data.inspectionItem.itemCategoryId: （类型: integer）
-     data.inspectionItem.itemCategoryName: （类型: string）
-     data.inspectionItem.inspectionType: （类型: string）
-     data.inspectionItem.inspectionMethod: （类型: string）
-     data.inspectionItem.toolId: （类型: integer）
-     data.inspectionItem.toolName: （类型: string）
-     data.inspectionItem.inspectionStandardText: （类型: string）
-     data.inspectionItem.unit: （类型: string）
-     data.inspectionItem.remark: （类型: string）
-     data.inspectionItem.tenantId: （类型: string）
-     data.inspectionItem.createBy: （类型: string）
-     data.inspectionItem.createTime: （类型: string）
-     data.inspectionItem.updateBy: （类型: string）
-     data.inspectionItem.updateTime: （类型: string）
-     data.inspectionItem.isValid: （类型: integer）
-     data.inspectionItem.points: （类型: array<object>）
-       data.inspectionItem.points[].id: （类型: integer）
-       data.inspectionItem.points[].itemId: （类型: integer）
-       data.inspectionItem.points[].deviceCode: （类型: string）
-       data.inspectionItem.points[].pointCode: （类型: string）
-       data.inspectionItem.points[].pointName: （类型: string）
-       data.inspectionItem.points[].tenantId: （类型: string）
-       data.inspectionItem.points[].createBy: （类型: string）
-       data.inspectionItem.points[].createTime: （类型: string）
-       data.inspectionItem.points[].updateBy: （类型: string）
-       data.inspectionItem.points[].updateTime: （类型: string）
-       data.inspectionItem.points[].isValid: （类型: integer）
-   data.defects: （类型: array<object>）
-     data.defects[].id: （类型: integer）
-     data.defects[].orderId: （类型: integer）
-     data.defects[].itemId: （类型: integer）
-     data.defects[].defectId: （类型: integer）
-     data.defects[].defectName: （类型: string）
-     data.defects[].tenantId: （类型: string）
-     data.defects[].createBy: （类型: string）
-     data.defects[].createTime: （类型: string）
-     data.defects[].updateBy: （类型: string）
-     data.defects[].updateTime: （类型: string）
-     data.defects[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/inspection-order-items/{id}
- **所属模块：** 检验单-检验项结果表（记录每个检验项的检测数据及判定结果）
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.orderId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.deviceCode: （类型: string）
-   data.pointCode: （类型: string）
-   data.pointName: （类型: string）
-   data.sl: （类型: number）
-   data.usl: （类型: number）
-   data.lsl: （类型: number）
-   data.positiveTol: （类型: number）
-   data.negativeTol: （类型: number）
-   data.inspectValue: （类型: string）
-   data.result: （类型: string）
-   data.inspectionItem: （类型: object）
-     data.inspectionItem.id: （类型: integer）
-     data.inspectionItem.itemCode: （类型: string）
-     data.inspectionItem.itemName: （类型: string）
-     data.inspectionItem.itemCategoryId: （类型: integer）
-     data.inspectionItem.itemCategoryName: （类型: string）
-     data.inspectionItem.inspectionType: （类型: string）
-     data.inspectionItem.inspectionMethod: （类型: string）
-     data.inspectionItem.toolId: （类型: integer）
-     data.inspectionItem.toolName: （类型: string）
-     data.inspectionItem.inspectionStandardText: （类型: string）
-     data.inspectionItem.unit: （类型: string）
-     data.inspectionItem.remark: （类型: string）
-     data.inspectionItem.tenantId: （类型: string）
-     data.inspectionItem.createBy: （类型: string）
-     data.inspectionItem.createTime: （类型: string）
-     data.inspectionItem.updateBy: （类型: string）
-     data.inspectionItem.updateTime: （类型: string）
-     data.inspectionItem.isValid: （类型: integer）
-     data.inspectionItem.points: （类型: array<object>）
-       data.inspectionItem.points[].id: （类型: integer）
-       data.inspectionItem.points[].itemId: （类型: integer）
-       data.inspectionItem.points[].deviceCode: （类型: string）
-       data.inspectionItem.points[].pointCode: （类型: string）
-       data.inspectionItem.points[].pointName: （类型: string）
-       data.inspectionItem.points[].tenantId: （类型: string）
-       data.inspectionItem.points[].createBy: （类型: string）
-       data.inspectionItem.points[].createTime: （类型: string）
-       data.inspectionItem.points[].updateBy: （类型: string）
-       data.inspectionItem.points[].updateTime: （类型: string）
-       data.inspectionItem.points[].isValid: （类型: integer）
-   data.defects: （类型: array<object>）
-     data.defects[].id: （类型: integer）
-     data.defects[].orderId: （类型: integer）
-     data.defects[].itemId: （类型: integer）
-     data.defects[].defectId: （类型: integer）
-     data.defects[].defectName: （类型: string）
-     data.defects[].tenantId: （类型: string）
-     data.defects[].createBy: （类型: string）
-     data.defects[].createTime: （类型: string）
-     data.defects[].updateBy: （类型: string）
-     data.defects[].updateTime: （类型: string）
-     data.defects[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/inspection-order-items/{id}
- **所属模块：** 检验单-检验项结果表（记录每个检验项的检测数据及判定结果）
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- orderId: （类型: integer，可选）
- itemId: （类型: integer，可选）
- deviceCode: （类型: string，可选）
- pointCode: （类型: string，可选）
- pointName: （类型: string，可选）
- sl: （类型: number，可选）
- usl: （类型: number，可选）
- lsl: （类型: number，可选）
- positiveTol: （类型: number，可选）
- negativeTol: （类型: number，可选）
- inspectValue: （类型: string，可选）
- result: （类型: string，可选）
- inspectionItem: （类型: object，可选）
-   inspectionItem.id: （类型: integer）
-   inspectionItem.itemCode: （类型: string）
-   inspectionItem.itemName: （类型: string）
-   inspectionItem.itemCategoryId: （类型: integer）
-   inspectionItem.itemCategoryName: （类型: string）
-   inspectionItem.inspectionType: （类型: string）
-   inspectionItem.inspectionMethod: （类型: string）
-   inspectionItem.toolId: （类型: integer）
-   inspectionItem.toolName: （类型: string）
-   inspectionItem.inspectionStandardText: （类型: string）
-   inspectionItem.unit: （类型: string）
-   inspectionItem.remark: （类型: string）
-   inspectionItem.tenantId: （类型: string）
-   inspectionItem.createBy: （类型: string）
-   inspectionItem.createTime: （类型: string）
-   inspectionItem.updateBy: （类型: string）
-   inspectionItem.updateTime: （类型: string）
-   inspectionItem.isValid: （类型: integer）
-   inspectionItem.points: （类型: array<object>）
-     inspectionItem.points[].id: （类型: integer）
-     inspectionItem.points[].itemId: （类型: integer）
-     inspectionItem.points[].deviceCode: （类型: string）
-     inspectionItem.points[].pointCode: （类型: string）
-     inspectionItem.points[].pointName: （类型: string）
-     inspectionItem.points[].tenantId: （类型: string）
-     inspectionItem.points[].createBy: （类型: string）
-     inspectionItem.points[].createTime: （类型: string）
-     inspectionItem.points[].updateBy: （类型: string）
-     inspectionItem.points[].updateTime: （类型: string）
-     inspectionItem.points[].isValid: （类型: integer）
- defects: （类型: array<object>，可选）
-   defects[].id: （类型: integer）
-   defects[].orderId: （类型: integer）
-   defects[].itemId: （类型: integer）
-   defects[].defectId: （类型: integer）
-   defects[].defectName: （类型: string）
-   defects[].tenantId: （类型: string）
-   defects[].createBy: （类型: string）
-   defects[].createTime: （类型: string）
-   defects[].updateBy: （类型: string）
-   defects[].updateTime: （类型: string）
-   defects[].isValid: （类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.orderId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.deviceCode: （类型: string）
-   data.pointCode: （类型: string）
-   data.pointName: （类型: string）
-   data.sl: （类型: number）
-   data.usl: （类型: number）
-   data.lsl: （类型: number）
-   data.positiveTol: （类型: number）
-   data.negativeTol: （类型: number）
-   data.inspectValue: （类型: string）
-   data.result: （类型: string）
-   data.inspectionItem: （类型: object）
-     data.inspectionItem.id: （类型: integer）
-     data.inspectionItem.itemCode: （类型: string）
-     data.inspectionItem.itemName: （类型: string）
-     data.inspectionItem.itemCategoryId: （类型: integer）
-     data.inspectionItem.itemCategoryName: （类型: string）
-     data.inspectionItem.inspectionType: （类型: string）
-     data.inspectionItem.inspectionMethod: （类型: string）
-     data.inspectionItem.toolId: （类型: integer）
-     data.inspectionItem.toolName: （类型: string）
-     data.inspectionItem.inspectionStandardText: （类型: string）
-     data.inspectionItem.unit: （类型: string）
-     data.inspectionItem.remark: （类型: string）
-     data.inspectionItem.tenantId: （类型: string）
-     data.inspectionItem.createBy: （类型: string）
-     data.inspectionItem.createTime: （类型: string）
-     data.inspectionItem.updateBy: （类型: string）
-     data.inspectionItem.updateTime: （类型: string）
-     data.inspectionItem.isValid: （类型: integer）
-     data.inspectionItem.points: （类型: array<object>）
-       data.inspectionItem.points[].id: （类型: integer）
-       data.inspectionItem.points[].itemId: （类型: integer）
-       data.inspectionItem.points[].deviceCode: （类型: string）
-       data.inspectionItem.points[].pointCode: （类型: string）
-       data.inspectionItem.points[].pointName: （类型: string）
-       data.inspectionItem.points[].tenantId: （类型: string）
-       data.inspectionItem.points[].createBy: （类型: string）
-       data.inspectionItem.points[].createTime: （类型: string）
-       data.inspectionItem.points[].updateBy: （类型: string）
-       data.inspectionItem.points[].updateTime: （类型: string）
-       data.inspectionItem.points[].isValid: （类型: integer）
-   data.defects: （类型: array<object>）
-     data.defects[].id: （类型: integer）
-     data.defects[].orderId: （类型: integer）
-     data.defects[].itemId: （类型: integer）
-     data.defects[].defectId: （类型: integer）
-     data.defects[].defectName: （类型: string）
-     data.defects[].tenantId: （类型: string）
-     data.defects[].createBy: （类型: string）
-     data.defects[].createTime: （类型: string）
-     data.defects[].updateBy: （类型: string）
-     data.defects[].updateTime: （类型: string）
-     data.defects[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/inspection-order-items/{id}
- **所属模块：** 检验单-检验项结果表（记录每个检验项的检测数据及判定结果）
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/inspection-orders
- **所属模块：** 检验单主表（统一IQC/IPQC/FQC/OQC检验业务）
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/inspection-orders
- **所属模块：** 检验单主表（统一IQC/IPQC/FQC/OQC检验业务）
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- orderNo: （类型: string，可选）
- orderType: （类型: string，可选）
- materialType: （类型: string，可选）
- materialId: （类型: integer，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- spec: （类型: string，可选）
- unit: （类型: string，可选）
- standardId: （类型: integer，可选）
- standardName: （类型: string，可选）
- supplier: （类型: string，可选）
- customerName: （类型: string，可选）
- deliveryNo: （类型: string，可选）
- salesOrderNo: （类型: string，可选）
- batchNo: （类型: string，可选）
- workOrderNo: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- processNo: （类型: string，可选）
- workCenter: （类型: string，可选）
- station: （类型: string，可选）
- quantity: （类型: number，可选）
- status: （类型: string，可选）
- result: （类型: string，可选）
- inspector: （类型: string，可选）
- initiator: （类型: string，可选）
- requestTime: （类型: string，可选）
- inspectTime: （类型: string，可选）
- deliveryTime: （类型: string，可选）
- incomingTime: （类型: string，可选）
- initiateTime: （类型: string，可选）
- finalInspectionMethod: （类型: string，可选）
- finalSampleQty: （类型: number，可选）
- finalDefectQty: （类型: number，可选）
- finalDefectRate: （类型: number，可选）
- finalResult: （类型: string，可选）
- finalInspectTime: （类型: string，可选）
- finalInspector: （类型: string，可选）
- finalRemark: （类型: string，可选）
- items: （类型: array<object>，可选）
-   items[].id: （类型: integer）
-   items[].orderId: （类型: integer）
-   items[].itemId: （类型: integer）
-   items[].deviceCode: （类型: string）
-   items[].pointCode: （类型: string）
-   items[].pointName: （类型: string）
-   items[].sl: （类型: number）
-   items[].usl: （类型: number）
-   items[].lsl: （类型: number）
-   items[].positiveTol: （类型: number）
-   items[].negativeTol: （类型: number）
-   items[].inspectValue: （类型: string）
-   items[].result: （类型: string）
-   items[].inspectionItem: （类型: object）
-     items[].inspectionItem.id: （类型: integer）
-     items[].inspectionItem.itemCode: （类型: string）
-     items[].inspectionItem.itemName: （类型: string）
-     items[].inspectionItem.itemCategoryId: （类型: integer）
-     items[].inspectionItem.itemCategoryName: （类型: string）
-     items[].inspectionItem.inspectionType: （类型: string）
-     items[].inspectionItem.inspectionMethod: （类型: string）
-     items[].inspectionItem.toolId: （类型: integer）
-     items[].inspectionItem.toolName: （类型: string）
-     items[].inspectionItem.inspectionStandardText: （类型: string）
-     items[].inspectionItem.unit: （类型: string）
-     items[].inspectionItem.remark: （类型: string）
-     items[].inspectionItem.tenantId: （类型: string）
-     items[].inspectionItem.createBy: （类型: string）
-     items[].inspectionItem.createTime: （类型: string）
-     items[].inspectionItem.updateBy: （类型: string）
-     items[].inspectionItem.updateTime: （类型: string）
-     items[].inspectionItem.isValid: （类型: integer）
-     items[].inspectionItem.points: （类型: array<object>）
-       items[].inspectionItem.points[].id: （类型: integer）
-       items[].inspectionItem.points[].itemId: （类型: integer）
-       items[].inspectionItem.points[].deviceCode: （类型: string）
-       items[].inspectionItem.points[].pointCode: （类型: string）
-       items[].inspectionItem.points[].pointName: （类型: string）
-       items[].inspectionItem.points[].tenantId: （类型: string）
-       items[].inspectionItem.points[].createBy: （类型: string）
-       items[].inspectionItem.points[].createTime: （类型: string）
-       items[].inspectionItem.points[].updateBy: （类型: string）
-       items[].inspectionItem.points[].updateTime: （类型: string）
-       items[].inspectionItem.points[].isValid: （类型: integer）
-   items[].defects: （类型: array<object>）
-     items[].defects[].id: （类型: integer）
-     items[].defects[].orderId: （类型: integer）
-     items[].defects[].itemId: （类型: integer）
-     items[].defects[].defectId: （类型: integer）
-     items[].defects[].defectName: （类型: string）
-     items[].defects[].tenantId: （类型: string）
-     items[].defects[].createBy: （类型: string）
-     items[].defects[].createTime: （类型: string）
-     items[].defects[].updateBy: （类型: string）
-     items[].defects[].updateTime: （类型: string）
-     items[].defects[].isValid: （类型: integer）
- waiting: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.orderNo: （类型: string）
-   data.orderType: （类型: string）
-   data.materialType: （类型: string）
-   data.materialId: （类型: integer）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.spec: （类型: string）
-   data.unit: （类型: string）
-   data.standardId: （类型: integer）
-   data.standardName: （类型: string）
-   data.supplier: （类型: string）
-   data.customerName: （类型: string）
-   data.deliveryNo: （类型: string）
-   data.salesOrderNo: （类型: string）
-   data.batchNo: （类型: string）
-   data.workOrderNo: （类型: string）
-   data.productionOrderNo: （类型: string）
-   data.processNo: （类型: string）
-   data.workCenter: （类型: string）
-   data.station: （类型: string）
-   data.quantity: （类型: number）
-   data.status: （类型: string）
-   data.result: （类型: string）
-   data.inspector: （类型: string）
-   data.initiator: （类型: string）
-   data.requestTime: （类型: string）
-   data.inspectTime: （类型: string）
-   data.deliveryTime: （类型: string）
-   data.incomingTime: （类型: string）
-   data.initiateTime: （类型: string）
-   data.finalInspectionMethod: （类型: string）
-   data.finalSampleQty: （类型: number）
-   data.finalDefectQty: （类型: number）
-   data.finalDefectRate: （类型: number）
-   data.finalResult: （类型: string）
-   data.finalInspectTime: （类型: string）
-   data.finalInspector: （类型: string）
-   data.finalRemark: （类型: string）
-   data.items: （类型: array<object>）
-     data.items[].id: （类型: integer）
-     data.items[].orderId: （类型: integer）
-     data.items[].itemId: （类型: integer）
-     data.items[].deviceCode: （类型: string）
-     data.items[].pointCode: （类型: string）
-     data.items[].pointName: （类型: string）
-     data.items[].sl: （类型: number）
-     data.items[].usl: （类型: number）
-     data.items[].lsl: （类型: number）
-     data.items[].positiveTol: （类型: number）
-     data.items[].negativeTol: （类型: number）
-     data.items[].inspectValue: （类型: string）
-     data.items[].result: （类型: string）
-     data.items[].inspectionItem: （类型: object）
-       data.items[].inspectionItem.id: （类型: integer）
-       data.items[].inspectionItem.itemCode: （类型: string）
-       data.items[].inspectionItem.itemName: （类型: string）
-       data.items[].inspectionItem.itemCategoryId: （类型: integer）
-       data.items[].inspectionItem.itemCategoryName: （类型: string）
-       data.items[].inspectionItem.inspectionType: （类型: string）
-       data.items[].inspectionItem.inspectionMethod: （类型: string）
-       data.items[].inspectionItem.toolId: （类型: integer）
-       data.items[].inspectionItem.toolName: （类型: string）
-       data.items[].inspectionItem.inspectionStandardText: （类型: string）
-       data.items[].inspectionItem.unit: （类型: string）
-       data.items[].inspectionItem.remark: （类型: string）
-       data.items[].inspectionItem.tenantId: （类型: string）
-       data.items[].inspectionItem.createBy: （类型: string）
-       data.items[].inspectionItem.createTime: （类型: string）
-       data.items[].inspectionItem.updateBy: （类型: string）
-       data.items[].inspectionItem.updateTime: （类型: string）
-       data.items[].inspectionItem.isValid: （类型: integer）
-       data.items[].inspectionItem.points: （类型: array<object>）
-         data.items[].inspectionItem.points[].id: （类型: integer）
-         data.items[].inspectionItem.points[].itemId: （类型: integer）
-         data.items[].inspectionItem.points[].deviceCode: （类型: string）
-         data.items[].inspectionItem.points[].pointCode: （类型: string）
-         data.items[].inspectionItem.points[].pointName: （类型: string）
-         data.items[].inspectionItem.points[].tenantId: （类型: string）
-         data.items[].inspectionItem.points[].createBy: （类型: string）
-         data.items[].inspectionItem.points[].createTime: （类型: string）
-         data.items[].inspectionItem.points[].updateBy: （类型: string）
-         data.items[].inspectionItem.points[].updateTime: （类型: string）
-         data.items[].inspectionItem.points[].isValid: （类型: integer）
-     data.items[].defects: （类型: array<object>）
-       data.items[].defects[].id: （类型: integer）
-       data.items[].defects[].orderId: （类型: integer）
-       data.items[].defects[].itemId: （类型: integer）
-       data.items[].defects[].defectId: （类型: integer）
-       data.items[].defects[].defectName: （类型: string）
-       data.items[].defects[].tenantId: （类型: string）
-       data.items[].defects[].createBy: （类型: string）
-       data.items[].defects[].createTime: （类型: string）
-       data.items[].defects[].updateBy: （类型: string）
-       data.items[].defects[].updateTime: （类型: string）
-       data.items[].defects[].isValid: （类型: integer）
-   data.waiting: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：按物料编码与批次号查询

- **路径：** GET /api/v1/inspection-orders/by-material-batch
- **所属模块：** 检验单主表（统一IQC/IPQC/FQC/OQC检验业务）
- **HTTP 方法：** GET

**功能说明：**
按物料编码与批次号查询

**请求参数：**
- materialCode （查询参数）: （类型: string，必填）
- batchNo （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.orderNo: （类型: string）
-   data.orderType: （类型: string）
-   data.materialType: （类型: string）
-   data.materialId: （类型: integer）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.spec: （类型: string）
-   data.unit: （类型: string）
-   data.standardId: （类型: integer）
-   data.standardName: （类型: string）
-   data.supplier: （类型: string）
-   data.customerName: （类型: string）
-   data.deliveryNo: （类型: string）
-   data.salesOrderNo: （类型: string）
-   data.batchNo: （类型: string）
-   data.workOrderNo: （类型: string）
-   data.productionOrderNo: （类型: string）
-   data.processNo: （类型: string）
-   data.workCenter: （类型: string）
-   data.station: （类型: string）
-   data.quantity: （类型: number）
-   data.status: （类型: string）
-   data.result: （类型: string）
-   data.inspector: （类型: string）
-   data.initiator: （类型: string）
-   data.requestTime: （类型: string）
-   data.inspectTime: （类型: string）
-   data.deliveryTime: （类型: string）
-   data.incomingTime: （类型: string）
-   data.initiateTime: （类型: string）
-   data.finalInspectionMethod: （类型: string）
-   data.finalSampleQty: （类型: number）
-   data.finalDefectQty: （类型: number）
-   data.finalDefectRate: （类型: number）
-   data.finalResult: （类型: string）
-   data.finalInspectTime: （类型: string）
-   data.finalInspector: （类型: string）
-   data.finalRemark: （类型: string）
-   data.items: （类型: array<object>）
-     data.items[].id: （类型: integer）
-     data.items[].orderId: （类型: integer）
-     data.items[].itemId: （类型: integer）
-     data.items[].deviceCode: （类型: string）
-     data.items[].pointCode: （类型: string）
-     data.items[].pointName: （类型: string）
-     data.items[].sl: （类型: number）
-     data.items[].usl: （类型: number）
-     data.items[].lsl: （类型: number）
-     data.items[].positiveTol: （类型: number）
-     data.items[].negativeTol: （类型: number）
-     data.items[].inspectValue: （类型: string）
-     data.items[].result: （类型: string）
-     data.items[].inspectionItem: （类型: object）
-       data.items[].inspectionItem.id: （类型: integer）
-       data.items[].inspectionItem.itemCode: （类型: string）
-       data.items[].inspectionItem.itemName: （类型: string）
-       data.items[].inspectionItem.itemCategoryId: （类型: integer）
-       data.items[].inspectionItem.itemCategoryName: （类型: string）
-       data.items[].inspectionItem.inspectionType: （类型: string）
-       data.items[].inspectionItem.inspectionMethod: （类型: string）
-       data.items[].inspectionItem.toolId: （类型: integer）
-       data.items[].inspectionItem.toolName: （类型: string）
-       data.items[].inspectionItem.inspectionStandardText: （类型: string）
-       data.items[].inspectionItem.unit: （类型: string）
-       data.items[].inspectionItem.remark: （类型: string）
-       data.items[].inspectionItem.tenantId: （类型: string）
-       data.items[].inspectionItem.createBy: （类型: string）
-       data.items[].inspectionItem.createTime: （类型: string）
-       data.items[].inspectionItem.updateBy: （类型: string）
-       data.items[].inspectionItem.updateTime: （类型: string）
-       data.items[].inspectionItem.isValid: （类型: integer）
-       data.items[].inspectionItem.points: （类型: array<object>）
-         data.items[].inspectionItem.points[].id: （类型: integer）
-         data.items[].inspectionItem.points[].itemId: （类型: integer）
-         data.items[].inspectionItem.points[].deviceCode: （类型: string）
-         data.items[].inspectionItem.points[].pointCode: （类型: string）
-         data.items[].inspectionItem.points[].pointName: （类型: string）
-         data.items[].inspectionItem.points[].tenantId: （类型: string）
-         data.items[].inspectionItem.points[].createBy: （类型: string）
-         data.items[].inspectionItem.points[].createTime: （类型: string）
-         data.items[].inspectionItem.points[].updateBy: （类型: string）
-         data.items[].inspectionItem.points[].updateTime: （类型: string）
-         data.items[].inspectionItem.points[].isValid: （类型: integer）
-     data.items[].defects: （类型: array<object>）
-       data.items[].defects[].id: （类型: integer）
-       data.items[].defects[].orderId: （类型: integer）
-       data.items[].defects[].itemId: （类型: integer）
-       data.items[].defects[].defectId: （类型: integer）
-       data.items[].defects[].defectName: （类型: string）
-       data.items[].defects[].tenantId: （类型: string）
-       data.items[].defects[].createBy: （类型: string）
-       data.items[].defects[].createTime: （类型: string）
-       data.items[].defects[].updateBy: （类型: string）
-       data.items[].defects[].updateTime: （类型: string）
-       data.items[].defects[].isValid: （类型: integer）
-   data.waiting: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：查询

- **路径：** GET /api/v1/inspection-orders/{id}
- **所属模块：** 检验单主表（统一IQC/IPQC/FQC/OQC检验业务）
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.orderNo: （类型: string）
-   data.orderType: （类型: string）
-   data.materialType: （类型: string）
-   data.materialId: （类型: integer）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.spec: （类型: string）
-   data.unit: （类型: string）
-   data.standardId: （类型: integer）
-   data.standardName: （类型: string）
-   data.supplier: （类型: string）
-   data.customerName: （类型: string）
-   data.deliveryNo: （类型: string）
-   data.salesOrderNo: （类型: string）
-   data.batchNo: （类型: string）
-   data.workOrderNo: （类型: string）
-   data.productionOrderNo: （类型: string）
-   data.processNo: （类型: string）
-   data.workCenter: （类型: string）
-   data.station: （类型: string）
-   data.quantity: （类型: number）
-   data.status: （类型: string）
-   data.result: （类型: string）
-   data.inspector: （类型: string）
-   data.initiator: （类型: string）
-   data.requestTime: （类型: string）
-   data.inspectTime: （类型: string）
-   data.deliveryTime: （类型: string）
-   data.incomingTime: （类型: string）
-   data.initiateTime: （类型: string）
-   data.finalInspectionMethod: （类型: string）
-   data.finalSampleQty: （类型: number）
-   data.finalDefectQty: （类型: number）
-   data.finalDefectRate: （类型: number）
-   data.finalResult: （类型: string）
-   data.finalInspectTime: （类型: string）
-   data.finalInspector: （类型: string）
-   data.finalRemark: （类型: string）
-   data.items: （类型: array<object>）
-     data.items[].id: （类型: integer）
-     data.items[].orderId: （类型: integer）
-     data.items[].itemId: （类型: integer）
-     data.items[].deviceCode: （类型: string）
-     data.items[].pointCode: （类型: string）
-     data.items[].pointName: （类型: string）
-     data.items[].sl: （类型: number）
-     data.items[].usl: （类型: number）
-     data.items[].lsl: （类型: number）
-     data.items[].positiveTol: （类型: number）
-     data.items[].negativeTol: （类型: number）
-     data.items[].inspectValue: （类型: string）
-     data.items[].result: （类型: string）
-     data.items[].inspectionItem: （类型: object）
-       data.items[].inspectionItem.id: （类型: integer）
-       data.items[].inspectionItem.itemCode: （类型: string）
-       data.items[].inspectionItem.itemName: （类型: string）
-       data.items[].inspectionItem.itemCategoryId: （类型: integer）
-       data.items[].inspectionItem.itemCategoryName: （类型: string）
-       data.items[].inspectionItem.inspectionType: （类型: string）
-       data.items[].inspectionItem.inspectionMethod: （类型: string）
-       data.items[].inspectionItem.toolId: （类型: integer）
-       data.items[].inspectionItem.toolName: （类型: string）
-       data.items[].inspectionItem.inspectionStandardText: （类型: string）
-       data.items[].inspectionItem.unit: （类型: string）
-       data.items[].inspectionItem.remark: （类型: string）
-       data.items[].inspectionItem.tenantId: （类型: string）
-       data.items[].inspectionItem.createBy: （类型: string）
-       data.items[].inspectionItem.createTime: （类型: string）
-       data.items[].inspectionItem.updateBy: （类型: string）
-       data.items[].inspectionItem.updateTime: （类型: string）
-       data.items[].inspectionItem.isValid: （类型: integer）
-       data.items[].inspectionItem.points: （类型: array<object>）
-         data.items[].inspectionItem.points[].id: （类型: integer）
-         data.items[].inspectionItem.points[].itemId: （类型: integer）
-         data.items[].inspectionItem.points[].deviceCode: （类型: string）
-         data.items[].inspectionItem.points[].pointCode: （类型: string）
-         data.items[].inspectionItem.points[].pointName: （类型: string）
-         data.items[].inspectionItem.points[].tenantId: （类型: string）
-         data.items[].inspectionItem.points[].createBy: （类型: string）
-         data.items[].inspectionItem.points[].createTime: （类型: string）
-         data.items[].inspectionItem.points[].updateBy: （类型: string）
-         data.items[].inspectionItem.points[].updateTime: （类型: string）
-         data.items[].inspectionItem.points[].isValid: （类型: integer）
-     data.items[].defects: （类型: array<object>）
-       data.items[].defects[].id: （类型: integer）
-       data.items[].defects[].orderId: （类型: integer）
-       data.items[].defects[].itemId: （类型: integer）
-       data.items[].defects[].defectId: （类型: integer）
-       data.items[].defects[].defectName: （类型: string）
-       data.items[].defects[].tenantId: （类型: string）
-       data.items[].defects[].createBy: （类型: string）
-       data.items[].defects[].createTime: （类型: string）
-       data.items[].defects[].updateBy: （类型: string）
-       data.items[].defects[].updateTime: （类型: string）
-       data.items[].defects[].isValid: （类型: integer）
-   data.waiting: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/inspection-orders/{id}
- **所属模块：** 检验单主表（统一IQC/IPQC/FQC/OQC检验业务）
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- orderNo: （类型: string，可选）
- orderType: （类型: string，可选）
- materialType: （类型: string，可选）
- materialId: （类型: integer，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- spec: （类型: string，可选）
- unit: （类型: string，可选）
- standardId: （类型: integer，可选）
- standardName: （类型: string，可选）
- supplier: （类型: string，可选）
- customerName: （类型: string，可选）
- deliveryNo: （类型: string，可选）
- salesOrderNo: （类型: string，可选）
- batchNo: （类型: string，可选）
- workOrderNo: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- processNo: （类型: string，可选）
- workCenter: （类型: string，可选）
- station: （类型: string，可选）
- quantity: （类型: number，可选）
- status: （类型: string，可选）
- result: （类型: string，可选）
- inspector: （类型: string，可选）
- initiator: （类型: string，可选）
- requestTime: （类型: string，可选）
- inspectTime: （类型: string，可选）
- deliveryTime: （类型: string，可选）
- incomingTime: （类型: string，可选）
- initiateTime: （类型: string，可选）
- finalInspectionMethod: （类型: string，可选）
- finalSampleQty: （类型: number，可选）
- finalDefectQty: （类型: number，可选）
- finalDefectRate: （类型: number，可选）
- finalResult: （类型: string，可选）
- finalInspectTime: （类型: string，可选）
- finalInspector: （类型: string，可选）
- finalRemark: （类型: string，可选）
- items: （类型: array<object>，可选）
-   items[].id: （类型: integer）
-   items[].orderId: （类型: integer）
-   items[].itemId: （类型: integer）
-   items[].deviceCode: （类型: string）
-   items[].pointCode: （类型: string）
-   items[].pointName: （类型: string）
-   items[].sl: （类型: number）
-   items[].usl: （类型: number）
-   items[].lsl: （类型: number）
-   items[].positiveTol: （类型: number）
-   items[].negativeTol: （类型: number）
-   items[].inspectValue: （类型: string）
-   items[].result: （类型: string）
-   items[].inspectionItem: （类型: object）
-     items[].inspectionItem.id: （类型: integer）
-     items[].inspectionItem.itemCode: （类型: string）
-     items[].inspectionItem.itemName: （类型: string）
-     items[].inspectionItem.itemCategoryId: （类型: integer）
-     items[].inspectionItem.itemCategoryName: （类型: string）
-     items[].inspectionItem.inspectionType: （类型: string）
-     items[].inspectionItem.inspectionMethod: （类型: string）
-     items[].inspectionItem.toolId: （类型: integer）
-     items[].inspectionItem.toolName: （类型: string）
-     items[].inspectionItem.inspectionStandardText: （类型: string）
-     items[].inspectionItem.unit: （类型: string）
-     items[].inspectionItem.remark: （类型: string）
-     items[].inspectionItem.tenantId: （类型: string）
-     items[].inspectionItem.createBy: （类型: string）
-     items[].inspectionItem.createTime: （类型: string）
-     items[].inspectionItem.updateBy: （类型: string）
-     items[].inspectionItem.updateTime: （类型: string）
-     items[].inspectionItem.isValid: （类型: integer）
-     items[].inspectionItem.points: （类型: array<object>）
-       items[].inspectionItem.points[].id: （类型: integer）
-       items[].inspectionItem.points[].itemId: （类型: integer）
-       items[].inspectionItem.points[].deviceCode: （类型: string）
-       items[].inspectionItem.points[].pointCode: （类型: string）
-       items[].inspectionItem.points[].pointName: （类型: string）
-       items[].inspectionItem.points[].tenantId: （类型: string）
-       items[].inspectionItem.points[].createBy: （类型: string）
-       items[].inspectionItem.points[].createTime: （类型: string）
-       items[].inspectionItem.points[].updateBy: （类型: string）
-       items[].inspectionItem.points[].updateTime: （类型: string）
-       items[].inspectionItem.points[].isValid: （类型: integer）
-   items[].defects: （类型: array<object>）
-     items[].defects[].id: （类型: integer）
-     items[].defects[].orderId: （类型: integer）
-     items[].defects[].itemId: （类型: integer）
-     items[].defects[].defectId: （类型: integer）
-     items[].defects[].defectName: （类型: string）
-     items[].defects[].tenantId: （类型: string）
-     items[].defects[].createBy: （类型: string）
-     items[].defects[].createTime: （类型: string）
-     items[].defects[].updateBy: （类型: string）
-     items[].defects[].updateTime: （类型: string）
-     items[].defects[].isValid: （类型: integer）
- waiting: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.orderNo: （类型: string）
-   data.orderType: （类型: string）
-   data.materialType: （类型: string）
-   data.materialId: （类型: integer）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.spec: （类型: string）
-   data.unit: （类型: string）
-   data.standardId: （类型: integer）
-   data.standardName: （类型: string）
-   data.supplier: （类型: string）
-   data.customerName: （类型: string）
-   data.deliveryNo: （类型: string）
-   data.salesOrderNo: （类型: string）
-   data.batchNo: （类型: string）
-   data.workOrderNo: （类型: string）
-   data.productionOrderNo: （类型: string）
-   data.processNo: （类型: string）
-   data.workCenter: （类型: string）
-   data.station: （类型: string）
-   data.quantity: （类型: number）
-   data.status: （类型: string）
-   data.result: （类型: string）
-   data.inspector: （类型: string）
-   data.initiator: （类型: string）
-   data.requestTime: （类型: string）
-   data.inspectTime: （类型: string）
-   data.deliveryTime: （类型: string）
-   data.incomingTime: （类型: string）
-   data.initiateTime: （类型: string）
-   data.finalInspectionMethod: （类型: string）
-   data.finalSampleQty: （类型: number）
-   data.finalDefectQty: （类型: number）
-   data.finalDefectRate: （类型: number）
-   data.finalResult: （类型: string）
-   data.finalInspectTime: （类型: string）
-   data.finalInspector: （类型: string）
-   data.finalRemark: （类型: string）
-   data.items: （类型: array<object>）
-     data.items[].id: （类型: integer）
-     data.items[].orderId: （类型: integer）
-     data.items[].itemId: （类型: integer）
-     data.items[].deviceCode: （类型: string）
-     data.items[].pointCode: （类型: string）
-     data.items[].pointName: （类型: string）
-     data.items[].sl: （类型: number）
-     data.items[].usl: （类型: number）
-     data.items[].lsl: （类型: number）
-     data.items[].positiveTol: （类型: number）
-     data.items[].negativeTol: （类型: number）
-     data.items[].inspectValue: （类型: string）
-     data.items[].result: （类型: string）
-     data.items[].inspectionItem: （类型: object）
-       data.items[].inspectionItem.id: （类型: integer）
-       data.items[].inspectionItem.itemCode: （类型: string）
-       data.items[].inspectionItem.itemName: （类型: string）
-       data.items[].inspectionItem.itemCategoryId: （类型: integer）
-       data.items[].inspectionItem.itemCategoryName: （类型: string）
-       data.items[].inspectionItem.inspectionType: （类型: string）
-       data.items[].inspectionItem.inspectionMethod: （类型: string）
-       data.items[].inspectionItem.toolId: （类型: integer）
-       data.items[].inspectionItem.toolName: （类型: string）
-       data.items[].inspectionItem.inspectionStandardText: （类型: string）
-       data.items[].inspectionItem.unit: （类型: string）
-       data.items[].inspectionItem.remark: （类型: string）
-       data.items[].inspectionItem.tenantId: （类型: string）
-       data.items[].inspectionItem.createBy: （类型: string）
-       data.items[].inspectionItem.createTime: （类型: string）
-       data.items[].inspectionItem.updateBy: （类型: string）
-       data.items[].inspectionItem.updateTime: （类型: string）
-       data.items[].inspectionItem.isValid: （类型: integer）
-       data.items[].inspectionItem.points: （类型: array<object>）
-         data.items[].inspectionItem.points[].id: （类型: integer）
-         data.items[].inspectionItem.points[].itemId: （类型: integer）
-         data.items[].inspectionItem.points[].deviceCode: （类型: string）
-         data.items[].inspectionItem.points[].pointCode: （类型: string）
-         data.items[].inspectionItem.points[].pointName: （类型: string）
-         data.items[].inspectionItem.points[].tenantId: （类型: string）
-         data.items[].inspectionItem.points[].createBy: （类型: string）
-         data.items[].inspectionItem.points[].createTime: （类型: string）
-         data.items[].inspectionItem.points[].updateBy: （类型: string）
-         data.items[].inspectionItem.points[].updateTime: （类型: string）
-         data.items[].inspectionItem.points[].isValid: （类型: integer）
-     data.items[].defects: （类型: array<object>）
-       data.items[].defects[].id: （类型: integer）
-       data.items[].defects[].orderId: （类型: integer）
-       data.items[].defects[].itemId: （类型: integer）
-       data.items[].defects[].defectId: （类型: integer）
-       data.items[].defects[].defectName: （类型: string）
-       data.items[].defects[].tenantId: （类型: string）
-       data.items[].defects[].createBy: （类型: string）
-       data.items[].defects[].createTime: （类型: string）
-       data.items[].defects[].updateBy: （类型: string）
-       data.items[].defects[].updateTime: （类型: string）
-       data.items[].defects[].isValid: （类型: integer）
-   data.waiting: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/inspection-orders/{id}
- **所属模块：** 检验单主表（统一IQC/IPQC/FQC/OQC检验业务）
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/tools
- **所属模块：** 检验工具（检具）
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/tools
- **所属模块：** 检验工具（检具）
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- toolCode: （类型: string，可选）
- toolName: （类型: string，可选）
- specModel: （类型: string，可选）
- toolType: （类型: string，可选）
- manufacturer: （类型: string，可选）
- useDepartment: （类型: string，可选）
- owner: （类型: string，可选）
- location: （类型: string，可选）
- entryDate: （类型: string，可选）
- remark: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.toolCode: （类型: string）
-   data.toolName: （类型: string）
-   data.specModel: （类型: string）
-   data.toolType: （类型: string）
-   data.manufacturer: （类型: string）
-   data.useDepartment: （类型: string）
-   data.owner: （类型: string）
-   data.location: （类型: string）
-   data.entryDate: （类型: string）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/tools/{id}
- **所属模块：** 检验工具（检具）
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.toolCode: （类型: string）
-   data.toolName: （类型: string）
-   data.specModel: （类型: string）
-   data.toolType: （类型: string）
-   data.manufacturer: （类型: string）
-   data.useDepartment: （类型: string）
-   data.owner: （类型: string）
-   data.location: （类型: string）
-   data.entryDate: （类型: string）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/tools/{id}
- **所属模块：** 检验工具（检具）
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- toolCode: （类型: string，可选）
- toolName: （类型: string，可选）
- specModel: （类型: string，可选）
- toolType: （类型: string，可选）
- manufacturer: （类型: string，可选）
- useDepartment: （类型: string，可选）
- owner: （类型: string，可选）
- location: （类型: string，可选）
- entryDate: （类型: string，可选）
- remark: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.toolCode: （类型: string）
-   data.toolName: （类型: string）
-   data.specModel: （类型: string）
-   data.toolType: （类型: string）
-   data.manufacturer: （类型: string）
-   data.useDepartment: （类型: string）
-   data.owner: （类型: string）
-   data.location: （类型: string）
-   data.entryDate: （类型: string）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/tools/{id}
- **所属模块：** 检验工具（检具）
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/inspection-standards
- **所属模块：** 检验标准
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/inspection-standards
- **所属模块：** 检验标准
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- standardCode: （类型: string，可选）
- standardName: （类型: string，可选）
- standardType: （类型: string，可选）
- version: （类型: string，可选）
- isEffective: （类型: integer，可选）
- remark: （类型: string，可选）
- updateItems: （类型: integer，可选）
- standardItems: （类型: array<object>，可选）
-   standardItems[].id: （类型: integer）
-   standardItems[].standardId: （类型: integer）
-   standardItems[].itemId: （类型: integer）
-   standardItems[].sl: （类型: number）
-   standardItems[].usl: （类型: number）
-   standardItems[].lsl: （类型: number）
-   standardItems[].positiveTol: （类型: number）
-   standardItems[].negativeTol: （类型: number）
-   standardItems[].tenantId: （类型: string）
-   standardItems[].createBy: （类型: string）
-   standardItems[].createTime: （类型: string）
-   standardItems[].updateBy: （类型: string）
-   standardItems[].updateTime: （类型: string）
-   standardItems[].isValid: （类型: integer）
-   standardItems[].inspectionItem: （类型: object）
-     standardItems[].inspectionItem.id: （类型: integer）
-     standardItems[].inspectionItem.itemCode: （类型: string）
-     standardItems[].inspectionItem.itemName: （类型: string）
-     standardItems[].inspectionItem.itemCategoryId: （类型: integer）
-     standardItems[].inspectionItem.itemCategoryName: （类型: string）
-     standardItems[].inspectionItem.inspectionType: （类型: string）
-     standardItems[].inspectionItem.inspectionMethod: （类型: string）
-     standardItems[].inspectionItem.toolId: （类型: integer）
-     standardItems[].inspectionItem.toolName: （类型: string）
-     standardItems[].inspectionItem.inspectionStandardText: （类型: string）
-     standardItems[].inspectionItem.unit: （类型: string）
-     standardItems[].inspectionItem.remark: （类型: string）
-     standardItems[].inspectionItem.tenantId: （类型: string）
-     standardItems[].inspectionItem.createBy: （类型: string）
-     standardItems[].inspectionItem.createTime: （类型: string）
-     standardItems[].inspectionItem.updateBy: （类型: string）
-     standardItems[].inspectionItem.updateTime: （类型: string）
-     standardItems[].inspectionItem.isValid: （类型: integer）
-     standardItems[].inspectionItem.points: （类型: array<object>）
-       standardItems[].inspectionItem.points[].id: （类型: integer）
-       standardItems[].inspectionItem.points[].itemId: （类型: integer）
-       standardItems[].inspectionItem.points[].deviceCode: （类型: string）
-       standardItems[].inspectionItem.points[].pointCode: （类型: string）
-       standardItems[].inspectionItem.points[].pointName: （类型: string）
-       standardItems[].inspectionItem.points[].tenantId: （类型: string）
-       standardItems[].inspectionItem.points[].createBy: （类型: string）
-       standardItems[].inspectionItem.points[].createTime: （类型: string）
-       standardItems[].inspectionItem.points[].updateBy: （类型: string）
-       standardItems[].inspectionItem.points[].updateTime: （类型: string）
-       standardItems[].inspectionItem.points[].isValid: （类型: integer）
- updateMaterials: （类型: integer，可选）
- standardMaterials: （类型: array<object>，可选）
-   standardMaterials[].id: （类型: integer）
-   standardMaterials[].standardId: （类型: integer）
-   standardMaterials[].materialCode: （类型: string）
-   standardMaterials[].materialType: （类型: string）
-   standardMaterials[].tenantId: （类型: string）
-   standardMaterials[].createBy: （类型: string）
-   standardMaterials[].createTime: （类型: string）
-   standardMaterials[].updateBy: （类型: string）
-   standardMaterials[].updateTime: （类型: string）
-   standardMaterials[].isValid: （类型: integer）
- updateTime: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardCode: （类型: string）
-   data.standardName: （类型: string）
-   data.standardType: （类型: string）
-   data.version: （类型: string）
-   data.isEffective: （类型: integer）
-   data.remark: （类型: string）
-   data.updateItems: （类型: integer）
-   data.standardItems: （类型: array<object>）
-     data.standardItems[].id: （类型: integer）
-     data.standardItems[].standardId: （类型: integer）
-     data.standardItems[].itemId: （类型: integer）
-     data.standardItems[].sl: （类型: number）
-     data.standardItems[].usl: （类型: number）
-     data.standardItems[].lsl: （类型: number）
-     data.standardItems[].positiveTol: （类型: number）
-     data.standardItems[].negativeTol: （类型: number）
-     data.standardItems[].tenantId: （类型: string）
-     data.standardItems[].createBy: （类型: string）
-     data.standardItems[].createTime: （类型: string）
-     data.standardItems[].updateBy: （类型: string）
-     data.standardItems[].updateTime: （类型: string）
-     data.standardItems[].isValid: （类型: integer）
-     data.standardItems[].inspectionItem: （类型: object）
-       data.standardItems[].inspectionItem.id: （类型: integer）
-       data.standardItems[].inspectionItem.itemCode: （类型: string）
-       data.standardItems[].inspectionItem.itemName: （类型: string）
-       data.standardItems[].inspectionItem.itemCategoryId: （类型: integer）
-       data.standardItems[].inspectionItem.itemCategoryName: （类型: string）
-       data.standardItems[].inspectionItem.inspectionType: （类型: string）
-       data.standardItems[].inspectionItem.inspectionMethod: （类型: string）
-       data.standardItems[].inspectionItem.toolId: （类型: integer）
-       data.standardItems[].inspectionItem.toolName: （类型: string）
-       data.standardItems[].inspectionItem.inspectionStandardText: （类型: string）
-       data.standardItems[].inspectionItem.unit: （类型: string）
-       data.standardItems[].inspectionItem.remark: （类型: string）
-       data.standardItems[].inspectionItem.tenantId: （类型: string）
-       data.standardItems[].inspectionItem.createBy: （类型: string）
-       data.standardItems[].inspectionItem.createTime: （类型: string）
-       data.standardItems[].inspectionItem.updateBy: （类型: string）
-       data.standardItems[].inspectionItem.updateTime: （类型: string）
-       data.standardItems[].inspectionItem.isValid: （类型: integer）
-       data.standardItems[].inspectionItem.points: （类型: array<object>）
-         data.standardItems[].inspectionItem.points[].id: （类型: integer）
-         data.standardItems[].inspectionItem.points[].itemId: （类型: integer）
-         data.standardItems[].inspectionItem.points[].deviceCode: （类型: string）
-         data.standardItems[].inspectionItem.points[].pointCode: （类型: string）
-         data.standardItems[].inspectionItem.points[].pointName: （类型: string）
-         data.standardItems[].inspectionItem.points[].tenantId: （类型: string）
-         data.standardItems[].inspectionItem.points[].createBy: （类型: string）
-         data.standardItems[].inspectionItem.points[].createTime: （类型: string）
-         data.standardItems[].inspectionItem.points[].updateBy: （类型: string）
-         data.standardItems[].inspectionItem.points[].updateTime: （类型: string）
-         data.standardItems[].inspectionItem.points[].isValid: （类型: integer）
-   data.updateMaterials: （类型: integer）
-   data.standardMaterials: （类型: array<object>）
-     data.standardMaterials[].id: （类型: integer）
-     data.standardMaterials[].standardId: （类型: integer）
-     data.standardMaterials[].materialCode: （类型: string）
-     data.standardMaterials[].materialType: （类型: string）
-     data.standardMaterials[].tenantId: （类型: string）
-     data.standardMaterials[].createBy: （类型: string）
-     data.standardMaterials[].createTime: （类型: string）
-     data.standardMaterials[].updateBy: （类型: string）
-     data.standardMaterials[].updateTime: （类型: string）
-     data.standardMaterials[].isValid: （类型: integer）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：根据物料编码查询检验标准列表

- **路径：** GET /api/v1/inspection-standards/listByMaterialCode
- **所属模块：** 检验标准
- **HTTP 方法：** GET

**功能说明：**
根据物料编码查询检验标准列表

**请求参数：**
- materialCode （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].standardCode: （类型: string）
-   data[].standardName: （类型: string）
-   data[].standardType: （类型: string）
-   data[].version: （类型: string）
-   data[].isEffective: （类型: integer）
-   data[].remark: （类型: string）
-   data[].updateItems: （类型: integer）
-   data[].standardItems: （类型: array<object>）
-     data[].standardItems[].id: （类型: integer）
-     data[].standardItems[].standardId: （类型: integer）
-     data[].standardItems[].itemId: （类型: integer）
-     data[].standardItems[].sl: （类型: number）
-     data[].standardItems[].usl: （类型: number）
-     data[].standardItems[].lsl: （类型: number）
-     data[].standardItems[].positiveTol: （类型: number）
-     data[].standardItems[].negativeTol: （类型: number）
-     data[].standardItems[].tenantId: （类型: string）
-     data[].standardItems[].createBy: （类型: string）
-     data[].standardItems[].createTime: （类型: string）
-     data[].standardItems[].updateBy: （类型: string）
-     data[].standardItems[].updateTime: （类型: string）
-     data[].standardItems[].isValid: （类型: integer）
-     data[].standardItems[].inspectionItem: （类型: object）
-       data[].standardItems[].inspectionItem.id: （类型: integer）
-       data[].standardItems[].inspectionItem.itemCode: （类型: string）
-       data[].standardItems[].inspectionItem.itemName: （类型: string）
-       data[].standardItems[].inspectionItem.itemCategoryId: （类型: integer）
-       data[].standardItems[].inspectionItem.itemCategoryName: （类型: string）
-       data[].standardItems[].inspectionItem.inspectionType: （类型: string）
-       data[].standardItems[].inspectionItem.inspectionMethod: （类型: string）
-       data[].standardItems[].inspectionItem.toolId: （类型: integer）
-       data[].standardItems[].inspectionItem.toolName: （类型: string）
-       data[].standardItems[].inspectionItem.inspectionStandardText: （类型: string）
-       data[].standardItems[].inspectionItem.unit: （类型: string）
-       data[].standardItems[].inspectionItem.remark: （类型: string）
-       data[].standardItems[].inspectionItem.tenantId: （类型: string）
-       data[].standardItems[].inspectionItem.createBy: （类型: string）
-       data[].standardItems[].inspectionItem.createTime: （类型: string）
-       data[].standardItems[].inspectionItem.updateBy: （类型: string）
-       data[].standardItems[].inspectionItem.updateTime: （类型: string）
-       data[].standardItems[].inspectionItem.isValid: （类型: integer）
-       data[].standardItems[].inspectionItem.points: （类型: array<object>）
-         data[].standardItems[].inspectionItem.points[].id: （类型: integer）
-         data[].standardItems[].inspectionItem.points[].itemId: （类型: integer）
-         data[].standardItems[].inspectionItem.points[].deviceCode: （类型: string）
-         data[].standardItems[].inspectionItem.points[].pointCode: （类型: string）
-         data[].standardItems[].inspectionItem.points[].pointName: （类型: string）
-         data[].standardItems[].inspectionItem.points[].tenantId: （类型: string）
-         data[].standardItems[].inspectionItem.points[].createBy: （类型: string）
-         data[].standardItems[].inspectionItem.points[].createTime: （类型: string）
-         data[].standardItems[].inspectionItem.points[].updateBy: （类型: string）
-         data[].standardItems[].inspectionItem.points[].updateTime: （类型: string）
-         data[].standardItems[].inspectionItem.points[].isValid: （类型: integer）
-   data[].updateMaterials: （类型: integer）
-   data[].standardMaterials: （类型: array<object>）
-     data[].standardMaterials[].id: （类型: integer）
-     data[].standardMaterials[].standardId: （类型: integer）
-     data[].standardMaterials[].materialCode: （类型: string）
-     data[].standardMaterials[].materialType: （类型: string）
-     data[].standardMaterials[].tenantId: （类型: string）
-     data[].standardMaterials[].createBy: （类型: string）
-     data[].standardMaterials[].createTime: （类型: string）
-     data[].standardMaterials[].updateBy: （类型: string）
-     data[].standardMaterials[].updateTime: （类型: string）
-     data[].standardMaterials[].isValid: （类型: integer）
-   data[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询检验标准列表"
- "查看检验标准列表列表"
- "有哪些检验标准列表？"
- "列出检验标准列表"

## 接口：查询

- **路径：** GET /api/v1/inspection-standards/{id}
- **所属模块：** 检验标准
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardCode: （类型: string）
-   data.standardName: （类型: string）
-   data.standardType: （类型: string）
-   data.version: （类型: string）
-   data.isEffective: （类型: integer）
-   data.remark: （类型: string）
-   data.updateItems: （类型: integer）
-   data.standardItems: （类型: array<object>）
-     data.standardItems[].id: （类型: integer）
-     data.standardItems[].standardId: （类型: integer）
-     data.standardItems[].itemId: （类型: integer）
-     data.standardItems[].sl: （类型: number）
-     data.standardItems[].usl: （类型: number）
-     data.standardItems[].lsl: （类型: number）
-     data.standardItems[].positiveTol: （类型: number）
-     data.standardItems[].negativeTol: （类型: number）
-     data.standardItems[].tenantId: （类型: string）
-     data.standardItems[].createBy: （类型: string）
-     data.standardItems[].createTime: （类型: string）
-     data.standardItems[].updateBy: （类型: string）
-     data.standardItems[].updateTime: （类型: string）
-     data.standardItems[].isValid: （类型: integer）
-     data.standardItems[].inspectionItem: （类型: object）
-       data.standardItems[].inspectionItem.id: （类型: integer）
-       data.standardItems[].inspectionItem.itemCode: （类型: string）
-       data.standardItems[].inspectionItem.itemName: （类型: string）
-       data.standardItems[].inspectionItem.itemCategoryId: （类型: integer）
-       data.standardItems[].inspectionItem.itemCategoryName: （类型: string）
-       data.standardItems[].inspectionItem.inspectionType: （类型: string）
-       data.standardItems[].inspectionItem.inspectionMethod: （类型: string）
-       data.standardItems[].inspectionItem.toolId: （类型: integer）
-       data.standardItems[].inspectionItem.toolName: （类型: string）
-       data.standardItems[].inspectionItem.inspectionStandardText: （类型: string）
-       data.standardItems[].inspectionItem.unit: （类型: string）
-       data.standardItems[].inspectionItem.remark: （类型: string）
-       data.standardItems[].inspectionItem.tenantId: （类型: string）
-       data.standardItems[].inspectionItem.createBy: （类型: string）
-       data.standardItems[].inspectionItem.createTime: （类型: string）
-       data.standardItems[].inspectionItem.updateBy: （类型: string）
-       data.standardItems[].inspectionItem.updateTime: （类型: string）
-       data.standardItems[].inspectionItem.isValid: （类型: integer）
-       data.standardItems[].inspectionItem.points: （类型: array<object>）
-         data.standardItems[].inspectionItem.points[].id: （类型: integer）
-         data.standardItems[].inspectionItem.points[].itemId: （类型: integer）
-         data.standardItems[].inspectionItem.points[].deviceCode: （类型: string）
-         data.standardItems[].inspectionItem.points[].pointCode: （类型: string）
-         data.standardItems[].inspectionItem.points[].pointName: （类型: string）
-         data.standardItems[].inspectionItem.points[].tenantId: （类型: string）
-         data.standardItems[].inspectionItem.points[].createBy: （类型: string）
-         data.standardItems[].inspectionItem.points[].createTime: （类型: string）
-         data.standardItems[].inspectionItem.points[].updateBy: （类型: string）
-         data.standardItems[].inspectionItem.points[].updateTime: （类型: string）
-         data.standardItems[].inspectionItem.points[].isValid: （类型: integer）
-   data.updateMaterials: （类型: integer）
-   data.standardMaterials: （类型: array<object>）
-     data.standardMaterials[].id: （类型: integer）
-     data.standardMaterials[].standardId: （类型: integer）
-     data.standardMaterials[].materialCode: （类型: string）
-     data.standardMaterials[].materialType: （类型: string）
-     data.standardMaterials[].tenantId: （类型: string）
-     data.standardMaterials[].createBy: （类型: string）
-     data.standardMaterials[].createTime: （类型: string）
-     data.standardMaterials[].updateBy: （类型: string）
-     data.standardMaterials[].updateTime: （类型: string）
-     data.standardMaterials[].isValid: （类型: integer）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/inspection-standards/{id}
- **所属模块：** 检验标准
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- standardCode: （类型: string，可选）
- standardName: （类型: string，可选）
- standardType: （类型: string，可选）
- version: （类型: string，可选）
- isEffective: （类型: integer，可选）
- remark: （类型: string，可选）
- updateItems: （类型: integer，可选）
- standardItems: （类型: array<object>，可选）
-   standardItems[].id: （类型: integer）
-   standardItems[].standardId: （类型: integer）
-   standardItems[].itemId: （类型: integer）
-   standardItems[].sl: （类型: number）
-   standardItems[].usl: （类型: number）
-   standardItems[].lsl: （类型: number）
-   standardItems[].positiveTol: （类型: number）
-   standardItems[].negativeTol: （类型: number）
-   standardItems[].tenantId: （类型: string）
-   standardItems[].createBy: （类型: string）
-   standardItems[].createTime: （类型: string）
-   standardItems[].updateBy: （类型: string）
-   standardItems[].updateTime: （类型: string）
-   standardItems[].isValid: （类型: integer）
-   standardItems[].inspectionItem: （类型: object）
-     standardItems[].inspectionItem.id: （类型: integer）
-     standardItems[].inspectionItem.itemCode: （类型: string）
-     standardItems[].inspectionItem.itemName: （类型: string）
-     standardItems[].inspectionItem.itemCategoryId: （类型: integer）
-     standardItems[].inspectionItem.itemCategoryName: （类型: string）
-     standardItems[].inspectionItem.inspectionType: （类型: string）
-     standardItems[].inspectionItem.inspectionMethod: （类型: string）
-     standardItems[].inspectionItem.toolId: （类型: integer）
-     standardItems[].inspectionItem.toolName: （类型: string）
-     standardItems[].inspectionItem.inspectionStandardText: （类型: string）
-     standardItems[].inspectionItem.unit: （类型: string）
-     standardItems[].inspectionItem.remark: （类型: string）
-     standardItems[].inspectionItem.tenantId: （类型: string）
-     standardItems[].inspectionItem.createBy: （类型: string）
-     standardItems[].inspectionItem.createTime: （类型: string）
-     standardItems[].inspectionItem.updateBy: （类型: string）
-     standardItems[].inspectionItem.updateTime: （类型: string）
-     standardItems[].inspectionItem.isValid: （类型: integer）
-     standardItems[].inspectionItem.points: （类型: array<object>）
-       standardItems[].inspectionItem.points[].id: （类型: integer）
-       standardItems[].inspectionItem.points[].itemId: （类型: integer）
-       standardItems[].inspectionItem.points[].deviceCode: （类型: string）
-       standardItems[].inspectionItem.points[].pointCode: （类型: string）
-       standardItems[].inspectionItem.points[].pointName: （类型: string）
-       standardItems[].inspectionItem.points[].tenantId: （类型: string）
-       standardItems[].inspectionItem.points[].createBy: （类型: string）
-       standardItems[].inspectionItem.points[].createTime: （类型: string）
-       standardItems[].inspectionItem.points[].updateBy: （类型: string）
-       standardItems[].inspectionItem.points[].updateTime: （类型: string）
-       standardItems[].inspectionItem.points[].isValid: （类型: integer）
- updateMaterials: （类型: integer，可选）
- standardMaterials: （类型: array<object>，可选）
-   standardMaterials[].id: （类型: integer）
-   standardMaterials[].standardId: （类型: integer）
-   standardMaterials[].materialCode: （类型: string）
-   standardMaterials[].materialType: （类型: string）
-   standardMaterials[].tenantId: （类型: string）
-   standardMaterials[].createBy: （类型: string）
-   standardMaterials[].createTime: （类型: string）
-   standardMaterials[].updateBy: （类型: string）
-   standardMaterials[].updateTime: （类型: string）
-   standardMaterials[].isValid: （类型: integer）
- updateTime: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardCode: （类型: string）
-   data.standardName: （类型: string）
-   data.standardType: （类型: string）
-   data.version: （类型: string）
-   data.isEffective: （类型: integer）
-   data.remark: （类型: string）
-   data.updateItems: （类型: integer）
-   data.standardItems: （类型: array<object>）
-     data.standardItems[].id: （类型: integer）
-     data.standardItems[].standardId: （类型: integer）
-     data.standardItems[].itemId: （类型: integer）
-     data.standardItems[].sl: （类型: number）
-     data.standardItems[].usl: （类型: number）
-     data.standardItems[].lsl: （类型: number）
-     data.standardItems[].positiveTol: （类型: number）
-     data.standardItems[].negativeTol: （类型: number）
-     data.standardItems[].tenantId: （类型: string）
-     data.standardItems[].createBy: （类型: string）
-     data.standardItems[].createTime: （类型: string）
-     data.standardItems[].updateBy: （类型: string）
-     data.standardItems[].updateTime: （类型: string）
-     data.standardItems[].isValid: （类型: integer）
-     data.standardItems[].inspectionItem: （类型: object）
-       data.standardItems[].inspectionItem.id: （类型: integer）
-       data.standardItems[].inspectionItem.itemCode: （类型: string）
-       data.standardItems[].inspectionItem.itemName: （类型: string）
-       data.standardItems[].inspectionItem.itemCategoryId: （类型: integer）
-       data.standardItems[].inspectionItem.itemCategoryName: （类型: string）
-       data.standardItems[].inspectionItem.inspectionType: （类型: string）
-       data.standardItems[].inspectionItem.inspectionMethod: （类型: string）
-       data.standardItems[].inspectionItem.toolId: （类型: integer）
-       data.standardItems[].inspectionItem.toolName: （类型: string）
-       data.standardItems[].inspectionItem.inspectionStandardText: （类型: string）
-       data.standardItems[].inspectionItem.unit: （类型: string）
-       data.standardItems[].inspectionItem.remark: （类型: string）
-       data.standardItems[].inspectionItem.tenantId: （类型: string）
-       data.standardItems[].inspectionItem.createBy: （类型: string）
-       data.standardItems[].inspectionItem.createTime: （类型: string）
-       data.standardItems[].inspectionItem.updateBy: （类型: string）
-       data.standardItems[].inspectionItem.updateTime: （类型: string）
-       data.standardItems[].inspectionItem.isValid: （类型: integer）
-       data.standardItems[].inspectionItem.points: （类型: array<object>）
-         data.standardItems[].inspectionItem.points[].id: （类型: integer）
-         data.standardItems[].inspectionItem.points[].itemId: （类型: integer）
-         data.standardItems[].inspectionItem.points[].deviceCode: （类型: string）
-         data.standardItems[].inspectionItem.points[].pointCode: （类型: string）
-         data.standardItems[].inspectionItem.points[].pointName: （类型: string）
-         data.standardItems[].inspectionItem.points[].tenantId: （类型: string）
-         data.standardItems[].inspectionItem.points[].createBy: （类型: string）
-         data.standardItems[].inspectionItem.points[].createTime: （类型: string）
-         data.standardItems[].inspectionItem.points[].updateBy: （类型: string）
-         data.standardItems[].inspectionItem.points[].updateTime: （类型: string）
-         data.standardItems[].inspectionItem.points[].isValid: （类型: integer）
-   data.updateMaterials: （类型: integer）
-   data.standardMaterials: （类型: array<object>）
-     data.standardMaterials[].id: （类型: integer）
-     data.standardMaterials[].standardId: （类型: integer）
-     data.standardMaterials[].materialCode: （类型: string）
-     data.standardMaterials[].materialType: （类型: string）
-     data.standardMaterials[].tenantId: （类型: string）
-     data.standardMaterials[].createBy: （类型: string）
-     data.standardMaterials[].createTime: （类型: string）
-     data.standardMaterials[].updateBy: （类型: string）
-     data.standardMaterials[].updateTime: （类型: string）
-     data.standardMaterials[].isValid: （类型: integer）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/inspection-standards/{id}
- **所属模块：** 检验标准
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/defects
- **所属模块：** 检验缺陷
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/defects
- **所属模块：** 检验缺陷
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- defectCode: （类型: string，可选）
- defectName: （类型: string，可选）
- defectTypeId: （类型: integer，可选）
- defectTypeName: （类型: string，可选）
- defectLevel: （类型: string，可选）
- remark: （类型: string，可选）
- itemNames: （类型: string，可选）
- items: （类型: array<object>，可选）
-   items[].id: （类型: integer）
-   items[].defectId: （类型: integer）
-   items[].itemId: （类型: integer）
-   items[].tenantId: （类型: string）
-   items[].createBy: （类型: string）
-   items[].createTime: （类型: string）
-   items[].updateBy: （类型: string）
-   items[].updateTime: （类型: string）
-   items[].isValid: （类型: integer）
-   items[].defect: （类型: object）
-     items[].defect.id: （类型: integer）
-     items[].defect.defectCode: （类型: string）
-     items[].defect.defectName: （类型: string）
-     items[].defect.defectTypeId: （类型: integer）
-     items[].defect.defectTypeName: （类型: string）
-     items[].defect.defectLevel: （类型: string）
-     items[].defect.remark: （类型: string）
-     items[].defect.itemNames: （类型: string）
-     items[].defect.items: （类型: array<object>）
-       items[].defect.items[].id: （类型: integer）
-       items[].defect.items[].defectId: （类型: integer）
-       items[].defect.items[].itemId: （类型: integer）
-       items[].defect.items[].tenantId: （类型: string）
-       items[].defect.items[].createBy: （类型: string）
-       items[].defect.items[].createTime: （类型: string）
-       items[].defect.items[].updateBy: （类型: string）
-       items[].defect.items[].updateTime: （类型: string）
-       items[].defect.items[].isValid: （类型: integer）
-       items[].defect.items[].defect: （类型: object）
-         items[].defect.items[].defect.id: （类型: integer）
-         items[].defect.items[].defect.defectCode: （类型: string）
-         items[].defect.items[].defect.defectName: （类型: string）
-         items[].defect.items[].defect.defectTypeId: （类型: integer）
-         items[].defect.items[].defect.defectTypeName: （类型: string）
-         items[].defect.items[].defect.defectLevel: （类型: string）
-         items[].defect.items[].defect.remark: （类型: string）
-         items[].defect.items[].defect.itemNames: （类型: string）
-         items[].defect.items[].defect.items: （类型: array<object>）
-         items[].defect.items[].defect.inspectionItems: （类型: array<object>）
-     items[].defect.inspectionItems: （类型: array<object>）
-       items[].defect.inspectionItems[].id: （类型: integer）
-       items[].defect.inspectionItems[].itemCode: （类型: string）
-       items[].defect.inspectionItems[].itemName: （类型: string）
-       items[].defect.inspectionItems[].itemCategoryId: （类型: integer）
-       items[].defect.inspectionItems[].itemCategoryName: （类型: string）
-       items[].defect.inspectionItems[].inspectionType: （类型: string）
-       items[].defect.inspectionItems[].inspectionMethod: （类型: string）
-       items[].defect.inspectionItems[].toolId: （类型: integer）
-       items[].defect.inspectionItems[].toolName: （类型: string）
-       items[].defect.inspectionItems[].inspectionStandardText: （类型: string）
-       items[].defect.inspectionItems[].unit: （类型: string）
-       items[].defect.inspectionItems[].remark: （类型: string）
-       items[].defect.inspectionItems[].tenantId: （类型: string）
-       items[].defect.inspectionItems[].createBy: （类型: string）
-       items[].defect.inspectionItems[].createTime: （类型: string）
-       items[].defect.inspectionItems[].updateBy: （类型: string）
-       items[].defect.inspectionItems[].updateTime: （类型: string）
-       items[].defect.inspectionItems[].isValid: （类型: integer）
-       items[].defect.inspectionItems[].points: （类型: array<object>）
-         items[].defect.inspectionItems[].points[].id: （类型: integer）
-         items[].defect.inspectionItems[].points[].itemId: （类型: integer）
-         items[].defect.inspectionItems[].points[].deviceCode: （类型: string）
-         items[].defect.inspectionItems[].points[].pointCode: （类型: string）
-         items[].defect.inspectionItems[].points[].pointName: （类型: string）
-         items[].defect.inspectionItems[].points[].tenantId: （类型: string）
-         items[].defect.inspectionItems[].points[].createBy: （类型: string）
-         items[].defect.inspectionItems[].points[].createTime: （类型: string）
-         items[].defect.inspectionItems[].points[].updateBy: （类型: string）
-         items[].defect.inspectionItems[].points[].updateTime: （类型: string）
-         items[].defect.inspectionItems[].points[].isValid: （类型: integer）
- inspectionItems: （类型: array<object>，可选）
-   inspectionItems[].id: （类型: integer）
-   inspectionItems[].itemCode: （类型: string）
-   inspectionItems[].itemName: （类型: string）
-   inspectionItems[].itemCategoryId: （类型: integer）
-   inspectionItems[].itemCategoryName: （类型: string）
-   inspectionItems[].inspectionType: （类型: string）
-   inspectionItems[].inspectionMethod: （类型: string）
-   inspectionItems[].toolId: （类型: integer）
-   inspectionItems[].toolName: （类型: string）
-   inspectionItems[].inspectionStandardText: （类型: string）
-   inspectionItems[].unit: （类型: string）
-   inspectionItems[].remark: （类型: string）
-   inspectionItems[].tenantId: （类型: string）
-   inspectionItems[].createBy: （类型: string）
-   inspectionItems[].createTime: （类型: string）
-   inspectionItems[].updateBy: （类型: string）
-   inspectionItems[].updateTime: （类型: string）
-   inspectionItems[].isValid: （类型: integer）
-   inspectionItems[].points: （类型: array<object>）
-     inspectionItems[].points[].id: （类型: integer）
-     inspectionItems[].points[].itemId: （类型: integer）
-     inspectionItems[].points[].deviceCode: （类型: string）
-     inspectionItems[].points[].pointCode: （类型: string）
-     inspectionItems[].points[].pointName: （类型: string）
-     inspectionItems[].points[].tenantId: （类型: string）
-     inspectionItems[].points[].createBy: （类型: string）
-     inspectionItems[].points[].createTime: （类型: string）
-     inspectionItems[].points[].updateBy: （类型: string）
-     inspectionItems[].points[].updateTime: （类型: string）
-     inspectionItems[].points[].isValid: （类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.defectCode: （类型: string）
-   data.defectName: （类型: string）
-   data.defectTypeId: （类型: integer）
-   data.defectTypeName: （类型: string）
-   data.defectLevel: （类型: string）
-   data.remark: （类型: string）
-   data.itemNames: （类型: string）
-   data.items: （类型: array<object>）
-     data.items[].id: （类型: integer）
-     data.items[].defectId: （类型: integer）
-     data.items[].itemId: （类型: integer）
-     data.items[].tenantId: （类型: string）
-     data.items[].createBy: （类型: string）
-     data.items[].createTime: （类型: string）
-     data.items[].updateBy: （类型: string）
-     data.items[].updateTime: （类型: string）
-     data.items[].isValid: （类型: integer）
-     data.items[].defect: （类型: object）
-       data.items[].defect.id: （类型: integer）
-       data.items[].defect.defectCode: （类型: string）
-       data.items[].defect.defectName: （类型: string）
-       data.items[].defect.defectTypeId: （类型: integer）
-       data.items[].defect.defectTypeName: （类型: string）
-       data.items[].defect.defectLevel: （类型: string）
-       data.items[].defect.remark: （类型: string）
-       data.items[].defect.itemNames: （类型: string）
-       data.items[].defect.items: （类型: array<object>）
-         data.items[].defect.items[].id: （类型: integer）
-         data.items[].defect.items[].defectId: （类型: integer）
-         data.items[].defect.items[].itemId: （类型: integer）
-         data.items[].defect.items[].tenantId: （类型: string）
-         data.items[].defect.items[].createBy: （类型: string）
-         data.items[].defect.items[].createTime: （类型: string）
-         data.items[].defect.items[].updateBy: （类型: string）
-         data.items[].defect.items[].updateTime: （类型: string）
-         data.items[].defect.items[].isValid: （类型: integer）
-         data.items[].defect.items[].defect: （类型: object）
-       data.items[].defect.inspectionItems: （类型: array<object>）
-         data.items[].defect.inspectionItems[].id: （类型: integer）
-         data.items[].defect.inspectionItems[].itemCode: （类型: string）
-         data.items[].defect.inspectionItems[].itemName: （类型: string）
-         data.items[].defect.inspectionItems[].itemCategoryId: （类型: integer）
-         data.items[].defect.inspectionItems[].itemCategoryName: （类型: string）
-         data.items[].defect.inspectionItems[].inspectionType: （类型: string）
-         data.items[].defect.inspectionItems[].inspectionMethod: （类型: string）
-         data.items[].defect.inspectionItems[].toolId: （类型: integer）
-         data.items[].defect.inspectionItems[].toolName: （类型: string）
-         data.items[].defect.inspectionItems[].inspectionStandardText: （类型: string）
-         data.items[].defect.inspectionItems[].unit: （类型: string）
-         data.items[].defect.inspectionItems[].remark: （类型: string）
-         data.items[].defect.inspectionItems[].tenantId: （类型: string）
-         data.items[].defect.inspectionItems[].createBy: （类型: string）
-         data.items[].defect.inspectionItems[].createTime: （类型: string）
-         data.items[].defect.inspectionItems[].updateBy: （类型: string）
-         data.items[].defect.inspectionItems[].updateTime: （类型: string）
-         data.items[].defect.inspectionItems[].isValid: （类型: integer）
-         data.items[].defect.inspectionItems[].points: （类型: array<object>）
-   data.inspectionItems: （类型: array<object>）
-     data.inspectionItems[].id: （类型: integer）
-     data.inspectionItems[].itemCode: （类型: string）
-     data.inspectionItems[].itemName: （类型: string）
-     data.inspectionItems[].itemCategoryId: （类型: integer）
-     data.inspectionItems[].itemCategoryName: （类型: string）
-     data.inspectionItems[].inspectionType: （类型: string）
-     data.inspectionItems[].inspectionMethod: （类型: string）
-     data.inspectionItems[].toolId: （类型: integer）
-     data.inspectionItems[].toolName: （类型: string）
-     data.inspectionItems[].inspectionStandardText: （类型: string）
-     data.inspectionItems[].unit: （类型: string）
-     data.inspectionItems[].remark: （类型: string）
-     data.inspectionItems[].tenantId: （类型: string）
-     data.inspectionItems[].createBy: （类型: string）
-     data.inspectionItems[].createTime: （类型: string）
-     data.inspectionItems[].updateBy: （类型: string）
-     data.inspectionItems[].updateTime: （类型: string）
-     data.inspectionItems[].isValid: （类型: integer）
-     data.inspectionItems[].points: （类型: array<object>）
-       data.inspectionItems[].points[].id: （类型: integer）
-       data.inspectionItems[].points[].itemId: （类型: integer）
-       data.inspectionItems[].points[].deviceCode: （类型: string）
-       data.inspectionItems[].points[].pointCode: （类型: string）
-       data.inspectionItems[].points[].pointName: （类型: string）
-       data.inspectionItems[].points[].tenantId: （类型: string）
-       data.inspectionItems[].points[].createBy: （类型: string）
-       data.inspectionItems[].points[].createTime: （类型: string）
-       data.inspectionItems[].points[].updateBy: （类型: string）
-       data.inspectionItems[].points[].updateTime: （类型: string）
-       data.inspectionItems[].points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/defects/{id}
- **所属模块：** 检验缺陷
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.defectCode: （类型: string）
-   data.defectName: （类型: string）
-   data.defectTypeId: （类型: integer）
-   data.defectTypeName: （类型: string）
-   data.defectLevel: （类型: string）
-   data.remark: （类型: string）
-   data.itemNames: （类型: string）
-   data.items: （类型: array<object>）
-     data.items[].id: （类型: integer）
-     data.items[].defectId: （类型: integer）
-     data.items[].itemId: （类型: integer）
-     data.items[].tenantId: （类型: string）
-     data.items[].createBy: （类型: string）
-     data.items[].createTime: （类型: string）
-     data.items[].updateBy: （类型: string）
-     data.items[].updateTime: （类型: string）
-     data.items[].isValid: （类型: integer）
-     data.items[].defect: （类型: object）
-       data.items[].defect.id: （类型: integer）
-       data.items[].defect.defectCode: （类型: string）
-       data.items[].defect.defectName: （类型: string）
-       data.items[].defect.defectTypeId: （类型: integer）
-       data.items[].defect.defectTypeName: （类型: string）
-       data.items[].defect.defectLevel: （类型: string）
-       data.items[].defect.remark: （类型: string）
-       data.items[].defect.itemNames: （类型: string）
-       data.items[].defect.items: （类型: array<object>）
-         data.items[].defect.items[].id: （类型: integer）
-         data.items[].defect.items[].defectId: （类型: integer）
-         data.items[].defect.items[].itemId: （类型: integer）
-         data.items[].defect.items[].tenantId: （类型: string）
-         data.items[].defect.items[].createBy: （类型: string）
-         data.items[].defect.items[].createTime: （类型: string）
-         data.items[].defect.items[].updateBy: （类型: string）
-         data.items[].defect.items[].updateTime: （类型: string）
-         data.items[].defect.items[].isValid: （类型: integer）
-         data.items[].defect.items[].defect: （类型: object）
-       data.items[].defect.inspectionItems: （类型: array<object>）
-         data.items[].defect.inspectionItems[].id: （类型: integer）
-         data.items[].defect.inspectionItems[].itemCode: （类型: string）
-         data.items[].defect.inspectionItems[].itemName: （类型: string）
-         data.items[].defect.inspectionItems[].itemCategoryId: （类型: integer）
-         data.items[].defect.inspectionItems[].itemCategoryName: （类型: string）
-         data.items[].defect.inspectionItems[].inspectionType: （类型: string）
-         data.items[].defect.inspectionItems[].inspectionMethod: （类型: string）
-         data.items[].defect.inspectionItems[].toolId: （类型: integer）
-         data.items[].defect.inspectionItems[].toolName: （类型: string）
-         data.items[].defect.inspectionItems[].inspectionStandardText: （类型: string）
-         data.items[].defect.inspectionItems[].unit: （类型: string）
-         data.items[].defect.inspectionItems[].remark: （类型: string）
-         data.items[].defect.inspectionItems[].tenantId: （类型: string）
-         data.items[].defect.inspectionItems[].createBy: （类型: string）
-         data.items[].defect.inspectionItems[].createTime: （类型: string）
-         data.items[].defect.inspectionItems[].updateBy: （类型: string）
-         data.items[].defect.inspectionItems[].updateTime: （类型: string）
-         data.items[].defect.inspectionItems[].isValid: （类型: integer）
-         data.items[].defect.inspectionItems[].points: （类型: array<object>）
-   data.inspectionItems: （类型: array<object>）
-     data.inspectionItems[].id: （类型: integer）
-     data.inspectionItems[].itemCode: （类型: string）
-     data.inspectionItems[].itemName: （类型: string）
-     data.inspectionItems[].itemCategoryId: （类型: integer）
-     data.inspectionItems[].itemCategoryName: （类型: string）
-     data.inspectionItems[].inspectionType: （类型: string）
-     data.inspectionItems[].inspectionMethod: （类型: string）
-     data.inspectionItems[].toolId: （类型: integer）
-     data.inspectionItems[].toolName: （类型: string）
-     data.inspectionItems[].inspectionStandardText: （类型: string）
-     data.inspectionItems[].unit: （类型: string）
-     data.inspectionItems[].remark: （类型: string）
-     data.inspectionItems[].tenantId: （类型: string）
-     data.inspectionItems[].createBy: （类型: string）
-     data.inspectionItems[].createTime: （类型: string）
-     data.inspectionItems[].updateBy: （类型: string）
-     data.inspectionItems[].updateTime: （类型: string）
-     data.inspectionItems[].isValid: （类型: integer）
-     data.inspectionItems[].points: （类型: array<object>）
-       data.inspectionItems[].points[].id: （类型: integer）
-       data.inspectionItems[].points[].itemId: （类型: integer）
-       data.inspectionItems[].points[].deviceCode: （类型: string）
-       data.inspectionItems[].points[].pointCode: （类型: string）
-       data.inspectionItems[].points[].pointName: （类型: string）
-       data.inspectionItems[].points[].tenantId: （类型: string）
-       data.inspectionItems[].points[].createBy: （类型: string）
-       data.inspectionItems[].points[].createTime: （类型: string）
-       data.inspectionItems[].points[].updateBy: （类型: string）
-       data.inspectionItems[].points[].updateTime: （类型: string）
-       data.inspectionItems[].points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/defects/{id}
- **所属模块：** 检验缺陷
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- defectCode: （类型: string，可选）
- defectName: （类型: string，可选）
- defectTypeId: （类型: integer，可选）
- defectTypeName: （类型: string，可选）
- defectLevel: （类型: string，可选）
- remark: （类型: string，可选）
- itemNames: （类型: string，可选）
- items: （类型: array<object>，可选）
-   items[].id: （类型: integer）
-   items[].defectId: （类型: integer）
-   items[].itemId: （类型: integer）
-   items[].tenantId: （类型: string）
-   items[].createBy: （类型: string）
-   items[].createTime: （类型: string）
-   items[].updateBy: （类型: string）
-   items[].updateTime: （类型: string）
-   items[].isValid: （类型: integer）
-   items[].defect: （类型: object）
-     items[].defect.id: （类型: integer）
-     items[].defect.defectCode: （类型: string）
-     items[].defect.defectName: （类型: string）
-     items[].defect.defectTypeId: （类型: integer）
-     items[].defect.defectTypeName: （类型: string）
-     items[].defect.defectLevel: （类型: string）
-     items[].defect.remark: （类型: string）
-     items[].defect.itemNames: （类型: string）
-     items[].defect.items: （类型: array<object>）
-       items[].defect.items[].id: （类型: integer）
-       items[].defect.items[].defectId: （类型: integer）
-       items[].defect.items[].itemId: （类型: integer）
-       items[].defect.items[].tenantId: （类型: string）
-       items[].defect.items[].createBy: （类型: string）
-       items[].defect.items[].createTime: （类型: string）
-       items[].defect.items[].updateBy: （类型: string）
-       items[].defect.items[].updateTime: （类型: string）
-       items[].defect.items[].isValid: （类型: integer）
-       items[].defect.items[].defect: （类型: object）
-         items[].defect.items[].defect.id: （类型: integer）
-         items[].defect.items[].defect.defectCode: （类型: string）
-         items[].defect.items[].defect.defectName: （类型: string）
-         items[].defect.items[].defect.defectTypeId: （类型: integer）
-         items[].defect.items[].defect.defectTypeName: （类型: string）
-         items[].defect.items[].defect.defectLevel: （类型: string）
-         items[].defect.items[].defect.remark: （类型: string）
-         items[].defect.items[].defect.itemNames: （类型: string）
-         items[].defect.items[].defect.items: （类型: array<object>）
-         items[].defect.items[].defect.inspectionItems: （类型: array<object>）
-     items[].defect.inspectionItems: （类型: array<object>）
-       items[].defect.inspectionItems[].id: （类型: integer）
-       items[].defect.inspectionItems[].itemCode: （类型: string）
-       items[].defect.inspectionItems[].itemName: （类型: string）
-       items[].defect.inspectionItems[].itemCategoryId: （类型: integer）
-       items[].defect.inspectionItems[].itemCategoryName: （类型: string）
-       items[].defect.inspectionItems[].inspectionType: （类型: string）
-       items[].defect.inspectionItems[].inspectionMethod: （类型: string）
-       items[].defect.inspectionItems[].toolId: （类型: integer）
-       items[].defect.inspectionItems[].toolName: （类型: string）
-       items[].defect.inspectionItems[].inspectionStandardText: （类型: string）
-       items[].defect.inspectionItems[].unit: （类型: string）
-       items[].defect.inspectionItems[].remark: （类型: string）
-       items[].defect.inspectionItems[].tenantId: （类型: string）
-       items[].defect.inspectionItems[].createBy: （类型: string）
-       items[].defect.inspectionItems[].createTime: （类型: string）
-       items[].defect.inspectionItems[].updateBy: （类型: string）
-       items[].defect.inspectionItems[].updateTime: （类型: string）
-       items[].defect.inspectionItems[].isValid: （类型: integer）
-       items[].defect.inspectionItems[].points: （类型: array<object>）
-         items[].defect.inspectionItems[].points[].id: （类型: integer）
-         items[].defect.inspectionItems[].points[].itemId: （类型: integer）
-         items[].defect.inspectionItems[].points[].deviceCode: （类型: string）
-         items[].defect.inspectionItems[].points[].pointCode: （类型: string）
-         items[].defect.inspectionItems[].points[].pointName: （类型: string）
-         items[].defect.inspectionItems[].points[].tenantId: （类型: string）
-         items[].defect.inspectionItems[].points[].createBy: （类型: string）
-         items[].defect.inspectionItems[].points[].createTime: （类型: string）
-         items[].defect.inspectionItems[].points[].updateBy: （类型: string）
-         items[].defect.inspectionItems[].points[].updateTime: （类型: string）
-         items[].defect.inspectionItems[].points[].isValid: （类型: integer）
- inspectionItems: （类型: array<object>，可选）
-   inspectionItems[].id: （类型: integer）
-   inspectionItems[].itemCode: （类型: string）
-   inspectionItems[].itemName: （类型: string）
-   inspectionItems[].itemCategoryId: （类型: integer）
-   inspectionItems[].itemCategoryName: （类型: string）
-   inspectionItems[].inspectionType: （类型: string）
-   inspectionItems[].inspectionMethod: （类型: string）
-   inspectionItems[].toolId: （类型: integer）
-   inspectionItems[].toolName: （类型: string）
-   inspectionItems[].inspectionStandardText: （类型: string）
-   inspectionItems[].unit: （类型: string）
-   inspectionItems[].remark: （类型: string）
-   inspectionItems[].tenantId: （类型: string）
-   inspectionItems[].createBy: （类型: string）
-   inspectionItems[].createTime: （类型: string）
-   inspectionItems[].updateBy: （类型: string）
-   inspectionItems[].updateTime: （类型: string）
-   inspectionItems[].isValid: （类型: integer）
-   inspectionItems[].points: （类型: array<object>）
-     inspectionItems[].points[].id: （类型: integer）
-     inspectionItems[].points[].itemId: （类型: integer）
-     inspectionItems[].points[].deviceCode: （类型: string）
-     inspectionItems[].points[].pointCode: （类型: string）
-     inspectionItems[].points[].pointName: （类型: string）
-     inspectionItems[].points[].tenantId: （类型: string）
-     inspectionItems[].points[].createBy: （类型: string）
-     inspectionItems[].points[].createTime: （类型: string）
-     inspectionItems[].points[].updateBy: （类型: string）
-     inspectionItems[].points[].updateTime: （类型: string）
-     inspectionItems[].points[].isValid: （类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.defectCode: （类型: string）
-   data.defectName: （类型: string）
-   data.defectTypeId: （类型: integer）
-   data.defectTypeName: （类型: string）
-   data.defectLevel: （类型: string）
-   data.remark: （类型: string）
-   data.itemNames: （类型: string）
-   data.items: （类型: array<object>）
-     data.items[].id: （类型: integer）
-     data.items[].defectId: （类型: integer）
-     data.items[].itemId: （类型: integer）
-     data.items[].tenantId: （类型: string）
-     data.items[].createBy: （类型: string）
-     data.items[].createTime: （类型: string）
-     data.items[].updateBy: （类型: string）
-     data.items[].updateTime: （类型: string）
-     data.items[].isValid: （类型: integer）
-     data.items[].defect: （类型: object）
-       data.items[].defect.id: （类型: integer）
-       data.items[].defect.defectCode: （类型: string）
-       data.items[].defect.defectName: （类型: string）
-       data.items[].defect.defectTypeId: （类型: integer）
-       data.items[].defect.defectTypeName: （类型: string）
-       data.items[].defect.defectLevel: （类型: string）
-       data.items[].defect.remark: （类型: string）
-       data.items[].defect.itemNames: （类型: string）
-       data.items[].defect.items: （类型: array<object>）
-         data.items[].defect.items[].id: （类型: integer）
-         data.items[].defect.items[].defectId: （类型: integer）
-         data.items[].defect.items[].itemId: （类型: integer）
-         data.items[].defect.items[].tenantId: （类型: string）
-         data.items[].defect.items[].createBy: （类型: string）
-         data.items[].defect.items[].createTime: （类型: string）
-         data.items[].defect.items[].updateBy: （类型: string）
-         data.items[].defect.items[].updateTime: （类型: string）
-         data.items[].defect.items[].isValid: （类型: integer）
-         data.items[].defect.items[].defect: （类型: object）
-       data.items[].defect.inspectionItems: （类型: array<object>）
-         data.items[].defect.inspectionItems[].id: （类型: integer）
-         data.items[].defect.inspectionItems[].itemCode: （类型: string）
-         data.items[].defect.inspectionItems[].itemName: （类型: string）
-         data.items[].defect.inspectionItems[].itemCategoryId: （类型: integer）
-         data.items[].defect.inspectionItems[].itemCategoryName: （类型: string）
-         data.items[].defect.inspectionItems[].inspectionType: （类型: string）
-         data.items[].defect.inspectionItems[].inspectionMethod: （类型: string）
-         data.items[].defect.inspectionItems[].toolId: （类型: integer）
-         data.items[].defect.inspectionItems[].toolName: （类型: string）
-         data.items[].defect.inspectionItems[].inspectionStandardText: （类型: string）
-         data.items[].defect.inspectionItems[].unit: （类型: string）
-         data.items[].defect.inspectionItems[].remark: （类型: string）
-         data.items[].defect.inspectionItems[].tenantId: （类型: string）
-         data.items[].defect.inspectionItems[].createBy: （类型: string）
-         data.items[].defect.inspectionItems[].createTime: （类型: string）
-         data.items[].defect.inspectionItems[].updateBy: （类型: string）
-         data.items[].defect.inspectionItems[].updateTime: （类型: string）
-         data.items[].defect.inspectionItems[].isValid: （类型: integer）
-         data.items[].defect.inspectionItems[].points: （类型: array<object>）
-   data.inspectionItems: （类型: array<object>）
-     data.inspectionItems[].id: （类型: integer）
-     data.inspectionItems[].itemCode: （类型: string）
-     data.inspectionItems[].itemName: （类型: string）
-     data.inspectionItems[].itemCategoryId: （类型: integer）
-     data.inspectionItems[].itemCategoryName: （类型: string）
-     data.inspectionItems[].inspectionType: （类型: string）
-     data.inspectionItems[].inspectionMethod: （类型: string）
-     data.inspectionItems[].toolId: （类型: integer）
-     data.inspectionItems[].toolName: （类型: string）
-     data.inspectionItems[].inspectionStandardText: （类型: string）
-     data.inspectionItems[].unit: （类型: string）
-     data.inspectionItems[].remark: （类型: string）
-     data.inspectionItems[].tenantId: （类型: string）
-     data.inspectionItems[].createBy: （类型: string）
-     data.inspectionItems[].createTime: （类型: string）
-     data.inspectionItems[].updateBy: （类型: string）
-     data.inspectionItems[].updateTime: （类型: string）
-     data.inspectionItems[].isValid: （类型: integer）
-     data.inspectionItems[].points: （类型: array<object>）
-       data.inspectionItems[].points[].id: （类型: integer）
-       data.inspectionItems[].points[].itemId: （类型: integer）
-       data.inspectionItems[].points[].deviceCode: （类型: string）
-       data.inspectionItems[].points[].pointCode: （类型: string）
-       data.inspectionItems[].points[].pointName: （类型: string）
-       data.inspectionItems[].points[].tenantId: （类型: string）
-       data.inspectionItems[].points[].createBy: （类型: string）
-       data.inspectionItems[].points[].createTime: （类型: string）
-       data.inspectionItems[].points[].updateBy: （类型: string）
-       data.inspectionItems[].points[].updateTime: （类型: string）
-       data.inspectionItems[].points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/defects/{id}
- **所属模块：** 检验缺陷
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/inspection-items
- **所属模块：** 检验项目
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/inspection-items
- **所属模块：** 检验项目
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- itemCode: （类型: string，可选）
- itemName: （类型: string，可选）
- itemCategoryId: （类型: integer，可选）
- itemCategoryName: （类型: string，可选）
- inspectionType: （类型: string，可选）
- inspectionMethod: （类型: string，可选）
- toolId: （类型: integer，可选）
- toolName: （类型: string，可选）
- inspectionStandardText: （类型: string，可选）
- unit: （类型: string，可选）
- remark: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）
- points: （类型: array<object>，可选）
-   points[].id: （类型: integer）
-   points[].itemId: （类型: integer）
-   points[].deviceCode: （类型: string）
-   points[].pointCode: （类型: string）
-   points[].pointName: （类型: string）
-   points[].tenantId: （类型: string）
-   points[].createBy: （类型: string）
-   points[].createTime: （类型: string）
-   points[].updateBy: （类型: string）
-   points[].updateTime: （类型: string）
-   points[].isValid: （类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.itemCode: （类型: string）
-   data.itemName: （类型: string）
-   data.itemCategoryId: （类型: integer）
-   data.itemCategoryName: （类型: string）
-   data.inspectionType: （类型: string）
-   data.inspectionMethod: （类型: string）
-   data.toolId: （类型: integer）
-   data.toolName: （类型: string）
-   data.inspectionStandardText: （类型: string）
-   data.unit: （类型: string）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.points: （类型: array<object>）
-     data.points[].id: （类型: integer）
-     data.points[].itemId: （类型: integer）
-     data.points[].deviceCode: （类型: string）
-     data.points[].pointCode: （类型: string）
-     data.points[].pointName: （类型: string）
-     data.points[].tenantId: （类型: string）
-     data.points[].createBy: （类型: string）
-     data.points[].createTime: （类型: string）
-     data.points[].updateBy: （类型: string）
-     data.points[].updateTime: （类型: string）
-     data.points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/inspection-items/{id}
- **所属模块：** 检验项目
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.itemCode: （类型: string）
-   data.itemName: （类型: string）
-   data.itemCategoryId: （类型: integer）
-   data.itemCategoryName: （类型: string）
-   data.inspectionType: （类型: string）
-   data.inspectionMethod: （类型: string）
-   data.toolId: （类型: integer）
-   data.toolName: （类型: string）
-   data.inspectionStandardText: （类型: string）
-   data.unit: （类型: string）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.points: （类型: array<object>）
-     data.points[].id: （类型: integer）
-     data.points[].itemId: （类型: integer）
-     data.points[].deviceCode: （类型: string）
-     data.points[].pointCode: （类型: string）
-     data.points[].pointName: （类型: string）
-     data.points[].tenantId: （类型: string）
-     data.points[].createBy: （类型: string）
-     data.points[].createTime: （类型: string）
-     data.points[].updateBy: （类型: string）
-     data.points[].updateTime: （类型: string）
-     data.points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/inspection-items/{id}
- **所属模块：** 检验项目
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- itemCode: （类型: string，可选）
- itemName: （类型: string，可选）
- itemCategoryId: （类型: integer，可选）
- itemCategoryName: （类型: string，可选）
- inspectionType: （类型: string，可选）
- inspectionMethod: （类型: string，可选）
- toolId: （类型: integer，可选）
- toolName: （类型: string，可选）
- inspectionStandardText: （类型: string，可选）
- unit: （类型: string，可选）
- remark: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）
- points: （类型: array<object>，可选）
-   points[].id: （类型: integer）
-   points[].itemId: （类型: integer）
-   points[].deviceCode: （类型: string）
-   points[].pointCode: （类型: string）
-   points[].pointName: （类型: string）
-   points[].tenantId: （类型: string）
-   points[].createBy: （类型: string）
-   points[].createTime: （类型: string）
-   points[].updateBy: （类型: string）
-   points[].updateTime: （类型: string）
-   points[].isValid: （类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.itemCode: （类型: string）
-   data.itemName: （类型: string）
-   data.itemCategoryId: （类型: integer）
-   data.itemCategoryName: （类型: string）
-   data.inspectionType: （类型: string）
-   data.inspectionMethod: （类型: string）
-   data.toolId: （类型: integer）
-   data.toolName: （类型: string）
-   data.inspectionStandardText: （类型: string）
-   data.unit: （类型: string）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.points: （类型: array<object>）
-     data.points[].id: （类型: integer）
-     data.points[].itemId: （类型: integer）
-     data.points[].deviceCode: （类型: string）
-     data.points[].pointCode: （类型: string）
-     data.points[].pointName: （类型: string）
-     data.points[].tenantId: （类型: string）
-     data.points[].createBy: （类型: string）
-     data.points[].createTime: （类型: string）
-     data.points[].updateBy: （类型: string）
-     data.points[].updateTime: （类型: string）
-     data.points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/inspection-items/{id}
- **所属模块：** 检验项目
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/defect-types
- **所属模块：** 检验项目分类
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/defect-types
- **所属模块：** 检验项目分类
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- name: （类型: string，可选）
- sort: （类型: integer，可选）
- remark: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.name: （类型: string）
-   data.sort: （类型: integer）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：批量创建

- **路径：** POST /api/v1/defect-types/batch
- **所属模块：** 检验项目分类
- **HTTP 方法：** POST

**功能说明：**
批量创建

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].name: （类型: string）
-   data[].sort: （类型: integer）
-   data[].remark: （类型: string）
-   data[].tenantId: （类型: string）
-   data[].createBy: （类型: string）
-   data[].createTime: （类型: string）
-   data[].updateBy: （类型: string）
-   data[].updateTime: （类型: string）
-   data[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量创建？"
- "批量创建怎么做？"
- "帮我批量创建"

## 接口：查询

- **路径：** GET /api/v1/defect-types/{id}
- **所属模块：** 检验项目分类
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.name: （类型: string）
-   data.sort: （类型: integer）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/defect-types/{id}
- **所属模块：** 检验项目分类
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- name: （类型: string，可选）
- sort: （类型: integer，可选）
- remark: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.name: （类型: string）
-   data.sort: （类型: integer）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/defect-types/{id}
- **所属模块：** 检验项目分类
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/inspection-item-categories
- **所属模块：** 检验项目分类
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/inspection-item-categories
- **所属模块：** 检验项目分类
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- categoryName: （类型: string，可选）
- sort: （类型: integer，可选）
- remark: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.categoryName: （类型: string）
-   data.sort: （类型: integer）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/inspection-item-categories/{id}
- **所属模块：** 检验项目分类
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.categoryName: （类型: string）
-   data.sort: （类型: integer）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/inspection-item-categories/{id}
- **所属模块：** 检验项目分类
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- categoryName: （类型: string，可选）
- sort: （类型: integer，可选）
- remark: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.categoryName: （类型: string）
-   data.sort: （类型: integer）
-   data.remark: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/inspection-item-categories/{id}
- **所属模块：** 检验项目分类
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/inspection-item-device-points
- **所属模块：** 检验项设备测点绑定
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/inspection-item-device-points
- **所属模块：** 检验项设备测点绑定
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- itemId: （类型: integer，可选）
- deviceCode: （类型: string，可选）
- pointCode: （类型: string，可选）
- pointName: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.itemId: （类型: integer）
-   data.deviceCode: （类型: string）
-   data.pointCode: （类型: string）
-   data.pointName: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/inspection-item-device-points/{id}
- **所属模块：** 检验项设备测点绑定
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.itemId: （类型: integer）
-   data.deviceCode: （类型: string）
-   data.pointCode: （类型: string）
-   data.pointName: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/inspection-item-device-points/{id}
- **所属模块：** 检验项设备测点绑定
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- itemId: （类型: integer，可选）
- deviceCode: （类型: string，可选）
- pointCode: （类型: string，可选）
- pointName: （类型: string，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.itemId: （类型: integer）
-   data.deviceCode: （类型: string）
-   data.pointCode: （类型: string）
-   data.pointName: （类型: string）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/inspection-item-device-points/{id}
- **所属模块：** 检验项设备测点绑定
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/defect-items
- **所属模块：** 缺陷检验项
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/defect-items
- **所属模块：** 缺陷检验项
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: （类型: integer，可选）
- defectId: （类型: integer，可选）
- itemId: （类型: integer，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）
- defect: （类型: object，可选）
-   defect.id: （类型: integer）
-   defect.defectCode: （类型: string）
-   defect.defectName: （类型: string）
-   defect.defectTypeId: （类型: integer）
-   defect.defectTypeName: （类型: string）
-   defect.defectLevel: （类型: string）
-   defect.remark: （类型: string）
-   defect.itemNames: （类型: string）
-   defect.items: （类型: array<object>）
-     defect.items[].id: （类型: integer）
-     defect.items[].defectId: （类型: integer）
-     defect.items[].itemId: （类型: integer）
-     defect.items[].tenantId: （类型: string）
-     defect.items[].createBy: （类型: string）
-     defect.items[].createTime: （类型: string）
-     defect.items[].updateBy: （类型: string）
-     defect.items[].updateTime: （类型: string）
-     defect.items[].isValid: （类型: integer）
-     defect.items[].defect: （类型: object）
-       defect.items[].defect.id: （类型: integer）
-       defect.items[].defect.defectCode: （类型: string）
-       defect.items[].defect.defectName: （类型: string）
-       defect.items[].defect.defectTypeId: （类型: integer）
-       defect.items[].defect.defectTypeName: （类型: string）
-       defect.items[].defect.defectLevel: （类型: string）
-       defect.items[].defect.remark: （类型: string）
-       defect.items[].defect.itemNames: （类型: string）
-       defect.items[].defect.items: （类型: array<object>）
-         defect.items[].defect.items[].id: （类型: integer）
-         defect.items[].defect.items[].defectId: （类型: integer）
-         defect.items[].defect.items[].itemId: （类型: integer）
-         defect.items[].defect.items[].tenantId: （类型: string）
-         defect.items[].defect.items[].createBy: （类型: string）
-         defect.items[].defect.items[].createTime: （类型: string）
-         defect.items[].defect.items[].updateBy: （类型: string）
-         defect.items[].defect.items[].updateTime: （类型: string）
-         defect.items[].defect.items[].isValid: （类型: integer）
-         defect.items[].defect.items[].defect: （类型: object）
-       defect.items[].defect.inspectionItems: （类型: array<object>）
-         defect.items[].defect.inspectionItems[].id: （类型: integer）
-         defect.items[].defect.inspectionItems[].itemCode: （类型: string）
-         defect.items[].defect.inspectionItems[].itemName: （类型: string）
-         defect.items[].defect.inspectionItems[].itemCategoryId: （类型: integer）
-         defect.items[].defect.inspectionItems[].itemCategoryName: （类型: string）
-         defect.items[].defect.inspectionItems[].inspectionType: （类型: string）
-         defect.items[].defect.inspectionItems[].inspectionMethod: （类型: string）
-         defect.items[].defect.inspectionItems[].toolId: （类型: integer）
-         defect.items[].defect.inspectionItems[].toolName: （类型: string）
-         defect.items[].defect.inspectionItems[].inspectionStandardText: （类型: string）
-         defect.items[].defect.inspectionItems[].unit: （类型: string）
-         defect.items[].defect.inspectionItems[].remark: （类型: string）
-         defect.items[].defect.inspectionItems[].tenantId: （类型: string）
-         defect.items[].defect.inspectionItems[].createBy: （类型: string）
-         defect.items[].defect.inspectionItems[].createTime: （类型: string）
-         defect.items[].defect.inspectionItems[].updateBy: （类型: string）
-         defect.items[].defect.inspectionItems[].updateTime: （类型: string）
-         defect.items[].defect.inspectionItems[].isValid: （类型: integer）
-         defect.items[].defect.inspectionItems[].points: （类型: array<object>）
-   defect.inspectionItems: （类型: array<object>）
-     defect.inspectionItems[].id: （类型: integer）
-     defect.inspectionItems[].itemCode: （类型: string）
-     defect.inspectionItems[].itemName: （类型: string）
-     defect.inspectionItems[].itemCategoryId: （类型: integer）
-     defect.inspectionItems[].itemCategoryName: （类型: string）
-     defect.inspectionItems[].inspectionType: （类型: string）
-     defect.inspectionItems[].inspectionMethod: （类型: string）
-     defect.inspectionItems[].toolId: （类型: integer）
-     defect.inspectionItems[].toolName: （类型: string）
-     defect.inspectionItems[].inspectionStandardText: （类型: string）
-     defect.inspectionItems[].unit: （类型: string）
-     defect.inspectionItems[].remark: （类型: string）
-     defect.inspectionItems[].tenantId: （类型: string）
-     defect.inspectionItems[].createBy: （类型: string）
-     defect.inspectionItems[].createTime: （类型: string）
-     defect.inspectionItems[].updateBy: （类型: string）
-     defect.inspectionItems[].updateTime: （类型: string）
-     defect.inspectionItems[].isValid: （类型: integer）
-     defect.inspectionItems[].points: （类型: array<object>）
-       defect.inspectionItems[].points[].id: （类型: integer）
-       defect.inspectionItems[].points[].itemId: （类型: integer）
-       defect.inspectionItems[].points[].deviceCode: （类型: string）
-       defect.inspectionItems[].points[].pointCode: （类型: string）
-       defect.inspectionItems[].points[].pointName: （类型: string）
-       defect.inspectionItems[].points[].tenantId: （类型: string）
-       defect.inspectionItems[].points[].createBy: （类型: string）
-       defect.inspectionItems[].points[].createTime: （类型: string）
-       defect.inspectionItems[].points[].updateBy: （类型: string）
-       defect.inspectionItems[].points[].updateTime: （类型: string）
-       defect.inspectionItems[].points[].isValid: （类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.defectId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.defect: （类型: object）
-     data.defect.id: （类型: integer）
-     data.defect.defectCode: （类型: string）
-     data.defect.defectName: （类型: string）
-     data.defect.defectTypeId: （类型: integer）
-     data.defect.defectTypeName: （类型: string）
-     data.defect.defectLevel: （类型: string）
-     data.defect.remark: （类型: string）
-     data.defect.itemNames: （类型: string）
-     data.defect.items: （类型: array<object>）
-       data.defect.items[].id: （类型: integer）
-       data.defect.items[].defectId: （类型: integer）
-       data.defect.items[].itemId: （类型: integer）
-       data.defect.items[].tenantId: （类型: string）
-       data.defect.items[].createBy: （类型: string）
-       data.defect.items[].createTime: （类型: string）
-       data.defect.items[].updateBy: （类型: string）
-       data.defect.items[].updateTime: （类型: string）
-       data.defect.items[].isValid: （类型: integer）
-       data.defect.items[].defect: （类型: object）
-         data.defect.items[].defect.id: （类型: integer）
-         data.defect.items[].defect.defectCode: （类型: string）
-         data.defect.items[].defect.defectName: （类型: string）
-         data.defect.items[].defect.defectTypeId: （类型: integer）
-         data.defect.items[].defect.defectTypeName: （类型: string）
-         data.defect.items[].defect.defectLevel: （类型: string）
-         data.defect.items[].defect.remark: （类型: string）
-         data.defect.items[].defect.itemNames: （类型: string）
-         data.defect.items[].defect.items: （类型: array<object>）
-         data.defect.items[].defect.inspectionItems: （类型: array<object>）
-     data.defect.inspectionItems: （类型: array<object>）
-       data.defect.inspectionItems[].id: （类型: integer）
-       data.defect.inspectionItems[].itemCode: （类型: string）
-       data.defect.inspectionItems[].itemName: （类型: string）
-       data.defect.inspectionItems[].itemCategoryId: （类型: integer）
-       data.defect.inspectionItems[].itemCategoryName: （类型: string）
-       data.defect.inspectionItems[].inspectionType: （类型: string）
-       data.defect.inspectionItems[].inspectionMethod: （类型: string）
-       data.defect.inspectionItems[].toolId: （类型: integer）
-       data.defect.inspectionItems[].toolName: （类型: string）
-       data.defect.inspectionItems[].inspectionStandardText: （类型: string）
-       data.defect.inspectionItems[].unit: （类型: string）
-       data.defect.inspectionItems[].remark: （类型: string）
-       data.defect.inspectionItems[].tenantId: （类型: string）
-       data.defect.inspectionItems[].createBy: （类型: string）
-       data.defect.inspectionItems[].createTime: （类型: string）
-       data.defect.inspectionItems[].updateBy: （类型: string）
-       data.defect.inspectionItems[].updateTime: （类型: string）
-       data.defect.inspectionItems[].isValid: （类型: integer）
-       data.defect.inspectionItems[].points: （类型: array<object>）
-         data.defect.inspectionItems[].points[].id: （类型: integer）
-         data.defect.inspectionItems[].points[].itemId: （类型: integer）
-         data.defect.inspectionItems[].points[].deviceCode: （类型: string）
-         data.defect.inspectionItems[].points[].pointCode: （类型: string）
-         data.defect.inspectionItems[].points[].pointName: （类型: string）
-         data.defect.inspectionItems[].points[].tenantId: （类型: string）
-         data.defect.inspectionItems[].points[].createBy: （类型: string）
-         data.defect.inspectionItems[].points[].createTime: （类型: string）
-         data.defect.inspectionItems[].points[].updateBy: （类型: string）
-         data.defect.inspectionItems[].points[].updateTime: （类型: string）
-         data.defect.inspectionItems[].points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/defect-items/{id}
- **所属模块：** 缺陷检验项
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.defectId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.defect: （类型: object）
-     data.defect.id: （类型: integer）
-     data.defect.defectCode: （类型: string）
-     data.defect.defectName: （类型: string）
-     data.defect.defectTypeId: （类型: integer）
-     data.defect.defectTypeName: （类型: string）
-     data.defect.defectLevel: （类型: string）
-     data.defect.remark: （类型: string）
-     data.defect.itemNames: （类型: string）
-     data.defect.items: （类型: array<object>）
-       data.defect.items[].id: （类型: integer）
-       data.defect.items[].defectId: （类型: integer）
-       data.defect.items[].itemId: （类型: integer）
-       data.defect.items[].tenantId: （类型: string）
-       data.defect.items[].createBy: （类型: string）
-       data.defect.items[].createTime: （类型: string）
-       data.defect.items[].updateBy: （类型: string）
-       data.defect.items[].updateTime: （类型: string）
-       data.defect.items[].isValid: （类型: integer）
-       data.defect.items[].defect: （类型: object）
-         data.defect.items[].defect.id: （类型: integer）
-         data.defect.items[].defect.defectCode: （类型: string）
-         data.defect.items[].defect.defectName: （类型: string）
-         data.defect.items[].defect.defectTypeId: （类型: integer）
-         data.defect.items[].defect.defectTypeName: （类型: string）
-         data.defect.items[].defect.defectLevel: （类型: string）
-         data.defect.items[].defect.remark: （类型: string）
-         data.defect.items[].defect.itemNames: （类型: string）
-         data.defect.items[].defect.items: （类型: array<object>）
-         data.defect.items[].defect.inspectionItems: （类型: array<object>）
-     data.defect.inspectionItems: （类型: array<object>）
-       data.defect.inspectionItems[].id: （类型: integer）
-       data.defect.inspectionItems[].itemCode: （类型: string）
-       data.defect.inspectionItems[].itemName: （类型: string）
-       data.defect.inspectionItems[].itemCategoryId: （类型: integer）
-       data.defect.inspectionItems[].itemCategoryName: （类型: string）
-       data.defect.inspectionItems[].inspectionType: （类型: string）
-       data.defect.inspectionItems[].inspectionMethod: （类型: string）
-       data.defect.inspectionItems[].toolId: （类型: integer）
-       data.defect.inspectionItems[].toolName: （类型: string）
-       data.defect.inspectionItems[].inspectionStandardText: （类型: string）
-       data.defect.inspectionItems[].unit: （类型: string）
-       data.defect.inspectionItems[].remark: （类型: string）
-       data.defect.inspectionItems[].tenantId: （类型: string）
-       data.defect.inspectionItems[].createBy: （类型: string）
-       data.defect.inspectionItems[].createTime: （类型: string）
-       data.defect.inspectionItems[].updateBy: （类型: string）
-       data.defect.inspectionItems[].updateTime: （类型: string）
-       data.defect.inspectionItems[].isValid: （类型: integer）
-       data.defect.inspectionItems[].points: （类型: array<object>）
-         data.defect.inspectionItems[].points[].id: （类型: integer）
-         data.defect.inspectionItems[].points[].itemId: （类型: integer）
-         data.defect.inspectionItems[].points[].deviceCode: （类型: string）
-         data.defect.inspectionItems[].points[].pointCode: （类型: string）
-         data.defect.inspectionItems[].points[].pointName: （类型: string）
-         data.defect.inspectionItems[].points[].tenantId: （类型: string）
-         data.defect.inspectionItems[].points[].createBy: （类型: string）
-         data.defect.inspectionItems[].points[].createTime: （类型: string）
-         data.defect.inspectionItems[].points[].updateBy: （类型: string）
-         data.defect.inspectionItems[].points[].updateTime: （类型: string）
-         data.defect.inspectionItems[].points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/defect-items/{id}
- **所属模块：** 缺陷检验项
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- defectId: （类型: integer，可选）
- itemId: （类型: integer，可选）
- tenantId: （类型: string，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）
- defect: （类型: object，可选）
-   defect.id: （类型: integer）
-   defect.defectCode: （类型: string）
-   defect.defectName: （类型: string）
-   defect.defectTypeId: （类型: integer）
-   defect.defectTypeName: （类型: string）
-   defect.defectLevel: （类型: string）
-   defect.remark: （类型: string）
-   defect.itemNames: （类型: string）
-   defect.items: （类型: array<object>）
-     defect.items[].id: （类型: integer）
-     defect.items[].defectId: （类型: integer）
-     defect.items[].itemId: （类型: integer）
-     defect.items[].tenantId: （类型: string）
-     defect.items[].createBy: （类型: string）
-     defect.items[].createTime: （类型: string）
-     defect.items[].updateBy: （类型: string）
-     defect.items[].updateTime: （类型: string）
-     defect.items[].isValid: （类型: integer）
-     defect.items[].defect: （类型: object）
-       defect.items[].defect.id: （类型: integer）
-       defect.items[].defect.defectCode: （类型: string）
-       defect.items[].defect.defectName: （类型: string）
-       defect.items[].defect.defectTypeId: （类型: integer）
-       defect.items[].defect.defectTypeName: （类型: string）
-       defect.items[].defect.defectLevel: （类型: string）
-       defect.items[].defect.remark: （类型: string）
-       defect.items[].defect.itemNames: （类型: string）
-       defect.items[].defect.items: （类型: array<object>）
-         defect.items[].defect.items[].id: （类型: integer）
-         defect.items[].defect.items[].defectId: （类型: integer）
-         defect.items[].defect.items[].itemId: （类型: integer）
-         defect.items[].defect.items[].tenantId: （类型: string）
-         defect.items[].defect.items[].createBy: （类型: string）
-         defect.items[].defect.items[].createTime: （类型: string）
-         defect.items[].defect.items[].updateBy: （类型: string）
-         defect.items[].defect.items[].updateTime: （类型: string）
-         defect.items[].defect.items[].isValid: （类型: integer）
-         defect.items[].defect.items[].defect: （类型: object）
-       defect.items[].defect.inspectionItems: （类型: array<object>）
-         defect.items[].defect.inspectionItems[].id: （类型: integer）
-         defect.items[].defect.inspectionItems[].itemCode: （类型: string）
-         defect.items[].defect.inspectionItems[].itemName: （类型: string）
-         defect.items[].defect.inspectionItems[].itemCategoryId: （类型: integer）
-         defect.items[].defect.inspectionItems[].itemCategoryName: （类型: string）
-         defect.items[].defect.inspectionItems[].inspectionType: （类型: string）
-         defect.items[].defect.inspectionItems[].inspectionMethod: （类型: string）
-         defect.items[].defect.inspectionItems[].toolId: （类型: integer）
-         defect.items[].defect.inspectionItems[].toolName: （类型: string）
-         defect.items[].defect.inspectionItems[].inspectionStandardText: （类型: string）
-         defect.items[].defect.inspectionItems[].unit: （类型: string）
-         defect.items[].defect.inspectionItems[].remark: （类型: string）
-         defect.items[].defect.inspectionItems[].tenantId: （类型: string）
-         defect.items[].defect.inspectionItems[].createBy: （类型: string）
-         defect.items[].defect.inspectionItems[].createTime: （类型: string）
-         defect.items[].defect.inspectionItems[].updateBy: （类型: string）
-         defect.items[].defect.inspectionItems[].updateTime: （类型: string）
-         defect.items[].defect.inspectionItems[].isValid: （类型: integer）
-         defect.items[].defect.inspectionItems[].points: （类型: array<object>）
-   defect.inspectionItems: （类型: array<object>）
-     defect.inspectionItems[].id: （类型: integer）
-     defect.inspectionItems[].itemCode: （类型: string）
-     defect.inspectionItems[].itemName: （类型: string）
-     defect.inspectionItems[].itemCategoryId: （类型: integer）
-     defect.inspectionItems[].itemCategoryName: （类型: string）
-     defect.inspectionItems[].inspectionType: （类型: string）
-     defect.inspectionItems[].inspectionMethod: （类型: string）
-     defect.inspectionItems[].toolId: （类型: integer）
-     defect.inspectionItems[].toolName: （类型: string）
-     defect.inspectionItems[].inspectionStandardText: （类型: string）
-     defect.inspectionItems[].unit: （类型: string）
-     defect.inspectionItems[].remark: （类型: string）
-     defect.inspectionItems[].tenantId: （类型: string）
-     defect.inspectionItems[].createBy: （类型: string）
-     defect.inspectionItems[].createTime: （类型: string）
-     defect.inspectionItems[].updateBy: （类型: string）
-     defect.inspectionItems[].updateTime: （类型: string）
-     defect.inspectionItems[].isValid: （类型: integer）
-     defect.inspectionItems[].points: （类型: array<object>）
-       defect.inspectionItems[].points[].id: （类型: integer）
-       defect.inspectionItems[].points[].itemId: （类型: integer）
-       defect.inspectionItems[].points[].deviceCode: （类型: string）
-       defect.inspectionItems[].points[].pointCode: （类型: string）
-       defect.inspectionItems[].points[].pointName: （类型: string）
-       defect.inspectionItems[].points[].tenantId: （类型: string）
-       defect.inspectionItems[].points[].createBy: （类型: string）
-       defect.inspectionItems[].points[].createTime: （类型: string）
-       defect.inspectionItems[].points[].updateBy: （类型: string）
-       defect.inspectionItems[].points[].updateTime: （类型: string）
-       defect.inspectionItems[].points[].isValid: （类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.defectId: （类型: integer）
-   data.itemId: （类型: integer）
-   data.tenantId: （类型: string）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.defect: （类型: object）
-     data.defect.id: （类型: integer）
-     data.defect.defectCode: （类型: string）
-     data.defect.defectName: （类型: string）
-     data.defect.defectTypeId: （类型: integer）
-     data.defect.defectTypeName: （类型: string）
-     data.defect.defectLevel: （类型: string）
-     data.defect.remark: （类型: string）
-     data.defect.itemNames: （类型: string）
-     data.defect.items: （类型: array<object>）
-       data.defect.items[].id: （类型: integer）
-       data.defect.items[].defectId: （类型: integer）
-       data.defect.items[].itemId: （类型: integer）
-       data.defect.items[].tenantId: （类型: string）
-       data.defect.items[].createBy: （类型: string）
-       data.defect.items[].createTime: （类型: string）
-       data.defect.items[].updateBy: （类型: string）
-       data.defect.items[].updateTime: （类型: string）
-       data.defect.items[].isValid: （类型: integer）
-       data.defect.items[].defect: （类型: object）
-         data.defect.items[].defect.id: （类型: integer）
-         data.defect.items[].defect.defectCode: （类型: string）
-         data.defect.items[].defect.defectName: （类型: string）
-         data.defect.items[].defect.defectTypeId: （类型: integer）
-         data.defect.items[].defect.defectTypeName: （类型: string）
-         data.defect.items[].defect.defectLevel: （类型: string）
-         data.defect.items[].defect.remark: （类型: string）
-         data.defect.items[].defect.itemNames: （类型: string）
-         data.defect.items[].defect.items: （类型: array<object>）
-         data.defect.items[].defect.inspectionItems: （类型: array<object>）
-     data.defect.inspectionItems: （类型: array<object>）
-       data.defect.inspectionItems[].id: （类型: integer）
-       data.defect.inspectionItems[].itemCode: （类型: string）
-       data.defect.inspectionItems[].itemName: （类型: string）
-       data.defect.inspectionItems[].itemCategoryId: （类型: integer）
-       data.defect.inspectionItems[].itemCategoryName: （类型: string）
-       data.defect.inspectionItems[].inspectionType: （类型: string）
-       data.defect.inspectionItems[].inspectionMethod: （类型: string）
-       data.defect.inspectionItems[].toolId: （类型: integer）
-       data.defect.inspectionItems[].toolName: （类型: string）
-       data.defect.inspectionItems[].inspectionStandardText: （类型: string）
-       data.defect.inspectionItems[].unit: （类型: string）
-       data.defect.inspectionItems[].remark: （类型: string）
-       data.defect.inspectionItems[].tenantId: （类型: string）
-       data.defect.inspectionItems[].createBy: （类型: string）
-       data.defect.inspectionItems[].createTime: （类型: string）
-       data.defect.inspectionItems[].updateBy: （类型: string）
-       data.defect.inspectionItems[].updateTime: （类型: string）
-       data.defect.inspectionItems[].isValid: （类型: integer）
-       data.defect.inspectionItems[].points: （类型: array<object>）
-         data.defect.inspectionItems[].points[].id: （类型: integer）
-         data.defect.inspectionItems[].points[].itemId: （类型: integer）
-         data.defect.inspectionItems[].points[].deviceCode: （类型: string）
-         data.defect.inspectionItems[].points[].pointCode: （类型: string）
-         data.defect.inspectionItems[].points[].pointName: （类型: string）
-         data.defect.inspectionItems[].points[].tenantId: （类型: string）
-         data.defect.inspectionItems[].points[].createBy: （类型: string）
-         data.defect.inspectionItems[].points[].createTime: （类型: string）
-         data.defect.inspectionItems[].points[].updateBy: （类型: string）
-         data.defect.inspectionItems[].points[].updateTime: （类型: string）
-         data.defect.inspectionItems[].points[].isValid: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/defect-items/{id}
- **所属模块：** 缺陷检验项
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/report-templates
- **所属模块：** 质量报告模板
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/report-templates
- **所属模块：** 质量报告模板
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- templateKey: 模板标识（系统唯一标识）（类型: string，可选）
- templateCode: 模板编号（类型: string，可选）
- templateName: 模板标题（类型: string，可选）
- templateVersion: 模板版本（类型: string，可选）
- filePath: 文档路径（类型: string，可选）
- remark: 备注（类型: string，可选）
- tenantId: 组织id（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量报告模板 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.templateKey: 模板标识（系统唯一标识）（类型: string）
-   data.templateCode: 模板编号（类型: string）
-   data.templateName: 模板标题（类型: string）
-   data.templateVersion: 模板版本（类型: string）
-   data.filePath: 文档路径（类型: string）
-   data.remark: 备注（类型: string）
-   data.tenantId: 组织id（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/report-templates/{id}
- **所属模块：** 质量报告模板
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量报告模板 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.templateKey: 模板标识（系统唯一标识）（类型: string）
-   data.templateCode: 模板编号（类型: string）
-   data.templateName: 模板标题（类型: string）
-   data.templateVersion: 模板版本（类型: string）
-   data.filePath: 文档路径（类型: string）
-   data.remark: 备注（类型: string）
-   data.tenantId: 组织id（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/report-templates/{id}
- **所属模块：** 质量报告模板
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- templateKey: 模板标识（系统唯一标识）（类型: string，可选）
- templateCode: 模板编号（类型: string，可选）
- templateName: 模板标题（类型: string，可选）
- templateVersion: 模板版本（类型: string，可选）
- filePath: 文档路径（类型: string，可选）
- remark: 备注（类型: string，可选）
- tenantId: 组织id（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量报告模板 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.templateKey: 模板标识（系统唯一标识）（类型: string）
-   data.templateCode: 模板编号（类型: string）
-   data.templateName: 模板标题（类型: string）
-   data.templateVersion: 模板版本（类型: string）
-   data.filePath: 文档路径（类型: string）
-   data.remark: 备注（类型: string）
-   data.tenantId: 组织id（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/report-templates/{id}
- **所属模块：** 质量报告模板
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/quality-docs
- **所属模块：** 质量文档统一表
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/quality-docs
- **所属模块：** 质量文档统一表
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键ID（类型: integer，可选）
- docType: 文档类型：MANUAL=质量手册，SYSTEM=体系文件，KNOWLEDGE=质量经验库，EXPERT=质量专家库（类型: string，可选）
- title: 文档标题（类型: string，可选）
- version: 版本号（类型: string，可选）
- docFlag: 文档标识（受控/非受控/作废）（类型: string，可选）
- filePath: 文件路径（类型: string，可选）
- tenantId: 组织id（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量文档统一表 DTO（类型: object）
-   data.id: 主键ID（类型: integer）
-   data.docType: 文档类型：MANUAL=质量手册，SYSTEM=体系文件，KNOWLEDGE=质量经验库，EXPERT=质量专家库（类型: string）
-   data.title: 文档标题（类型: string）
-   data.version: 版本号（类型: string）
-   data.docFlag: 文档标识（受控/非受控/作废）（类型: string）
-   data.filePath: 文件路径（类型: string）
-   data.tenantId: 组织id（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/quality-docs/{id}
- **所属模块：** 质量文档统一表
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量文档统一表 DTO（类型: object）
-   data.id: 主键ID（类型: integer）
-   data.docType: 文档类型：MANUAL=质量手册，SYSTEM=体系文件，KNOWLEDGE=质量经验库，EXPERT=质量专家库（类型: string）
-   data.title: 文档标题（类型: string）
-   data.version: 版本号（类型: string）
-   data.docFlag: 文档标识（受控/非受控/作废）（类型: string）
-   data.filePath: 文件路径（类型: string）
-   data.tenantId: 组织id（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/quality-docs/{id}
- **所属模块：** 质量文档统一表
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键ID（类型: integer，可选）
- docType: 文档类型：MANUAL=质量手册，SYSTEM=体系文件，KNOWLEDGE=质量经验库，EXPERT=质量专家库（类型: string，可选）
- title: 文档标题（类型: string，可选）
- version: 版本号（类型: string，可选）
- docFlag: 文档标识（受控/非受控/作废）（类型: string，可选）
- filePath: 文件路径（类型: string，可选）
- tenantId: 组织id（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量文档统一表 DTO（类型: object）
-   data.id: 主键ID（类型: integer）
-   data.docType: 文档类型：MANUAL=质量手册，SYSTEM=体系文件，KNOWLEDGE=质量经验库，EXPERT=质量专家库（类型: string）
-   data.title: 文档标题（类型: string）
-   data.version: 版本号（类型: string）
-   data.docFlag: 文档标识（受控/非受控/作废）（类型: string）
-   data.filePath: 文件路径（类型: string）
-   data.tenantId: 组织id（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/quality-docs/{id}
- **所属模块：** 质量文档统一表
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/qms-quality-models
- **所属模块：** 质量模型
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/qms-quality-models
- **所属模块：** 质量模型
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- modelCode: 质量模型编号（类型: string，可选）
- modelName: 质量模型名称（类型: string，可选）
- materialId: 物料id（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- materialTypeCode: 物料类型编码（类型: string，可选）
- materialTypeName: 物料类型名称（类型: string，可选）
- specModel: 规格型号（类型: string，可选）
- remark: 备注（类型: string，可选）
- status: 状态（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer，可选）
- items: 指标项列表（类型: array<object>，可选）
-   items[].id: 主键（类型: integer）
-   items[].modelId: 质量模型ID（类型: integer）
-   items[].indicatorName: 指标名称（类型: string）
-   items[].indicatorCode: 英文名称/代号（类型: string）
-   items[].unitName: 单位（类型: string）
-   items[].standardType: 选用标准（类型: string）
-   items[].standardValue: 标准值（类型: string）
-   items[].upperLimitValue: 上限值（类型: number）
-   items[].lowerLimitValue: 下限值（类型: number）
-   items[].isCore: 是否核心指标:1是0否（类型: integer）
-   items[].qualityWeight: 质量比重(%)（类型: number）
-   items[].processId: 工序ID（类型: integer）
-   items[].processCode: 工序编码（类型: string）
-   items[].processName: 工序名称（类型: string）
-   items[].sortNo: 排序（类型: integer）
-   items[].remark: 备注（类型: string）
-   items[].createBy: 创建人（类型: string）
-   items[].createTime: 记录创建时间（类型: string）
-   items[].updateBy: 更新人（类型: string）
-   items[].updateTime: 记录更新时间（类型: string）
-   items[].isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-   items[].points: 测点配置列表（类型: array<object>）
-     items[].points[].id: 主键（类型: integer）
-     items[].points[].modelItemId: 质量模型指标ID（类型: integer）
-     items[].points[].deviceId: 设备ID（类型: integer）
-     items[].points[].deviceCode: 设备编码（类型: string）
-     items[].points[].deviceName: 设备名称（类型: string）
-     items[].points[].pointId: 测点ID（类型: integer）
-     items[].points[].pointCode: 测点编码（类型: string）
-     items[].points[].pointName: 测点名称（类型: string）
-     items[].points[].standardValue: 标准值（类型: string）
-     items[].points[].minValue: 最小值（类型: number）
-     items[].points[].maxValue: 最大值（类型: number）
-     items[].points[].effectRelation: 影响关系说明（类型: string）
-     items[].points[].exceptionAction: 异常处理方法（类型: string）
-     items[].points[].sortNo: 排序（类型: integer）
-     items[].points[].remark: 备注（类型: string）
-     items[].points[].createBy: 创建人（类型: string）
-     items[].points[].createTime: 创建时间（类型: string）
-     items[].points[].updateBy: 更新人（类型: string）
-     items[].points[].updateTime: 更新时间（类型: string）
-     items[].points[].isValid: 是否有效（类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量模型 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.modelCode: 质量模型编号（类型: string）
-   data.modelName: 质量模型名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.remark: 备注（类型: string）
-   data.status: 状态（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-   data.items: 指标项列表（类型: array<object>）
-     data.items[].id: 主键（类型: integer）
-     data.items[].modelId: 质量模型ID（类型: integer）
-     data.items[].indicatorName: 指标名称（类型: string）
-     data.items[].indicatorCode: 英文名称/代号（类型: string）
-     data.items[].unitName: 单位（类型: string）
-     data.items[].standardType: 选用标准（类型: string）
-     data.items[].standardValue: 标准值（类型: string）
-     data.items[].upperLimitValue: 上限值（类型: number）
-     data.items[].lowerLimitValue: 下限值（类型: number）
-     data.items[].isCore: 是否核心指标:1是0否（类型: integer）
-     data.items[].qualityWeight: 质量比重(%)（类型: number）
-     data.items[].processId: 工序ID（类型: integer）
-     data.items[].processCode: 工序编码（类型: string）
-     data.items[].processName: 工序名称（类型: string）
-     data.items[].sortNo: 排序（类型: integer）
-     data.items[].remark: 备注（类型: string）
-     data.items[].createBy: 创建人（类型: string）
-     data.items[].createTime: 记录创建时间（类型: string）
-     data.items[].updateBy: 更新人（类型: string）
-     data.items[].updateTime: 记录更新时间（类型: string）
-     data.items[].isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-     data.items[].points: 测点配置列表（类型: array<object>）
-       data.items[].points[].id: 主键（类型: integer）
-       data.items[].points[].modelItemId: 质量模型指标ID（类型: integer）
-       data.items[].points[].deviceId: 设备ID（类型: integer）
-       data.items[].points[].deviceCode: 设备编码（类型: string）
-       data.items[].points[].deviceName: 设备名称（类型: string）
-       data.items[].points[].pointId: 测点ID（类型: integer）
-       data.items[].points[].pointCode: 测点编码（类型: string）
-       data.items[].points[].pointName: 测点名称（类型: string）
-       data.items[].points[].standardValue: 标准值（类型: string）
-       data.items[].points[].minValue: 最小值（类型: number）
-       data.items[].points[].maxValue: 最大值（类型: number）
-       data.items[].points[].effectRelation: 影响关系说明（类型: string）
-       data.items[].points[].exceptionAction: 异常处理方法（类型: string）
-       data.items[].points[].sortNo: 排序（类型: integer）
-       data.items[].points[].remark: 备注（类型: string）
-       data.items[].points[].createBy: 创建人（类型: string）
-       data.items[].points[].createTime: 创建时间（类型: string）
-       data.items[].points[].updateBy: 更新人（类型: string）
-       data.items[].points[].updateTime: 更新时间（类型: string）
-       data.items[].points[].isValid: 是否有效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：按质量模型编码查询

- **路径：** GET /api/v1/qms-quality-models/by-code
- **所属模块：** 质量模型
- **HTTP 方法：** GET

**功能说明：**
按质量模型编码查询

**请求参数：**
- modelCode （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量模型 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.modelCode: 质量模型编号（类型: string）
-   data.modelName: 质量模型名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.remark: 备注（类型: string）
-   data.status: 状态（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-   data.items: 指标项列表（类型: array<object>）
-     data.items[].id: 主键（类型: integer）
-     data.items[].modelId: 质量模型ID（类型: integer）
-     data.items[].indicatorName: 指标名称（类型: string）
-     data.items[].indicatorCode: 英文名称/代号（类型: string）
-     data.items[].unitName: 单位（类型: string）
-     data.items[].standardType: 选用标准（类型: string）
-     data.items[].standardValue: 标准值（类型: string）
-     data.items[].upperLimitValue: 上限值（类型: number）
-     data.items[].lowerLimitValue: 下限值（类型: number）
-     data.items[].isCore: 是否核心指标:1是0否（类型: integer）
-     data.items[].qualityWeight: 质量比重(%)（类型: number）
-     data.items[].processId: 工序ID（类型: integer）
-     data.items[].processCode: 工序编码（类型: string）
-     data.items[].processName: 工序名称（类型: string）
-     data.items[].sortNo: 排序（类型: integer）
-     data.items[].remark: 备注（类型: string）
-     data.items[].createBy: 创建人（类型: string）
-     data.items[].createTime: 记录创建时间（类型: string）
-     data.items[].updateBy: 更新人（类型: string）
-     data.items[].updateTime: 记录更新时间（类型: string）
-     data.items[].isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-     data.items[].points: 测点配置列表（类型: array<object>）
-       data.items[].points[].id: 主键（类型: integer）
-       data.items[].points[].modelItemId: 质量模型指标ID（类型: integer）
-       data.items[].points[].deviceId: 设备ID（类型: integer）
-       data.items[].points[].deviceCode: 设备编码（类型: string）
-       data.items[].points[].deviceName: 设备名称（类型: string）
-       data.items[].points[].pointId: 测点ID（类型: integer）
-       data.items[].points[].pointCode: 测点编码（类型: string）
-       data.items[].points[].pointName: 测点名称（类型: string）
-       data.items[].points[].standardValue: 标准值（类型: string）
-       data.items[].points[].minValue: 最小值（类型: number）
-       data.items[].points[].maxValue: 最大值（类型: number）
-       data.items[].points[].effectRelation: 影响关系说明（类型: string）
-       data.items[].points[].exceptionAction: 异常处理方法（类型: string）
-       data.items[].points[].sortNo: 排序（类型: integer）
-       data.items[].points[].remark: 备注（类型: string）
-       data.items[].points[].createBy: 创建人（类型: string）
-       data.items[].points[].createTime: 创建时间（类型: string）
-       data.items[].points[].updateBy: 更新人（类型: string）
-       data.items[].points[].updateTime: 更新时间（类型: string）
-       data.items[].points[].isValid: 是否有效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：查询

- **路径：** GET /api/v1/qms-quality-models/{id}
- **所属模块：** 质量模型
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量模型 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.modelCode: 质量模型编号（类型: string）
-   data.modelName: 质量模型名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.remark: 备注（类型: string）
-   data.status: 状态（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-   data.items: 指标项列表（类型: array<object>）
-     data.items[].id: 主键（类型: integer）
-     data.items[].modelId: 质量模型ID（类型: integer）
-     data.items[].indicatorName: 指标名称（类型: string）
-     data.items[].indicatorCode: 英文名称/代号（类型: string）
-     data.items[].unitName: 单位（类型: string）
-     data.items[].standardType: 选用标准（类型: string）
-     data.items[].standardValue: 标准值（类型: string）
-     data.items[].upperLimitValue: 上限值（类型: number）
-     data.items[].lowerLimitValue: 下限值（类型: number）
-     data.items[].isCore: 是否核心指标:1是0否（类型: integer）
-     data.items[].qualityWeight: 质量比重(%)（类型: number）
-     data.items[].processId: 工序ID（类型: integer）
-     data.items[].processCode: 工序编码（类型: string）
-     data.items[].processName: 工序名称（类型: string）
-     data.items[].sortNo: 排序（类型: integer）
-     data.items[].remark: 备注（类型: string）
-     data.items[].createBy: 创建人（类型: string）
-     data.items[].createTime: 记录创建时间（类型: string）
-     data.items[].updateBy: 更新人（类型: string）
-     data.items[].updateTime: 记录更新时间（类型: string）
-     data.items[].isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-     data.items[].points: 测点配置列表（类型: array<object>）
-       data.items[].points[].id: 主键（类型: integer）
-       data.items[].points[].modelItemId: 质量模型指标ID（类型: integer）
-       data.items[].points[].deviceId: 设备ID（类型: integer）
-       data.items[].points[].deviceCode: 设备编码（类型: string）
-       data.items[].points[].deviceName: 设备名称（类型: string）
-       data.items[].points[].pointId: 测点ID（类型: integer）
-       data.items[].points[].pointCode: 测点编码（类型: string）
-       data.items[].points[].pointName: 测点名称（类型: string）
-       data.items[].points[].standardValue: 标准值（类型: string）
-       data.items[].points[].minValue: 最小值（类型: number）
-       data.items[].points[].maxValue: 最大值（类型: number）
-       data.items[].points[].effectRelation: 影响关系说明（类型: string）
-       data.items[].points[].exceptionAction: 异常处理方法（类型: string）
-       data.items[].points[].sortNo: 排序（类型: integer）
-       data.items[].points[].remark: 备注（类型: string）
-       data.items[].points[].createBy: 创建人（类型: string）
-       data.items[].points[].createTime: 创建时间（类型: string）
-       data.items[].points[].updateBy: 更新人（类型: string）
-       data.items[].points[].updateTime: 更新时间（类型: string）
-       data.items[].points[].isValid: 是否有效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/qms-quality-models/{id}
- **所属模块：** 质量模型
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- modelCode: 质量模型编号（类型: string，可选）
- modelName: 质量模型名称（类型: string，可选）
- materialId: 物料id（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- materialTypeCode: 物料类型编码（类型: string，可选）
- materialTypeName: 物料类型名称（类型: string，可选）
- specModel: 规格型号（类型: string，可选）
- remark: 备注（类型: string，可选）
- status: 状态（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer，可选）
- items: 指标项列表（类型: array<object>，可选）
-   items[].id: 主键（类型: integer）
-   items[].modelId: 质量模型ID（类型: integer）
-   items[].indicatorName: 指标名称（类型: string）
-   items[].indicatorCode: 英文名称/代号（类型: string）
-   items[].unitName: 单位（类型: string）
-   items[].standardType: 选用标准（类型: string）
-   items[].standardValue: 标准值（类型: string）
-   items[].upperLimitValue: 上限值（类型: number）
-   items[].lowerLimitValue: 下限值（类型: number）
-   items[].isCore: 是否核心指标:1是0否（类型: integer）
-   items[].qualityWeight: 质量比重(%)（类型: number）
-   items[].processId: 工序ID（类型: integer）
-   items[].processCode: 工序编码（类型: string）
-   items[].processName: 工序名称（类型: string）
-   items[].sortNo: 排序（类型: integer）
-   items[].remark: 备注（类型: string）
-   items[].createBy: 创建人（类型: string）
-   items[].createTime: 记录创建时间（类型: string）
-   items[].updateBy: 更新人（类型: string）
-   items[].updateTime: 记录更新时间（类型: string）
-   items[].isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-   items[].points: 测点配置列表（类型: array<object>）
-     items[].points[].id: 主键（类型: integer）
-     items[].points[].modelItemId: 质量模型指标ID（类型: integer）
-     items[].points[].deviceId: 设备ID（类型: integer）
-     items[].points[].deviceCode: 设备编码（类型: string）
-     items[].points[].deviceName: 设备名称（类型: string）
-     items[].points[].pointId: 测点ID（类型: integer）
-     items[].points[].pointCode: 测点编码（类型: string）
-     items[].points[].pointName: 测点名称（类型: string）
-     items[].points[].standardValue: 标准值（类型: string）
-     items[].points[].minValue: 最小值（类型: number）
-     items[].points[].maxValue: 最大值（类型: number）
-     items[].points[].effectRelation: 影响关系说明（类型: string）
-     items[].points[].exceptionAction: 异常处理方法（类型: string）
-     items[].points[].sortNo: 排序（类型: integer）
-     items[].points[].remark: 备注（类型: string）
-     items[].points[].createBy: 创建人（类型: string）
-     items[].points[].createTime: 创建时间（类型: string）
-     items[].points[].updateBy: 更新人（类型: string）
-     items[].points[].updateTime: 更新时间（类型: string）
-     items[].points[].isValid: 是否有效（类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量模型 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.modelCode: 质量模型编号（类型: string）
-   data.modelName: 质量模型名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.remark: 备注（类型: string）
-   data.status: 状态（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-   data.items: 指标项列表（类型: array<object>）
-     data.items[].id: 主键（类型: integer）
-     data.items[].modelId: 质量模型ID（类型: integer）
-     data.items[].indicatorName: 指标名称（类型: string）
-     data.items[].indicatorCode: 英文名称/代号（类型: string）
-     data.items[].unitName: 单位（类型: string）
-     data.items[].standardType: 选用标准（类型: string）
-     data.items[].standardValue: 标准值（类型: string）
-     data.items[].upperLimitValue: 上限值（类型: number）
-     data.items[].lowerLimitValue: 下限值（类型: number）
-     data.items[].isCore: 是否核心指标:1是0否（类型: integer）
-     data.items[].qualityWeight: 质量比重(%)（类型: number）
-     data.items[].processId: 工序ID（类型: integer）
-     data.items[].processCode: 工序编码（类型: string）
-     data.items[].processName: 工序名称（类型: string）
-     data.items[].sortNo: 排序（类型: integer）
-     data.items[].remark: 备注（类型: string）
-     data.items[].createBy: 创建人（类型: string）
-     data.items[].createTime: 记录创建时间（类型: string）
-     data.items[].updateBy: 更新人（类型: string）
-     data.items[].updateTime: 记录更新时间（类型: string）
-     data.items[].isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-     data.items[].points: 测点配置列表（类型: array<object>）
-       data.items[].points[].id: 主键（类型: integer）
-       data.items[].points[].modelItemId: 质量模型指标ID（类型: integer）
-       data.items[].points[].deviceId: 设备ID（类型: integer）
-       data.items[].points[].deviceCode: 设备编码（类型: string）
-       data.items[].points[].deviceName: 设备名称（类型: string）
-       data.items[].points[].pointId: 测点ID（类型: integer）
-       data.items[].points[].pointCode: 测点编码（类型: string）
-       data.items[].points[].pointName: 测点名称（类型: string）
-       data.items[].points[].standardValue: 标准值（类型: string）
-       data.items[].points[].minValue: 最小值（类型: number）
-       data.items[].points[].maxValue: 最大值（类型: number）
-       data.items[].points[].effectRelation: 影响关系说明（类型: string）
-       data.items[].points[].exceptionAction: 异常处理方法（类型: string）
-       data.items[].points[].sortNo: 排序（类型: integer）
-       data.items[].points[].remark: 备注（类型: string）
-       data.items[].points[].createBy: 创建人（类型: string）
-       data.items[].points[].createTime: 创建时间（类型: string）
-       data.items[].points[].updateBy: 更新人（类型: string）
-       data.items[].points[].updateTime: 更新时间（类型: string）
-       data.items[].points[].isValid: 是否有效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/qms-quality-models/{id}
- **所属模块：** 质量模型
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/qms-quality-model-items
- **所属模块：** 质量模型指标项
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/qms-quality-model-items
- **所属模块：** 质量模型指标项
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- modelId: 质量模型ID（类型: integer，可选）
- indicatorName: 指标名称（类型: string，可选）
- indicatorCode: 英文名称/代号（类型: string，可选）
- unitName: 单位（类型: string，可选）
- standardType: 选用标准（类型: string，可选）
- standardValue: 标准值（类型: string，可选）
- upperLimitValue: 上限值（类型: number，可选）
- lowerLimitValue: 下限值（类型: number，可选）
- isCore: 是否核心指标:1是0否（类型: integer，可选）
- qualityWeight: 质量比重(%)（类型: number，可选）
- processId: 工序ID（类型: integer，可选）
- processCode: 工序编码（类型: string，可选）
- processName: 工序名称（类型: string，可选）
- sortNo: 排序（类型: integer，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer，可选）
- points: 测点配置列表（类型: array<object>，可选）
-   points[].id: 主键（类型: integer）
-   points[].modelItemId: 质量模型指标ID（类型: integer）
-   points[].deviceId: 设备ID（类型: integer）
-   points[].deviceCode: 设备编码（类型: string）
-   points[].deviceName: 设备名称（类型: string）
-   points[].pointId: 测点ID（类型: integer）
-   points[].pointCode: 测点编码（类型: string）
-   points[].pointName: 测点名称（类型: string）
-   points[].standardValue: 标准值（类型: string）
-   points[].minValue: 最小值（类型: number）
-   points[].maxValue: 最大值（类型: number）
-   points[].effectRelation: 影响关系说明（类型: string）
-   points[].exceptionAction: 异常处理方法（类型: string）
-   points[].sortNo: 排序（类型: integer）
-   points[].remark: 备注（类型: string）
-   points[].createBy: 创建人（类型: string）
-   points[].createTime: 创建时间（类型: string）
-   points[].updateBy: 更新人（类型: string）
-   points[].updateTime: 更新时间（类型: string）
-   points[].isValid: 是否有效（类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量模型指标项 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.modelId: 质量模型ID（类型: integer）
-   data.indicatorName: 指标名称（类型: string）
-   data.indicatorCode: 英文名称/代号（类型: string）
-   data.unitName: 单位（类型: string）
-   data.standardType: 选用标准（类型: string）
-   data.standardValue: 标准值（类型: string）
-   data.upperLimitValue: 上限值（类型: number）
-   data.lowerLimitValue: 下限值（类型: number）
-   data.isCore: 是否核心指标:1是0否（类型: integer）
-   data.qualityWeight: 质量比重(%)（类型: number）
-   data.processId: 工序ID（类型: integer）
-   data.processCode: 工序编码（类型: string）
-   data.processName: 工序名称（类型: string）
-   data.sortNo: 排序（类型: integer）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-   data.points: 测点配置列表（类型: array<object>）
-     data.points[].id: 主键（类型: integer）
-     data.points[].modelItemId: 质量模型指标ID（类型: integer）
-     data.points[].deviceId: 设备ID（类型: integer）
-     data.points[].deviceCode: 设备编码（类型: string）
-     data.points[].deviceName: 设备名称（类型: string）
-     data.points[].pointId: 测点ID（类型: integer）
-     data.points[].pointCode: 测点编码（类型: string）
-     data.points[].pointName: 测点名称（类型: string）
-     data.points[].standardValue: 标准值（类型: string）
-     data.points[].minValue: 最小值（类型: number）
-     data.points[].maxValue: 最大值（类型: number）
-     data.points[].effectRelation: 影响关系说明（类型: string）
-     data.points[].exceptionAction: 异常处理方法（类型: string）
-     data.points[].sortNo: 排序（类型: integer）
-     data.points[].remark: 备注（类型: string）
-     data.points[].createBy: 创建人（类型: string）
-     data.points[].createTime: 创建时间（类型: string）
-     data.points[].updateBy: 更新人（类型: string）
-     data.points[].updateTime: 更新时间（类型: string）
-     data.points[].isValid: 是否有效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/qms-quality-model-items/{id}
- **所属模块：** 质量模型指标项
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量模型指标项 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.modelId: 质量模型ID（类型: integer）
-   data.indicatorName: 指标名称（类型: string）
-   data.indicatorCode: 英文名称/代号（类型: string）
-   data.unitName: 单位（类型: string）
-   data.standardType: 选用标准（类型: string）
-   data.standardValue: 标准值（类型: string）
-   data.upperLimitValue: 上限值（类型: number）
-   data.lowerLimitValue: 下限值（类型: number）
-   data.isCore: 是否核心指标:1是0否（类型: integer）
-   data.qualityWeight: 质量比重(%)（类型: number）
-   data.processId: 工序ID（类型: integer）
-   data.processCode: 工序编码（类型: string）
-   data.processName: 工序名称（类型: string）
-   data.sortNo: 排序（类型: integer）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-   data.points: 测点配置列表（类型: array<object>）
-     data.points[].id: 主键（类型: integer）
-     data.points[].modelItemId: 质量模型指标ID（类型: integer）
-     data.points[].deviceId: 设备ID（类型: integer）
-     data.points[].deviceCode: 设备编码（类型: string）
-     data.points[].deviceName: 设备名称（类型: string）
-     data.points[].pointId: 测点ID（类型: integer）
-     data.points[].pointCode: 测点编码（类型: string）
-     data.points[].pointName: 测点名称（类型: string）
-     data.points[].standardValue: 标准值（类型: string）
-     data.points[].minValue: 最小值（类型: number）
-     data.points[].maxValue: 最大值（类型: number）
-     data.points[].effectRelation: 影响关系说明（类型: string）
-     data.points[].exceptionAction: 异常处理方法（类型: string）
-     data.points[].sortNo: 排序（类型: integer）
-     data.points[].remark: 备注（类型: string）
-     data.points[].createBy: 创建人（类型: string）
-     data.points[].createTime: 创建时间（类型: string）
-     data.points[].updateBy: 更新人（类型: string）
-     data.points[].updateTime: 更新时间（类型: string）
-     data.points[].isValid: 是否有效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/qms-quality-model-items/{id}
- **所属模块：** 质量模型指标项
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- modelId: 质量模型ID（类型: integer，可选）
- indicatorName: 指标名称（类型: string，可选）
- indicatorCode: 英文名称/代号（类型: string，可选）
- unitName: 单位（类型: string，可选）
- standardType: 选用标准（类型: string，可选）
- standardValue: 标准值（类型: string，可选）
- upperLimitValue: 上限值（类型: number，可选）
- lowerLimitValue: 下限值（类型: number，可选）
- isCore: 是否核心指标:1是0否（类型: integer，可选）
- qualityWeight: 质量比重(%)（类型: number，可选）
- processId: 工序ID（类型: integer，可选）
- processCode: 工序编码（类型: string，可选）
- processName: 工序名称（类型: string，可选）
- sortNo: 排序（类型: integer，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer，可选）
- points: 测点配置列表（类型: array<object>，可选）
-   points[].id: 主键（类型: integer）
-   points[].modelItemId: 质量模型指标ID（类型: integer）
-   points[].deviceId: 设备ID（类型: integer）
-   points[].deviceCode: 设备编码（类型: string）
-   points[].deviceName: 设备名称（类型: string）
-   points[].pointId: 测点ID（类型: integer）
-   points[].pointCode: 测点编码（类型: string）
-   points[].pointName: 测点名称（类型: string）
-   points[].standardValue: 标准值（类型: string）
-   points[].minValue: 最小值（类型: number）
-   points[].maxValue: 最大值（类型: number）
-   points[].effectRelation: 影响关系说明（类型: string）
-   points[].exceptionAction: 异常处理方法（类型: string）
-   points[].sortNo: 排序（类型: integer）
-   points[].remark: 备注（类型: string）
-   points[].createBy: 创建人（类型: string）
-   points[].createTime: 创建时间（类型: string）
-   points[].updateBy: 更新人（类型: string）
-   points[].updateTime: 更新时间（类型: string）
-   points[].isValid: 是否有效（类型: integer）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量模型指标项 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.modelId: 质量模型ID（类型: integer）
-   data.indicatorName: 指标名称（类型: string）
-   data.indicatorCode: 英文名称/代号（类型: string）
-   data.unitName: 单位（类型: string）
-   data.standardType: 选用标准（类型: string）
-   data.standardValue: 标准值（类型: string）
-   data.upperLimitValue: 上限值（类型: number）
-   data.lowerLimitValue: 下限值（类型: number）
-   data.isCore: 是否核心指标:1是0否（类型: integer）
-   data.qualityWeight: 质量比重(%)（类型: number）
-   data.processId: 工序ID（类型: integer）
-   data.processCode: 工序编码（类型: string）
-   data.processName: 工序名称（类型: string）
-   data.sortNo: 排序（类型: integer）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（逻辑删除）（类型: integer）
-   data.points: 测点配置列表（类型: array<object>）
-     data.points[].id: 主键（类型: integer）
-     data.points[].modelItemId: 质量模型指标ID（类型: integer）
-     data.points[].deviceId: 设备ID（类型: integer）
-     data.points[].deviceCode: 设备编码（类型: string）
-     data.points[].deviceName: 设备名称（类型: string）
-     data.points[].pointId: 测点ID（类型: integer）
-     data.points[].pointCode: 测点编码（类型: string）
-     data.points[].pointName: 测点名称（类型: string）
-     data.points[].standardValue: 标准值（类型: string）
-     data.points[].minValue: 最小值（类型: number）
-     data.points[].maxValue: 最大值（类型: number）
-     data.points[].effectRelation: 影响关系说明（类型: string）
-     data.points[].exceptionAction: 异常处理方法（类型: string）
-     data.points[].sortNo: 排序（类型: integer）
-     data.points[].remark: 备注（类型: string）
-     data.points[].createBy: 创建人（类型: string）
-     data.points[].createTime: 创建时间（类型: string）
-     data.points[].updateBy: 更新人（类型: string）
-     data.points[].updateTime: 更新时间（类型: string）
-     data.points[].isValid: 是否有效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/qms-quality-model-items/{id}
- **所属模块：** 质量模型指标项
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/qms-quality-model-points
- **所属模块：** 质量模型测点配置
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询分页"
- "查看分页列表"
- "有哪些分页？"
- "列出分页"

## 接口：创建

- **路径：** POST /api/v1/qms-quality-model-points
- **所属模块：** 质量模型测点配置
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- modelItemId: 质量模型指标ID（类型: integer，可选）
- deviceId: 设备ID（类型: integer，可选）
- deviceCode: 设备编码（类型: string，可选）
- deviceName: 设备名称（类型: string，可选）
- pointId: 测点ID（类型: integer，可选）
- pointCode: 测点编码（类型: string，可选）
- pointName: 测点名称（类型: string，可选）
- standardValue: 标准值（类型: string，可选）
- minValue: 最小值（类型: number，可选）
- maxValue: 最大值（类型: number，可选）
- effectRelation: 影响关系说明（类型: string，可选）
- exceptionAction: 异常处理方法（类型: string，可选）
- sortNo: 排序（类型: integer，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 更新时间（类型: string，可选）
- isValid: 是否有效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量模型测点配置 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.modelItemId: 质量模型指标ID（类型: integer）
-   data.deviceId: 设备ID（类型: integer）
-   data.deviceCode: 设备编码（类型: string）
-   data.deviceName: 设备名称（类型: string）
-   data.pointId: 测点ID（类型: integer）
-   data.pointCode: 测点编码（类型: string）
-   data.pointName: 测点名称（类型: string）
-   data.standardValue: 标准值（类型: string）
-   data.minValue: 最小值（类型: number）
-   data.maxValue: 最大值（类型: number）
-   data.effectRelation: 影响关系说明（类型: string）
-   data.exceptionAction: 异常处理方法（类型: string）
-   data.sortNo: 排序（类型: integer）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 更新时间（类型: string）
-   data.isValid: 是否有效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/qms-quality-model-points/{id}
- **所属模块：** 质量模型测点配置
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量模型测点配置 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.modelItemId: 质量模型指标ID（类型: integer）
-   data.deviceId: 设备ID（类型: integer）
-   data.deviceCode: 设备编码（类型: string）
-   data.deviceName: 设备名称（类型: string）
-   data.pointId: 测点ID（类型: integer）
-   data.pointCode: 测点编码（类型: string）
-   data.pointName: 测点名称（类型: string）
-   data.standardValue: 标准值（类型: string）
-   data.minValue: 最小值（类型: number）
-   data.maxValue: 最大值（类型: number）
-   data.effectRelation: 影响关系说明（类型: string）
-   data.exceptionAction: 异常处理方法（类型: string）
-   data.sortNo: 排序（类型: integer）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 更新时间（类型: string）
-   data.isValid: 是否有效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/qms-quality-model-points/{id}
- **所属模块：** 质量模型测点配置
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- modelItemId: 质量模型指标ID（类型: integer，可选）
- deviceId: 设备ID（类型: integer，可选）
- deviceCode: 设备编码（类型: string，可选）
- deviceName: 设备名称（类型: string，可选）
- pointId: 测点ID（类型: integer，可选）
- pointCode: 测点编码（类型: string，可选）
- pointName: 测点名称（类型: string，可选）
- standardValue: 标准值（类型: string，可选）
- minValue: 最小值（类型: number，可选）
- maxValue: 最大值（类型: number，可选）
- effectRelation: 影响关系说明（类型: string，可选）
- exceptionAction: 异常处理方法（类型: string，可选）
- sortNo: 排序（类型: integer，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 更新时间（类型: string，可选）
- isValid: 是否有效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: 质量模型测点配置 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.modelItemId: 质量模型指标ID（类型: integer）
-   data.deviceId: 设备ID（类型: integer）
-   data.deviceCode: 设备编码（类型: string）
-   data.deviceName: 设备名称（类型: string）
-   data.pointId: 测点ID（类型: integer）
-   data.pointCode: 测点编码（类型: string）
-   data.pointName: 测点名称（类型: string）
-   data.standardValue: 标准值（类型: string）
-   data.minValue: 最小值（类型: number）
-   data.maxValue: 最大值（类型: number）
-   data.effectRelation: 影响关系说明（类型: string）
-   data.exceptionAction: 异常处理方法（类型: string）
-   data.sortNo: 排序（类型: integer）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 更新时间（类型: string）
-   data.isValid: 是否有效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/qms-quality-model-points/{id}
- **所属模块：** 质量模型测点配置
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除"
- "移除某个"
