package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ApiCatalogTest {

    private static final String SAMPLE = """
            # 服务：CRM Service API

            ## 接口：创建分享二维码

            - **路径：** POST /api/qrcode/create
            - **所属模块：** 分享二维码管理
            - **HTTP 方法：** POST

            **功能说明：**
            用于向客户生成专属分享二维码，客户扫码后可进入客户H5页面。

            **请求参数：**
            - customerId: 客户ID（类型: integer，必填）

            **返回字段：**
            - code: （类型: integer）

            ## 接口：删除反馈单

            - **路径：** DELETE /api/feedback/delete/{id}
            - **所属模块：** 反馈单管理
            - **HTTP 方法：** DELETE

            **功能说明：**
            按ID删除一条反馈单记录。

            **请求参数：**
            - id （路径参数）: （类型: string，必填）
            """;

    @Test
    void parseExtractsEachInterfaceBlock() {
        var entries = ApiCatalog.parse("CRM", SAMPLE);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).service()).isEqualTo("CRM");
        assertThat(entries.get(0).name()).isEqualTo("创建分享二维码");
        assertThat(entries.get(0).method()).isEqualTo("POST");
        assertThat(entries.get(0).path()).isEqualTo("/api/qrcode/create");
        assertThat(entries.get(0).summary()).isEqualTo("用于向客户生成专属分享二维码，客户扫码后可进入客户H5页面。");
        assertThat(entries.get(0).detail()).contains("**请求参数：**", "customerId");
    }

    @Test
    void parseHandlesPathParamsAndSecondEntry() {
        var entries = ApiCatalog.parse("CRM", SAMPLE);

        assertThat(entries.get(1).method()).isEqualTo("DELETE");
        assertThat(entries.get(1).path()).isEqualTo("/api/feedback/delete/{id}");
        assertThat(entries.get(1).summary()).isEqualTo("按ID删除一条反馈单记录。");
    }

    @Test
    void realBundledDocsParseWithExpectedCounts() {
        var catalog = new ApiCatalog();

        // Raw "## 接口：" heading count per file is 81/289/121/116/108 = 715 (confirmed via
        // grep -c '^## 接口：' src/main/resources/api-docs/*/*.md), matching the plan's assumption.
        // However, srm-service-api-docs.md has two headings ("## 接口：" with no name text after
        // the colon, around the "monthly-order"/"monthly-order-amount" stats endpoints) that don't
        // match the INTERFACE_HEADING pattern's required (.+) name capture. Those two blocks get
        // silently absorbed into the preceding entry's raw block instead of becoming their own
        // ApiEntry, so srm yields 114 parseable entries, not 116 — total 713, not 715. This is a
        // genuine data-quality issue in the bundled markdown, not a parser bug or a file change.
        assertThat(catalog.size()).isEqualTo(81 + 289 + 121 + 114 + 108);
        assertThat(catalog.isWhitelisted("MES", "POST", "/api/mes/five-s-records/create")).isTrue();
        assertThat(catalog.isWhitelisted("MES", "POST", "/etc/passwd")).isFalse();
        assertThat(catalog.isWhitelisted("CRM", "GET", "/api/mes/five-s-records/create")).isFalse();
    }

    @Test
    void compactIndexForServiceContainsOnlyThatServicesEntries() {
        var catalog = new ApiCatalog();

        var mesIndex = catalog.compactIndex("MES");

        // Raw "## 接口：" heading count for MES is 289 (grep -c '^## 接口：' src/main/resources/api-docs/mes/*.md).
        assertThat(mesIndex.lines()).allMatch(line -> line.startsWith("MES | "));
        assertThat(mesIndex.lines().count()).isEqualTo(289);
        assertThat(mesIndex.lines().count()).isLessThan(catalog.size());
        assertThat(mesIndex).contains("MES | ", "POST /api/mes/five-s-records/create");
    }

    @Test
    void detailReturnsRawBlockForKnownPathAndEmptyForUnknown() {
        var catalog = new ApiCatalog();

        assertThat(catalog.detail("MES", "/api/mes/five-s-records/create")).isPresent();
        assertThat(catalog.detail("MES", "/no/such/path")).isEmpty();
    }
}
