package com.jolumn.vtslcommon.context;

public final class UserContext {

    public static final ScopedValue<Long> USER_ID = ScopedValue.newInstance();
    public static final ScopedValue<Integer> ROLE = ScopedValue.newInstance();
    public static final ScopedValue<String> DEVICE_ID = ScopedValue.newInstance();

    private UserContext() {
    }

    public static Long currentUserId() {
        return USER_ID.isBound() ? USER_ID.get() : null;
    }

    public static Integer currentRole() {
        return ROLE.isBound() ? ROLE.get() : null;
    }

    public static String currentDeviceId() {
        return DEVICE_ID.isBound() ? DEVICE_ID.get() : null;
    }

    public static boolean isAnonymous() {
        return !USER_ID.isBound() || USER_ID.get() == null;
    }

    public static boolean isAuthenticated() {
        return !isAnonymous();
    }
}