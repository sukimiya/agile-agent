# 服务：wms-service APIs document

## 接口：获取全部仓库列表

- **路径：** GET /api/v1/warehouses/all
- **所属模块：** 仓库主数据管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取仓库主数据管理中指定记录的完整详情信息。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].warehouseCode: （类型: string）
-   data[].warehouseName: （类型: string）
-   data[].warehouseDesc: （类型: string）
-   data[].siteId: （类型: string）
-   data[].siteName: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询获取全部仓库列表"
- "查看获取全部仓库列表列表"
- "有哪些获取全部仓库列表？"
- "列出获取全部仓库列表"

## 接口：创建仓库

- **路径：** POST /api/v1/warehouses/create
- **所属模块：** 仓库主数据管理
- **HTTP 方法：** POST

**功能说明：**
创建仓库

**请求参数：**
- id: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- warehouseName: （类型: string，可选）
- warehouseDesc: （类型: string，可选）
- siteId: （类型: string，可选）
- siteName: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.warehouseCode: （类型: string）
-   data.warehouseName: （类型: string）
-   data.warehouseDesc: （类型: string）
-   data.siteId: （类型: string）
-   data.siteName: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个仓库"
- "新增仓库记录"
- "创建一个新的仓库"
- "帮我添加仓库"

## 接口：删除仓库

- **路径：** POST /api/v1/warehouses/delete/{id}
- **所属模块：** 仓库主数据管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的仓库记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除仓库"
- "移除某个仓库"

## 接口：查询仓库详情

- **路径：** GET /api/v1/warehouses/detail/{id}
- **所属模块：** 仓库主数据管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取仓库主数据管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.warehouseCode: （类型: string）
-   data.warehouseName: （类型: string）
-   data.warehouseDesc: （类型: string）
-   data.siteId: （类型: string）
-   data.siteName: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询仓库详情"
- "查看仓库详情列表"
- "有哪些仓库详情？"
- "列出仓库详情"

## 接口：分页查询仓库列表

- **路径：** POST /api/v1/warehouses/list
- **所属模块：** 仓库主数据管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询仓库列表列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- warehouseName: （类型: string，可选）
- siteIds: （类型: array<string>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询仓库列表"
- "查看仓库列表列表"
- "有哪些仓库列表？"
- "列出仓库列表"

## 接口：获取仓库下拉选项

- **路径：** GET /api/v1/warehouses/options
- **所属模块：** 仓库主数据管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取仓库主数据管理中指定记录的完整详情信息。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].warehouseCode: （类型: string）
-   data[].warehouseName: （类型: string）
-   data[].warehouseDesc: （类型: string）
-   data[].siteId: （类型: string）
-   data[].siteName: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看仓库下拉选项"
- "仓库下拉选项是什么？"

## 接口：更新仓库

- **路径：** POST /api/v1/warehouses/update/{id}
- **所属模块：** 仓库主数据管理
- **HTTP 方法：** POST

**功能说明：**
更新仓库

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- warehouseName: （类型: string，可选）
- warehouseDesc: （类型: string，可选）
- siteId: （类型: string，可选）
- siteName: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.warehouseCode: （类型: string）
-   data.warehouseName: （类型: string）
-   data.warehouseDesc: （类型: string）
-   data.siteId: （类型: string）
-   data.siteName: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改仓库信息"
- "更新仓库数据"
- "编辑仓库"

## 接口：获取出入库单数按年统计列表（入库/出库各 12 个月，物料与成品合并计数）

- **路径：** GET /api/v1/statistics/bills/yearly
- **所属模块：** 出入库统计分析
- **HTTP 方法：** GET

**功能说明：**
根据ID获取出入库统计分析中指定记录的完整详情信息。

**请求参数：**
- year （查询参数）: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.year: （类型: integer）
-   data.inboundCountList: （类型: array<integer>）
-   data.outboundCountList: （类型: array<integer>）
- extraData: （类型: object）

**适用问题示例：**
- "查询获取出入库单数按年统计列表（入库/出库各 12 个月，物料与成品合并计数）"
- "查看获取出入库单数按年统计列表（入库/出库各 12 个月，物料与成品合并计数）列表"
- "有哪些获取出入库单数按年统计列表（入库/出库各 12 个月，物料与成品合并计数）？"
- "列出获取出入库单数按年统计列表（入库/出库各 12 个月，物料与成品合并计数）"
- "有多少物料？"
- "物料数量"

## 接口：获取近10天物料出入库单数列表

- **路径：** GET /api/v1/statistics/material/bills/recent-10-days
- **所属模块：** 出入库统计分析
- **HTTP 方法：** GET

**功能说明：**
根据ID获取出入库统计分析中指定记录的完整详情信息。

**请求参数：**
- type （查询参数）: （类型: integer，可选，默认: 1）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<integer>）
- extraData: （类型: object）

**适用问题示例：**
- "查询获取近10天物料出入库单数列表"
- "查看获取近10天物料出入库单数列表列表"
- "有哪些获取近10天物料出入库单数列表？"
- "列出获取近10天物料出入库单数列表"
- "有多少物料？"
- "物料数量"

## 接口：获取物料统计总览

- **路径：** GET /api/v1/statistics/material/overview
- **所属模块：** 出入库统计分析
- **HTTP 方法：** GET

**功能说明：**
根据ID获取出入库统计分析中指定记录的完整详情信息。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.inboundCountList: （类型: array<integer>）
-   data.outboundCountList: （类型: array<integer>）
-   data.recentInboundRecords: （类型: array<object>）
-     data.recentInboundRecords[].itemName: （类型: string）
-     data.recentInboundRecords[].qty: （类型: number）
-     data.recentInboundRecords[].operateTime: （类型: string）
-     data.recentInboundRecords[].itemCode: （类型: string）
-     data.recentInboundRecords[].unitName: （类型: string）
-     data.recentInboundRecords[].batchNo: （类型: string）
-     data.recentInboundRecords[].operator: （类型: string）
-     data.recentInboundRecords[].inboundDate: （类型: string）
-   data.recentOutboundRecords: （类型: array<object>）
-     data.recentOutboundRecords[].itemName: （类型: string）
-     data.recentOutboundRecords[].qty: （类型: number）
-     data.recentOutboundRecords[].operateTime: （类型: string）
-     data.recentOutboundRecords[].itemCode: （类型: string）
-     data.recentOutboundRecords[].unitName: （类型: string）
-     data.recentOutboundRecords[].batchNo: （类型: string）
-     data.recentOutboundRecords[].operator: （类型: string）
-     data.recentOutboundRecords[].inboundDate: （类型: string）
-   data.currentMonthInboundBillTotal: （类型: integer）
-   data.currentMonthOutboundBillTotal: （类型: integer）
-   data.inventoryAlertCategoryTotal: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取物料统计总览"
- "怎么获取物料统计总览？"
- "获取物料统计总览怎么做？"
- "帮我获取物料统计总览"

## 接口：获取近30天物料出入库记录列表

- **路径：** GET /api/v1/statistics/material/records/recent-30-days
- **所属模块：** 出入库统计分析
- **HTTP 方法：** GET

**功能说明：**
根据ID获取出入库统计分析中指定记录的完整详情信息。

**请求参数：**
- type （查询参数）: （类型: integer，可选，默认: 1）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].itemName: （类型: string）
-   data[].qty: （类型: number）
-   data[].operateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询获取近30天物料出入库记录列表"
- "查看获取近30天物料出入库记录列表列表"
- "有哪些获取近30天物料出入库记录列表？"
- "列出获取近30天物料出入库记录列表"
- "有多少物料？"
- "物料数量"

## 接口：获取近10天成品出入库单数列表

- **路径：** GET /api/v1/statistics/product/bills/recent-10-days
- **所属模块：** 出入库统计分析
- **HTTP 方法：** GET

**功能说明：**
根据ID获取出入库统计分析中指定记录的完整详情信息。

**请求参数：**
- type （查询参数）: （类型: integer，可选，默认: 1）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<integer>）
- extraData: （类型: object）

