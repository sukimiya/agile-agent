# 服务：MES Service API

## 接口：创建5S记录

- **路径：** POST /api/mes/five-s-records/create
- **所属模块：** 5S管理
- **HTTP 方法：** POST

**功能说明：**
创建5S记录

**请求参数：**
- centerId: （类型: integer，可选）
- recordDate: （类型: string，可选）
- fiveScore: （类型: number，可选）
- accidentCount: （类型: integer，可选）
- safeDayCount: （类型: integer，可选）
- onDCount: （类型: integer，可选）
- totalCount: （类型: integer，可选）
- a1: （类型: number，可选）
- a2: （类型: number，可选）
- a3: （类型: number，可选）
- a4: （类型: number，可选）
- a5: （类型: number，可选）
- a6: （类型: number，可选）
- a7: （类型: number，可选）
- a8: （类型: number，可选）
- a9: （类型: number，可选）
- a10: （类型: number，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个5S记录"
- "新增5S记录记录"
- "创建一个新的5S记录"
- "帮我添加5S记录"

## 接口：删除5S记录

- **路径：** POST /api/mes/five-s-records/delete
- **所属模块：** 5S管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的5S记录记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除5S记录"
- "移除某个5S记录"

## 接口：获取5S记录详情

- **路径：** GET /api/mes/five-s-records/detail
- **所属模块：** 5S管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取5S管理中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.centerId: （类型: integer）
-   data.centerName: （类型: string）
-   data.recordDate: （类型: string）
-   data.fiveScore: （类型: number）
-   data.accidentCount: （类型: integer）
-   data.safeDayCount: （类型: integer）
-   data.onDCount: （类型: integer）
-   data.totalCount: （类型: integer）
-   data.a1: （类型: number）
-   data.a2: （类型: number）
-   data.a3: （类型: number）
-   data.a4: （类型: number）
-   data.a5: （类型: number）
-   data.a6: （类型: number）
-   data.a7: （类型: number）
-   data.a8: （类型: number）
-   data.a9: （类型: number）
-   data.a10: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看5S记录的详细信息"
- "5S记录详情"
- "5S记录的具体情况"

## 接口：分页查询5S记录

- **路径：** POST /api/mes/five-s-records/page
- **所属模块：** 5S管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询5S记录列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].centerId: （类型: integer）
-     data.list[].centerName: （类型: string）
-     data.list[].recordDate: （类型: string）
-     data.list[].fiveScore: （类型: number）
-     data.list[].accidentCount: （类型: integer）
-     data.list[].safeDayCount: （类型: integer）
-     data.list[].onDCount: （类型: integer）
-     data.list[].totalCount: （类型: integer）
-     data.list[].a1: （类型: number）
-     data.list[].a2: （类型: number）
-     data.list[].a3: （类型: number）
-     data.list[].a4: （类型: number）
-     data.list[].a5: （类型: number）
-     data.list[].a6: （类型: number）
-     data.list[].a7: （类型: number）
-     data.list[].a8: （类型: number）
-     data.list[].a9: （类型: number）
-     data.list[].a10: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询5S记录"
- "查看5S记录列表"
- "有哪些5S记录？"
- "列出5S记录"

## 接口：编辑5S记录

- **路径：** POST /api/mes/five-s-records/update
- **所属模块：** 5S管理
- **HTTP 方法：** POST

**功能说明：**
编辑5S记录

**请求参数：**
- centerId: （类型: integer，可选）
- recordDate: （类型: string，可选）
- fiveScore: （类型: number，可选）
- accidentCount: （类型: integer，可选）
- safeDayCount: （类型: integer，可选）
- onDCount: （类型: integer，可选）
- totalCount: （类型: integer，可选）
- a1: （类型: number，可选）
- a2: （类型: number，可选）
- a3: （类型: number，可选）
- a4: （类型: number，可选）
- a5: （类型: number，可选）
- a6: （类型: number，可选）
- a7: （类型: number，可选）
- a8: （类型: number，可选）
- a9: （类型: number，可选）
- a10: （类型: number，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改5S记录信息"
- "更新5S记录数据"
- "编辑5S记录"

## 接口：创建入库申请

- **路径：** POST /api/mes/product-inbound/create
- **所属模块：** 产品入库申请
- **HTTP 方法：** POST

**功能说明：**
创建入库申请

**请求参数：**
- producingWorkOrderId: （类型: integer，可选）
- applyInboundQty: （类型: number，可选）
- warehouseCode: （类型: string，可选）
- orderSource: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个入库申请"
- "新增入库申请记录"
- "创建一个新的入库申请"
- "帮我添加入库申请"

## 接口：分页查询入库申请

- **路径：** POST /api/mes/product-inbound/page
- **所属模块：** 产品入库申请
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询入库申请列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- search: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- materialCode: （类型: string，可选）
- productionWorkOrderNo: （类型: string，可选）
- applicationNo: （类型: string，可选）
- applyInboundTimeStart: （类型: string，可选）
- applyInboundTimeEnd: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].productionOrderNo: （类型: string）
-     data.list[].productionWorkOrderNo: （类型: string）
-     data.list[].producingWorkOrderId: （类型: integer）
-     data.list[].id: （类型: integer）
-     data.list[].salesOrderCode: （类型: string）
-     data.list[].materialCode: （类型: string）
-     data.list[].materialName: （类型: string）
-     data.list[].productId: （类型: integer）
-     data.list[].producingType: （类型: integer）
-     data.list[].productionBatch: （类型: string）
-     data.list[].batchNo: （类型: string）
-     data.list[].predictNum: （类型: number）
-     data.list[].applicationNos: （类型: string）
-     data.list[].cumulativeInboundQty: （类型: number）
-     data.list[].latestInboundTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询入库申请"
- "查看入库申请列表"
- "有哪些入库申请？"
- "列出入库申请"

## 接口：入库质检

- **路径：** POST /api/mes/product-inbound/quality-check
- **所属模块：** 产品入库申请
- **HTTP 方法：** POST

**功能说明：**
入库质检

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么入库质检？"
- "入库质检怎么做？"
- "帮我入库质检"

## 接口：创建产品工艺绑定

- **路径：** POST /api/mes/process-route-bindings/create
- **所属模块：** 产品工艺绑定
- **HTTP 方法：** POST

**功能说明：**
创建产品工艺绑定

**请求参数：**
- productId: 产品ID（类型: integer，必填）
- materialType: 物料类型, 1=产品，2=源材料（类型: integer，可选）
- routeId: 工艺路线ID（类型: integer，必填）
- enabled: 是否启用（类型: boolean，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个产品工艺绑定"
- "新增产品工艺绑定记录"
- "创建一个新的产品工艺绑定"
- "帮我添加产品工艺绑定"

## 接口：删除产品工艺绑定

- **路径：** POST /api/mes/process-route-bindings/delete
- **所属模块：** 产品工艺绑定
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的产品工艺绑定记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除产品工艺绑定"
- "移除某个产品工艺绑定"

## 接口：获取产品工艺绑定详情

- **路径：** GET /api/mes/process-route-bindings/detail
- **所属模块：** 产品工艺绑定
- **HTTP 方法：** GET

**功能说明：**
根据ID获取产品工艺绑定中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: 产品工艺绑定分页返回 VO（类型: object）
-   data.id: （类型: integer）
-   data.routeId: 工艺ID（类型: integer）
-   data.routeName: 工艺名称（类型: string）
-   data.routeCode: 工艺编码（类型: string）
-   data.routeType: 工艺类型（类型: string）
-   data.routeVersion: 工艺版本（类型: string）
-   data.routeDescription: 工艺描述（类型: string）
-   data.productId: 产品ID（类型: integer）
-   data.materialType: （类型: integer）
-   data.productType: 产品类型（类型: string）
-   data.productCode: 产品编码（类型: string）
-   data.productName: 产品名称（类型: string）
-   data.productClass: 产品分类（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.unitId: 单位ID（类型: integer）
-   data.unitName: 单位名称（类型: string）
-   data.enabled: 是否启用（类型: boolean）
-   data.createTime: 创建时间（类型: string）
-   data.updateTime: 更新时间（类型: string）
-   data.status: 状态（类型: integer）
-   data.createBy: 创建人（类型: string）
-   data.updateBy: 更新人（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看产品工艺绑定的详细信息"
- "产品工艺绑定详情"
- "产品工艺绑定的具体情况"

## 接口：启用/禁用产品工艺绑定

- **路径：** POST /api/mes/process-route-bindings/enable
- **所属模块：** 产品工艺绑定
- **HTTP 方法：** POST

**功能说明：**
启用/禁用产品工艺绑定

**请求参数：**
- id （查询参数）: （类型: integer，必填）
- enabled （查询参数）: （类型: boolean，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么启用/禁用产品工艺绑定？"
- "启用/禁用产品工艺绑定怎么做？"
- "帮我启用/禁用产品工艺绑定"

## 接口：分页查询产品工艺绑定

- **路径：** POST /api/mes/process-route-bindings/page
- **所属模块：** 产品工艺绑定
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询产品工艺绑定列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: 页码（类型: integer，可选）
- pageSize: 每页数量（类型: integer，可选）
- productId: 产品ID（类型: integer，可选）
- materialType: （类型: integer，可选）
- routeId: 工艺ID（类型: integer，可选）
- enabled: 是否启用（类型: boolean，可选）
- search: 搜索内容（类型: string，可选）
- productCode: 产品编码（类型: string，可选）
- productName: 产品名称（类型: string，可选）
- processName: 工艺名称（类型: string，可选）
- processCode: 工艺编码（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].routeId: 工艺ID（类型: integer）
-     data.list[].routeName: 工艺名称（类型: string）
-     data.list[].routeCode: 工艺编码（类型: string）
-     data.list[].routeType: 工艺类型（类型: string）
-     data.list[].routeVersion: 工艺版本（类型: string）
-     data.list[].routeDescription: 工艺描述（类型: string）
-     data.list[].productId: 产品ID（类型: integer）
-     data.list[].materialType: （类型: integer）
-     data.list[].productType: 产品类型（类型: string）
-     data.list[].productCode: 产品编码（类型: string）
-     data.list[].productName: 产品名称（类型: string）
-     data.list[].productClass: 产品分类（类型: string）
-     data.list[].specModel: 规格型号（类型: string）
-     data.list[].unitId: 单位ID（类型: integer）
-     data.list[].unitName: 单位名称（类型: string）
-     data.list[].enabled: 是否启用（类型: boolean）
-     data.list[].createTime: 创建时间（类型: string）
-     data.list[].updateTime: 更新时间（类型: string）
-     data.list[].status: 状态（类型: integer）
-     data.list[].createBy: 创建人（类型: string）
-     data.list[].updateBy: 更新人（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询产品工艺绑定"
- "查看产品工艺绑定列表"
- "有哪些产品工艺绑定？"
- "列出产品工艺绑定"

## 接口：按产品分页查询工艺绑定（去重）

- **路径：** POST /api/mes/process-route-bindings/page-by-product
- **所属模块：** 产品工艺绑定
- **HTTP 方法：** POST

**功能说明：**
按产品分页查询工艺绑定（去重）

**请求参数：**
- pageNum: 页码（类型: integer，可选）
- pageSize: 每页数量（类型: integer，可选）
- productId: 产品ID（类型: integer，可选）
- materialType: （类型: integer，可选）
- routeId: 工艺ID（类型: integer，可选）
- enabled: 是否启用（类型: boolean，可选）
- search: 搜索内容（类型: string，可选）
- productCode: 产品编码（类型: string，可选）
- productName: 产品名称（类型: string，可选）
- processName: 工艺名称（类型: string，可选）
- processCode: 工艺编码（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].productId: （类型: integer）
-     data.list[].materialType: （类型: integer）
-     data.list[].productName: （类型: string）
-     data.list[].productCode: （类型: string）
-     data.list[].productType: （类型: string）
-     data.list[].productClass: （类型: string）
-     data.list[].specModel: （类型: string）
-     data.list[].unitId: （类型: integer）
-     data.list[].unitName: （类型: string）
-     data.list[].bindings: （类型: array<object>）
-       data.list[].bindings[].id: （类型: integer）
-       data.list[].bindings[].routeId: 工艺ID（类型: integer）
-       data.list[].bindings[].routeName: 工艺名称（类型: string）
-       data.list[].bindings[].routeCode: 工艺编码（类型: string）
-       data.list[].bindings[].routeType: 工艺类型（类型: string）
-       data.list[].bindings[].routeVersion: 工艺版本（类型: string）
-       data.list[].bindings[].routeDescription: 工艺描述（类型: string）
-       data.list[].bindings[].productId: 产品ID（类型: integer）
-       data.list[].bindings[].materialType: （类型: integer）
-       data.list[].bindings[].productType: 产品类型（类型: string）
-       data.list[].bindings[].productCode: 产品编码（类型: string）
-       data.list[].bindings[].productName: 产品名称（类型: string）
-       data.list[].bindings[].productClass: 产品分类（类型: string）
-       data.list[].bindings[].specModel: 规格型号（类型: string）
-       data.list[].bindings[].unitId: 单位ID（类型: integer）
-       data.list[].bindings[].unitName: 单位名称（类型: string）
-       data.list[].bindings[].enabled: 是否启用（类型: boolean）
-       data.list[].bindings[].createTime: 创建时间（类型: string）
-       data.list[].bindings[].updateTime: 更新时间（类型: string）
-       data.list[].bindings[].status: 状态（类型: integer）
-       data.list[].bindings[].createBy: 创建人（类型: string）
-       data.list[].bindings[].updateBy: 更新人（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工艺绑定（去重）"
- "查看工艺绑定（去重）列表"
- "有哪些工艺绑定（去重）？"
- "列出工艺绑定（去重）"

## 接口：从MDM同步全量产品

- **路径：** POST /api/mes/process-route-bindings/sync
- **所属模块：** 产品工艺绑定
- **HTTP 方法：** POST

**功能说明：**
从MDM同步全量产品

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么从MDM同步全量产品？"
- "从MDM同步全量产品怎么做？"
- "帮我从MDM同步全量产品"

## 接口：编辑产品工艺绑定

- **路径：** POST /api/mes/process-route-bindings/update
- **所属模块：** 产品工艺绑定
- **HTTP 方法：** POST

**功能说明：**
编辑产品工艺绑定

**请求参数：**
- productId: 产品ID（类型: integer，必填）
- materialType: 物料类型, 1=产品，2=源材料（类型: integer，可选）
- routeId: 工艺路线ID（类型: integer，必填）
- enabled: 是否启用（类型: boolean，可选）
- id: 绑定记录ID（类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改产品工艺绑定信息"
- "更新产品工艺绑定数据"
- "编辑产品工艺绑定"

## 接口：安灯持续时长统计

- **路径：** GET /api/mes/andon-dashboard/duration
- **所属模块：** 安灯仪表盘
- **HTTP 方法：** GET

**功能说明：**
安灯持续时长统计

**请求参数：**
- workCenterId （查询参数）: （类型: integer，可选）
- startDate （查询参数）: （类型: string，必填）
- endDate （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].date: （类型: string）
-   data[].avgDuration: （类型: number）
-   data[].maxDuration: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看安灯持续时长统计"
- "怎么安灯持续时长统计？"
- "安灯持续时长统计怎么做？"
- "帮我安灯持续时长统计"

## 接口：当月异常总数 + 每日趋势

- **路径：** GET /api/mes/andon-dashboard/monthly-trend
- **所属模块：** 安灯仪表盘
- **HTTP 方法：** GET

**功能说明：**
当月异常总数 + 每日趋势

**请求参数：**
- workCenterId （查询参数）: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.dailyCounts: （类型: array<object>）
-     data.dailyCounts[].date: （类型: string）
-     data.dailyCounts[].count: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "怎么当月异常总数 + 每日趋势？"
- "当月异常总数 + 每日趋势怎么做？"
- "帮我当月异常总数 + 每日趋势"

## 接口：手动触发统计 - 指定日期范围重新统计

- **路径：** POST /api/mes/andon-dashboard/trigger-stats
- **所属模块：** 安灯仪表盘
- **HTTP 方法：** POST

**功能说明：**
手动触发统计 - 指定日期范围重新统计的实时计算和更新，用于数据同步和报表更新。

**请求参数：**
- startDate （查询参数）: （类型: string，必填）
- endDate （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看手动触发统计 - 指定日期范围重新统计"
- "怎么手动触发统计 - 指定日期范围重新统计？"
- "手动触发统计 - 指定日期范围重新统计怎么做？"
- "帮我手动触发统计 - 指定日期范围重新统计"

## 接口：安灯事件数 - 按类型统计

- **路径：** GET /api/mes/andon-dashboard/type-count
- **所属模块：** 安灯仪表盘
- **HTTP 方法：** GET

**功能说明：**
安灯事件数 - 按类型统计

**请求参数：**
- workCenterId （查询参数）: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].andonType: （类型: string）
-   data[].andonTypeDesc: （类型: string）
-   data[].count: （类型: integer）
-   data[].percentage: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看安灯事件数 - 按类型统计"
- "怎么安灯事件数 - 按类型统计？"
- "安灯事件数 - 按类型统计怎么做？"
- "帮我安灯事件数 - 按类型统计"

## 接口：安灯类型分布

- **路径：** GET /api/mes/andon-dashboard/type-distribution
- **所属模块：** 安灯仪表盘
- **HTTP 方法：** GET

**功能说明：**
安灯类型分布

**请求参数：**
- workCenterId （查询参数）: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].andonType: （类型: string）
-   data[].andonTypeDesc: （类型: string）
-   data[].count: （类型: integer）
-   data[].percentage: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "怎么安灯类型分布？"
- "安灯类型分布怎么做？"
- "帮我安灯类型分布"

## 接口：创建安灯事件

- **路径：** POST /api/mes/andon-events/create
- **所属模块：** 安灯信息
- **HTTP 方法：** POST

**功能说明：**
创建安灯事件

**请求参数：**
- workCenterId: （类型: integer，可选）
- workStationId: （类型: integer，可选）
- andonType: （类型: string，可选）
- level: （类型: string，可选）
- message: （类型: string，可选）
- initiatorId: （类型: integer，可选）
- handlerId: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个安灯事件"
- "新增安灯事件记录"
- "创建一个新的安灯事件"
- "帮我添加安灯事件"

## 接口：删除安灯事件

- **路径：** POST /api/mes/andon-events/delete
- **所属模块：** 安灯信息
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的安灯事件记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除安灯事件"
- "移除某个安灯事件"

## 接口：获取安灯事件详情

- **路径：** GET /api/mes/andon-events/detail
- **所属模块：** 安灯信息
- **HTTP 方法：** GET

**功能说明：**
根据ID获取安灯信息中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: 安灯事件列表 VO（类型: object）
-   data.id: （类型: integer）
-   data.workCenterName: 工作中心名称（类型: string）
-   data.andonType: 安灯类型（类型: string）
-   data.andonTypeDesc: 安灯类型描述（类型: string）
-   data.initiateTime: 发起时间（类型: string）
-   data.initiatorName: 发起人名称（类型: string）
-   data.workStationName: 工位名称（类型: string）
-   data.level: 级别（类型: string）
-   data.levelDesc: 级别描述（类型: string）
-   data.message: 消息（类型: string）
-   data.forwarderName: 转发人名称（类型: string）
-   data.forwardTime: 转发时间（类型: string）
-   data.handleTime: 处理时间（类型: string）
-   data.duration: 持续时间（类型: integer）
-   data.andonStatus: 安灯状态(1=待处理, 2=已转发, 3=已解决)（类型: string）
-   data.andonStatusDesc: 安灯状态描述（类型: string）
-   data.resultRemark: 结果备注（类型: string）
-   data.resultRemarkDesc: 结果备注描述（类型: string）
-   data.handlerName: 处理人名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看安灯事件的详细信息"
- "安灯事件详情"
- "安灯事件的具体情况"

## 接口：批量导出安灯事件

- **路径：** POST /api/mes/andon-events/export
- **所属模块：** 安灯信息
- **HTTP 方法：** POST

**功能说明：**
批量导出安灯事件

**请求参数：**
- workCenterId: （类型: integer，可选）
- level: （类型: array<string>，可选）
- initiateStartTime: （类型: string，可选）
- initiateEndTime: （类型: string，可选）
- handleStartTime: （类型: string，可选）
- handleEndTime: （类型: string，可选）
- andonStatus: （类型: array<string>，可选）
- andonType: （类型: array<string>，可选）
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）

**返回字段：**
- 无

**适用问题示例：**
- "怎么批量导出安灯事件？"
- "批量导出安灯事件怎么做？"
- "帮我批量导出安灯事件"

## 接口：转发安灯事件

- **路径：** POST /api/mes/andon-events/forward
- **所属模块：** 安灯信息
- **HTTP 方法：** POST

**功能说明：**
转发安灯事件

**请求参数：**
- id: （类型: integer，可选）
- forwarderId: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么转发安灯事件？"
- "转发安灯事件怎么做？"
- "帮我转发安灯事件"

## 接口：处理安灯事件

- **路径：** POST /api/mes/andon-events/handle
- **所属模块：** 安灯信息
- **HTTP 方法：** POST

**功能说明：**
处理安灯事件

**请求参数：**
- id: （类型: integer，可选）
- handlerId: （类型: string，可选）
- resultRemark: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么处理安灯事件？"
- "处理安灯事件怎么做？"
- "帮我处理安灯事件"

## 接口：分页查询安灯事件

- **路径：** POST /api/mes/andon-events/page
- **所属模块：** 安灯信息
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询安灯事件列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- workCenterId: （类型: integer，可选）
- level: （类型: array<string>，可选）
- initiateStartTime: （类型: string，可选）
- initiateEndTime: （类型: string，可选）
- handleStartTime: （类型: string，可选）
- handleEndTime: （类型: string，可选）
- andonStatus: （类型: array<string>，可选）
- andonType: （类型: array<string>，可选）
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].workCenterName: 工作中心名称（类型: string）
-     data.list[].andonType: 安灯类型（类型: string）
-     data.list[].andonTypeDesc: 安灯类型描述（类型: string）
-     data.list[].initiateTime: 发起时间（类型: string）
-     data.list[].initiatorName: 发起人名称（类型: string）
-     data.list[].workStationName: 工位名称（类型: string）
-     data.list[].level: 级别（类型: string）
-     data.list[].levelDesc: 级别描述（类型: string）
-     data.list[].message: 消息（类型: string）
-     data.list[].forwarderName: 转发人名称（类型: string）
-     data.list[].forwardTime: 转发时间（类型: string）
-     data.list[].handleTime: 处理时间（类型: string）
-     data.list[].duration: 持续时间（类型: integer）
-     data.list[].andonStatus: 安灯状态(1=待处理, 2=已转发, 3=已解决)（类型: string）
-     data.list[].andonStatusDesc: 安灯状态描述（类型: string）
-     data.list[].resultRemark: 结果备注（类型: string）
-     data.list[].resultRemarkDesc: 结果备注描述（类型: string）
-     data.list[].handlerName: 处理人名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询安灯事件"
- "查看安灯事件列表"
- "有哪些安灯事件？"
- "列出安灯事件"

## 接口：编辑安灯事件

- **路径：** POST /api/mes/andon-events/update
- **所属模块：** 安灯信息
- **HTTP 方法：** POST

**功能说明：**
编辑安灯事件

**请求参数：**
- id: （类型: integer，可选）
- andonType: （类型: string，可选）
- level: （类型: string，可选）
- message: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改安灯事件信息"
- "更新安灯事件数据"
- "编辑安灯事件"

## 接口：查询导出任务状态

- **路径：** GET /api/mes/export/task/{taskId}
- **所属模块：** 导出任务
- **HTTP 方法：** GET

**功能说明：**
查询导出任务状态

**请求参数：**
- taskId （路径参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询导出任务状态"
- "查看导出任务状态列表"
- "有哪些导出任务状态？"
- "列出导出任务状态"
- "哪些{thing}还未完成？"
- "导出任务状态进展如何？"

## 接口：创建工位

- **路径：** POST /api/mes/work-sits/create
- **所属模块：** 工位
- **HTTP 方法：** POST

**功能说明：**
创建工位

**请求参数：**
- centerId: 中心ID（类型: integer，必填）
- sitCode: 工位编码，为空时自动生成（类型: string，可选）
- sitName: 工位名称（类型: string，必填）
- deviceId: （类型: integer，可选）
- deviceCode: （类型: string，可选）
- remark: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工位"
- "新增工位记录"
- "创建一个新的工位"
- "帮我添加工位"

## 接口：删除工位

- **路径：** POST /api/mes/work-sits/delete
- **所属模块：** 工位
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工位记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工位"
- "移除某个工位"

## 接口：获取工位详情

- **路径：** GET /api/mes/work-sits/detail
- **所属模块：** 工位
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工位中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.centerId: （类型: integer）
-   data.centerName: （类型: string）
-   data.sitCode: （类型: string）
-   data.sitName: （类型: string）
-   data.deviceId: （类型: integer）
-   data.deviceCode: （类型: string）
-   data.remark: （类型: string）
-   data.status: （类型: integer）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工位的详细信息"
- "工位详情"
- "工位的具体情况"

## 接口：使工位有效/无效

- **路径：** POST /api/mes/work-sits/enable
- **所属模块：** 工位
- **HTTP 方法：** POST

**功能说明：**
使工位有效/无效

**请求参数：**
- id （查询参数）: （类型: integer，必填）
- valid （查询参数）: （类型: boolean，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么使工位有效/无效？"
- "使工位有效/无效怎么做？"
- "帮我使工位有效/无效"

## 接口：导出工位

- **路径：** POST /api/mes/work-sits/export
- **所属模块：** 工位
- **HTTP 方法：** POST

**功能说明：**
导出工位

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- sitCode: （类型: string，可选）
- sitName: （类型: string，可选）
- deviceId: （类型: integer，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么导出工位？"
- "导出工位怎么做？"
- "帮我导出工位"

## 接口：下载工位导入模板

- **路径：** GET /api/mes/work-sits/import-template
- **所属模块：** 工位
- **HTTP 方法：** GET

**功能说明：**
下载工位导入模板

**请求参数：**
- 无

**返回字段：**
- 无

**适用问题示例：**
- "怎么下载工位导入模板？"
- "下载工位导入模板怎么做？"
- "帮我下载工位导入模板"

## 接口：分页查询工位

- **路径：** POST /api/mes/work-sits/page
- **所属模块：** 工位
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工位列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- sitCode: （类型: string，可选）
- sitName: （类型: string，可选）
- deviceId: （类型: integer，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].centerId: （类型: integer）
-     data.list[].centerName: （类型: string）
-     data.list[].sitCode: （类型: string）
-     data.list[].sitName: （类型: string）
-     data.list[].deviceId: （类型: integer）
-     data.list[].deviceCode: （类型: string）
-     data.list[].remark: （类型: string）
-     data.list[].status: （类型: integer）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工位"
- "查看工位列表"
- "有哪些工位？"
- "列出工位"

## 接口：编辑工位

- **路径：** POST /api/mes/work-sits/update
- **所属模块：** 工位
- **HTTP 方法：** POST

**功能说明：**
编辑工位

**请求参数：**
- centerId: 中心ID（类型: integer，必填）
- sitCode: 工位编码，为空时自动生成（类型: string，可选）
- sitName: 工位名称（类型: string，必填）
- deviceId: （类型: integer，可选）
- deviceCode: （类型: string，可选）
- remark: （类型: string，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工位信息"
- "更新工位数据"
- "编辑工位"

## 接口：工位看板

- **路径：** GET /api/mes/work-stations/station-screen
- **所属模块：** 工位看板
- **HTTP 方法：** GET

**功能说明：**
根据工作中心和工位查询工位看板数据

**请求参数：**
- centerId （查询参数）: （类型: integer，必填）
- workSitId （查询参数）: （类型: integer，必填）
- now （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.centerId: 工作中心ID（类型: integer）
-   data.centerName: 工作中心名称（类型: string）
-   data.centerCode: 工作中心编码（类型: string）
-   data.workSitId: 工位ID（类型: integer）
-   data.workSitName: 工位名称（类型: string）
-   data.sitCode: 工位编码（类型: string）
-   data.stationId: 场站ID（类型: integer）
-   data.processOperationId: 工序ID（类型: integer）
-   data.operationCode: 工序编号（类型: string）
-   data.operationName: 工序名称（类型: string）
-   data.processRoute: 工艺路线（类型: string）
-   data.processFilePath: 工艺文件路径（类型: string）
-   data.workDispatchId: 派工单ID（类型: integer）
-   data.code: 派工单编号（类型: string）
-   data.dispatchStatus: 派工单状态（类型: integer）
-   data.issuingTime: 下发时间（类型: string）
-   data.workStartTime: 计划开始时间（类型: string）
-   data.workEndTime: 计划结束时间（类型: string）
-   data.actualStartTime: 实际开始时间（类型: string）
-   data.producingWorkOrderId: 生产工单ID（类型: integer）
-   data.productionWorkOrderNo: 生产工单号（类型: string）
-   data.productionOrderNo: 生产订单号（类型: string）
-   data.batchNo: 批次号（类型: string）
-   data.salesOrderCode: 销售订单号（类型: string）
-   data.deliveryDate: 交货日期（类型: string）
-   data.productName: 产品名称（类型: string）
-   data.productCode: 产品编码（类型: string）
-   data.productId: 产品ID（类型: integer）
-   data.materialType: 物料类型（类型: integer）
-   data.producedNum: 已生产数量（类型: number）
-   data.workerId: 当前工人ID（类型: integer）
-   data.employeeCode: 工号（类型: string）
-   data.remark: 备注（类型: string）
-   data.deviceId: 设备ID（类型: integer）
-   data.deviceType: 设备类型（类型: string）
-   data.deviceName: （类型: string）
-   data.deviceCode: （类型: string）
-   data.utilizationRate: 时间稼动率（类型: string）
-   data.runningTime: 运行时间（类型: string）
-   data.standTime: 待机时间（类型: string）
-   data.stopTime: 停机时间（类型: string）
-   data.downTime: 故障时间（类型: string）
-   data.offlineTime: 离线时间（类型: string）
-   data.shift: 班次（类型: integer）
-   data.round: 轮次（类型: integer）
-   data.shiftType: 班次类型（类型: integer）
-   data.roundType: 轮次类型（类型: integer）
-   data.shiftWorkerId: 班次工人ID（类型: string）
-   data.checkInTime: 签到时间（类型: string）
-   data.completeQuantity: 完成数量（类型: number）
-   data.predictNum: 预测生产数量（类型: number）
-   data.persent: 生产进度百分比（类型: number）
-   data.goods: 良品数（类型: number）
-   data.bads: 不良数（类型: number）
-   data.goodsOfSum: 累计良品数（类型: number）
-   data.badsOfSum: 累计不良数（类型: number）
-   data.badsRefusedOfSum: 审核通过不良数（类型: number）
-   data.reportQuantityOfSum: 报工总数（全部记录）（类型: number）
-   data.reportQuantityOfGoods: 报工良品数（全部记录）（类型: number）
-   data.approvedTotal: 审批完成总数（类型: number）
-   data.approvedGood: 审批完成良品数（类型: number）
-   data.yieldRate: 良率（类型: number）
-   data.worktimeOfSum: 累计生产时长（类型: integer）
-   data.predictTime: 预测完成时间（类型: string）
-   data.isOvertime: 是否超时（类型: boolean）
- extraData: （类型: object）

