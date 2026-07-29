# 服务：CRM Service API

## 接口：创建分享二维码

- **路径：** POST /api/qrcode/create
- **所属模块：** 分享二维码管理
- **HTTP 方法：** POST

**功能说明：**
用于向客户生成专属分享二维码，客户扫码后可进入客户H5页面，无需登录即可查看自己的反馈记录和销售订单信息。服务端通过JWT token机制完成客户身份认证和权限控制，token在设定天数后自动过期（默认7天），过期后二维码失效，需重新生成。

**请求参数：**
- customerId: 客户ID（类型: integer，必填）
- expireDays: 过期天数(默认7天)（类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: 分享二维码响应（类型: object）
-   data.id: 二维码记录ID（类型: integer）
-   data.linkUrl: 分享链接（类型: string）
-   data.token: JWT token（类型: string）
-   data.expireDays: 过期天数（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个分享二维码"
- "新增分享二维码记录"
- "创建一个新的分享二维码"
- "帮我添加分享二维码"

## 接口：创建反馈单

- **路径：** POST /api/feedback/create
- **所属模块：** 反馈单管理
- **HTTP 方法：** POST

**功能说明：**
创建反馈单，记录客户反馈的问题类型、问题描述和附件信息，用于客户投诉和建议的跟踪处理。

**请求参数：**
- customerId: 客户ID（类型: string，必填）
- problemType: 问题类别（关联字典项ID）（类型: string，必填）
- problemDescription: 问题内容（类型: string，必填）
- createrId: 创建人ID（类型: string，必填）
- attachments: 文件附件地址列表（类型: array<string>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个反馈单"
- "新增反馈单记录"
- "创建一个新的反馈单"
- "帮我添加反馈单"

## 接口：删除反馈单

- **路径：** DELETE /api/feedback/delete/{id}
- **所属模块：** 反馈单管理
- **HTTP 方法：** DELETE

**功能说明：**
删除指定反馈单记录。

**请求参数：**
- id （路径参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除反馈单"
- "移除某个反馈单"

## 接口：获取反馈单详情

- **路径：** GET /api/feedback/detail/{id}
- **所属模块：** 反馈单管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取反馈单的完整详情，包括问题描述、处理进度和处理结果。

**请求参数：**
- id （路径参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: 反馈单ID（类型: integer）
-   data.feedbackCode: 反馈单编号（类型: string）
-   data.customerId: 客户ID（类型: integer）
-   data.createName: 提交人（类型: string）
-   data.problemType: 问题类别（类型: string）
-   data.problemTypeName: 问题类别名称（类型: string）
-   data.problemDescription: 问题内容（类型: string）
-   data.processTime: 处理时间（类型: string）
-   data.workOrder: 工单编号（类型: string）
-   data.workOrderId: 工单ID（类型: integer）
-   data.createTime: 创建时间（类型: string）
-   data.attachments: 附件列表（类型: array<string>）
-   data.customerName: 客户名称（类型: string）
-   data.result: 处理结果（类型: string）
-   data.workStatus: 工单状态（类型: string）
-   data.workStatusName: 工单状态名称（类型: string）
-   data.salesOrderNo: 销售订单编号（类型: string）
-   data.producingOrderNo: 生产订单编号（类型: string）
-   data.responsiblePersonName: 处理人（类型: string）
-   data.productSn: 物料编码（类型: string）
-   data.productName: 物料名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看反馈单的详细信息"
- "反馈单详情"
- "反馈单的具体情况"

## 接口：生成反馈单编号

- **路径：** GET /api/feedback/generate-code
- **所属模块：** 反馈单管理
- **HTTP 方法：** GET

**功能说明：**
生成反馈单的唯一编号，用于反馈单的标识和跟踪。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "生成编号"
- "获取新编号"

## 接口：分页查询反馈单

- **路径：** POST /api/feedback/page
- **所属模块：** 反馈单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询反馈单列表，支持按问题类型、工单号、时间段等筛选。

**请求参数：**
- current: 反馈单分页查询DTO（类型: integer，可选）
- size: （类型: integer，可选）
- customerId: （类型: string，可选）
- search: （类型: string，可选）
- createTimeStart: （类型: string，可选）
- createTimeEnd: （类型: string，可选）
- problemType: （类型: string，可选）
- workOrder: （类型: string，可选）
- workStatus: （类型: string，可选）
- problemTypeList: （类型: array<string>，可选）
- workStatusList: （类型: array<string>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: 反馈单ID（类型: integer）
-     data.list[].feedbackCode: 反馈单编号（类型: string）
-     data.list[].customerId: 客户ID（类型: integer）
-     data.list[].creator: 提交人（类型: string）
-     data.list[].problemType: 问题类别（类型: string）
-     data.list[].problemTypeName: 问题类别名称（类型: string）
-     data.list[].problemDescription: 问题内容（类型: string）
-     data.list[].processTime: 处理时间（类型: string）
-     data.list[].workOrder: 工单编号（类型: string）
-     data.list[].createTime: 创建时间（类型: string）
-     data.list[].attachments: 附件ID列表（类型: string）
-     data.list[].customerName: 客户名称（类型: string）
-     data.list[].responsiblePerson: 处理人（类型: string）
-     data.list[].customerEvaluation: 客户评价（类型: string）
-     data.list[].result: 处理结果（类型: string）
-     data.list[].workStatus: 工单状态（类型: string）
-     data.list[].workStatusName: 工单状态名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询反馈单"
- "查看反馈单列表"
- "有哪些反馈单？"
- "列出反馈单"
- "最近一周的{thing}"
- "本月反馈单情况"

## 接口：更新反馈单

- **路径：** POST /api/feedback/update
- **所属模块：** 反馈单管理
- **HTTP 方法：** POST

**功能说明：**
更新反馈单信息，包括处理时间、处理结果等。

**请求参数：**
- id: 更新反馈单DTO（类型: string，可选）
- customerId: （类型: string，可选）
- problemType: （类型: string，可选）
- problemDescription: （类型: string，可选）
- processTime: 处理时间（类型: string，可选）
- workOrder: （类型: string，可选）
- creator: 提交人（类型: string，可选）
- result: （类型: string，可选）
- attachments: （类型: array<string>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改反馈单信息"
- "更新反馈单数据"
- "编辑反馈单"

## 接口：新增反馈

- **路径：** PUT /api/c/feedback/create
- **所属模块：** 客户H5 - 反馈管理
- **HTTP 方法：** PUT

**功能说明：**
客户H5端创建反馈，客户通过手机端提交问题反馈，无需登录系统即可操作。

**请求参数：**
- problemType: 问题类别（1=产品质量 2=交付履约 3=服务体验 4=需求建议）（类型: string，必填）
- problemDescription: 问题内容（类型: string，必填）
- attachments: 附件地址列表（类型: array<string>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个反馈"
- "新增反馈记录"
- "创建一个新的反馈"
- "帮我添加反馈"

## 接口：分页查询反馈

- **路径：** GET /api/c/feedback/list
- **所属模块：** 客户H5 - 反馈管理
- **HTTP 方法：** GET

**功能说明：**
客户H5端分页查询自己的反馈记录列表。

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: 反馈单ID（类型: integer）
-     data.list[].feedbackCode: 反馈单编号（类型: string）
-     data.list[].customerId: 客户ID（类型: integer）
-     data.list[].creator: 提交人（类型: string）
-     data.list[].problemType: 问题类别（类型: string）
-     data.list[].problemTypeName: 问题类别名称（类型: string）
-     data.list[].problemDescription: 问题内容（类型: string）
-     data.list[].processTime: 处理时间（类型: string）
-     data.list[].workOrder: 工单编号（类型: string）
-     data.list[].createTime: 创建时间（类型: string）
-     data.list[].attachments: 附件ID列表（类型: string）
-     data.list[].customerName: 客户名称（类型: string）
-     data.list[].responsiblePerson: 处理人（类型: string）
-     data.list[].customerEvaluation: 客户评价（类型: string）
-     data.list[].result: 处理结果（类型: string）
-     data.list[].workStatus: 工单状态（类型: string）
-     data.list[].workStatusName: 工单状态名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询反馈"
- "查看反馈列表"
- "有哪些反馈？"
- "列出反馈"

## 接口：修改反馈

- **路径：** POST /api/c/feedback/update
- **所属模块：** 客户H5 - 反馈管理
- **HTTP 方法：** POST

**功能说明：**
客户H5端修改已提交的反馈内容。

**请求参数：**
- id: 反馈ID（类型: integer，必填）
- problemDescription: 问题内容（类型: string，可选）
- attachments: 附件地址列表（类型: array<string>，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改反馈信息"
- "更新反馈数据"
- "编辑反馈"

## 接口：查询管理员联系方式

- **路径：** GET /api/c/contact-info
- **所属模块：** 客户H5 - 联系方式
- **HTTP 方法：** GET

**功能说明：**
客户H5端查看管理员联系方式，方便客户在有问题时联系管理人员。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查询管理员联系方式"
- "查看管理员联系方式列表"
- "有哪些管理员联系方式？"
- "列出管理员联系方式"

## 接口：订单详情

- **路径：** GET /api/c/order/detail
- **所属模块：** 客户H5 - 订单查看
- **HTTP 方法：** GET

**功能说明：**
客户H5端查看某个销售订单的详细信息。