**适用问题示例：**
- "查询获取近10天成品出入库单数列表"
- "查看获取近10天成品出入库单数列表列表"
- "有哪些获取近10天成品出入库单数列表？"
- "列出获取近10天成品出入库单数列表"

## 接口：获取成品统计总览

- **路径：** GET /api/v1/statistics/product/overview
- **所属模块：** 出入库统计分析
- **HTTP 方法：** GET

**功能说明：**
根据ID获取出入库统计分析中指定记录的完整详情信息。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.inboundCountList: （类型: array<integer>）
-   data.outboundCountList: （类型: array<integer>）
-   data.recentInboundRecords: （类型: array<object>）
-     data.recentInboundRecords[].itemName: （类型: string）
-     data.recentInboundRecords[].qty: （类型: number）
-     data.recentInboundRecords[].operateTime: （类型: string）
-     data.recentInboundRecords[].itemCode: （类型: string）
-     data.recentInboundRecords[].unitName: （类型: string）
-     data.recentInboundRecords[].batchNo: （类型: string）
-     data.recentInboundRecords[].operator: （类型: string）
-     data.recentInboundRecords[].inboundDate: （类型: string）
-   data.recentOutboundRecords: （类型: array<object>）
-     data.recentOutboundRecords[].itemName: （类型: string）
-     data.recentOutboundRecords[].qty: （类型: number）
-     data.recentOutboundRecords[].operateTime: （类型: string）
-     data.recentOutboundRecords[].itemCode: （类型: string）
-     data.recentOutboundRecords[].unitName: （类型: string）
-     data.recentOutboundRecords[].batchNo: （类型: string）
-     data.recentOutboundRecords[].operator: （类型: string）
-     data.recentOutboundRecords[].inboundDate: （类型: string）
-   data.currentMonthInboundBillTotal: （类型: integer）
-   data.currentMonthOutboundBillTotal: （类型: integer）
-   data.inventoryAlertCategoryTotal: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取成品统计总览"
- "怎么获取成品统计总览？"
- "获取成品统计总览怎么做？"
- "帮我获取成品统计总览"

## 接口：获取近30天成品出入库记录列表

- **路径：** GET /api/v1/statistics/product/records/recent-30-days
- **所属模块：** 出入库统计分析
- **HTTP 方法：** GET

**功能说明：**
根据ID获取出入库统计分析中指定记录的完整详情信息。

**请求参数：**
- type （查询参数）: （类型: integer，可选，默认: 1）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].itemName: （类型: string）
-   data[].qty: （类型: number）
-   data[].operateTime: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询获取近30天成品出入库记录列表"
- "查看获取近30天成品出入库记录列表列表"
- "有哪些获取近30天成品出入库记录列表？"
- "列出获取近30天成品出入库记录列表"

## 接口：取消成品入库单

- **路径：** POST /api/v1/product-receivings/cancel/{id}
- **所属模块：** 成品入库单管理
- **HTTP 方法：** POST

**功能说明：**
取消成品入库单

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么取消成品入库单？"
- "取消成品入库单怎么做？"
- "帮我取消成品入库单"

## 接口：创建成品入库单

- **路径：** POST /api/v1/product-receivings/create
- **所属模块：** 成品入库单管理
- **HTTP 方法：** POST

**功能说明：**
创建成品入库单

**请求参数：**
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- applicationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].productId: （类型: integer）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].productBatchNo: （类型: string）
-   details[].productOrderNo: （类型: string）
-   details[].workOrderNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].applyOutboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].outboundCheck: （类型: integer）
-   details[].outboundCheckId: （类型: string）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个成品入库单"
- "新增成品入库单记录"
- "创建一个新的成品入库单"
- "帮我添加成品入库单"

## 接口：删除成品入库单

- **路径：** POST /api/v1/product-receivings/delete/{id}
- **所属模块：** 成品入库单管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的成品入库单记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除成品入库单"
- "移除某个成品入库单"

## 接口：查询成品入库单详情

- **路径：** GET /api/v1/product-receivings/detail/{id}
- **所属模块：** 成品入库单管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取成品入库单管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品入库单详情"
- "查看成品入库单详情列表"
- "有哪些成品入库单详情？"
- "列出成品入库单详情"

## 接口：执行成品入库

- **路径：** POST /api/v1/product-receivings/inbound
- **所属模块：** 成品入库单管理
- **HTTP 方法：** POST

**功能说明：**
执行成品入库

**请求参数：**
- id: （类型: integer，可选）
- finished: （类型: boolean，可选）
- details: （类型: array<object>，可选）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitName: （类型: string）
-   details[].currentQty: （类型: number）
-   details[].productBatchNo: （类型: string）
-   details[].inboundDate: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "怎么执行成品入库？"
- "执行成品入库怎么做？"
- "帮我执行成品入库"

## 接口：分页查询成品入库单列表

- **路径：** POST /api/v1/product-receivings/list
- **所属模块：** 成品入库单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品入库单列表列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- search: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）
- orderStatus: （类型: array<integer>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品入库单列表"
- "查看成品入库单列表列表"
- "有哪些成品入库单列表？"
- "列出成品入库单列表"

## 接口：更新成品入库单

- **路径：** POST /api/v1/product-receivings/update/{id}
- **所属模块：** 成品入库单管理
- **HTTP 方法：** POST

**功能说明：**
更新成品入库单

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- applicationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].productId: （类型: integer）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].productBatchNo: （类型: string）
-   details[].productOrderNo: （类型: string）
-   details[].workOrderNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].applyOutboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].outboundCheck: （类型: integer）
-   details[].outboundCheckId: （类型: string）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改成品入库单信息"
- "更新成品入库单数据"
- "编辑成品入库单"

## 接口：分页查询成品入库操作日志

- **路径：** POST /api/v1/product-receiving-logs/list
- **所属模块：** 成品入库操作日志管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品入库操作日志列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- billNo: （类型: string，可选）
- productCode: （类型: string，可选）
- productBatchNo: （类型: string，可选）
- operationType: （类型: integer，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品入库操作日志"
- "查看成品入库操作日志列表"
- "有哪些成品入库操作日志？"
- "列出成品入库操作日志"

## 接口：分页查询成品入库日志按月汇总

- **路径：** POST /api/v1/product-receiving-logs/monthly-summary/list
- **所属模块：** 成品入库操作日志管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品入库日志按月汇总列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- productCode: （类型: string，可选）
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
- extraData: （类型: object）

**适用问题示例：**
- "查询成品入库日志按月汇总"
- "查看成品入库日志按月汇总列表"
- "有哪些成品入库日志按月汇总？"
- "列出成品入库日志按月汇总"

## 接口：创建成品入库申请单

- **路径：** POST /api/v1/product-receiving-applications/create
- **所属模块：** 成品入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
创建成品入库申请单

**请求参数：**
- id: （类型: integer，可选）
- applicationNo: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- billGenStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].productId: （类型: integer）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].productBatchNo: （类型: string）
-   details[].productOrderNo: （类型: string）
-   details[].workOrderNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].applyOutboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].outboundCheck: （类型: integer）
-   details[].outboundCheckId: （类型: string）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个成品入库申请单"
- "新增成品入库申请单记录"
- "创建一个新的成品入库申请单"
- "帮我添加成品入库申请单"

## 接口：由成品入库申请单生成成品入库单

- **路径：** POST /api/v1/product-receiving-applications/create-receiving
- **所属模块：** 成品入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
由成品入库申请单生成成品入库单

**请求参数：**
- applicationId: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].productCode: （类型: string）
-   details[].planInboundQty: （类型: number）
-   details[].refuseQty: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么由成品入库申请单生成成品入库单？"
- "由成品入库申请单生成成品入库单怎么做？"
- "帮我由成品入库申请单生成成品入库单"

## 接口：删除成品入库申请单

- **路径：** POST /api/v1/product-receiving-applications/delete/{id}
- **所属模块：** 成品入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的成品入库申请单记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除成品入库申请单"
- "移除某个成品入库申请单"

## 接口：查询成品入库申请单详情