**适用问题示例：**
- "查看工位看板"
- "怎么工位看板？"
- "工位看板怎么做？"
- "帮我工位看板"

## 接口：创建工作中心

- **路径：** POST /api/mes/work-centers/create
- **所属模块：** 工作中心
- **HTTP 方法：** POST

**功能说明：**
创建工作中心

**请求参数：**
- stationId: （类型: integer，可选）
- centerCode: （类型: string，可选）
- centerName: （类型: string，可选）
- centerType: （类型: string，可选）
- managerId: （类型: string，可选）
- processId: （类型: integer，可选）
- remark: （类型: string，可选）
- isEnable: （类型: boolean，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工作中心"
- "新增工作中心记录"
- "创建一个新的工作中心"
- "帮我添加工作中心"

## 接口：删除工作中心

- **路径：** POST /api/mes/work-centers/delete
- **所属模块：** 工作中心
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工作中心记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工作中心"
- "移除某个工作中心"

## 接口：获取工作中心详情

- **路径：** GET /api/mes/work-centers/detail
- **所属模块：** 工作中心
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工作中心中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.stationId: （类型: integer）
-   data.centerCode: （类型: string）
-   data.centerName: （类型: string）
-   data.centerType: （类型: string）
-   data.processId: （类型: integer）
-   data.processName: （类型: string）
-   data.managerId: （类型: string）
-   data.managerName: （类型: string）
-   data.remark: （类型: string）
-   data.status: （类型: integer）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工作中心的详细信息"
- "工作中心详情"
- "工作中心的具体情况"

## 接口：启用/禁用工作中心

- **路径：** POST /api/mes/work-centers/enable
- **所属模块：** 工作中心
- **HTTP 方法：** POST

**功能说明：**
启用/禁用工作中心

**请求参数：**
- id （查询参数）: （类型: integer，必填）
- enabled （查询参数）: （类型: boolean，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么启用/禁用工作中心？"
- "启用/禁用工作中心怎么做？"
- "帮我启用/禁用工作中心"

## 接口：分页查询工作中心

- **路径：** POST /api/mes/work-centers/page
- **所属模块：** 工作中心
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工作中心列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- stationId: （类型: integer，可选）
- centerCode: （类型: string，可选）
- centerName: （类型: string，可选）
- centerType: （类型: string，可选）
- processId: （类型: integer，可选）
- managerId: （类型: string，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].stationId: （类型: integer）
-     data.list[].centerCode: （类型: string）
-     data.list[].centerName: （类型: string）
-     data.list[].centerType: （类型: string）
-     data.list[].processId: （类型: integer）
-     data.list[].processName: （类型: string）
-     data.list[].managerId: （类型: string）
-     data.list[].managerName: （类型: string）
-     data.list[].remark: （类型: string）
-     data.list[].status: （类型: integer）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工作中心"
- "查看工作中心列表"
- "有哪些工作中心？"
- "列出工作中心"

## 接口：编辑工作中心

- **路径：** POST /api/mes/work-centers/update
- **所属模块：** 工作中心
- **HTTP 方法：** POST

**功能说明：**
编辑工作中心

**请求参数：**
- stationId: （类型: integer，可选）
- centerCode: （类型: string，可选）
- centerName: （类型: string，可选）
- centerType: （类型: string，可选）
- managerId: （类型: string，可选）
- processId: （类型: integer，可选）
- remark: （类型: string，可选）
- isEnable: （类型: boolean，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工作中心信息"
- "更新工作中心数据"
- "编辑工作中心"

## 接口：异常事件统计

- **路径：** GET /api/mes/work-center-dashboard/abnormal-summary
- **所属模块：** 工作中心看板
- **HTTP 方法：** GET

**功能说明：**
异常事件统计

**请求参数：**
- centerId （查询参数）: （类型: integer，必填）
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）
- pageNum （查询参数）: （类型: integer，可选，默认: 1）
- pageSize （查询参数）: （类型: integer，可选，默认: 10）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.list: （类型: array<object>）
-     data.list[].centerId: （类型: integer）
-     data.list[].dateFrom: （类型: string）
-     data.list[].dateTo: （类型: string）
-     data.list[].abnormalType: （类型: string）
-     data.list[].totalCount: （类型: integer）
-     data.list[].totalDuration: （类型: number）
-   data.total: （类型: integer）
-   data.size: （类型: integer）
-   data.current: （类型: integer）
-   data.pages: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看异常事件统计"
- "怎么异常事件统计？"
- "异常事件统计怎么做？"
- "帮我异常事件统计"

## 接口：安灯事件列表

- **路径：** GET /api/mes/work-center-dashboard/andon-events
- **所属模块：** 工作中心看板
- **HTTP 方法：** GET

**功能说明：**
安灯事件列表

**请求参数：**
- centerId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].eventId: （类型: integer）
-   data[].stationName: （类型: string）
-   data[].message: （类型: string）
-   data[].andonType: （类型: string）
-   data[].level: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询安灯事件列表"
- "查看安灯事件列表列表"
- "有哪些安灯事件列表？"
- "列出安灯事件列表"

## 接口：工作中心看板主屏幕

- **路径：** GET /api/mes/work-center-dashboard/main-screen
- **所属模块：** 工作中心看板
- **HTTP 方法：** GET

**功能说明：**
工作中心看板主屏幕

**请求参数：**
- centerId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.todayDispatchCount: （类型: integer）
-   data.todayCompletedOrderCount: （类型: integer）
-   data.onDutyWorkerCount: （类型: integer）
-   data.todayAnomalyDuration: （类型: integer）
-   data.dayPlanAchievementRate: （类型: number）
-   data.monthPlanAchievementRate: （类型: number）
-   data.monthWorkHours: （类型: number）
-   data.monthNewDispatchCount: （类型: integer）
-   data.monthIssuedDispatchCount: （类型: integer）
-   data.monthCompletedDispatchCount: （类型: integer）
-   data.andonTypeStats: （类型: array<object>）
-     data.andonTypeStats[].andonType: （类型: string）
-     data.andonTypeStats[].andonTypeDesc: （类型: string）
-     data.andonTypeStats[].count: （类型: integer）
-     data.andonTypeStats[].percentage: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看工作中心看板主屏幕"
- "怎么工作中心看板主屏幕？"
- "工作中心看板主屏幕怎么做？"
- "帮我工作中心看板主屏幕"

## 接口：派工单月变化趋势

- **路径：** GET /api/mes/work-center-dashboard/monthly-dispatch-trend
- **所属模块：** 工作中心看板
- **HTTP 方法：** GET

**功能说明：**
派工单月变化趋势

**请求参数：**
- centerId （查询参数）: （类型: integer，必填）
- yearMonth （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.yearMonth: （类型: string）
-   data.totalNew: （类型: integer）
-   data.totalIssued: （类型: integer）
-   data.totalCompleted: （类型: integer）
-   data.dailyCounts: （类型: array<object>）
-     data.dailyCounts[].statDate: （类型: string）
-     data.dailyCounts[].newCount: （类型: integer）
-     data.dailyCounts[].issuedCount: （类型: integer）
-     data.dailyCounts[].completedCount: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "怎么派工单月变化趋势？"
- "派工单月变化趋势怎么做？"
- "帮我派工单月变化趋势"

## 接口：派工单生产进度监控

- **路径：** GET /api/mes/work-center-dashboard/work-dispatch-monitor
- **所属模块：** 工作中心看板
- **HTTP 方法：** GET

**功能说明：**
派工单生产进度监控

**请求参数：**
- centerId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].dispatchId: （类型: integer）
-   data[].code: （类型: string）
-   data[].completedNum: （类型: number）
-   data[].shortageNum: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "怎么派工单生产进度监控？"
- "派工单生产进度监控怎么做？"
- "帮我派工单生产进度监控"

## 接口：创建休息日, 如果跨天, 则会创建多个休息日

- **路径：** POST /api/mes/work-holidays/create
- **所属模块：** 工作日历-休息日
- **HTTP 方法：** POST

**功能说明：**
创建休息日, 如果跨天, 则会创建多个休息日

**请求参数：**
- centerId: （类型: integer，可选）
- name: （类型: string，可选）
- startTime: （类型: string，可选）
- endTime: （类型: string，可选）
- holidayType: （类型: integer，可选）
- remark: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<integer>）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个休息日, 如果跨天, 则会创建多个休息日"
- "新增休息日, 如果跨天, 则会创建多个休息日记录"
- "创建一个新的休息日, 如果跨天, 则会创建多个休息日"
- "帮我添加休息日, 如果跨天, 则会创建多个休息日"

## 接口：批量创建休息日(如果需要插入离散的休息日时间，则用这个接口)

- **路径：** POST /api/mes/work-holidays/create-batch
- **所属模块：** 工作日历-休息日
- **HTTP 方法：** POST

**功能说明：**
批量创建休息日(如果需要插入离散的休息日时间，则用这个接口)

**请求参数：**
- centerId: （类型: integer，可选）
- workHolidayCreateVOList: （类型: array<object>，可选）
-   workHolidayCreateVOList[].centerId: （类型: integer）
-   workHolidayCreateVOList[].name: （类型: string）
-   workHolidayCreateVOList[].startTime: （类型: string）
-   workHolidayCreateVOList[].endTime: （类型: string）
-   workHolidayCreateVOList[].holidayType: （类型: integer）
-   workHolidayCreateVOList[].remark: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<integer>）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量创建休息日(如果需要插入离散的休息日时间，则用这个接口)？"
- "批量创建休息日(如果需要插入离散的休息日时间，则用这个接口)怎么做？"
- "帮我批量创建休息日(如果需要插入离散的休息日时间，则用这个接口)"

## 接口：删除休息日

- **路径：** POST /api/mes/work-holidays/delete
- **所属模块：** 工作日历-休息日
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的休息日记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除休息日"
- "移除某个休息日"

## 接口：获取休息日详情

- **路径：** GET /api/mes/work-holidays/detail
- **所属模块：** 工作日历-休息日
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工作日历-休息日中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.centerId: （类型: integer）
-   data.name: （类型: string）
-   data.startTime: （类型: string）
-   data.endTime: （类型: string）
-   data.holidayType: （类型: integer）
-   data.remark: （类型: string）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看休息日的详细信息"
- "休息日详情"
- "休息日的具体情况"

## 接口：分页查询休息日

- **路径：** POST /api/mes/work-holidays/page
- **所属模块：** 工作日历-休息日
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询休息日列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- holidayType: （类型: integer，可选）
- rangeFrom: （类型: string，可选）
- rangeTo: （类型: string，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].centerId: （类型: integer）
-     data.list[].name: （类型: string）
-     data.list[].startTime: （类型: string）
-     data.list[].endTime: （类型: string）
-     data.list[].holidayType: （类型: integer）
-     data.list[].remark: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询休息日"
- "查看休息日列表"
- "有哪些休息日？"
- "列出休息日"

## 接口：编辑休息日

- **路径：** POST /api/mes/work-holidays/update
- **所属模块：** 工作日历-休息日
- **HTTP 方法：** POST

**功能说明：**
编辑休息日

**请求参数：**
- centerId: （类型: integer，可选）
- name: （类型: string，可选）
- startTime: （类型: string，可选）
- endTime: （类型: string，可选）
- holidayType: （类型: integer，可选）
- remark: （类型: string，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改休息日信息"
- "更新休息日数据"
- "编辑休息日"

## 接口：正向：从 start 开始工作 durationMinutes 后的完工时刻

- **路径：** GET /api/mes/work-calendar/compute-end
- **所属模块：** 工作日历计算
- **HTTP 方法：** GET

**功能说明：**
正向：从 start 开始工作 durationMinutes 后的完工时刻

**请求参数：**
- scheduleId （查询参数）: （类型: integer，必填）
- start （查询参数）: （类型: string，必填）
- durationMinutes （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "开始生产"
- "启动生产"

## 接口：反向：在 end 前完成 durationMinutes 的最晚开工时刻

- **路径：** GET /api/mes/work-calendar/compute-start
- **所属模块：** 工作日历计算
- **HTTP 方法：** GET

**功能说明：**
反向：在 end 前完成 durationMinutes 的最晚开工时刻

**请求参数：**
- scheduleId （查询参数）: （类型: integer，必填）
- end （查询参数）: （类型: string，必填）
- durationMinutes （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "完成生产"
- "生产完成"

## 接口：批量生成班次：按 shiftType 时间配置，为指定日期范围创建 WorkScheduleShift

- **路径：** POST /api/mes/work-calendar/generate-shifts
- **所属模块：** 工作日历计算
- **HTTP 方法：** POST

**功能说明：**
批量生成班次：按 shiftType 时间配置，为指定日期范围创建 WorkScheduleShift

**请求参数：**
- scheduleId: （类型: integer，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量生成班次：按 shiftType 时间配置，为指定日期范围创建 WorkScheduleShift？"
- "批量生成班次：按 shiftType 时间配置，为指定日期范围创建 WorkScheduleShift怎么做？"
- "帮我批量生成班次：按 shiftType 时间配置，为指定日期范围创建 WorkScheduleShift"

## 接口：查询全局班次类型时间配置

- **路径：** GET /api/mes/work-calendar/shift-type-times
- **所属模块：** 工作日历计算
- **HTTP 方法：** GET

**功能说明：**
查询全局班次类型时间配置

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].shiftType: （类型: integer）
-   data[].shift: （类型: integer）
-   data[].startTime: （类型: object）
-     data[].startTime.hour: （类型: integer）
-     data[].startTime.minute: （类型: integer）
-     data[].startTime.second: （类型: integer）
-     data[].startTime.nano: （类型: integer）
-   data[].endTime: （类型: object）
-     data[].endTime.hour: （类型: integer）
-     data[].endTime.minute: （类型: integer）
-     data[].endTime.second: （类型: integer）
-     data[].endTime.nano: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询全局班次类型时间配置"
- "查看全局班次类型时间配置列表"
- "有哪些全局班次类型时间配置？"
- "列出全局班次类型时间配置"
- "最近一周的{thing}"
- "本月全局班次类型时间配置情况"

## 接口：保存全局班次类型时间配置（全量替换）

- **路径：** POST /api/mes/work-calendar/shift-type-times
- **所属模块：** 工作日历计算
- **HTTP 方法：** POST

**功能说明：**
保存全局班次类型时间配置（全量替换）

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么保存全局班次类型时间配置（全量替换）？"
- "保存全局班次类型时间配置（全量替换）怎么做？"
- "帮我保存全局班次类型时间配置（全量替换）"

## 接口：返回区间内的净工作时间段（班次 − 休息日）

- **路径：** GET /api/mes/work-calendar/working-intervals
- **所属模块：** 工作日历计算
- **HTTP 方法：** GET

**功能说明：**
返回区间内的净工作时间段（班次 − 休息日）

**请求参数：**
- scheduleId （查询参数）: （类型: integer，必填）
- from （查询参数）: （类型: string，必填）
- to （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].start: （类型: string）
-   data[].end: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么返回区间内的净工作时间段（班次 − 休息日）？"
- "返回区间内的净工作时间段（班次 − 休息日）怎么做？"
- "帮我返回区间内的净工作时间段（班次 − 休息日）"

## 接口：获取工厂大屏看板全部数据

- **路径：** GET /api/mes/factory-dashboard
- **所属模块：** 工厂大屏看板
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工厂大屏看板中指定记录的完整详情信息。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.overview: （类型: object）
-     data.overview.onDutyCount: （类型: integer）
-     data.overview.todayCompletedOrders: （类型: integer）
-     data.overview.cumulativeSafeDays: （类型: integer）
-   data.todayPlanAchievement: （类型: object）
-     data.todayPlanAchievement.dayPlanAchievementRate: （类型: number）
-     data.todayPlanAchievement.yieldRate: （类型: number）
-     data.todayPlanAchievement.todayGood: （类型: number）
-     data.todayPlanAchievement.dailyPlanTotal: （类型: number）
-   data.anomalySummary: （类型: object）
-     data.anomalySummary.todayAnomalyMinutes: （类型: integer）
-     data.anomalySummary.monthlyAnomalyCount: （类型: integer）
-     data.anomalySummary.monthlyAnomalyMinutes: （类型: integer）
-     data.anomalySummary.cumulativeSafeDays: （类型: integer）
-   data.centerStats: （类型: array<object>）
-     data.centerStats[].centerId: （类型: integer）
-     data.centerStats[].centerCode: （类型: string）
-     data.centerStats[].centerName: （类型: string）
-     data.centerStats[].dayPlanAchievementRate: （类型: number）
-     data.centerStats[].monthPlanAchievementRate: （类型: number）
-     data.centerStats[].monthlyYieldRate: （类型: number）
-     data.centerStats[].monthlyAndonCount: （类型: integer）
-     data.centerStats[].monthlyWorkHours: （类型: number）
-     data.centerStats[].monthlyAnomalyMinutes: （类型: integer）
-   data.productionTrend: （类型: array<object>）
-     data.productionTrend[].yearMonth: （类型: string）
-     data.productionTrend[].good: （类型: number）
-     data.productionTrend[].bad: （类型: number）
-     data.productionTrend[].yieldRate: （类型: number）
-   data.defectTrend: （类型: array<object>）
-     data.defectTrend[].yearMonth: （类型: string）
-     data.defectTrend[].good: （类型: number）
-     data.defectTrend[].bad: （类型: number）
-     data.defectTrend[].yieldRate: （类型: number）
-   data.anomalyDistribution: （类型: array<object>）
-     data.anomalyDistribution[].andonType: （类型: string）
-     data.anomalyDistribution[].andonTypeDesc: （类型: string）
-     data.anomalyDistribution[].count: （类型: integer）
-     data.anomalyDistribution[].percentage: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取工厂大屏看板全部数据"
- "怎么获取工厂大屏看板全部数据？"
- "获取工厂大屏看板全部数据怎么做？"
- "帮我获取工厂大屏看板全部数据"

## 接口：创建工序信息

- **路径：** POST /api/mes/process-operation-infos/create
- **所属模块：** 工序信息
- **HTTP 方法：** POST

**功能说明：**
创建工序信息

**请求参数：**
- standardName: （类型: string，可选）
- standardCode: （类型: string，可选）
- standardVersion: （类型: string，可选）
- standardType: （类型: integer，可选）
- operationType: （类型: string，可选）
- standardTime: （类型: number，可选）
- workContent: （类型: string，可选）
- description: （类型: string，可选）
- beat: （类型: number，可选）
- deviceType: （类型: string，可选）
- isSelfProduced: （类型: boolean，可选）
- price: （类型: number，可选）
- enabled: （类型: boolean，可选）
- inspectionRequired: （类型: boolean，可选）
- operationLevel: （类型: string，可选）
- multiLanguageSupported: （类型: boolean，可选）
- industryCode: （类型: string，可选）
- specialProcessRequired: （类型: boolean，可选）
- enterpriseCode: （类型: string，可选）
- customOperation: （类型: boolean，可选）
- centerId: （类型: integer，可选）
- sort: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工序信息"
- "新增工序信息记录"
- "创建一个新的工序信息"
- "帮我添加工序信息"

## 接口：删除工序信息

- **路径：** POST /api/mes/process-operation-infos/delete
- **所属模块：** 工序信息
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工序信息记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工序信息"
- "移除某个工序信息"

## 接口：获取工序信息详情

- **路径：** GET /api/mes/process-operation-infos/detail
- **所属模块：** 工序信息
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工序信息中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardId: （类型: string）
-   data.sort: （类型: string）
-   data.centerId: （类型: integer）
-   data.centerName: （类型: string）
-   data.standardName: （类型: string）
-   data.standardVersion: （类型: string）
-   data.standardType: （类型: integer）
-   data.standardCode: （类型: string）
-   data.operationType: （类型: string）
-   data.standardTime: （类型: number）
-   data.workContent: （类型: string）
-   data.description: （类型: string）
-   data.beat: （类型: number）
-   data.deviceType: （类型: string）
-   data.isSelfProduced: （类型: boolean）
-   data.price: （类型: number）
-   data.enabled: （类型: boolean）
-   data.inspectionRequired: （类型: boolean）
-   data.operationLevel: （类型: string）
-   data.multiLanguageSupported: （类型: boolean）
-   data.industryCode: （类型: string）
-   data.specialProcessRequired: （类型: boolean）
-   data.enterpriseCode: （类型: string）
-   data.customOperation: （类型: boolean）
-   data.status: （类型: integer）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工序信息的详细信息"
- "工序信息详情"
- "工序信息的具体情况"

## 接口：启用/禁用工序信息

- **路径：** POST /api/mes/process-operation-infos/enable
- **所属模块：** 工序信息
- **HTTP 方法：** POST

**功能说明：**
启用/禁用工序信息

**请求参数：**
- id （查询参数）: （类型: integer，必填）
- enabled （查询参数）: （类型: boolean，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么启用/禁用工序信息？"
- "启用/禁用工序信息怎么做？"
- "帮我启用/禁用工序信息"

## 接口：查询工序信息列表（不分页）

- **路径：** POST /api/mes/process-operation-infos/list
- **所属模块：** 工序信息
- **HTTP 方法：** POST

**功能说明：**
查询工序信息列表（不分页）

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- standardName: （类型: string，可选）
- standardCode: （类型: string，可选）
- standardType: （类型: string，可选）
- operationType: （类型: string，可选）
- enabled: （类型: boolean，可选）
- centerId: （类型: integer，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].standardId: （类型: string）
-   data[].sort: （类型: string）
-   data[].centerId: （类型: integer）
-   data[].centerName: （类型: string）
-   data[].standardName: （类型: string）
-   data[].standardVersion: （类型: string）
-   data[].standardType: （类型: integer）
-   data[].standardCode: （类型: string）
-   data[].operationType: （类型: string）
-   data[].standardTime: （类型: number）
-   data[].workContent: （类型: string）
-   data[].description: （类型: string）
-   data[].beat: （类型: number）
-   data[].deviceType: （类型: string）
-   data[].isSelfProduced: （类型: boolean）
-   data[].price: （类型: number）
-   data[].enabled: （类型: boolean）
-   data[].inspectionRequired: （类型: boolean）
-   data[].operationLevel: （类型: string）
-   data[].multiLanguageSupported: （类型: boolean）
-   data[].industryCode: （类型: string）
-   data[].specialProcessRequired: （类型: boolean）
-   data[].enterpriseCode: （类型: string）
-   data[].customOperation: （类型: boolean）
-   data[].status: （类型: integer）
-   data[].createTime: （类型: string）
-   data[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工序信息列表（不分页）"
- "查看工序信息列表（不分页）列表"
- "有哪些工序信息列表（不分页）？"
- "列出工序信息列表（不分页）"

## 接口：分页查询工序信息

- **路径：** POST /api/mes/process-operation-infos/page
- **所属模块：** 工序信息
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工序信息列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- standardName: （类型: string，可选）
- standardCode: （类型: string，可选）
- standardType: （类型: string，可选）
- operationType: （类型: string，可选）
- enabled: （类型: boolean，可选）
- centerId: （类型: integer，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].standardId: （类型: string）
-     data.list[].sort: （类型: string）
-     data.list[].centerId: （类型: integer）
-     data.list[].centerName: （类型: string）
-     data.list[].standardName: （类型: string）
-     data.list[].standardVersion: （类型: string）
-     data.list[].standardType: （类型: integer）
-     data.list[].standardCode: （类型: string）
-     data.list[].operationType: （类型: string）
-     data.list[].standardTime: （类型: number）
-     data.list[].workContent: （类型: string）
-     data.list[].description: （类型: string）
-     data.list[].beat: （类型: number）
-     data.list[].deviceType: （类型: string）
-     data.list[].isSelfProduced: （类型: boolean）
-     data.list[].price: （类型: number）
-     data.list[].enabled: （类型: boolean）
-     data.list[].inspectionRequired: （类型: boolean）
-     data.list[].operationLevel: （类型: string）
-     data.list[].multiLanguageSupported: （类型: boolean）
-     data.list[].industryCode: （类型: string）
-     data.list[].specialProcessRequired: （类型: boolean）
-     data.list[].enterpriseCode: （类型: string）
-     data.list[].customOperation: （类型: boolean）
-     data.list[].status: （类型: integer）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工序信息"
- "查看工序信息列表"
- "有哪些工序信息？"
- "列出工序信息"

## 接口：编辑工序信息

- **路径：** POST /api/mes/process-operation-infos/update
- **所属模块：** 工序信息
- **HTTP 方法：** POST

**功能说明：**
编辑工序信息

**请求参数：**
- standardName: （类型: string，可选）
- standardCode: （类型: string，可选）
- standardVersion: （类型: string，可选）
- standardType: （类型: integer，可选）
- operationType: （类型: string，可选）
- standardTime: （类型: number，可选）
- workContent: （类型: string，可选）
- description: （类型: string，可选）
- beat: （类型: number，可选）
- deviceType: （类型: string，可选）
- isSelfProduced: （类型: boolean，可选）
- price: （类型: number，可选）
- enabled: （类型: boolean，可选）
- inspectionRequired: （类型: boolean，可选）
- operationLevel: （类型: string，可选）
- multiLanguageSupported: （类型: boolean，可选）
- industryCode: （类型: string，可选）
- specialProcessRequired: （类型: boolean，可选）
- enterpriseCode: （类型: string，可选）
- customOperation: （类型: boolean，可选）
- centerId: （类型: integer，可选）
- sort: （类型: string，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工序信息信息"
- "更新工序信息数据"
- "编辑工序信息"

## 接口：批量保存工序投入物料（无id则新增，有id则编辑）

- **路径：** POST /api/mes/process-input-materials/batch-save
- **所属模块：** 工序投入物料
- **HTTP 方法：** POST

**功能说明：**
批量保存工序投入物料（无id则新增，有id则编辑）

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量保存工序投入物料（无id则新增，有id则编辑）？"
- "批量保存工序投入物料（无id则新增，有id则编辑）怎么做？"
- "帮我批量保存工序投入物料（无id则新增，有id则编辑）"

## 接口：创建工序投入物料

- **路径：** POST /api/mes/process-input-materials/create
- **所属模块：** 工序投入物料
- **HTTP 方法：** POST

**功能说明：**
创建工序投入物料

**请求参数：**
- operationId: （类型: integer，可选）
- count: （类型: number，可选）
- unit: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- materialId: （类型: string，可选）
- materialType: （类型: integer，可选）
- productType: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工序投入物料"
- "新增工序投入物料记录"
- "创建一个新的工序投入物料"
- "帮我添加工序投入物料"

## 接口：删除工序投入物料

- **路径：** POST /api/mes/process-input-materials/delete
- **所属模块：** 工序投入物料
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工序投入物料记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工序投入物料"
- "移除某个工序投入物料"

## 接口：获取工序投入物料详情

- **路径：** GET /api/mes/process-input-materials/detail
- **所属模块：** 工序投入物料
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工序投入物料中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.operationId: （类型: integer）
-   data.count: （类型: number）
-   data.unit: （类型: string）
-   data.status: （类型: integer）
-   data.materialId: （类型: string）
-   data.materialType: （类型: integer）
-   data.productType: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工序投入物料的详细信息"
- "工序投入物料详情"
- "工序投入物料的具体情况"

## 接口：分页查询工序投入物料

- **路径：** POST /api/mes/process-input-materials/page
- **所属模块：** 工序投入物料
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工序投入物料列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- operationId: （类型: integer，可选）
- operationInfoId: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].operationId: （类型: integer）
-     data.list[].count: （类型: number）
-     data.list[].unit: （类型: string）
-     data.list[].status: （类型: integer）
-     data.list[].materialId: （类型: string）
-     data.list[].materialType: （类型: integer）
-     data.list[].productType: （类型: string）
-     data.list[].materialCode: （类型: string）
-     data.list[].materialName: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工序投入物料"
- "查看工序投入物料列表"
- "有哪些工序投入物料？"
- "列出工序投入物料"

## 接口：编辑工序投入物料

- **路径：** POST /api/mes/process-input-materials/update
- **所属模块：** 工序投入物料
- **HTTP 方法：** POST

**功能说明：**
编辑工序投入物料

