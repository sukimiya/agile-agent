# 服务：srm-service APIs document

## 接口：查询分页

- **路径：** GET /api/v1/payments
- **所属模块：** 付款单
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/payments
- **所属模块：** 付款单
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- invoiceId: 发票ID（类型: integer，可选）
- supplierId: 供应商ID（类型: integer，可选）
- paymentCode: 付款单号（类型: string，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- contact: 联系人（类型: string，可选）
- contactPhone: 联系电话（类型: string，可选）
- bankAccountName: 收款账户名称（类型: string，可选）
- bankName: 开户银行（类型: string，可选）
- bankAccount: 银行账号（类型: string，可选）
- purchaseOrderCode: 采购订单号（类型: string，可选）
- orderAmount: 订单金额（类型: number，可选）
- contractCode: 合同编号（类型: string，可选）
- contractFile: 合同附件（类型: string，可选）
- paidAmount: 已付款金额（类型: number，可选）
- paymentName: 款项名称（类型: string，可选）
- paymentAmount: 本次付款金额（类型: number，可选）
- paymentTime: 付款时间（类型: string，可选）
- isLocked: 是否锁定（类型: integer，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- invoiceAmount: （类型: number，可选）
- invoiceNo: （类型: string，可选）
- totalPaymentAmount: （类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 付款单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.invoiceId: 发票ID（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.paymentCode: 付款单号（类型: string）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.contact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.bankAccountName: 收款账户名称（类型: string）
-   data.bankName: 开户银行（类型: string）
-   data.bankAccount: 银行账号（类型: string）
-   data.purchaseOrderCode: 采购订单号（类型: string）
-   data.orderAmount: 订单金额（类型: number）
-   data.contractCode: 合同编号（类型: string）
-   data.contractFile: 合同附件（类型: string）
-   data.paidAmount: 已付款金额（类型: number）
-   data.paymentName: 款项名称（类型: string）
-   data.paymentAmount: 本次付款金额（类型: number）
-   data.paymentTime: 付款时间（类型: string）
-   data.isLocked: 是否锁定（类型: integer）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.invoiceAmount: （类型: number）
-   data.invoiceNo: （类型: string）
-   data.totalPaymentAmount: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/payments/{id}
- **所属模块：** 付款单
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 付款单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.invoiceId: 发票ID（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.paymentCode: 付款单号（类型: string）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.contact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.bankAccountName: 收款账户名称（类型: string）
-   data.bankName: 开户银行（类型: string）
-   data.bankAccount: 银行账号（类型: string）
-   data.purchaseOrderCode: 采购订单号（类型: string）
-   data.orderAmount: 订单金额（类型: number）
-   data.contractCode: 合同编号（类型: string）
-   data.contractFile: 合同附件（类型: string）
-   data.paidAmount: 已付款金额（类型: number）
-   data.paymentName: 款项名称（类型: string）
-   data.paymentAmount: 本次付款金额（类型: number）
-   data.paymentTime: 付款时间（类型: string）
-   data.isLocked: 是否锁定（类型: integer）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.invoiceAmount: （类型: number）
-   data.invoiceNo: （类型: string）
-   data.totalPaymentAmount: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/payments/{id}
- **所属模块：** 付款单
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- invoiceId: 发票ID（类型: integer，可选）
- supplierId: 供应商ID（类型: integer，可选）
- paymentCode: 付款单号（类型: string，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- contact: 联系人（类型: string，可选）
- contactPhone: 联系电话（类型: string，可选）
- bankAccountName: 收款账户名称（类型: string，可选）
- bankName: 开户银行（类型: string，可选）
- bankAccount: 银行账号（类型: string，可选）
- purchaseOrderCode: 采购订单号（类型: string，可选）
- orderAmount: 订单金额（类型: number，可选）
- contractCode: 合同编号（类型: string，可选）
- contractFile: 合同附件（类型: string，可选）
- paidAmount: 已付款金额（类型: number，可选）
- paymentName: 款项名称（类型: string，可选）
- paymentAmount: 本次付款金额（类型: number，可选）
- paymentTime: 付款时间（类型: string，可选）
- isLocked: 是否锁定（类型: integer，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- invoiceAmount: （类型: number，可选）
- invoiceNo: （类型: string，可选）
- totalPaymentAmount: （类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 付款单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.invoiceId: 发票ID（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.paymentCode: 付款单号（类型: string）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.contact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.bankAccountName: 收款账户名称（类型: string）
-   data.bankName: 开户银行（类型: string）
-   data.bankAccount: 银行账号（类型: string）
-   data.purchaseOrderCode: 采购订单号（类型: string）
-   data.orderAmount: 订单金额（类型: number）
-   data.contractCode: 合同编号（类型: string）
-   data.contractFile: 合同附件（类型: string）
-   data.paidAmount: 已付款金额（类型: number）
-   data.paymentName: 款项名称（类型: string）
-   data.paymentAmount: 本次付款金额（类型: number）
-   data.paymentTime: 付款时间（类型: string）
-   data.isLocked: 是否锁定（类型: integer）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.invoiceAmount: （类型: number）
-   data.invoiceNo: （类型: string）
-   data.totalPaymentAmount: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/payments/{id}
- **所属模块：** 付款单
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/suppliers
- **所属模块：** 供应商主表
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/suppliers
- **所属模块：** 供应商主表
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- supplierStatus: 供应商状态（类型: string，可选）
- supplierLevel: 供应商级别（类型: string，可选）
- materialClass: 供货物料类别（类型: string，可选）
- materialNames: 供货物料（类型: string，可选）
- employeeCount: 公司人数（类型: integer，可选）
- annualRevenue: 年营收（类型: number，可选）
- website: 网址（类型: string，可选）
- areaRegion: 所在地区（类型: string，可选）
- address: 详细地址（类型: string，可选）
- contact: 联系人（类型: string，可选）
- contactPhone: 联系电话（类型: string，可选）
- contactEmail: 电子邮箱（类型: string，可选）
- enterpriseName: 企业名称（类型: string，可选）
- creditCode: 统一社会信用代码（类型: string，可选）
- enterpriseNature: 企业类型（类型: string，可选）
- legalRepresentative: 法定代表人（类型: string，可选）
- registeredCapital: 注册资本（类型: number，可选）
- registrationDate: 注册日期（类型: string，可选）
- businessScope: 经营范围（类型: string，可选）
- bankAccountName: 开户银行账户名称（类型: string，可选）
- bankName: 开户银行（类型: string，可选）
- bankAccount: 银行账号（类型: string，可选）
- taxType: 税种（类型: string，可选）
- taxRate: 税率（类型: number，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商主表 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.supplierStatus: 供应商状态（类型: string）
-   data.supplierLevel: 供应商级别（类型: string）
-   data.materialClass: 供货物料类别（类型: string）
-   data.materialNames: 供货物料（类型: string）
-   data.employeeCount: 公司人数（类型: integer）
-   data.annualRevenue: 年营收（类型: number）
-   data.website: 网址（类型: string）
-   data.areaRegion: 所在地区（类型: string）
-   data.address: 详细地址（类型: string）
-   data.contact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.contactEmail: 电子邮箱（类型: string）
-   data.enterpriseName: 企业名称（类型: string）
-   data.creditCode: 统一社会信用代码（类型: string）
-   data.enterpriseNature: 企业类型（类型: string）
-   data.legalRepresentative: 法定代表人（类型: string）
-   data.registeredCapital: 注册资本（类型: number）
-   data.registrationDate: 注册日期（类型: string）
-   data.businessScope: 经营范围（类型: string）
-   data.bankAccountName: 开户银行账户名称（类型: string）
-   data.bankName: 开户银行（类型: string）
-   data.bankAccount: 银行账号（类型: string）
-   data.taxType: 税种（类型: string）
-   data.taxRate: 税率（类型: number）
-   data.remark: 备注（类型: string）
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

## 接口：供应商地区统计（省份）

- **路径：** GET /api/v1/suppliers/stats/area
- **所属模块：** 供应商主表
- **HTTP 方法：** GET

**功能说明：**
供应商地区统计（省份）

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].code: （类型: string）
-   data[].value: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看供应商地区统计（省份）"
- "怎么供应商地区统计（省份）？"
- "供应商地区统计（省份）怎么做？"
- "帮我供应商地区统计（省份）"

## 接口：供应商级别统计

- **路径：** GET /api/v1/suppliers/stats/level
- **所属模块：** 供应商主表
- **HTTP 方法：** GET

**功能说明：**
供应商级别统计

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].code: （类型: string）
-   data[].value: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看供应商级别统计"
- "怎么供应商级别统计？"
- "供应商级别统计怎么做？"
- "帮我供应商级别统计"

## 接口：供应商物料分类统计

- **路径：** GET /api/v1/suppliers/stats/material-class
- **所属模块：** 供应商主表
- **HTTP 方法：** GET

**功能说明：**
供应商物料分类统计

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].code: （类型: string）
-   data[].value: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看供应商物料分类统计"
- "怎么供应商物料分类统计？"
- "供应商物料分类统计怎么做？"
- "帮我供应商物料分类统计"

## 接口：供应商订单金额排名

- **路径：** GET /api/v1/suppliers/stats/order-rank
- **所属模块：** 供应商主表
- **HTTP 方法：** GET

**功能说明：**
供应商订单金额排名

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].supplierId: （类型: integer）
-   data[].supplierName: （类型: string）
-   data[].orderCount: （类型: integer）
-   data[].orderAmount: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "怎么供应商订单金额排名？"
- "供应商订单金额排名怎么做？"
- "帮我供应商订单金额排名"

## 接口：供应商状态统计

- **路径：** GET /api/v1/suppliers/stats/status
- **所属模块：** 供应商主表
- **HTTP 方法：** GET

**功能说明：**
供应商状态统计

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].code: （类型: string）
-   data[].value: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看供应商状态统计"
- "客户状态分布"
- "当前状态统计"

## 接口：查询

- **路径：** GET /api/v1/suppliers/{id}
- **所属模块：** 供应商主表
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商主表 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.supplierStatus: 供应商状态（类型: string）
-   data.supplierLevel: 供应商级别（类型: string）
-   data.materialClass: 供货物料类别（类型: string）
-   data.materialNames: 供货物料（类型: string）
-   data.employeeCount: 公司人数（类型: integer）
-   data.annualRevenue: 年营收（类型: number）
-   data.website: 网址（类型: string）
-   data.areaRegion: 所在地区（类型: string）
-   data.address: 详细地址（类型: string）
-   data.contact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.contactEmail: 电子邮箱（类型: string）
-   data.enterpriseName: 企业名称（类型: string）
-   data.creditCode: 统一社会信用代码（类型: string）
-   data.enterpriseNature: 企业类型（类型: string）
-   data.legalRepresentative: 法定代表人（类型: string）
-   data.registeredCapital: 注册资本（类型: number）
-   data.registrationDate: 注册日期（类型: string）
-   data.businessScope: 经营范围（类型: string）
-   data.bankAccountName: 开户银行账户名称（类型: string）
-   data.bankName: 开户银行（类型: string）
-   data.bankAccount: 银行账号（类型: string）
-   data.taxType: 税种（类型: string）
-   data.taxRate: 税率（类型: number）
-   data.remark: 备注（类型: string）
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

- **路径：** PUT /api/v1/suppliers/{id}
- **所属模块：** 供应商主表
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- supplierStatus: 供应商状态（类型: string，可选）
- supplierLevel: 供应商级别（类型: string，可选）
- materialClass: 供货物料类别（类型: string，可选）
- materialNames: 供货物料（类型: string，可选）
- employeeCount: 公司人数（类型: integer，可选）
- annualRevenue: 年营收（类型: number，可选）
- website: 网址（类型: string，可选）
- areaRegion: 所在地区（类型: string，可选）
- address: 详细地址（类型: string，可选）
- contact: 联系人（类型: string，可选）
- contactPhone: 联系电话（类型: string，可选）
- contactEmail: 电子邮箱（类型: string，可选）
- enterpriseName: 企业名称（类型: string，可选）
- creditCode: 统一社会信用代码（类型: string，可选）
- enterpriseNature: 企业类型（类型: string，可选）
- legalRepresentative: 法定代表人（类型: string，可选）
- registeredCapital: 注册资本（类型: number，可选）
- registrationDate: 注册日期（类型: string，可选）
- businessScope: 经营范围（类型: string，可选）
- bankAccountName: 开户银行账户名称（类型: string，可选）
- bankName: 开户银行（类型: string，可选）
- bankAccount: 银行账号（类型: string，可选）
- taxType: 税种（类型: string，可选）
- taxRate: 税率（类型: number，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商主表 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.supplierStatus: 供应商状态（类型: string）
-   data.supplierLevel: 供应商级别（类型: string）
-   data.materialClass: 供货物料类别（类型: string）
-   data.materialNames: 供货物料（类型: string）
-   data.employeeCount: 公司人数（类型: integer）
-   data.annualRevenue: 年营收（类型: number）
-   data.website: 网址（类型: string）
-   data.areaRegion: 所在地区（类型: string）
-   data.address: 详细地址（类型: string）
-   data.contact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.contactEmail: 电子邮箱（类型: string）
-   data.enterpriseName: 企业名称（类型: string）
-   data.creditCode: 统一社会信用代码（类型: string）
-   data.enterpriseNature: 企业类型（类型: string）
-   data.legalRepresentative: 法定代表人（类型: string）
-   data.registeredCapital: 注册资本（类型: number）
-   data.registrationDate: 注册日期（类型: string）
-   data.businessScope: 经营范围（类型: string）
-   data.bankAccountName: 开户银行账户名称（类型: string）
-   data.bankName: 开户银行（类型: string）
-   data.bankAccount: 银行账号（类型: string）
-   data.taxType: 税种（类型: string）
-   data.taxRate: 税率（类型: number）
-   data.remark: 备注（类型: string）
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

- **路径：** DELETE /api/v1/suppliers/{id}
- **所属模块：** 供应商主表
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/supplier-materials
- **所属模块：** 供应商供货物料关系
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/supplier-materials
- **所属模块：** 供应商供货物料关系
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- supplierId: 供应商ID（类型: integer，可选）
- materialId: 物料id（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- materialTypeCode: 物料类型编码（类型: string，可选）
- materialTypeName: 物料类型名称（类型: string，可选）
- materialClassCode: 物料分类编码（类型: string，可选）
- materialClassName: 物料分类名称（类型: string，可选）
- unitId: 计量单位id（类型: string，可选）
- unitName: 计量单位名称（类型: string，可选）
- specModel: 规格型号（类型: string，可选）
- unitPrice: 单价（类型: number，可选）
- price: 供货单价（类型: number，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- supplierName: 供应商名字（类型: string，可选）
- supplierCode: 供应商编码（类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商供货物料关系 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位id（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.unitPrice: 单价（类型: number）
-   data.price: 供货单价（类型: number）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.supplierName: 供应商名字（类型: string）
-   data.supplierCode: 供应商编码（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：批量创建

- **路径：** POST /api/v1/supplier-materials/batch
- **所属模块：** 供应商供货物料关系
- **HTTP 方法：** POST

