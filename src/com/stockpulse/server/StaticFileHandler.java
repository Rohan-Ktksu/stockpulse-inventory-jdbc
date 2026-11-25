package com.stockpulse.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Serves static HTML, CSS, and JavaScript frontend files from the web/ directory.
 */
public class StaticFileHandler implements HttpHandler {

    private final File webRoot;

    public StaticFileHandler(String webRootPath) {
        this.webRoot = new File(webRootPath).getAbsoluteFile();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        String uriPath = exchange.getRequestURI().getPath();
        if (uriPath == null || "/".equals(uriPath)) {
            uriPath = "/index.html";
        }

        File target = new File(webRoot, uriPath.substring(1)).getCanonicalFile();
        if (!target.getPath().startsWith(webRoot.getCanonicalPath()) || !target.exists() || target.isDirectory()) {
            sendText(exchange, 404, "404 Not Found");
            return;
        }

        byte[] content = Files.readAllBytes(target.toPath());
        exchange.getResponseHeaders().set("Content-Type", getMimeType(target.getName()));
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(content);
        }
    }

    private String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css"))  return "text/css; charset=UTF-8";
        if (lower.endsWith(".js"))   return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".svg"))  return "image/svg+xml";
        if (lower.endsWith(".png"))  return "image/png";
        return "application/octet-stream";
    }

    private void sendText(HttpExchange exchange, int code, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