**请求参数：**
- id （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: 主键ID（类型: integer）
-   data.createBy: 创建人ID（类型: string）
-   data.createTime: 创建时间（类型: string）
-   data.updateBy: 更新人ID（类型: string）
-   data.updateTime: 更新时间（类型: string）
-   data.isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-   data.tenantId: 租户ID（类型: string）
-   data.tenantName: 租户名称（类型: string）
-   data.ordersCode: 销售单编号（类型: string）
-   data.ordersName: 销售单名称（类型: string）
-   data.ordersDate: 订单签订日期（类型: string）
-   data.deliveryDate: 订单交付日期（类型: string）
-   data.customerId: 客户ID（类型: integer）
-   data.customerName: 客户名称（类型: string）
-   data.priority: 优先级：1-普通、2-较高、3-紧急、4-特急（类型: integer）
-   data.customerManager: 客户经理（类型: string）
-   data.salesAmount: 销售金额（类型: number）
-   data.requirementName: 需求名称（类型: string）
-   data.requirementCode: 需求Code（类型: string）
-   data.products: 订单商品列表（类型: array<object>）
-     data.products[].id: 主键ID（类型: integer）
-     data.products[].status: 状态：0-禁用，1-启用（类型: integer）
-     data.products[].createBy: 创建人ID（类型: string）
-     data.products[].createTime: 创建时间（类型: string）
-     data.products[].updateBy: 更新人ID（类型: string）
-     data.products[].updateTime: 更新时间（类型: string）
-     data.products[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-     data.products[].requirementId: 客户需求ID（类型: integer）
-     data.products[].productCode: 产品编码（类型: string）
-     data.products[].productName: 产品名称（类型: string）
-     data.products[].productType: 产品类别（类型: string）
-     data.products[].specModel: 产品型号（类型: string）
-     data.products[].productNum: 产品数量（类型: number）
-     data.products[].unitId: 单位（类型: string）
-     data.products[].productId: 产品ID（类型: integer）
-   data.ordersStatus: 订单状态（类型: integer）
-   data.deliveryCode: 发货单号（类型: string）
-   data.auditBy: 审核人用户ID（类型: string）
-   data.auditTime: 审核时间（类型: string）
-   data.statusDesc: 状态描述（类型: string）
-   data.priorityDesc: 优先级描述（类型: string）
-   data.customerManagerName: 客户经理名称（类型: string）
-   data.demandContent: 需求内容（类型: string）
-   data.productItems: 产品项列表（类型: array<object>）
-     data.productItems[].id: 主键ID（类型: integer）
-     data.productItems[].status: 状态：0-禁用，1-启用（类型: integer）
-     data.productItems[].createBy: 创建人ID（类型: string）
-     data.productItems[].createTime: 创建时间（类型: string）
-     data.productItems[].updateBy: 更新人ID（类型: string）
-     data.productItems[].updateTime: 更新时间（类型: string）
-     data.productItems[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-     data.productItems[].productType: 产品类型（类型: string）
-     data.productItems[].productCode: 产品编码（类型: string）
-     data.productItems[].productName: 产品名称（类型: string）
-     data.productItems[].productClass: 产品类别（类型: string）
-     data.productItems[].specModel: 规格型号（类型: string）
-     data.productItems[].unitId: 单位ID（类型: integer）
-     data.productItems[].extraAttr1: 扩展属性1（类型: string）
-     data.productItems[].extraAttr2: 扩展属性2（类型: string）
-     data.productItems[].productId: 物料ID（类型: integer）
-     data.productItems[].unitName: 单位名称（类型: string）
-     data.productItems[].productNum: 产品数量（类型: number）
-     data.productItems[].quantity: （类型: number）
-   data.statusEnum: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看订单的详细信息"
- "订单详情"
- "订单的具体情况"

## 接口：分页查询销售订单

- **路径：** GET /api/c/order/list
- **所属模块：** 客户H5 - 订单查看
- **HTTP 方法：** GET

**功能说明：**
客户H5端分页查询自己的销售订单列表。

**请求参数：**
- pageNum （查询参数）: （类型: integer，可选，默认: 1）
- pageSize （查询参数）: （类型: integer，可选，默认: 10）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: 主键ID（类型: integer）
-     data.list[].createBy: 创建人ID（类型: string）
-     data.list[].createTime: 创建时间（类型: string）
-     data.list[].updateBy: 更新人ID（类型: string）
-     data.list[].updateTime: 更新时间（类型: string）
-     data.list[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-     data.list[].tenantId: 租户ID（类型: string）
-     data.list[].tenantName: 租户名称（类型: string）
-     data.list[].ordersCode: 销售单编号（类型: string）
-     data.list[].ordersName: 销售单名称（类型: string）
-     data.list[].ordersDate: 订单签订日期（类型: string）
-     data.list[].deliveryDate: 订单交付日期（类型: string）
-     data.list[].customerId: 客户ID（类型: integer）
-     data.list[].customerName: 客户名称（类型: string）
-     data.list[].priority: 优先级：1-普通、2-较高、3-紧急、4-特急（类型: integer）
-     data.list[].customerManager: 客户经理（类型: string）
-     data.list[].salesAmount: 销售金额（类型: number）
-     data.list[].requirementName: 需求名称（类型: string）
-     data.list[].requirementCode: 需求Code（类型: string）
-     data.list[].products: 订单商品列表（类型: array<object>）
-       data.list[].products[].id: 主键ID（类型: integer）
-       data.list[].products[].status: 状态：0-禁用，1-启用（类型: integer）
-       data.list[].products[].createBy: 创建人ID（类型: string）
-       data.list[].products[].createTime: 创建时间（类型: string）
-       data.list[].products[].updateBy: 更新人ID（类型: string）
-       data.list[].products[].updateTime: 更新时间（类型: string）
-       data.list[].products[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-       data.list[].products[].requirementId: 客户需求ID（类型: integer）
-       data.list[].products[].productCode: 产品编码（类型: string）
-       data.list[].products[].productName: 产品名称（类型: string）
-       data.list[].products[].productType: 产品类别（类型: string）
-       data.list[].products[].specModel: 产品型号（类型: string）
-       data.list[].products[].productNum: 产品数量（类型: number）
-       data.list[].products[].unitId: 单位（类型: string）
-       data.list[].products[].productId: 产品ID（类型: integer）
-     data.list[].ordersStatus: 订单状态（类型: integer）
-     data.list[].deliveryCode: 发货单号（类型: string）
-     data.list[].auditBy: 审核人用户ID（类型: string）
-     data.list[].auditTime: 审核时间（类型: string）
-     data.list[].statusDesc: 状态描述（类型: string）
-     data.list[].priorityDesc: 优先级描述（类型: string）
-     data.list[].customerManagerName: 客户经理名称（类型: string）
-     data.list[].demandContent: 需求内容（类型: string）
-     data.list[].productItems: 产品项列表（类型: array<object>）
-       data.list[].productItems[].id: 主键ID（类型: integer）
-       data.list[].productItems[].status: 状态：0-禁用，1-启用（类型: integer）
-       data.list[].productItems[].createBy: 创建人ID（类型: string）
-       data.list[].productItems[].createTime: 创建时间（类型: string）
-       data.list[].productItems[].updateBy: 更新人ID（类型: string）
-       data.list[].productItems[].updateTime: 更新时间（类型: string）
-       data.list[].productItems[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-       data.list[].productItems[].productType: 产品类型（类型: string）
-       data.list[].productItems[].productCode: 产品编码（类型: string）
-       data.list[].productItems[].productName: 产品名称（类型: string）
-       data.list[].productItems[].productClass: 产品类别（类型: string）
-       data.list[].productItems[].specModel: 规格型号（类型: string）
-       data.list[].productItems[].unitId: 单位ID（类型: integer）
-       data.list[].productItems[].extraAttr1: 扩展属性1（类型: string）
-       data.list[].productItems[].extraAttr2: 扩展属性2（类型: string）
-       data.list[].productItems[].productId: 物料ID（类型: integer）
-       data.list[].productItems[].unitName: 单位名称（类型: string）
-       data.list[].productItems[].productNum: 产品数量（类型: number）
-       data.list[].productItems[].quantity: （类型: number）
-     data.list[].statusEnum: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询销售订单"
- "查看销售订单列表"
- "有哪些销售订单？"
- "列出销售订单"

## 接口：创建客户

- **路径：** POST /api/customer/create
- **所属模块：** 客户管理
- **HTTP 方法：** POST

**功能说明：**
创建新客户档案，记录客户基本信息（名称、状态、级别、行业）、联系信息（联系人、电话、邮箱）及财务信息（发票抬头、税号、银行账户等）。

**请求参数：**
- customerName: 创建客户请求数据（类型: string，必填）
- customerCode: （类型: string，可选）
- customerStatus: （类型: string，必填）
- customerLevel: （类型: string，必填）
- customerIndustry: （类型: string，可选）
- organizationId: （类型: string，必填）
- customerManager: （类型: string，必填）
- remark: （类型: string，可选）
- regionCode: （类型: string，可选）
- address: （类型: string，可选）
- contactPerson: （类型: string，可选）
- contactPhone: （类型: string，可选）
- email: （类型: string，可选）
- invoiceTitle: （类型: string，可选）
- taxId: （类型: string，可选）
- bankName: （类型: string，可选）
- bankAccount: （类型: string，可选）
- companyAddress: （类型: string，可选）
- companyPhone: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个客户"
- "新增客户记录"
- "创建一个新的客户"
- "帮我添加客户"

## 接口：删除客户

- **路径：** POST /api/customer/delete/{id}
- **所属模块：** 客户管理
- **HTTP 方法：** POST

**功能说明：**
逻辑删除指定客户，将客户标记为无效状态，不会物理删除数据库记录。

**请求参数：**
- id （路径参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除客户"
- "移除某个客户"

## 接口：获取客户详情

- **路径：** GET /api/customer/detail
- **所属模块：** 客户管理
- **HTTP 方法：** GET

**功能说明：**
根据客户ID获取客户完整详情信息，包括基本信息、联系信息、财务信息、区域信息等。

**请求参数：**
- id （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: 客户ID（类型: integer）
-   data.customerName: 客户名称（类型: string）
-   data.customerCode: 客户编码（类型: string）
-   data.customerStatus: 客户状态（类型: string）
-   data.customerStatusName: 客户状态名称（类型: string）
-   data.customerLevel: 客户级别（类型: string）
-   data.customerLevelName: 客户级别名称（类型: string）
-   data.customerIndustry: 客户行业（类型: string）
-   data.customerIndustryName: 客户行业名称（类型: string）
-   data.organizationId: 所属组织ID（类型: string）
-   data.organizationName: 所属组织名称（类型: string）
-   data.accountManagerId: 客户经理ID（类型: string）
-   data.accountManagerName: 客户经理名称（类型: string）
-   data.remark: 备注信息（类型: string）
-   data.regionCode: 客户地区编码（类型: string）
-   data.regionInfo: （类型: object）
-     data.regionInfo.code: （类型: string）
-     data.regionInfo.name: （类型: string）
-     data.regionInfo.parentCode: （类型: string）
-     data.regionInfo.level: （类型: integer）
-   data.detailedAddress: 详细地址（类型: string）
-   data.contactPerson: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.email: 电子邮箱（类型: string）
-   data.invoiceTitle: 发票抬头（类型: string）
-   data.taxId: 纳税人识别号（类型: string）
-   data.bankName: 开户行（类型: string）
-   data.bankAccount: 银行账号（类型: string）
-   data.companyAddress: 公司地址（类型: string）
-   data.companyPhone: 公司电话（类型: string）
-   data.createTime: 创建时间（类型: string）
-   data.updateTime: 更新时间（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看客户的详细信息"
- "客户详情"
- "客户的具体情况"

## 接口：编辑客户

- **路径：** POST /api/customer/edit
- **所属模块：** 客户管理
- **HTTP 方法：** POST

**功能说明：**
编辑修改已有客户档案信息，更新客户基本信息或联系信息等字段。

**请求参数：**
- customerName: 创建客户请求数据（类型: string，必填）
- customerCode: （类型: string，可选）
- customerStatus: （类型: string，必填）
- customerLevel: （类型: string，必填）
- customerIndustry: （类型: string，可选）
- organizationId: （类型: string，必填）
- customerManager: （类型: string，必填）
- remark: （类型: string，可选）
- regionCode: （类型: string，可选）
- address: （类型: string，可选）
- contactPerson: （类型: string，可选）
- contactPhone: （类型: string，可选）
- email: （类型: string，可选）
- invoiceTitle: （类型: string，可选）
- taxId: （类型: string，可选）
- bankName: （类型: string，可选）
- bankAccount: （类型: string，可选）
- companyAddress: （类型: string，可选）
- companyPhone: （类型: string，可选）
- id: 更新客户DTO（类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改客户信息"
- "更新客户数据"
- "编辑客户"

## 接口：分页查询客户列表

- **路径：** POST /api/customer/page
- **所属模块：** 客户管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询客户列表，支持按客户名称、编码、状态、级别等多维度筛选。

**请求参数：**
- pageNum: （类型: integer，可选）
- pageSize: （类型: integer，可选）
- customerName: 客户分页查询请求数据（类型: string，可选）
- customerCode: （类型: string，可选）
- customerStatus: （类型: string，可选）
- customerLevel: （类型: string，可选）
- customerIndustry: （类型: string，可选）
- organizationId: （类型: string，可选）
- customerManager: （类型: string，可选）
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
-     data.list[].id: 客户ID（类型: string）
-     data.list[].customerName: 客户名称（类型: string）
-     data.list[].customerCode: 客户编码（类型: string）
-     data.list[].customerStatus: 客户状态（类型: string）
-     data.list[].customerStatusName: 客户状态名称（类型: string）
-     data.list[].customerLevel: 客户级别（类型: string）
-     data.list[].customerLevelName: 客户级别名称（类型: string）
-     data.list[].customerIndustry: 客户行业（类型: string）
-     data.list[].customerIndustryName: 客户行业名称（类型: string）
-     data.list[].organizationName: 所属组织名称（类型: string）
-     data.list[].customerManager: 客户经理名称（类型: string）
-     data.list[].contactPerson: 联系人（类型: string）
-     data.list[].contactPhone: 联系电话（类型: string）
-     data.list[].createTime: 创建时间（类型: string）
-     data.list[].remark: 备注（类型: string）
-     data.list[].longId: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询客户列表"
- "查看客户列表列表"
- "有哪些客户列表？"
- "列出客户列表"
- "哪些{thing}还未完成？"
- "客户列表进展如何？"

## 接口：快速模糊查询客户列表

- **路径：** POST /api/customer/quick-search
- **所属模块：** 客户管理
- **HTTP 方法：** POST

**功能说明：**
基于MySQL全文索引的客户快速搜索，输入关键字即可模糊匹配客户名称，按相关度排序返回最匹配的结果（默认前20个），支持百万级数据量的毫秒级搜索。

**请求参数：**
- keyword: 搜索关键字（客户名称），支持模糊匹配，最少1个字符（类型: string，必填）
- limit: 返回数量限制，默认20，最大50（类型: integer，可选）
- organizationId: 所属组织ID（可选）（类型: string，可选）
- effectiveLimit: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: 客户ID（类型: string）
-   data[].customerName: 客户名称（类型: string）
-   data[].customerCode: 客户编码（类型: string）
-   data[].customerStatus: 客户状态（类型: string）
-   data[].customerStatusName: 客户状态名称（类型: string）
-   data[].customerLevel: 客户级别（类型: string）
-   data[].customerLevelName: 客户级别名称（类型: string）
-   data[].contactPerson: 联系人（类型: string）
-   data[].contactPhone: 联系电话（类型: string）
-   data[].relevanceScore: 匹配相关度得分（仅全文搜索时有效）（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询客户列表"
- "查看客户列表列表"
- "有哪些客户列表？"
- "列出客户列表"

## 接口：创建客户需求

- **路径：** POST /api/requirement/create
- **所属模块：** 客户需求管理
- **HTTP 方法：** POST

**功能说明：**
创建客户需求记录，记录客户提出的产品需求、销售阶段、赢单概率、需求金额及期望日期等信息，用于商机跟踪和管理。

**请求参数：**
- customerId: 客户ID（类型: string，必填）
- demandContent: （类型: string，必填）
- salesStage: （类型: string，可选）
- winRate: （类型: number，可选）
- reason: （类型: string，可选）
- customerManager: （类型: string，可选）
- submitTime: （类型: string，可选）
- demandAmount: （类型: number，可选）
- expectedDate: （类型: string，可选）
- salesOrderNo: （类型: string，可选）
- productItemVOs: （类型: array<object>，可选）
-   productItemVOs[].id: 主键ID（类型: integer）
-   productItemVOs[].status: 状态：0-禁用，1-启用（类型: integer）
-   productItemVOs[].createBy: 创建人ID（类型: string）
-   productItemVOs[].createTime: 创建时间（类型: string）
-   productItemVOs[].updateBy: 更新人ID（类型: string）
-   productItemVOs[].updateTime: 更新时间（类型: string）
-   productItemVOs[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-   productItemVOs[].productType: 产品类型（类型: string）
-   productItemVOs[].productCode: 产品编码（类型: string）
-   productItemVOs[].productName: 产品名称（类型: string）
-   productItemVOs[].productClass: 产品类别（类型: string）
-   productItemVOs[].specModel: 规格型号（类型: string）
-   productItemVOs[].unitId: 单位ID（类型: integer）
-   productItemVOs[].extraAttr1: 扩展属性1（类型: string）
-   productItemVOs[].extraAttr2: 扩展属性2（类型: string）
-   productItemVOs[].productId: 物料ID（类型: integer）
-   productItemVOs[].unitName: 单位名称（类型: string）
-   productItemVOs[].productNum: 产品数量（类型: number）
-   productItemVOs[].quantity: （类型: number）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个客户需求"
- "新增客户需求记录"
- "创建一个新的客户需求"
- "帮我添加客户需求"

## 接口：删除客户需求

- **路径：** POST /api/requirement/delete
- **所属模块：** 客户需求管理
- **HTTP 方法：** POST

**功能说明：**
删除指定客户需求记录。

**请求参数：**
- id （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除客户需求"
- "移除某个客户需求"

## 接口：编辑客户需求

- **路径：** POST /api/requirement/edit
- **所属模块：** 客户需求管理
- **HTTP 方法：** POST

**功能说明：**
编辑修改已有客户需求信息，更新需求内容、销售阶段、金额等字段。

**请求参数：**
- customerId: 客户ID（类型: string，必填）
- demandContent: （类型: string，必填）
- salesStage: （类型: string，可选）
- winRate: （类型: number，可选）
- reason: （类型: string，可选）
- customerManager: （类型: string，可选）
- submitTime: （类型: string，可选）
- demandAmount: （类型: number，可选）
- expectedDate: （类型: string，可选）
- salesOrderNo: （类型: string，可选）
- productItemVOs: （类型: array<object>，可选）
-   productItemVOs[].id: 主键ID（类型: integer）
-   productItemVOs[].status: 状态：0-禁用，1-启用（类型: integer）
-   productItemVOs[].createBy: 创建人ID（类型: string）
-   productItemVOs[].createTime: 创建时间（类型: string）
-   productItemVOs[].updateBy: 更新人ID（类型: string）
-   productItemVOs[].updateTime: 更新时间（类型: string）
-   productItemVOs[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-   productItemVOs[].productType: 产品类型（类型: string）
-   productItemVOs[].productCode: 产品编码（类型: string）
-   productItemVOs[].productName: 产品名称（类型: string）
-   productItemVOs[].productClass: 产品类别（类型: string）
-   productItemVOs[].specModel: 规格型号（类型: string）
-   productItemVOs[].unitId: 单位ID（类型: integer）
-   productItemVOs[].extraAttr1: 扩展属性1（类型: string）
-   productItemVOs[].extraAttr2: 扩展属性2（类型: string）
-   productItemVOs[].productId: 物料ID（类型: integer）
-   productItemVOs[].unitName: 单位名称（类型: string）
-   productItemVOs[].productNum: 产品数量（类型: number）
-   productItemVOs[].quantity: （类型: number）
- id: 更新客户需求DTO（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改客户需求信息"
- "更新客户需求数据"
- "编辑客户需求"

## 接口：分页查询客户需求列表

- **路径：** GET /api/requirement/page
- **所属模块：** 客户需求管理
- **HTTP 方法：** GET

**功能说明：**
按条件分页查询客户需求列表，支持按客户名称、销售阶段、客户经理等多维度筛选。

**请求参数：**
- current （查询参数）: （类型: integer，可选，默认: 1）
- size （查询参数）: （类型: integer，可选，默认: 10）
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: 主键ID（类型: integer）
-     data.list[].customerId: 客户ID（类型: string）
-     data.list[].requirementCode: 需求编号（类型: string）
-     data.list[].customerName: 客户名称（类型: string）
-     data.list[].demandContent: 需求内容（类型: string）
-     data.list[].salesStage: 销售阶段（类型: string）
-     data.list[].winRate: 赢单率（类型: number）
-     data.list[].customerManager: 客户经理ID（类型: string）
-     data.list[].customerManagerName: 客户经理名称（类型: string）
-     data.list[].submitTime: 提交时间（类型: string）
-     data.list[].demandAmount: 需求金额（类型: number）
-     data.list[].expectedDate: 预计日期（类型: string）
-     data.list[].salesOrderNo: 销售订单号（类型: string）
-     data.list[].salesStageName: 销售阶段名称（类型: string）
-     data.list[].reason: 原因（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询客户需求列表"
- "查看客户需求列表列表"
- "有哪些客户需求列表？"
- "列出客户需求列表"

## 接口：获取客户需求详情

- **路径：** GET /api/requirement/{id}
- **所属模块：** 客户需求管理
- **HTTP 方法：** GET

**功能说明：**
根据ID获取客户需求的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: 主键ID（类型: integer）
-   data.customerId: 客户ID（类型: integer）
-   data.requirementCode: 需求编号（类型: string）
-   data.customerName: 客户名称（类型: string）
-   data.demandContent: 需求内容（类型: string）
-   data.salesStage: 销售阶段（类型: string）
-   data.salesStageName: 销售阶段名称（类型: string）
-   data.reason: 原因（类型: string）
-   data.winRate: 赢单率（类型: number）
-   data.customerManager: 客户经理ID（类型: string）
-   data.customerManagerName: 客户经理名称（类型: string）
-   data.submitTime: 提交时间（类型: string）
-   data.demandAmount: 需求金额（类型: number）
-   data.expectedDate: 预计日期（类型: string）
-   data.salesOrderNo: 销售订单号（类型: string）
-   data.productItemVOs: 产品项集合（类型: array<object>）
-     data.productItemVOs[].id: 主键ID（类型: integer）
-     data.productItemVOs[].status: 状态：0-禁用，1-启用（类型: integer）
-     data.productItemVOs[].createBy: 创建人ID（类型: string）
-     data.productItemVOs[].createTime: 创建时间（类型: string）
-     data.productItemVOs[].updateBy: 更新人ID（类型: string）
-     data.productItemVOs[].updateTime: 更新时间（类型: string）
-     data.productItemVOs[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-     data.productItemVOs[].productType: 产品类型（类型: string）
-     data.productItemVOs[].productCode: 产品编码（类型: string）
-     data.productItemVOs[].productName: 产品名称（类型: string）
-     data.productItemVOs[].productClass: 产品类别（类型: string）
-     data.productItemVOs[].specModel: 规格型号（类型: string）
-     data.productItemVOs[].unitId: 单位ID（类型: integer）
-     data.productItemVOs[].extraAttr1: 扩展属性1（类型: string）
-     data.productItemVOs[].extraAttr2: 扩展属性2（类型: string）
-     data.productItemVOs[].productId: 物料ID（类型: integer）
-     data.productItemVOs[].unitName: 单位名称（类型: string）
-     data.productItemVOs[].productNum: 产品数量（类型: number）
-     data.productItemVOs[].quantity: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看客户需求的详细信息"
- "客户需求详情"
- "客户需求的具体情况"

## 接口：手动触发日统计

- **路径：** POST /api/report/calculate-daily
- **所属模块：** 报表统计
- **HTTP 方法：** POST

**功能说明：**
手动触发日销售统计的计算和更新。

**请求参数：**
- calcDate （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看手动触发日统计"
- "销售额怎么样？"
- "最近销售情况如何？"
- "销售额有多少？"

## 接口：手动触发销售排名统计

- **路径：** POST /api/report/calculate-order-rank
- **所属模块：** 报表统计
- **HTTP 方法：** POST

**功能说明：**
手动触发客户销售排名统计的计算和更新。

**请求参数：**
- calcDate （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看手动触发销售排名统计"
- "有多少个客户？"
- "当前客户数量"
- "客户总数"
- "客户情况怎么样？"
- "销售额怎么样？"

## 接口：手动触发RFM报表统计

- **路径：** POST /api/report/calculate-rfm
- **所属模块：** 报表统计
- **HTTP 方法：** POST

**功能说明：**
手动触发RFM报表统计的计算和更新。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看手动触发RFM报表统计"
- "怎么手动触发RFM报表统计？"
- "手动触发RFM报表统计怎么做？"
- "帮我手动触发RFM报表统计"

## 接口：手动触发汇总统计

- **路径：** POST /api/report/calculate-summaries
- **所属模块：** 报表统计
- **HTTP 方法：** POST

**功能说明：**
手动触发销售汇总统计的计算和更新。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看手动触发汇总统计"
- "销售额怎么样？"
- "最近销售情况如何？"
- "销售额有多少？"
- "当前汇总数据"
- "最新的汇总统计"

## 接口：查询客户销售排名

- **路径：** GET /api/report/customer-rank
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询自定义时间范围内的客户销售排名。

**请求参数：**
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）
- limit （查询参数）: （类型: integer，可选，默认: 10）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].statDate: 统计日期（类型: string）
-   data[].customerId: 客户ID（类型: integer）
-   data[].customerName: 客户名称（类型: string）
-   data[].dailyAmount: 当日金额（类型: number）
-   data[].orderCount: 订单数量（类型: integer）
-   data[].monthlyAmount: 当月金额（类型: number）
-   data[].yearlyAmount: 当年金额（类型: number）
-   data[].rankNum: 排名（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询客户销售排名"
- "查看客户销售排名列表"
- "有哪些客户销售排名？"
- "列出客户销售排名"
- "有多少个客户？"
- "当前客户数量"

## 接口：查询最新客户RFM报表

- **路径：** GET /api/report/customer-rfm/latest
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询最新的客户RFM分析报表，包括最近消费时间、消费频率和消费金额三个维度。

**请求参数：**
- statisticCycle （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: 主键ID（类型: integer）
-   data[].status: 状态：0-禁用，1-启用（类型: integer）
-   data[].createBy: 创建人ID（类型: string）
-   data[].createTime: 创建时间（类型: string）
-   data[].updateBy: 更新人ID（类型: string）
-   data[].updateTime: 更新时间（类型: string）
-   data[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-   data[].customerId: 客户ID（类型: integer）
-   data[].customerName: 客户名称（类型: string）
-   data[].recencyDays: 最近购买天数（类型: integer）
-   data[].frequencyCount: 购买频次（类型: integer）
-   data[].monetaryAmount: 购买金额（类型: number）
-   data[].statisticCycle: 统计周期（类型: string）
-   data[].statisticDate: 统计日期（类型: string）
-   data[].tenantId: 租户ID（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询最新客户RFM报表"
- "查看最新客户RFM报表列表"
- "有哪些最新客户RFM报表？"
- "列出最新客户RFM报表"
- "最近一周的{thing}"
- "本月最新客户RFM报表情况"

## 接口：查询最新客户状态统计报表

- **路径：** GET /api/report/customer-status/latest
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询最新的客户状态统计报表，包括客户总数、潜在客户、成交客户分布等。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: 主键ID（类型: integer）
-   data.status: 状态：0-禁用，1-启用（类型: integer）
-   data.createBy: 创建人ID（类型: string）
-   data.createTime: 创建时间（类型: string）
-   data.updateBy: 更新人ID（类型: string）
-   data.updateTime: 更新时间（类型: string）
-   data.isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-   data.tenantId: 租户ID（类型: string）
-   data.tenantName: 租户名称（类型: string）
-   data.statDate: 统计日期（类型: string）
-   data.totalCount: 客户总数（类型: integer）
-   data.potentialCount: 潜在客户数（类型: integer）
-   data.dealCount: 成交客户数（类型: integer）
-   data.invalidCount: 无效客户数（类型: integer）
-   data.levelDistribution: 客户级别分布（类型: array<object>）
-     data.levelDistribution[].status_name: （类型: string）
-     data.levelDistribution[].count: （类型: number）
-   data.regionDistribution: 客户地域分布（类型: array<object>）
-     data.regionDistribution[].status_name: （类型: string）
-     data.regionDistribution[].count: （类型: number）
-   data.industryDistribution: 客户行业分布（类型: array<object>）
-     data.industryDistribution[].status_name: （类型: string）
-     data.industryDistribution[].count: （类型: number）
-   data.conversionFunnel: 客户转化漏斗（类型: array<object>）
-     data.conversionFunnel[].status_name: （类型: string）
-     data.conversionFunnel[].count: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询最新客户状态统计报表"
- "查看最新客户状态统计报表列表"
- "有哪些最新客户状态统计报表？"
- "列出最新客户状态统计报表"
- "有多少个客户？"
- "当前客户数量"

## 接口：查询日销售统计

- **路径：** GET /api/report/daily-stats
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
按日查询销售统计数据，支持按时间范围和客户筛选。

**请求参数：**
- current （查询参数）: （类型: integer，可选，默认: 1）
- size （查询参数）: （类型: integer，可选，默认: 10）
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）
- customerId （查询参数）: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].statDate: 统计日期（类型: string）
-     data.list[].customerId: 客户ID（类型: integer）
-     data.list[].customerName: 客户名称（类型: string）
-     data.list[].dailyAmount: 当日金额（类型: number）
-     data.list[].orderCount: 订单数量（类型: integer）
-     data.list[].monthlyAmount: 当月金额（类型: number）
-     data.list[].yearlyAmount: 当年金额（类型: number）
-     data.list[].rankNum: 排名（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询日销售统计"
- "查看日销售统计列表"
- "有哪些日销售统计？"
- "列出日销售统计"
- "有多少个客户？"
- "当前客户数量"

## 接口：查询月销售统计

- **路径：** GET /api/report/monthly-stats
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
按月查询销售统计数据，支持按年月和客户筛选。

**请求参数：**
- current （查询参数）: （类型: integer，可选，默认: 1）
- size （查询参数）: （类型: integer，可选，默认: 10）
- yearMonth （查询参数）: （类型: string，可选）
- customerId （查询参数）: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].yearMonth: 统计年月（类型: string）
-     data.list[].customerId: 客户ID（类型: integer）
-     data.list[].customerName: 客户名称（类型: string）
-     data.list[].monthlyAmount: 当月金额（类型: number）
-     data.list[].orderCount: 订单数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询月销售统计"
- "查看月销售统计列表"
- "有哪些月销售统计？"
- "列出月销售统计"
- "有多少个客户？"
- "当前客户数量"

## 接口：查询本月客户排名

- **路径：** GET /api/report/rank/current-month
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询本月客户销售额排名，支持限制返回数量。

**请求参数：**
- limit （查询参数）: （类型: integer，可选，默认: 10）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].statDate: 统计日期（类型: string）
-   data[].customerId: 客户ID（类型: integer）
-   data[].customerName: 客户名称（类型: string）
-   data[].dailyAmount: 当日金额（类型: number）
-   data[].orderCount: 订单数量（类型: integer）
-   data[].monthlyAmount: 当月金额（类型: number）
-   data[].yearlyAmount: 当年金额（类型: number）
-   data[].rankNum: 排名（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询本月客户排名"
- "查看本月客户排名列表"
- "有哪些本月客户排名？"
- "列出本月客户排名"
- "有多少个客户？"
- "当前客户数量"

## 接口：查询上月客户排名

- **路径：** GET /api/report/rank/last-month
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询上月客户销售额排名。

**请求参数：**
- limit （查询参数）: （类型: integer，可选，默认: 10）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].statDate: 统计日期（类型: string）
-   data[].customerId: 客户ID（类型: integer）
-   data[].customerName: 客户名称（类型: string）
-   data[].dailyAmount: 当日金额（类型: number）
-   data[].orderCount: 订单数量（类型: integer）
-   data[].monthlyAmount: 当月金额（类型: number）
-   data[].yearlyAmount: 当年金额（类型: number）
-   data[].rankNum: 排名（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询上月客户排名"
- "查看上月客户排名列表"
- "有哪些上月客户排名？"
- "列出上月客户排名"
- "有多少个客户？"
- "当前客户数量"

## 接口：查询销售统计（带趋势）

- **路径：** GET /api/report/sales-statistics
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询指定类型的销售统计数据（本月/上月/本年/去年/自定义），含趋势数据。

**请求参数：**
- type （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.statisticsType: 统计类型：CURRENT_MONTH-本月、LAST_MONTH-上月、CURRENT_YEAR-本年、LAST_YEAR-去年、CUSTOM-自定义（类型: string）
-   data.totalAmount: 销售订单总金额（类型: number）
-   data.customerCount: 签单客户数（类型: integer）
-   data.orderCount: 销售订单签署数量（类型: integer）
-   data.startDate: 统计开始日期（类型: string）
-   data.endDate: 统计结束日期（类型: string）
-   data.trendList: 销售趋势数据（类型: array<object>）
-     data.trendList[].yearMonth: 年月（格式：yyyy-MM）（类型: string）
-     data.trendList[].salesAmount: 销售金额（类型: number）
-     data.trendList[].orderCount: 订单数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询销售统计（带趋势）"
- "查看销售统计（带趋势）列表"
- "有哪些销售统计（带趋势）？"
- "列出销售统计（带趋势）"
- "最近一周的{thing}"
- "本月销售统计（带趋势）情况"

## 接口：查询本月销售统计

- **路径：** GET /api/report/sales-statistics/current-month
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询本月销售统计

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.statisticsType: 统计类型：CURRENT_MONTH-本月、LAST_MONTH-上月、CURRENT_YEAR-本年、LAST_YEAR-去年、CUSTOM-自定义（类型: string）
-   data.totalAmount: 销售订单总金额（类型: number）
-   data.customerCount: 签单客户数（类型: integer）
-   data.orderCount: 销售订单签署数量（类型: integer）
-   data.startDate: 统计开始日期（类型: string）
-   data.endDate: 统计结束日期（类型: string）
-   data.trendList: 销售趋势数据（类型: array<object>）
-     data.trendList[].yearMonth: 年月（格式：yyyy-MM）（类型: string）
-     data.trendList[].salesAmount: 销售金额（类型: number）
-     data.trendList[].orderCount: 订单数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询本月销售统计"
- "查看本月销售统计列表"
- "有哪些本月销售统计？"
- "列出本月销售统计"

## 接口：查询本年销售统计

- **路径：** GET /api/report/sales-statistics/current-year
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询本年销售统计

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.statisticsType: 统计类型：CURRENT_MONTH-本月、LAST_MONTH-上月、CURRENT_YEAR-本年、LAST_YEAR-去年、CUSTOM-自定义（类型: string）
-   data.totalAmount: 销售订单总金额（类型: number）
-   data.customerCount: 签单客户数（类型: integer）
-   data.orderCount: 销售订单签署数量（类型: integer）
-   data.startDate: 统计开始日期（类型: string）
-   data.endDate: 统计结束日期（类型: string）
-   data.trendList: 销售趋势数据（类型: array<object>）
-     data.trendList[].yearMonth: 年月（格式：yyyy-MM）（类型: string）
-     data.trendList[].salesAmount: 销售金额（类型: number）
-     data.trendList[].orderCount: 订单数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询本年销售统计"
- "查看本年销售统计列表"
- "有哪些本年销售统计？"
- "列出本年销售统计"

## 接口：查询自定义时间范围销售统计

- **路径：** GET /api/report/sales-statistics/custom
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询自定义时间范围销售统计

**请求参数：**
- startDate （查询参数）: （类型: string，必填）
- endDate （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.statisticsType: 统计类型：CURRENT_MONTH-本月、LAST_MONTH-上月、CURRENT_YEAR-本年、LAST_YEAR-去年、CUSTOM-自定义（类型: string）
-   data.totalAmount: 销售订单总金额（类型: number）
-   data.customerCount: 签单客户数（类型: integer）
-   data.orderCount: 销售订单签署数量（类型: integer）
-   data.startDate: 统计开始日期（类型: string）
-   data.endDate: 统计结束日期（类型: string）
-   data.trendList: 销售趋势数据（类型: array<object>）
-     data.trendList[].yearMonth: 年月（格式：yyyy-MM）（类型: string）
-     data.trendList[].salesAmount: 销售金额（类型: number）
-     data.trendList[].orderCount: 订单数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询自定义时间范围销售统计"
- "查看自定义时间范围销售统计列表"
- "有哪些自定义时间范围销售统计？"
- "列出自定义时间范围销售统计"
- "最近一周的{thing}"
- "本月自定义时间范围销售统计情况"

## 接口：查询上月销售统计

- **路径：** GET /api/report/sales-statistics/last-month
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询上月销售统计

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.statisticsType: 统计类型：CURRENT_MONTH-本月、LAST_MONTH-上月、CURRENT_YEAR-本年、LAST_YEAR-去年、CUSTOM-自定义（类型: string）
-   data.totalAmount: 销售订单总金额（类型: number）
-   data.customerCount: 签单客户数（类型: integer）
-   data.orderCount: 销售订单签署数量（类型: integer）
-   data.startDate: 统计开始日期（类型: string）
-   data.endDate: 统计结束日期（类型: string）
-   data.trendList: 销售趋势数据（类型: array<object>）
-     data.trendList[].yearMonth: 年月（格式：yyyy-MM）（类型: string）
-     data.trendList[].salesAmount: 销售金额（类型: number）
-     data.trendList[].orderCount: 订单数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询上月销售统计"
- "查看上月销售统计列表"
- "有哪些上月销售统计？"
- "列出上月销售统计"

## 接口：查询去年销售统计

- **路径：** GET /api/report/sales-statistics/last-year
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询去年销售统计

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.statisticsType: 统计类型：CURRENT_MONTH-本月、LAST_MONTH-上月、CURRENT_YEAR-本年、LAST_YEAR-去年、CUSTOM-自定义（类型: string）
-   data.totalAmount: 销售订单总金额（类型: number）
-   data.customerCount: 签单客户数（类型: integer）
-   data.orderCount: 销售订单签署数量（类型: integer）
-   data.startDate: 统计开始日期（类型: string）
-   data.endDate: 统计结束日期（类型: string）
-   data.trendList: 销售趋势数据（类型: array<object>）
-     data.trendList[].yearMonth: 年月（格式：yyyy-MM）（类型: string）
-     data.trendList[].salesAmount: 销售金额（类型: number）
-     data.trendList[].orderCount: 订单数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询去年销售统计"
- "查看去年销售统计列表"
- "有哪些去年销售统计？"
- "列出去年销售统计"

## 接口：查询销售趋势（所有类型）

- **路径：** GET /api/report/sales-trend
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询所有类型的销售趋势数据，包括销售额和订单数的时间序列。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].statisticsType: 统计类型：CURRENT_MONTH-本月、LAST_MONTH-上月、CURRENT_YEAR-本年、LAST_YEAR-去年、CUSTOM-自定义（类型: string）
-   data[].totalAmount: 销售订单总金额（类型: number）
-   data[].customerCount: 签单客户数（类型: integer）
-   data[].orderCount: 销售订单签署数量（类型: integer）
-   data[].startDate: 统计开始日期（类型: string）
-   data[].endDate: 统计结束日期（类型: string）
-   data[].trendList: 销售趋势数据（类型: array<object>）
-     data[].trendList[].yearMonth: 年月（格式：yyyy-MM）（类型: string）
-     data[].trendList[].salesAmount: 销售金额（类型: number）
-     data[].trendList[].orderCount: 订单数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询销售趋势（所有类型）"
- "查看销售趋势（所有类型）列表"
- "有哪些销售趋势（所有类型）？"
- "列出销售趋势（所有类型）"
- "最近一周的{thing}"
- "本月销售趋势（所有类型）情况"

## 接口：查询本月销售汇总

- **路径：** GET /api/report/summary/current-month
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询本月销售汇总数据，包括总销售额、订单数和签单客户数。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalAmount: （类型: number）
-   data.orderCount: （类型: integer）
-   data.customerCount: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询本月销售汇总"
- "查看本月销售汇总列表"
- "有哪些本月销售汇总？"
- "列出本月销售汇总"
- "有多少个客户？"
- "当前客户数量"

## 接口：查询本年销售汇总

- **路径：** GET /api/report/summary/current-year
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询本年销售汇总数据。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalAmount: （类型: number）
-   data.orderCount: （类型: integer）
-   data.customerCount: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询本年销售汇总"
- "查看本年销售汇总列表"
- "有哪些本年销售汇总？"
- "列出本年销售汇总"

## 接口：查询上月销售汇总

- **路径：** GET /api/report/summary/last-month
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询上月销售汇总数据。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalAmount: （类型: number）
-   data.orderCount: （类型: integer）
-   data.customerCount: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询上月销售汇总"
- "查看上月销售汇总列表"
- "有哪些上月销售汇总？"
- "列出上月销售汇总"

## 接口：查询去年销售汇总

- **路径：** GET /api/report/summary/last-year
- **所属模块：** 报表统计
- **HTTP 方法：** GET

**功能说明：**
查询去年销售汇总数据。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalAmount: （类型: number）
-   data.orderCount: （类型: integer）
-   data.customerCount: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询去年销售汇总"
- "查看去年销售汇总列表"
- "有哪些去年销售汇总？"
- "列出去年销售汇总"

## 接口：请求文件删除凭证（Minio预授权删除地址）

- **路径：** GET /api/file/request-delete
- **所属模块：** 文件上传
- **HTTP 方法：** GET

**功能说明：**
请求文件删除凭证，返回Minio预签名删除URL，用于删除已上传的文件。

**请求参数：**
- filePath （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.presignedUrls: 预签名URL列表（类型: array<string>）
-   data.endpoint: MinIO端点（类型: string）
-   data.bucketName: 存储桶名称（类型: string）
-   data.uploadPrefix: 上传路径前缀（类型: string）
-   data.maxFileSize: 最大文件大小(字节)（类型: integer）
-   data.maxFiles: 最大文件数量（类型: integer）
-   data.allowedFormats: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么请求文件删除凭证（Minio预授权删除地址）？"
- "请求文件删除凭证（Minio预授权删除地址）怎么做？"
- "帮我请求文件删除凭证（Minio预授权删除地址）"

## 接口：请求文件上传凭证（Minio预授权上传地址）文件名,号分隔

- **路径：** GET /api/file/request-upload
- **所属模块：** 文件上传
- **HTTP 方法：** GET

**功能说明：**
请求文件上传凭证（Minio预授权上传地址）文件名,号分隔

**请求参数：**
- fileNum （查询参数）: （类型: integer，可选，默认: 1）
- parentDir （查询参数）: （类型: string，可选）
- fileNames （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.presignedUrls: 预签名URL列表（类型: array<string>）
-   data.endpoint: MinIO端点（类型: string）
-   data.bucketName: 存储桶名称（类型: string）
-   data.uploadPrefix: 上传路径前缀（类型: string）
-   data.maxFileSize: 最大文件大小(字节)（类型: integer）
-   data.maxFiles: 最大文件数量（类型: integer）
-   data.allowedFormats: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么请求文件上传凭证（Minio预授权上传地址）文件名,号分隔？"
- "请求文件上传凭证（Minio预授权上传地址）文件名,号分隔怎么做？"
- "帮我请求文件上传凭证（Minio预授权上传地址）文件名,号分隔"

## 接口：初始化倒排索引

- **路径：** POST /api/admin/init-inverted-index
- **所属模块：** 管理后台
- **HTTP 方法：** POST

**功能说明：**
初始化倒排索引，为所有已有客户数据建立全文搜索索引，首次启用搜索功能或数据修复时使用。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "怎么初始化倒排索引？"
- "初始化倒排索引怎么做？"
- "帮我初始化倒排索引"

## 接口：获取倒排索引统计

- **路径：** GET /api/admin/inverted-index-stats
- **所属模块：** 管理后台
- **HTTP 方法：** GET

**功能说明：**
查看倒排索引的统计信息，包括已索引客户数、词项总数等。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取倒排索引统计"
- "有多少个客户？"
- "当前客户数量"
- "客户总数"
- "客户情况怎么样？"

## 接口：检查倒排索引状态

- **路径：** GET /api/admin/inverted-index-status
- **所属模块：** 管理后台
- **HTTP 方法：** GET

**功能说明：**
检查倒排索引是否已初始化并包含有效数据。

**请求参数：**
- 无

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看倒排索引状态"
- "倒排索引状态是什么？"

## 接口：重建单个客户索引

- **路径：** POST /api/admin/rebuild-customer-index/{customerId}
- **所属模块：** 管理后台
- **HTTP 方法：** POST

**功能说明：**
为指定客户重建倒排索引，用于修复单个客户在搜索中的数据问题。

**请求参数：**
- customerId （路径参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么重建单个客户索引？"
- "重建单个客户索引怎么做？"
- "帮我重建单个客户索引"

## 接口：审核通过（待审核 -> 待生产）

- **路径：** POST /api/sales-order/approve
- **所属模块：** 销售订单管理
- **HTTP 方法：** POST

**功能说明：**
将处于「待审核(0)」状态的销售订单审核通过。旧流程：订单状态变更为「待生产(2)」；新流程：变更为「新增(1)」。审核通过后CRM系统自动向MES生产系统发送消息（Routing Key: `order.status.PENDING_PRODUCTION`），触发MES创建生产工单。该操作为订单全生命周期的起始流转节点，后续由MES/WMS等外部系统接力驱动状态变更。

**请求参数：**
- orderId （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "审核通过"
- "通过审核"
- "审批通过"

## 接口：完成生产（生产中 -> 待发货）

- **路径：** POST /api/sales-order/complete-production
- **所属模块：** 销售订单管理
- **HTTP 方法：** POST

**功能说明：**
生产完成后确认完工。旧流程：订单状态从「生产中(3)」变更为「待发货(4)」；新流程：变更为「已完工(4)」。可由MES生产系统（`PRODUCTION_COMPLETE`）或QMS质检系统（`QUALITY_PASSED`）通过外部系统通用通道（Routing Key: `external.order.status.{system}.{event}`）触发。需填写实际生产数量用于库存和成本核算。QMS质检不通过时仅记录日志，需人工处理。

**请求参数：**
- orderId （查询参数）: （类型: string，必填）
- actualQty （查询参数）: （类型: integer，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "完成生产"
- "生产完成"

## 接口：确认签收（已发货 -> 已签收）

- **路径：** POST /api/sales-order/confirm-receipt
- **所属模块：** 销售订单管理
- **HTTP 方法：** POST

**功能说明：**
确认客户已签收订单货物，订单状态从「已发货(5)」变更为「已签收(6)」，为订单正向流转的终态。通常由WMS仓储系统通过RabbitMQ通用通道（Routing Key: `external.order.status.wms.inbound_complete`）触发CRM回调，需填写客户签收日期（可选，默认当天）。

**请求参数：**
- orderId （查询参数）: （类型: string，必填）
- receiptDate （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "确认发货"
- "发货出库"

## 接口：确认发货（待发货 -> 已发货）

- **路径：** POST /api/sales-order/confirm-shipment
- **所属模块：** 销售订单管理
- **HTTP 方法：** POST

**功能说明：**
确认订单已发货出库，订单状态从「已发货前的状态」变更为「已发货(5)」。通常由WMS仓储系统通过RabbitMQ通用通道（Routing Key: `external.order.status.wms.shipment_confirm`）触发CRM回调，消息体中需包含发货单号。支持MES系统（`APPLY_SHIPMENT`）协同完成发货流程。

**请求参数：**
- orderId （查询参数）: （类型: string，必填）
- deliveryCode （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "确认发货"
- "发货出库"

## 接口：创建销售订单

- **路径：** POST /api/sales-order/create
- **所属模块：** 销售订单管理
- **HTTP 方法：** POST

**功能说明：**
用于创建新的销售订单，记录订单名称、编号、客户信息、客户经理、产品明细及金额等关键信息。订单创建完成后进入「待审核(0)」状态，后续需通过审核流程流转。订单全生命周期涉及多个外部系统协同（MES生产/QMS质检/WMS仓储/ERP销售），各阶段状态变更通过RabbitMQ消息队列驱动。

**请求参数：**
- ordersName: 订单名称（类型: string，必填）
- ordersCode: 订单编号（类型: string，可选）
- customerId: 客户 ID（类型: string，必填）
- customerManager: 客户经理 ID（类型: string，必填）
- requirementName: 需求名称（类型: string，必填）
- requirementCode: 需求编号（类型: string，可选）
- ordersDate: 签单日期（类型: string，必填）
- deliveryDate: 交货日期（类型: string，必填）
- priority: 优先级 (普通/较高/紧急/特急)（类型: integer，必填）
- salesAmount: 订单金额（类型: number，必填）
- products: 产品列表 (JSON 字符串)（类型: array<object>，必填）
-   products[].id: 主键ID（类型: integer）
-   products[].status: 状态：0-禁用，1-启用（类型: integer）
-   products[].createBy: 创建人ID（类型: string）
-   products[].createTime: 创建时间（类型: string）
-   products[].updateBy: 更新人ID（类型: string）
-   products[].updateTime: 更新时间（类型: string）
-   products[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-   products[].requirementId: 客户需求ID（类型: integer）
-   products[].productCode: 产品编码（类型: string）
-   products[].productName: 产品名称（类型: string）
-   products[].productType: 产品类别（类型: string）
-   products[].specModel: 产品型号（类型: string）
-   products[].productNum: 产品数量（类型: number）
-   products[].unitId: 单位（类型: string）
-   products[].productId: 产品ID（类型: integer）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个销售订单"
- "新增销售订单记录"
- "创建一个新的销售订单"
- "帮我添加销售订单"

## 接口：删除订单

- **路径：** POST /api/sales-order/delete
- **所属模块：** 销售订单管理
- **HTTP 方法：** POST

**功能说明：**
软删除销售订单，将订单标记为删除状态，不会物理删除数据库记录。

**请求参数：**
- orderId （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除订单"
- "移除某个订单"

## 接口：销售订单详情

- **路径：** GET /api/sales-order/detail
- **所属模块：** 销售订单管理
- **HTTP 方法：** GET

**功能说明：**
根据订单ID获取销售订单的完整详情信息，包括产品明细、当前状态及状态描述等。订单状态基于RabbitMQ消息驱动的跨系统状态机，除CRM自身操作外，还可由MES（生产）、QMS（质检）、WMS（仓储）、ERP（销售）等外部系统触发变更。

**请求参数：**
- orderId （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: 主键ID（类型: integer）
-   data.createBy: 创建人ID（类型: string）
-   data.createTime: 创建时间（类型: string）
-   data.updateBy: 更新人ID（类型: string）
-   data.updateTime: 更新时间（类型: string）
-   data.isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-   data.tenantId: 租户ID（类型: string）
-   data.tenantName: 租户名称（类型: string）
-   data.ordersCode: 销售单编号（类型: string）
-   data.ordersName: 销售单名称（类型: string）
-   data.ordersDate: 订单签订日期（类型: string）
-   data.deliveryDate: 订单交付日期（类型: string）
-   data.customerId: 客户ID（类型: integer）
-   data.customerName: 客户名称（类型: string）
-   data.priority: 优先级：1-普通、2-较高、3-紧急、4-特急（类型: integer）
-   data.customerManager: 客户经理（类型: string）
-   data.salesAmount: 销售金额（类型: number）
-   data.requirementName: 需求名称（类型: string）
-   data.requirementCode: 需求Code（类型: string）
-   data.products: 订单商品列表（类型: array<object>）
-     data.products[].id: 主键ID（类型: integer）
-     data.products[].status: 状态：0-禁用，1-启用（类型: integer）
-     data.products[].createBy: 创建人ID（类型: string）
-     data.products[].createTime: 创建时间（类型: string）
-     data.products[].updateBy: 更新人ID（类型: string）
-     data.products[].updateTime: 更新时间（类型: string）
-     data.products[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-     data.products[].requirementId: 客户需求ID（类型: integer）
-     data.products[].productCode: 产品编码（类型: string）
-     data.products[].productName: 产品名称（类型: string）
-     data.products[].productType: 产品类别（类型: string）
-     data.products[].specModel: 产品型号（类型: string）
-     data.products[].productNum: 产品数量（类型: number）
-     data.products[].unitId: 单位（类型: string）
-     data.products[].productId: 产品ID（类型: integer）
-   data.ordersStatus: 订单状态（类型: integer）
-   data.deliveryCode: 发货单号（类型: string）
-   data.auditBy: 审核人用户ID（类型: string）
-   data.auditTime: 审核时间（类型: string）
-   data.statusDesc: 状态描述（类型: string）
-   data.priorityDesc: 优先级描述（类型: string）
-   data.customerManagerName: 客户经理名称（类型: string）
-   data.demandContent: 需求内容（类型: string）
-   data.productItems: 产品项列表（类型: array<object>）
-     data.productItems[].id: 主键ID（类型: integer）
-     data.productItems[].status: 状态：0-禁用，1-启用（类型: integer）
-     data.productItems[].createBy: 创建人ID（类型: string）
-     data.productItems[].createTime: 创建时间（类型: string）
-     data.productItems[].updateBy: 更新人ID（类型: string）
-     data.productItems[].updateTime: 更新时间（类型: string）
-     data.productItems[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-     data.productItems[].productType: 产品类型（类型: string）
-     data.productItems[].productCode: 产品编码（类型: string）
-     data.productItems[].productName: 产品名称（类型: string）
-     data.productItems[].productClass: 产品类别（类型: string）
-     data.productItems[].specModel: 规格型号（类型: string）
-     data.productItems[].unitId: 单位ID（类型: integer）
-     data.productItems[].extraAttr1: 扩展属性1（类型: string）
-     data.productItems[].extraAttr2: 扩展属性2（类型: string）
-     data.productItems[].productId: 物料ID（类型: integer）
-     data.productItems[].unitName: 单位名称（类型: string）
-     data.productItems[].productNum: 产品数量（类型: number）
-     data.productItems[].quantity: （类型: number）
-   data.statusEnum: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看订单的详细信息"
- "订单详情"
- "订单的具体情况"

## 接口：分页查询销售订单

- **路径：** GET /api/sales-order/page
- **所属模块：** 销售订单管理
- **HTTP 方法：** GET

**功能说明：**
按条件分页查询销售订单列表，支持按客户名称、订单编号、订单状态、优先级、交货日期范围等多维度筛选，用于订单管理列表展示。订单状态基于状态机流转：待审核(0)→已审核/新增(1)→待生产/已建工单(2)→生产中(3)→待发货/已完工(4)→已发货(5)→已签收(6)，另有被否决(8)和已终止(7)两个终态。旧流程和新流程的状态编码略有不同，查询时需注意版本兼容。

**请求参数：**
- current （查询参数）: （类型: integer，可选，默认: 1）
- size （查询参数）: （类型: integer，可选，默认: 10）
- customerId （查询参数）: （类型: string，可选）
- priority （查询参数）: （类型: string，可选）
- customerName （查询参数）: （类型: string，可选）
- customerManagerId （查询参数）: （类型: string，可选）
- deliveryDateStart （查询参数）: （类型: string，可选）
- deliveryDateEnd （查询参数）: （类型: string，可选）
- ordersCode （查询参数）: （类型: string，可选）
- materialCode （查询参数）: （类型: string，可选）
- search （查询参数）: （类型: string，可选）
- orderStatus （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: 主键ID（类型: integer）
-     data.list[].createBy: 创建人ID（类型: string）
-     data.list[].createTime: 创建时间（类型: string）
-     data.list[].updateBy: 更新人ID（类型: string）
-     data.list[].updateTime: 更新时间（类型: string）
-     data.list[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-     data.list[].tenantId: 租户ID（类型: string）
-     data.list[].tenantName: 租户名称（类型: string）
-     data.list[].ordersCode: 销售单编号（类型: string）
-     data.list[].ordersName: 销售单名称（类型: string）
-     data.list[].ordersDate: 订单签订日期（类型: string）
-     data.list[].deliveryDate: 订单交付日期（类型: string）
-     data.list[].customerId: 客户ID（类型: integer）
-     data.list[].customerName: 客户名称（类型: string）
-     data.list[].priority: 优先级：1-普通、2-较高、3-紧急、4-特急（类型: integer）
-     data.list[].customerManager: 客户经理（类型: string）
-     data.list[].salesAmount: 销售金额（类型: number）
-     data.list[].requirementName: 需求名称（类型: string）
-     data.list[].requirementCode: 需求Code（类型: string）
-     data.list[].products: 订单商品列表（类型: array<object>）
-       data.list[].products[].id: 主键ID（类型: integer）
-       data.list[].products[].status: 状态：0-禁用，1-启用（类型: integer）
-       data.list[].products[].createBy: 创建人ID（类型: string）
-       data.list[].products[].createTime: 创建时间（类型: string）
-       data.list[].products[].updateBy: 更新人ID（类型: string）
-       data.list[].products[].updateTime: 更新时间（类型: string）
-       data.list[].products[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-       data.list[].products[].requirementId: 客户需求ID（类型: integer）
-       data.list[].products[].productCode: 产品编码（类型: string）
-       data.list[].products[].productName: 产品名称（类型: string）
-       data.list[].products[].productType: 产品类别（类型: string）
-       data.list[].products[].specModel: 产品型号（类型: string）
-       data.list[].products[].productNum: 产品数量（类型: number）
-       data.list[].products[].unitId: 单位（类型: string）
-       data.list[].products[].productId: 产品ID（类型: integer）
-     data.list[].ordersStatus: 订单状态（类型: integer）
-     data.list[].deliveryCode: 发货单号（类型: string）
-     data.list[].auditBy: 审核人用户ID（类型: string）
-     data.list[].auditTime: 审核时间（类型: string）
-     data.list[].statusDesc: 状态描述（类型: string）
-     data.list[].priorityDesc: 优先级描述（类型: string）
-     data.list[].customerManagerName: 客户经理名称（类型: string）
-     data.list[].demandContent: 需求内容（类型: string）
-     data.list[].productItems: 产品项列表（类型: array<object>）
-       data.list[].productItems[].id: 主键ID（类型: integer）
-       data.list[].productItems[].status: 状态：0-禁用，1-启用（类型: integer）
-       data.list[].productItems[].createBy: 创建人ID（类型: string）
-       data.list[].productItems[].createTime: 创建时间（类型: string）
-       data.list[].productItems[].updateBy: 更新人ID（类型: string）
-       data.list[].productItems[].updateTime: 更新时间（类型: string）
-       data.list[].productItems[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-       data.list[].productItems[].productType: 产品类型（类型: string）
-       data.list[].productItems[].productCode: 产品编码（类型: string）
-       data.list[].productItems[].productName: 产品名称（类型: string）
-       data.list[].productItems[].productClass: 产品类别（类型: string）
-       data.list[].productItems[].specModel: 规格型号（类型: string）
-       data.list[].productItems[].unitId: 单位ID（类型: integer）
-       data.list[].productItems[].extraAttr1: 扩展属性1（类型: string）
-       data.list[].productItems[].extraAttr2: 扩展属性2（类型: string）
-       data.list[].productItems[].productId: 物料ID（类型: integer）
-       data.list[].productItems[].unitName: 单位名称（类型: string）
-       data.list[].productItems[].productNum: 产品数量（类型: number）
-       data.list[].productItems[].quantity: （类型: number）
-     data.list[].statusEnum: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询销售订单"
- "查看销售订单列表"
- "有哪些销售订单？"
- "列出销售订单"
- "哪些{thing}还未完成？"
- "销售订单进展如何？"

## 接口：发布待生产订单(已审核 -> 待生产)

- **路径：** POST /api/sales-order/publish-production
- **所属模块：** 销售订单管理
- **HTTP 方法：** POST

**功能说明：**
将已审核通过（「新增(1)」/「已审核(1)」）的订单发布到生产环节，订单状态变更为「已建工单(2)」/「待生产(2)」。该操作在旧流程中为人工发布，新流程中审核通过后CRM自动通过RabbitMQ向MES发送消息（`order.status.PENDING_PRODUCTION`），MES创建工单后回调CRM触发此状态变更。

**请求参数：**
- orderId （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么发布待生产订单(已审核 -> 待生产)？"
- "发布待生产订单(已审核 -> 待生产)怎么做？"
- "帮我发布待生产订单(已审核 -> 待生产)"

## 接口：审核否决（待审核 -> 被否决）

- **路径：** POST /api/sales-order/reject
- **所属模块：** 销售订单管理
- **HTTP 方法：** POST

**功能说明：**
将处于「待审核(0)」状态的销售订单审核否决，订单状态变更为「被否决(8)」，订单流转终止。可填写否决原因供后续参考。否决后订单不再参与后续生产流程，且不会向MES等外部系统发送消息。

**请求参数：**
- orderId （查询参数）: （类型: string，必填）
- reason （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "审核否决"
- "驳回订单"
- "审核不通过"

## 接口：开始生产（待生产 -> 生产中）

- **路径：** POST /api/sales-order/start-production
- **所属模块：** 销售订单管理
- **HTTP 方法：** POST

**功能说明：**
将订单状态从「待生产(2)」/「已建工单(2)」变更为「生产中(3)」。由MES生产系统通过专用通道消息（Routing Key: `order.status.IN_PRODUCTION`）触发CRM回调，表示生产订单已发布、工厂已开始生产。消息体包含工单号、产线信息等扩展数据。

**请求参数：**
- orderId （查询参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "开始生产"
- "启动生产"

## 接口：终止订单

- **路径：** POST /api/sales-order/terminate
- **所属模块：** 销售订单管理
- **HTTP 方法：** POST

**功能说明：**
终止处于任何状态的销售订单，需填写终止原因和生产进度说明（可选），终止后订单状态变为「已终止(7)」且不再参与任何流转。可由ERP销售系统通过RabbitMQ通用通道（Routing Key: `external.order.status.erp.order_terminate`）远程触发，消息中需携带终止原因和生产进度数据。

**请求参数：**
- orderId （查询参数）: （类型: string，必填）
- reason （查询参数）: （类型: string，必填）
- productionProgress （查询参数）: （类型: integer，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "终止订单"
- "取消订单"

## 接口：编辑售订单

- **路径：** POST /api/sales-order/update
- **所属模块：** 销售订单管理
- **HTTP 方法：** POST

**功能说明：**
编辑已存在的销售订单信息，可修改订单名称、产品明细、交货日期、金额等字段。注意：订单状态变更后部分字段可能不可修改。

**请求参数：**
- orderId: 订单ID（类型: string，可选）
- deliveryCode: 物流编号（类型: string，可选）
- ordersStatus: 订单状态（类型: integer，可选）
- ordersName: 订单名称（类型: string，可选）
- ordersCode: 订单编号（类型: string，可选）
- customerId: 客户 ID（类型: string，可选）
- customerName: 客户名称（类型: string，可选）
- customerManager: 客户经理 ID（类型: string，可选）
- requirementName: 需求名称（类型: string，可选）
- requirementCode: 需求编号（类型: string，可选）
- ordersDate: 签单日期（类型: string，可选）
- deliveryDate: 交货日期（类型: string，可选）
- priority: 优先级 (普通/较高/紧急/特急)（类型: integer，可选）
- salesAmount: 订单金额（类型: number，可选）
- auditBy: 审核人用户ID（类型: string，可选）
- auditTime: 审核时间（类型: string，可选）
- products: 产品列表 (JSON 字符串)（类型: array<object>，可选）
-   products[].id: 主键ID（类型: integer）
-   products[].status: 状态：0-禁用，1-启用（类型: integer）
-   products[].createBy: 创建人ID（类型: string）
-   products[].createTime: 创建时间（类型: string）
-   products[].updateBy: 更新人ID（类型: string）
-   products[].updateTime: 更新时间（类型: string）
-   products[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-   products[].requirementId: 客户需求ID（类型: integer）
-   products[].productCode: 产品编码（类型: string）
-   products[].productName: 产品名称（类型: string）
-   products[].productType: 产品类别（类型: string）
-   products[].specModel: 产品型号（类型: string）
-   products[].productNum: 产品数量（类型: number）
-   products[].unitId: 单位（类型: string）
-   products[].productId: 产品ID（类型: integer）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改售订单信息"
- "更新售订单数据"
- "编辑售订单"

## 接口：创建问题工单

- **路径：** POST /api/feedback-solve
- **所属模块：** 问题工单管理
- **HTTP 方法：** POST

**功能说明：**
根据反馈单创建问题工单（售后/处理工单），记录物料信息、责任人、生产订单号等，用于反馈问题的跟踪解决。

**请求参数：**
- feedbackId: 问题工单ID（类型: integer，可选）
- customerId: 客户ID（类型: string，必填）
- problemNo: 问题编号（类型: string，可选）
- salesOrderNo: 销售订单编号（类型: string，可选）
- productOrderNo: 产品订单编号（类型: string，可选）
- productSn: 物料编码（类型: string，可选）
- productName: 物料名称（类型: string，可选）
- workStatus: 工单状态（类型: string，可选）
- responsiblePerson: 处理人（类型: string，可选）
- producingOrderNo: 生产订单编号（类型: string，可选）
- result: 处理结果（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: 工单处理成功VO（类型: object）
-   data.workOrderNo: 工单编号（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个问题工单"
- "新增问题工单记录"
- "创建一个新的问题工单"
- "帮我添加问题工单"

## 接口：删除问题工单

- **路径：** POST /api/feedback-solve/delete/{id}
- **所属模块：** 问题工单管理
- **HTTP 方法：** POST

**功能说明：**
删除指定问题工单。

**请求参数：**
- id （路径参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "删除问题工单"
- "移除某个问题工单"

## 接口：查询问题工单详情

- **路径：** GET /api/feedback-solve/detail/{id}
- **所属模块：** 问题工单管理
- **HTTP 方法：** GET

**功能说明：**
获取问题工单的完整详情信息。

**请求参数：**
- id （路径参数）: （类型: string，必填）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.id: 主键ID（类型: integer）
-   data.workOrderNo: 工单编号（类型: string）
-   data.customerId: 客户ID（类型: integer）
-   data.customerName: 客户名称（类型: string）
-   data.feedbackId: 反馈单ID（类型: integer）
-   data.problemNo: 问题编号（类型: string）
-   data.salesOrderNo: 销售订单编号（类型: string）
-   data.producingOrderNo: 生产订单编号（类型: string）
-   data.productSn: 物料编码（类型: string）
-   data.productName: 物料名称（类型: string）
-   data.workStatus: 工单状态（类型: string）
-   data.workStatusName: 工单状态名称（类型: string）
-   data.responsiblePerson: 处理人（类型: string）
-   data.responsiblePersonName: 处理人名称（类型: string）
-   data.status: 状态：0-禁用，1-启用（类型: integer）
-   data.createBy: 创建人ID（类型: string）
-   data.createTime: 创建时间（类型: string）
-   data.updateBy: 更新人ID（类型: string）
-   data.updateTime: 更新时间（类型: string）
-   data.problemTypeName: 问题类型名称（类型: string）
-   data.problemType: 问题类型（类型: string）
-   data.problemDescription: 问题描述（类型: string）
-   data.attachments: 附件列表（类型: array<string>）
-   data.result: 处理结果（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询问题工单详情"
- "查看问题工单详情列表"
- "有哪些问题工单详情？"
- "列出问题工单详情"

## 接口：查询问题工单列表

- **路径：** POST /api/feedback-solve/page
- **所属模块：** 问题工单管理
- **HTTP 方法：** POST

**功能说明：**
按条件分页查询问题工单列表，支持按客户、工单号、状态等多维度筛选。

**请求参数：**
- current: （类型: integer，可选）
- size: （类型: integer，可选）
- customerId: （类型: string，可选）
- customerName: （类型: string，可选）
- workOrderNo: （类型: string，可选）
- salesOrderNo: （类型: string，可选）
- productSn: （类型: string，可选）
- productName: （类型: string，可选）
- workStatus: （类型: string，可选）
- workStatusList: （类型: array<string>，可选）
- search: （类型: string，可选）
- startTime: （类型: string，可选）
- endTime: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pageSize: （类型: integer）
-   data.totalPage: （类型: integer）
-   data.currPage: （类型: integer）
-   data.list: （类型: array<object>）
-     data.list[].id: 主键ID（类型: integer）
-     data.list[].status: 状态：0-禁用，1-启用（类型: integer）
-     data.list[].createBy: 创建人ID（类型: string）
-     data.list[].createTime: 创建时间（类型: string）
-     data.list[].updateBy: 更新人ID（类型: string）
-     data.list[].updateTime: 更新时间（类型: string）
-     data.list[].isValid: 是否有效：0-无效，1-有效（逻辑删除标志）（类型: integer）
-     data.list[].feedbackId: 反馈单ID（类型: integer）
-     data.list[].workOrderNo: 工单编号（类型: string）
-     data.list[].customerId: 客户ID（类型: integer）
-     data.list[].customerName: 客户名称（类型: string）
-     data.list[].problemNo: 问题编号（类型: string）
-     data.list[].salesOrderNo: 销售订单编号（类型: string）
-     data.list[].productSn: 物料编码（类型: string）
-     data.list[].productName: 物料名称（类型: string）
-     data.list[].workStatus: 工单状态（类型: string）
-     data.list[].responsiblePerson: 处理人（类型: string）
-     data.list[].result: 处理结果（类型: string）
-     data.list[].producingOrderNo: 生产订单编号（类型: string）
-     data.list[].responsiblePersonName: 处理人名称（类型: string）
-     data.list[].workStatusName: 工单状态名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询问题工单列表"
- "查看问题工单列表列表"
- "有哪些问题工单列表？"
- "列出问题工单列表"
- "哪些{thing}还未完成？"
- "问题工单列表进展如何？"

## 接口：更新问题工单

- **路径：** POST /api/feedback-solve/update
- **所属模块：** 问题工单管理
- **HTTP 方法：** POST

**功能说明：**
更新问题工单信息，包括处理状态、责任人、处理结果等。

**请求参数：**
- feedbackId: 问题工单ID（类型: integer，可选）
- customerId: 客户ID（类型: string，必填）
- problemNo: 问题编号（类型: string，可选）
- salesOrderNo: 销售订单编号（类型: string，可选）
- productOrderNo: 产品订单编号（类型: string，可选）
- productSn: 物料编码（类型: string，可选）
- productName: 物料名称（类型: string，可选）
- workStatus: 工单状态（类型: string，可选）
- responsiblePerson: 处理人（类型: string，可选）
- producingOrderNo: 生产订单编号（类型: string，可选）
- result: 处理结果（类型: string，可选）
- id: 更新问题工单DTO（类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改问题工单信息"
- "更新问题工单数据"
- "编辑问题工单"

## 接口：获取需求金额分布

- **路径：** GET /api/requirement-report/amount-distribution
- **所属模块：** 需求分析报表
- **HTTP 方法：** GET

**功能说明：**
获取需求金额在各销售阶段的分布情况。

**请求参数：**
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].stageCode: 销售阶段代码（类型: string）
-   data[].stageName: 销售阶段名称（类型: string）
-   data[].amount: 阶段金额（类型: number）
-   data[].count: 阶段需求数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取需求金额分布"
- "销售额怎么样？"
- "最近销售情况如何？"
- "销售额有多少？"
- "需求情况怎么样？"
- "有多少需求？"

## 接口：手动触发需求月度统计

- **路径：** POST /api/requirement-report/calculate-monthly
- **所属模块：** 需求分析报表
- **HTTP 方法：** POST

**功能说明：**
手动触发需求月度统计数据的计算和更新。

**请求参数：**
- statDate （查询参数）: （类型: string，可选）
- yearMonth （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查看手动触发需求月度统计"
- "需求情况怎么样？"
- "有多少需求？"
- "需求转化如何？"

## 接口：获取需求转化看板

- **路径：** GET /api/requirement-report/conversion-dashboard
- **所属模块：** 需求分析报表
- **HTTP 方法：** GET

**功能说明：**
获取需求转化看板数据，展示客户经理维度的转化统计。

**请求参数：**
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].managerId: 客户经理ID（类型: string）
-   data[].managerName: 客户经理名称（类型: string）
-   data[].demandAmount: 商机金额预测（需求金额）（类型: number）
-   data[].demandCount: 商机数（需求数量）（类型: integer）
-   data[].winAmount: 赢单金额（类型: number）
-   data[].winCount: 赢单数（类型: integer）
-   data[].customerCount: 需求客户数（类型: integer）
-   data[].winRate: 赢单率(%)（类型: number）
-   data[].totalCustomers: 客户数（柱状图-蓝色）（类型: integer）
-   data[].convertedDemandCount: 转化需求数（柱状图-绿色）（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取需求转化看板"
- "有多少个客户？"
- "当前客户数量"
- "客户总数"
- "客户情况怎么样？"
- "需求情况怎么样？"

## 接口：获取赢单转化率趋势

- **路径：** GET /api/requirement-report/conversion-trend
- **所属模块：** 需求分析报表
- **HTTP 方法：** GET

**功能说明：**
获取赢单转化率的月度趋势数据。

**请求参数：**
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].yearMonth: 统计月份（类型: string）
-   data[].winConvertRate: 赢单转化率(%)（类型: number）
-   data[].winCount: 赢单数（类型: integer）
-   data[].lostCount: 输单数（类型: integer）
-   data[].totalClosedCount: 总闭环数(赢单+输单)（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取赢单转化率趋势"
- "最近趋势怎么样？"
- "变化趋势如何？"
- "转化率是多少？"
- "赢单率怎么样？"

## 接口：获取客户经理需求统计

- **路径：** GET /api/requirement-report/customer-manager-stats
- **所属模块：** 需求分析报表
- **HTTP 方法：** GET

**功能说明：**
统计各客户经理的需求跟踪数据，包括商机数、赢单数、转化率等。

**请求参数：**
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].managerId: 客户经理ID（类型: string）
-   data[].managerName: 客户经理名称（类型: string）
-   data[].demandAmount: 商机金额预测（需求金额）（类型: number）
-   data[].demandCount: 商机数（需求数量）（类型: integer）
-   data[].winAmount: 赢单金额（类型: number）
-   data[].winCount: 赢单数（类型: integer）
-   data[].customerCount: 需求客户数（类型: integer）
-   data[].winRate: 赢单率(%)（类型: number）
-   data[].totalCustomers: 客户数（柱状图-蓝色）（类型: integer）
-   data[].convertedDemandCount: 转化需求数（柱状图-绿色）（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取客户经理需求统计"
- "有多少个客户？"
- "当前客户数量"
- "客户总数"
- "客户情况怎么样？"
- "需求情况怎么样？"

## 接口：获取输单原因分析

- **路径：** GET /api/requirement-report/loss-reason-analysis
- **所属模块：** 需求分析报表
- **HTTP 方法：** GET

**功能说明：**
获取输单原因分析数据，统计各输单原因的数量和占比。

**请求参数：**
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].reason: 输单原因（类型: string）
-   data[].count: 输单数量（类型: integer）
-   data[].ratio: 占比(%)（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取输单原因分析"
- "怎么获取输单原因分析？"
- "获取输单原因分析怎么做？"
- "帮我获取输单原因分析"

## 接口：获取需求金额月度趋势

- **路径：** GET /api/requirement-report/monthly-amount-trend
- **所属模块：** 需求分析报表
- **HTTP 方法：** GET

**功能说明：**
获取需求金额的月度趋势数据，用于分析需求变化趋势。

**请求参数：**
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].yearMonth: 统计月份（类型: string）
-   data[].demandAmount: 需求金额（类型: number）
-   data[].demandCount: 需求数量（类型: integer）
-   data[].winAmount: 赢单金额（类型: number）
-   data[].winCount: 赢单数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取需求金额月度趋势"
- "需求情况怎么样？"
- "有多少需求？"
- "需求转化如何？"
- "最近趋势怎么样？"
- "变化趋势如何？"

## 接口：获取需求区域分布

- **路径：** GET /api/requirement-report/region-distribution
- **所属模块：** 需求分析报表
- **HTTP 方法：** GET

**功能说明：**
获取需求按区域分布的统计分析，包括各区域的需求数量和金额。

**请求参数：**
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].regionCode: 地域代码（类型: string）
-   data[].regionName: 地域名称（类型: string）
-   data[].customerCount: 需求客户数（类型: integer）
-   data[].demandCount: 需求总数（类型: integer）
-   data[].demandAmount: 需求金额（类型: number）
-   data[].winCount: 赢单数（类型: integer）
-   data[].winAmount: 赢单金额（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取需求区域分布"
- "需求情况怎么样？"
- "有多少需求？"
- "需求转化如何？"
- "分布情况怎么样？"
- "各区域的分布"

## 接口：获取需求阶段漏斗

- **路径：** GET /api/requirement-report/stage-funnel
- **所属模块：** 需求分析报表
- **HTTP 方法：** GET

**功能说明：**
获取各销售阶段的需求数量漏斗分析，展示从线索到赢单的转化路径。

**请求参数：**
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].stageCode: 销售阶段代码（类型: string）
-   data[].stageName: 销售阶段名称（类型: string）
-   data[].count: 阶段需求数量（类型: integer）
-   data[].amount: 阶段金额（类型: number）
-   data[].ratio: 占比(%)（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取需求阶段漏斗"
- "销售额怎么样？"
- "最近销售情况如何？"
- "销售额有多少？"
- "需求情况怎么样？"
- "有多少需求？"

## 接口：获取需求分析汇总数据

- **路径：** GET /api/requirement-report/summary
- **所属模块：** 需求分析报表
- **HTTP 方法：** GET

**功能说明：**
获取需求分析看板的汇总统计数据，包括需求总数、赢单率、转化率等关键指标。

**请求参数：**
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: 需求分析汇总统计（类型: object）
-   data.demandCustomerCount: 需求客户数（类型: integer）
-   data.demandTotalCount: 需求总数（类型: integer）
-   data.ongoingDemandCount: 进行中需求数（类型: integer）
-   data.demandAmount: 需求预测金额（类型: number）
-   data.winDemandCount: 已赢单需求数（类型: integer）
-   data.demandWinRate: 需求赢单率(%)（类型: number）
-   data.lostDemandCount: 已输单需求数（类型: integer）
-   data.invalidDemandCount: 无效需求数（类型: integer）
-   data.wonAmount: 赢单金额（类型: number）
-   data.winConvertRate: 赢单转化率(%)（类型: number）
-   data.lostRatio: 输单占比(%)（类型: number）
-   data.monthlyWinConvertRate: 月度赢单转化率Map，key为年月(yyyy-MM)，value为转化率（类型: object）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取需求分析汇总数据"
- "需求情况怎么样？"
- "有多少需求？"
- "需求转化如何？"
- "当前汇总数据"
- "最新的汇总统计"

## 接口：获取赢单金额趋势

- **路径：** GET /api/requirement-report/won-amount-trend
- **所属模块：** 需求分析报表
- **HTTP 方法：** GET

**功能说明：**
获取赢单金额的月度趋势数据，用于分析赢单变化趋势。

**请求参数：**
- startDate （查询参数）: （类型: string，可选）
- endDate （查询参数）: （类型: string，可选）

**返回字段：**
- code: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].yearMonth: 统计月份（类型: string）
-   data[].demandAmount: 需求金额（类型: number）
-   data[].demandCount: 需求数量（类型: integer）
-   data[].winAmount: 赢单金额（类型: number）
-   data[].winCount: 赢单数量（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看获取赢单金额趋势"
- "最近趋势怎么样？"
- "变化趋势如何？"