**功能说明：**
批量创建

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: 主键（类型: integer）
-   data[].supplierId: 供应商ID（类型: integer）
-   data[].materialId: 物料id（类型: string）
-   data[].materialCode: 物料编码（类型: string）
-   data[].materialName: 物料名称（类型: string）
-   data[].materialTypeCode: 物料类型编码（类型: string）
-   data[].materialTypeName: 物料类型名称（类型: string）
-   data[].materialClassCode: 物料分类编码（类型: string）
-   data[].materialClassName: 物料分类名称（类型: string）
-   data[].unitId: 计量单位id（类型: string）
-   data[].unitName: 计量单位名称（类型: string）
-   data[].specModel: 规格型号（类型: string）
-   data[].unitPrice: 单价（类型: number）
-   data[].price: 供货单价（类型: number）
-   data[].remark: 备注（类型: string）
-   data[].createBy: 创建人（类型: string）
-   data[].createTime: 记录创建时间（类型: string）
-   data[].updateBy: 更新人（类型: string）
-   data[].updateTime: 记录更新时间（类型: string）
-   data[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data[].supplierName: 供应商名字（类型: string）
-   data[].supplierCode: 供应商编码（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么批量创建？"
- "批量创建怎么做？"
- "帮我批量创建"

## 接口：查询

- **路径：** GET /api/v1/supplier-materials/{id}
- **所属模块：** 供应商供货物料关系
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商供货物料关系 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位id（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.unitPrice: 单价（类型: number）
-   data.price: 供货单价（类型: number）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.supplierName: 供应商名字（类型: string）
-   data.supplierCode: 供应商编码（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/supplier-materials/{id}
- **所属模块：** 供应商供货物料关系
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- supplierId: 供应商ID（类型: integer，可选）
- materialId: 物料id（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- materialTypeCode: 物料类型编码（类型: string，可选）
- materialTypeName: 物料类型名称（类型: string，可选）
- materialClassCode: 物料分类编码（类型: string，可选）
- materialClassName: 物料分类名称（类型: string，可选）
- unitId: 计量单位id（类型: string，可选）
- unitName: 计量单位名称（类型: string，可选）
- specModel: 规格型号（类型: string，可选）
- unitPrice: 单价（类型: number，可选）
- price: 供货单价（类型: number，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- supplierName: 供应商名字（类型: string，可选）
- supplierCode: 供应商编码（类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商供货物料关系 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位id（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.unitPrice: 单价（类型: number）
-   data.price: 供货单价（类型: number）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.supplierName: 供应商名字（类型: string）
-   data.supplierCode: 供应商编码（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/supplier-materials/{id}
- **所属模块：** 供应商供货物料关系
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：根据供应商ID查询物料编码列表

- **路径：** GET /api/v1/supplier-materials/{supplierId}/material-codes
- **所属模块：** 供应商供货物料关系
- **HTTP 方法：** GET

**功能说明：**
根据供应商ID查询物料编码列表

**请求参数：**
- supplierId （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<string>）
- extraData: （类型: object）

**适用问题示例：**
- "查询物料编码列表"
- "查看物料编码列表列表"
- "有哪些物料编码列表？"
- "列出物料编码列表"

## 接口：查询分页

- **路径：** GET /api/v1/supplier-evaluations
- **所属模块：** 供应商评价头
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/supplier-evaluations
- **所属模块：** 供应商评价头
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- supplierId: 供应商ID（类型: integer，可选）
- evalCycle: 评价周期（类型: string，可选）
- qualityScore: 产品质量分（类型: integer，可选）
- deliveryScore: 供货情况分（类型: integer，可选）
- priceScore: 价格分（类型: integer，可选）
- serviceScore: 服务分（类型: integer，可选）
- prodCapacity: 生产能力分（类型: integer，可选）
- mgmtSystem: 管理体系分（类型: integer，可选）
- financialScore: 财务状况分（类型: integer，可选）
- totalScore: 总分（类型: integer，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].evaluationId: 评价头ID（类型: integer）
-   detail[].scoreDimension: 评分维度（类型: string）
-   detail[].scoreDescription: 维度说明（类型: string）
-   detail[].scoreStandard: 评分标准（类型: string）
-   detail[].scoreRule: 评分规则（类型: string）
-   detail[].fullMark: 满分（类型: integer）
-   detail[].score: 得分（类型: integer）
-   detail[].remark: 备注（类型: string）
-   detail[].createBy: 创建人（类型: string）
-   detail[].createTime: 记录创建时间（类型: string）
-   detail[].updateBy: 更新人（类型: string）
-   detail[].updateTime: 记录更新时间（类型: string）
-   detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
- supplierCode: /**（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商评价头 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.evalCycle: 评价周期（类型: string）
-   data.qualityScore: 产品质量分（类型: integer）
-   data.deliveryScore: 供货情况分（类型: integer）
-   data.priceScore: 价格分（类型: integer）
-   data.serviceScore: 服务分（类型: integer）
-   data.prodCapacity: 生产能力分（类型: integer）
-   data.mgmtSystem: 管理体系分（类型: integer）
-   data.financialScore: 财务状况分（类型: integer）
-   data.totalScore: 总分（类型: integer）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].evaluationId: 评价头ID（类型: integer）
-     data.detail[].scoreDimension: 评分维度（类型: string）
-     data.detail[].scoreDescription: 维度说明（类型: string）
-     data.detail[].scoreStandard: 评分标准（类型: string）
-     data.detail[].scoreRule: 评分规则（类型: string）
-     data.detail[].fullMark: 满分（类型: integer）
-     data.detail[].score: 得分（类型: integer）
-     data.detail[].remark: 备注（类型: string）
-     data.detail[].createBy: 创建人（类型: string）
-     data.detail[].createTime: 记录创建时间（类型: string）
-     data.detail[].updateBy: 更新人（类型: string）
-     data.detail[].updateTime: 记录更新时间（类型: string）
-     data.detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.supplierCode: /**（类型: string）
-   data.supplierName: 供应商名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：根据供应商ID和周期查询

- **路径：** GET /api/v1/supplier-evaluations/by-supplier
- **所属模块：** 供应商评价头
- **HTTP 方法：** GET

**功能说明：**
根据供应商ID和周期查询

**请求参数：**
- supplierId （查询参数）: （类型: integer，必填）
- evalCycle （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商评价头 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.evalCycle: 评价周期（类型: string）
-   data.qualityScore: 产品质量分（类型: integer）
-   data.deliveryScore: 供货情况分（类型: integer）
-   data.priceScore: 价格分（类型: integer）
-   data.serviceScore: 服务分（类型: integer）
-   data.prodCapacity: 生产能力分（类型: integer）
-   data.mgmtSystem: 管理体系分（类型: integer）
-   data.financialScore: 财务状况分（类型: integer）
-   data.totalScore: 总分（类型: integer）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].evaluationId: 评价头ID（类型: integer）
-     data.detail[].scoreDimension: 评分维度（类型: string）
-     data.detail[].scoreDescription: 维度说明（类型: string）
-     data.detail[].scoreStandard: 评分标准（类型: string）
-     data.detail[].scoreRule: 评分规则（类型: string）
-     data.detail[].fullMark: 满分（类型: integer）
-     data.detail[].score: 得分（类型: integer）
-     data.detail[].remark: 备注（类型: string）
-     data.detail[].createBy: 创建人（类型: string）
-     data.detail[].createTime: 记录创建时间（类型: string）
-     data.detail[].updateBy: 更新人（类型: string）
-     data.detail[].updateTime: 记录更新时间（类型: string）
-     data.detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.supplierCode: /**（类型: string）
-   data.supplierName: 供应商名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：查询

- **路径：** GET /api/v1/supplier-evaluations/{id}
- **所属模块：** 供应商评价头
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商评价头 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.evalCycle: 评价周期（类型: string）
-   data.qualityScore: 产品质量分（类型: integer）
-   data.deliveryScore: 供货情况分（类型: integer）
-   data.priceScore: 价格分（类型: integer）
-   data.serviceScore: 服务分（类型: integer）
-   data.prodCapacity: 生产能力分（类型: integer）
-   data.mgmtSystem: 管理体系分（类型: integer）
-   data.financialScore: 财务状况分（类型: integer）
-   data.totalScore: 总分（类型: integer）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].evaluationId: 评价头ID（类型: integer）
-     data.detail[].scoreDimension: 评分维度（类型: string）
-     data.detail[].scoreDescription: 维度说明（类型: string）
-     data.detail[].scoreStandard: 评分标准（类型: string）
-     data.detail[].scoreRule: 评分规则（类型: string）
-     data.detail[].fullMark: 满分（类型: integer）
-     data.detail[].score: 得分（类型: integer）
-     data.detail[].remark: 备注（类型: string）
-     data.detail[].createBy: 创建人（类型: string）
-     data.detail[].createTime: 记录创建时间（类型: string）
-     data.detail[].updateBy: 更新人（类型: string）
-     data.detail[].updateTime: 记录更新时间（类型: string）
-     data.detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.supplierCode: /**（类型: string）
-   data.supplierName: 供应商名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/supplier-evaluations/{id}
- **所属模块：** 供应商评价头
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- supplierId: 供应商ID（类型: integer，可选）
- evalCycle: 评价周期（类型: string，可选）
- qualityScore: 产品质量分（类型: integer，可选）
- deliveryScore: 供货情况分（类型: integer，可选）
- priceScore: 价格分（类型: integer，可选）
- serviceScore: 服务分（类型: integer，可选）
- prodCapacity: 生产能力分（类型: integer，可选）
- mgmtSystem: 管理体系分（类型: integer，可选）
- financialScore: 财务状况分（类型: integer，可选）
- totalScore: 总分（类型: integer，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].evaluationId: 评价头ID（类型: integer）
-   detail[].scoreDimension: 评分维度（类型: string）
-   detail[].scoreDescription: 维度说明（类型: string）
-   detail[].scoreStandard: 评分标准（类型: string）
-   detail[].scoreRule: 评分规则（类型: string）
-   detail[].fullMark: 满分（类型: integer）
-   detail[].score: 得分（类型: integer）
-   detail[].remark: 备注（类型: string）
-   detail[].createBy: 创建人（类型: string）
-   detail[].createTime: 记录创建时间（类型: string）
-   detail[].updateBy: 更新人（类型: string）
-   detail[].updateTime: 记录更新时间（类型: string）
-   detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
- supplierCode: /**（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商评价头 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.evalCycle: 评价周期（类型: string）
-   data.qualityScore: 产品质量分（类型: integer）
-   data.deliveryScore: 供货情况分（类型: integer）
-   data.priceScore: 价格分（类型: integer）
-   data.serviceScore: 服务分（类型: integer）
-   data.prodCapacity: 生产能力分（类型: integer）
-   data.mgmtSystem: 管理体系分（类型: integer）
-   data.financialScore: 财务状况分（类型: integer）
-   data.totalScore: 总分（类型: integer）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].evaluationId: 评价头ID（类型: integer）
-     data.detail[].scoreDimension: 评分维度（类型: string）
-     data.detail[].scoreDescription: 维度说明（类型: string）
-     data.detail[].scoreStandard: 评分标准（类型: string）
-     data.detail[].scoreRule: 评分规则（类型: string）
-     data.detail[].fullMark: 满分（类型: integer）
-     data.detail[].score: 得分（类型: integer）
-     data.detail[].remark: 备注（类型: string）
-     data.detail[].createBy: 创建人（类型: string）
-     data.detail[].createTime: 记录创建时间（类型: string）
-     data.detail[].updateBy: 更新人（类型: string）
-     data.detail[].updateTime: 记录更新时间（类型: string）
-     data.detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.supplierCode: /**（类型: string）
-   data.supplierName: 供应商名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/supplier-evaluations/{id}
- **所属模块：** 供应商评价头
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/supplier-evaluation-details
- **所属模块：** 供应商评价明细
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/supplier-evaluation-details
- **所属模块：** 供应商评价明细
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- evaluationId: 评价头ID（类型: integer，可选）
- scoreDimension: 评分维度（类型: string，可选）
- scoreDescription: 维度说明（类型: string，可选）
- scoreStandard: 评分标准（类型: string，可选）
- scoreRule: 评分规则（类型: string，可选）
- fullMark: 满分（类型: integer，可选）
- score: 得分（类型: integer，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商评价明细 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.evaluationId: 评价头ID（类型: integer）
-   data.scoreDimension: 评分维度（类型: string）
-   data.scoreDescription: 维度说明（类型: string）
-   data.scoreStandard: 评分标准（类型: string）
-   data.scoreRule: 评分规则（类型: string）
-   data.fullMark: 满分（类型: integer）
-   data.score: 得分（类型: integer）
-   data.remark: 备注（类型: string）
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

- **路径：** GET /api/v1/supplier-evaluation-details/{id}
- **所属模块：** 供应商评价明细
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商评价明细 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.evaluationId: 评价头ID（类型: integer）
-   data.scoreDimension: 评分维度（类型: string）
-   data.scoreDescription: 维度说明（类型: string）
-   data.scoreStandard: 评分标准（类型: string）
-   data.scoreRule: 评分规则（类型: string）
-   data.fullMark: 满分（类型: integer）
-   data.score: 得分（类型: integer）
-   data.remark: 备注（类型: string）
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

- **路径：** PUT /api/v1/supplier-evaluation-details/{id}
- **所属模块：** 供应商评价明细
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- evaluationId: 评价头ID（类型: integer，可选）
- scoreDimension: 评分维度（类型: string，可选）
- scoreDescription: 维度说明（类型: string，可选）
- scoreStandard: 评分标准（类型: string，可选）
- scoreRule: 评分规则（类型: string，可选）
- fullMark: 满分（类型: integer，可选）
- score: 得分（类型: integer，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商评价明细 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.evaluationId: 评价头ID（类型: integer）
-   data.scoreDimension: 评分维度（类型: string）
-   data.scoreDescription: 维度说明（类型: string）
-   data.scoreStandard: 评分标准（类型: string）
-   data.scoreRule: 评分规则（类型: string）
-   data.fullMark: 满分（类型: integer）
-   data.score: 得分（类型: integer）
-   data.remark: 备注（类型: string）
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

- **路径：** DELETE /api/v1/supplier-evaluation-details/{id}
- **所属模块：** 供应商评价明细
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/supplier-qualifications
- **所属模块：** 供应商资质
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/supplier-qualifications
- **所属模块：** 供应商资质
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- supplierId: 供应商ID（类型: integer，可选）
- certName: 证书名称（类型: string，可选）
- certCode: 证书编码（类型: string，可选）
- certType: 证书类别（类型: string，可选）
- certStatus: 证书状态（类型: string，可选）
- expireDate: 到期日期（类型: string，可选）
- remark: 备注（类型: string，可选）
- certFile: 证书附件（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商资质 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.certName: 证书名称（类型: string）
-   data.certCode: 证书编码（类型: string）
-   data.certType: 证书类别（类型: string）
-   data.certStatus: 证书状态（类型: string）
-   data.expireDate: 到期日期（类型: string）
-   data.remark: 备注（类型: string）
-   data.certFile: 证书附件（类型: string）
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

- **路径：** GET /api/v1/supplier-qualifications/{id}
- **所属模块：** 供应商资质
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商资质 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.certName: 证书名称（类型: string）
-   data.certCode: 证书编码（类型: string）
-   data.certType: 证书类别（类型: string）
-   data.certStatus: 证书状态（类型: string）
-   data.expireDate: 到期日期（类型: string）
-   data.remark: 备注（类型: string）
-   data.certFile: 证书附件（类型: string）
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

- **路径：** PUT /api/v1/supplier-qualifications/{id}
- **所属模块：** 供应商资质
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- supplierId: 供应商ID（类型: integer，可选）
- certName: 证书名称（类型: string，可选）
- certCode: 证书编码（类型: string，可选）
- certType: 证书类别（类型: string，可选）
- certStatus: 证书状态（类型: string，可选）
- expireDate: 到期日期（类型: string，可选）
- remark: 备注（类型: string，可选）
- certFile: 证书附件（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 供应商资质 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.certName: 证书名称（类型: string）
-   data.certCode: 证书编码（类型: string）
-   data.certType: 证书类别（类型: string）
-   data.certStatus: 证书状态（类型: string）
-   data.expireDate: 到期日期（类型: string）
-   data.remark: 备注（类型: string）
-   data.certFile: 证书附件（类型: string）
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

- **路径：** DELETE /api/v1/supplier-qualifications/{id}
- **所属模块：** 供应商资质
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/invoices
- **所属模块：** 发票
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/invoices
- **所属模块：** 发票
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- supplierId: 供应商ID（类型: integer，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- contact: 联系人（类型: string，可选）
- contactPhone: 联系电话（类型: string，可选）
- bankAccountName: 收款账户名称（类型: string，可选）
- bankName: 开户银行（类型: string，可选）
- bankAccount: 银行账号（类型: string，可选）
- purchaseOrderCode: 采购订单号（类型: string，可选）
- contractCode: 合同编号（类型: string，可选）
- contractFile: 合同附件（类型: string，可选）
- orderAmount: 订单金额（类型: number，可选）
- invoiceType: 发票类型（类型: string，可选）
- invoiceCode: 发票代码（类型: string，可选）
- invoiceNo: 发票号码（类型: string，可选）
- invoiceDate: 开票日期（类型: string，可选）
- invoiceAmount: 价税合计（类型: number，可选）
- taxRate: 税率（类型: number，可选）
- sellerName: 销售方名称（类型: string，可选）
- taxId: 纳税人识别号（类型: string，可选）
- invoiceFile: 发票附件（类型: string，可选）
- submitTime: 提交时间（类型: string，可选）
- isLocked: 是否锁定（类型: integer，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 发票 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.contact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.bankAccountName: 收款账户名称（类型: string）
-   data.bankName: 开户银行（类型: string）
-   data.bankAccount: 银行账号（类型: string）
-   data.purchaseOrderCode: 采购订单号（类型: string）
-   data.contractCode: 合同编号（类型: string）
-   data.contractFile: 合同附件（类型: string）
-   data.orderAmount: 订单金额（类型: number）
-   data.invoiceType: 发票类型（类型: string）
-   data.invoiceCode: 发票代码（类型: string）
-   data.invoiceNo: 发票号码（类型: string）
-   data.invoiceDate: 开票日期（类型: string）
-   data.invoiceAmount: 价税合计（类型: number）
-   data.taxRate: 税率（类型: number）
-   data.sellerName: 销售方名称（类型: string）
-   data.taxId: 纳税人识别号（类型: string）
-   data.invoiceFile: 发票附件（类型: string）
-   data.submitTime: 提交时间（类型: string）
-   data.isLocked: 是否锁定（类型: integer）
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

- **路径：** GET /api/v1/invoices/{id}
- **所属模块：** 发票
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 发票 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.contact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.bankAccountName: 收款账户名称（类型: string）
-   data.bankName: 开户银行（类型: string）
-   data.bankAccount: 银行账号（类型: string）
-   data.purchaseOrderCode: 采购订单号（类型: string）
-   data.contractCode: 合同编号（类型: string）
-   data.contractFile: 合同附件（类型: string）
-   data.orderAmount: 订单金额（类型: number）
-   data.invoiceType: 发票类型（类型: string）
-   data.invoiceCode: 发票代码（类型: string）
-   data.invoiceNo: 发票号码（类型: string）
-   data.invoiceDate: 开票日期（类型: string）
-   data.invoiceAmount: 价税合计（类型: number）
-   data.taxRate: 税率（类型: number）
-   data.sellerName: 销售方名称（类型: string）
-   data.taxId: 纳税人识别号（类型: string）
-   data.invoiceFile: 发票附件（类型: string）
-   data.submitTime: 提交时间（类型: string）
-   data.isLocked: 是否锁定（类型: integer）
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

- **路径：** PUT /api/v1/invoices/{id}
- **所属模块：** 发票
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- supplierId: 供应商ID（类型: integer，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- contact: 联系人（类型: string，可选）
- contactPhone: 联系电话（类型: string，可选）
- bankAccountName: 收款账户名称（类型: string，可选）
- bankName: 开户银行（类型: string，可选）
- bankAccount: 银行账号（类型: string，可选）
- purchaseOrderCode: 采购订单号（类型: string，可选）
- contractCode: 合同编号（类型: string，可选）
- contractFile: 合同附件（类型: string，可选）
- orderAmount: 订单金额（类型: number，可选）
- invoiceType: 发票类型（类型: string，可选）
- invoiceCode: 发票代码（类型: string，可选）
- invoiceNo: 发票号码（类型: string，可选）
- invoiceDate: 开票日期（类型: string，可选）
- invoiceAmount: 价税合计（类型: number，可选）
- taxRate: 税率（类型: number，可选）
- sellerName: 销售方名称（类型: string，可选）
- taxId: 纳税人识别号（类型: string，可选）
- invoiceFile: 发票附件（类型: string，可选）
- submitTime: 提交时间（类型: string，可选）
- isLocked: 是否锁定（类型: integer，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 发票 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.supplierId: 供应商ID（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.contact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.bankAccountName: 收款账户名称（类型: string）
-   data.bankName: 开户银行（类型: string）
-   data.bankAccount: 银行账号（类型: string）
-   data.purchaseOrderCode: 采购订单号（类型: string）
-   data.contractCode: 合同编号（类型: string）
-   data.contractFile: 合同附件（类型: string）
-   data.orderAmount: 订单金额（类型: number）
-   data.invoiceType: 发票类型（类型: string）
-   data.invoiceCode: 发票代码（类型: string）
-   data.invoiceNo: 发票号码（类型: string）
-   data.invoiceDate: 开票日期（类型: string）
-   data.invoiceAmount: 价税合计（类型: number）
-   data.taxRate: 税率（类型: number）
-   data.sellerName: 销售方名称（类型: string）
-   data.taxId: 纳税人识别号（类型: string）
-   data.invoiceFile: 发票附件（类型: string）
-   data.submitTime: 提交时间（类型: string）
-   data.isLocked: 是否锁定（类型: integer）
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

- **路径：** DELETE /api/v1/invoices/{id}
- **所属模块：** 发票
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/delivery-orders
- **所属模块：** 发货单
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/delivery-orders
- **所属模块：** 发货单
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- deliveryCode: 发货单号（类型: string，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- actualDeliveryTime: 发货时间（类型: string，可选）
- consignor: 发货人（类型: string，可选）
- consignorPhone: 发货人电话（类型: string，可选）
- expressNo: 物流单号（类型: string，可选）
- receiveWarehouseCode: 收货仓库id（类型: string，可选）
- receiveWarehouseName: 收货仓库名字（类型: string，可选）
- receiveAddress: 收货地址（类型: string，可选）
- consignee: 收货人（类型: string，可选）
- consigneePhone: 收货人电话（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].deliveryId: 发货单ID（类型: integer）
-   detail[].purchaseOrderItemId: 订单明细ID（类型: integer）
-   detail[].deliveryQty: 发货数量（类型: number）
-   detail[].applyCode: （类型: string）
-   detail[].lineNo: （类型: string）
-   detail[].materialCode: （类型: string）
-   detail[].materialName: （类型: string）
-   detail[].specModel: （类型: string）
-   detail[].unitName: （类型: string）
-   detail[].purchaseQty: （类型: number）
-   detail[].orderQty: （类型: number）
-   detail[].shippedQty: （类型: number）
-   detail[].unitPrice: （类型: number）
-   detail[].totalPrice: （类型: number）
- purchaseOrderCode: （类型: string，可选）
- supplierNames: （类型: string，可选）
- supplierIds: （类型: string，可选）
- deliveryContent: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 发货单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.deliveryCode: 发货单号（类型: string）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.actualDeliveryTime: 发货时间（类型: string）
-   data.consignor: 发货人（类型: string）
-   data.consignorPhone: 发货人电话（类型: string）
-   data.expressNo: 物流单号（类型: string）
-   data.receiveWarehouseCode: 收货仓库id（类型: string）
-   data.receiveWarehouseName: 收货仓库名字（类型: string）
-   data.receiveAddress: 收货地址（类型: string）
-   data.consignee: 收货人（类型: string）
-   data.consigneePhone: 收货人电话（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].deliveryId: 发货单ID（类型: integer）
-     data.detail[].purchaseOrderItemId: 订单明细ID（类型: integer）
-     data.detail[].deliveryQty: 发货数量（类型: number）
-     data.detail[].applyCode: （类型: string）
-     data.detail[].lineNo: （类型: string）
-     data.detail[].materialCode: （类型: string）
-     data.detail[].materialName: （类型: string）
-     data.detail[].specModel: （类型: string）
-     data.detail[].unitName: （类型: string）
-     data.detail[].purchaseQty: （类型: number）
-     data.detail[].orderQty: （类型: number）
-     data.detail[].shippedQty: （类型: number）
-     data.detail[].unitPrice: （类型: number）
-     data.detail[].totalPrice: （类型: number）
-   data.purchaseOrderCode: （类型: string）
-   data.supplierNames: （类型: string）
-   data.supplierIds: （类型: string）
-   data.deliveryContent: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/delivery-orders/{id}
- **所属模块：** 发货单
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 发货单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.deliveryCode: 发货单号（类型: string）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.actualDeliveryTime: 发货时间（类型: string）
-   data.consignor: 发货人（类型: string）
-   data.consignorPhone: 发货人电话（类型: string）
-   data.expressNo: 物流单号（类型: string）
-   data.receiveWarehouseCode: 收货仓库id（类型: string）
-   data.receiveWarehouseName: 收货仓库名字（类型: string）
-   data.receiveAddress: 收货地址（类型: string）
-   data.consignee: 收货人（类型: string）
-   data.consigneePhone: 收货人电话（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].deliveryId: 发货单ID（类型: integer）
-     data.detail[].purchaseOrderItemId: 订单明细ID（类型: integer）
-     data.detail[].deliveryQty: 发货数量（类型: number）
-     data.detail[].applyCode: （类型: string）
-     data.detail[].lineNo: （类型: string）
-     data.detail[].materialCode: （类型: string）
-     data.detail[].materialName: （类型: string）
-     data.detail[].specModel: （类型: string）
-     data.detail[].unitName: （类型: string）
-     data.detail[].purchaseQty: （类型: number）
-     data.detail[].orderQty: （类型: number）
-     data.detail[].shippedQty: （类型: number）
-     data.detail[].unitPrice: （类型: number）
-     data.detail[].totalPrice: （类型: number）
-   data.purchaseOrderCode: （类型: string）
-   data.supplierNames: （类型: string）
-   data.supplierIds: （类型: string）
-   data.deliveryContent: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/delivery-orders/{id}
- **所属模块：** 发货单
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- deliveryCode: 发货单号（类型: string，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- actualDeliveryTime: 发货时间（类型: string，可选）
- consignor: 发货人（类型: string，可选）
- consignorPhone: 发货人电话（类型: string，可选）
- expressNo: 物流单号（类型: string，可选）
- receiveWarehouseCode: 收货仓库id（类型: string，可选）
- receiveWarehouseName: 收货仓库名字（类型: string，可选）
- receiveAddress: 收货地址（类型: string，可选）
- consignee: 收货人（类型: string，可选）
- consigneePhone: 收货人电话（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].deliveryId: 发货单ID（类型: integer）
-   detail[].purchaseOrderItemId: 订单明细ID（类型: integer）
-   detail[].deliveryQty: 发货数量（类型: number）
-   detail[].applyCode: （类型: string）
-   detail[].lineNo: （类型: string）
-   detail[].materialCode: （类型: string）
-   detail[].materialName: （类型: string）
-   detail[].specModel: （类型: string）
-   detail[].unitName: （类型: string）
-   detail[].purchaseQty: （类型: number）
-   detail[].orderQty: （类型: number）
-   detail[].shippedQty: （类型: number）
-   detail[].unitPrice: （类型: number）
-   detail[].totalPrice: （类型: number）
- purchaseOrderCode: （类型: string，可选）
- supplierNames: （类型: string，可选）
- supplierIds: （类型: string，可选）
- deliveryContent: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 发货单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.deliveryCode: 发货单号（类型: string）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.actualDeliveryTime: 发货时间（类型: string）
-   data.consignor: 发货人（类型: string）
-   data.consignorPhone: 发货人电话（类型: string）
-   data.expressNo: 物流单号（类型: string）
-   data.receiveWarehouseCode: 收货仓库id（类型: string）
-   data.receiveWarehouseName: 收货仓库名字（类型: string）
-   data.receiveAddress: 收货地址（类型: string）
-   data.consignee: 收货人（类型: string）
-   data.consigneePhone: 收货人电话（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].deliveryId: 发货单ID（类型: integer）
-     data.detail[].purchaseOrderItemId: 订单明细ID（类型: integer）
-     data.detail[].deliveryQty: 发货数量（类型: number）
-     data.detail[].applyCode: （类型: string）
-     data.detail[].lineNo: （类型: string）
-     data.detail[].materialCode: （类型: string）
-     data.detail[].materialName: （类型: string）
-     data.detail[].specModel: （类型: string）
-     data.detail[].unitName: （类型: string）
-     data.detail[].purchaseQty: （类型: number）
-     data.detail[].orderQty: （类型: number）
-     data.detail[].shippedQty: （类型: number）
-     data.detail[].unitPrice: （类型: number）
-     data.detail[].totalPrice: （类型: number）
-   data.purchaseOrderCode: （类型: string）
-   data.supplierNames: （类型: string）
-   data.supplierIds: （类型: string）
-   data.deliveryContent: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/delivery-orders/{id}
- **所属模块：** 发货单
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/delivery-order-items
- **所属模块：** 发货单行
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/delivery-order-items
- **所属模块：** 发货单行
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- deliveryId: 发货单ID（类型: integer，可选）
- purchaseOrderItemId: 订单明细ID（类型: integer，可选）
- deliveryQty: 发货数量（类型: number，可选）
- applyCode: （类型: string，可选）
- lineNo: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- specModel: （类型: string，可选）
- unitName: （类型: string，可选）
- purchaseQty: （类型: number，可选）
- orderQty: （类型: number，可选）
- shippedQty: （类型: number，可选）
- unitPrice: （类型: number，可选）
- totalPrice: （类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 发货单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.deliveryId: 发货单ID（类型: integer）
-   data.purchaseOrderItemId: 订单明细ID（类型: integer）
-   data.deliveryQty: 发货数量（类型: number）
-   data.applyCode: （类型: string）
-   data.lineNo: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.specModel: （类型: string）
-   data.unitName: （类型: string）
-   data.purchaseQty: （类型: number）
-   data.orderQty: （类型: number）
-   data.shippedQty: （类型: number）
-   data.unitPrice: （类型: number）
-   data.totalPrice: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/delivery-order-items/{id}
- **所属模块：** 发货单行
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 发货单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.deliveryId: 发货单ID（类型: integer）
-   data.purchaseOrderItemId: 订单明细ID（类型: integer）
-   data.deliveryQty: 发货数量（类型: number）
-   data.applyCode: （类型: string）
-   data.lineNo: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.specModel: （类型: string）
-   data.unitName: （类型: string）
-   data.purchaseQty: （类型: number）
-   data.orderQty: （类型: number）
-   data.shippedQty: （类型: number）
-   data.unitPrice: （类型: number）
-   data.totalPrice: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/delivery-order-items/{id}
- **所属模块：** 发货单行
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- deliveryId: 发货单ID（类型: integer，可选）
- purchaseOrderItemId: 订单明细ID（类型: integer，可选）
- deliveryQty: 发货数量（类型: number，可选）
- applyCode: （类型: string，可选）
- lineNo: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- specModel: （类型: string，可选）
- unitName: （类型: string，可选）
- purchaseQty: （类型: number，可选）
- orderQty: （类型: number，可选）
- shippedQty: （类型: number，可选）
- unitPrice: （类型: number，可选）
- totalPrice: （类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 发货单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.deliveryId: 发货单ID（类型: integer）
-   data.purchaseOrderItemId: 订单明细ID（类型: integer）
-   data.deliveryQty: 发货数量（类型: number）
-   data.applyCode: （类型: string）
-   data.lineNo: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.specModel: （类型: string）
-   data.unitName: （类型: string）
-   data.purchaseQty: （类型: number）
-   data.orderQty: （类型: number）
-   data.shippedQty: （类型: number）
-   data.unitPrice: （类型: number）
-   data.totalPrice: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/delivery-order-items/{id}
- **所属模块：** 发货单行
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/receipt-orders
- **所属模块：** 收货单
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/receipt-orders
- **所属模块：** 收货单
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- receiptCode: 收货单号（类型: string，可选）
- wmsApplicationId: WMS来料收货申请主键（类型: integer，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- consignor: 发货人（类型: string，可选）
- consignorPhone: 发货人电话（类型: string，可选）
- expressNo: 物流单号（类型: string，可选）
- actualDeliveryTime: 发货时间（类型: string，可选）
- actualReceiveTime: 收货时间（类型: string，可选）
- receiveWarehouseCode: 收货仓库ID（类型: string，可选）
- receiveWarehouseName: 收货仓库（类型: string，可选）
- receiveAddress: 收货地址（类型: string，可选）
- consignee: 收货人（类型: string，可选）
- consigneePhone: 收货人电话（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].receiptId: 收货单ID（类型: integer）
-   detail[].purchaseOrderItemId: 订单明细ID（类型: integer）
-   detail[].receiptQty: 收货数量（类型: number）
-   detail[].isQc: 是否质检（类型: integer）
-   detail[].createBy: 创建人（类型: string）
-   detail[].createTime: 记录创建时间（类型: string）
-   detail[].updateBy: 更新人（类型: string）
-   detail[].updateTime: 记录更新时间（类型: string）
-   detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-   detail[].applyCode: （类型: string）
-   detail[].lineNo: （类型: string）
-   detail[].materialCode: （类型: string）
-   detail[].materialName: （类型: string）
-   detail[].specModel: （类型: string）
-   detail[].unitName: （类型: string）
-   detail[].orderQty: （类型: number）
-   detail[].totalReceiptQty: （类型: number）
-   detail[].unitPrice: （类型: number）
-   detail[].totalPrice: （类型: number）
- purchaseOrderCode: （类型: string，可选）
- supplierNames: （类型: string，可选）
- supplierIds: （类型: string，可选）
- receiptContent: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 收货单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.receiptCode: 收货单号（类型: string）
-   data.wmsApplicationId: WMS来料收货申请主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.consignor: 发货人（类型: string）
-   data.consignorPhone: 发货人电话（类型: string）
-   data.expressNo: 物流单号（类型: string）
-   data.actualDeliveryTime: 发货时间（类型: string）
-   data.actualReceiveTime: 收货时间（类型: string）
-   data.receiveWarehouseCode: 收货仓库ID（类型: string）
-   data.receiveWarehouseName: 收货仓库（类型: string）
-   data.receiveAddress: 收货地址（类型: string）
-   data.consignee: 收货人（类型: string）
-   data.consigneePhone: 收货人电话（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].receiptId: 收货单ID（类型: integer）
-     data.detail[].purchaseOrderItemId: 订单明细ID（类型: integer）
-     data.detail[].receiptQty: 收货数量（类型: number）
-     data.detail[].isQc: 是否质检（类型: integer）
-     data.detail[].createBy: 创建人（类型: string）
-     data.detail[].createTime: 记录创建时间（类型: string）
-     data.detail[].updateBy: 更新人（类型: string）
-     data.detail[].updateTime: 记录更新时间（类型: string）
-     data.detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-     data.detail[].applyCode: （类型: string）
-     data.detail[].lineNo: （类型: string）
-     data.detail[].materialCode: （类型: string）
-     data.detail[].materialName: （类型: string）
-     data.detail[].specModel: （类型: string）
-     data.detail[].unitName: （类型: string）
-     data.detail[].orderQty: （类型: number）
-     data.detail[].totalReceiptQty: （类型: number）
-     data.detail[].unitPrice: （类型: number）
-     data.detail[].totalPrice: （类型: number）
-   data.purchaseOrderCode: （类型: string）
-   data.supplierNames: （类型: string）
-   data.supplierIds: （类型: string）
-   data.receiptContent: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/receipt-orders/{id}
- **所属模块：** 收货单
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 收货单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.receiptCode: 收货单号（类型: string）
-   data.wmsApplicationId: WMS来料收货申请主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.consignor: 发货人（类型: string）
-   data.consignorPhone: 发货人电话（类型: string）
-   data.expressNo: 物流单号（类型: string）
-   data.actualDeliveryTime: 发货时间（类型: string）
-   data.actualReceiveTime: 收货时间（类型: string）
-   data.receiveWarehouseCode: 收货仓库ID（类型: string）
-   data.receiveWarehouseName: 收货仓库（类型: string）
-   data.receiveAddress: 收货地址（类型: string）
-   data.consignee: 收货人（类型: string）
-   data.consigneePhone: 收货人电话（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].receiptId: 收货单ID（类型: integer）
-     data.detail[].purchaseOrderItemId: 订单明细ID（类型: integer）
-     data.detail[].receiptQty: 收货数量（类型: number）
-     data.detail[].isQc: 是否质检（类型: integer）
-     data.detail[].createBy: 创建人（类型: string）
-     data.detail[].createTime: 记录创建时间（类型: string）
-     data.detail[].updateBy: 更新人（类型: string）
-     data.detail[].updateTime: 记录更新时间（类型: string）
-     data.detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-     data.detail[].applyCode: （类型: string）
-     data.detail[].lineNo: （类型: string）
-     data.detail[].materialCode: （类型: string）
-     data.detail[].materialName: （类型: string）
-     data.detail[].specModel: （类型: string）
-     data.detail[].unitName: （类型: string）
-     data.detail[].orderQty: （类型: number）
-     data.detail[].totalReceiptQty: （类型: number）
-     data.detail[].unitPrice: （类型: number）
-     data.detail[].totalPrice: （类型: number）
-   data.purchaseOrderCode: （类型: string）
-   data.supplierNames: （类型: string）
-   data.supplierIds: （类型: string）
-   data.receiptContent: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/receipt-orders/{id}
- **所属模块：** 收货单
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- receiptCode: 收货单号（类型: string，可选）
- wmsApplicationId: WMS来料收货申请主键（类型: integer，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- consignor: 发货人（类型: string，可选）
- consignorPhone: 发货人电话（类型: string，可选）
- expressNo: 物流单号（类型: string，可选）
- actualDeliveryTime: 发货时间（类型: string，可选）
- actualReceiveTime: 收货时间（类型: string，可选）
- receiveWarehouseCode: 收货仓库ID（类型: string，可选）
- receiveWarehouseName: 收货仓库（类型: string，可选）
- receiveAddress: 收货地址（类型: string，可选）
- consignee: 收货人（类型: string，可选）
- consigneePhone: 收货人电话（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].receiptId: 收货单ID（类型: integer）
-   detail[].purchaseOrderItemId: 订单明细ID（类型: integer）
-   detail[].receiptQty: 收货数量（类型: number）
-   detail[].isQc: 是否质检（类型: integer）
-   detail[].createBy: 创建人（类型: string）
-   detail[].createTime: 记录创建时间（类型: string）
-   detail[].updateBy: 更新人（类型: string）
-   detail[].updateTime: 记录更新时间（类型: string）
-   detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-   detail[].applyCode: （类型: string）
-   detail[].lineNo: （类型: string）
-   detail[].materialCode: （类型: string）
-   detail[].materialName: （类型: string）
-   detail[].specModel: （类型: string）
-   detail[].unitName: （类型: string）
-   detail[].orderQty: （类型: number）
-   detail[].totalReceiptQty: （类型: number）
-   detail[].unitPrice: （类型: number）
-   detail[].totalPrice: （类型: number）
- purchaseOrderCode: （类型: string，可选）
- supplierNames: （类型: string，可选）
- supplierIds: （类型: string，可选）
- receiptContent: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 收货单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.receiptCode: 收货单号（类型: string）
-   data.wmsApplicationId: WMS来料收货申请主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.consignor: 发货人（类型: string）
-   data.consignorPhone: 发货人电话（类型: string）
-   data.expressNo: 物流单号（类型: string）
-   data.actualDeliveryTime: 发货时间（类型: string）
-   data.actualReceiveTime: 收货时间（类型: string）
-   data.receiveWarehouseCode: 收货仓库ID（类型: string）
-   data.receiveWarehouseName: 收货仓库（类型: string）
-   data.receiveAddress: 收货地址（类型: string）
-   data.consignee: 收货人（类型: string）
-   data.consigneePhone: 收货人电话（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].receiptId: 收货单ID（类型: integer）
-     data.detail[].purchaseOrderItemId: 订单明细ID（类型: integer）
-     data.detail[].receiptQty: 收货数量（类型: number）
-     data.detail[].isQc: 是否质检（类型: integer）
-     data.detail[].createBy: 创建人（类型: string）
-     data.detail[].createTime: 记录创建时间（类型: string）
-     data.detail[].updateBy: 更新人（类型: string）
-     data.detail[].updateTime: 记录更新时间（类型: string）
-     data.detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-     data.detail[].applyCode: （类型: string）
-     data.detail[].lineNo: （类型: string）
-     data.detail[].materialCode: （类型: string）
-     data.detail[].materialName: （类型: string）
-     data.detail[].specModel: （类型: string）
-     data.detail[].unitName: （类型: string）
-     data.detail[].orderQty: （类型: number）
-     data.detail[].totalReceiptQty: （类型: number）
-     data.detail[].unitPrice: （类型: number）
-     data.detail[].totalPrice: （类型: number）
-   data.purchaseOrderCode: （类型: string）
-   data.supplierNames: （类型: string）
-   data.supplierIds: （类型: string）
-   data.receiptContent: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/receipt-orders/{id}
- **所属模块：** 收货单
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/receipt-order-items
- **所属模块：** 收货单行
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/receipt-order-items
- **所属模块：** 收货单行
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- receiptId: 收货单ID（类型: integer，可选）
- purchaseOrderItemId: 订单明细ID（类型: integer，可选）
- receiptQty: 收货数量（类型: number，可选）
- isQc: 是否质检（类型: integer，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- applyCode: （类型: string，可选）
- lineNo: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- specModel: （类型: string，可选）
- unitName: （类型: string，可选）
- orderQty: （类型: number，可选）
- totalReceiptQty: （类型: number，可选）
- unitPrice: （类型: number，可选）
- totalPrice: （类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 收货单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.receiptId: 收货单ID（类型: integer）
-   data.purchaseOrderItemId: 订单明细ID（类型: integer）
-   data.receiptQty: 收货数量（类型: number）
-   data.isQc: 是否质检（类型: integer）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.applyCode: （类型: string）
-   data.lineNo: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.specModel: （类型: string）
-   data.unitName: （类型: string）
-   data.orderQty: （类型: number）
-   data.totalReceiptQty: （类型: number）
-   data.unitPrice: （类型: number）
-   data.totalPrice: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/receipt-order-items/{id}
- **所属模块：** 收货单行
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 收货单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.receiptId: 收货单ID（类型: integer）
-   data.purchaseOrderItemId: 订单明细ID（类型: integer）
-   data.receiptQty: 收货数量（类型: number）
-   data.isQc: 是否质检（类型: integer）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.applyCode: （类型: string）
-   data.lineNo: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.specModel: （类型: string）
-   data.unitName: （类型: string）
-   data.orderQty: （类型: number）
-   data.totalReceiptQty: （类型: number）
-   data.unitPrice: （类型: number）
-   data.totalPrice: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/receipt-order-items/{id}
- **所属模块：** 收货单行
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- receiptId: 收货单ID（类型: integer，可选）
- purchaseOrderItemId: 订单明细ID（类型: integer，可选）
- receiptQty: 收货数量（类型: number，可选）
- isQc: 是否质检（类型: integer，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- applyCode: （类型: string，可选）
- lineNo: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- specModel: （类型: string，可选）
- unitName: （类型: string，可选）
- orderQty: （类型: number，可选）
- totalReceiptQty: （类型: number，可选）
- unitPrice: （类型: number，可选）
- totalPrice: （类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 收货单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.receiptId: 收货单ID（类型: integer）
-   data.purchaseOrderItemId: 订单明细ID（类型: integer）
-   data.receiptQty: 收货数量（类型: number）
-   data.isQc: 是否质检（类型: integer）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.applyCode: （类型: string）
-   data.lineNo: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.specModel: （类型: string）
-   data.unitName: （类型: string）
-   data.orderQty: （类型: number）
-   data.totalReceiptQty: （类型: number）
-   data.unitPrice: （类型: number）
-   data.totalPrice: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/receipt-order-items/{id}
- **所属模块：** 收货单行
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：异步同步检验单号（立即返回，请轮询 /status 查看 progressPercent）

- **路径：** POST /api/v1/receipt-qc-sync
- **所属模块：** 检验单号同步（WMS+QMS）
- **HTTP 方法：** POST

**功能说明：**
异步同步检验单号（立即返回，请轮询 /status 查看 progressPercent）

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 检验单号全量同步状态（类型: object）
-   data.syncing: 是否正在同步（类型: boolean）
-   data.allSynced: 是否已全部同步完成（无待同步收货明细）（类型: boolean）
-   data.totalCount: 当前任务总步骤数（异步进行中取最新任务）（类型: integer）
-   data.processedCount: 当前任务已完成步骤数（类型: integer）
-   data.progressPercent: 进度百分比 0-100（processedCount/totalCount）（类型: integer）
-   data.lastTaskStatus: 最近一次任务状态：1同步中 2已完成 3失败，无任务时为 null（类型: integer）
-   data.lastTaskStatusDesc: 最近一次任务状态描述（类型: string）
-   data.lastErrorMessage: 最近一次任务失败原因（类型: string）
-   data.lastTaskId: 最近一次任务 ID（类型: integer）
-   data.lastSyncTime: 最近一次任务结束时间（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么异步同步检验单号（立即返回，请轮询 /status 查看 progressPercent）？"
- "异步同步检验单号（立即返回，请轮询 /status 查看 progressPercent）怎么做？"
- "帮我异步同步检验单号（立即返回，请轮询 /status 查看 progressPercent）"

## 接口：查询全量同步状态

- **路径：** GET /api/v1/receipt-qc-sync/status
- **所属模块：** 检验单号同步（WMS+QMS）
- **HTTP 方法：** GET

**功能说明：**
查询全量同步状态

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 检验单号全量同步状态（类型: object）
-   data.syncing: 是否正在同步（类型: boolean）
-   data.allSynced: 是否已全部同步完成（无待同步收货明细）（类型: boolean）
-   data.totalCount: 当前任务总步骤数（异步进行中取最新任务）（类型: integer）
-   data.processedCount: 当前任务已完成步骤数（类型: integer）
-   data.progressPercent: 进度百分比 0-100（processedCount/totalCount）（类型: integer）
-   data.lastTaskStatus: 最近一次任务状态：1同步中 2已完成 3失败，无任务时为 null（类型: integer）
-   data.lastTaskStatusDesc: 最近一次任务状态描述（类型: string）
-   data.lastErrorMessage: 最近一次任务失败原因（类型: string）
-   data.lastTaskId: 最近一次任务 ID（类型: integer）
-   data.lastSyncTime: 最近一次任务结束时间（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询全量同步状态"
- "查看全量同步状态列表"
- "有哪些全量同步状态？"
- "列出全量同步状态"
- "哪些{thing}还未完成？"
- "全量同步状态进展如何？"

## 接口：查询分页

- **路径：** GET /api/v1/quality-inspections
- **所属模块：** 质检单
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/quality-inspections
- **所属模块：** 质检单
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- qcCode: 检验单号（类型: string，可选）
- receiptOrderItemId: 收货明细ID（类型: integer，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- purchaseOrderCode: 采购订单编码（类型: string，可选）
- supplierId: 供应商ID（类型: integer，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- materialId: 物料id（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- materialTypeCode: 物料类型编码（类型: string，可选）
- materialTypeName: 物料类型名称（类型: string，可选）
- materialClassCode: 物料分类编码（类型: string，可选）
- materialClassName: 物料分类名称（类型: string，可选）
- unitId: 计量单位编码（类型: string，可选）
- unitName: 计量单位名称（类型: string，可选）
- specModel: 规格型号（类型: string，可选）
- batchNo: 批次号（类型: string，可选）
- incomingQty: 来料数量（类型: number，可选）
- checkQty: 抽检数量（类型: number，可选）
- badQty: 不良数量（类型: number，可选）
- badRate: 不良率（类型: number，可选）
- qcResult: 检验结果（类型: string，可选）
- qcTime: 检验时间（类型: string，可选）
- inspector: 检验员（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 质检单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.qcCode: 检验单号（类型: string）
-   data.receiptOrderItemId: 收货明细ID（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.purchaseOrderCode: 采购订单编码（类型: string）
-   data.supplierId: 供应商ID（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位编码（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.batchNo: 批次号（类型: string）
-   data.incomingQty: 来料数量（类型: number）
-   data.checkQty: 抽检数量（类型: number）
-   data.badQty: 不良数量（类型: number）
-   data.badRate: 不良率（类型: number）
-   data.qcResult: 检验结果（类型: string）
-   data.qcTime: 检验时间（类型: string）
-   data.inspector: 检验员（类型: string）
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

## 接口：月度质检统计

- **路径：** GET /api/v1/quality-inspections/monthly-stats
- **所属模块：** 质检单
- **HTTP 方法：** GET

**功能说明：**
月度质检统计

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].month: （类型: string）
-   data[].qcCount: （类型: integer）
-   data[].passCount: （类型: integer）
-   data[].passRate: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看月度质检统计"
- "怎么月度质检统计？"
- "月度质检统计怎么做？"
- "帮我月度质检统计"

## 接口：查询

- **路径：** GET /api/v1/quality-inspections/{id}
- **所属模块：** 质检单
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 质检单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.qcCode: 检验单号（类型: string）
-   data.receiptOrderItemId: 收货明细ID（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.purchaseOrderCode: 采购订单编码（类型: string）
-   data.supplierId: 供应商ID（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位编码（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.batchNo: 批次号（类型: string）
-   data.incomingQty: 来料数量（类型: number）
-   data.checkQty: 抽检数量（类型: number）
-   data.badQty: 不良数量（类型: number）
-   data.badRate: 不良率（类型: number）
-   data.qcResult: 检验结果（类型: string）
-   data.qcTime: 检验时间（类型: string）
-   data.inspector: 检验员（类型: string）
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

- **路径：** PUT /api/v1/quality-inspections/{id}
- **所属模块：** 质检单
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- qcCode: 检验单号（类型: string，可选）
- receiptOrderItemId: 收货明细ID（类型: integer，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- purchaseOrderCode: 采购订单编码（类型: string，可选）
- supplierId: 供应商ID（类型: integer，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- materialId: 物料id（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- materialTypeCode: 物料类型编码（类型: string，可选）
- materialTypeName: 物料类型名称（类型: string，可选）
- materialClassCode: 物料分类编码（类型: string，可选）
- materialClassName: 物料分类名称（类型: string，可选）
- unitId: 计量单位编码（类型: string，可选）
- unitName: 计量单位名称（类型: string，可选）
- specModel: 规格型号（类型: string，可选）
- batchNo: 批次号（类型: string，可选）
- incomingQty: 来料数量（类型: number，可选）
- checkQty: 抽检数量（类型: number，可选）
- badQty: 不良数量（类型: number，可选）
- badRate: 不良率（类型: number，可选）
- qcResult: 检验结果（类型: string，可选）
- qcTime: 检验时间（类型: string，可选）
- inspector: 检验员（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 质检单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.qcCode: 检验单号（类型: string）
-   data.receiptOrderItemId: 收货明细ID（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.purchaseOrderCode: 采购订单编码（类型: string）
-   data.supplierId: 供应商ID（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位编码（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.batchNo: 批次号（类型: string）
-   data.incomingQty: 来料数量（类型: number）
-   data.checkQty: 抽检数量（类型: number）
-   data.badQty: 不良数量（类型: number）
-   data.badRate: 不良率（类型: number）
-   data.qcResult: 检验结果（类型: string）
-   data.qcTime: 检验时间（类型: string）
-   data.inspector: 检验员（类型: string）
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

- **路径：** DELETE /api/v1/quality-inspections/{id}
- **所属模块：** 质检单
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/return-orders
- **所属模块：** 退货单
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/return-orders
- **所属模块：** 退货单
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- returnCode: 退货单号（类型: string，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- returnTime: 退货日期（类型: string，可选）
- returner: 退货人（类型: string，可选）
- supplierContact: 联系人（类型: string，可选）
- contactPhone: 联系电话（类型: string，可选）
- returnAmount: 退货金额（类型: number，可选）
- returnReason: 退货原因（类型: string，可选）
- attachment: 附件（类型: string，可选）
- submitTime: 提交时间（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].returnId: 退货单ID（类型: integer）
-   detail[].purchaseOrderItemId: 采购订单明细ID（类型: integer）
-   detail[].returnQty: 退货数量（类型: number）
-   detail[].createBy: 创建人（类型: string）
-   detail[].createTime: 记录创建时间（类型: string）
-   detail[].updateBy: 更新人（类型: string）
-   detail[].updateTime: 记录更新时间（类型: string）
-   detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-   detail[].applyCode: （类型: string）
-   detail[].lineNo: （类型: string）
-   detail[].materialCode: （类型: string）
-   detail[].materialName: （类型: string）
-   detail[].specModel: （类型: string）
-   detail[].unitName: （类型: string）
-   detail[].orderQty: （类型: number）
-   detail[].shippedQty: （类型: number）
-   detail[].totalReceiptQty: （类型: number）
-   detail[].totalReturnQty: （类型: number）
-   detail[].unitPrice: （类型: number）
-   detail[].totalPrice: （类型: number）
- purchaseOrderCode: （类型: string，可选）
- returnContent: （类型: string，可选）
- supplierIds: （类型: string，可选）
- supplierNames: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 退货单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.returnCode: 退货单号（类型: string）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.returnTime: 退货日期（类型: string）
-   data.returner: 退货人（类型: string）
-   data.supplierContact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.returnAmount: 退货金额（类型: number）
-   data.returnReason: 退货原因（类型: string）
-   data.attachment: 附件（类型: string）
-   data.submitTime: 提交时间（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].returnId: 退货单ID（类型: integer）
-     data.detail[].purchaseOrderItemId: 采购订单明细ID（类型: integer）
-     data.detail[].returnQty: 退货数量（类型: number）
-     data.detail[].createBy: 创建人（类型: string）
-     data.detail[].createTime: 记录创建时间（类型: string）
-     data.detail[].updateBy: 更新人（类型: string）
-     data.detail[].updateTime: 记录更新时间（类型: string）
-     data.detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-     data.detail[].applyCode: （类型: string）
-     data.detail[].lineNo: （类型: string）
-     data.detail[].materialCode: （类型: string）
-     data.detail[].materialName: （类型: string）
-     data.detail[].specModel: （类型: string）
-     data.detail[].unitName: （类型: string）
-     data.detail[].orderQty: （类型: number）
-     data.detail[].shippedQty: （类型: number）
-     data.detail[].totalReceiptQty: （类型: number）
-     data.detail[].totalReturnQty: （类型: number）
-     data.detail[].unitPrice: （类型: number）
-     data.detail[].totalPrice: （类型: number）
-   data.purchaseOrderCode: （类型: string）
-   data.returnContent: （类型: string）
-   data.supplierIds: （类型: string）
-   data.supplierNames: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：退货物料排行

- **路径：** GET /api/v1/return-orders/material-rank
- **所属模块：** 退货单
- **HTTP 方法：** GET

**功能说明：**
退货物料排行

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].materialName: （类型: string）
-   data[].returnCount: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "怎么退货物料排行？"
- "退货物料排行怎么做？"
- "帮我退货物料排行"

## 接口：查询

- **路径：** GET /api/v1/return-orders/{id}
- **所属模块：** 退货单
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 退货单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.returnCode: 退货单号（类型: string）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.returnTime: 退货日期（类型: string）
-   data.returner: 退货人（类型: string）
-   data.supplierContact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.returnAmount: 退货金额（类型: number）
-   data.returnReason: 退货原因（类型: string）
-   data.attachment: 附件（类型: string）
-   data.submitTime: 提交时间（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].returnId: 退货单ID（类型: integer）
-     data.detail[].purchaseOrderItemId: 采购订单明细ID（类型: integer）
-     data.detail[].returnQty: 退货数量（类型: number）
-     data.detail[].createBy: 创建人（类型: string）
-     data.detail[].createTime: 记录创建时间（类型: string）
-     data.detail[].updateBy: 更新人（类型: string）
-     data.detail[].updateTime: 记录更新时间（类型: string）
-     data.detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-     data.detail[].applyCode: （类型: string）
-     data.detail[].lineNo: （类型: string）
-     data.detail[].materialCode: （类型: string）
-     data.detail[].materialName: （类型: string）
-     data.detail[].specModel: （类型: string）
-     data.detail[].unitName: （类型: string）
-     data.detail[].orderQty: （类型: number）
-     data.detail[].shippedQty: （类型: number）
-     data.detail[].totalReceiptQty: （类型: number）
-     data.detail[].totalReturnQty: （类型: number）
-     data.detail[].unitPrice: （类型: number）
-     data.detail[].totalPrice: （类型: number）
-   data.purchaseOrderCode: （类型: string）
-   data.returnContent: （类型: string）
-   data.supplierIds: （类型: string）
-   data.supplierNames: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/return-orders/{id}
- **所属模块：** 退货单
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- returnCode: 退货单号（类型: string，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- returnTime: 退货日期（类型: string，可选）
- returner: 退货人（类型: string，可选）
- supplierContact: 联系人（类型: string，可选）
- contactPhone: 联系电话（类型: string，可选）
- returnAmount: 退货金额（类型: number，可选）
- returnReason: 退货原因（类型: string，可选）
- attachment: 附件（类型: string，可选）
- submitTime: 提交时间（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].returnId: 退货单ID（类型: integer）
-   detail[].purchaseOrderItemId: 采购订单明细ID（类型: integer）
-   detail[].returnQty: 退货数量（类型: number）
-   detail[].createBy: 创建人（类型: string）
-   detail[].createTime: 记录创建时间（类型: string）
-   detail[].updateBy: 更新人（类型: string）
-   detail[].updateTime: 记录更新时间（类型: string）
-   detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-   detail[].applyCode: （类型: string）
-   detail[].lineNo: （类型: string）
-   detail[].materialCode: （类型: string）
-   detail[].materialName: （类型: string）
-   detail[].specModel: （类型: string）
-   detail[].unitName: （类型: string）
-   detail[].orderQty: （类型: number）
-   detail[].shippedQty: （类型: number）
-   detail[].totalReceiptQty: （类型: number）
-   detail[].totalReturnQty: （类型: number）
-   detail[].unitPrice: （类型: number）
-   detail[].totalPrice: （类型: number）
- purchaseOrderCode: （类型: string，可选）
- returnContent: （类型: string，可选）
- supplierIds: （类型: string，可选）
- supplierNames: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 退货单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.returnCode: 退货单号（类型: string）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.returnTime: 退货日期（类型: string）
-   data.returner: 退货人（类型: string）
-   data.supplierContact: 联系人（类型: string）
-   data.contactPhone: 联系电话（类型: string）
-   data.returnAmount: 退货金额（类型: number）
-   data.returnReason: 退货原因（类型: string）
-   data.attachment: 附件（类型: string）
-   data.submitTime: 提交时间（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].returnId: 退货单ID（类型: integer）
-     data.detail[].purchaseOrderItemId: 采购订单明细ID（类型: integer）
-     data.detail[].returnQty: 退货数量（类型: number）
-     data.detail[].createBy: 创建人（类型: string）
-     data.detail[].createTime: 记录创建时间（类型: string）
-     data.detail[].updateBy: 更新人（类型: string）
-     data.detail[].updateTime: 记录更新时间（类型: string）
-     data.detail[].isValid: 是否有效：1=有效，0=无效（类型: integer）
-     data.detail[].applyCode: （类型: string）
-     data.detail[].lineNo: （类型: string）
-     data.detail[].materialCode: （类型: string）
-     data.detail[].materialName: （类型: string）
-     data.detail[].specModel: （类型: string）
-     data.detail[].unitName: （类型: string）
-     data.detail[].orderQty: （类型: number）
-     data.detail[].shippedQty: （类型: number）
-     data.detail[].totalReceiptQty: （类型: number）
-     data.detail[].totalReturnQty: （类型: number）
-     data.detail[].unitPrice: （类型: number）
-     data.detail[].totalPrice: （类型: number）
-   data.purchaseOrderCode: （类型: string）
-   data.returnContent: （类型: string）
-   data.supplierIds: （类型: string）
-   data.supplierNames: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/return-orders/{id}
- **所属模块：** 退货单
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/return-order-items
- **所属模块：** 退货单行
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/return-order-items
- **所属模块：** 退货单行
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- returnId: 退货单ID（类型: integer，可选）
- purchaseOrderItemId: 采购订单明细ID（类型: integer，可选）
- returnQty: 退货数量（类型: number，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- applyCode: （类型: string，可选）
- lineNo: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- specModel: （类型: string，可选）
- unitName: （类型: string，可选）
- orderQty: （类型: number，可选）
- shippedQty: （类型: number，可选）
- totalReceiptQty: （类型: number，可选）
- totalReturnQty: （类型: number，可选）
- unitPrice: （类型: number，可选）
- totalPrice: （类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 退货单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.returnId: 退货单ID（类型: integer）
-   data.purchaseOrderItemId: 采购订单明细ID（类型: integer）
-   data.returnQty: 退货数量（类型: number）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.applyCode: （类型: string）
-   data.lineNo: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.specModel: （类型: string）
-   data.unitName: （类型: string）
-   data.orderQty: （类型: number）
-   data.shippedQty: （类型: number）
-   data.totalReceiptQty: （类型: number）
-   data.totalReturnQty: （类型: number）
-   data.unitPrice: （类型: number）
-   data.totalPrice: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/return-order-items/{id}
- **所属模块：** 退货单行
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 退货单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.returnId: 退货单ID（类型: integer）
-   data.purchaseOrderItemId: 采购订单明细ID（类型: integer）
-   data.returnQty: 退货数量（类型: number）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.applyCode: （类型: string）
-   data.lineNo: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.specModel: （类型: string）
-   data.unitName: （类型: string）
-   data.orderQty: （类型: number）
-   data.shippedQty: （类型: number）
-   data.totalReceiptQty: （类型: number）
-   data.totalReturnQty: （类型: number）
-   data.unitPrice: （类型: number）
-   data.totalPrice: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/return-order-items/{id}
- **所属模块：** 退货单行
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- returnId: 退货单ID（类型: integer，可选）
- purchaseOrderItemId: 采购订单明细ID（类型: integer，可选）
- returnQty: 退货数量（类型: number，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- applyCode: （类型: string，可选）
- lineNo: （类型: string，可选）
- materialCode: （类型: string，可选）
- materialName: （类型: string，可选）
- specModel: （类型: string，可选）
- unitName: （类型: string，可选）
- orderQty: （类型: number，可选）
- shippedQty: （类型: number，可选）
- totalReceiptQty: （类型: number，可选）
- totalReturnQty: （类型: number，可选）
- unitPrice: （类型: number，可选）
- totalPrice: （类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 退货单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.returnId: 退货单ID（类型: integer）
-   data.purchaseOrderItemId: 采购订单明细ID（类型: integer）
-   data.returnQty: 退货数量（类型: number）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.applyCode: （类型: string）
-   data.lineNo: （类型: string）
-   data.materialCode: （类型: string）
-   data.materialName: （类型: string）
-   data.specModel: （类型: string）
-   data.unitName: （类型: string）
-   data.orderQty: （类型: number）
-   data.shippedQty: （类型: number）
-   data.totalReceiptQty: （类型: number）
-   data.totalReturnQty: （类型: number）
-   data.unitPrice: （类型: number）
-   data.totalPrice: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/return-order-items/{id}
- **所属模块：** 退货单行
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/purchase-requests
- **所属模块：** 采购申请单
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/purchase-requests
- **所属模块：** 采购申请单
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- applyCode: 申请单号（类型: string，可选）
- purchaseType: 采购类型（类型: string，可选）
- applicant: 申请人（类型: string，可选）
- applyTime: 申请时间（类型: string，可选）
- expectTime: 期望到货时间（类型: string，可选）
- purchaseStatus: 状态（类型: string，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].requestId: 申请单ID（类型: integer）
-   detail[].lineNo: 申请单行号（类型: string）
-   detail[].supplierId: 供应商id（类型: integer）
-   detail[].purchaseItemStatus: 明细状态（类型: string）
-   detail[].supplierCode: 供应商编码（类型: string）
-   detail[].supplierName: 供应商名称（类型: string）
-   detail[].materialId: 物料id（类型: string）
-   detail[].materialCode: 物料编码（类型: string）
-   detail[].materialName: 物料名称（类型: string）
-   detail[].materialTypeCode: 物料类型编码（类型: string）
-   detail[].materialTypeName: 物料类型名称（类型: string）
-   detail[].materialClassCode: 物料分类编码（类型: string）
-   detail[].materialClassName: 物料分类名称（类型: string）
-   detail[].unitId: 计量单位编码（类型: string）
-   detail[].unitName: 计量单位名称（类型: string）
-   detail[].specModel: 规格型号（类型: string）
-   detail[].qty: 申请数量（类型: number）
-   detail[].purchasedQty: 已采购数量（类型: number）
-   detail[].applyCode: 申请单号（类型: string）
-   detail[].purchaseType: 采购类型（类型: string）
-   detail[].applicant: 申请人（类型: string）
-   detail[].applyTime: 申请时间（类型: string）
-   detail[].expectTime: 期望到货时间（类型: string）
-   detail[].purchaseStatus: 需求状态（类型: string）
-   detail[].remark: 申请备注（类型: string）
-   detail[].inCart: 是否加入（类型: integer）
-   detail[].cartId: （类型: integer）
-   detail[].purchaseOrderCodes: （类型: string）
- materialNames: 物料名称（类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购申请单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.applyCode: 申请单号（类型: string）
-   data.purchaseType: 采购类型（类型: string）
-   data.applicant: 申请人（类型: string）
-   data.applyTime: 申请时间（类型: string）
-   data.expectTime: 期望到货时间（类型: string）
-   data.purchaseStatus: 状态（类型: string）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].requestId: 申请单ID（类型: integer）
-     data.detail[].lineNo: 申请单行号（类型: string）
-     data.detail[].supplierId: 供应商id（类型: integer）
-     data.detail[].purchaseItemStatus: 明细状态（类型: string）
-     data.detail[].supplierCode: 供应商编码（类型: string）
-     data.detail[].supplierName: 供应商名称（类型: string）
-     data.detail[].materialId: 物料id（类型: string）
-     data.detail[].materialCode: 物料编码（类型: string）
-     data.detail[].materialName: 物料名称（类型: string）
-     data.detail[].materialTypeCode: 物料类型编码（类型: string）
-     data.detail[].materialTypeName: 物料类型名称（类型: string）
-     data.detail[].materialClassCode: 物料分类编码（类型: string）
-     data.detail[].materialClassName: 物料分类名称（类型: string）
-     data.detail[].unitId: 计量单位编码（类型: string）
-     data.detail[].unitName: 计量单位名称（类型: string）
-     data.detail[].specModel: 规格型号（类型: string）
-     data.detail[].qty: 申请数量（类型: number）
-     data.detail[].purchasedQty: 已采购数量（类型: number）
-     data.detail[].applyCode: 申请单号（类型: string）
-     data.detail[].purchaseType: 采购类型（类型: string）
-     data.detail[].applicant: 申请人（类型: string）
-     data.detail[].applyTime: 申请时间（类型: string）
-     data.detail[].expectTime: 期望到货时间（类型: string）
-     data.detail[].purchaseStatus: 需求状态（类型: string）
-     data.detail[].remark: 申请备注（类型: string）
-     data.detail[].inCart: 是否加入（类型: integer）
-     data.detail[].cartId: （类型: integer）
-     data.detail[].purchaseOrderCodes: （类型: string）
-   data.materialNames: 物料名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：物料需求量排行

- **路径：** GET /api/v1/purchase-requests/material-demand-rank
- **所属模块：** 采购申请单
- **HTTP 方法：** GET

**功能说明：**
物料需求量排行

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].code: （类型: string）
-   data[].value: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "怎么物料需求量排行？"
- "物料需求量排行怎么做？"
- "帮我物料需求量排行"

## 接口：按月统计采购需求

- **路径：** GET /api/v1/purchase-requests/monthly-stats
- **所属模块：** 采购申请单
- **HTTP 方法：** GET

**功能说明：**
按月统计采购需求

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].month: （类型: string）
-   data[].count: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看按月统计采购需求"
- "需求情况怎么样？"
- "有多少需求？"
- "需求转化如何？"

## 接口：采购类型统计

- **路径：** GET /api/v1/purchase-requests/purchase-type-stats
- **所属模块：** 采购申请单
- **HTTP 方法：** GET

**功能说明：**
采购类型统计

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].code: （类型: string）
-   data[].value: （类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查看采购类型统计"
- "怎么采购类型统计？"
- "采购类型统计怎么做？"
- "帮我采购类型统计"

## 接口：采购需求统计

- **路径：** GET /api/v1/purchase-requests/stats
- **所属模块：** 采购申请单
- **HTTP 方法：** GET

**功能说明：**
采购需求统计

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalCount: （类型: integer）
-   data.pendingCount: （类型: integer）
-   data.purchasingCount: （类型: integer）
-   data.purchasedCount: （类型: integer）
-   data.purchasedAmount: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看采购需求统计"
- "需求情况怎么样？"
- "有多少需求？"
- "需求转化如何？"

## 接口：查询

- **路径：** GET /api/v1/purchase-requests/{id}
- **所属模块：** 采购申请单
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购申请单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.applyCode: 申请单号（类型: string）
-   data.purchaseType: 采购类型（类型: string）
-   data.applicant: 申请人（类型: string）
-   data.applyTime: 申请时间（类型: string）
-   data.expectTime: 期望到货时间（类型: string）
-   data.purchaseStatus: 状态（类型: string）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].requestId: 申请单ID（类型: integer）
-     data.detail[].lineNo: 申请单行号（类型: string）
-     data.detail[].supplierId: 供应商id（类型: integer）
-     data.detail[].purchaseItemStatus: 明细状态（类型: string）
-     data.detail[].supplierCode: 供应商编码（类型: string）
-     data.detail[].supplierName: 供应商名称（类型: string）
-     data.detail[].materialId: 物料id（类型: string）
-     data.detail[].materialCode: 物料编码（类型: string）
-     data.detail[].materialName: 物料名称（类型: string）
-     data.detail[].materialTypeCode: 物料类型编码（类型: string）
-     data.detail[].materialTypeName: 物料类型名称（类型: string）
-     data.detail[].materialClassCode: 物料分类编码（类型: string）
-     data.detail[].materialClassName: 物料分类名称（类型: string）
-     data.detail[].unitId: 计量单位编码（类型: string）
-     data.detail[].unitName: 计量单位名称（类型: string）
-     data.detail[].specModel: 规格型号（类型: string）
-     data.detail[].qty: 申请数量（类型: number）
-     data.detail[].purchasedQty: 已采购数量（类型: number）
-     data.detail[].applyCode: 申请单号（类型: string）
-     data.detail[].purchaseType: 采购类型（类型: string）
-     data.detail[].applicant: 申请人（类型: string）
-     data.detail[].applyTime: 申请时间（类型: string）
-     data.detail[].expectTime: 期望到货时间（类型: string）
-     data.detail[].purchaseStatus: 需求状态（类型: string）
-     data.detail[].remark: 申请备注（类型: string）
-     data.detail[].inCart: 是否加入（类型: integer）
-     data.detail[].cartId: （类型: integer）
-     data.detail[].purchaseOrderCodes: （类型: string）
-   data.materialNames: 物料名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/purchase-requests/{id}
- **所属模块：** 采购申请单
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- applyCode: 申请单号（类型: string，可选）
- purchaseType: 采购类型（类型: string，可选）
- applicant: 申请人（类型: string，可选）
- applyTime: 申请时间（类型: string，可选）
- expectTime: 期望到货时间（类型: string，可选）
- purchaseStatus: 状态（类型: string，可选）
- remark: 备注（类型: string，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].requestId: 申请单ID（类型: integer）
-   detail[].lineNo: 申请单行号（类型: string）
-   detail[].supplierId: 供应商id（类型: integer）
-   detail[].purchaseItemStatus: 明细状态（类型: string）
-   detail[].supplierCode: 供应商编码（类型: string）
-   detail[].supplierName: 供应商名称（类型: string）
-   detail[].materialId: 物料id（类型: string）
-   detail[].materialCode: 物料编码（类型: string）
-   detail[].materialName: 物料名称（类型: string）
-   detail[].materialTypeCode: 物料类型编码（类型: string）
-   detail[].materialTypeName: 物料类型名称（类型: string）
-   detail[].materialClassCode: 物料分类编码（类型: string）
-   detail[].materialClassName: 物料分类名称（类型: string）
-   detail[].unitId: 计量单位编码（类型: string）
-   detail[].unitName: 计量单位名称（类型: string）
-   detail[].specModel: 规格型号（类型: string）
-   detail[].qty: 申请数量（类型: number）
-   detail[].purchasedQty: 已采购数量（类型: number）
-   detail[].applyCode: 申请单号（类型: string）
-   detail[].purchaseType: 采购类型（类型: string）
-   detail[].applicant: 申请人（类型: string）
-   detail[].applyTime: 申请时间（类型: string）
-   detail[].expectTime: 期望到货时间（类型: string）
-   detail[].purchaseStatus: 需求状态（类型: string）
-   detail[].remark: 申请备注（类型: string）
-   detail[].inCart: 是否加入（类型: integer）
-   detail[].cartId: （类型: integer）
-   detail[].purchaseOrderCodes: （类型: string）
- materialNames: 物料名称（类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购申请单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.applyCode: 申请单号（类型: string）
-   data.purchaseType: 采购类型（类型: string）
-   data.applicant: 申请人（类型: string）
-   data.applyTime: 申请时间（类型: string）
-   data.expectTime: 期望到货时间（类型: string）
-   data.purchaseStatus: 状态（类型: string）
-   data.remark: 备注（类型: string）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].requestId: 申请单ID（类型: integer）
-     data.detail[].lineNo: 申请单行号（类型: string）
-     data.detail[].supplierId: 供应商id（类型: integer）
-     data.detail[].purchaseItemStatus: 明细状态（类型: string）
-     data.detail[].supplierCode: 供应商编码（类型: string）
-     data.detail[].supplierName: 供应商名称（类型: string）
-     data.detail[].materialId: 物料id（类型: string）
-     data.detail[].materialCode: 物料编码（类型: string）
-     data.detail[].materialName: 物料名称（类型: string）
-     data.detail[].materialTypeCode: 物料类型编码（类型: string）
-     data.detail[].materialTypeName: 物料类型名称（类型: string）
-     data.detail[].materialClassCode: 物料分类编码（类型: string）
-     data.detail[].materialClassName: 物料分类名称（类型: string）
-     data.detail[].unitId: 计量单位编码（类型: string）
-     data.detail[].unitName: 计量单位名称（类型: string）
-     data.detail[].specModel: 规格型号（类型: string）
-     data.detail[].qty: 申请数量（类型: number）
-     data.detail[].purchasedQty: 已采购数量（类型: number）
-     data.detail[].applyCode: 申请单号（类型: string）
-     data.detail[].purchaseType: 采购类型（类型: string）
-     data.detail[].applicant: 申请人（类型: string）
-     data.detail[].applyTime: 申请时间（类型: string）
-     data.detail[].expectTime: 期望到货时间（类型: string）
-     data.detail[].purchaseStatus: 需求状态（类型: string）
-     data.detail[].remark: 申请备注（类型: string）
-     data.detail[].inCart: 是否加入（类型: integer）
-     data.detail[].cartId: （类型: integer）
-     data.detail[].purchaseOrderCodes: （类型: string）
-   data.materialNames: 物料名称（类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/purchase-requests/{id}
- **所属模块：** 采购申请单
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/purchase-request-items
- **所属模块：** 采购申请行(采购需求行)
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/purchase-request-items
- **所属模块：** 采购申请行(采购需求行)
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- requestId: 申请单ID（类型: integer，可选）
- lineNo: 申请单行号（类型: string，可选）
- supplierId: 供应商id（类型: integer，可选）
- purchaseItemStatus: 明细状态（类型: string，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- materialId: 物料id（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- materialTypeCode: 物料类型编码（类型: string，可选）
- materialTypeName: 物料类型名称（类型: string，可选）
- materialClassCode: 物料分类编码（类型: string，可选）
- materialClassName: 物料分类名称（类型: string，可选）
- unitId: 计量单位编码（类型: string，可选）
- unitName: 计量单位名称（类型: string，可选）
- specModel: 规格型号（类型: string，可选）
- qty: 申请数量（类型: number，可选）
- purchasedQty: 已采购数量（类型: number，可选）
- applyCode: 申请单号（类型: string，可选）
- purchaseType: 采购类型（类型: string，可选）
- applicant: 申请人（类型: string，可选）
- applyTime: 申请时间（类型: string，可选）
- expectTime: 期望到货时间（类型: string，可选）
- purchaseStatus: 需求状态（类型: string，可选）
- remark: 申请备注（类型: string，可选）
- inCart: 是否加入（类型: integer，可选）
- cartId: （类型: integer，可选）
- purchaseOrderCodes: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购申请行(采购需求行) DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.requestId: 申请单ID（类型: integer）
-   data.lineNo: 申请单行号（类型: string）
-   data.supplierId: 供应商id（类型: integer）
-   data.purchaseItemStatus: 明细状态（类型: string）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位编码（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.qty: 申请数量（类型: number）
-   data.purchasedQty: 已采购数量（类型: number）
-   data.applyCode: 申请单号（类型: string）
-   data.purchaseType: 采购类型（类型: string）
-   data.applicant: 申请人（类型: string）
-   data.applyTime: 申请时间（类型: string）
-   data.expectTime: 期望到货时间（类型: string）
-   data.purchaseStatus: 需求状态（类型: string）
-   data.remark: 申请备注（类型: string）
-   data.inCart: 是否加入（类型: integer）
-   data.cartId: （类型: integer）
-   data.purchaseOrderCodes: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/purchase-request-items/{id}
- **所属模块：** 采购申请行(采购需求行)
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购申请行(采购需求行) DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.requestId: 申请单ID（类型: integer）
-   data.lineNo: 申请单行号（类型: string）
-   data.supplierId: 供应商id（类型: integer）
-   data.purchaseItemStatus: 明细状态（类型: string）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位编码（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.qty: 申请数量（类型: number）
-   data.purchasedQty: 已采购数量（类型: number）
-   data.applyCode: 申请单号（类型: string）
-   data.purchaseType: 采购类型（类型: string）
-   data.applicant: 申请人（类型: string）
-   data.applyTime: 申请时间（类型: string）
-   data.expectTime: 期望到货时间（类型: string）
-   data.purchaseStatus: 需求状态（类型: string）
-   data.remark: 申请备注（类型: string）
-   data.inCart: 是否加入（类型: integer）
-   data.cartId: （类型: integer）
-   data.purchaseOrderCodes: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/purchase-request-items/{id}
- **所属模块：** 采购申请行(采购需求行)
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- requestId: 申请单ID（类型: integer，可选）
- lineNo: 申请单行号（类型: string，可选）
- supplierId: 供应商id（类型: integer，可选）
- purchaseItemStatus: 明细状态（类型: string，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- materialId: 物料id（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- materialTypeCode: 物料类型编码（类型: string，可选）
- materialTypeName: 物料类型名称（类型: string，可选）
- materialClassCode: 物料分类编码（类型: string，可选）
- materialClassName: 物料分类名称（类型: string，可选）
- unitId: 计量单位编码（类型: string，可选）
- unitName: 计量单位名称（类型: string，可选）
- specModel: 规格型号（类型: string，可选）
- qty: 申请数量（类型: number，可选）
- purchasedQty: 已采购数量（类型: number，可选）
- applyCode: 申请单号（类型: string，可选）
- purchaseType: 采购类型（类型: string，可选）
- applicant: 申请人（类型: string，可选）
- applyTime: 申请时间（类型: string，可选）
- expectTime: 期望到货时间（类型: string，可选）
- purchaseStatus: 需求状态（类型: string，可选）
- remark: 申请备注（类型: string，可选）
- inCart: 是否加入（类型: integer，可选）
- cartId: （类型: integer，可选）
- purchaseOrderCodes: （类型: string，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购申请行(采购需求行) DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.requestId: 申请单ID（类型: integer）
-   data.lineNo: 申请单行号（类型: string）
-   data.supplierId: 供应商id（类型: integer）
-   data.purchaseItemStatus: 明细状态（类型: string）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位编码（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.qty: 申请数量（类型: number）
-   data.purchasedQty: 已采购数量（类型: number）
-   data.applyCode: 申请单号（类型: string）
-   data.purchaseType: 采购类型（类型: string）
-   data.applicant: 申请人（类型: string）
-   data.applyTime: 申请时间（类型: string）
-   data.expectTime: 期望到货时间（类型: string）
-   data.purchaseStatus: 需求状态（类型: string）
-   data.remark: 申请备注（类型: string）
-   data.inCart: 是否加入（类型: integer）
-   data.cartId: （类型: integer）
-   data.purchaseOrderCodes: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/purchase-request-items/{id}
- **所属模块：** 采购申请行(采购需求行)
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/purchase-orders
- **所属模块：** 采购订单
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/purchase-orders
- **所属模块：** 采购订单
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- purchaseOrderCode: 采购订单号（类型: string，可选）
- purchaseType: 采购类型（类型: string，可选）
- applicant: 采购人（类型: string，可选）
- orderTime: 下单时间（类型: string，可选）
- deliveryDate: 交货日期（类型: string，可选）
- orderStatus: 订单状态（类型: string，可选）
- contractCode: 合同编号（类型: string，可选）
- contractFile: 合同附件（类型: string，可选）
- orderAmount: 订单金额（类型: number，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].purchaseOrderId: 采购订单ID（类型: integer）
-   detail[].requestItemId: 来源申请单号（类型: integer）
-   detail[].supplierId: 供应商id（类型: integer）
-   detail[].supplierCode: 供应商编码（类型: string）
-   detail[].supplierName: 供应商名称（类型: string）
-   detail[].materialId: 物料id（类型: string）
-   detail[].materialCode: 物料编码（类型: string）
-   detail[].materialName: 物料名称（类型: string）
-   detail[].materialTypeCode: 物料类型编码（类型: string）
-   detail[].materialTypeName: 物料类型名称（类型: string）
-   detail[].materialClassCode: 物料分类编码（类型: string）
-   detail[].materialClassName: 物料分类名称（类型: string）
-   detail[].unitId: 计量单位id（类型: string）
-   detail[].unitName: 计量单位名称（类型: string）
-   detail[].specModel: 规格型号（类型: string）
-   detail[].orderQty: 采购数量（类型: number）
-   detail[].unitPrice: 单价（类型: number）
-   detail[].totalPrice: 总价（类型: number）
-   detail[].applyCode: 申请单编码（类型: string）
-   detail[].lineNo: 申请行号（类型: string）
-   detail[].qty: 需求数量（类型: number）
-   detail[].purchasedQty: 已采购数量（类型: number）
-   detail[].shippedQty: 已发数量（类型: number）
-   detail[].totalReceiptQty: 已收数量（类型: number）
-   detail[].totalReturnQty: 已退数量（类型: number）
- supplierIds: （类型: string，可选）
- supplierNames: （类型: string，可选）
- materialSummary: （类型: string，可选）
- deliveryCodes: （类型: string，可选）
- receiptCodes: （类型: string，可选）
- shippedComplete: （类型: integer，可选）
- receiptComplete: （类型: integer，可选）
- totalDeliveryQty: （类型: integer，可选）
- totalReceiptQty: （类型: integer，可选）
- totalOrderQty: （类型: integer，可选）
- totalPaymentAmount: （类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购订单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderCode: 采购订单号（类型: string）
-   data.purchaseType: 采购类型（类型: string）
-   data.applicant: 采购人（类型: string）
-   data.orderTime: 下单时间（类型: string）
-   data.deliveryDate: 交货日期（类型: string）
-   data.orderStatus: 订单状态（类型: string）
-   data.contractCode: 合同编号（类型: string）
-   data.contractFile: 合同附件（类型: string）
-   data.orderAmount: 订单金额（类型: number）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].purchaseOrderId: 采购订单ID（类型: integer）
-     data.detail[].requestItemId: 来源申请单号（类型: integer）
-     data.detail[].supplierId: 供应商id（类型: integer）
-     data.detail[].supplierCode: 供应商编码（类型: string）
-     data.detail[].supplierName: 供应商名称（类型: string）
-     data.detail[].materialId: 物料id（类型: string）
-     data.detail[].materialCode: 物料编码（类型: string）
-     data.detail[].materialName: 物料名称（类型: string）
-     data.detail[].materialTypeCode: 物料类型编码（类型: string）
-     data.detail[].materialTypeName: 物料类型名称（类型: string）
-     data.detail[].materialClassCode: 物料分类编码（类型: string）
-     data.detail[].materialClassName: 物料分类名称（类型: string）
-     data.detail[].unitId: 计量单位id（类型: string）
-     data.detail[].unitName: 计量单位名称（类型: string）
-     data.detail[].specModel: 规格型号（类型: string）
-     data.detail[].orderQty: 采购数量（类型: number）
-     data.detail[].unitPrice: 单价（类型: number）
-     data.detail[].totalPrice: 总价（类型: number）
-     data.detail[].applyCode: 申请单编码（类型: string）
-     data.detail[].lineNo: 申请行号（类型: string）
-     data.detail[].qty: 需求数量（类型: number）
-     data.detail[].purchasedQty: 已采购数量（类型: number）
-     data.detail[].shippedQty: 已发数量（类型: number）
-     data.detail[].totalReceiptQty: 已收数量（类型: number）
-     data.detail[].totalReturnQty: 已退数量（类型: number）
-   data.supplierIds: （类型: string）
-   data.supplierNames: （类型: string）
-   data.materialSummary: （类型: string）
-   data.deliveryCodes: （类型: string）
-   data.receiptCodes: （类型: string）
-   data.shippedComplete: （类型: integer）
-   data.receiptComplete: （类型: integer）
-   data.totalDeliveryQty: （类型: integer）
-   data.totalReceiptQty: （类型: integer）
-   data.totalOrderQty: （类型: integer）
-   data.totalPaymentAmount: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：采购订单统计

- **路径：** GET /api/v1/purchase-orders/stats
- **所属模块：** 采购订单
- **HTTP 方法：** GET

**功能说明：**
采购订单统计

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: object）
-   data.totalOrderCount: （类型: integer）
-   data.totalOrderAmount: （类型: number）
-   data.pendingOrderCount: （类型: integer）
-   data.pendingOrderAmount: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查看采购订单统计"
- "怎么采购订单统计？"
- "采购订单统计怎么做？"
- "帮我采购订单统计"

## 接口：

- **路径：** GET /api/v1/purchase-orders/stats/monthly-order
- **所属模块：** 采购订单
- **HTTP 方法：** GET

**功能说明：**


**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].month: （类型: string）
-   data[].orderCount: （类型: integer）
-   data[].orderAmount: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "怎么？"
- "怎么做？"
- "帮我"

## 接口：

- **路径：** GET /api/v1/purchase-orders/stats/monthly-order-amount
- **所属模块：** 采购订单
- **HTTP 方法：** GET

**功能说明：**


**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].month: （类型: string）
-   data[].orderCount: （类型: integer）
-   data[].orderAmount: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "怎么？"
- "怎么做？"
- "帮我"

## 接口：查询

- **路径：** GET /api/v1/purchase-orders/{id}
- **所属模块：** 采购订单
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购订单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderCode: 采购订单号（类型: string）
-   data.purchaseType: 采购类型（类型: string）
-   data.applicant: 采购人（类型: string）
-   data.orderTime: 下单时间（类型: string）
-   data.deliveryDate: 交货日期（类型: string）
-   data.orderStatus: 订单状态（类型: string）
-   data.contractCode: 合同编号（类型: string）
-   data.contractFile: 合同附件（类型: string）
-   data.orderAmount: 订单金额（类型: number）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].purchaseOrderId: 采购订单ID（类型: integer）
-     data.detail[].requestItemId: 来源申请单号（类型: integer）
-     data.detail[].supplierId: 供应商id（类型: integer）
-     data.detail[].supplierCode: 供应商编码（类型: string）
-     data.detail[].supplierName: 供应商名称（类型: string）
-     data.detail[].materialId: 物料id（类型: string）
-     data.detail[].materialCode: 物料编码（类型: string）
-     data.detail[].materialName: 物料名称（类型: string）
-     data.detail[].materialTypeCode: 物料类型编码（类型: string）
-     data.detail[].materialTypeName: 物料类型名称（类型: string）
-     data.detail[].materialClassCode: 物料分类编码（类型: string）
-     data.detail[].materialClassName: 物料分类名称（类型: string）
-     data.detail[].unitId: 计量单位id（类型: string）
-     data.detail[].unitName: 计量单位名称（类型: string）
-     data.detail[].specModel: 规格型号（类型: string）
-     data.detail[].orderQty: 采购数量（类型: number）
-     data.detail[].unitPrice: 单价（类型: number）
-     data.detail[].totalPrice: 总价（类型: number）
-     data.detail[].applyCode: 申请单编码（类型: string）
-     data.detail[].lineNo: 申请行号（类型: string）
-     data.detail[].qty: 需求数量（类型: number）
-     data.detail[].purchasedQty: 已采购数量（类型: number）
-     data.detail[].shippedQty: 已发数量（类型: number）
-     data.detail[].totalReceiptQty: 已收数量（类型: number）
-     data.detail[].totalReturnQty: 已退数量（类型: number）
-   data.supplierIds: （类型: string）
-   data.supplierNames: （类型: string）
-   data.materialSummary: （类型: string）
-   data.deliveryCodes: （类型: string）
-   data.receiptCodes: （类型: string）
-   data.shippedComplete: （类型: integer）
-   data.receiptComplete: （类型: integer）
-   data.totalDeliveryQty: （类型: integer）
-   data.totalReceiptQty: （类型: integer）
-   data.totalOrderQty: （类型: integer）
-   data.totalPaymentAmount: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/purchase-orders/{id}
- **所属模块：** 采购订单
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- purchaseOrderCode: 采购订单号（类型: string，可选）
- purchaseType: 采购类型（类型: string，可选）
- applicant: 采购人（类型: string，可选）
- orderTime: 下单时间（类型: string，可选）
- deliveryDate: 交货日期（类型: string，可选）
- orderStatus: 订单状态（类型: string，可选）
- contractCode: 合同编号（类型: string，可选）
- contractFile: 合同附件（类型: string，可选）
- orderAmount: 订单金额（类型: number，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）
- detail: （类型: array<object>，可选）
-   detail[].id: 主键（类型: integer）
-   detail[].purchaseOrderId: 采购订单ID（类型: integer）
-   detail[].requestItemId: 来源申请单号（类型: integer）
-   detail[].supplierId: 供应商id（类型: integer）
-   detail[].supplierCode: 供应商编码（类型: string）
-   detail[].supplierName: 供应商名称（类型: string）
-   detail[].materialId: 物料id（类型: string）
-   detail[].materialCode: 物料编码（类型: string）
-   detail[].materialName: 物料名称（类型: string）
-   detail[].materialTypeCode: 物料类型编码（类型: string）
-   detail[].materialTypeName: 物料类型名称（类型: string）
-   detail[].materialClassCode: 物料分类编码（类型: string）
-   detail[].materialClassName: 物料分类名称（类型: string）
-   detail[].unitId: 计量单位id（类型: string）
-   detail[].unitName: 计量单位名称（类型: string）
-   detail[].specModel: 规格型号（类型: string）
-   detail[].orderQty: 采购数量（类型: number）
-   detail[].unitPrice: 单价（类型: number）
-   detail[].totalPrice: 总价（类型: number）
-   detail[].applyCode: 申请单编码（类型: string）
-   detail[].lineNo: 申请行号（类型: string）
-   detail[].qty: 需求数量（类型: number）
-   detail[].purchasedQty: 已采购数量（类型: number）
-   detail[].shippedQty: 已发数量（类型: number）
-   detail[].totalReceiptQty: 已收数量（类型: number）
-   detail[].totalReturnQty: 已退数量（类型: number）
- supplierIds: （类型: string，可选）
- supplierNames: （类型: string，可选）
- materialSummary: （类型: string，可选）
- deliveryCodes: （类型: string，可选）
- receiptCodes: （类型: string，可选）
- shippedComplete: （类型: integer，可选）
- receiptComplete: （类型: integer，可选）
- totalDeliveryQty: （类型: integer，可选）
- totalReceiptQty: （类型: integer，可选）
- totalOrderQty: （类型: integer，可选）
- totalPaymentAmount: （类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购订单 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderCode: 采购订单号（类型: string）
-   data.purchaseType: 采购类型（类型: string）
-   data.applicant: 采购人（类型: string）
-   data.orderTime: 下单时间（类型: string）
-   data.deliveryDate: 交货日期（类型: string）
-   data.orderStatus: 订单状态（类型: string）
-   data.contractCode: 合同编号（类型: string）
-   data.contractFile: 合同附件（类型: string）
-   data.orderAmount: 订单金额（类型: number）
-   data.createBy: 创建人（类型: string）
-   data.createTime: 记录创建时间（类型: string）
-   data.updateBy: 更新人（类型: string）
-   data.updateTime: 记录更新时间（类型: string）
-   data.isValid: 是否有效：1=有效，0=无效（类型: integer）
-   data.detail: （类型: array<object>）
-     data.detail[].id: 主键（类型: integer）
-     data.detail[].purchaseOrderId: 采购订单ID（类型: integer）
-     data.detail[].requestItemId: 来源申请单号（类型: integer）
-     data.detail[].supplierId: 供应商id（类型: integer）
-     data.detail[].supplierCode: 供应商编码（类型: string）
-     data.detail[].supplierName: 供应商名称（类型: string）
-     data.detail[].materialId: 物料id（类型: string）
-     data.detail[].materialCode: 物料编码（类型: string）
-     data.detail[].materialName: 物料名称（类型: string）
-     data.detail[].materialTypeCode: 物料类型编码（类型: string）
-     data.detail[].materialTypeName: 物料类型名称（类型: string）
-     data.detail[].materialClassCode: 物料分类编码（类型: string）
-     data.detail[].materialClassName: 物料分类名称（类型: string）
-     data.detail[].unitId: 计量单位id（类型: string）
-     data.detail[].unitName: 计量单位名称（类型: string）
-     data.detail[].specModel: 规格型号（类型: string）
-     data.detail[].orderQty: 采购数量（类型: number）
-     data.detail[].unitPrice: 单价（类型: number）
-     data.detail[].totalPrice: 总价（类型: number）
-     data.detail[].applyCode: 申请单编码（类型: string）
-     data.detail[].lineNo: 申请行号（类型: string）
-     data.detail[].qty: 需求数量（类型: number）
-     data.detail[].purchasedQty: 已采购数量（类型: number）
-     data.detail[].shippedQty: 已发数量（类型: number）
-     data.detail[].totalReceiptQty: 已收数量（类型: number）
-     data.detail[].totalReturnQty: 已退数量（类型: number）
-   data.supplierIds: （类型: string）
-   data.supplierNames: （类型: string）
-   data.materialSummary: （类型: string）
-   data.deliveryCodes: （类型: string）
-   data.receiptCodes: （类型: string）
-   data.shippedComplete: （类型: integer）
-   data.receiptComplete: （类型: integer）
-   data.totalDeliveryQty: （类型: integer）
-   data.totalReceiptQty: （类型: integer）
-   data.totalOrderQty: （类型: integer）
-   data.totalPaymentAmount: （类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/purchase-orders/{id}
- **所属模块：** 采购订单
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：关闭订单

- **路径：** PUT /api/v1/purchase-orders/{id}/close
- **所属模块：** 采购订单
- **HTTP 方法：** PUT

**功能说明：**
关闭订单

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: string）
- extraData: （类型: object）

**适用问题示例：**
- "怎么关闭订单？"
- "关闭订单怎么做？"
- "帮我关闭订单"

## 接口：查询分页

- **路径：** GET /api/v1/purchase-order-items
- **所属模块：** 采购订单行
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/purchase-order-items
- **所属模块：** 采购订单行
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- requestItemId: 来源申请单号（类型: integer，可选）
- supplierId: 供应商id（类型: integer，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- materialId: 物料id（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- materialTypeCode: 物料类型编码（类型: string，可选）
- materialTypeName: 物料类型名称（类型: string，可选）
- materialClassCode: 物料分类编码（类型: string，可选）
- materialClassName: 物料分类名称（类型: string，可选）
- unitId: 计量单位id（类型: string，可选）
- unitName: 计量单位名称（类型: string，可选）
- specModel: 规格型号（类型: string，可选）
- orderQty: 采购数量（类型: number，可选）
- unitPrice: 单价（类型: number，可选）
- totalPrice: 总价（类型: number，可选）
- applyCode: 申请单编码（类型: string，可选）
- lineNo: 申请行号（类型: string，可选）
- qty: 需求数量（类型: number，可选）
- purchasedQty: 已采购数量（类型: number，可选）
- shippedQty: 已发数量（类型: number，可选）
- totalReceiptQty: 已收数量（类型: number，可选）
- totalReturnQty: 已退数量（类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购订单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.requestItemId: 来源申请单号（类型: integer）
-   data.supplierId: 供应商id（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位id（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.orderQty: 采购数量（类型: number）
-   data.unitPrice: 单价（类型: number）
-   data.totalPrice: 总价（类型: number）
-   data.applyCode: 申请单编码（类型: string）
-   data.lineNo: 申请行号（类型: string）
-   data.qty: 需求数量（类型: number）
-   data.purchasedQty: 已采购数量（类型: number）
-   data.shippedQty: 已发数量（类型: number）
-   data.totalReceiptQty: 已收数量（类型: number）
-   data.totalReturnQty: 已退数量（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "新建一个"
- "新增记录"
- "创建一个新的"
- "帮我添加"

## 接口：查询

- **路径：** GET /api/v1/purchase-order-items/{id}
- **所属模块：** 采购订单行
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购订单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.requestItemId: 来源申请单号（类型: integer）
-   data.supplierId: 供应商id（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位id（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.orderQty: 采购数量（类型: number）
-   data.unitPrice: 单价（类型: number）
-   data.totalPrice: 总价（类型: number）
-   data.applyCode: 申请单编码（类型: string）
-   data.lineNo: 申请行号（类型: string）
-   data.qty: 需求数量（类型: number）
-   data.purchasedQty: 已采购数量（类型: number）
-   data.shippedQty: 已发数量（类型: number）
-   data.totalReceiptQty: 已收数量（类型: number）
-   data.totalReturnQty: 已退数量（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "查询"
- "查看列表"
- "有哪些？"
- "列出"

## 接口：更新

- **路径：** PUT /api/v1/purchase-order-items/{id}
- **所属模块：** 采购订单行
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- purchaseOrderId: 采购订单ID（类型: integer，可选）
- requestItemId: 来源申请单号（类型: integer，可选）
- supplierId: 供应商id（类型: integer，可选）
- supplierCode: 供应商编码（类型: string，可选）
- supplierName: 供应商名称（类型: string，可选）
- materialId: 物料id（类型: string，可选）
- materialCode: 物料编码（类型: string，可选）
- materialName: 物料名称（类型: string，可选）
- materialTypeCode: 物料类型编码（类型: string，可选）
- materialTypeName: 物料类型名称（类型: string，可选）
- materialClassCode: 物料分类编码（类型: string，可选）
- materialClassName: 物料分类名称（类型: string，可选）
- unitId: 计量单位id（类型: string，可选）
- unitName: 计量单位名称（类型: string，可选）
- specModel: 规格型号（类型: string，可选）
- orderQty: 采购数量（类型: number，可选）
- unitPrice: 单价（类型: number，可选）
- totalPrice: 总价（类型: number，可选）
- applyCode: 申请单编码（类型: string，可选）
- lineNo: 申请行号（类型: string，可选）
- qty: 需求数量（类型: number，可选）
- purchasedQty: 已采购数量（类型: number，可选）
- shippedQty: 已发数量（类型: number，可选）
- totalReceiptQty: 已收数量（类型: number，可选）
- totalReturnQty: 已退数量（类型: number，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购订单行 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.purchaseOrderId: 采购订单ID（类型: integer）
-   data.requestItemId: 来源申请单号（类型: integer）
-   data.supplierId: 供应商id（类型: integer）
-   data.supplierCode: 供应商编码（类型: string）
-   data.supplierName: 供应商名称（类型: string）
-   data.materialId: 物料id（类型: string）
-   data.materialCode: 物料编码（类型: string）
-   data.materialName: 物料名称（类型: string）
-   data.materialTypeCode: 物料类型编码（类型: string）
-   data.materialTypeName: 物料类型名称（类型: string）
-   data.materialClassCode: 物料分类编码（类型: string）
-   data.materialClassName: 物料分类名称（类型: string）
-   data.unitId: 计量单位id（类型: string）
-   data.unitName: 计量单位名称（类型: string）
-   data.specModel: 规格型号（类型: string）
-   data.orderQty: 采购数量（类型: number）
-   data.unitPrice: 单价（类型: number）
-   data.totalPrice: 总价（类型: number）
-   data.applyCode: 申请单编码（类型: string）
-   data.lineNo: 申请行号（类型: string）
-   data.qty: 需求数量（类型: number）
-   data.purchasedQty: 已采购数量（类型: number）
-   data.shippedQty: 已发数量（类型: number）
-   data.totalReceiptQty: 已收数量（类型: number）
-   data.totalReturnQty: 已退数量（类型: number）
- extraData: （类型: object）

**适用问题示例：**
- "修改信息"
- "更新数据"
- "编辑"

## 接口：删除

- **路径：** DELETE /api/v1/purchase-order-items/{id}
- **所属模块：** 采购订单行
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

**适用问题示例：**
- "删除"
- "移除某个"

## 接口：查询分页

- **路径：** GET /api/v1/purchase-request-carts
- **所属模块：** 采购购物车
- **HTTP 方法：** GET

**功能说明：**
查询分页

**请求参数：**
- dto （查询参数）: （类型: string，必填）

**返回字段：**
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

- **路径：** POST /api/v1/purchase-request-carts
- **所属模块：** 采购购物车
- **HTTP 方法：** POST

**功能说明：**
创建

**请求参数：**
- id: 主键（类型: integer，可选）
- requestId: 采购申请单ID（类型: integer，可选）
- requestItemId: 采购申请行ID（类型: integer，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购购物车 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.requestId: 采购申请单ID（类型: integer）
-   data.requestItemId: 采购申请行ID（类型: integer）
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

## 接口：查询购物车中的供应商

- **路径：** GET /api/v1/purchase-request-carts/suppliers
- **所属模块：** 采购购物车
- **HTTP 方法：** GET

**功能说明：**
查询购物车中的供应商

**请求参数：**
- 无

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: （类型: array<object>）
-   data[].id: 主键（类型: integer）
-   data[].supplierCode: 供应商编码（类型: string）
-   data[].supplierName: 供应商名称（类型: string）
-   data[].supplierStatus: 供应商状态（类型: string）
-   data[].supplierLevel: 供应商级别（类型: string）
-   data[].materialClass: 供货物料类别（类型: string）
-   data[].materialNames: 供货物料（类型: string）
-   data[].employeeCount: 公司人数（类型: integer）
-   data[].annualRevenue: 年营收（类型: number）
-   data[].website: 网址（类型: string）
-   data[].areaRegion: 所在地区（类型: string）
-   data[].address: 详细地址（类型: string）
-   data[].contact: 联系人（类型: string）
-   data[].contactPhone: 联系电话（类型: string）
-   data[].contactEmail: 电子邮箱（类型: string）
-   data[].enterpriseName: 企业名称（类型: string）
-   data[].creditCode: 统一社会信用代码（类型: string）
-   data[].enterpriseNature: 企业类型（类型: string）
-   data[].legalRepresentative: 法定代表人（类型: string）
-   data[].registeredCapital: 注册资本（类型: number）
-   data[].registrationDate: 注册日期（类型: string）
-   data[].businessScope: 经营范围（类型: string）
-   data[].bankAccountName: 开户银行账户名称（类型: string）
-   data[].bankName: 开户银行（类型: string）
-   data[].bankAccount: 银行账号（类型: string）
-   data[].taxType: 税种（类型: string）
-   data[].taxRate: 税率（类型: number）
-   data[].remark: 备注（类型: string）
-   data[].createBy: 创建人（类型: string）
-   data[].createTime: 记录创建时间（类型: string）
-   data[].updateBy: 更新人（类型: string）
-   data[].updateTime: 记录更新时间（类型: string）
-   data[].isValid: 是否有效：1=有效，0=无效（类型: integer）
- extraData: （类型: object）

**适用问题示例：**
- "查询购物车中的供应商"
- "查看购物车中的供应商列表"
- "有哪些购物车中的供应商？"
- "列出购物车中的供应商"

## 接口：查询

- **路径：** GET /api/v1/purchase-request-carts/{id}
- **所属模块：** 采购购物车
- **HTTP 方法：** GET

**功能说明：**
查询

**请求参数：**
- id （路径参数）: （类型: integer，必填）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购购物车 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.requestId: 采购申请单ID（类型: integer）
-   data.requestItemId: 采购申请行ID（类型: integer）
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

- **路径：** PUT /api/v1/purchase-request-carts/{id}
- **所属模块：** 采购购物车
- **HTTP 方法：** PUT

**功能说明：**
更新

**请求参数：**
- id （路径参数）: （类型: integer，必填）
- id: 主键（类型: integer，可选）
- requestId: 采购申请单ID（类型: integer，可选）
- requestItemId: 采购申请行ID（类型: integer，可选）
- createBy: 创建人（类型: string，可选）
- createTime: 记录创建时间（类型: string，可选）
- updateBy: 更新人（类型: string，可选）
- updateTime: 记录更新时间（类型: string，可选）
- isValid: 是否有效：1=有效，0=无效（类型: integer，可选）

**返回字段：**
- status: （类型: integer）
- message: （类型: string）
- data: 采购购物车 DTO（类型: object）
-   data.id: 主键（类型: integer）
-   data.requestId: 采购申请单ID（类型: integer）
-   data.requestItemId: 采购申请行ID（类型: integer）
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

- **路径：** DELETE /api/v1/purchase-request-carts/{id}
- **所属模块：** 采购购物车
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

**适用问题示例：**
- "删除"
- "移除某个"