**请求参数：**
- operationId: （类型: integer，可选）
- count: （类型: number，可选）
- unit: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- materialId: （类型: string，可选）
- materialType: （类型: integer，可选）
- productType: （类型: string，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工序投入物料信息"
- "更新工序投入物料数据"
- "编辑工序投入物料"

## 接口：查询路线中可分配的前置工序列表（排除当前工序及其后代）

- **路径：** GET /api/mes/process-operations/available-predecessors
- **所属模块：** 工序操作
- **HTTP 方法：** GET

**功能说明：**
查询路线中可分配的前置工序列表（排除当前工序及其后代）

**请求参数：**
- routeId （查询参数）: （类型: integer，必填）
- operationId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].label: （类型: string）
-   data[].value: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询路线中可分配的前置工序列表（排除当前工序及其后代）"
- "查看路线中可分配的前置工序列表（排除当前工序及其后代）列表"
- "有哪些路线中可分配的前置工序列表（排除当前工序及其后代）？"
- "列出路线中可分配的前置工序列表（排除当前工序及其后代）"

## 接口：批量查询工序名称和编码

- **路径：** POST /api/mes/process-operations/batch-name
- **所属模块：** 工序操作
- **HTTP 方法：** POST

**功能说明：**
批量查询工序名称和编码

**请求参数：**
- nos: 工序编码列表，多个编码用逗号分隔（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].standardName: 工序名称（类型: string）
-   data[].standardCode: 工序编码（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工序名称和编码"
- "查看工序名称和编码列表"
- "有哪些工序名称和编码？"
- "列出工序名称和编码"

## 接口：删除工序操作

- **路径：** POST /api/mes/process-operations/delete
- **所属模块：** 工序操作
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工序操作记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工序操作"
- "移除某个工序操作"

## 接口：获取工序操作详情

- **路径：** GET /api/mes/process-operations/detail
- **所属模块：** 工序操作
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工序操作中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.operationInfoId: （类型: integer）
-   data.routeId: （类型: integer）
-   data.sequence: （类型: integer）
-   data.dependencySequences: （类型: string）
-   data.workCenterId: （类型: integer）
-   data.workCenterName: （类型: string）
-   data.status: （类型: integer）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
-   data.standardName: （类型: string）
-   data.standardCode: （类型: string）
-   data.standardVersion: （类型: string）
-   data.standardType: （类型: integer）
-   data.sort: （类型: string）
-   data.centerId: （类型: integer）
-   data.centerName: （类型: string）
-   data.operationType: （类型: string）
-   data.standardTime: （类型: number）
-   data.workContent: （类型: string）
-   data.description: （类型: string）
-   data.beat: （类型: number）
-   data.deviceType: （类型: string）
-   data.isSelfProduced: （类型: boolean）
-   data.price: （类型: number）
-   data.enabled: （类型: boolean）
-   data.inspectionRequired: （类型: boolean）
-   data.operationLevel: （类型: string）
-   data.multiLanguageSupported: （类型: boolean）
-   data.industryCode: （类型: string）
-   data.specialProcessRequired: （类型: boolean）
-   data.enterpriseCode: （类型: string）
-   data.customOperation: （类型: boolean）
- extraData: （类型: object）

**适用问题示例：**
- "查看工序操作的详细信息"
- "工序操作详情"
- "工序操作的具体情况"

## 接口：编辑工序

- **路径：** POST /api/mes/process-operations/edit
- **所属模块：** 工序操作
- **HTTP 方法：** POST

**功能说明：**
编辑工序

**请求参数：**
- id: （类型: integer，可选）
- dependencySequences: （类型: string，可选）
- workCenterId: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工序信息"
- "更新工序数据"
- "编辑工序"

## 接口：按依赖层级返回路线下所有工序

- **路径：** GET /api/mes/process-operations/levels
- **所属模块：** 工序操作
- **HTTP 方法：** GET

**功能说明：**
按依赖层级返回路线下所有工序

**请求参数：**
- routeId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么按依赖层级返回路线下所有工序？"
- "按依赖层级返回路线下所有工序怎么做？"
- "帮我按依赖层级返回路线下所有工序"

## 接口：查询工序操作列表（不分页）

- **路径：** POST /api/mes/process-operations/list
- **所属模块：** 工序操作
- **HTTP 方法：** POST

**功能说明：**
查询工序操作列表（不分页）

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- standardName: （类型: string，可选）
- standardType: （类型: string，可选）
- standardCode: （类型: string，可选）
- routeId: （类型: integer，可选）
- workCenterId: （类型: integer，可选）
- centerId: （类型: integer，可选）
- operationType: （类型: string，可选）
- isSelfProduced: （类型: boolean，可选）
- search: （类型: string，可选）
- standardTypeList: （类型: array<integer>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].operationInfoId: （类型: integer）
-   data[].routeId: （类型: integer）
-   data[].sequence: （类型: integer）
-   data[].dependencySequences: （类型: string）
-   data[].workCenterId: （类型: integer）
-   data[].workCenterName: （类型: string）
-   data[].status: （类型: integer）
-   data[].createTime: （类型: string）
-   data[].updateTime: （类型: string）
-   data[].standardName: （类型: string）
-   data[].standardCode: （类型: string）
-   data[].standardVersion: （类型: string）
-   data[].standardType: （类型: integer）
-   data[].sort: （类型: string）
-   data[].centerId: （类型: integer）
-   data[].centerName: （类型: string）
-   data[].operationType: （类型: string）
-   data[].standardTime: （类型: number）
-   data[].workContent: （类型: string）
-   data[].description: （类型: string）
-   data[].beat: （类型: number）
-   data[].deviceType: （类型: string）
-   data[].isSelfProduced: （类型: boolean）
-   data[].price: （类型: number）
-   data[].enabled: （类型: boolean）
-   data[].inspectionRequired: （类型: boolean）
-   data[].operationLevel: （类型: string）
-   data[].multiLanguageSupported: （类型: boolean）
-   data[].industryCode: （类型: string）
-   data[].specialProcessRequired: （类型: boolean）
-   data[].enterpriseCode: （类型: string）
-   data[].customOperation: （类型: boolean）
- extraData: （类型: object）

**适用问题示例：**
- "查询工序操作列表（不分页）"
- "查看工序操作列表（不分页）列表"
- "有哪些工序操作列表（不分页）？"
- "列出工序操作列表（不分页）"

## 接口：分页查询工序操作

- **路径：** POST /api/mes/process-operations/page
- **所属模块：** 工序操作
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工序操作列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- standardName: （类型: string，可选）
- standardType: （类型: string，可选）
- standardCode: （类型: string，可选）
- routeId: （类型: integer，可选）
- workCenterId: （类型: integer，可选）
- centerId: （类型: integer，可选）
- operationType: （类型: string，可选）
- isSelfProduced: （类型: boolean，可选）
- search: （类型: string，可选）
- standardTypeList: （类型: array<integer>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].operationInfoId: （类型: integer）
-     data.list[].routeId: （类型: integer）
-     data.list[].sequence: （类型: integer）
-     data.list[].dependencySequences: （类型: string）
-     data.list[].workCenterId: （类型: integer）
-     data.list[].workCenterName: （类型: string）
-     data.list[].status: （类型: integer）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
-     data.list[].standardName: （类型: string）
-     data.list[].standardCode: （类型: string）
-     data.list[].standardVersion: （类型: string）
-     data.list[].standardType: （类型: integer）
-     data.list[].sort: （类型: string）
-     data.list[].centerId: （类型: integer）
-     data.list[].centerName: （类型: string）
-     data.list[].operationType: （类型: string）
-     data.list[].standardTime: （类型: number）
-     data.list[].workContent: （类型: string）
-     data.list[].description: （类型: string）
-     data.list[].beat: （类型: number）
-     data.list[].deviceType: （类型: string）
-     data.list[].isSelfProduced: （类型: boolean）
-     data.list[].price: （类型: number）
-     data.list[].enabled: （类型: boolean）
-     data.list[].inspectionRequired: （类型: boolean）
-     data.list[].operationLevel: （类型: string）
-     data.list[].multiLanguageSupported: （类型: boolean）
-     data.list[].industryCode: （类型: string）
-     data.list[].specialProcessRequired: （类型: boolean）
-     data.list[].enterpriseCode: （类型: string）
-     data.list[].customOperation: （类型: boolean）
- extraData: （类型: object）

**适用问题示例：**
- "查询工序操作"
- "查看工序操作列表"
- "有哪些工序操作？"
- "列出工序操作"

## 接口：更新工序前置依赖

- **路径：** POST /api/mes/process-operations/update-dependency
- **所属模块：** 工序操作
- **HTTP 方法：** POST

**功能说明：**
更新工序前置依赖

**请求参数：**
- operationId: （类型: integer，可选）
- dependencySequences: （类型: string，可选）
- workCenterId: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工序前置依赖信息"
- "更新工序前置依赖数据"
- "编辑工序前置依赖"

## 接口：批量添加工艺标准

- **路径：** POST /api/mes/process-standards/batch-save
- **所属模块：** 工序标准
- **HTTP 方法：** POST

**功能说明：**
批量添加工艺标准

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量添加工艺标准？"
- "批量添加工艺标准怎么做？"
- "帮我批量添加工艺标准"

## 接口：创建工序标准

- **路径：** POST /api/mes/process-standards/create
- **所属模块：** 工序标准
- **HTTP 方法：** POST

**功能说明：**
创建工序标准

**请求参数：**
- operationId: （类型: integer，可选）
- isCritical: （类型: boolean，可选）
- needReport: （类型: boolean，可选）
- standardType: （类型: integer，可选）
- standardName: （类型: string，可选）
- standardCode: （类型: string，可选）
- parameter: （类型: object，可选）
-   parameter.standardId: 工序标准ID（类型: integer）
-   parameter.standardName: （类型: string）
-   parameter.standardType: （类型: integer）
-   parameter.standardCode: （类型: string）
-   parameter.operationId: 工序ID（类型: integer）
-   parameter.parameterName: 工序参数名称（类型: string）
-   parameter.parameterType: 工序参数类型（类型: string）
-   parameter.standardValue: 国家工序参数值（类型: string）
-   parameter.minValue: 工序参数-最小值（类型: string）
-   parameter.maxValue: 工序参数-最大值（类型: string）
-   parameter.unit: 工序参数-单位（类型: string）
-   parameter.critical: 是否关键工序参数（类型: boolean）
-   parameter.autoCollect: 工序参数-是否自动采集（类型: boolean）
-   parameter.toleranceRequired: 工序参数-是否允许误差（类型: boolean）
-   parameter.toleranceGrade: 工序参数-允许误差等级（类型: string）
-   parameter.inspectionMethod: 工序参数-检查方法（类型: string）
-   parameter.unitSystem: 单位制：SI, Imperial（类型: string）
-   parameter.industryCode: 行业代码（类型: string）
-   parameter.enterpriseCode: 企业代码（类型: string）
-   parameter.customRange: 是否自定义范围（类型: boolean）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工序标准"
- "新增工序标准记录"
- "创建一个新的工序标准"
- "帮我添加工序标准"

## 接口：删除工序标准

- **路径：** POST /api/mes/process-standards/delete
- **所属模块：** 工序标准
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工序标准记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工序标准"
- "移除某个工序标准"

## 接口：获取工序标准详情

- **路径：** GET /api/mes/process-standards/detail
- **所属模块：** 工序标准
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工序标准中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.operationId: （类型: integer）
-   data.isCritical: （类型: boolean）
-   data.needReport: （类型: boolean）
-   data.standardType: （类型: integer）
-   data.standardName: （类型: string）
-   data.standardCode: （类型: string）
-   data.parameterId: （类型: integer）
-   data.processParameter: （类型: object）
-     data.processParameter.id: （类型: integer）
-     data.processParameter.status: （类型: integer）
-     data.processParameter.createBy: （类型: string）
-     data.processParameter.createTime: （类型: string）
-     data.processParameter.updateBy: （类型: string）
-     data.processParameter.updateTime: （类型: string）
-     data.processParameter.isValid: （类型: integer）
-     data.processParameter.standardId: （类型: integer）
-     data.processParameter.standardName: （类型: string）
-     data.processParameter.standardType: （类型: integer）
-     data.processParameter.standardCode: （类型: string）
-     data.processParameter.operationId: （类型: integer）
-     data.processParameter.operation: （类型: object）
-       data.processParameter.operation.id: （类型: integer）
-       data.processParameter.operation.status: （类型: integer）
-       data.processParameter.operation.createBy: （类型: string）
-       data.processParameter.operation.createTime: （类型: string）
-       data.processParameter.operation.updateBy: （类型: string）
-       data.processParameter.operation.updateTime: （类型: string）
-       data.processParameter.operation.isValid: （类型: integer）
-       data.processParameter.operation.operationInfoId: （类型: integer）
-       data.processParameter.operation.routeId: （类型: integer）
-       data.processParameter.operation.sequence: （类型: integer）
-       data.processParameter.operation.dependencySequences: （类型: string）
-       data.processParameter.operation.workCenterId: （类型: integer）
-       data.processParameter.operation.standardName: （类型: string）
-       data.processParameter.operation.standardCode: （类型: string）
-       data.processParameter.operation.standardVersion: （类型: string）
-       data.processParameter.operation.standardType: （类型: integer）
-       data.processParameter.operation.standardId: （类型: string）
-       data.processParameter.operation.operationType: （类型: string）
-       data.processParameter.operation.standardTime: （类型: number）
-       data.processParameter.operation.workContent: （类型: string）
-       data.processParameter.operation.description: （类型: string）
-       data.processParameter.operation.beat: （类型: number）
-       data.processParameter.operation.deviceType: （类型: string）
-       data.processParameter.operation.isSelfProduced: （类型: boolean）
-       data.processParameter.operation.price: （类型: number）
-       data.processParameter.operation.enabled: （类型: boolean）
-       data.processParameter.operation.inspectionRequired: （类型: boolean）
-       data.processParameter.operation.operationLevel: （类型: string）
-       data.processParameter.operation.multiLanguageSupported: （类型: boolean）
-       data.processParameter.operation.industryCode: （类型: string）
-       data.processParameter.operation.specialProcessRequired: （类型: boolean）
-       data.processParameter.operation.enterpriseCode: （类型: string）
-       data.processParameter.operation.customOperation: （类型: boolean）
-     data.processParameter.parameterName: （类型: string）
-     data.processParameter.parameterType: （类型: string）
-     data.processParameter.standardValue: （类型: string）
-     data.processParameter.minValue: （类型: string）
-     data.processParameter.maxValue: （类型: string）
-     data.processParameter.unit: （类型: string）
-     data.processParameter.critical: （类型: boolean）
-     data.processParameter.autoCollect: （类型: boolean）
-     data.processParameter.toleranceRequired: （类型: boolean）
-     data.processParameter.toleranceGrade: （类型: string）
-     data.processParameter.inspectionMethod: （类型: string）
-     data.processParameter.unitSystem: （类型: string）
-     data.processParameter.industryCode: （类型: string）
-     data.processParameter.enterpriseCode: （类型: string）
-     data.processParameter.customRange: （类型: boolean）
-     data.processParameter.parameterTypeEnum: （类型: string）
-     data.processParameter.standardTypeEnum: （类型: string）
-   data.status: （类型: integer）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工序标准的详细信息"
- "工序标准详情"
- "工序标准的具体情况"

## 接口：分页查询工序标准

- **路径：** POST /api/mes/process-standards/page
- **所属模块：** 工序标准
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工序标准列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- operationId: （类型: integer，可选）
- operationInfoId: （类型: integer，可选）
- standardType: （类型: string，可选）
- isCritical: （类型: boolean，可选）
- parameterId: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].operationId: （类型: integer）
-     data.list[].isCritical: （类型: boolean）
-     data.list[].needReport: （类型: boolean）
-     data.list[].standardType: （类型: integer）
-     data.list[].standardName: （类型: string）
-     data.list[].standardCode: （类型: string）
-     data.list[].parameterId: （类型: integer）
-     data.list[].processParameter: （类型: object）
-       data.list[].processParameter.id: （类型: integer）
-       data.list[].processParameter.status: （类型: integer）
-       data.list[].processParameter.createBy: （类型: string）
-       data.list[].processParameter.createTime: （类型: string）
-       data.list[].processParameter.updateBy: （类型: string）
-       data.list[].processParameter.updateTime: （类型: string）
-       data.list[].processParameter.isValid: （类型: integer）
-       data.list[].processParameter.standardId: （类型: integer）
-       data.list[].processParameter.standardName: （类型: string）
-       data.list[].processParameter.standardType: （类型: integer）
-       data.list[].processParameter.standardCode: （类型: string）
-       data.list[].processParameter.operationId: （类型: integer）
-       data.list[].processParameter.operation: （类型: object）
-         data.list[].processParameter.operation.id: （类型: integer）
-         data.list[].processParameter.operation.status: （类型: integer）
-         data.list[].processParameter.operation.createBy: （类型: string）
-         data.list[].processParameter.operation.createTime: （类型: string）
-         data.list[].processParameter.operation.updateBy: （类型: string）
-         data.list[].processParameter.operation.updateTime: （类型: string）
-         data.list[].processParameter.operation.isValid: （类型: integer）
-         data.list[].processParameter.operation.operationInfoId: （类型: integer）
-         data.list[].processParameter.operation.routeId: （类型: integer）
-         data.list[].processParameter.operation.sequence: （类型: integer）
-         data.list[].processParameter.operation.dependencySequences: （类型: string）
-         data.list[].processParameter.operation.workCenterId: （类型: integer）
-         data.list[].processParameter.operation.standardName: （类型: string）
-         data.list[].processParameter.operation.standardCode: （类型: string）
-         data.list[].processParameter.operation.standardVersion: （类型: string）
-         data.list[].processParameter.operation.standardType: （类型: integer）
-         data.list[].processParameter.operation.standardId: （类型: string）
-         data.list[].processParameter.operation.operationType: （类型: string）
-         data.list[].processParameter.operation.standardTime: （类型: number）
-         data.list[].processParameter.operation.workContent: （类型: string）
-         data.list[].processParameter.operation.description: （类型: string）
-         data.list[].processParameter.operation.beat: （类型: number）
-         data.list[].processParameter.operation.deviceType: （类型: string）
-         data.list[].processParameter.operation.isSelfProduced: （类型: boolean）
-         data.list[].processParameter.operation.price: （类型: number）
-         data.list[].processParameter.operation.enabled: （类型: boolean）
-         data.list[].processParameter.operation.inspectionRequired: （类型: boolean）
-         data.list[].processParameter.operation.operationLevel: （类型: string）
-         data.list[].processParameter.operation.multiLanguageSupported: （类型: boolean）
-         data.list[].processParameter.operation.industryCode: （类型: string）
-         data.list[].processParameter.operation.specialProcessRequired: （类型: boolean）
-         data.list[].processParameter.operation.enterpriseCode: （类型: string）
-         data.list[].processParameter.operation.customOperation: （类型: boolean）
-       data.list[].processParameter.parameterName: （类型: string）
-       data.list[].processParameter.parameterType: （类型: string）
-       data.list[].processParameter.standardValue: （类型: string）
-       data.list[].processParameter.minValue: （类型: string）
-       data.list[].processParameter.maxValue: （类型: string）
-       data.list[].processParameter.unit: （类型: string）
-       data.list[].processParameter.critical: （类型: boolean）
-       data.list[].processParameter.autoCollect: （类型: boolean）
-       data.list[].processParameter.toleranceRequired: （类型: boolean）
-       data.list[].processParameter.toleranceGrade: （类型: string）
-       data.list[].processParameter.inspectionMethod: （类型: string）
-       data.list[].processParameter.unitSystem: （类型: string）
-       data.list[].processParameter.industryCode: （类型: string）
-       data.list[].processParameter.enterpriseCode: （类型: string）
-       data.list[].processParameter.customRange: （类型: boolean）
-       data.list[].processParameter.parameterTypeEnum: （类型: string）
-       data.list[].processParameter.standardTypeEnum: （类型: string）
-     data.list[].status: （类型: integer）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工序标准"
- "查看工序标准列表"
- "有哪些工序标准？"
- "列出工序标准"

## 接口：编辑工序标准

- **路径：** POST /api/mes/process-standards/update
- **所属模块：** 工序标准
- **HTTP 方法：** POST

**功能说明：**
编辑工序标准

**请求参数：**
- operationId: （类型: integer，可选）
- isCritical: （类型: boolean，可选）
- needReport: （类型: boolean，可选）
- standardType: （类型: integer，可选）
- standardName: （类型: string，可选）
- standardCode: （类型: string，可选）
- parameter: （类型: object，可选）
-   parameter.standardId: 工序标准ID（类型: integer）
-   parameter.standardName: （类型: string）
-   parameter.standardType: （类型: integer）
-   parameter.standardCode: （类型: string）
-   parameter.operationId: 工序ID（类型: integer）
-   parameter.parameterName: 工序参数名称（类型: string）
-   parameter.parameterType: 工序参数类型（类型: string）
-   parameter.standardValue: 国家工序参数值（类型: string）
-   parameter.minValue: 工序参数-最小值（类型: string）
-   parameter.maxValue: 工序参数-最大值（类型: string）
-   parameter.unit: 工序参数-单位（类型: string）
-   parameter.critical: 是否关键工序参数（类型: boolean）
-   parameter.autoCollect: 工序参数-是否自动采集（类型: boolean）
-   parameter.toleranceRequired: 工序参数-是否允许误差（类型: boolean）
-   parameter.toleranceGrade: 工序参数-允许误差等级（类型: string）
-   parameter.inspectionMethod: 工序参数-检查方法（类型: string）
-   parameter.unitSystem: 单位制：SI, Imperial（类型: string）
-   parameter.industryCode: 行业代码（类型: string）
-   parameter.enterpriseCode: 企业代码（类型: string）
-   parameter.customRange: 是否自定义范围（类型: boolean）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工序标准信息"
- "更新工序标准数据"
- "编辑工序标准"

## 接口：拷贝工艺BOM创建新版本

- **路径：** POST /api/mes/process-boms/copy
- **所属模块：** 工艺BOM
- **HTTP 方法：** POST

**功能说明：**
拷贝工艺BOM创建新版本

**请求参数：**
- id （查询参数）: （类型: integer，必填）
- version （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "怎么拷贝工艺BOM创建新版本？"
- "拷贝工艺BOM创建新版本怎么做？"
- "帮我拷贝工艺BOM创建新版本"

## 接口：创建工艺BOM

- **路径：** POST /api/mes/process-boms/create
- **所属模块：** 工艺BOM
- **HTTP 方法：** POST

**功能说明：**
创建工艺BOM

**请求参数：**
- stationId: （类型: integer，可选）
- stationName: （类型: string，可选）
- name: （类型: string，可选）
- version: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- productId: （类型: integer，可选）
- unit: （类型: string，可选）
- materialType: （类型: integer，可选）
- isKeyPart: （类型: boolean，可选）
- remark: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工艺BOM"
- "新增工艺BOM记录"
- "创建一个新的工艺BOM"
- "帮我添加工艺BOM"

## 接口：删除工艺BOM

- **路径：** POST /api/mes/process-boms/delete
- **所属模块：** 工艺BOM
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工艺BOM记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工艺BOM"
- "移除某个工艺BOM"

## 接口：获取工艺BOM详情

- **路径：** GET /api/mes/process-boms/detail
- **所属模块：** 工艺BOM
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工艺BOM中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.stationId: （类型: integer）
-   data.stationName: （类型: string）
-   data.name: （类型: string）
-   data.version: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.productId: （类型: integer）
-   data.materialType: （类型: integer）
-   data.isKeyPart: （类型: boolean）
-   data.remark: （类型: string）
-   data.status: （类型: integer）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
-   data.publishTime: （类型: string）
-   data.productClass: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工艺BOM的详细信息"
- "工艺BOM详情"
- "工艺BOM的具体情况"

## 接口：获取工艺BOM已发布版本列表

- **路径：** GET /api/mes/process-boms/get-versions
- **所属模块：** 工艺BOM
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工艺BOM中指定记录的完整详情信息。

**请求参数：**
- productId （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.versions: （类型: array<object>）
-     data.versions[].bomId: （类型: integer）
-     data.versions[].version: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询获取工艺BOM已发布版本列表"
- "查看获取工艺BOM已发布版本列表列表"
- "有哪些获取工艺BOM已发布版本列表？"
- "列出获取工艺BOM已发布版本列表"

## 接口：分页查询操作日志

- **路径：** POST /api/mes/process-boms/log-page
- **所属模块：** 工艺BOM
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询操作日志列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: 页码（类型: integer，可选）
- pageSize: 每页条数（类型: integer，可选）
- bomId: BOM ID(精确匹配)（类型: integer，可选）
- action: 操作类型: PUBLISH/CREATE/UPDATE/DELETE/COPY/ADDNODE/DELETENODE（类型: string，可选）
- startTime: 操作时间起（类型: string，可选）
- endTime: 操作时间止（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].bomId: BOM ID（类型: integer）
-     data.list[].updateTime: 更新时间（类型: string）
-     data.list[].updateBy: 更新人ID（类型: string）
-     data.list[].updateByName: 更新人姓名（类型: string）
-     data.list[].logMsg: 日志内容（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询操作日志"
- "查看操作日志列表"
- "有哪些操作日志？"
- "列出操作日志"

## 接口：分页查询工艺BOM

- **路径：** POST /api/mes/process-boms/page
- **所属模块：** 工艺BOM
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工艺BOM列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- stationId: （类型: integer，可选）
- stationName: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- name: （类型: string，可选）
- version: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].stationId: （类型: integer）
-     data.list[].stationName: （类型: string）
-     data.list[].name: （类型: string）
-     data.list[].version: （类型: string）
-     data.list[].materialCode: （类型: string）
-     data.list[].materialName: （类型: string）
-     data.list[].productId: （类型: integer）
-     data.list[].materialType: （类型: integer）
-     data.list[].isKeyPart: （类型: boolean）
-     data.list[].remark: （类型: string）
-     data.list[].status: （类型: integer）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
-     data.list[].publishTime: （类型: string）
-     data.list[].productClass: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工艺BOM"
- "查看工艺BOM列表"
- "有哪些工艺BOM？"
- "列出工艺BOM"

## 接口：发布工艺BOM版本

- **路径：** POST /api/mes/process-boms/publish
- **所属模块：** 工艺BOM
- **HTTP 方法：** POST

**功能说明：**
发布工艺BOM版本

**请求参数：**
- id （查询参数）: （类型: integer，必填）
- version （查询参数）: （类型: string，可选）
- remark （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "发布到生产"
- "提交生产"

## 接口：获取工艺BOM树

- **路径：** GET /api/mes/process-boms/tree
- **所属模块：** 工艺BOM
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工艺BOM中指定记录的完整详情信息。

**请求参数：**
- bomId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.productId: 物料ID（类型: integer）
-   data.materialType: 物料类型, 1=产品， 2=原料（类型: integer）
-   data.name: BOM名称（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.remark: BOM描述（类型: string）
-   data.isKeyPart: 是否关键件（类型: boolean）
-   data.parentId: 父节点ID（类型: integer）
-   data.processBomId: 工艺BOMID（类型: integer）
-   data.children: （类型: array<object>）
-     data.children[].id: （类型: integer）
-     data.children[].productId: 物料ID（类型: integer）
-     data.children[].materialType: 物料类型, 1=产品， 2=原料（类型: integer）
-     data.children[].name: BOM名称（类型: string）
-     data.children[].materialCode: 物料编码（类型: string）
-     data.children[].materialName: 物料名称（类型: string）
-     data.children[].remark: BOM描述（类型: string）
-     data.children[].isKeyPart: 是否关键件（类型: boolean）
-     data.children[].parentId: 父节点ID（类型: integer）
-     data.children[].processBomId: 工艺BOMID（类型: integer）
-     data.children[].children: （类型: array<object>）
-       data.children[].children[].id: （类型: integer）
-       data.children[].children[].productId: 物料ID（类型: integer）
-       data.children[].children[].materialType: 物料类型, 1=产品， 2=原料（类型: integer）
-       data.children[].children[].name: BOM名称（类型: string）
-       data.children[].children[].materialCode: 物料编码（类型: string）
-       data.children[].children[].materialName: 物料名称（类型: string）
-       data.children[].children[].remark: BOM描述（类型: string）
-       data.children[].children[].isKeyPart: 是否关键件（类型: boolean）
-       data.children[].children[].parentId: 父节点ID（类型: integer）
-       data.children[].children[].processBomId: 工艺BOMID（类型: integer）
-       data.children[].children[].children: （类型: array<object>）
-         data.children[].children[].children[].id: （类型: integer）
-         data.children[].children[].children[].productId: 物料ID（类型: integer）
-         data.children[].children[].children[].materialType: 物料类型, 1=产品， 2=原料（类型: integer）
-         data.children[].children[].children[].name: BOM名称（类型: string）
-         data.children[].children[].children[].materialCode: 物料编码（类型: string）
-         data.children[].children[].children[].materialName: 物料名称（类型: string）
-         data.children[].children[].children[].remark: BOM描述（类型: string）
-         data.children[].children[].children[].isKeyPart: 是否关键件（类型: boolean）
-         data.children[].children[].children[].parentId: 父节点ID（类型: integer）
-         data.children[].children[].children[].processBomId: 工艺BOMID（类型: integer）
-         data.children[].children[].children[].children: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查看工艺BOM树"
- "工艺BOM树是什么？"

## 接口：编辑工艺BOM

- **路径：** POST /api/mes/process-boms/update
- **所属模块：** 工艺BOM
- **HTTP 方法：** POST

**功能说明：**
编辑工艺BOM