- **路径：** GET /api/v1/product-receiving-applications/detail/{id}
- **所属模块：** 成品入库申请单管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取成品入库申请单管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品入库申请单详情"
- "查看成品入库申请单详情列表"
- "有哪些成品入库申请单详情？"
- "列出成品入库申请单详情"

## 接口：分页查询成品入库申请单列表

- **路径：** POST /api/v1/product-receiving-applications/list
- **所属模块：** 成品入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品入库申请单列表列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- search: （类型: string，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品入库申请单列表"
- "查看成品入库申请单列表列表"
- "有哪些成品入库申请单列表？"
- "列出成品入库申请单列表"

## 接口：批量更新成品入库申请明细入库检查状态

- **路径：** POST /api/v1/product-receiving-applications/update-inbound-check
- **所属模块：** 成品入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
批量更新成品入库申请明细入库检查状态

**请求参数：**
- applicationId: （类型: integer，可选）
- codes: （类型: array<string>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量更新成品入库申请明细入库检查状态？"
- "批量更新成品入库申请明细入库检查状态怎么做？"
- "帮我批量更新成品入库申请明细入库检查状态"

## 接口：更新成品入库申请单

- **路径：** POST /api/v1/product-receiving-applications/update/{id}
- **所属模块：** 成品入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
更新成品入库申请单

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- applicationNo: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- billGenStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].productId: （类型: integer）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].productBatchNo: （类型: string）
-   details[].productOrderNo: （类型: string）
-   details[].workOrderNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].applyOutboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].outboundCheck: （类型: integer）
-   details[].outboundCheckId: （类型: string）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改成品入库申请单信息"
- "更新成品入库申请单数据"
- "编辑成品入库申请单"

## 接口：取消成品出库单

- **路径：** POST /api/v1/product-outbounds/cancel/{id}
- **所属模块：** 成品出库单管理
- **HTTP 方法：** POST

**功能说明：**
取消成品出库单

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么取消成品出库单？"
- "取消成品出库单怎么做？"
- "帮我取消成品出库单"

## 接口：创建成品出库单

- **路径：** POST /api/v1/product-outbounds/create
- **所属模块：** 成品出库单管理
- **HTTP 方法：** POST

**功能说明：**
创建成品出库单

**请求参数：**
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- customer: （类型: string，可选）
- customerName: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- applicationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- outboundApplicationId: （类型: integer，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].productId: （类型: integer）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].productBatchNo: （类型: string）
-   details[].productOrderNo: （类型: string）
-   details[].workOrderNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].applyOutboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].outboundCheck: （类型: integer）
-   details[].outboundCheckId: （类型: string）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.customer: （类型: string）
-   data.customerName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.outboundApplicationId: （类型: integer）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个成品出库单"
- "新增成品出库单记录"
- "创建一个新的成品出库单"
- "帮我添加成品出库单"

## 接口：删除成品出库单

- **路径：** POST /api/v1/product-outbounds/delete/{id}
- **所属模块：** 成品出库单管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的成品出库单记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除成品出库单"
- "移除某个成品出库单"

## 接口：查询成品出库单详情

- **路径：** GET /api/v1/product-outbounds/detail/{id}
- **所属模块：** 成品出库单管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取成品出库单管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.customer: （类型: string）
-   data.customerName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.outboundApplicationId: （类型: integer）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品出库单详情"
- "查看成品出库单详情列表"
- "有哪些成品出库单详情？"
- "列出成品出库单详情"

## 接口：分页查询成品出库单列表

- **路径：** POST /api/v1/product-outbounds/list
- **所属模块：** 成品出库单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品出库单列表列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- search: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）
- orderStatus: （类型: array<integer>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品出库单列表"
- "查看成品出库单列表列表"
- "有哪些成品出库单列表？"
- "列出成品出库单列表"

## 接口：执行成品出库

- **路径：** POST /api/v1/product-outbounds/outbound
- **所属模块：** 成品出库单管理
- **HTTP 方法：** POST

**功能说明：**
执行成品出库

**请求参数：**
- id: （类型: integer，可选）
- finished: （类型: boolean，可选）
- details: （类型: array<object>，可选）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitName: （类型: string）
-   details[].currentQty: （类型: number）
-   details[].productBatchNo: （类型: string）
-   details[].inboundDate: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.customer: （类型: string）
-   data.customerName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.outboundApplicationId: （类型: integer）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "怎么执行成品出库？"
- "执行成品出库怎么做？"
- "帮我执行成品出库"

## 接口：更新成品出库单

- **路径：** POST /api/v1/product-outbounds/update/{id}
- **所属模块：** 成品出库单管理
- **HTTP 方法：** POST

**功能说明：**
更新成品出库单

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- customer: （类型: string，可选）
- customerName: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- applicationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- outboundApplicationId: （类型: integer，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].productId: （类型: integer）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].productBatchNo: （类型: string）
-   details[].productOrderNo: （类型: string）
-   details[].workOrderNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].applyOutboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].outboundCheck: （类型: integer）
-   details[].outboundCheckId: （类型: string）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.customer: （类型: string）
-   data.customerName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.outboundApplicationId: （类型: integer）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改成品出库单信息"
- "更新成品出库单数据"
- "编辑成品出库单"

## 接口：分页查询成品出库操作日志

- **路径：** POST /api/v1/product-outbound-logs/list
- **所属模块：** 成品出库操作日志管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品出库操作日志列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- billNo: （类型: string，可选）
- productCode: （类型: string，可选）
- productBatchNo: （类型: string，可选）
- operationType: （类型: integer，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品出库操作日志"
- "查看成品出库操作日志列表"
- "有哪些成品出库操作日志？"
- "列出成品出库操作日志"

## 接口：分页查询成品出库日志按月汇总

- **路径：** POST /api/v1/product-outbound-logs/monthly-summary/list
- **所属模块：** 成品出库操作日志管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品出库日志按月汇总列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- productCode: （类型: string，可选）
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
- extraData: （类型: object）

**适用问题示例：**
- "查询成品出库日志按月汇总"
- "查看成品出库日志按月汇总列表"
- "有哪些成品出库日志按月汇总？"
- "列出成品出库日志按月汇总"

## 接口：创建成品出库申请单

- **路径：** POST /api/v1/product-outbound-applications/create
- **所属模块：** 成品出库申请单管理
- **HTTP 方法：** POST

**功能说明：**
创建成品出库申请单

**请求参数：**
- id: （类型: integer，可选）
- applicationNo: （类型: string，可选）
- customer: （类型: string，可选）
- customerName: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- billGenStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].productId: （类型: integer）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].productBatchNo: （类型: string）
-   details[].productOrderNo: （类型: string）
-   details[].workOrderNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].applyOutboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].outboundCheck: （类型: integer）
-   details[].outboundCheckId: （类型: string）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.customer: （类型: string）
-   data.customerName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个成品出库申请单"
- "新增成品出库申请单记录"
- "创建一个新的成品出库申请单"
- "帮我添加成品出库申请单"

## 接口：删除成品出库申请单

- **路径：** POST /api/v1/product-outbound-applications/delete/{id}
- **所属模块：** 成品出库申请单管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的成品出库申请单记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除成品出库申请单"
- "移除某个成品出库申请单"

## 接口：查询成品出库申请单详情

- **路径：** GET /api/v1/product-outbound-applications/detail/{id}
- **所属模块：** 成品出库申请单管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取成品出库申请单管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.customer: （类型: string）
-   data.customerName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品出库申请单详情"
- "查看成品出库申请单详情列表"
- "有哪些成品出库申请单详情？"
- "列出成品出库申请单详情"

## 接口：分页查询成品出库申请单列表

- **路径：** POST /api/v1/product-outbound-applications/list
- **所属模块：** 成品出库申请单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品出库申请单列表列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- search: （类型: string，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品出库申请单列表"
- "查看成品出库申请单列表列表"
- "有哪些成品出库申请单列表？"
- "列出成品出库申请单列表"

## 接口：批量更新成品出库申请明细出库检查状态

- **路径：** POST /api/v1/product-outbound-applications/update-outbound-check
- **所属模块：** 成品出库申请单管理
- **HTTP 方法：** POST

