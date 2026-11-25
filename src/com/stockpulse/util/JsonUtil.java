package com.stockpulse.util;

import com.stockpulse.model.Product;
import com.stockpulse.model.User;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Core-Java JSON serializer and lightweight parser (no external libraries or Maven needed).
 */
public class JsonUtil {

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escape(value) + "\"";
    }

    public static String errorJson(String message) {
        return "{\"success\":false,\"error\":" + quote(message) + "}";
    }

    public static String successJson(String message) {
        return "{\"success\":true,\"message\":" + quote(message) + "}";
    }

    public static String userToJson(User user) {
        if (user == null) {
            return "null";
        }
        return "{"
            + "\"id\":" + user.getId() + ","
            + "\"fullName\":" + quote(user.getFullName()) + ","
            + "\"username\":" + quote(user.getUsername()) + ","
            + "\"email\":" + quote(user.getEmail()) + ","
            + "\"role\":" + quote(user.getRole()) + ","
            + "\"createdAt\":" + quote(user.getCreatedAt())
            + "}";
    }

    public static String productToJson(Product p) {
        if (p == null) {
            return "null";
        }
        return "{"
            + "\"id\":" + p.getId() + ","
            + "\"sku\":" + quote(p.getSku()) + ","
            + "\"name\":" + quote(p.getName()) + ","
            + "\"category\":" + quote(p.getCategory()) + ","
            + "\"quantity\":" + p.getQuantity() + ","
            + "\"unitPrice\":" + (p.getUnitPrice() != null ? p.getUnitPrice().toPlainString() : "0.00") + ","
            + "\"totalValue\":" + p.getTotalValue().toPlainString() + ","
            + "\"supplier\":" + quote(p.getSupplier()) + ","
            + "\"description\":" + quote(p.getDescription()) + ","
            + "\"status\":" + quote(p.getStatus()) + ","
            + "\"createdAt\":" + quote(p.getCreatedAt()) + ","
            + "\"updatedAt\":" + quote(p.getUpdatedAt())
            + "}";
    }

    public static String productsToJsonArray(List<Product> products) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < products.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(productToJson(products.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Parses a flat JSON object (e.g. {"sku":"A-1","quantity":10,"unitPrice":12.5}) into a Map<String, String>.
     */
    public static Map<String, String> parseFlatObject(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null) {
            return map;
        }
        String s = json.trim();
        if (s.isEmpty() || !s.startsWith("{") || !s.endsWith("}")) {
            return map;
        }

        int i = 1;
        int n = s.length() - 1;
        while (i < n) {
            i = skipWhitespace(s, i, n);
            if (i >= n) break;
            if (s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (s.charAt(i) != '"') {
                break;
            }

            ParsedToken keyToken = readString(s, i, n);
            i = skipWhitespace(s, keyToken.nextIndex, n);
            if (i >= n || s.charAt(i) != ':') {
                break;
            }
            i = skipWhitespace(s, i + 1, n);
            if (i >= n) break;

            String val;
            if (s.charAt(i) == '"') {
                ParsedToken valToken = readString(s, i, n);
                val = valToken.value;
                i = valToken.nextIndex;
            } else {
                int start = i;
                while (i < n && s.charAt(i) != ',' && s.charAt(i) != '}') {
                    i++;
                }
                String raw = s.substring(start, i).trim();
                val = "null".equals(raw) ? null : raw;
            }
            map.put(keyToken.value, val);
        }
        return map;
    }

    private static int skipWhitespace(String s, int i, int n) {
        while (i < n && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static ParsedToken readString(String s, int startQuoteIdx, int n) {
        StringBuilder sb = new StringBuilder();
        int i = startQuoteIdx + 1;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"':  sb.append('"'); i += 2; break;
                    case '\\': sb.append('\\'); i += 2; break;
                    case '/':  sb.append('/'); i += 2; break;
                    case 'b':  sb.append('\b'); i += 2; break;
                    case 'f':  sb.append('\f'); i += 2; break;
                    case 'n':  sb.append('\n'); i += 2; break;
                    case 'r':  sb.append('\r'); i += 2; break;
                    case 't':  sb.append('\t'); i += 2; break;
                    case 'u':
                        if (i + 5 < n) {
                            String hex = s.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 6;
                            } catch (NumberFormatException ex) {
                                sb.append("\\u");
                                i += 2;
                            }
                        } else {
                            sb.append(next);
                            i += 2;
                        }
                        break;
                    default:
                        sb.append(next);
                        i += 2;
                }
            } else if (c == '"') {
                return new ParsedToken(sb.toString(), i + 1);
            } else {
                sb.append(c);
                i++;
            }
        }
        return new ParsedToken(sb.toString(), i);
    }

    private static class ParsedToken {
        final String value;
        final int nextIndex;

        ParsedToken(String value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }
}
