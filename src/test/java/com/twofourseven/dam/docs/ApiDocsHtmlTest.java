package com.twofourseven.dam.docs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDocsHtmlTest {

    @Test
    void embedsSpecAndLoadsRedoc() {
        String html = ApiDocsHtml.render("DAM API", "{\"openapi\":\"3.1.0\"}");

        assertThat(html).startsWith("<!doctype html>");
        assertThat(html).contains("<title>DAM API</title>");
        assertThat(html).contains("{\"openapi\":\"3.1.0\"}");
        assertThat(html).contains(ApiDocsHtml.REDOC_SCRIPT);
    }

    @Test
    void specCannotCloseItsScriptTag() {
        String html = ApiDocsHtml.render("t", "{\"description\":\"</script><script>alert(1)</script>\"}");

        assertThat(html).doesNotContain("</script><script>alert(1)");
        assertThat(html).contains("<\\/script>");
    }

    @Test
    void titleIsEscaped() {
        assertThat(ApiDocsHtml.render("<b>&", "{}")).contains("<title>&lt;b&gt;&amp;</title>");
    }
}