**功能说明：**
批量更新成品出库申请明细出库检查状态

**请求参数：**
- applicationId: （类型: integer，可选）
- codes: （类型: array<string>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量更新成品出库申请明细出库检查状态？"
- "批量更新成品出库申请明细出库检查状态怎么做？"
- "帮我批量更新成品出库申请明细出库检查状态"

## 接口：更新成品出库申请单

- **路径：** POST /api/v1/product-outbound-applications/update/{id}
- **所属模块：** 成品出库申请单管理
- **HTTP 方法：** POST

**功能说明：**
更新成品出库申请单

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- applicationNo: （类型: string，可选）
- customer: （类型: string，可选）
- customerName: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- billGenStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].productId: （类型: integer）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].productBatchNo: （类型: string）
-   details[].productOrderNo: （类型: string）
-   details[].workOrderNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].applyOutboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].outboundCheck: （类型: integer）
-   details[].outboundCheckId: （类型: string）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.customer: （类型: string）
-   data.customerName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].productId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].productOrderNo: （类型: string）
-     data.details[].workOrderNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].outboundCheck: （类型: integer）
-     data.details[].outboundCheckId: （类型: string）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改成品出库申请单信息"
- "更新成品出库申请单数据"
- "编辑成品出库申请单"

## 接口：删除成品基础信息

- **路径：** POST /api/v1/product-infos/delete/{id}
- **所属模块：** 成品基础信息管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的成品基础信息记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除成品基础信息"
- "移除某个成品基础信息"

## 接口：查询成品基础信息详情

- **路径：** GET /api/v1/product-infos/detail/{id}
- **所属模块：** 成品基础信息管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取成品基础信息管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.productCode: （类型: string）
-   data.productName: （类型: string）
-   data.productClass: （类型: string）
-   data.productType: （类型: string）
-   data.specModel: （类型: string）
-   data.unitId: （类型: integer）
-   data.unitName: （类型: string）
-   data.extraAttr1: （类型: string）
-   data.extraAttr2: （类型: string）
-   data.minSafeQty: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品基础信息详情"
- "查看成品基础信息详情列表"
- "有哪些成品基础信息详情？"
- "列出成品基础信息详情"

## 接口：分页查询成品基础信息

- **路径：** POST /api/v1/product-infos/list
- **所属模块：** 成品基础信息管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品基础信息列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- productCode: （类型: string，可选）
- productName: （类型: string，可选）
- productClass: （类型: string，可选）
- productType: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品基础信息"
- "查看成品基础信息列表"
- "有哪些成品基础信息？"
- "列出成品基础信息"

## 接口：创建/更新成品基础信息

- **路径：** POST /api/v1/product-infos/save
- **所属模块：** 成品基础信息管理
- **HTTP 方法：** POST

**功能说明：**
创建/更新成品基础信息

**请求参数：**
- id: （类型: integer，可选）
- productCode: （类型: string，可选）
- productName: （类型: string，可选）
- productClass: （类型: string，可选）
- productType: （类型: string，可选）
- specModel: （类型: string，可选）
- unitId: （类型: integer，可选）
- unitName: （类型: string，可选）
- extraAttr1: （类型: string，可选）
- extraAttr2: （类型: string，可选）
- minSafeQty: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.productCode: （类型: string）
-   data.productName: （类型: string）
-   data.productClass: （类型: string）
-   data.productType: （类型: string）
-   data.specModel: （类型: string）
-   data.unitId: （类型: integer）
-   data.unitName: （类型: string）
-   data.extraAttr1: （类型: string）
-   data.extraAttr2: （类型: string）
-   data.minSafeQty: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个/更新成品基础信息"
- "新增/更新成品基础信息记录"
- "创建一个新的/更新成品基础信息"
- "帮我添加/更新成品基础信息"

## 接口：查询成品库存详情

- **路径：** GET /api/v1/product-inventories/detail/{id}
- **所属模块：** 成品库存管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取成品库存管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.warehouseCode: （类型: string）
-   data.productCode: （类型: string）
-   data.productName: （类型: string）
-   data.productClass: （类型: string）
-   data.productType: （类型: string）
-   data.specModel: （类型: string）
-   data.unitId: （类型: integer）
-   data.unitName: （类型: string）
-   data.extraAttr1: （类型: string）
-   data.extraAttr2: （类型: string）
-   data.inventoryQty: （类型: number）
-   data.availableQty: （类型: number）
-   data.frozenQty: （类型: number）
-   data.minSafeQty: （类型: integer）
-   data.inventoryStatus: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品库存详情"
- "查看成品库存详情列表"
- "有哪些成品库存详情？"
- "列出成品库存详情"

## 接口：分页查询成品库存

- **路径：** POST /api/v1/product-inventories/list
- **所属模块：** 成品库存管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品库存列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- productCode: （类型: string，可选）
- productName: （类型: string，可选）
- inventoryStatus: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品库存"
- "查看成品库存列表"
- "有哪些成品库存？"
- "列出成品库存"

## 接口：分页查询成品批次库存

- **路径：** POST /api/v1/product-batch-inventories/list
- **所属模块：** 成品批次库存管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品批次库存列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- productCode: （类型: string，可选）
- productBatchNo: （类型: string，可选）
- excludeBatchInventoryIds: （类型: array<integer>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品批次库存"
- "查看成品批次库存列表"
- "有哪些成品批次库存？"
- "列出成品批次库存"

## 接口：根据仓库和成品编码查询成品库存及批次库存

- **路径：** GET /api/v1/product-batch-inventories/summary
- **所属模块：** 成品批次库存管理
- **HTTP 方法：** GET

**功能说明：**
根据仓库和成品编码查询成品库存及批次库存

**请求参数：**
- warehouseCode （查询参数）: （类型: string，可选）
- productCode （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.inventories: （类型: array<object>）
-     data.inventories[].id: （类型: integer）
-     data.inventories[].warehouseCode: （类型: string）
-     data.inventories[].productCode: （类型: string）
-     data.inventories[].productName: （类型: string）
-     data.inventories[].productClass: （类型: string）
-     data.inventories[].productType: （类型: string）
-     data.inventories[].specModel: （类型: string）
-     data.inventories[].unitId: （类型: integer）
-     data.inventories[].unitName: （类型: string）
-     data.inventories[].extraAttr1: （类型: string）
-     data.inventories[].extraAttr2: （类型: string）
-     data.inventories[].inventoryQty: （类型: number）
-     data.inventories[].availableQty: （类型: number）
-     data.inventories[].frozenQty: （类型: number）
-     data.inventories[].minSafeQty: （类型: integer）
-     data.inventories[].inventoryStatus: （类型: integer）
-   data.batches: （类型: array<object>）
-     data.batches[].id: （类型: integer）
-     data.batches[].warehouseCode: （类型: string）
-     data.batches[].productCode: （类型: string）
-     data.batches[].productName: （类型: string）
-     data.batches[].productClass: （类型: string）
-     data.batches[].productType: （类型: string）
-     data.batches[].specModel: （类型: string）
-     data.batches[].unitId: （类型: integer）
-     data.batches[].unitName: （类型: string）
-     data.batches[].extraAttr1: （类型: string）
-     data.batches[].extraAttr2: （类型: string）
-     data.batches[].productBatchNo: （类型: string）
-     data.batches[].inventoryQty: （类型: number）
-     data.batches[].inboundDate: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品库存及批次库存"
- "查看成品库存及批次库存列表"
- "有哪些成品库存及批次库存？"
- "列出成品库存及批次库存"

## 接口：取消成品盘点单

- **路径：** POST /api/v1/product-inventory-checks/cancel/{id}
- **所属模块：** 成品盘点单管理
- **HTTP 方法：** POST

**功能说明：**
取消成品盘点单

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么取消成品盘点单？"
- "取消成品盘点单怎么做？"
- "帮我取消成品盘点单"

## 接口：完成成品盘点

- **路径：** POST /api/v1/product-inventory-checks/complete/{id}
- **所属模块：** 成品盘点单管理
- **HTTP 方法：** POST

