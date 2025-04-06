window.onload = function() {
    const ui = SwaggerUIBundle({
        url: "/v3/api-docs",
        dom_id: '#swagger-ui',
        presets: [
            SwaggerUIBundle.presets.apis,
            SwaggerUIStandalonePreset
        ],
        layout: "StandaloneLayout"
    });

    const link = document.createElement("link");
    link.href = "/swagger-ui/custom-swagger.css";
    link.rel = "stylesheet";
    document.getElementsByTagName("head")[0].appendChild(link);
};
