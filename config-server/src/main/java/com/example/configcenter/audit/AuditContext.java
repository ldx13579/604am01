package com.example.configcenter.audit;

import java.util.HashMap;
import java.util.Map;

public class AuditContext {

    private static final ThreadLocal<Map<String, String>> CONTEXT = ThreadLocal.withInitial(HashMap::new);

    private static final String KEY_USERNAME = "username";
    private static final String KEY_IP = "ip";
    private static final String KEY_USER_AGENT = "userAgent";
    private static final String KEY_REQUEST_METHOD = "requestMethod";
    private static final String KEY_REQUEST_URI = "requestUri";
    private static final String KEY_SESSION_ID = "sessionId";

    public static void setUsername(String username) {
        CONTEXT.get().put(KEY_USERNAME, username);
    }

    public static String getUsername() {
        return CONTEXT.get().get(KEY_USERNAME);
    }

    public static void setIp(String ip) {
        CONTEXT.get().put(KEY_IP, ip);
    }

    public static String getIp() {
        return CONTEXT.get().get(KEY_IP);
    }

    public static void setUserAgent(String userAgent) {
        CONTEXT.get().put(KEY_USER_AGENT, userAgent);
    }

    public static String getUserAgent() {
        return CONTEXT.get().get(KEY_USER_AGENT);
    }

    public static void setRequestMethod(String method) {
        CONTEXT.get().put(KEY_REQUEST_METHOD, method);
    }

    public static String getRequestMethod() {
        return CONTEXT.get().get(KEY_REQUEST_METHOD);
    }

    public static void setRequestUri(String uri) {
        CONTEXT.get().put(KEY_REQUEST_URI, uri);
    }

    public static String getRequestUri() {
        return CONTEXT.get().get(KEY_REQUEST_URI);
    }

    public static void setSessionId(String sessionId) {
        CONTEXT.get().put(KEY_SESSION_ID, sessionId);
    }

    public static String getSessionId() {
        return CONTEXT.get().get(KEY_SESSION_ID);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