**功能说明：**
完成成品盘点

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].batchInventoryId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].inboundDate: （类型: string）
-     data.details[].inventoryQty: （类型: number）
-     data.details[].countQty: （类型: number）
-     data.details[].differenceQty: （类型: number）
-     data.details[].differenceReason: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "完成生产"
- "生产完成"

## 接口：创建成品盘点单

- **路径：** POST /api/v1/product-inventory-checks/create
- **所属模块：** 成品盘点单管理
- **HTTP 方法：** POST

**功能说明：**
创建成品盘点单

**请求参数：**
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].batchInventoryId: （类型: integer）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitName: （类型: string）
-   details[].productBatchNo: （类型: string）
-   details[].inboundDate: （类型: string）
-   details[].inventoryQty: （类型: number）
-   details[].countQty: （类型: number）
-   details[].differenceQty: （类型: number）
-   details[].differenceReason: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].batchInventoryId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].inboundDate: （类型: string）
-     data.details[].inventoryQty: （类型: number）
-     data.details[].countQty: （类型: number）
-     data.details[].differenceQty: （类型: number）
-     data.details[].differenceReason: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个成品盘点单"
- "新增成品盘点单记录"
- "创建一个新的成品盘点单"
- "帮我添加成品盘点单"

## 接口：删除成品盘点单

- **路径：** POST /api/v1/product-inventory-checks/delete/{id}
- **所属模块：** 成品盘点单管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的成品盘点单记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除成品盘点单"
- "移除某个成品盘点单"

## 接口：查询成品盘点单详情

- **路径：** GET /api/v1/product-inventory-checks/detail/{id}
- **所属模块：** 成品盘点单管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取成品盘点单管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].batchInventoryId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].inboundDate: （类型: string）
-     data.details[].inventoryQty: （类型: number）
-     data.details[].countQty: （类型: number）
-     data.details[].differenceQty: （类型: number）
-     data.details[].differenceReason: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品盘点单详情"
- "查看成品盘点单详情列表"
- "有哪些成品盘点单详情？"
- "列出成品盘点单详情"

## 接口：执行成品盘点

- **路径：** POST /api/v1/product-inventory-checks/execute
- **所属模块：** 成品盘点单管理
- **HTTP 方法：** POST

**功能说明：**
执行成品盘点

**请求参数：**
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].batchInventoryId: （类型: integer）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitName: （类型: string）
-   details[].productBatchNo: （类型: string）
-   details[].inboundDate: （类型: string）
-   details[].inventoryQty: （类型: number）
-   details[].countQty: （类型: number）
-   details[].differenceQty: （类型: number）
-   details[].differenceReason: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].batchInventoryId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].inboundDate: （类型: string）
-     data.details[].inventoryQty: （类型: number）
-     data.details[].countQty: （类型: number）
-     data.details[].differenceQty: （类型: number）
-     data.details[].differenceReason: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么执行成品盘点？"
- "执行成品盘点怎么做？"
- "帮我执行成品盘点"

## 接口：分页查询成品盘点单列表

- **路径：** POST /api/v1/product-inventory-checks/list
- **所属模块：** 成品盘点单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询成品盘点单列表列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）
- orderStatus: （类型: array<integer>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询成品盘点单列表"
- "查看成品盘点单列表列表"
- "有哪些成品盘点单列表？"
- "列出成品盘点单列表"

## 接口：更新成品盘点单

- **路径：** POST /api/v1/product-inventory-checks/update/{id}
- **所属模块：** 成品盘点单管理
- **HTTP 方法：** POST

**功能说明：**
更新成品盘点单

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].batchInventoryId: （类型: integer）
-   details[].productCode: （类型: string）
-   details[].productName: （类型: string）
-   details[].productClass: （类型: string）
-   details[].productType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitName: （类型: string）
-   details[].productBatchNo: （类型: string）
-   details[].inboundDate: （类型: string）
-   details[].inventoryQty: （类型: number）
-   details[].countQty: （类型: number）
-   details[].differenceQty: （类型: number）
-   details[].differenceReason: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].batchInventoryId: （类型: integer）
-     data.details[].productCode: （类型: string）
-     data.details[].productName: （类型: string）
-     data.details[].productClass: （类型: string）
-     data.details[].productType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitName: （类型: string）
-     data.details[].productBatchNo: （类型: string）
-     data.details[].inboundDate: （类型: string）
-     data.details[].inventoryQty: （类型: number）
-     data.details[].countQty: （类型: number）
-     data.details[].differenceQty: （类型: number）
-     data.details[].differenceReason: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改成品盘点单信息"
- "更新成品盘点单数据"
- "编辑成品盘点单"

## 接口：取消物料入库单

- **路径：** POST /api/v1/material-receivings/cancel/{id}
- **所属模块：** 物料入库单管理
- **HTTP 方法：** POST

**功能说明：**
取消物料入库单

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么取消物料入库单？"
- "取消物料入库单怎么做？"
- "帮我取消物料入库单"

## 接口：创建物料入库单

- **路径：** POST /api/v1/material-receivings/create
- **所属模块：** 物料入库单管理
- **HTTP 方法：** POST

**功能说明：**
创建物料入库单

**请求参数：**
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- supplier: （类型: string，可选）
- supplierName: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- applicationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].materialId: （类型: integer）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].materialBatchNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].applyOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.supplier: （类型: string）
-   data.supplierName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个物料入库单"
- "新增物料入库单记录"
- "创建一个新的物料入库单"
- "帮我添加物料入库单"

## 接口：删除物料入库单

- **路径：** POST /api/v1/material-receivings/delete/{id}
- **所属模块：** 物料入库单管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的物料入库单记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除物料入库单"
- "移除某个物料入库单"

## 接口：查询物料入库单详情

- **路径：** GET /api/v1/material-receivings/detail/{id}
- **所属模块：** 物料入库单管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取物料入库单管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.supplier: （类型: string）
-   data.supplierName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料入库单详情"
- "查看物料入库单详情列表"
- "有哪些物料入库单详情？"
- "列出物料入库单详情"

## 接口：执行物料入库

- **路径：** POST /api/v1/material-receivings/inbound
- **所属模块：** 物料入库单管理
- **HTTP 方法：** POST

**功能说明：**
执行物料入库

**请求参数：**
- id: （类型: integer，可选）
- finished: （类型: boolean，可选）
- details: （类型: array<object>，可选）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitName: （类型: string）
-   details[].currentQty: （类型: number）
-   details[].materialBatchNo: （类型: string）
-   details[].inboundDate: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.supplier: （类型: string）
-   data.supplierName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "怎么执行物料入库？"
- "执行物料入库怎么做？"
- "帮我执行物料入库"

## 接口：分页查询物料入库单列表

- **路径：** POST /api/v1/material-receivings/list
- **所属模块：** 物料入库单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料入库单列表列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- search: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）
- orderStatus: （类型: array<integer>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料入库单列表"
- "查看物料入库单列表列表"
- "有哪些物料入库单列表？"
- "列出物料入库单列表"

## 接口：更新物料入库单

- **路径：** POST /api/v1/material-receivings/update/{id}
- **所属模块：** 物料入库单管理
- **HTTP 方法：** POST

**功能说明：**
更新物料入库单

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- supplier: （类型: string，可选）
- supplierName: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- applicationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].materialId: （类型: integer）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].materialBatchNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].applyOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.supplier: （类型: string）
-   data.supplierName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改物料入库单信息"
- "更新物料入库单数据"
- "编辑物料入库单"

## 接口：分页查询物料入库操作日志

- **路径：** POST /api/v1/material-receiving-logs/list
- **所属模块：** 物料入库操作日志管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料入库操作日志列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- billNo: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialBatchNo: （类型: string，可选）
- operationType: （类型: integer，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料入库操作日志"
- "查看物料入库操作日志列表"
- "有哪些物料入库操作日志？"
- "列出物料入库操作日志"

## 接口：分页查询物料入库日志按月汇总