**请求参数：**
- stationId: （类型: integer，可选）
- stationName: （类型: string，可选）
- name: （类型: string，可选）
- version: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- productId: （类型: integer，可选）
- unit: （类型: string，可选）
- materialType: （类型: integer，可选）
- isKeyPart: （类型: boolean，可选）
- remark: （类型: string，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工艺BOM信息"
- "更新工艺BOM数据"
- "编辑工艺BOM"

## 接口：批量创建工艺BOM节点

- **路径：** POST /api/mes/process-bom-nodes/batch-create
- **所属模块：** 工艺BOM节点
- **HTTP 方法：** POST

**功能说明：**
批量创建工艺BOM节点

**请求参数：**
- boms: （类型: array<object>，可选）
-   boms[].processBomId: 工艺BOMID（类型: integer）
-   boms[].name: 名称（类型: string）
-   boms[].parentId: 父节点ID（类型: integer）
-   boms[].operationId: 工序ID（类型: integer）
-   boms[].productId: 产品ID（类型: integer）
-   boms[].materialType: 物料类型（类型: integer）
-   boms[].salesOrderId: 销售订单ID（类型: integer）
-   boms[].usage: 使用量, 数量（类型: number）
-   boms[].unit: 单位（类型: string）
-   boms[].lossRate: 损耗率（类型: number）
-   boms[].materialName: 物料名称（类型: string）
-   boms[].materialCode: 物料编码（类型: string）
-   boms[].remark: 备注（类型: string）
-   boms[].getType: 获取方式（类型: integer）
-   boms[].isKeyPart: 是否关键件（类型: boolean）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量创建工艺BOM节点？"
- "批量创建工艺BOM节点怎么做？"
- "帮我批量创建工艺BOM节点"

## 接口：创建工艺BOM节点

- **路径：** POST /api/mes/process-bom-nodes/create
- **所属模块：** 工艺BOM节点
- **HTTP 方法：** POST

**功能说明：**
创建工艺BOM节点

**请求参数：**
- processBomId: 工艺BOMID（类型: integer，可选）
- name: 名称（类型: string，可选）
- parentId: 父节点ID（类型: integer，可选）
- operationId: 工序ID（类型: integer，可选）
- productId: 产品ID（类型: integer，可选）
- materialType: 物料类型（类型: integer，可选）
- salesOrderId: 销售订单ID（类型: integer，可选）
- usage: 使用量, 数量（类型: number，可选）
- unit: 单位（类型: string，可选）
- lossRate: 损耗率（类型: number，可选）
- materialName: 物料名称（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- remark: 备注（类型: string，可选）
- getType: 获取方式（类型: integer，可选）
- isKeyPart: 是否关键件（类型: boolean，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工艺BOM节点"
- "新增工艺BOM节点记录"
- "创建一个新的工艺BOM节点"
- "帮我添加工艺BOM节点"

## 接口：删除工艺BOM节点

- **路径：** POST /api/mes/process-bom-nodes/delete
- **所属模块：** 工艺BOM节点
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工艺BOM节点记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工艺BOM节点"
- "移除某个工艺BOM节点"

## 接口：获取工艺BOM节点详情

- **路径：** GET /api/mes/process-bom-nodes/detail
- **所属模块：** 工艺BOM节点
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工艺BOM节点中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.processBomId: （类型: integer）
-   data.name: （类型: string）
-   data.version: （类型: string）
-   data.parentId: （类型: integer）
-   data.operationId: （类型: integer）
-   data.operationCode: （类型: string）
-   data.operationName: （类型: string）
-   data.productId: （类型: integer）
-   data.materialType: （类型: integer）
-   data.salesOrderId: （类型: integer）
-   data.usage: （类型: number）
-   data.unit: （类型: string）
-   data.lossRate: （类型: number）
-   data.materialName: （类型: string）
-   data.materialCode: （类型: string）
-   data.isKeyPart: （类型: boolean）
-   data.remark: （类型: string）
-   data.getType: （类型: integer）
-   data.beat: （类型: number）
-   data.status: （类型: integer）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工艺BOM节点的详细信息"
- "工艺BOM节点详情"
- "工艺BOM节点的具体情况"

## 接口：分页查询工艺BOM节点

- **路径：** POST /api/mes/process-bom-nodes/page
- **所属模块：** 工艺BOM节点
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工艺BOM节点列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- processBomId: （类型: integer，可选）
- name: （类型: string，可选）
- operationId: （类型: integer，可选）
- productId: （类型: integer，可选）
- materialType: （类型: integer，可选）
- salesOrderId: （类型: integer，可选）
- materialName: （类型: string，可选）
- materialCode: （类型: string，可选）
- search: （类型: string，可选）
- parentId: 父节点ID（类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].processBomId: （类型: integer）
-     data.list[].name: （类型: string）
-     data.list[].version: （类型: string）
-     data.list[].parentId: （类型: integer）
-     data.list[].operationId: （类型: integer）
-     data.list[].operationCode: （类型: string）
-     data.list[].operationName: （类型: string）
-     data.list[].productId: （类型: integer）
-     data.list[].materialType: （类型: integer）
-     data.list[].salesOrderId: （类型: integer）
-     data.list[].usage: （类型: number）
-     data.list[].unit: （类型: string）
-     data.list[].lossRate: （类型: number）
-     data.list[].materialName: （类型: string）
-     data.list[].materialCode: （类型: string）
-     data.list[].isKeyPart: （类型: boolean）
-     data.list[].remark: （类型: string）
-     data.list[].getType: （类型: integer）
-     data.list[].beat: （类型: number）
-     data.list[].status: （类型: integer）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工艺BOM节点"
- "查看工艺BOM节点列表"
- "有哪些工艺BOM节点？"
- "列出工艺BOM节点"

## 接口：编辑工艺BOM节点

- **路径：** POST /api/mes/process-bom-nodes/update
- **所属模块：** 工艺BOM节点
- **HTTP 方法：** POST

**功能说明：**
编辑工艺BOM节点

**请求参数：**
- processBomId: 工艺BOMID（类型: integer，可选）
- name: 名称（类型: string，可选）
- parentId: 父节点ID（类型: integer，可选）
- operationId: 工序ID（类型: integer，可选）
- productId: 产品ID（类型: integer，可选）
- materialType: 物料类型（类型: integer，可选）
- salesOrderId: 销售订单ID（类型: integer，可选）
- usage: 使用量, 数量（类型: number，可选）
- unit: 单位（类型: string，可选）
- lossRate: 损耗率（类型: number，可选）
- materialName: 物料名称（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- remark: 备注（类型: string，可选）
- getType: 获取方式（类型: integer，可选）
- isKeyPart: 是否关键件（类型: boolean，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工艺BOM节点信息"
- "更新工艺BOM节点数据"
- "编辑工艺BOM节点"

## 接口：批量保存工艺参数（无id则新增，有id则编辑）

- **路径：** POST /api/mes/process-parameters/batch-save
- **所属模块：** 工艺参数
- **HTTP 方法：** POST

**功能说明：**
批量保存工艺参数（无id则新增，有id则编辑）

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量保存工艺参数（无id则新增，有id则编辑）？"
- "批量保存工艺参数（无id则新增，有id则编辑）怎么做？"
- "帮我批量保存工艺参数（无id则新增，有id则编辑）"

## 接口：创建工艺参数

- **路径：** POST /api/mes/process-parameters/create
- **所属模块：** 工艺参数
- **HTTP 方法：** POST

**功能说明：**
创建工艺参数

**请求参数：**
- standardId: 工序标准ID（类型: integer，可选）
- standardName: （类型: string，可选）
- standardType: （类型: integer，可选）
- standardCode: （类型: string，可选）
- operationId: 工序ID（类型: integer，可选）
- parameterName: 工序参数名称（类型: string，可选）
- parameterType: 工序参数类型（类型: string，可选）
- standardValue: 国家工序参数值（类型: string，可选）
- minValue: 工序参数-最小值（类型: string，可选）
- maxValue: 工序参数-最大值（类型: string，可选）
- unit: 工序参数-单位（类型: string，可选）
- critical: 是否关键工序参数（类型: boolean，可选）
- autoCollect: 工序参数-是否自动采集（类型: boolean，可选）
- toleranceRequired: 工序参数-是否允许误差（类型: boolean，可选）
- toleranceGrade: 工序参数-允许误差等级（类型: string，可选）
- inspectionMethod: 工序参数-检查方法（类型: string，可选）
- unitSystem: 单位制：SI, Imperial（类型: string，可选）
- industryCode: 行业代码（类型: string，可选）
- enterpriseCode: 企业代码（类型: string，可选）
- customRange: 是否自定义范围（类型: boolean，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工艺参数"
- "新增工艺参数记录"
- "创建一个新的工艺参数"
- "帮我添加工艺参数"

## 接口：删除工艺参数

- **路径：** POST /api/mes/process-parameters/delete
- **所属模块：** 工艺参数
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工艺参数记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工艺参数"
- "移除某个工艺参数"

## 接口：获取工艺参数详情

- **路径：** GET /api/mes/process-parameters/detail
- **所属模块：** 工艺参数
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工艺参数中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardId: （类型: integer）
-   data.standardName: （类型: string）
-   data.standardType: （类型: integer）
-   data.standardCode: （类型: string）
-   data.operationId: （类型: integer）
-   data.parameterName: （类型: string）
-   data.parameterType: （类型: string）
-   data.standardValue: （类型: string）
-   data.minValue: （类型: string）
-   data.maxValue: （类型: string）
-   data.unit: （类型: string）
-   data.critical: （类型: boolean）
-   data.autoCollect: （类型: boolean）
-   data.toleranceRequired: （类型: boolean）
-   data.toleranceGrade: （类型: string）
-   data.inspectionMethod: （类型: string）
-   data.unitSystem: （类型: string）
-   data.industryCode: （类型: string）
-   data.enterpriseCode: （类型: string）
-   data.customRange: （类型: boolean）
-   data.status: （类型: integer）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工艺参数的详细信息"
- "工艺参数详情"
- "工艺参数的具体情况"

## 接口：分页查询工艺参数

- **路径：** POST /api/mes/process-parameters/page
- **所属模块：** 工艺参数
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工艺参数列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- standardId: （类型: integer，可选）
- standardName: （类型: string，可选）
- standardType: （类型: string，可选）
- operationId: （类型: integer，可选）
- parameterName: （类型: string，可选）
- critical: （类型: boolean，可选）
- autoCollect: （类型: boolean，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].standardId: （类型: integer）
-     data.list[].standardName: （类型: string）
-     data.list[].standardType: （类型: integer）
-     data.list[].standardCode: （类型: string）
-     data.list[].operationId: （类型: integer）
-     data.list[].parameterName: （类型: string）
-     data.list[].parameterType: （类型: string）
-     data.list[].standardValue: （类型: string）
-     data.list[].minValue: （类型: string）
-     data.list[].maxValue: （类型: string）
-     data.list[].unit: （类型: string）
-     data.list[].critical: （类型: boolean）
-     data.list[].autoCollect: （类型: boolean）
-     data.list[].toleranceRequired: （类型: boolean）
-     data.list[].toleranceGrade: （类型: string）
-     data.list[].inspectionMethod: （类型: string）
-     data.list[].unitSystem: （类型: string）
-     data.list[].industryCode: （类型: string）
-     data.list[].enterpriseCode: （类型: string）
-     data.list[].customRange: （类型: boolean）
-     data.list[].status: （类型: integer）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工艺参数"
- "查看工艺参数列表"
- "有哪些工艺参数？"
- "列出工艺参数"

## 接口：编辑工艺参数

- **路径：** POST /api/mes/process-parameters/update
- **所属模块：** 工艺参数
- **HTTP 方法：** POST

**功能说明：**
编辑工艺参数

**请求参数：**
- standardId: 工序标准ID（类型: integer，可选）
- standardName: （类型: string，可选）
- standardType: （类型: integer，可选）
- standardCode: （类型: string，可选）
- operationId: 工序ID（类型: integer，可选）
- parameterName: 工序参数名称（类型: string，可选）
- parameterType: 工序参数类型（类型: string，可选）
- standardValue: 国家工序参数值（类型: string，可选）
- minValue: 工序参数-最小值（类型: string，可选）
- maxValue: 工序参数-最大值（类型: string，可选）
- unit: 工序参数-单位（类型: string，可选）
- critical: 是否关键工序参数（类型: boolean，可选）
- autoCollect: 工序参数-是否自动采集（类型: boolean，可选）
- toleranceRequired: 工序参数-是否允许误差（类型: boolean，可选）
- toleranceGrade: 工序参数-允许误差等级（类型: string，可选）
- inspectionMethod: 工序参数-检查方法（类型: string，可选）
- unitSystem: 单位制：SI, Imperial（类型: string，可选）
- industryCode: 行业代码（类型: string，可选）
- enterpriseCode: 企业代码（类型: string，可选）
- customRange: 是否自定义范围（类型: boolean，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工艺参数信息"
- "更新工艺参数数据"
- "编辑工艺参数"

## 接口：创建工艺文件

- **路径：** POST /api/mes/process-files/create
- **所属模块：** 工艺文件
- **HTTP 方法：** POST

**功能说明：**
创建工艺文件

**请求参数：**
- stationId: （类型: integer，可选）
- processRouteId: （类型: integer，可选）
- productId: （类型: integer，可选）
- materialType: （类型: integer，可选）
- fileName: （类型: string，可选）
- fileType: （类型: string，可选）
- url: （类型: string，可选）
- fileSize: （类型: integer，可选）
- fileVersion: （类型: string，可选）
- fileTitle: （类型: string，可选）
- fileIdentifier: （类型: string，可选）
- operationId: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工艺文件"
- "新增工艺文件记录"
- "创建一个新的工艺文件"
- "帮我添加工艺文件"

## 接口：删除工艺文件

- **路径：** POST /api/mes/process-files/delete
- **所属模块：** 工艺文件
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工艺文件记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工艺文件"
- "移除某个工艺文件"

## 接口：获取工艺文件详情

- **路径：** GET /api/mes/process-files/detail
- **所属模块：** 工艺文件
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工艺文件中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.stationId: （类型: integer）
-   data.stationName: （类型: string）
-   data.processRouteId: （类型: integer）
-   data.routeCode: （类型: string）
-   data.routeName: （类型: string）
-   data.routeVersion: （类型: string）
-   data.productId: （类型: integer）
-   data.materialType: （类型: integer）
-   data.productName: （类型: string）
-   data.productCode: （类型: string）
-   data.productSpec: （类型: string）
-   data.fileName: （类型: string）
-   data.fileType: （类型: string）
-   data.url: （类型: string）
-   data.fileSize: （类型: integer）
-   data.fileVersion: （类型: string）
-   data.fileTitle: （类型: string）
-   data.fileIdentifier: （类型: string）
-   data.operationId: （类型: string）
-   data.operationCode: （类型: string）
-   data.operationName: （类型: string）
-   data.status: （类型: integer）
-   data.updateBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工艺文件的详细信息"
- "工艺文件详情"
- "工艺文件的具体情况"

## 接口：分页查询工艺文件

- **路径：** POST /api/mes/process-files/page
- **所属模块：** 工艺文件
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工艺文件列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- stationId: （类型: integer，可选）
- processRouteId: （类型: integer，可选）
- productId: （类型: integer，可选）
- materialType: （类型: integer，可选）
- fileName: （类型: string，可选）
- fileType: （类型: string，可选）
- fileVersion: （类型: string，可选）
- fileTitle: （类型: string，可选）
- operationId: （类型: string，可选）
- routeCode: （类型: string，可选）
- routeName: （类型: string，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].stationId: （类型: integer）
-     data.list[].stationName: （类型: string）
-     data.list[].processRouteId: （类型: integer）
-     data.list[].routeCode: （类型: string）
-     data.list[].routeName: （类型: string）
-     data.list[].routeVersion: （类型: string）
-     data.list[].productId: （类型: integer）
-     data.list[].materialType: （类型: integer）
-     data.list[].productName: （类型: string）
-     data.list[].productCode: （类型: string）
-     data.list[].productSpec: （类型: string）
-     data.list[].fileName: （类型: string）
-     data.list[].fileType: （类型: string）
-     data.list[].url: （类型: string）
-     data.list[].fileSize: （类型: integer）
-     data.list[].fileVersion: （类型: string）
-     data.list[].fileTitle: （类型: string）
-     data.list[].fileIdentifier: （类型: string）
-     data.list[].operationId: （类型: string）
-     data.list[].operationCode: （类型: string）
-     data.list[].operationName: （类型: string）
-     data.list[].status: （类型: integer）
-     data.list[].updateBy: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工艺文件"
- "查看工艺文件列表"
- "有哪些工艺文件？"
- "列出工艺文件"

## 接口：编辑工艺文件

- **路径：** POST /api/mes/process-files/update
- **所属模块：** 工艺文件
- **HTTP 方法：** POST

**功能说明：**
编辑工艺文件

**请求参数：**
- stationId: （类型: integer，可选）
- processRouteId: （类型: integer，可选）
- productId: （类型: integer，可选）
- materialType: （类型: integer，可选）
- fileName: （类型: string，可选）
- fileType: （类型: string，可选）
- url: （类型: string，可选）
- fileSize: （类型: integer，可选）
- fileVersion: （类型: string，可选）
- fileTitle: （类型: string，可选）
- fileIdentifier: （类型: string，可选）
- operationId: （类型: string，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工艺文件信息"
- "更新工艺文件数据"
- "编辑工艺文件"

## 接口：批量绑定工序到路线（清空前置依赖）

- **路径：** POST /api/mes/process-routes/bind-operations
- **所属模块：** 工艺路线
- **HTTP 方法：** POST

**功能说明：**
批量绑定工序到路线（清空前置依赖）

**请求参数：**
- routeId: （类型: integer，可选）
- operationInfoIds: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量绑定工序到路线（清空前置依赖）？"
- "批量绑定工序到路线（清空前置依赖）怎么做？"
- "帮我批量绑定工序到路线（清空前置依赖）"

## 接口：创建工艺路线

- **路径：** POST /api/mes/process-routes/create
- **所属模块：** 工艺路线
- **HTTP 方法：** POST

**功能说明：**
创建工艺路线

**请求参数：**
- standardId: （类型: string，可选）
- standardName: （类型: string，可选）
- routeCode: （类型: string，可选）
- standardVersion: （类型: string，可选）
- materialCode: （类型: string，可选）
- standardType: （类型: integer，可选）
- standardCode: （类型: string，可选）
- referenceStandards: （类型: string，可选）
- routeType: （类型: string，可选）
- parallelAllowed: （类型: boolean，可选）
- alternativeAllowed: （类型: boolean，可选）
- effectiveDate: （类型: string，可选）
- expireDate: （类型: string，可选）
- mandatoryInspectionItems: （类型: string，可选）
- forceExecution: （类型: boolean，可选）
- standardOrganization: （类型: string，可选）
- multiLanguageSupported: （类型: boolean，可选）
- industryCode: （类型: string，可选）
- industryName: （类型: string，可选）
- enterpriseCode: （类型: string，可选）
- enterpriseName: （类型: string，可选）
- customizable: （类型: boolean，可选）
- description: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工艺路线"
- "新增工艺路线记录"
- "创建一个新的工艺路线"
- "帮我添加工艺路线"

## 接口：删除工艺路线

- **路径：** POST /api/mes/process-routes/delete
- **所属模块：** 工艺路线
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工艺路线记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工艺路线"
- "移除某个工艺路线"

## 接口：获取工艺路线详情

- **路径：** GET /api/mes/process-routes/detail
- **所属模块：** 工艺路线
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工艺路线中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.standardId: （类型: string）
-   data.standardName: （类型: string）
-   data.routeCode: （类型: string）
-   data.standardVersion: （类型: string）
-   data.materialCode: （类型: string）
-   data.standardType: （类型: integer）
-   data.standardCode: （类型: string）
-   data.referenceStandards: （类型: string）
-   data.routeType: （类型: string）
-   data.parallelAllowed: （类型: boolean）
-   data.alternativeAllowed: （类型: boolean）
-   data.effectiveDate: （类型: string）
-   data.expireDate: （类型: string）
-   data.mandatoryInspectionItems: （类型: string）
-   data.forceExecution: （类型: boolean）
-   data.standardOrganization: （类型: string）
-   data.multiLanguageSupported: （类型: boolean）
-   data.industryCode: （类型: string）
-   data.industryName: （类型: string）
-   data.enterpriseCode: （类型: string）
-   data.enterpriseName: （类型: string）
-   data.customizable: （类型: boolean）
-   data.description: （类型: string）
-   data.status: （类型: integer）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
-   data.createBy: （类型: string）
-   data.updateBy: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工艺路线的详细信息"
- "工艺路线详情"
- "工艺路线的具体情况"

## 接口：启用/禁用工艺路线

- **路径：** POST /api/mes/process-routes/enabled
- **所属模块：** 工艺路线
- **HTTP 方法：** POST

**功能说明：**
启用/禁用工艺路线

**请求参数：**
- id （查询参数）: （类型: integer，必填）
- isEnabled （查询参数）: （类型: boolean，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么启用/禁用工艺路线？"
- "启用/禁用工艺路线怎么做？"
- "帮我启用/禁用工艺路线"

## 接口：查询工艺路线编码列表

- **路径：** POST /api/mes/process-routes/list-code
- **所属模块：** 工艺路线
- **HTTP 方法：** POST

**功能说明：**
查询工艺路线编码列表

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- standardId: （类型: string，可选）
- standardName: （类型: string，可选）
- standardType: （类型: string，可选）
- standardCode: （类型: string，可选）
- routeCode: （类型: string，可选）
- materialCode: （类型: string，可选）
- routeType: （类型: string，可选）
- parallelAllowed: （类型: boolean，可选）
- effectiveDateBegin: （类型: string，可选）
- effectiveDateEnd: （类型: string，可选）
- expireDateBegin: （类型: string，可选）
- expireDateEnd: （类型: string，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: 主键ID（类型: string）
-   data[].code: 工艺路线编码（类型: string）
-   data[].name: 工艺路线名称（类型: string）
-   data[].label: （类型: string）
-   data[].value: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工艺路线编码列表"
- "查看工艺路线编码列表列表"
- "有哪些工艺路线编码列表？"
- "列出工艺路线编码列表"

## 接口：分页查询工艺路线

- **路径：** POST /api/mes/process-routes/page
- **所属模块：** 工艺路线
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工艺路线列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- standardId: （类型: string，可选）
- standardName: （类型: string，可选）
- standardType: （类型: string，可选）
- standardCode: （类型: string，可选）
- routeCode: （类型: string，可选）
- materialCode: （类型: string，可选）
- routeType: （类型: string，可选）
- parallelAllowed: （类型: boolean，可选）
- effectiveDateBegin: （类型: string，可选）
- effectiveDateEnd: （类型: string，可选）
- expireDateBegin: （类型: string，可选）
- expireDateEnd: （类型: string，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].standardId: （类型: string）
-     data.list[].standardName: （类型: string）
-     data.list[].routeCode: （类型: string）
-     data.list[].standardVersion: （类型: string）
-     data.list[].materialCode: （类型: string）
-     data.list[].standardType: （类型: integer）
-     data.list[].standardCode: （类型: string）
-     data.list[].referenceStandards: （类型: string）
-     data.list[].routeType: （类型: string）
-     data.list[].parallelAllowed: （类型: boolean）
-     data.list[].alternativeAllowed: （类型: boolean）
-     data.list[].effectiveDate: （类型: string）
-     data.list[].expireDate: （类型: string）
-     data.list[].mandatoryInspectionItems: （类型: string）
-     data.list[].forceExecution: （类型: boolean）
-     data.list[].standardOrganization: （类型: string）
-     data.list[].multiLanguageSupported: （类型: boolean）
-     data.list[].industryCode: （类型: string）
-     data.list[].industryName: （类型: string）
-     data.list[].enterpriseCode: （类型: string）
-     data.list[].enterpriseName: （类型: string）
-     data.list[].customizable: （类型: boolean）
-     data.list[].description: （类型: string）
-     data.list[].status: （类型: integer）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
-     data.list[].createBy: （类型: string）
-     data.list[].updateBy: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工艺路线"
- "查看工艺路线列表"
- "有哪些工艺路线？"
- "列出工艺路线"

## 接口：编辑工艺路线

- **路径：** POST /api/mes/process-routes/update
- **所属模块：** 工艺路线
- **HTTP 方法：** POST

**功能说明：**
编辑工艺路线

**请求参数：**
- standardId: （类型: string，可选）
- standardName: （类型: string，可选）
- routeCode: （类型: string，可选）
- standardVersion: （类型: string，可选）
- materialCode: （类型: string，可选）
- standardType: （类型: integer，可选）
- standardCode: （类型: string，可选）
- referenceStandards: （类型: string，可选）
- routeType: （类型: string，可选）
- parallelAllowed: （类型: boolean，可选）
- alternativeAllowed: （类型: boolean，可选）
- effectiveDate: （类型: string，可选）
- expireDate: （类型: string，可选）
- mandatoryInspectionItems: （类型: string，可选）
- forceExecution: （类型: boolean，可选）
- standardOrganization: （类型: string，可选）
- multiLanguageSupported: （类型: boolean，可选）
- industryCode: （类型: string，可选）
- industryName: （类型: string，可选）
- enterpriseCode: （类型: string，可选）
- enterpriseName: （类型: string，可选）
- customizable: （类型: boolean，可选）
- description: （类型: string，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工艺路线信息"
- "更新工艺路线数据"
- "编辑工艺路线"

## 接口：创建工装

- **路径：** POST /api/mes/work-tools/create
- **所属模块：** 工装
- **HTTP 方法：** POST

**功能说明：**
创建工装

**请求参数：**
- categoryId: （类型: integer，可选）
- code: （类型: string，可选）
- name: （类型: string，可选）
- spec: （类型: string，可选）
- unit: （类型: string，可选）
- currentStatus: （类型: number，可选）
- maxStatus: （类型: number，可选）
- expectedLoss: （类型: number，可选）
- actualLoss: （类型: number，可选）
- remark: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工装"
- "新增工装记录"
- "创建一个新的工装"
- "帮我添加工装"

## 接口：删除工装

- **路径：** POST /api/mes/work-tools/delete
- **所属模块：** 工装
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工装记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工装"
- "移除某个工装"

## 接口：获取工装详情

- **路径：** GET /api/mes/work-tools/detail
- **所属模块：** 工装
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工装中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.categoryId: （类型: integer）
-   data.code: （类型: string）
-   data.name: （类型: string）
-   data.spec: （类型: string）
-   data.unit: （类型: string）
-   data.currentStatus: （类型: number）
-   data.maxStatus: （类型: number）
-   data.expectedLoss: （类型: number）
-   data.actualLoss: （类型: number）
-   data.remark: （类型: string）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工装的详细信息"
- "工装详情"
- "工装的具体情况"

## 接口：分页查询工装

- **路径：** POST /api/mes/work-tools/page
- **所属模块：** 工装
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工装列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- categoryId: （类型: integer，可选）
- code: （类型: string，可选）
- name: （类型: string，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].categoryId: （类型: integer）
-     data.list[].code: （类型: string）
-     data.list[].name: （类型: string）
-     data.list[].spec: （类型: string）
-     data.list[].unit: （类型: string）
-     data.list[].currentStatus: （类型: number）
-     data.list[].maxStatus: （类型: number）
-     data.list[].expectedLoss: （类型: number）
-     data.list[].actualLoss: （类型: number）
-     data.list[].remark: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工装"
- "查看工装列表"
- "有哪些工装？"
- "列出工装"

## 接口：编辑工装

- **路径：** POST /api/mes/work-tools/update
- **所属模块：** 工装
- **HTTP 方法：** POST

**功能说明：**
编辑工装

**请求参数：**
- categoryId: （类型: integer，可选）
- code: （类型: string，可选）
- name: （类型: string，可选）
- spec: （类型: string，可选）
- unit: （类型: string，可选）
- currentStatus: （类型: number，可选）
- maxStatus: （类型: number，可选）
- expectedLoss: （类型: number，可选）
- actualLoss: （类型: number，可选）
- remark: （类型: string，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工装信息"
- "更新工装数据"
- "编辑工装"

## 接口：创建工装分类

- **路径：** POST /api/mes/work-tool-categories/create
- **所属模块：** 工装分类
- **HTTP 方法：** POST

**功能说明：**
创建工装分类

**请求参数：**
- stationId: （类型: integer，可选）
- name: （类型: string，可选）
- code: （类型: string，可选）
- remark: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工装分类"
- "新增工装分类记录"
- "创建一个新的工装分类"
- "帮我添加工装分类"

## 接口：删除工装分类

- **路径：** POST /api/mes/work-tool-categories/delete
- **所属模块：** 工装分类
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工装分类记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工装分类"
- "移除某个工装分类"

## 接口：获取工装分类详情

- **路径：** GET /api/mes/work-tool-categories/detail
- **所属模块：** 工装分类
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工装分类中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.stationId: （类型: integer）
-   data.name: （类型: string）
-   data.code: （类型: string）
-   data.remark: （类型: string）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工装分类的详细信息"
- "工装分类详情"
- "工装分类的具体情况"

## 接口：分页查询工装分类

- **路径：** POST /api/mes/work-tool-categories/page
- **所属模块：** 工装分类
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工装分类列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- stationId: （类型: integer，可选）
- name: （类型: string，可选）
- code: （类型: string，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].stationId: （类型: integer）
-     data.list[].name: （类型: string）
-     data.list[].code: （类型: string）
-     data.list[].remark: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工装分类"
- "查看工装分类列表"
- "有哪些工装分类？"
- "列出工装分类"

## 接口：编辑工装分类

- **路径：** POST /api/mes/work-tool-categories/update
- **所属模块：** 工装分类
- **HTTP 方法：** POST

