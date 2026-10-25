package com.twofourseven.dam.docs;

/**
 * Builds a single static HTML page that renders an OpenAPI document with Redoc.
 * The spec is embedded in the page, so it can be opened straight from disk (file://).
 * Redoc's script is loaded from a CDN, so viewing it needs internet access.
 */
final class ApiDocsHtml {

    static final String REDOC_SCRIPT = "https://cdn.jsdelivr.net/npm/redoc@2.1.5/bundles/redoc.standalone.js";

    private ApiDocsHtml() {
    }

    static String render(String title, String openApiJson) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>body { margin: 0; padding: 0; }</style>
                </head>
                <body>
                  <div id="redoc"></div>
                  <script id="openapi-spec" type="application/json">%s</script>
                  <script src="%s"></script>
                  <script>
                    var spec = JSON.parse(document.getElementById('openapi-spec').textContent);
                    Redoc.init(spec, { hideDownloadButton: false }, document.getElementById('redoc'));
                  </script>
                </body>
                </html>
                """.formatted(escapeHtml(title), escapeForScript(openApiJson), REDOC_SCRIPT);
    }

    /** Stops the embedded JSON from closing its script tag early (e.g. a description containing "</script>"). */
    static String escapeForScript(String json) {
        return json.replace("</", "<\\/").replace("<!--", "<\\!--");
    }

    static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