- **路径：** POST /api/v1/material-receiving-logs/monthly-summary/list
- **所属模块：** 物料入库操作日志管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料入库日志按月汇总列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- materialCode: （类型: string，可选）
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
- extraData: （类型: object）

**适用问题示例：**
- "查询物料入库日志按月汇总"
- "查看物料入库日志按月汇总列表"
- "有哪些物料入库日志按月汇总？"
- "列出物料入库日志按月汇总"
- "有多少物料？"
- "物料数量"

## 接口：创建物料入库申请单

- **路径：** POST /api/v1/material-receiving-applications/create
- **所属模块：** 物料入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
创建物料入库申请单

**请求参数：**
- id: （类型: integer，可选）
- applicationNo: （类型: string，可选）
- supplier: （类型: string，可选）
- supplierName: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- billGenStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].materialId: （类型: integer）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].materialBatchNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].applyOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.supplier: （类型: string）
-   data.supplierName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个物料入库申请单"
- "新增物料入库申请单记录"
- "创建一个新的物料入库申请单"
- "帮我添加物料入库申请单"

## 接口：由物料入库申请单生成物料入库单

- **路径：** POST /api/v1/material-receiving-applications/create-receiving
- **所属模块：** 物料入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
由物料入库申请单生成物料入库单

**请求参数：**
- applicationId: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].materialCode: （类型: string）
-   details[].planInboundQty: （类型: number）
-   details[].refuseQty: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么由物料入库申请单生成物料入库单？"
- "由物料入库申请单生成物料入库单怎么做？"
- "帮我由物料入库申请单生成物料入库单"

## 接口：删除物料入库申请单

- **路径：** POST /api/v1/material-receiving-applications/delete/{id}
- **所属模块：** 物料入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的物料入库申请单记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除物料入库申请单"
- "移除某个物料入库申请单"

## 接口：查询物料入库申请单详情

- **路径：** GET /api/v1/material-receiving-applications/detail/{id}
- **所属模块：** 物料入库申请单管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取物料入库申请单管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.supplier: （类型: string）
-   data.supplierName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料入库申请单详情"
- "查看物料入库申请单详情列表"
- "有哪些物料入库申请单详情？"
- "列出物料入库申请单详情"

## 接口：按多个ID查询物料入库申请单列表（含明细）

- **路径：** POST /api/v1/material-receiving-applications/details-by-ids
- **所属模块：** 物料入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
按多个ID查询物料入库申请单列表（含明细）

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: （类型: integer）
-   data[].applicationNo: （类型: string）
-   data[].supplier: （类型: string）
-   data[].supplierName: （类型: string）
-   data[].orderSource: （类型: string）
-   data[].relationNo: （类型: string）
-   data[].orderStatus: （类型: integer）
-   data[].billGenStatus: （类型: integer）
-   data[].creator: （类型: string）
-   data[].createTime: （类型: string）
-   data[].details: （类型: array<object>）
-     data[].details[].id: （类型: integer）
-     data[].details[].materialId: （类型: integer）
-     data[].details[].materialCode: （类型: string）
-     data[].details[].materialName: （类型: string）
-     data[].details[].materialClass: （类型: string）
-     data[].details[].materialType: （类型: string）
-     data[].details[].specModel: （类型: string）
-     data[].details[].unitId: （类型: integer）
-     data[].details[].unitName: （类型: string）
-     data[].details[].materialBatchNo: （类型: string）
-     data[].details[].applyInboundQty: （类型: number）
-     data[].details[].inboundCheck: （类型: integer）
-     data[].details[].inboundCheckId: （类型: string）
-     data[].details[].inboundQty: （类型: number）
-     data[].details[].refuseQty: （类型: number）
-     data[].details[].planInboundQty: （类型: number）
-     data[].details[].applyOutboundQty: （类型: number）
-     data[].details[].outboundQty: （类型: number）
-     data[].details[].planOutboundQty: （类型: number）
-     data[].details[].planInboundTotal: （类型: number）
-     data[].details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料入库申请单列表（含明细）"
- "查看物料入库申请单列表（含明细）列表"
- "有哪些物料入库申请单列表（含明细）？"
- "列出物料入库申请单列表（含明细）"

## 接口：分页查询物料入库申请单列表

- **路径：** POST /api/v1/material-receiving-applications/list
- **所属模块：** 物料入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料入库申请单列表列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- search: （类型: string，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料入库申请单列表"
- "查看物料入库申请单列表列表"
- "有哪些物料入库申请单列表？"
- "列出物料入库申请单列表"

## 接口：批量更新物料入库申请明细入库检查状态

- **路径：** POST /api/v1/material-receiving-applications/update-inbound-check
- **所属模块：** 物料入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
批量更新物料入库申请明细入库检查状态

**请求参数：**
- applicationId: （类型: integer，可选）
- codes: （类型: array<string>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量更新物料入库申请明细入库检查状态？"
- "批量更新物料入库申请明细入库检查状态怎么做？"
- "帮我批量更新物料入库申请明细入库检查状态"

## 接口：更新物料入库申请单

- **路径：** POST /api/v1/material-receiving-applications/update/{id}
- **所属模块：** 物料入库申请单管理
- **HTTP 方法：** POST

**功能说明：**
更新物料入库申请单

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- applicationNo: （类型: string，可选）
- supplier: （类型: string，可选）
- supplierName: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- billGenStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].materialId: （类型: integer）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].materialBatchNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].applyOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.supplier: （类型: string）
-   data.supplierName: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改物料入库申请单信息"
- "更新物料入库申请单数据"
- "编辑物料入库申请单"

## 接口：取消物料出库单

- **路径：** POST /api/v1/material-outbounds/cancel/{id}
- **所属模块：** 物料出库单管理
- **HTTP 方法：** POST

**功能说明：**
取消物料出库单

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么取消物料出库单？"
- "取消物料出库单怎么做？"
- "帮我取消物料出库单"

## 接口：创建物料出库单

- **路径：** POST /api/v1/material-outbounds/create
- **所属模块：** 物料出库单管理
- **HTTP 方法：** POST

**功能说明：**
创建物料出库单

**请求参数：**
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- applicationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- outboundApplicationId: （类型: integer，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].materialId: （类型: integer）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].materialBatchNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].applyOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.outboundApplicationId: （类型: integer）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个物料出库单"
- "新增物料出库单记录"
- "创建一个新的物料出库单"
- "帮我添加物料出库单"

## 接口：删除物料出库单

- **路径：** POST /api/v1/material-outbounds/delete/{id}
- **所属模块：** 物料出库单管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的物料出库单记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除物料出库单"
- "移除某个物料出库单"

## 接口：查询物料出库单详情

- **路径：** GET /api/v1/material-outbounds/detail/{id}
- **所属模块：** 物料出库单管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取物料出库单管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.outboundApplicationId: （类型: integer）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料出库单详情"
- "查看物料出库单详情列表"
- "有哪些物料出库单详情？"
- "列出物料出库单详情"

## 接口：分页查询物料出库单列表

- **路径：** POST /api/v1/material-outbounds/list
- **所属模块：** 物料出库单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料出库单列表列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- search: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）
- orderStatus: （类型: array<integer>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料出库单列表"
- "查看物料出库单列表列表"
- "有哪些物料出库单列表？"
- "列出物料出库单列表"

## 接口：执行物料出库

- **路径：** POST /api/v1/material-outbounds/outbound
- **所属模块：** 物料出库单管理
- **HTTP 方法：** POST

**功能说明：**
执行物料出库

**请求参数：**
- id: （类型: integer，可选）
- finished: （类型: boolean，可选）
- details: （类型: array<object>，可选）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitName: （类型: string）
-   details[].currentQty: （类型: number）
-   details[].materialBatchNo: （类型: string）
-   details[].inboundDate: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.outboundApplicationId: （类型: integer）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "怎么执行物料出库？"
- "执行物料出库怎么做？"
- "帮我执行物料出库"

## 接口：更新物料出库单

- **路径：** POST /api/v1/material-outbounds/update/{id}
- **所属模块：** 物料出库单管理
- **HTTP 方法：** POST