**功能说明：**
编辑工装分类

**请求参数：**
- stationId: （类型: integer，可选）
- name: （类型: string，可选）
- code: （类型: string，可选）
- remark: （类型: string，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工装分类信息"
- "更新工装分类数据"
- "编辑工装分类"

## 接口：创建工装维保

- **路径：** POST /api/mes/work-tool-maintenances/create
- **所属模块：** 工装维保
- **HTTP 方法：** POST

**功能说明：**
创建工装维保

**请求参数：**
- toolId: （类型: integer，可选）
- content: （类型: string，可选）
- reportTime: （类型: string，可选）
- workerIds: （类型: array<string>，可选）
- status: （类型: integer，可选）
- completeTime: （类型: string，可选）
- duration: （类型: number，可选）
- cost: （类型: number，可选）
- repairType: （类型: integer，可选）
- repairedMount: （类型: number，可选）
- repairedPercent: （类型: number，可选）
- repairedLoss: （类型: number，可选）
- remark: （类型: string，可选）
- useToolIds: （类型: array<integer>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个工装维保"
- "新增工装维保记录"
- "创建一个新的工装维保"
- "帮我添加工装维保"

## 接口：删除工装维保

- **路径：** POST /api/mes/work-tool-maintenances/delete
- **所属模块：** 工装维保
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的工装维保记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除工装维保"
- "移除某个工装维保"

## 接口：获取工装维保详情

- **路径：** GET /api/mes/work-tool-maintenances/detail
- **所属模块：** 工装维保
- **HTTP 方法：** GET

**功能说明：**
根据ID获取工装维保中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.toolId: （类型: integer）
-   data.content: （类型: string）
-   data.reportTime: （类型: string）
-   data.workerIds: （类型: string）
-   data.workerNames: （类型: string）
-   data.status: （类型: integer）
-   data.completeTime: （类型: string）
-   data.duration: （类型: number）
-   data.cost: （类型: number）
-   data.repairType: （类型: integer）
-   data.repairedMount: （类型: number）
-   data.repairedPercent: （类型: number）
-   data.repairedLoss: （类型: number）
-   data.remark: （类型: string）
-   data.useToolIds: （类型: string）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看工装维保的详细信息"
- "工装维保详情"
- "工装维保的具体情况"

## 接口：分页查询工装维保

- **路径：** POST /api/mes/work-tool-maintenances/page
- **所属模块：** 工装维保
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工装维保列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- toolId: （类型: integer，可选）
- status: （类型: integer，可选）
- reportTimeBegin: （类型: string，可选）
- reportTimeEnd: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].toolId: （类型: integer）
-     data.list[].content: （类型: string）
-     data.list[].reportTime: （类型: string）
-     data.list[].workerIds: （类型: string）
-     data.list[].workerNames: （类型: string）
-     data.list[].status: （类型: integer）
-     data.list[].completeTime: （类型: string）
-     data.list[].duration: （类型: number）
-     data.list[].cost: （类型: number）
-     data.list[].repairType: （类型: integer）
-     data.list[].repairedMount: （类型: number）
-     data.list[].repairedPercent: （类型: number）
-     data.list[].repairedLoss: （类型: number）
-     data.list[].remark: （类型: string）
-     data.list[].useToolIds: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工装维保"
- "查看工装维保列表"
- "有哪些工装维保？"
- "列出工装维保"

## 接口：编辑工装维保

- **路径：** POST /api/mes/work-tool-maintenances/update
- **所属模块：** 工装维保
- **HTTP 方法：** POST

**功能说明：**
编辑工装维保

**请求参数：**
- toolId: （类型: integer，可选）
- content: （类型: string，可选）
- reportTime: （类型: string，可选）
- workerIds: （类型: array<string>，可选）
- status: （类型: integer，可选）
- completeTime: （类型: string，可选）
- duration: （类型: number，可选）
- cost: （类型: number，可选）
- repairType: （类型: integer，可选）
- repairedMount: （类型: number，可选）
- repairedPercent: （类型: number，可选）
- repairedLoss: （类型: number，可选）
- remark: （类型: string，可选）
- useToolIds: （类型: array<integer>，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改工装维保信息"
- "更新工装维保数据"
- "编辑工装维保"

## 接口：创建异常事件

- **路径：** POST /api/mes/abnormal-events/create
- **所属模块：** 异常事件
- **HTTP 方法：** POST

**功能说明：**
创建异常事件

**请求参数：**
- workCenterId: （类型: integer，可选）
- startTime: （类型: string，可选）
- abnormalType: （类型: integer，可选）
- duration: （类型: number，可选）
- description: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个异常事件"
- "新增异常事件记录"
- "创建一个新的异常事件"
- "帮我添加异常事件"

## 接口：处理异常事件

- **路径：** POST /api/mes/abnormal-events/handle
- **所属模块：** 异常事件
- **HTTP 方法：** POST

**功能说明：**
处理异常事件

**请求参数：**
- id: （类型: integer，可选）
- endTime: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "怎么处理异常事件？"
- "处理异常事件怎么做？"
- "帮我处理异常事件"

## 接口：分页查询异常事件

- **路径：** POST /api/mes/abnormal-events/page
- **所属模块：** 异常事件
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询异常事件列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- workCenterId: （类型: integer，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].workCenterId: （类型: integer）
-     data.list[].workCenterName: 工作中心名称（类型: string）
-     data.list[].abnormalType: （类型: integer）
-     data.list[].abnormalTypeDesc: 异常类型描述（类型: string）
-     data.list[].startTime: （类型: string）
-     data.list[].endTime: （类型: string）
-     data.list[].duration: 持续时长(小时)（类型: number）
-     data.list[].description: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询异常事件"
- "查看异常事件列表"
- "有哪些异常事件？"
- "列出异常事件"

## 接口：批量导入工位

- **路径：** POST /api/mes/work-sits/import
- **所属模块：** 批量导入
- **HTTP 方法：** POST

**功能说明：**
批量导入工位

**请求参数：**
- file: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.successCount: （类型: integer）
-   data.failCount: （类型: integer）
-   data.errors: （类型: array<string>）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量导入工位？"
- "批量导入工位怎么做？"
- "帮我批量导入工位"

## 接口：批量导入操作工

- **路径：** POST /api/mes/workers/import
- **所属模块：** 批量导入
- **HTTP 方法：** POST

**功能说明：**
批量导入操作工

**请求参数：**
- file: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.successCount: （类型: integer）
-   data.failCount: （类型: integer）
-   data.errors: （类型: array<string>）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量导入操作工？"
- "批量导入操作工怎么做？"
- "帮我批量导入操作工"

## 接口：审核报工记录

- **路径：** POST /api/mes/work-report-records/audit
- **所属模块：** 报工记录
- **HTTP 方法：** POST

**功能说明：**
审核报工记录

**请求参数：**
- id （查询参数）: （类型: integer，必填）
- auditStatus （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么审核报工记录？"
- "审核报工记录怎么做？"
- "帮我审核报工记录"

## 接口：批量审核报工记录

- **路径：** POST /api/mes/work-report-records/batch-audit
- **所属模块：** 报工记录
- **HTTP 方法：** POST

**功能说明：**
批量审核报工记录

**请求参数：**
- ids （查询参数）: （类型: array，必填）
- auditStatus （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量审核报工记录？"
- "批量审核报工记录怎么做？"
- "帮我批量审核报工记录"

## 接口：创建报工记录

- **路径：** POST /api/mes/work-report-records/create
- **所属模块：** 报工记录
- **HTTP 方法：** POST

**功能说明：**
创建报工记录

**请求参数：**
- id: （类型: integer，可选）
- workDispatchId: （类型: integer，可选）
- centerId: （类型: integer，可选）
- workSitId: （类型: integer，可选）
- workerId: （类型: integer，可选）
- groupId: （类型: integer，可选）
- productType: （类型: string，可选）
- productId: （类型: integer，可选）
- materialType: 物料类型, 1=产品，2=源材料（类型: integer，可选）
- productCode: （类型: string，可选）
- productName: （类型: string，可选）
- specModel: （类型: string，可选）
- unitId: （类型: integer，可选）
- unitName: （类型: string，可选）
- centerName: （类型: string，可选）
- workSitName: （类型: string，可选）
- operatorName: （类型: string，可选）
- shiftName: （类型: string，可选）
- workHours: （类型: number，可选）
- total: （类型: number，可选）
- good: （类型: number，可选）
- bad: （类型: number，可选）
- workReportId: （类型: integer，可选）
- auditorName: （类型: string，可选）
- auditTime: （类型: string，可选）
- remark: （类型: string，可选）
- reportStatus: （类型: integer，可选）
- auditStatus: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个报工记录"
- "新增报工记录记录"
- "创建一个新的报工记录"
- "帮我添加报工记录"

## 接口：删除报工记录

- **路径：** POST /api/mes/work-report-records/delete
- **所属模块：** 报工记录
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的报工记录记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除报工记录"
- "移除某个报工记录"

## 接口：获取报工记录详情

- **路径：** GET /api/mes/work-report-records/detail
- **所属模块：** 报工记录
- **HTTP 方法：** GET

**功能说明：**
根据ID获取报工记录中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.status: （类型: integer）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.productId: （类型: integer）
-   data.materialType: （类型: integer）
-   data.workReportId: （类型: integer）
-   data.workDispatchId: （类型: integer）
-   data.centerId: （类型: integer）
-   data.workSitId: （类型: integer）
-   data.workerId: （类型: integer）
-   data.groupId: （类型: integer）
-   data.scheduleShiftId: （类型: integer）
-   data.productType: （类型: string）
-   data.productCode: （类型: string）
-   data.productName: （类型: string）
-   data.specModel: （类型: string）
-   data.unitId: （类型: integer）
-   data.unitName: （类型: string）
-   data.workHours: （类型: number）
-   data.total: （类型: number）
-   data.good: （类型: number）
-   data.bad: （类型: number）
-   data.auditorId: （类型: string）
-   data.auditTime: （类型: string）
-   data.remark: （类型: string）
-   data.reportStatus: （类型: integer）
-   data.auditStatus: （类型: integer）
-   data.batchNo: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看报工记录的详细信息"
- "报工记录详情"
- "报工记录的具体情况"

## 接口：导出报工记录

- **路径：** POST /api/mes/work-report-records/export
- **所属模块：** 报工记录
- **HTTP 方法：** POST

**功能说明：**
导出报工记录

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- workReportId: （类型: integer，可选）
- dispatchCode: （类型: string，可选）
- producingWorkOrderNo: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- productCode: （类型: string，可选）
- productName: （类型: string，可选）
- operatorName: （类型: string，可选）
- auditStatus: （类型: integer，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- centerId: （类型: integer，可选）
- batchNo: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么导出报工记录？"
- "导出报工记录怎么做？"
- "帮我导出报工记录"

## 接口：分页查询报工记录

- **路径：** POST /api/mes/work-report-records/page
- **所属模块：** 报工记录
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询报工记录列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- workReportId: （类型: integer，可选）
- dispatchCode: （类型: string，可选）
- producingWorkOrderNo: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- productCode: （类型: string，可选）
- productName: （类型: string，可选）
- operatorName: （类型: string，可选）
- auditStatus: （类型: integer，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- centerId: （类型: integer，可选）
- batchNo: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].workReportId: （类型: integer）
-     data.list[].workDispatchId: （类型: integer）
-     data.list[].centerId: （类型: integer）
-     data.list[].workSitId: （类型: integer）
-     data.list[].workerId: （类型: integer）
-     data.list[].groupId: （类型: integer）
-     data.list[].scheduleShiftId: （类型: integer）
-     data.list[].shiftName: （类型: string）
-     data.list[].productType: （类型: string）
-     data.list[].productId: （类型: integer）
-     data.list[].materialType: （类型: integer）
-     data.list[].productCode: （类型: string）
-     data.list[].productName: （类型: string）
-     data.list[].specModel: （类型: string）
-     data.list[].unitId: （类型: integer）
-     data.list[].unitName: （类型: string）
-     data.list[].workHours: （类型: number）
-     data.list[].total: （类型: number）
-     data.list[].good: （类型: number）
-     data.list[].bad: （类型: number）
-     data.list[].auditorId: （类型: string）
-     data.list[].auditorName: （类型: string）
-     data.list[].auditTime: （类型: string）
-     data.list[].remark: （类型: string）
-     data.list[].reportStatus: （类型: integer）
-     data.list[].auditStatus: （类型: integer）
-     data.list[].auditStatusName: （类型: string）
-     data.list[].batchNo: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].dispatchCode: （类型: string）
-     data.list[].centerName: （类型: string）
-     data.list[].workSitName: （类型: string）
-     data.list[].processCode: （类型: string）
-     data.list[].processOperationCode: （类型: string）
-     data.list[].processOperationName: （类型: string）
-     data.list[].productionWorkOrderNo: （类型: string）
-     data.list[].productionOrderNo: （类型: string）
-     data.list[].operatorName: （类型: string）
-     data.list[].yieldRate: （类型: number）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.auditTotal: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询报工记录"
- "查看报工记录列表"
- "有哪些报工记录？"
- "列出报工记录"

## 接口：编辑报工记录

- **路径：** POST /api/mes/work-report-records/update
- **所属模块：** 报工记录
- **HTTP 方法：** POST

**功能说明：**
编辑报工记录

**请求参数：**
- id: （类型: integer，可选）
- status: （类型: integer，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）
- productId: （类型: integer，可选）
- materialType: （类型: integer，可选）
- workReportId: （类型: integer，可选）
- workDispatchId: （类型: integer，可选）
- centerId: （类型: integer，可选）
- workSitId: （类型: integer，可选）
- workerId: （类型: integer，可选）
- groupId: （类型: integer，可选）
- scheduleShiftId: （类型: integer，可选）
- productType: （类型: string，可选）
- productCode: （类型: string，可选）
- productName: （类型: string，可选）
- specModel: （类型: string，可选）
- unitId: （类型: integer，可选）
- unitName: （类型: string，可选）
- workHours: （类型: number，可选）
- total: （类型: number，可选）
- good: （类型: number，可选）
- bad: （类型: number，可选）
- auditorId: （类型: string，可选）
- auditTime: （类型: string，可选）
- remark: （类型: string，可选）
- reportStatus: （类型: integer，可选）
- auditStatus: （类型: integer，可选）
- batchNo: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改报工记录信息"
- "更新报工记录数据"
- "编辑报工记录"

## 接口：创建排班班次

- **路径：** POST /api/mes/work-schedule-shifts/create
- **所属模块：** 排班班次
- **HTTP 方法：** POST

**功能说明：**
创建排班班次

**请求参数：**
- centerId: （类型: integer，可选）
- scheduleId: （类型: integer，可选）
- sitId: （类型: integer，可选）
- startTime: （类型: string，可选）
- endTime: （类型: string，可选）
- workerId: （类型: string，可选）
- shiftType: （类型: integer，可选）
- roundType: （类型: integer，可选）
- shift: （类型: integer，可选）
- round: （类型: integer，可选）
- remark: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个排班班次"
- "新增排班班次记录"
- "创建一个新的排班班次"
- "帮我添加排班班次"

## 接口：删除排班班次

- **路径：** POST /api/mes/work-schedule-shifts/delete
- **所属模块：** 排班班次
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的排班班次记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除排班班次"
- "移除某个排班班次"

## 接口：获取排班班次详情

- **路径：** GET /api/mes/work-schedule-shifts/detail
- **所属模块：** 排班班次
- **HTTP 方法：** GET

**功能说明：**
根据ID获取排班班次中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.centerId: （类型: integer）
-   data.scheduleId: （类型: integer）
-   data.sitId: （类型: integer）
-   data.sitName: （类型: string）
-   data.startTime: （类型: string）
-   data.endTime: （类型: string）
-   data.workerId: （类型: integer）
-   data.workerName: （类型: string）
-   data.shiftType: （类型: integer）
-   data.roundType: （类型: integer）
-   data.shift: （类型: integer）
-   data.round: （类型: integer）
-   data.header: （类型: string）
-   data.remark: （类型: string）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看排班班次的详细信息"
- "排班班次详情"
- "排班班次的具体情况"

## 接口：导出排班班次

- **路径：** POST /api/mes/work-schedule-shifts/export
- **所属模块：** 排班班次
- **HTTP 方法：** POST

**功能说明：**
导出排班班次

**请求参数：**
- centerId: （类型: integer，可选）
- monthly: （类型: boolean，可选）
- weekly: （类型: boolean，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么导出排班班次？"
- "导出排班班次怎么做？"
- "帮我导出排班班次"

## 接口：分页查询排班班次

- **路径：** POST /api/mes/work-schedule-shifts/page
- **所属模块：** 排班班次
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询排班班次列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: 页码（类型: integer，可选）
- pageSize: 每页数量（类型: integer，可选）
- centerId: 工作中心 ID（类型: integer，可选）
- scheduleId: 排班 ID（类型: integer，可选）
- sitId: 班次 ID（类型: integer，可选）
- workerId: 员工 ID（类型: integer，可选）
- shiftType: 班次类型（类型: integer，可选）
- roundType: 轮次类型（类型: integer，可选）
- startTime: 开始时间（类型: string，可选）
- endTime: 结束时间（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].centerId: （类型: integer）
-     data.list[].scheduleId: （类型: integer）
-     data.list[].sitId: （类型: integer）
-     data.list[].sitName: （类型: string）
-     data.list[].startTime: （类型: string）
-     data.list[].endTime: （类型: string）
-     data.list[].workerId: （类型: integer）
-     data.list[].workerName: （类型: string）
-     data.list[].shiftType: （类型: integer）
-     data.list[].roundType: （类型: integer）
-     data.list[].shift: （类型: integer）
-     data.list[].round: （类型: integer）
-     data.list[].header: （类型: string）
-     data.list[].remark: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询排班班次"
- "查看排班班次列表"
- "有哪些排班班次？"
- "列出排班班次"

## 接口：按天分页查询排班班次（按工位分组）

- **路径：** POST /api/mes/work-schedule-shifts/page-by-day
- **所属模块：** 排班班次
- **HTTP 方法：** POST

**功能说明：**
按天分页查询排班班次（按工位分组）

**请求参数：**
- pageNum: 页码（类型: integer，可选）
- pageSize: 每页数量（类型: integer，可选）
- centerId: 工作中心 ID（类型: integer，可选）
- scheduleId: 排班 ID（类型: integer，可选）
- sitId: 班次 ID（类型: integer，可选）
- workerId: 员工 ID（类型: integer，可选）
- shiftType: 班次类型（类型: integer，可选）
- roundType: 轮次类型（类型: integer，可选）
- startTime: 开始时间（类型: string，可选）
- endTime: 结束时间（类型: string，可选）
- today: 当前日期（类型: string，必填）
- displayWindow: 显示天数（类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].sitId: （类型: integer）
-     data.list[].sitName: （类型: string）
-     data.list[].centerId: （类型: integer）
-     data.list[].centerName: （类型: string）
-     data.list[].shift: （类型: integer）
-     data.list[].shiftName: （类型: string）
-     data.list[].shifts: （类型: array<object>）
-       data.list[].shifts[].id: （类型: integer）
-       data.list[].shifts[].centerId: （类型: integer）
-       data.list[].shifts[].scheduleId: （类型: integer）
-       data.list[].shifts[].sitId: （类型: integer）
-       data.list[].shifts[].sitName: （类型: string）
-       data.list[].shifts[].startTime: （类型: string）
-       data.list[].shifts[].endTime: （类型: string）
-       data.list[].shifts[].workerId: （类型: integer）
-       data.list[].shifts[].workerName: （类型: string）
-       data.list[].shifts[].shiftType: （类型: integer）
-       data.list[].shifts[].roundType: （类型: integer）
-       data.list[].shifts[].shift: （类型: integer）
-       data.list[].shifts[].round: （类型: integer）
-       data.list[].shifts[].header: （类型: string）
-       data.list[].shifts[].remark: （类型: string）
-       data.list[].shifts[].createTime: （类型: string）
-       data.list[].shifts[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询排班班次（按工位分组）"
- "查看排班班次（按工位分组）列表"
- "有哪些排班班次（按工位分组）？"
- "列出排班班次（按工位分组）"

## 接口：按轮次分页查询排班班次（按工位分组）

- **路径：** POST /api/mes/work-schedule-shifts/page-by-round
- **所属模块：** 排班班次
- **HTTP 方法：** POST

**功能说明：**
按轮次分页查询排班班次（按工位分组）

**请求参数：**
- pageNum: 页码（类型: integer，可选）
- pageSize: 每页数量（类型: integer，可选）
- centerId: 工作中心 ID（类型: integer，可选）
- scheduleId: 排班 ID（类型: integer，可选）
- sitId: 班次 ID（类型: integer，可选）
- workerId: 员工 ID（类型: integer，可选）
- shiftType: 班次类型（类型: integer，可选）
- roundType: 轮班方式（1=天轮班，2=周轮班，3=月轮班，4=季度轮班）（类型: integer，必填）
- startTime: 开始时间（类型: string，可选）
- endTime: 结束时间（类型: string，可选）
- today: 当前日期（类型: string，必填）
- displayWindow: 显示窗口大小（类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].sitId: （类型: integer）
-     data.list[].sitName: （类型: string）
-     data.list[].centerId: （类型: integer）
-     data.list[].centerName: （类型: string）
-     data.list[].shift: （类型: integer）
-     data.list[].shiftName: （类型: string）
-     data.list[].shifts: （类型: array<object>）
-       data.list[].shifts[].id: （类型: integer）
-       data.list[].shifts[].centerId: （类型: integer）
-       data.list[].shifts[].scheduleId: （类型: integer）
-       data.list[].shifts[].sitId: （类型: integer）
-       data.list[].shifts[].sitName: （类型: string）
-       data.list[].shifts[].startTime: （类型: string）
-       data.list[].shifts[].endTime: （类型: string）
-       data.list[].shifts[].workerId: （类型: integer）
-       data.list[].shifts[].workerName: （类型: string）
-       data.list[].shifts[].shiftType: （类型: integer）
-       data.list[].shifts[].roundType: （类型: integer）
-       data.list[].shifts[].shift: （类型: integer）
-       data.list[].shifts[].round: （类型: integer）
-       data.list[].shifts[].header: （类型: string）
-       data.list[].shifts[].remark: （类型: string）
-       data.list[].shifts[].createTime: （类型: string）
-       data.list[].shifts[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询排班班次（按工位分组）"
- "查看排班班次（按工位分组）列表"
- "有哪些排班班次（按工位分组）？"
- "列出排班班次（按工位分组）"

## 接口：编辑排班班次

- **路径：** POST /api/mes/work-schedule-shifts/update
- **所属模块：** 排班班次
- **HTTP 方法：** POST

**功能说明：**
编辑排班班次

**请求参数：**
- centerId: （类型: integer，可选）
- scheduleId: （类型: integer，可选）
- sitId: （类型: integer，可选）
- startTime: （类型: string，可选）
- endTime: （类型: string，可选）
- workerId: （类型: string，可选）
- shiftType: （类型: integer，可选）
- roundType: （类型: integer，可选）
- shift: （类型: integer，可选）
- round: （类型: integer，可选）
- remark: （类型: string，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改排班班次信息"
- "更新排班班次数据"
- "编辑排班班次"

## 接口：查询工作中心下的工人列表

- **路径：** GET /api/mes/work-schedule-shifts/worker-list
- **所属模块：** 排班班次
- **HTTP 方法：** GET

**功能说明：**
查询工作中心下的工人列表

**请求参数：**
- workCenterId （查询参数）: （类型: integer，必填）
- name （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].employeeName: （类型: string）
-   data[].employeeId: （类型: string）
-   data[].value: （类型: integer）
-   data[].label: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工作中心下的工人列表"
- "查看工作中心下的工人列表列表"
- "有哪些工作中心下的工人列表？"
- "列出工作中心下的工人列表"

## 接口：创建排班计划

- **路径：** POST /api/mes/work-schedules/create
- **所属模块：** 排班计划
- **HTTP 方法：** POST

**功能说明：**
创建排班计划

**请求参数：**
- centerId: （类型: integer，必填）
- scheduleCode: （类型: string，可选）
- scheduleName: （类型: string，必填）
- groupId: （类型: integer，可选）
- remark: （类型: string，可选）
- startTime: （类型: string，可选）
- endTime: （类型: string，可选）
- shiftType: （类型: integer，可选）
- roundType: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个排班计划"
- "新增排班计划记录"
- "创建一个新的排班计划"
- "帮我添加排班计划"

## 接口：删除排班计划

- **路径：** POST /api/mes/work-schedules/delete
- **所属模块：** 排班计划
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的排班计划记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除排班计划"
- "移除某个排班计划"

## 接口：获取排班计划详情

- **路径：** GET /api/mes/work-schedules/detail
- **所属模块：** 排班计划
- **HTTP 方法：** GET

**功能说明：**
根据ID获取排班计划中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.centerId: （类型: integer）
-   data.scheduleCode: （类型: string）
-   data.scheduleName: （类型: string）
-   data.groupId: （类型: integer）
-   data.remark: （类型: string）
-   data.startTime: （类型: string）
-   data.endTime: （类型: string）
-   data.shiftType: （类型: integer）
-   data.roundType: （类型: integer）
-   data.status: （类型: integer）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看排班计划的详细信息"
- "排班计划详情"
- "排班计划的具体情况"

## 接口：启用/停用排班计划

- **路径：** POST /api/mes/work-schedules/enable
- **所属模块：** 排班计划
- **HTTP 方法：** POST

**功能说明：**
启用/停用排班计划

**请求参数：**
- id （查询参数）: （类型: integer，必填）
- enabled （查询参数）: （类型: boolean，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么启用/停用排班计划？"
- "启用/停用排班计划怎么做？"
- "帮我启用/停用排班计划"

## 接口：分页查询排班计划

- **路径：** POST /api/mes/work-schedules/page
- **所属模块：** 排班计划
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询排班计划列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- scheduleCode: （类型: string，可选）
- scheduleName: （类型: string，可选）
- groupId: （类型: integer，可选）
- shiftType: （类型: string，可选）
- roundType: （类型: string，可选）
- startTime: （类型: string，可选）
- endTime: （类型: string，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].centerId: （类型: integer）
-     data.list[].scheduleCode: （类型: string）
-     data.list[].scheduleName: （类型: string）
-     data.list[].groupId: （类型: integer）
-     data.list[].remark: （类型: string）
-     data.list[].startTime: （类型: string）
-     data.list[].endTime: （类型: string）
-     data.list[].shiftType: （类型: integer）
-     data.list[].roundType: （类型: integer）
-     data.list[].status: （类型: integer）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询排班计划"
- "查看排班计划列表"
- "有哪些排班计划？"
- "列出排班计划"

## 接口：编辑排班计划

- **路径：** POST /api/mes/work-schedules/update
- **所属模块：** 排班计划
- **HTTP 方法：** POST

**功能说明：**
编辑排班计划

**请求参数：**
- centerId: （类型: integer，必填）
- scheduleCode: （类型: string，可选）
- scheduleName: （类型: string，必填）
- groupId: （类型: integer，可选）
- remark: （类型: string，可选）
- startTime: （类型: string，可选）
- endTime: （类型: string，可选）
- shiftType: （类型: integer，可选）
- roundType: （类型: integer，可选）
- id: 工作计划ID（类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改排班计划信息"
- "更新排班计划数据"
- "编辑排班计划"

## 接口：创建操作工

- **路径：** POST /api/mes/workers/create
- **所属模块：** 操作工
- **HTTP 方法：** POST

**功能说明：**
创建操作工

**请求参数：**
- centerIds: （类型: array<integer>，可选）
- employeeId: （类型: string，可选）
- employeeName: 员工名称（类型: string，必填）
- employeeSex: （类型: integer，可选）
- employeeAge: （类型: integer，可选）
- isNewEmployee: （类型: boolean，可选）
- isMultiSkill: （类型: boolean，可选）
- specialSkill: （类型: string，可选）
- specialRemark: （类型: string，可选）
- enable: 是否启用（类型: boolean，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个操作工"
- "新增操作工记录"
- "创建一个新的操作工"
- "帮我添加操作工"

## 接口：删除操作工

- **路径：** POST /api/mes/workers/delete
- **所属模块：** 操作工
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的操作工记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除操作工"
- "移除某个操作工"

## 接口：获取操作工详情

- **路径：** GET /api/mes/workers/detail
- **所属模块：** 操作工
- **HTTP 方法：** GET

**功能说明：**
根据ID获取操作工中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.centerIds: （类型: array<integer>）
-   data.employeeId: （类型: string）
-   data.employeeName: （类型: string）
-   data.employeeSex: （类型: integer）
-   data.employeeAge: （类型: integer）
-   data.isNewEmployee: （类型: boolean）
-   data.isMultiSkill: （类型: boolean）
-   data.specialSkill: （类型: string）
-   data.specialRemark: （类型: string）
-   data.enable: （类型: boolean）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看操作工的详细信息"
- "操作工详情"
- "操作工的具体情况"

## 接口：使操作工有效/无效

- **路径：** POST /api/mes/workers/enable
- **所属模块：** 操作工
- **HTTP 方法：** POST

**功能说明：**
使操作工有效/无效

**请求参数：**
- id （查询参数）: （类型: integer，必填）
- valid （查询参数）: （类型: boolean，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么使操作工有效/无效？"
- "使操作工有效/无效怎么做？"
- "帮我使操作工有效/无效"

## 接口：导出操作工

- **路径：** POST /api/mes/workers/export
- **所属模块：** 操作工
- **HTTP 方法：** POST

