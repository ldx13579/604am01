package com.example.configcenter.audit;

import java.util.HashMap;
import java.util.Map;

public class AuditContext {

    private static final ThreadLocal<Map<String, String>> CONTEXT = ThreadLocal.withInitial(HashMap::new);

    private static final String KEY_USERNAME = "username";
    private static final String KEY_IP = "ip";

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

    public static void clear() {
        CONTEXT.remove();
    }
}