**功能说明：**
更新物料出库单

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- applicationNo: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- outboundApplicationId: （类型: integer，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].materialId: （类型: integer）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].materialBatchNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].applyOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.applicationNo: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.outboundApplicationId: （类型: integer）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改物料出库单信息"
- "更新物料出库单数据"
- "编辑物料出库单"

## 接口：分页查询物料出库操作日志

- **路径：** POST /api/v1/material-outbound-logs/list
- **所属模块：** 物料出库操作日志管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料出库操作日志列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- billNo: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialBatchNo: （类型: string，可选）
- operationType: （类型: integer，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料出库操作日志"
- "查看物料出库操作日志列表"
- "有哪些物料出库操作日志？"
- "列出物料出库操作日志"

## 接口：分页查询物料出库日志按月汇总

- **路径：** POST /api/v1/material-outbound-logs/monthly-summary/list
- **所属模块：** 物料出库操作日志管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料出库日志按月汇总列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- materialCode: （类型: string，可选）
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
- extraData: （类型: object）

**适用问题示例：**
- "查询物料出库日志按月汇总"
- "查看物料出库日志按月汇总列表"
- "有哪些物料出库日志按月汇总？"
- "列出物料出库日志按月汇总"
- "有多少物料？"
- "物料数量"

## 接口：创建物料出库申请单

- **路径：** POST /api/v1/material-outbound-applications/create
- **所属模块：** 物料出库申请单管理
- **HTTP 方法：** POST

**功能说明：**
创建物料出库申请单

**请求参数：**
- id: （类型: integer，可选）
- applicationNo: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- billGenStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].materialId: （类型: integer）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].materialBatchNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].applyOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个物料出库申请单"
- "新增物料出库申请单记录"
- "创建一个新的物料出库申请单"
- "帮我添加物料出库申请单"

## 接口：删除物料出库申请单

- **路径：** POST /api/v1/material-outbound-applications/delete/{id}
- **所属模块：** 物料出库申请单管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的物料出库申请单记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除物料出库申请单"
- "移除某个物料出库申请单"

## 接口：查询物料出库申请单详情

- **路径：** GET /api/v1/material-outbound-applications/detail/{id}
- **所属模块：** 物料出库申请单管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取物料出库申请单管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料出库申请单详情"
- "查看物料出库申请单详情列表"
- "有哪些物料出库申请单详情？"
- "列出物料出库申请单详情"

## 接口：分页查询物料出库申请单列表

- **路径：** POST /api/v1/material-outbound-applications/list
- **所属模块：** 物料出库申请单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料出库申请单列表列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- search: （类型: string，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料出库申请单列表"
- "查看物料出库申请单列表列表"
- "有哪些物料出库申请单列表？"
- "列出物料出库申请单列表"

## 接口：更新物料出库申请单

- **路径：** POST /api/v1/material-outbound-applications/update/{id}
- **所属模块：** 物料出库申请单管理
- **HTTP 方法：** POST

**功能说明：**
更新物料出库申请单

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- applicationNo: （类型: string，可选）
- orderSource: （类型: string，可选）
- relationNo: （类型: string，可选）
- billGenStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].materialId: （类型: integer）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitId: （类型: integer）
-   details[].unitName: （类型: string）
-   details[].materialBatchNo: （类型: string）
-   details[].applyInboundQty: （类型: number）
-   details[].inboundCheck: （类型: integer）
-   details[].inboundCheckId: （类型: string）
-   details[].inboundQty: （类型: number）
-   details[].refuseQty: （类型: number）
-   details[].planInboundQty: （类型: number）
-   details[].applyOutboundQty: （类型: number）
-   details[].outboundQty: （类型: number）
-   details[].planOutboundQty: （类型: number）
-   details[].planInboundTotal: （类型: number）
-   details[].planOutboundTotal: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.applicationNo: （类型: string）
-   data.orderSource: （类型: string）
-   data.relationNo: （类型: string）
-   data.billGenStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].materialId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitId: （类型: integer）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].applyInboundQty: （类型: number）
-     data.details[].inboundCheck: （类型: integer）
-     data.details[].inboundCheckId: （类型: string）
-     data.details[].inboundQty: （类型: number）
-     data.details[].refuseQty: （类型: number）
-     data.details[].planInboundQty: （类型: number）
-     data.details[].applyOutboundQty: （类型: number）
-     data.details[].outboundQty: （类型: number）
-     data.details[].planOutboundQty: （类型: number）
-     data.details[].planInboundTotal: （类型: number）
-     data.details[].planOutboundTotal: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改物料出库申请单信息"
- "更新物料出库申请单数据"
- "编辑物料出库申请单"

## 接口：删除物料基础信息

- **路径：** POST /api/v1/material-infos/delete/{id}
- **所属模块：** 物料基础信息管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的物料基础信息记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除物料基础信息"
- "移除某个物料基础信息"

## 接口：查询物料基础信息详情

- **路径：** GET /api/v1/material-infos/detail/{id}
- **所属模块：** 物料基础信息管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取物料基础信息管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.materialClass: （类型: string）
-   data.materialType: （类型: string）
-   data.specModel: （类型: string）
-   data.unitId: （类型: integer）
-   data.unitName: （类型: string）
-   data.minSafeQty: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料基础信息详情"
- "查看物料基础信息详情列表"
- "有哪些物料基础信息详情？"
- "列出物料基础信息详情"

## 接口：分页查询物料基础信息

- **路径：** POST /api/v1/material-infos/list
- **所属模块：** 物料基础信息管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料基础信息列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- materialClass: （类型: string，可选）
- materialType: （类型: string，可选）
- isBatchManaged: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料基础信息"
- "查看物料基础信息列表"
- "有哪些物料基础信息？"
- "列出物料基础信息"

## 接口：创建/更新物料基础信息

- **路径：** POST /api/v1/material-infos/save
- **所属模块：** 物料基础信息管理
- **HTTP 方法：** POST

**功能说明：**
创建/更新物料基础信息

**请求参数：**
- id: （类型: integer，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- materialClass: （类型: string，可选）
- materialType: （类型: string，可选）
- specModel: （类型: string，可选）
- unitId: （类型: integer，可选）
- unitName: （类型: string，可选）
- minSafeQty: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.materialClass: （类型: string）
-   data.materialType: （类型: string）
-   data.specModel: （类型: string）
-   data.unitId: （类型: integer）
-   data.unitName: （类型: string）
-   data.minSafeQty: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个/更新物料基础信息"
- "新增/更新物料基础信息记录"
- "创建一个新的/更新物料基础信息"
- "帮我添加/更新物料基础信息"

## 接口：查询物料库存详情

- **路径：** GET /api/v1/material-inventories/detail/{id}
- **所属模块：** 物料库存管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取物料库存管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.warehouseCode: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.materialClass: （类型: string）
-   data.materialType: （类型: string）
-   data.specModel: （类型: string）
-   data.unitId: （类型: integer）
-   data.unitName: （类型: string）
-   data.inventoryQty: （类型: number）
-   data.availableQty: （类型: number）
-   data.frozenQty: （类型: number）
-   data.minSafeQty: （类型: integer）
-   data.inventoryStatus: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料库存详情"
- "查看物料库存详情列表"
- "有哪些物料库存详情？"
- "列出物料库存详情"

## 接口：分页查询物料库存

- **路径：** POST /api/v1/material-inventories/list
- **所属模块：** 物料库存管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料库存列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- materialCode: （类型: string，可选）
- inventoryStatus: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料库存"
- "查看物料库存列表"
- "有哪些物料库存？"
- "列出物料库存"

## 接口：分页查询物料批次库存

- **路径：** POST /api/v1/material-batch-inventories/list
- **所属模块：** 物料批次库存管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料批次库存列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- warehouseCode: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialBatchNo: （类型: string，可选）
- excludeBatchInventoryIds: （类型: array<integer>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料批次库存"
- "查看物料批次库存列表"
- "有哪些物料批次库存？"
- "列出物料批次库存"

## 接口：根据仓库和物料编码查询物料库存及批次库存

