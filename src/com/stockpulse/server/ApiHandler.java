package com.stockpulse.server;

import com.stockpulse.config.DBConnection;
import com.stockpulse.dao.ProductDAO;
import com.stockpulse.dao.UserDAO;
import com.stockpulse.model.Product;
import com.stockpulse.model.User;
import com.stockpulse.util.JsonUtil;
import com.stockpulse.util.PasswordUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all HTTP API routes (/api/*) for authentication, dashboard stats, and Product CRUD.
 */
public class ApiHandler implements HttpHandler {

    private final UserDAO userDAO = new UserDAO();
    private final ProductDAO productDAO = new ProductDAO();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        try {
            if ("/api/health".equals(path) && "GET".equals(method)) {
                handleHealth(exchange);
            } else if ("/api/register".equals(path) && "POST".equals(method)) {
                handleRegister(exchange);
            } else if ("/api/login".equals(path) && "POST".equals(method)) {
                handleLogin(exchange);
            } else if ("/api/logout".equals(path) && "POST".equals(method)) {
                handleLogout(exchange);
            } else if ("/api/me".equals(path) && "GET".equals(method)) {
                handleMe(exchange);
            } else if ("/api/dashboard".equals(path) && "GET".equals(method)) {
                handleDashboard(exchange);
            } else if ("/api/products".equals(path)) {
                if ("GET".equals(method)) {
                    handleListProducts(exchange);
                } else if ("POST".equals(method)) {
                    handleCreateProduct(exchange);
                } else {
                    sendJson(exchange, 405, JsonUtil.errorJson("Method not allowed"));
                }
            } else if (path.startsWith("/api/products/")) {
                handleSingleProduct(exchange, path, method);
            } else {
                sendJson(exchange, 404, JsonUtil.errorJson("API endpoint not found"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500, JsonUtil.errorJson("Database error: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, JsonUtil.errorJson("Server error: " + e.getMessage()));
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        boolean dbUp = DBConnection.testConnection();
        String json = "{\"success\":true,\"databaseConnected\":" + dbUp + "}";
        sendJson(exchange, dbUp ? 200 : 503, json);
    }

    private void handleRegister(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> body = JsonUtil.parseFlatObject(readRequestBody(exchange));
        String fullName = trimOrEmpty(body.get("fullName"));
        String username = trimOrEmpty(body.get("username"));
        String email = trimOrEmpty(body.get("email"));
        String password = body.get("password");

        if (fullName.isEmpty() || username.isEmpty() || email.isEmpty() || password == null || password.length() < 4) {
            sendJson(exchange, 400, JsonUtil.errorJson("Full name, username, valid email, and password (min 4 chars) are required."));
            return;
        }
        if (!email.contains("@") || !email.contains(".")) {
            sendJson(exchange, 400, JsonUtil.errorJson("Please enter a valid email address."));
            return;
        }
        if (userDAO.existsByUsernameOrEmail(username, email)) {
            sendJson(exchange, 409, JsonUtil.errorJson("Username or email is already registered."));
            return;
        }

        User user = new User();
        user.setFullName(fullName);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(PasswordUtil.sha256(password));
        user.setRole("STAFF");

        User saved = userDAO.registerUser(user);
        String token = SessionManager.createSession(saved);

        String json = "{"
            + "\"success\":true,"
            + "\"message\":\"Account registered successfully.\","
            + "\"token\":" + JsonUtil.quote(token) + ","
            + "\"user\":" + JsonUtil.userToJson(saved)
            + "}";
        sendJson(exchange, 201, json);
    }

    private void handleLogin(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> body = JsonUtil.parseFlatObject(readRequestBody(exchange));
        String identifier = trimOrEmpty(body.get("username"));
        if (identifier.isEmpty()) {
            identifier = trimOrEmpty(body.get("identifier"));
        }
        String password = body.get("password");

        if (identifier.isEmpty() || password == null || password.isEmpty()) {
            sendJson(exchange, 400, JsonUtil.errorJson("Username/Email and password are required."));
            return;
        }

        String hash = PasswordUtil.sha256(password);
        User user = userDAO.authenticate(identifier, hash);
        if (user == null) {
            sendJson(exchange, 401, JsonUtil.errorJson("Invalid username/email or password."));
            return;
        }

        String token = SessionManager.createSession(user);
        String json = "{"
            + "\"success\":true,"
            + "\"message\":\"Login successful.\","
            + "\"token\":" + JsonUtil.quote(token) + ","
            + "\"user\":" + JsonUtil.userToJson(user)
            + "}";
        sendJson(exchange, 200, json);
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String token = extractBearerToken(exchange);
        SessionManager.invalidate(token);
        sendJson(exchange, 200, JsonUtil.successJson("Logged out successfully."));
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        User user = requireAuth(exchange);
        if (user == null) return;
        sendJson(exchange, 200, "{\"success\":true,\"user\":" + JsonUtil.userToJson(user) + "}");
    }

    @SuppressWarnings("unchecked")
    private void handleDashboard(HttpExchange exchange) throws IOException, SQLException {
        User user = requireAuth(exchange);
        if (user == null) return;

        Map<String, Object> stats = productDAO.getDashboardStats();
        List<Map<String, Object>> categories = (List<Map<String, Object>>) stats.get("categories");

        StringBuilder catJson = new StringBuilder("[");
        if (categories != null) {
            for (int i = 0; i < categories.size(); i++) {
                if (i > 0) catJson.append(",");
                Map<String, Object> c = categories.get(i);
                catJson.append("{")
                    .append("\"category\":").append(JsonUtil.quote(String.valueOf(c.get("category")))).append(",")
                    .append("\"itemCount\":").append(c.get("itemCount")).append(",")
                    .append("\"unitCount\":").append(c.get("unitCount")).append(",")
                    .append("\"categoryValue\":").append(c.get("categoryValue"))
                    .append("}");
            }
        }
        catJson.append("]");

        String json = "{"
            + "\"success\":true,"
            + "\"totalProducts\":" + stats.get("totalProducts") + ","
            + "\"totalUnits\":" + stats.get("totalUnits") + ","
            + "\"totalValue\":" + stats.get("totalValue") + ","
            + "\"lowStockCount\":" + stats.get("lowStockCount") + ","
            + "\"outOfStockCount\":" + stats.get("outOfStockCount") + ","
            + "\"categories\":" + catJson
            + "}";
        sendJson(exchange, 200, json);
    }

    private void handleListProducts(HttpExchange exchange) throws IOException, SQLException {
        User user = requireAuth(exchange);
        if (user == null) return;

        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
        String q = query.get("q");
        String category = query.get("category");

        List<Product> products = productDAO.getAllProducts(q, category);
        String json = "{\"success\":true,\"products\":" + JsonUtil.productsToJsonArray(products) + "}";
        sendJson(exchange, 200, json);
    }

    private void handleCreateProduct(HttpExchange exchange) throws IOException, SQLException {
        User user = requireAuth(exchange);
        if (user == null) return;

        Map<String, String> body = JsonUtil.parseFlatObject(readRequestBody(exchange));
        Product p = parseAndValidateProduct(body, exchange);
        if (p == null) return;

        p.setCreatedBy(user.getId());
        try {
            Product saved = productDAO.insertProduct(p);
            String json = "{\"success\":true,\"message\":\"Product added to MySQL database.\",\"product\":" + JsonUtil.productToJson(saved) + "}";
            sendJson(exchange, 201, json);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate")) {
                sendJson(exchange, 409, JsonUtil.errorJson("A product with SKU '" + p.getSku() + "' already exists."));
            } else {
                throw e;
            }
        }
    }

    private void handleSingleProduct(HttpExchange exchange, String path, String method) throws IOException, SQLException {
        User user = requireAuth(exchange);
        if (user == null) return;

        String idPart = path.substring("/api/products/".length());
        int id;
        try {
            id = Integer.parseInt(idPart);
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, JsonUtil.errorJson("Invalid product ID."));
            return;
        }

        if ("GET".equals(method)) {
            Product p = productDAO.getProductById(id);
            if (p == null) {
                sendJson(exchange, 404, JsonUtil.errorJson("Product not found."));
            } else {
                sendJson(exchange, 200, "{\"success\":true,\"product\":" + JsonUtil.productToJson(p) + "}");
            }
        } else if ("PUT".equals(method)) {
            Map<String, String> body = JsonUtil.parseFlatObject(readRequestBody(exchange));
            Product p = parseAndValidateProduct(body, exchange);
            if (p == null) return;
            p.setId(id);

            try {
                Product updated = productDAO.updateProduct(p);
                if (updated == null) {
                    sendJson(exchange, 404, JsonUtil.errorJson("Product not found."));
                } else {
                    sendJson(exchange, 200, "{\"success\":true,\"message\":\"Product updated in MySQL database.\",\"product\":" + JsonUtil.productToJson(updated) + "}");
                }
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate")) {
                    sendJson(exchange, 409, JsonUtil.errorJson("Another product already uses SKU '" + p.getSku() + "'."));
                } else {
                    throw e;
                }
            }
        } else if ("DELETE".equals(method)) {
            boolean deleted = productDAO.deleteProduct(id);
            if (!deleted) {
                sendJson(exchange, 404, JsonUtil.errorJson("Product not found."));
            } else {
                sendJson(exchange, 200, JsonUtil.successJson("Product deleted from MySQL database."));
            }
        } else {
            sendJson(exchange, 405, JsonUtil.errorJson("Method not allowed."));
        }
    }

    private Product parseAndValidateProduct(Map<String, String> body, HttpExchange exchange) throws IOException {
        String sku = trimOrEmpty(body.get("sku"));
        String name = trimOrEmpty(body.get("name"));
        String category = trimOrEmpty(body.get("category"));
        String supplier = trimOrEmpty(body.get("supplier"));
        String description = trimOrEmpty(body.get("description"));
        String qtyStr = trimOrEmpty(body.get("quantity"));
        String priceStr = trimOrEmpty(body.get("unitPrice"));

        if (sku.isEmpty() || name.isEmpty() || category.isEmpty() || supplier.isEmpty()) {
            sendJson(exchange, 400, JsonUtil.errorJson("SKU, product name, category, and supplier are required."));
            return null;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(qtyStr);
            if (quantity < 0) {
                sendJson(exchange, 400, JsonUtil.errorJson("Quantity cannot be negative."));
                return null;
            }
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, JsonUtil.errorJson("Quantity must be a valid whole number."));
            return null;
        }

        BigDecimal unitPrice;
        try {
            unitPrice = new BigDecimal(priceStr);
            if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                sendJson(exchange, 400, JsonUtil.errorJson("Unit price cannot be negative."));
                return null;
            }
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, JsonUtil.errorJson("Unit price must be a valid decimal number."));
            return null;
        }

        Product p = new Product();
        p.setSku(sku);
        p.setName(name);
        p.setCategory(category);
        p.setQuantity(quantity);
        p.setUnitPrice(unitPrice);
        p.setSupplier(supplier);
        p.setDescription(description);
        return p;
    }

    private User requireAuth(HttpExchange exchange) throws IOException {
        String token = extractBearerToken(exchange);
        User user = SessionManager.getUser(token);
        if (user == null) {
            sendJson(exchange, 401, JsonUtil.errorJson("Authentication required. Please log in."));
            return null;
        }
        return user;
    }

    private String extractBearerToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[4096];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return map;
        }
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