**功能说明：**
导出操作工

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerIds: （类型: array<integer>，可选）
- employeeId: （类型: string，可选）
- employeeName: （类型: string，可选）
- employeeSex: （类型: integer，可选）
- isNewEmployee: （类型: boolean，可选）
- isMultiSkill: （类型: boolean，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么导出操作工？"
- "导出操作工怎么做？"
- "帮我导出操作工"

## 接口：下载操作工导入模板

- **路径：** GET /api/mes/workers/import-template
- **所属模块：** 操作工
- **HTTP 方法：** GET

**功能说明：**
下载操作工导入模板

**请求参数：**
- 无

**返回字段：**
- 无

**适用问题示例：**
- "怎么下载操作工导入模板？"
- "下载操作工导入模板怎么做？"
- "帮我下载操作工导入模板"

## 接口：分页查询操作工

- **路径：** POST /api/mes/workers/page
- **所属模块：** 操作工
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询操作工列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerIds: （类型: array<integer>，可选）
- employeeId: （类型: string，可选）
- employeeName: （类型: string，可选）
- employeeSex: （类型: integer，可选）
- isNewEmployee: （类型: boolean，可选）
- isMultiSkill: （类型: boolean，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].centerIds: （类型: array<integer>）
-     data.list[].employeeId: （类型: string）
-     data.list[].employeeName: （类型: string）
-     data.list[].employeeSex: （类型: integer）
-     data.list[].employeeAge: （类型: integer）
-     data.list[].isNewEmployee: （类型: boolean）
-     data.list[].isMultiSkill: （类型: boolean）
-     data.list[].specialSkill: （类型: string）
-     data.list[].specialRemark: （类型: string）
-     data.list[].enable: （类型: boolean）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询操作工"
- "查看操作工列表"
- "有哪些操作工？"
- "列出操作工"

## 接口：编辑操作工

- **路径：** POST /api/mes/workers/update
- **所属模块：** 操作工
- **HTTP 方法：** POST

**功能说明：**
编辑操作工

**请求参数：**
- centerIds: （类型: array<integer>，可选）
- employeeId: 员工ID（类型: string，必填）
- employeeName: 员工名称（类型: string，必填）
- employeeSex: （类型: integer，可选）
- employeeAge: （类型: integer，可选）
- isNewEmployee: （类型: boolean，可选）
- isMultiSkill: （类型: boolean，可选）
- specialSkill: （类型: string，可选）
- specialRemark: （类型: string，可选）
- enable: 是否启用（类型: boolean，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改操作工信息"
- "更新操作工数据"
- "编辑操作工"

## 接口：获取可用工位列表

- **路径：** GET /api/mes/work-dispatches/available-work-sits
- **所属模块：** 派工单
- **HTTP 方法：** GET

**功能说明：**
根据ID获取派工单中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].sitCode: （类型: string）
-   data[].sitName: （类型: string）
-   data[].centerId: （类型: integer）
-   data[].deviceCode: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询获取可用工位列表"
- "查看获取可用工位列表列表"
- "有哪些获取可用工位列表？"
- "列出获取可用工位列表"

## 接口：批量下发派工单

- **路径：** POST /api/mes/work-dispatches/batch-delivery
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
批量下发派工单

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：完工

- **路径：** POST /api/mes/work-dispatches/complete
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
统计 WorkReport.good 是否达到 predictNum，达到则标记已完成

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：手动创建派工单

- **路径：** POST /api/mes/work-dispatches/create
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
手动创建派工单

**请求参数：**
- centerId: 工作中心ID（类型: integer，必填）
- processOperationId: 工序ID（类型: integer，可选）
- producingWorkOrderId: 生产工单ID（类型: integer，可选）
- productId: 产品ID（类型: integer，可选）
- materialType: （类型: integer，可选）
- workSitId: 工位ID（类型: integer，可选）
- workStartTime: 计划开始时间（类型: string，可选）
- workEndTime: 计划结束时间（类型: string，可选）
- predictNum: 预计生产数量，不传则从生产订单继承（类型: number，可选）
- remark: 备注（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：删除派工单

- **路径：** POST /api/mes/work-dispatches/delete
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的派工单记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除派工单"
- "移除某个派工单"

## 接口：下发派工单

- **路径：** POST /api/mes/work-dispatches/delivery
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
下发派工单

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：获取派工单详情

- **路径：** GET /api/mes/work-dispatches/detail
- **所属模块：** 派工单
- **HTTP 方法：** GET

**功能说明：**
根据ID获取派工单中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.code: （类型: string）
-   data.centerId: （类型: integer）
-   data.workSitId: （类型: integer）
-   data.dispatchStatus: （类型: integer）
-   data.dispatchStatusName: （类型: string）
-   data.predictNum: （类型: number）
-   data.issuingTime: （类型: string）
-   data.workStartTime: （类型: string）
-   data.workEndTime: （类型: string）
-   data.remark: （类型: string）
-   data.salesOrderId: （类型: integer）
-   data.salesOrderCode: （类型: string）
-   data.deliveryDate: （类型: string）
-   data.productionWorkOrderNo: （类型: string）
-   data.productionOrderNo: （类型: string）
-   data.batchNo: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.specModel: （类型: string）
-   data.processCode: （类型: string）
-   data.processRouteId: （类型: integer）
-   data.processRouteCode: （类型: string）
-   data.processRouteName: （类型: string）
-   data.processOperationId: （类型: integer）
-   data.processOperationCode: （类型: string）
-   data.processOperationName: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看派工单的详细信息"
- "派工单详情"
- "派工单的具体情况"

## 接口：派发

- **路径：** POST /api/mes/work-dispatches/dispatch
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
将派工单从已下发改为进行中；batchNo为空时自动生成或继承同工单批次号

**请求参数：**
- workDispatchId （查询参数）: （类型: integer，必填）
- centerId （查询参数）: （类型: integer，必填）
- workSitId （查询参数）: （类型: integer，必填）
- batchNo （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：派工单执行记录分页查询

- **路径：** POST /api/mes/work-dispatches/execution-page
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
以 WorkDispatch 和 WorkDispatchWorker 为基础数据，关联查询派工单执行记录

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- workDispatchCode: （类型: string，可选）
- dispatchStatus: （类型: string，可选）
- producingWorkOrderNo: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- centerId: （类型: integer，可选）
- materialCode: 物料编码（类型: string，可选）
- processOperatorCode: 工序编码（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].workDispatchCode: （类型: string）
-     data.list[].dispatchStatus: （类型: integer）
-     data.list[].dispatchStatusName: （类型: string）
-     data.list[].processCode: （类型: string）
-     data.list[].processOperationCode: （类型: string）
-     data.list[].processOperationName: （类型: string）
-     data.list[].productionWorkOrderNo: （类型: string）
-     data.list[].productionOrderNo: （类型: string）
-     data.list[].productCode: （类型: string）
-     data.list[].productName: （类型: string）
-     data.list[].workSitName: （类型: string）
-     data.list[].deviceName: （类型: string）
-     data.list[].dispatchTime: （类型: string）
-     data.list[].completeTime: （类型: string）
-     data.list[].operatorNames: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：导出派工单执行记录

- **路径：** POST /api/mes/work-dispatches/execution-page/export
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
导出派工单执行记录

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- workDispatchCode: （类型: string，可选）
- dispatchStatus: （类型: string，可选）
- producingWorkOrderNo: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- centerId: （类型: integer，可选）
- materialCode: 物料编码（类型: string，可选）
- processOperatorCode: 工序编码（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：导出派工单

- **路径：** POST /api/mes/work-dispatches/export
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
导出派工单

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- producingOrderCode: （类型: string，可选）
- orderCode: （类型: string，可选）
- productCode: （类型: string，可选）
- processOperationCode: （类型: string，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- workOrderStatus: （类型: string，可选）
- dispatchStatus: （类型: string，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：分页查询派工单

- **路径：** POST /api/mes/work-dispatches/page
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询派工单列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- producingOrderCode: （类型: string，可选）
- orderCode: （类型: string，可选）
- productCode: （类型: string，可选）
- processOperationCode: （类型: string，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- workOrderStatus: （类型: string，可选）
- dispatchStatus: （类型: string，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].centerId: （类型: integer）
-     data.list[].workSitId: （类型: integer）
-     data.list[].code: （类型: string）
-     data.list[].processCode: （类型: string）
-     data.list[].processOperationCode: （类型: string）
-     data.list[].processOperationName: （类型: string）
-     data.list[].producingOrderCode: （类型: string）
-     data.list[].orderCode: （类型: string）
-     data.list[].workOrderCode: （类型: string）
-     data.list[].productCode: （类型: string）
-     data.list[].producedNum: （类型: integer）
-     data.list[].predictNum: （类型: number）
-     data.list[].workOrderStatus: （类型: integer）
-     data.list[].dispatchStatus: （类型: integer）
-     data.list[].dispatchStatusName: （类型: string）
-     data.list[].deliveryTime: （类型: string）
-     data.list[].centerName: （类型: string）
-     data.list[].workSitName: （类型: string）
-     data.list[].shiftName: （类型: string）
-     data.list[].materialName: （类型: string）
-     data.list[].startTime: （类型: string）
-     data.list[].endTime: （类型: string）
-     data.list[].workingDuration: （类型: number）
-     data.list[].deviceName: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询派工单"
- "查看派工单列表"
- "有哪些派工单？"
- "列出派工单"

## 接口：生产看板

- **路径：** POST /api/mes/work-dispatches/producing-screen
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
查询工作中心下的派工单生产进度

**请求参数：**
- centerId: （类型: integer，可选）
- isProducing: （类型: boolean，可选）
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.producingNum: （类型: integer）
-   data.pendingNum: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].centerId: （类型: integer）
-     data.list[].workSitId: （类型: integer）
-     data.list[].code: （类型: string）
-     data.list[].processCode: （类型: string）
-     data.list[].processOperationCode: （类型: string）
-     data.list[].processOperationName: （类型: string）
-     data.list[].producingOrderCode: （类型: string）
-     data.list[].orderCode: （类型: string）
-     data.list[].workOrderCode: （类型: string）
-     data.list[].productCode: （类型: string）
-     data.list[].producedNum: （类型: integer）
-     data.list[].predictNum: （类型: number）
-     data.list[].workOrderStatus: （类型: integer）
-     data.list[].dispatchStatus: （类型: integer）
-     data.list[].dispatchStatusName: （类型: string）
-     data.list[].deliveryTime: （类型: string）
-     data.list[].centerName: （类型: string）
-     data.list[].workSitName: （类型: string）
-     data.list[].shiftName: （类型: string）
-     data.list[].materialName: （类型: string）
-     data.list[].startTime: （类型: string）
-     data.list[].endTime: （类型: string）
-     data.list[].workingDuration: （类型: number）
-     data.list[].deviceName: （类型: string）
-     data.list[].producingWorkOrderId: （类型: integer）
-     data.list[].progress: （类型: number）
-     data.list[].employeeName: （类型: string）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看生产看板"
- "怎么生产看板？"
- "生产看板怎么做？"
- "帮我生产看板"

## 接口：轻量更新派工单

- **路径：** POST /api/mes/work-dispatches/quick-update
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
仅更新工位、计划开始/结束时间，仅待下发状态可修改

**请求参数：**
- id: 派工单ID（类型: integer，可选）
- workSitId: 工位ID（类型: integer，可选）
- workStartTime: 计划开始时间（类型: string，可选）
- workEndTime: 计划结束时间（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：报工

- **路径：** POST /api/mes/work-dispatches/report
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
报工

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：编辑派工单

- **路径：** POST /api/mes/work-dispatches/update
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
编辑派工单

**请求参数：**
- id: （类型: integer，可选）
- status: （类型: integer，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）
- productId: （类型: integer，可选）
- materialType: （类型: integer，可选）
- predictNum: （类型: number，可选）
- producedNum: （类型: number，可选）
- stationId: （类型: integer，可选）
- centerId: （类型: integer，可选）
- processOperationId: （类型: integer，可选）
- producingWorkOrderId: （类型: integer，可选）
- issuingTime: （类型: string，可选）
- workSitId: （类型: integer，可选）
- workStartTime: （类型: string，可选）
- workEndTime: （类型: string，可选）
- dispatchStatus: （类型: integer，可选）
- remark: （类型: string，可选）
- code: （类型: string，可选）
- workerId: （类型: integer，可选）
- batchNo: （类型: string，可选）
- actualStartTime: （类型: string，可选）
- completedTime: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改派工单信息"
- "更新派工单数据"
- "编辑派工单"

## 接口：工人上下班打卡

- **路径：** POST /api/mes/work-dispatches/worker-clock
- **所属模块：** 派工单
- **HTTP 方法：** POST

**功能说明：**
工人上下班打卡

**请求参数：**
- workDispatchId: （类型: integer，可选）
- workerId: （类型: integer，可选）
- workScheduleShiftId: （类型: integer，可选）
- isStartWork: （类型: boolean，可选）
- now: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：添加物料需求明细

- **路径：** POST /api/mes/material-demands/add-material
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
添加物料需求明细

**请求参数：**
- workOrderId: 生产工单ID（类型: integer，必填）
- workDemandId: 物料需求单ID（为空时自动创建）（类型: integer，可选）
- materialId: 物料ID（类型: integer，可选）
- materialType: 物料类型：1-产品，2-原材料（类型: integer，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- quantity: 需求数量（类型: number，可选）
- unit: 单位（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "怎么添加物料需求明细？"
- "添加物料需求明细怎么做？"
- "帮我添加物料需求明细"

## 接口：物料齐套检查

- **路径：** POST /api/mes/material-demands/check-readiness
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
根据系统配置动态计算物料需求并对比 WMS 库存，按物料编码聚合，支持多个工单

**请求参数：**
- workOrderIds: 生产工单ID列表（类型: array<string>，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: 物料齐套检查结果（类型: object）
-   data.ready: 是否齐套（true=全部充足, false=存在缺料）（类型: boolean）
-   data.items: 物料明细列表（类型: array<object>）
-     data.items[].materialCode: 物料编码（类型: string）
-     data.items[].materialName: 物料名称（类型: string）
-     data.items[].demandQuantity: 需求数量（类型: number）
-     data.items[].stockQuantity: WMS 可用库存（类型: number）
-     data.items[].shortageQuantity: 缺料数量（不足时 > 0）（类型: number）
-     data.items[].sufficient: 库存是否充足（类型: boolean）
-     data.items[].unit: 单位（类型: string）
-     data.items[].productSpec: 规格型号（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么物料齐套检查？"
- "物料齐套检查怎么做？"
- "帮我物料齐套检查"

## 接口：消除缺料

- **路径：** POST /api/mes/material-demands/clear-shortage
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
将物料需求单状态设为已消除

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么消除缺料？"
- "消除缺料怎么做？"
- "帮我消除缺料"

## 接口：生成物料需求单

- **路径：** POST /api/mes/material-demands/create
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
根据系统配置（工艺投料信息/产品工艺BOM）自动生成

**请求参数：**
- workOrderId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "怎么生成物料需求单？"
- "生成物料需求单怎么做？"
- "帮我生成物料需求单"

## 接口：生成物料需求单

- **路径：** POST /api/mes/material-demands/create-by-demand
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
手动生成物料单需求

**请求参数：**
- workOrderId: 工作单ID（类型: integer，可选）
- centerId: 工作中心ID（类型: integer，可选）
- items: 需求项（类型: array<object>，可选）
-   items[].materialId: （类型: integer）
-   items[].type: （类型: integer）
-   items[].materialType: （类型: integer）
-   items[].materialCode: （类型: string）
-   items[].materialName: （类型: string）
-   items[].quantity: （类型: number）
-   items[].unit: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "怎么生成物料需求单？"
- "生成物料需求单怎么做？"
- "帮我生成物料需求单"

## 接口：检查WMS库存并创建缺料需求单

- **路径：** POST /api/mes/material-demands/create-shortage-demands
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
根据系统配置动态计算物料需求，对比WMS库存，仅对缺料部分创建物料需求单，支持多个工单

**请求参数：**
- workOrderIds: 生产工单ID列表（类型: array<string>，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<integer>）
- extraData: （类型: object）

**适用问题示例：**
- "查看WMS库存并创建缺料需求单"
- "WMS库存并创建缺料需求单是什么？"

## 接口：删除物料需求明细

- **路径：** POST /api/mes/material-demands/delete-detail
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的物料需求明细记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除物料需求明细"
- "移除某个物料需求明细"

## 接口：根据ID查询物料需求单详情

- **路径：** GET /api/mes/material-demands/detail
- **所属模块：** 物料需求单
- **HTTP 方法：** GET

**功能说明：**
根据ID获取物料需求单中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: 物料需求单详情（类型: object）
-   data.id: 物料需求单ID（类型: integer）
-   data.code: 物料需求单Code（类型: string）
-   data.workOrderId: 工单ID（类型: integer）
-   data.demandStatus: 需求状态：0-待处理，1-已处理，2-缺料（类型: integer）
-   data.productionWorkOrderNo: 生产工单号（类型: string）
-   data.productionOrderNo: 生产订单号（类型: string）
-   data.deliveryDate: 交期（类型: string）
-   data.producingType: 生产方式：1-自产，2-外协（类型: integer）
-   data.standardVersion: BOM版本号（类型: string）
-   data.processBOMVersion: 产品工艺BOM版本（类型: string）
-   data.materialCode: 物料编号（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialType: 物料类型：1-产品，2-原材料（类型: integer）
-   data.specModel: 规格型号（类型: string）
-   data.unitName: 单位名称（类型: string）
-   data.predictNum: 生产数量（类型: number）
-   data.items: 物料需求明细列表（类型: array<object>）
-     data.items[].id: （类型: integer）
-     data.items[].materialId: （类型: integer）
-     data.items[].materialType: （类型: integer）
-     data.items[].materialCode: （类型: string）
-     data.items[].materialName: （类型: string）
-     data.items[].quantity: （类型: number）
-     data.items[].unit: （类型: string）
-     data.items[].unitName: （类型: string）
-     data.items[].specModel: （类型: string）
-     data.items[].productType: （类型: string）
-     data.items[].status: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料需求单详情"
- "查看物料需求单详情列表"
- "有哪些物料需求单详情？"
- "列出物料需求单详情"

## 接口：创建物料需求明细

- **路径：** POST /api/mes/material-demands/detail-items/create
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
用于创建新的物料需求明细记录，录入相关业务信息。

**请求参数：**
- workOrderId: 生产工单ID（类型: integer，必填）
- workDemandId: 物料需求单ID（为空时自动创建）（类型: integer，可选）
- materialId: 物料ID（类型: integer，可选）
- materialType: 物料类型：1-产品，2-原材料（类型: integer，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- quantity: 需求数量（类型: number，可选）
- unit: 单位（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个物料需求明细"
- "新增物料需求明细记录"
- "创建一个新的物料需求明细"
- "帮我添加物料需求明细"

## 接口：删除物料需求明细

- **路径：** POST /api/mes/material-demands/detail-items/delete
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的物料需求明细记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除物料需求明细"
- "移除某个物料需求明细"

## 接口：消除缺料明细

- **路径：** POST /api/mes/material-demands/detail-items/eliminate
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
将物料需求明细状态改为已消除

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么消除缺料明细？"
- "消除缺料明细怎么做？"
- "帮我消除缺料明细"

## 接口：导出物料需求明细

- **路径：** GET /api/mes/material-demands/detail-items/export
- **所属模块：** 物料需求单
- **HTTP 方法：** GET

**功能说明：**
导出物料需求明细

**请求参数：**
- query （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么导出物料需求明细？"
- "导出物料需求明细怎么做？"
- "帮我导出物料需求明细"

## 接口：分页查询物料需求明细

- **路径：** GET /api/mes/material-demands/detail-items/page
- **所属模块：** 物料需求单
- **HTTP 方法：** GET

**功能说明：**
按条件分页查询物料需求明细列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- query （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: MaterialDemand.id（类型: integer）
-     data.list[].workOrderCode: 工单编号（类型: string）
-     data.list[].orderCode: 订单编号（类型: string）
-     data.list[].materialId: 物料ID（类型: integer）
-     data.list[].materialType: 物料类型（类型: integer）
-     data.list[].materialCode: 物料编号（类型: string）
-     data.list[].materialName: 物料名称（类型: string）
-     data.list[].predictNum: 工单数量（类型: number）
-     data.list[].deliveryDate: 交期（类型: string）
-     data.list[].producingType: 生产方式：1-自产，2-外协（类型: integer）
-     data.list[].createTime: 创建时间（类型: string）
-     data.list[].materialDemandId: 缺料ID（类型: integer）
-     data.list[].materialDemandType: 缺料类型（类型: integer）
-     data.list[].materialDemandCode: 缺料编号（类型: string）
-     data.list[].materialDemandName: 缺料名称（类型: string）
-     data.list[].materialDemandSpec: 缺料规格（类型: string）
-     data.list[].unitName: 单位名称（类型: string）
-     data.list[].demandNum: 缺料总需量（类型: number）
-     data.list[].stockNum: 缺料当前库存量（类型: number）
-     data.list[].needNum: 缺料数量（类型: number）
-     data.list[].status: 状态（类型: integer）
-     data.list[].workMaterialDemandId: 缺料单ID（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料需求明细"
- "查看物料需求明细列表"
- "有哪些物料需求明细？"
- "列出物料需求明细"

## 接口：编辑物料需求明细

- **路径：** POST /api/mes/material-demands/detail-items/update
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
用于编辑修改已有的物料需求明细信息，更新相关字段。

**请求参数：**
- id: 物料需求明细ID（类型: integer，可选）
- quantity: 需求数量（类型: number，可选）
- unit: 单位（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改物料需求明细信息"
- "更新物料需求明细数据"
- "编辑物料需求明细"

## 接口：查询物料需求明细

- **路径：** GET /api/mes/material-demands/detail-items/{id}
- **所属模块：** 物料需求单
- **HTTP 方法：** GET

**功能说明：**
查询物料需求明细

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: 物料需求明细分页结果（类型: object）
-   data.id: MaterialDemand.id（类型: integer）
-   data.workOrderCode: 工单编号（类型: string）
-   data.orderCode: 订单编号（类型: string）
-   data.materialId: 物料ID（类型: integer）
-   data.materialType: 物料类型（类型: integer）
-   data.materialCode: 物料编号（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.predictNum: 工单数量（类型: number）
-   data.deliveryDate: 交期（类型: string）
-   data.producingType: 生产方式：1-自产，2-外协（类型: integer）
-   data.createTime: 创建时间（类型: string）
-   data.materialDemandId: 缺料ID（类型: integer）
-   data.materialDemandType: 缺料类型（类型: integer）
-   data.materialDemandCode: 缺料编号（类型: string）
-   data.materialDemandName: 缺料名称（类型: string）
-   data.materialDemandSpec: 缺料规格（类型: string）
-   data.unitName: 单位名称（类型: string）
-   data.demandNum: 缺料总需量（类型: number）
-   data.stockNum: 缺料当前库存量（类型: number）
-   data.needNum: 缺料数量（类型: number）
-   data.status: 状态（类型: integer）
-   data.workMaterialDemandId: 缺料单ID（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料需求明细"
- "查看物料需求明细列表"
- "有哪些物料需求明细？"
- "列出物料需求明细"

## 接口：根据工单ID查询物料需求单详情

- **路径：** GET /api/mes/material-demands/get-by-work-order
- **所属模块：** 物料需求单
- **HTTP 方法：** GET

**功能说明：**
根据ID获取物料需求单中指定记录的完整详情信息。

**请求参数：**
- workOrderId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: 物料需求单详情（类型: object）
-   data.id: 物料需求单ID（类型: integer）
-   data.code: 物料需求单Code（类型: string）
-   data.workOrderId: 工单ID（类型: integer）
-   data.demandStatus: 需求状态：0-待处理，1-已处理，2-缺料（类型: integer）
-   data.productionWorkOrderNo: 生产工单号（类型: string）
-   data.productionOrderNo: 生产订单号（类型: string）
-   data.deliveryDate: 交期（类型: string）
-   data.producingType: 生产方式：1-自产，2-外协（类型: integer）
-   data.standardVersion: BOM版本号（类型: string）
-   data.processBOMVersion: 产品工艺BOM版本（类型: string）
-   data.materialCode: 物料编号（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialType: 物料类型：1-产品，2-原材料（类型: integer）
-   data.specModel: 规格型号（类型: string）
-   data.unitName: 单位名称（类型: string）
-   data.predictNum: 生产数量（类型: number）
-   data.items: 物料需求明细列表（类型: array<object>）
-     data.items[].id: （类型: integer）
-     data.items[].materialId: （类型: integer）
-     data.items[].materialType: （类型: integer）
-     data.items[].materialCode: （类型: string）
-     data.items[].materialName: （类型: string）
-     data.items[].quantity: （类型: number）
-     data.items[].unit: （类型: string）
-     data.items[].unitName: （类型: string）
-     data.items[].specModel: （类型: string）
-     data.items[].productType: （类型: string）
-     data.items[].status: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料需求单详情"
- "查看物料需求单详情列表"
- "有哪些物料需求单详情？"
- "列出物料需求单详情"

## 接口：出库申请

- **路径：** POST /api/mes/material-demands/outbound/create
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
向 WMS 提交物料出库申请，按物料类型分流向成品出库和物料出库

**请求参数：**
- id: （类型: integer，可选）
- applicationNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- materials: （类型: array<object>，可选）
-   materials[].id: （类型: integer）
-   materials[].quantity: （类型: number）
-   materials[].materialType: （类型: integer）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.productResult: （类型: object）
-     data.productResult.id: （类型: integer）
-     data.productResult.applicationNo: （类型: string）
-     data.productResult.success: （类型: boolean）
-     data.productResult.message: （类型: string）
-   data.materialResult: （类型: object）
-     data.materialResult.id: （类型: integer）
-     data.materialResult.applicationNo: （类型: string）
-     data.materialResult.success: （类型: boolean）
-     data.materialResult.message: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么出库申请？"
- "出库申请怎么做？"
- "帮我出库申请"

## 接口：分页查询物料需求单

- **路径：** GET /api/mes/material-demands/page
- **所属模块：** 物料需求单
- **HTTP 方法：** GET

**功能说明：**
按条件分页查询物料需求单列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- query （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: ID（类型: integer）
-     data.list[].code: 物料需求单号（类型: string）
-     data.list[].workOrderId: 工单ID（类型: integer）
-     data.list[].demandStatus: 需求状态：0-待处理，1-已处理，2-缺料（类型: integer）
-     data.list[].productionWorkOrderNo: 生产工单号（类型: string）
-     data.list[].productionOrderNo: 生产订单号（类型: string）
-     data.list[].materialCode: 物料编码（类型: string）
-     data.list[].materialName: 物料名称（类型: string）
-     data.list[].specModel: 规格型号（类型: string）
-     data.list[].unitName: 单位名称（类型: string）
-     data.list[].predictNum: 工单数量（类型: number）
-     data.list[].deliveryDate: 交期（类型: string）
-     data.list[].producingType: 生产方式：1-自产，2-外协（类型: integer）
-     data.list[].createTime: 创建时间（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料需求单"
- "查看物料需求单列表"
- "有哪些物料需求单？"
- "列出物料需求单"

## 接口：采购申请

- **路径：** POST /api/mes/material-demands/procurement/apply
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
将物料需求单状态设为采购申请

**请求参数：**
- materialDemandIds: 物料需求ID列表（类型: array<string>，可选）
- materialDemandItems: （类型: array<object>，可选）
-   materialDemandItems[].materialId: （类型: integer）
-   materialDemandItems[].type: （类型: integer）
-   materialDemandItems[].materialType: （类型: integer）
-   materialDemandItems[].materialCode: （类型: string）
-   materialDemandItems[].materialName: （类型: string）
-   materialDemandItems[].quantity: （类型: number）
-   materialDemandItems[].unit: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.requestId: （类型: integer）
-   data.requestNo: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么采购申请？"
- "采购申请怎么做？"
- "帮我采购申请"

## 接口：缺料汇总统计

- **路径：** POST /api/mes/material-demands/shortage-summary
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
按物料编码聚合多个物料需求单的缺料数量

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: 主键ID（类型: integer）
-   data[].materialType: 物料类型（类型: integer）
-   data[].materialCode: 物料编码（类型: string）
-   data[].materialName: 物料名称（类型: string）
-   data[].specModel: 规格型号（类型: string）
-   data[].unit: 单位（类型: string）
-   data[].totalQuantity: 总缺料数量（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看缺料汇总统计"
- "需求情况怎么样？"
- "有多少需求？"
- "需求转化如何？"
- "当前汇总数据"
- "最新的汇总统计"

## 接口：编辑物料需求单（全量替换明细）

- **路径：** POST /api/mes/material-demands/update
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
用于编辑修改已有的物料需求单（全量替换明细）信息，更新相关字段。

**请求参数：**
- workOrderId: 工作单ID（类型: integer，可选）
- centerId: 工作中心ID（类型: integer，可选）
- items: 需求项（类型: array<object>，可选）
-   items[].materialId: （类型: integer）
-   items[].type: （类型: integer）
-   items[].materialType: （类型: integer）
-   items[].materialCode: （类型: string）
-   items[].materialName: （类型: string）
-   items[].quantity: （类型: number）
-   items[].unit: （类型: string）
- id: 物料需求单ID（类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改物料需求单（全量替换明细）信息"
- "更新物料需求单（全量替换明细）数据"
- "编辑物料需求单（全量替换明细）"

## 接口：编辑物料需求单

- **路径：** POST /api/mes/material-demands/update-work-demand
- **所属模块：** 物料需求单
- **HTTP 方法：** POST

**功能说明：**
用于编辑修改已有的物料需求单信息，更新相关字段。

**请求参数：**
- id: 物料需求单ID（类型: integer，必填）
- demandStatus: 需求状态：0-待处理，1-已处理，2-缺料（类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改物料需求单信息"
- "更新物料需求单数据"
- "编辑物料需求单"

