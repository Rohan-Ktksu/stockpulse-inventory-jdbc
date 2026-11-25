package com.stockpulse.server;

import com.stockpulse.model.User;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory session store mapping Bearer tokens to authenticated User objects.
 */
public class SessionManager {
    private static final Map<String, User> SESSIONS = new ConcurrentHashMap<>();

    public static String createSession(User user) {
        String token = UUID.randomUUID().toString();
        SESSIONS.put(token, user);
        return token;
    }

    public static User getUser(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        return SESSIONS.get(token.trim());
    }

    public static void invalidate(String token) {
        if (token != null) {
            SESSIONS.remove(token.trim());
        }
    }
}