- **路径：** GET /api/v1/material-batch-inventories/summary
- **所属模块：** 物料批次库存管理
- **HTTP 方法：** GET

**功能说明：**
根据仓库和物料编码查询物料库存及批次库存

**请求参数：**
- warehouseCode （查询参数）: （类型: string，可选）
- materialCode （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.inventories: （类型: array<object>）
-     data.inventories[].id: （类型: integer）
-     data.inventories[].warehouseCode: （类型: string）
-     data.inventories[].materialCode: （类型: string）
-     data.inventories[].materialName: （类型: string）
-     data.inventories[].materialClass: （类型: string）
-     data.inventories[].materialType: （类型: string）
-     data.inventories[].specModel: （类型: string）
-     data.inventories[].unitId: （类型: integer）
-     data.inventories[].unitName: （类型: string）
-     data.inventories[].inventoryQty: （类型: number）
-     data.inventories[].availableQty: （类型: number）
-     data.inventories[].frozenQty: （类型: number）
-     data.inventories[].minSafeQty: （类型: integer）
-     data.inventories[].inventoryStatus: （类型: integer）
-   data.batches: （类型: array<object>）
-     data.batches[].id: （类型: integer）
-     data.batches[].warehouseCode: （类型: string）
-     data.batches[].materialCode: （类型: string）
-     data.batches[].materialName: （类型: string）
-     data.batches[].materialClass: （类型: string）
-     data.batches[].materialType: （类型: string）
-     data.batches[].specModel: （类型: string）
-     data.batches[].unitId: （类型: integer）
-     data.batches[].unitName: （类型: string）
-     data.batches[].materialBatchNo: （类型: string）
-     data.batches[].inventoryQty: （类型: number）
-     data.batches[].inboundDate: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料库存及批次库存"
- "查看物料库存及批次库存列表"
- "有哪些物料库存及批次库存？"
- "列出物料库存及批次库存"

## 接口：取消物料盘点单

- **路径：** POST /api/v1/material-inventory-checks/cancel/{id}
- **所属模块：** 物料盘点单管理
- **HTTP 方法：** POST

**功能说明：**
取消物料盘点单

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么取消物料盘点单？"
- "取消物料盘点单怎么做？"
- "帮我取消物料盘点单"

## 接口：完成物料盘点

- **路径：** POST /api/v1/material-inventory-checks/complete/{id}
- **所属模块：** 物料盘点单管理
- **HTTP 方法：** POST

**功能说明：**
完成物料盘点

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].batchInventoryId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].inboundDate: （类型: string）
-     data.details[].inventoryQty: （类型: number）
-     data.details[].countQty: （类型: number）
-     data.details[].differenceQty: （类型: number）
-     data.details[].differenceReason: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "完成生产"
- "生产完成"

## 接口：创建物料盘点单

- **路径：** POST /api/v1/material-inventory-checks/create
- **所属模块：** 物料盘点单管理
- **HTTP 方法：** POST

**功能说明：**
创建物料盘点单

**请求参数：**
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].batchInventoryId: （类型: integer）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitName: （类型: string）
-   details[].materialBatchNo: （类型: string）
-   details[].inboundDate: （类型: string）
-   details[].inventoryQty: （类型: number）
-   details[].countQty: （类型: number）
-   details[].differenceQty: （类型: number）
-   details[].differenceReason: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].batchInventoryId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].inboundDate: （类型: string）
-     data.details[].inventoryQty: （类型: number）
-     data.details[].countQty: （类型: number）
-     data.details[].differenceQty: （类型: number）
-     data.details[].differenceReason: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个物料盘点单"
- "新增物料盘点单记录"
- "创建一个新的物料盘点单"
- "帮我添加物料盘点单"

## 接口：删除物料盘点单

- **路径：** POST /api/v1/material-inventory-checks/delete/{id}
- **所属模块：** 物料盘点单管理
- **HTTP 方法：** POST

**功能说明：**
用于删除指定的物料盘点单记录。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除物料盘点单"
- "移除某个物料盘点单"

## 接口：查询物料盘点单详情

- **路径：** GET /api/v1/material-inventory-checks/detail/{id}
- **所属模块：** 物料盘点单管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取物料盘点单管理中指定记录的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].batchInventoryId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].inboundDate: （类型: string）
-     data.details[].inventoryQty: （类型: number）
-     data.details[].countQty: （类型: number）
-     data.details[].differenceQty: （类型: number）
-     data.details[].differenceReason: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料盘点单详情"
- "查看物料盘点单详情列表"
- "有哪些物料盘点单详情？"
- "列出物料盘点单详情"

## 接口：执行物料盘点

- **路径：** POST /api/v1/material-inventory-checks/execute
- **所属模块：** 物料盘点单管理
- **HTTP 方法：** POST

**功能说明：**
执行物料盘点

**请求参数：**
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].batchInventoryId: （类型: integer）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitName: （类型: string）
-   details[].materialBatchNo: （类型: string）
-   details[].inboundDate: （类型: string）
-   details[].inventoryQty: （类型: number）
-   details[].countQty: （类型: number）
-   details[].differenceQty: （类型: number）
-   details[].differenceReason: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].batchInventoryId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].inboundDate: （类型: string）
-     data.details[].inventoryQty: （类型: number）
-     data.details[].countQty: （类型: number）
-     data.details[].differenceQty: （类型: number）
-     data.details[].differenceReason: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么执行物料盘点？"
- "执行物料盘点怎么做？"
- "帮我执行物料盘点"

## 接口：分页查询物料盘点单列表

- **路径：** POST /api/v1/material-inventory-checks/list
- **所属模块：** 物料盘点单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询物料盘点单列表列表，支持多维度筛选，用于列表展示和搜索。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- from: （类型: string，可选）
- to: （类型: string，可选）
- orderStatus: （类型: array<integer>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料盘点单列表"
- "查看物料盘点单列表列表"
- "有哪些物料盘点单列表？"
- "列出物料盘点单列表"

## 接口：更新物料盘点单

- **路径：** POST /api/v1/material-inventory-checks/update/{id}
- **所属模块：** 物料盘点单管理
- **HTTP 方法：** POST

**功能说明：**
更新物料盘点单

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: （类型: integer，可选）
- billNo: （类型: string，可选）
- warehouseCode: （类型: string，可选）
- orderStatus: （类型: integer，可选）
- creator: （类型: string，可选）
- createTime: （类型: string，可选）
- details: （类型: array<object>，可选）
-   details[].id: （类型: integer）
-   details[].batchInventoryId: （类型: integer）
-   details[].materialCode: （类型: string）
-   details[].materialName: （类型: string）
-   details[].materialClass: （类型: string）
-   details[].materialType: （类型: string）
-   details[].specModel: （类型: string）
-   details[].unitName: （类型: string）
-   details[].materialBatchNo: （类型: string）
-   details[].inboundDate: （类型: string）
-   details[].inventoryQty: （类型: number）
-   details[].countQty: （类型: number）
-   details[].differenceQty: （类型: number）
-   details[].differenceReason: （类型: string）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: （类型: integer）
-   data.billNo: （类型: string）
-   data.warehouseCode: （类型: string）
-   data.orderStatus: （类型: integer）
-   data.creator: （类型: string）
-   data.createTime: （类型: string）
-   data.details: （类型: array<object>）
-     data.details[].id: （类型: integer）
-     data.details[].batchInventoryId: （类型: integer）
-     data.details[].materialCode: （类型: string）
-     data.details[].materialName: （类型: string）
-     data.details[].materialClass: （类型: string）
-     data.details[].materialType: （类型: string）
-     data.details[].specModel: （类型: string）
-     data.details[].unitName: （类型: string）
-     data.details[].materialBatchNo: （类型: string）
-     data.details[].inboundDate: （类型: string）
-     data.details[].inventoryQty: （类型: number）
-     data.details[].countQty: （类型: number）
-     data.details[].differenceQty: （类型: number）
-     data.details[].differenceReason: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改物料盘点单信息"
- "更新物料盘点单数据"
- "编辑物料盘点单"