## 接口：创建班次设置

- **路径：** POST /api/mes/work-shift-settings/create
- **所属模块：** 班次设置
- **HTTP 方法：** POST

**功能说明：**
创建班次设置

**请求参数：**
- centerId: （类型: integer，可选）
- generateType: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个班次设置"
- "新增班次设置记录"
- "创建一个新的班次设置"
- "帮我添加班次设置"

## 接口：删除班次设置

- **路径：** POST /api/mes/work-shift-settings/delete
- **所属模块：** 班次设置
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的班次设置记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除班次设置"
- "移除某个班次设置"

## 接口：获取班次设置详情

- **路径：** GET /api/mes/work-shift-settings/detail
- **所属模块：** 班次设置
- **HTTP 方法：** GET

**功能说明：**
根据ID获取班次设置中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.centerId: （类型: integer）
-   data.generateType: （类型: integer）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看班次设置的详细信息"
- "班次设置详情"
- "班次设置的具体情况"

## 接口：分页查询班次设置

- **路径：** POST /api/mes/work-shift-settings/page
- **所属模块：** 班次设置
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询班次设置列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- generateType: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].centerId: （类型: integer）
-     data.list[].generateType: （类型: integer）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询班次设置"
- "查看班次设置列表"
- "有哪些班次设置？"
- "列出班次设置"

## 接口：编辑班次设置

- **路径：** POST /api/mes/work-shift-settings/update
- **所属模块：** 班次设置
- **HTTP 方法：** POST

**功能说明：**
编辑班次设置

**请求参数：**
- centerId: （类型: integer，可选）
- generateType: （类型: integer，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改班次设置信息"
- "更新班次设置数据"
- "编辑班次设置"

## 接口：创建班组

- **路径：** POST /api/mes/work-groups/create
- **所属模块：** 班组
- **HTTP 方法：** POST

**功能说明：**
创建班组

**请求参数：**
- centerId: （类型: integer，必填）
- groupCode: 班组编码，不传则由系统自动生成（类型: string，可选）
- groupName: 班组名称（类型: string，必填）
- remark: （类型: string，可选）
- leaderId: 班组长（类型: string，必填）
- memberIds: （类型: array<string>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个班组"
- "新增班组记录"
- "创建一个新的班组"
- "帮我添加班组"

## 接口：删除班组

- **路径：** POST /api/mes/work-groups/delete
- **所属模块：** 班组
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的班组记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除班组"
- "移除某个班组"

## 接口：获取班组详情

- **路径：** GET /api/mes/work-groups/detail
- **所属模块：** 班组
- **HTTP 方法：** GET

**功能说明：**
根据ID获取班组中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.centerId: （类型: integer）
-   data.groupCode: （类型: string）
-   data.groupName: （类型: string）
-   data.remark: （类型: string）
-   data.leaderId: （类型: integer）
-   data.leaderName: （类型: string）
-   data.workers: （类型: array<object>）
-     data.workers[].id: （类型: integer）
-     data.workers[].status: （类型: integer）
-     data.workers[].createBy: （类型: string）
-     data.workers[].createTime: （类型: string）
-     data.workers[].updateBy: （类型: string）
-     data.workers[].updateTime: （类型: string）
-     data.workers[].isValid: （类型: integer）
-     data.workers[].centerIds: （类型: array<integer>）
-     data.workers[].employeeId: （类型: string）
-     data.workers[].employeeName: （类型: string）
-     data.workers[].employeeSex: （类型: integer）
-     data.workers[].employeeAge: （类型: integer）
-     data.workers[].isNewEmployee: （类型: boolean）
-     data.workers[].isMultiSkill: （类型: boolean）
-     data.workers[].specialSkill: （类型: string）
-     data.workers[].specialRemark: （类型: string）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看班组的详细信息"
- "班组详情"
- "班组的具体情况"

## 接口：分页查询班组

- **路径：** POST /api/mes/work-groups/page
- **所属模块：** 班组
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询班组列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- groupCode: （类型: string，可选）
- groupName: （类型: string，可选）
- leaderId: （类型: integer，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].centerId: （类型: integer）
-     data.list[].groupCode: （类型: string）
-     data.list[].groupName: （类型: string）
-     data.list[].remark: （类型: string）
-     data.list[].leaderId: （类型: integer）
-     data.list[].leaderName: （类型: string）
-     data.list[].workers: （类型: array<object>）
-       data.list[].workers[].id: （类型: integer）
-       data.list[].workers[].status: （类型: integer）
-       data.list[].workers[].createBy: （类型: string）
-       data.list[].workers[].createTime: （类型: string）
-       data.list[].workers[].updateBy: （类型: string）
-       data.list[].workers[].updateTime: （类型: string）
-       data.list[].workers[].isValid: （类型: integer）
-       data.list[].workers[].centerIds: （类型: array<integer>）
-       data.list[].workers[].employeeId: （类型: string）
-       data.list[].workers[].employeeName: （类型: string）
-       data.list[].workers[].employeeSex: （类型: integer）
-       data.list[].workers[].employeeAge: （类型: integer）
-       data.list[].workers[].isNewEmployee: （类型: boolean）
-       data.list[].workers[].isMultiSkill: （类型: boolean）
-       data.list[].workers[].specialSkill: （类型: string）
-       data.list[].workers[].specialRemark: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询班组"
- "查看班组列表"
- "有哪些班组？"
- "列出班组"

## 接口：编辑班组

- **路径：** POST /api/mes/work-groups/update
- **所属模块：** 班组
- **HTTP 方法：** POST

**功能说明：**
编辑班组

**请求参数：**
- centerId: （类型: integer，必填）
- groupCode: 班组编码，不传则由系统自动生成（类型: string，可选）
- groupName: 班组名称（类型: string，必填）
- remark: （类型: string，可选）
- leaderId: 班组长（类型: string，必填）
- memberIds: （类型: array<string>，可选）
- id: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改班组信息"
- "更新班组数据"
- "编辑班组"

## 接口：分页查询生产工单

- **路径：** GET /api/mes/producing-work-orders
- **所属模块：** 生产工单
- **HTTP 方法：** GET

**功能说明：**
按条件分页查询生产工单列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- query （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].code: （类型: string）
-     data.list[].salesOrderId: （类型: integer）
-     data.list[].producingOrderId: （类型: integer）
-     data.list[].productId: （类型: integer）
-     data.list[].materialType: （类型: integer）
-     data.list[].productName: （类型: string）
-     data.list[].materialCode: （类型: string）
-     data.list[].productNum: （类型: number）
-     data.list[].manager: （类型: string）
-     data.list[].managerName: （类型: string）
-     data.list[].startDate: （类型: string）
-     data.list[].endDate: （类型: string）
-     data.list[].deliveryDate: （类型: string）
-     data.list[].issuingTime: （类型: string）
-     data.list[].duration: （类型: integer）
-     data.list[].type: （类型: integer）
-     data.list[].priority: （类型: integer）
-     data.list[].processRouteId: （类型: integer）
-     data.list[].processRouteName: （类型: string）
-     data.list[].processCode: （类型: string）
-     data.list[].standardVersion: （类型: string）
-     data.list[].processBOMId: （类型: integer）
-     data.list[].bomSnapshot: （类型: integer）
-     data.list[].workStatus: （类型: integer）
-     data.list[].workStatusName: （类型: string）
-     data.list[].producingType: （类型: integer）
-     data.list[].producingTypeName: （类型: string）
-     data.list[].workOrderCode: （类型: string）
-     data.list[].producingOrderCode: （类型: string）
-     data.list[].typeName: （类型: string）
-     data.list[].priorityName: （类型: string）
-     data.list[].remark: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询生产工单"
- "查看生产工单列表"
- "有哪些生产工单？"
- "列出生产工单"

## 接口：编辑生产工单

- **路径：** PUT /api/mes/producing-work-orders
- **所属模块：** 生产工单
- **HTTP 方法：** PUT

**功能说明：**
编辑生产工单

**请求参数：**
- id: （类型: integer，可选）
- status: （类型: integer，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）
- productId: （类型: integer，可选）
- materialType: （类型: integer，可选）
- predictNum: （类型: number，可选）
- producedNum: （类型: number，可选）
- salesOrderId: （类型: integer，可选）
- producingOrderId: （类型: integer，可选）
- productionOrderNo: （类型: string，可选）
- productionWorkOrderNo: （类型: string，可选）
- code: （类型: string，可选）
- materialCode: （类型: string，可选）
- priority: （类型: integer，可选）
- manager: （类型: string，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- deliveryDate: （类型: string，可选）
- issuingTime: （类型: string，可选）
- duration: （类型: integer，可选）
- type: （类型: integer，可选）
- scheduleId: （类型: integer，可选）
- processRouteId: （类型: integer，可选）
- processCode: （类型: string，可选）
- standardVersion: （类型: string，可选）
- processBOMId: （类型: integer，可选）
- processBOMVersion: （类型: string，可选）
- workStatus: （类型: integer，可选）
- actualEndTime: （类型: string，可选）
- producingType: （类型: integer，可选）
- remark: （类型: string，可选）
- workStatusEnum: （类型: string，可选）
- producingTypeEnum: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改生产工单信息"
- "更新生产工单数据"
- "编辑生产工单"

## 接口：创建生产工单

- **路径：** POST /api/mes/producing-work-orders
- **所属模块：** 生产工单
- **HTTP 方法：** POST

**功能说明：**
创建生产工单

**请求参数：**
- id: （类型: integer，可选）
- salesOrderId: （类型: integer，可选）
- producingOrderId: （类型: integer，可选）
- productId: （类型: integer，可选）
- materialType: （类型: integer，可选）
- materialCode: （类型: string，可选）
- routeCode: （类型: string，可选）
- manager: （类型: string，可选）
- processRouteId: （类型: integer，可选）
- scheduleId: （类型: integer，可选）
- predictNum: （类型: number，可选）
- deliveryDate: （类型: string，可选）
- type: （类型: integer，可选）
- priority: （类型: integer，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- duration: （类型: integer，可选）
- workStatus: （类型: integer，可选）
- producingType: （类型: integer，可选）
- remark: （类型: string，可选）
- standardVersion: （类型: string，可选）
- processBOMId: （类型: integer，可选）
- bomSnapshot: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个生产工单"
- "新增生产工单记录"
- "创建一个新的生产工单"
- "帮我添加生产工单"

## 接口：删除生产工单

- **路径：** DELETE /api/mes/producing-work-orders
- **所属模块：** 生产工单
- **HTTP 方法：** DELETE

**功能说明：**
用于删除指定的生产工单记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除生产工单"
- "移除某个生产工单"

## 接口：批量下发生产工单

- **路径：** POST /api/mes/producing-work-orders/batch-delivery
- **所属模块：** 生产工单
- **HTTP 方法：** POST

**功能说明：**
按工单ID列表批量排程并下发派工单

**请求参数：**
- ids: 派工单ID列表（类型: array<integer>，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：根据生产工单号查询生产工单详情

- **路径：** GET /api/mes/producing-work-orders/by-no
- **所属模块：** 生产工单
- **HTTP 方法：** GET

**功能说明：**
根据ID获取生产工单中指定记录的完整详情信息。

**请求参数：**
- productionWorkOrderNo （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.productionOrderNo: （类型: string）
-   data.productionWorkOrderNo: （类型: string）
-   data.code: （类型: string）
-   data.managerName: （类型: string）
-   data.processOperations: （类型: array<object>）
-     data.processOperations[].id: （类型: integer）
-     data.processOperations[].standardId: （类型: string）
-     data.processOperations[].sort: （类型: string）
-     data.processOperations[].standardName: （类型: string）
-     data.processOperations[].standardVersion: （类型: string）
-     data.processOperations[].standardType: （类型: integer）
-     data.processOperations[].standardCode: （类型: string）
-     data.processOperations[].routeId: （类型: integer）
-     data.processOperations[].sequence: （类型: integer）
-     data.processOperations[].workCenterId: （类型: integer）
-     data.processOperations[].operationType: （类型: string）
-     data.processOperations[].standardTime: （类型: number）
-     data.processOperations[].workContent: （类型: string）
-     data.processOperations[].beat: （类型: number）
-     data.processOperations[].deviceType: （类型: string）
-     data.processOperations[].isSelfProduced: （类型: boolean）
-     data.processOperations[].price: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询生产工单详情"
- "查看生产工单详情列表"
- "有哪些生产工单详情？"
- "列出生产工单详情"

## 接口：完工生产工单

- **路径：** POST /api/mes/producing-work-orders/complete
- **所属模块：** 生产工单
- **HTTP 方法：** POST

**功能说明：**
完工生产工单

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：交付生产工单

- **路径：** POST /api/mes/producing-work-orders/delivery
- **所属模块：** 生产工单
- **HTTP 方法：** POST

**功能说明：**
可传入batchNo指定批次号，为空则自动生成

**请求参数：**
- id （查询参数）: （类型: integer，必填）
- batchNo （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：获取生产工单详情

- **路径：** GET /api/mes/producing-work-orders/detail
- **所属模块：** 生产工单
- **HTTP 方法：** GET

**功能说明：**
根据ID获取生产工单中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.status: （类型: integer）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.productId: （类型: integer）
-   data.materialType: （类型: integer）
-   data.predictNum: （类型: number）
-   data.producedNum: （类型: number）
-   data.salesOrderId: （类型: integer）
-   data.producingOrderId: （类型: integer）
-   data.productionOrderNo: （类型: string）
-   data.productionWorkOrderNo: （类型: string）
-   data.code: （类型: string）
-   data.materialCode: （类型: string）
-   data.priority: （类型: integer）
-   data.manager: （类型: string）
-   data.startDate: （类型: string）
-   data.endDate: （类型: string）
-   data.deliveryDate: （类型: string）
-   data.issuingTime: （类型: string）
-   data.duration: （类型: integer）
-   data.type: （类型: integer）
-   data.scheduleId: （类型: integer）
-   data.processRouteId: （类型: integer）
-   data.processCode: （类型: string）
-   data.standardVersion: （类型: string）
-   data.processBOMId: （类型: integer）
-   data.processBOMVersion: （类型: string）
-   data.workStatus: （类型: integer）
-   data.actualEndTime: （类型: string）
-   data.producingType: （类型: integer）
-   data.remark: （类型: string）
-   data.managerName: （类型: string）
-   data.productName: （类型: string）
-   data.productCode: （类型: string）
-   data.productSpec: （类型: string）
-   data.specModel: （类型: string）
-   data.productType: （类型: string）
-   data.unitName: （类型: string）
-   data.workStatusEnum: （类型: string）
-   data.producingTypeEnum: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看生产工单的详细信息"
- "生产工单详情"
- "生产工单的具体情况"

## 接口：导出生产工单

- **路径：** GET /api/mes/producing-work-orders/export
- **所属模块：** 生产工单
- **HTTP 方法：** GET

**功能说明：**
导出生产工单

**请求参数：**
- query （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：查询工单下所有批次号

- **路径：** GET /api/mes/producing-work-orders/{workOrderId}/batch-numbers
- **所属模块：** 生产工单
- **HTTP 方法：** GET

**功能说明：**
查询工单下所有批次号

**请求参数：**
- workOrderId （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<string>）
- extraData: （类型: object）

**适用问题示例：**
- "查询工单下所有批次号"
- "查看工单下所有批次号列表"
- "有哪些工单下所有批次号？"
- "列出工单下所有批次号"

## 接口：自动排程：按 P1 分层 + P3 空闲窗口前向贪心，写 WorkDispatch + DeviceTimeline

- **路径：** POST /api/mes/producing-work-order/auto-schedule
- **所属模块：** 生产工单排程
- **HTTP 方法：** POST

**功能说明：**
自动排程：按 P1 分层 + P3 空闲窗口前向贪心，写 WorkDispatch + DeviceTimeline

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.success: （类型: boolean）
-   data.errorMessage: （类型: string）
-   data.code: （类型: string）
-   data.producingWorkOrderId: （类型: integer）
-   data.estimatedDurationHours: （类型: integer）
-   data.plannedStart: （类型: string）
-   data.plannedEnd: （类型: string）
-   data.dispatches: （类型: array<object>）
-     data.dispatches[].id: （类型: integer）
-     data.dispatches[].status: （类型: integer）
-     data.dispatches[].createBy: （类型: string）
-     data.dispatches[].createTime: （类型: string）
-     data.dispatches[].updateBy: （类型: string）
-     data.dispatches[].updateTime: （类型: string）
-     data.dispatches[].isValid: （类型: integer）
-     data.dispatches[].productId: （类型: integer）
-     data.dispatches[].materialType: （类型: integer）
-     data.dispatches[].predictNum: （类型: number）
-     data.dispatches[].producedNum: （类型: number）
-     data.dispatches[].stationId: （类型: integer）
-     data.dispatches[].centerId: （类型: integer）
-     data.dispatches[].processOperationId: （类型: integer）
-     data.dispatches[].producingWorkOrderId: （类型: integer）
-     data.dispatches[].issuingTime: （类型: string）
-     data.dispatches[].workSitId: （类型: integer）
-     data.dispatches[].workStartTime: （类型: string）
-     data.dispatches[].workEndTime: （类型: string）
-     data.dispatches[].dispatchStatus: （类型: integer）
-     data.dispatches[].remark: （类型: string）
-     data.dispatches[].code: （类型: string）
-     data.dispatches[].workerId: （类型: integer）
-     data.dispatches[].batchNo: （类型: string）
-     data.dispatches[].actualStartTime: （类型: string）
-     data.dispatches[].completedTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：批量自动排程

- **路径：** POST /api/mes/producing-work-order/batch-auto-schedule
- **所属模块：** 生产工单排程
- **HTTP 方法：** POST

**功能说明：**
批量自动排程

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].success: （类型: boolean）
-   data[].errorMessage: （类型: string）
-   data[].code: （类型: string）
-   data[].producingWorkOrderId: （类型: integer）
-   data[].estimatedDurationHours: （类型: integer）
-   data[].plannedStart: （类型: string）
-   data[].plannedEnd: （类型: string）
-   data[].dispatches: （类型: array<object>）
-     data[].dispatches[].id: （类型: integer）
-     data[].dispatches[].status: （类型: integer）
-     data[].dispatches[].createBy: （类型: string）
-     data[].dispatches[].createTime: （类型: string）
-     data[].dispatches[].updateBy: （类型: string）
-     data[].dispatches[].updateTime: （类型: string）
-     data[].dispatches[].isValid: （类型: integer）
-     data[].dispatches[].productId: （类型: integer）
-     data[].dispatches[].materialType: （类型: integer）
-     data[].dispatches[].predictNum: （类型: number）
-     data[].dispatches[].producedNum: （类型: number）
-     data[].dispatches[].stationId: （类型: integer）
-     data[].dispatches[].centerId: （类型: integer）
-     data[].dispatches[].processOperationId: （类型: integer）
-     data[].dispatches[].producingWorkOrderId: （类型: integer）
-     data[].dispatches[].issuingTime: （类型: string）
-     data[].dispatches[].workSitId: （类型: integer）
-     data[].dispatches[].workStartTime: （类型: string）
-     data[].dispatches[].workEndTime: （类型: string）
-     data[].dispatches[].dispatchStatus: （类型: integer）
-     data[].dispatches[].remark: （类型: string）
-     data[].dispatches[].code: （类型: string）
-     data[].dispatches[].workerId: （类型: integer）
-     data[].dispatches[].batchNo: （类型: string）
-     data[].dispatches[].actualStartTime: （类型: string）
-     data[].dispatches[].completedTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：查询所有工位空闲窗口（Gantt 图用）

- **路径：** GET /api/mes/producing-work-order/gantt-idle
- **所属模块：** 生产工单排程
- **HTTP 方法：** GET

**功能说明：**
查询所有工位空闲窗口（Gantt 图用）

**请求参数：**
- centerId （查询参数）: （类型: integer，可选）
- deviceType （查询参数）: （类型: string，可选）
- from （查询参数）: （类型: string，可选）
- to （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].workSitId: （类型: integer）
-   data[].sitName: （类型: string）
-   data[].deviceId: （类型: integer）
-   data[].startTime: （类型: string）
-   data[].endTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询所有工位空闲窗口（Gantt 图用）"
- "查看所有工位空闲窗口（Gantt 图用）列表"
- "有哪些所有工位空闲窗口（Gantt 图用）？"
- "列出所有工位空闲窗口（Gantt 图用）"

## 接口：仅做物料齐套检查，不触发排程

- **路径：** GET /api/mes/producing-work-order/material-check
- **所属模块：** 生产工单排程
- **HTTP 方法：** GET

**功能说明：**
仅做物料齐套检查，不触发排程

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.ready: （类型: boolean）
-   data.shortages: （类型: array<object>）
-     data.shortages[].materialCode: （类型: string）
-     data.shortages[].materialName: （类型: string）
-     data.shortages[].requiredQuantity: （类型: number）
-     data.shortages[].unit: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：重排：删除未开工的派工 + 时序后重新 autoSchedule

- **路径：** POST /api/mes/producing-work-order/reschedule
- **所属模块：** 生产工单排程
- **HTTP 方法：** POST

**功能说明：**
重排：删除未开工的派工 + 时序后重新 autoSchedule

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.success: （类型: boolean）
-   data.errorMessage: （类型: string）
-   data.code: （类型: string）
-   data.producingWorkOrderId: （类型: integer）
-   data.estimatedDurationHours: （类型: integer）
-   data.plannedStart: （类型: string）
-   data.plannedEnd: （类型: string）
-   data.dispatches: （类型: array<object>）
-     data.dispatches[].id: （类型: integer）
-     data.dispatches[].status: （类型: integer）
-     data.dispatches[].createBy: （类型: string）
-     data.dispatches[].createTime: （类型: string）
-     data.dispatches[].updateBy: （类型: string）
-     data.dispatches[].updateTime: （类型: string）
-     data.dispatches[].isValid: （类型: integer）
-     data.dispatches[].productId: （类型: integer）
-     data.dispatches[].materialType: （类型: integer）
-     data.dispatches[].predictNum: （类型: number）
-     data.dispatches[].producedNum: （类型: number）
-     data.dispatches[].stationId: （类型: integer）
-     data.dispatches[].centerId: （类型: integer）
-     data.dispatches[].processOperationId: （类型: integer）
-     data.dispatches[].producingWorkOrderId: （类型: integer）
-     data.dispatches[].issuingTime: （类型: string）
-     data.dispatches[].workSitId: （类型: integer）
-     data.dispatches[].workStartTime: （类型: string）
-     data.dispatches[].workEndTime: （类型: string）
-     data.dispatches[].dispatchStatus: （类型: integer）
-     data.dispatches[].remark: （类型: string）
-     data.dispatches[].code: （类型: string）
-     data.dispatches[].workerId: （类型: integer）
-     data.dispatches[].batchNo: （类型: string）
-     data.dispatches[].actualStartTime: （类型: string）
-     data.dispatches[].completedTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：发送生产工单状态机事件

- **路径：** POST /api/mes/orders/{orderId}/work-orders/{workOrderId}/events
- **所属模块：** 生产工单状态机
- **HTTP 方法：** POST

**功能说明：**
发送生产工单状态机事件

**请求参数：**
- orderId （路径参数）: （类型: integer，必填）
- workOrderId （路径参数）: （类型: integer，必填）
- event: （类型: string，必填）
- headers: （类型: object，可选）
- goodQty: （类型: number，可选）
- badQty: （类型: number，可选）
- totalQty: （类型: number，可选）

**返回字段：**
- 无

**适用问题示例：**
- "查看问题工单"
- "工单处理情况"
- "有哪些问题工单？"

## 接口：查询工单下所有批次

- **路径：** GET /api/mes/producing-batches/list
- **所属模块：** 生产批次
- **HTTP 方法：** GET

**功能说明：**
查询工单下所有批次

**请求参数：**
- workOrderId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].status: （类型: integer）
-   data[].createBy: （类型: string）
-   data[].createTime: （类型: string）
-   data[].updateBy: （类型: string）
-   data[].updateTime: （类型: string）
-   data[].isValid: （类型: integer）
-   data[].batchNo: （类型: string）
-   data[].producingWorkOrderId: （类型: integer）
-   data[].salesOrderId: （类型: integer）
-   data[].producingOrderId: （类型: integer）
-   data[].productionOrderNo: （类型: string）
-   data[].productionWorkOrderNo: （类型: string）
-   data[].materialCode: （类型: string）
-   data[].productId: （类型: integer）
-   data[].materialType: （类型: integer）
-   data[].predictNum: （类型: number）
-   data[].producedNum: （类型: number）
-   data[].priority: （类型: integer）
-   data[].manager: （类型: string）
-   data[].deliveryDate: （类型: string）
-   data[].issuingTime: （类型: string）
-   data[].processRouteId: （类型: integer）
-   data[].processCode: （类型: string）
-   data[].processBOMId: （类型: integer）
-   data[].processBOMVersion: （类型: string）
-   data[].workStatus: （类型: integer）
-   data[].producingType: （类型: integer）
-   data[].remark: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工单下所有批次"
- "查看工单下所有批次列表"
- "有哪些工单下所有批次？"
- "列出工单下所有批次"

## 接口：手动创建新批次

- **路径：** POST /api/mes/producing-batches/new-batch
- **所属模块：** 生产批次
- **HTTP 方法：** POST

**功能说明：**
为派工单创建新批次，传入batchNo时使用指定值，否则自动生成

**请求参数：**
- workDispatchId （查询参数）: （类型: integer，必填）
- batchNo （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么手动创建新批次？"
- "手动创建新批次怎么做？"
- "帮我手动创建新批次"

## 接口：根据派工单查询工单下所有批次

- **路径：** GET /api/mes/producing-batches/simple-list
- **所属模块：** 生产批次
- **HTTP 方法：** GET

**功能说明：**
根据派工单查询工单下所有批次

**请求参数：**
- workDispatchId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].batchNo: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询工单下所有批次"
- "查看工单下所有批次列表"
- "有哪些工单下所有批次？"
- "列出工单下所有批次"

## 接口：切换批次

- **路径：** POST /api/mes/producing-batches/switch-batch
- **所属模块：** 生产批次
- **HTTP 方法：** POST

**功能说明：**
将派工单切换到指定批次，并记录快照

**请求参数：**
- workDispatchId （查询参数）: （类型: integer，必填）
- batchId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么切换批次？"
- "切换批次怎么做？"
- "帮我切换批次"

## 接口：导出工序汇总统计

- **路径：** POST /api/mes/work-report-operation-statistics/export
- **所属模块：** 生产报工-工序汇总统计
- **HTTP 方法：** POST

**功能说明：**
导出工序汇总统计

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- productionWorkOrderNo: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- materialCode: （类型: string，可选）
- processCode: （类型: string，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- dispatchStatus: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看导出工序汇总统计"
- "当前汇总数据"
- "最新的汇总统计"

## 接口：分页查询工序汇总统计

- **路径：** POST /api/mes/work-report-operation-statistics/page
- **所属模块：** 生产报工-工序汇总统计
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询工序汇总统计列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- productionWorkOrderNo: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- materialCode: （类型: string，可选）
- processCode: （类型: string，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- dispatchStatus: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].dispatchCode: 派工单号（类型: string）
-     data.list[].dispatchStatus: 派工单状态（类型: integer）
-     data.list[].dispatchStatusName: 派工单状态名称（类型: string）
-     data.list[].processRouteCode: 工艺编号（类型: string）
-     data.list[].processCode: 工序编号（类型: string）
-     data.list[].processName: 工序名称（类型: string）
-     data.list[].productionWorkOrderNo: 生产工单号（类型: string）
-     data.list[].productionOrderNo: 生产订单号（类型: string）
-     data.list[].productId: 产品ID（MDM查询用）（类型: integer）
-     data.list[].materialType: 物料类型（类型: integer）
-     data.list[].materialCode: 物料编码（类型: string）
-     data.list[].materialName: 物料名称（类型: string）
-     data.list[].predictNum: 计划生产数量（类型: number）
-     data.list[].workstation: 工位（类型: string）
-     data.list[].equipment: 设备（类型: string）
-     data.list[].actualStartTime: 实际开始时间（类型: string）
-     data.list[].actualEndTime: 实际完工时间（类型: string）
-     data.list[].cumulativeWorkHours: 累计工时(h)（类型: number）
-     data.list[].cumulativeCompleted: 累计完成数量（类型: number）
-     data.list[].cumulativeGood: 累计良品数（类型: number）
-     data.list[].cumulativeBad: 累计不良品数（类型: number）
-     data.list[].actualCycleTime: 实际节拍(分)（类型: number）
-     data.list[].productionProgress: 生产进度%（类型: number）
-     data.list[].yieldRate: 总良率%（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询工序汇总统计"
- "查看工序汇总统计列表"
- "有哪些工序汇总统计？"
- "列出工序汇总统计"

## 接口：导出操作工统计

- **路径：** POST /api/mes/work-report-operator-statistics/export
- **所属模块：** 生产报工-操作工统计
- **HTTP 方法：** POST

**功能说明：**
导出操作工统计

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- operatorName: （类型: string，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- materialCode: （类型: string，可选）
- processCode: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看导出操作工统计"
- "怎么导出操作工统计？"
- "导出操作工统计怎么做？"
- "帮我导出操作工统计"

## 接口：分页查询操作工统计

