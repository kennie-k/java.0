package com.kenyarealestate.payment.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class CallbackSecurity {

    private CallbackSecurity() {}

    public static boolean secretMatches(String provided, String expected) {
        if (provided == null || expected == null) return false;
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean ipAllowed(String callerIp, String allowlistCsv) {
        if (allowlistCsv == null || allowlistCsv.isBlank()) return true;
        if (callerIp == null) return false;
        for (String candidate : allowlistCsv.split(",")) {
            if (candidate.trim().equals(callerIp.trim())) return true;
        }
        return false;
    }
}