- **路径：** POST /api/mes/work-report-operator-statistics/page
- **所属模块：** 生产报工-操作工统计
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询操作工统计列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- centerId: （类型: integer，可选）
- operatorName: （类型: string，可选）
- startDate: （类型: string，可选）
- endDate: （类型: string，可选）
- materialCode: （类型: string，可选）
- processCode: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].operatorName: 操作工（类型: string）
-     data.list[].earliestStart: 开始时间（类型: string）
-     data.list[].latestEnd: 结束时间（类型: string）
-     data.list[].processCode: 工序编号（类型: string）
-     data.list[].processName: 工序名称（类型: string）
-     data.list[].materialCode: 物料编码（类型: string）
-     data.list[].materialName: 物料名称（类型: string）
-     data.list[].totalWorkHours: 总工时(h)（类型: number）
-     data.list[].totalGood: 总良品数（类型: number）
-     data.list[].totalBad: 总不良品数（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询操作工统计"
- "查看操作工统计列表"
- "有哪些操作工统计？"
- "列出操作工统计"

## 接口：导出生产月报

- **路径：** POST /api/mes/production-monthly-report/export
- **所属模块：** 生产月报
- **HTTP 方法：** POST

**功能说明：**
导出生产月报

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- yearMonth: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么导出生产月报？"
- "导出生产月报怎么做？"
- "帮我导出生产月报"

## 接口：分页查询生产月报

- **路径：** POST /api/mes/production-monthly-report/page
- **所属模块：** 生产月报
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询生产月报列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- yearMonth: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].yearMonth: 年月（类型: string）
-     data.list[].materialCode: 物料编号（类型: string）
-     data.list[].materialName: 物料名称（类型: string）
-     data.list[].productionOrderNo: 生产订单号（类型: string）
-     data.list[].plannedTotal: 计划总产量（类型: number）
-     data.list[].applyInboundTotal: 申请入库总数（类型: number）
-     data.list[].actualInboundTotal: 已入库总数（类型: number）
-     data.list[].cumulativeBad: 累计不良品数（类型: number）
-     data.list[].yieldRate: 总良率%（类型: number）
-     data.list[].planAchievementRate: 计划达成率%（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询生产月报"
- "查看生产月报列表"
- "有哪些生产月报？"
- "列出生产月报"

## 接口：批量从销售订单创建生产订单

- **路径：** POST /api/mes/producing-orders/batch-create
- **所属模块：** 生产订单
- **HTTP 方法：** POST

**功能说明：**
批量从销售订单创建生产订单

**请求参数：**
- salesIds: 销售订单 ID 列表（类型: array<string>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<integer>）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量从销售订单创建生产订单？"
- "批量从销售订单创建生产订单怎么做？"
- "帮我批量从销售订单创建生产订单"

## 接口：批量发布生产订单

- **路径：** POST /api/mes/producing-orders/batch-publish
- **所属模块：** 生产订单
- **HTTP 方法：** POST

**功能说明：**
批量发布生产订单

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "发布到生产"
- "提交生产"

## 接口：从销售订单创建生产订单

- **路径：** POST /api/mes/producing-orders/create
- **所属模块：** 生产订单
- **HTTP 方法：** POST

**功能说明：**
从销售订单创建生产订单

**请求参数：**
- salesOrderId （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<integer>）
- extraData: （类型: object）

**适用问题示例：**
- "怎么从销售订单创建生产订单？"
- "从销售订单创建生产订单怎么做？"
- "帮我从销售订单创建生产订单"

## 接口：从销售订单创建生产订单

- **路径：** POST /api/mes/producing-orders/create-by-sales-order
- **所属模块：** 生产订单
- **HTTP 方法：** POST

**功能说明：**
从销售订单创建生产订单

**请求参数：**
- salesOrderId: 销售订单 ID（类型: integer，可选）
- predictVos: 预计生产列表（类型: array<object>，可选）
-   predictVos[].productId: （类型: integer）
-   predictVos[].materialType: （类型: integer）
-   predictVos[].predictNum: （类型: number）
-   predictVos[].type: （类型: integer）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<integer>）
- extraData: （类型: object）

**适用问题示例：**
- "怎么从销售订单创建生产订单？"
- "从销售订单创建生产订单怎么做？"
- "帮我从销售订单创建生产订单"

## 接口：创建新的生产订单

- **路径：** POST /api/mes/producing-orders/create-work-order
- **所属模块：** 生产订单
- **HTTP 方法：** POST

**功能说明：**
用于创建新的新的生产订单记录，录入相关业务信息。

**请求参数：**
- salesOrderId: （类型: integer，可选）
- type: （类型: integer，可选）
- remark: （类型: string，可选）
- predictNum: （类型: number，可选）
- customerId: （类型: integer，可选）
- ordersCode: （类型: string，可选）
- ordersName: （类型: string，可选）
- deliveryDate: （类型: string，可选）
- salesAmount: （类型: number，可选）
- priority: （类型: integer，可选）
- customerManager: （类型: string，可选）
- productId: （类型: integer，可选）
- materialType: （类型: integer，可选）
- productType: （类型: string，可选）
- productCode: （类型: string，可选）
- productName: （类型: string，可选）
- productClass: （类型: string，可选）
- specModel: （类型: string，可选）
- unitId: （类型: integer，可选）
- productNum: （类型: number，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个新的生产订单"
- "新增新的生产订单记录"
- "创建一个新的新的生产订单"
- "帮我添加新的生产订单"

## 接口：删除生产订单

- **路径：** POST /api/mes/producing-orders/delete
- **所属模块：** 生产订单
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的生产订单记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除生产订单"
- "移除某个生产订单"

## 接口：获取生产订单详情

- **路径：** GET /api/mes/producing-orders/detail
- **所属模块：** 生产订单
- **HTTP 方法：** GET

**功能说明：**
根据ID获取生产订单中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.status: （类型: integer）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.productId: （类型: integer）
-   data.materialType: （类型: integer）
-   data.predictNum: （类型: number）
-   data.producedNum: （类型: number）
-   data.salesOrderId: （类型: integer）
-   data.salesOrderCode: （类型: string）
-   data.salesOrderName: （类型: string）
-   data.customerId: （类型: integer）
-   data.productType: （类型: string）
-   data.productCode: （类型: string）
-   data.productName: （类型: string）
-   data.productClass: （类型: string）
-   data.specModel: （类型: string）
-   data.unitId: （类型: integer）
-   data.unitName: （类型: string）
-   data.productionOrderNo: （类型: string）
-   data.type: （类型: integer）
-   data.priority: （类型: integer）
-   data.customerManager: （类型: string）
-   data.salesAmount: （类型: number）
-   data.requirementName: （类型: string）
-   data.requirementCode: （类型: string）
-   data.remark: （类型: string）
-   data.receivedNum: （类型: integer）
-   data.deliveryDate: （类型: string）
-   data.ordersStatus: （类型: integer）
-   data.moLifecycleState: （类型: integer）
-   data.moStateTime: （类型: string）
-   data.ordersStatusName: （类型: string）
-   data.typeEnum: （类型: string）
-   data.moLifecycleStateEnum: （类型: string）
-   data.statusEnum: （类型: string）
-   data.priorityName: （类型: string）
-   data.typeName: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看生产订单的详细信息"
- "生产订单详情"
- "生产订单的具体情况"

## 接口：分页查询生产订单

- **路径：** POST /api/mes/producing-orders/page
- **所属模块：** 生产订单
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询生产订单列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- customerName: （类型: string，可选）
- customerId: （类型: string，可选）
- customerManagerName: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- salesOrderNo: （类型: string，可选）
- productCode: （类型: string，可选）
- productName: （类型: string，可选）
- deliveryDateStart: （类型: string，可选）
- deliveryDateEnd: （类型: string，可选）
- type: （类型: integer，可选）
- ordersStatus: （类型: string，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].status: （类型: integer）
-     data.list[].createBy: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateBy: （类型: string）
-     data.list[].updateTime: （类型: string）
-     data.list[].isValid: （类型: integer）
-     data.list[].productId: （类型: integer）
-     data.list[].materialType: （类型: integer）
-     data.list[].predictNum: （类型: number）
-     data.list[].producedNum: （类型: number）
-     data.list[].salesOrderId: （类型: integer）
-     data.list[].salesOrderCode: （类型: string）
-     data.list[].salesOrderName: （类型: string）
-     data.list[].customerId: （类型: integer）
-     data.list[].productType: （类型: string）
-     data.list[].productCode: （类型: string）
-     data.list[].productName: （类型: string）
-     data.list[].productClass: （类型: string）
-     data.list[].specModel: （类型: string）
-     data.list[].unitId: （类型: integer）
-     data.list[].unitName: （类型: string）
-     data.list[].productionOrderNo: （类型: string）
-     data.list[].type: （类型: integer）
-     data.list[].priority: （类型: integer）
-     data.list[].customerManager: （类型: string）
-     data.list[].salesAmount: （类型: number）
-     data.list[].requirementName: （类型: string）
-     data.list[].requirementCode: （类型: string）
-     data.list[].remark: （类型: string）
-     data.list[].receivedNum: （类型: integer）
-     data.list[].deliveryDate: （类型: string）
-     data.list[].ordersStatus: （类型: integer）
-     data.list[].moLifecycleState: （类型: integer）
-     data.list[].moStateTime: （类型: string）
-     data.list[].typeEnum: （类型: string）
-     data.list[].moLifecycleStateEnum: （类型: string）
-     data.list[].statusEnum: （类型: string）
-     data.list[].priorityName: （类型: string）
-     data.list[].ordersStatusName: （类型: string）
-     data.list[].typeName: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询生产订单"
- "查看生产订单列表"
- "有哪些生产订单？"
- "列出生产订单"

## 接口：发布生产订单

- **路径：** POST /api/mes/producing-orders/publish
- **所属模块：** 生产订单
- **HTTP 方法：** POST

**功能说明：**
发布生产订单

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "发布到生产"
- "提交生产"

## 接口：终止生产订单（仅新增状态可终止）

- **路径：** POST /api/mes/producing-orders/terminate
- **所属模块：** 生产订单
- **HTTP 方法：** POST

**功能说明：**
终止生产订单（仅新增状态可终止）

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "终止订单"
- "取消订单"

## 接口：编辑生产订单

- **路径：** POST /api/mes/producing-orders/update
- **所属模块：** 生产订单
- **HTTP 方法：** POST

**功能说明：**
用于编辑修改已有的生产订单信息，更新相关字段。

**请求参数：**
- id: （类型: integer，可选）
- status: （类型: integer，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）
- productId: （类型: integer，可选）
- materialType: （类型: integer，可选）
- predictNum: （类型: number，可选）
- producedNum: （类型: number，可选）
- salesOrderId: （类型: integer，可选）
- salesOrderCode: （类型: string，可选）
- salesOrderName: （类型: string，可选）
- customerId: （类型: integer，可选）
- productType: （类型: string，可选）
- productCode: （类型: string，可选）
- productName: （类型: string，可选）
- productClass: （类型: string，可选）
- specModel: （类型: string，可选）
- unitId: （类型: integer，可选）
- unitName: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- type: （类型: integer，可选）
- priority: （类型: integer，可选）
- customerManager: （类型: string，可选）
- salesAmount: （类型: number，可选）
- requirementName: （类型: string，可选）
- requirementCode: （类型: string，可选）
- remark: （类型: string，可选）
- receivedNum: （类型: integer，可选）
- deliveryDate: （类型: string，可选）
- ordersStatus: （类型: integer，可选）
- moLifecycleState: （类型: integer，可选）
- moStateTime: （类型: string，可选）
- typeEnum: （类型: string，可选）
- moLifecycleStateEnum: （类型: string，可选）
- statusEnum: （类型: string，可选）
- priorityName: （类型: string，可选）
- ordersStatusName: （类型: string，可选）
- typeName: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改生产订单信息"
- "更新生产订单数据"
- "编辑生产订单"

## 接口：发送生产订单状态机事件

- **路径：** POST /api/mes/orders/{orderId}/events
- **所属模块：** 生产订单状态机
- **HTTP 方法：** POST

**功能说明：**
发送生产订单状态机事件

**请求参数：**
- orderId （路径参数）: （类型: integer，必填）
- event: （类型: string，必填）
- headers: （类型: object，可选）
- goodQty: （类型: number，可选）
- badQty: （类型: number，可选）
- totalQty: （类型: number，可选）

**返回字段：**
- 无

**适用问题示例：**
- "怎么发送生产订单状态机事件？"
- "发送生产订单状态机事件怎么做？"
- "帮我发送生产订单状态机事件"

## 接口：查询生产订单状态

- **路径：** GET /api/mes/orders/{orderId}/status
- **所属模块：** 生产订单状态机
- **HTTP 方法：** GET

**功能说明：**
查询生产订单状态

**请求参数：**
- orderId （路径参数）: （类型: integer，必填）
- historyLimit （查询参数）: （类型: integer，可选，默认: 20）

**返回字段：**
- orderId: （类型: integer）
- currentStatus: （类型: string）
- availableEvents: （类型: array<string>）
- recentHistory: （类型: array<object>）
-   recentHistory[].fromState: （类型: integer）
-   recentHistory[].toState: （类型: integer）
-   recentHistory[].eventName: （类型: string）
-   recentHistory[].createTime: （类型: string）

**适用问题示例：**
- "查询生产订单状态"
- "查看生产订单状态列表"
- "有哪些生产订单状态？"
- "列出生产订单状态"
- "哪些{thing}还未完成？"
- "生产订单状态进展如何？"

## 接口：导出生产订单统计

- **路径：** POST /api/mes/producing-order-statistics/export
- **所属模块：** 生产订单统计
- **HTTP 方法：** POST

**功能说明：**
导出生产订单统计

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- search: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- productionWorkOrderNo: （类型: string，可选）
- salesOrderCode: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.taskId: （类型: string）
-   data.status: （类型: string）
-   data.statusName: （类型: string）
-   data.downloadUrl: （类型: string）
-   data.errorMsg: （类型: string）
-   data.createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看导出生产订单统计"
- "怎么导出生产订单统计？"
- "导出生产订单统计怎么做？"
- "帮我导出生产订单统计"

## 接口：分页查询生产订单统计

- **路径：** POST /api/mes/producing-order-statistics/page
- **所属模块：** 生产订单统计
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询生产订单统计列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- search: （类型: string，可选）
- productionOrderNo: （类型: string，可选）
- productionWorkOrderNo: （类型: string，可选）
- salesOrderCode: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].salesOrderCode: 销售订单号（类型: string）
-     data.list[].productionOrderNo: 生产订单号（类型: string）
-     data.list[].productionWorkOrderNo: 生产工单号（类型: string）
-     data.list[].predictNum: 计划生产数量（类型: number）
-     data.list[].applyInboundQty: 申请入库数（类型: number）
-     data.list[].productionProgress: 生产进度%（类型: number）
-     data.list[].inboundQty: 已入库数（类型: number）
-     data.list[].totalGood: 总良品数（类型: number）
-     data.list[].orderCompletionProgress: 订单完成进度%（类型: number）
-     data.list[].totalBad: 总次品数（类型: number）
-     data.list[].yieldRate: 总良率%（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询生产订单统计"
- "查看生产订单统计列表"
- "有哪些生产订单统计？"
- "列出生产订单统计"
- "有多少个订单？"
- "当前订单数量"

## 接口：查询空闲窗口：空结果返回 []，不抛错

- **路径：** GET /api/mes/device-schedule/idle
- **所属模块：** 空闲设备查询
- **HTTP 方法：** GET

**功能说明：**
查询空闲窗口：空结果返回 []，不抛错

**请求参数：**
- deviceType （查询参数）: （类型: string，必填）
- from （查询参数）: （类型: string，可选）
- to （查询参数）: （类型: string，可选）
- minMinutes （查询参数）: （类型: integer，可选，默认: 0）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].deviceId: （类型: integer）
-   data[].workSitId: （类型: integer）
-   data[].scheduleId: （类型: integer）
-   data[].startTime: （类型: string）
-   data[].endTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询空闲窗口：空结果返回 []，不抛错"
- "查看空闲窗口：空结果返回 []，不抛错列表"
- "有哪些空闲窗口：空结果返回 []，不抛错？"
- "列出空闲窗口：空结果返回 []，不抛错"

## 接口：获取批次号配置

- **路径：** GET /api/mes/system-settings/batch-no-config
- **所属模块：** 系统设置
- **HTTP 方法：** GET

**功能说明：**
根据ID获取系统设置中指定记录的完整详情信息。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.header: 批次号前缀（类型: string）
-   data.dataFormat: 批次号日期格式，默认YYYYMMDD（类型: string）
-   data.seqLength: 批次号序列长度，默认4（类型: integer）
-   data.unique: 是否唯一，默认true（类型: boolean）
- extraData: （类型: object）

**适用问题示例：**
- "查看批次号配置"
- "批次号配置是什么？"

## 接口：设置批次号配置

- **路径：** POST /api/mes/system-settings/batch-no-config
- **所属模块：** 系统设置
- **HTTP 方法：** POST

**功能说明：**
设置批次号配置

**请求参数：**
- header: 批次号前缀（类型: string，可选）
- dataFormat: 批次号日期格式，默认YYYYMMDD（类型: string，可选）
- seqLength: 批次号序列长度，默认4（类型: integer，可选）
- unique: 是否唯一，默认true（类型: boolean，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么设置批次号配置？"
- "设置批次号配置怎么做？"
- "帮我设置批次号配置"

## 接口：获取派工单完成配置

- **路径：** GET /api/mes/system-settings/dispatch-complete-config
- **所属模块：** 系统设置
- **HTTP 方法：** GET

**功能说明：**
根据ID获取系统设置中指定记录的完整详情信息。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.completeBy: 完成判断依据：good=累计良品数, total=累计报工总数（类型: string）
-   data.quantitySource: 数据来源：reported=报工数, approved=审批数（类型: string）
-   data.allowShortCompletion: 累计数量不足时是否允许完成（类型: boolean）
- extraData: （类型: object）

**适用问题示例：**
- "查看派工单完成配置"
- "派工单完成配置是什么？"

## 接口：设置派工单完成配置

- **路径：** POST /api/mes/system-settings/dispatch-complete-config
- **所属模块：** 系统设置
- **HTTP 方法：** POST

**功能说明：**
设置派工单完成配置

**请求参数：**
- completeBy: 完成判断依据：good=累计良品数, total=累计报工总数（类型: string，可选）
- quantitySource: 数据来源：reported=报工数, approved=审批数（类型: string，可选）
- allowShortCompletion: 累计数量不足时是否允许完成（类型: boolean，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "完成生产"
- "生产完成"

## 接口：获取物料需求单生成依据

- **路径：** GET /api/mes/system-settings/material-demand-source
- **所属模块：** 系统设置
- **HTTP 方法：** GET

**功能说明：**
根据ID获取系统设置中指定记录的完整详情信息。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看物料需求单生成依据"
- "物料需求单生成依据是什么？"

## 接口：设置物料需求单生成依据

- **路径：** POST /api/mes/system-settings/material-demand-source
- **所属模块：** 系统设置
- **HTTP 方法：** POST

**功能说明：**
1=工艺投料信息, 2=产品工艺BOM

**请求参数：**
- source （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么设置物料需求单生成依据？"
- "设置物料需求单生成依据怎么做？"
- "帮我设置物料需求单生成依据"

## 接口：获取工单下发模式

- **路径：** GET /api/mes/system-settings/work-order-delivery-mode
- **所属模块：** 系统设置
- **HTTP 方法：** GET

**功能说明：**
根据ID获取系统设置中指定记录的完整详情信息。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看工单下发模式"
- "工单下发模式是什么？"

## 接口：设置工单下发模式

- **路径：** POST /api/mes/system-settings/work-order-delivery-mode
- **所属模块：** 系统设置
- **HTTP 方法：** POST

**功能说明：**
1=直接整体下发, 2=按工序分解多派工单下发

**请求参数：**
- mode （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么设置工单下发模式？"
- "设置工单下发模式怎么做？"
- "帮我设置工单下发模式"

## 接口：创建维保工人

- **路径：** POST /api/mes/maintenance-workers/create
- **所属模块：** 维保工人
- **HTTP 方法：** POST

**功能说明：**
创建维保工人

**请求参数：**
- employeeId: （类型: string，可选）
- employeeName: （类型: string，可选）
- employeeSex: （类型: integer，可选）
- remark: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个维保工人"
- "新增维保工人记录"
- "创建一个新的维保工人"
- "帮我添加维保工人"

## 接口：删除维保工人

- **路径：** POST /api/mes/maintenance-workers/delete
- **所属模块：** 维保工人
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的维保工人记录。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "删除维保工人"
- "移除某个维保工人"

## 接口：获取维保工人详情

- **路径：** GET /api/mes/maintenance-workers/detail
- **所属模块：** 维保工人
- **HTTP 方法：** GET

**功能说明：**
根据ID获取维保工人中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.employeeId: （类型: string）
-   data.employeeName: （类型: string）
-   data.employeeSex: （类型: integer）
-   data.remark: （类型: string）
-   data.createTime: （类型: string）
-   data.updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看维保工人的详细信息"
- "维保工人详情"
- "维保工人的具体情况"

## 接口：分页查询维保工人

- **路径：** POST /api/mes/maintenance-workers/page
- **所属模块：** 维保工人
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询维保工人列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- employeeId: （类型: string，可选）
- employeeName: （类型: string，可选）
- employeeSex: （类型: integer，可选）
- search: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].employeeId: （类型: string）
-     data.list[].employeeName: （类型: string）
-     data.list[].employeeSex: （类型: integer）
-     data.list[].remark: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询维保工人"
- "查看维保工人列表"
- "有哪些维保工人？"
- "列出维保工人"

## 接口：编辑维保工人

- **路径：** POST /api/mes/maintenance-workers/update
- **所属模块：** 维保工人
- **HTTP 方法：** POST

**功能说明：**
编辑维保工人

**请求参数：**
- employeeId: （类型: string，可选）
- employeeName: （类型: string，可选）
- employeeSex: （类型: integer，可选）
- remark: （类型: string，可选）
- id: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改维保工人信息"
- "更新维保工人数据"
- "编辑维保工人"

## 接口：分页查询设备忙段

- **路径：** GET /api/mes/device-timelines/page
- **所属模块：** 设备时序
- **HTTP 方法：** GET

**功能说明：**
按条件分页查询设备忙段列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- deviceId （查询参数）: （类型: integer，可选）
- from （查询参数）: （类型: string，可选）
- to （查询参数）: （类型: string，可选）
- pageNum （查询参数）: （类型: integer，可选）
- pageSize （查询参数）: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].status: （类型: integer）
-     data.list[].createBy: （类型: string）
-     data.list[].createTime: （类型: string）
-     data.list[].updateBy: （类型: string）
-     data.list[].updateTime: （类型: string）
-     data.list[].isValid: （类型: integer）
-     data.list[].deviceId: （类型: integer）
-     data.list[].workSitId: （类型: integer）
-     data.list[].workDispatchId: （类型: integer）
-     data.list[].startTime: （类型: string）
-     data.list[].endTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询设备忙段"
- "查看设备忙段列表"
- "有哪些设备忙段？"
- "列出设备忙段"

## 接口：创建销售订单

- **路径：** POST /api/mes/sales-orders/create
- **所属模块：** 销售订单
- **HTTP 方法：** POST

**功能说明：**
用于创建新的销售订单记录，录入相关业务信息。

**请求参数：**
- ordersCode: （类型: string，可选）
- ordersName: （类型: string，可选）
- ordersDate: （类型: string，可选）
- deliveryDate: （类型: string，可选）
- customerId: （类型: integer，可选）
- priority: （类型: integer，可选）
- customerManager: （类型: string，可选）
- salesAmount: （类型: number，可选）
- requirementName: （类型: string，可选）
- requirementCode: （类型: string，可选）
- products: （类型: array<object>，可选）
-   products[].id: （类型: integer）
-   products[].productNum: （类型: number）
-   products[].unit: （类型: string）
- remark: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个销售订单"
- "新增销售订单记录"
- "创建一个新的销售订单"
- "帮我添加销售订单"

## 接口：从销售订单创建生产订单

- **路径：** POST /api/mes/sales-orders/create-producing-orders
- **所属模块：** 销售订单
- **HTTP 方法：** POST

**功能说明：**
从销售订单创建生产订单

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<integer>）
- extraData: （类型: object）

**适用问题示例：**
- "怎么从销售订单创建生产订单？"
- "从销售订单创建生产订单怎么做？"
- "帮我从销售订单创建生产订单"

## 接口：获取销售订单详情

- **路径：** GET /api/mes/sales-orders/detail
- **所属模块：** 销售订单
- **HTTP 方法：** GET

**功能说明：**
根据ID获取销售订单中指定记录的完整详情信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.status: （类型: integer）
-   data.createBy: （类型: string）
-   data.createTime: （类型: string）
-   data.updateBy: （类型: string）
-   data.updateTime: （类型: string）
-   data.isValid: （类型: integer）
-   data.requirementId: （类型: integer）
-   data.producingOrderId: （类型: integer）
-   data.ordersCode: （类型: string）
-   data.ordersName: （类型: string）
-   data.ordersDate: （类型: string）
-   data.deliveryDate: （类型: string）
-   data.customerId: （类型: integer）
-   data.priority: （类型: integer）
-   data.customerManager: （类型: string）
-   data.salesAmount: （类型: number）
-   data.requirementName: （类型: string）
-   data.requirementCode: （类型: string）
-   data.products: （类型: array<object>）
-     data.products[].id: （类型: integer）
-     data.products[].productNum: （类型: number）
-     data.products[].unit: （类型: string）
-   data.productItems: （类型: array<object>）
-     data.productItems[].id: （类型: integer）
-     data.productItems[].status: （类型: integer）
-     data.productItems[].createBy: （类型: string）
-     data.productItems[].createTime: （类型: string）
-     data.productItems[].updateBy: （类型: string）
-     data.productItems[].updateTime: （类型: string）
-     data.productItems[].isValid: （类型: integer）
-     data.productItems[].productId: （类型: integer）
-     data.productItems[].materialType: （类型: integer）
-     data.productItems[].requirementId: （类型: integer）
-     data.productItems[].productCode: （类型: string）
-     data.productItems[].productName: （类型: string）
-     data.productItems[].productType: （类型: string）
-     data.productItems[].specModel: （类型: string）
-     data.productItems[].productNum: （类型: number）
-     data.productItems[].unitId: （类型: string）
-   data.ordersStatus: （类型: integer）
-   data.deliveryCode: （类型: string）
-   data.statusEnum: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看订单的详细信息"
- "订单详情"
- "订单的具体情况"

## 接口：结束销售订单

- **路径：** POST /api/mes/sales-orders/end
- **所属模块：** 销售订单
- **HTTP 方法：** POST

**功能说明：**
结束销售订单

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么结束销售订单？"
- "结束销售订单怎么做？"
- "帮我结束销售订单"

## 接口：分页查询销售订单

- **路径：** POST /api/mes/sales-orders/page
- **所属模块：** 销售订单
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询销售订单列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- customerManager: （类型: integer，可选）
- ordersStatus: （类型: string，可选）
- salesOrderCode: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: （类型: integer）
-     data.list[].ordersCode: （类型: string）
-     data.list[].ordersName: （类型: string）
-     data.list[].ordersDate: （类型: string）
-     data.list[].deliveryDate: （类型: string）
-     data.list[].customerId: （类型: integer）
-     data.list[].customerName: （类型: string）
-     data.list[].priority: （类型: integer）
-     data.list[].customerManager: （类型: string）
-     data.list[].customerManagerName: （类型: string）
-     data.list[].salesAmount: （类型: number）
-     data.list[].requirementName: （类型: string）
-     data.list[].requirementCode: （类型: string）
-     data.list[].ordersStatus: （类型: integer）
-     data.list[].deliveryCode: （类型: string）
-     data.list[].createTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询销售订单"
- "查看销售订单列表"
- "有哪些销售订单？"
- "列出销售订单"

## 接口：编辑销售订单

- **路径：** POST /api/mes/sales-orders/update
- **所属模块：** 销售订单
- **HTTP 方法：** POST

**功能说明：**
用于编辑修改已有的销售订单信息，更新相关字段。

**请求参数：**
- id: （类型: integer，可选）
- status: （类型: integer，可选）
- createBy: （类型: string，可选）
- createTime: （类型: string，可选）
- updateBy: （类型: string，可选）
- updateTime: （类型: string，可选）
- isValid: （类型: integer，可选）
- requirementId: （类型: integer，可选）
- producingOrderId: （类型: integer，可选）
- ordersCode: （类型: string，可选）
- ordersName: （类型: string，可选）
- ordersDate: （类型: string，可选）
- deliveryDate: （类型: string，可选）
- customerId: （类型: integer，可选）
- priority: （类型: integer，可选）
- customerManager: （类型: string，可选）
- salesAmount: （类型: number，可选）
- requirementName: （类型: string，可选）
- requirementCode: （类型: string，可选）
- products: （类型: array<object>，可选）
-   products[].id: （类型: integer）
-   products[].productNum: （类型: number）
-   products[].unit: （类型: string）
- productItems: （类型: array<object>，可选）
-   productItems[].id: （类型: integer）
-   productItems[].status: （类型: integer）
-   productItems[].createBy: （类型: string）
-   productItems[].createTime: （类型: string）
-   productItems[].updateBy: （类型: string）
-   productItems[].updateTime: （类型: string）
-   productItems[].isValid: （类型: integer）
-   productItems[].productId: （类型: integer）
-   productItems[].materialType: （类型: integer）
-   productItems[].requirementId: （类型: integer）
-   productItems[].productCode: （类型: string）
-   productItems[].productName: （类型: string）
-   productItems[].productType: （类型: string）
-   productItems[].specModel: （类型: string）
-   productItems[].productNum: （类型: number）
-   productItems[].unitId: （类型: string）
- ordersStatus: （类型: integer，可选）
- deliveryCode: （类型: string，可选）
- statusEnum: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "修改销售订单信息"
- "更新销售订单数据"
- "编辑销售订单"
