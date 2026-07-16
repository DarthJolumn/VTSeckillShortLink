package com.jolumn.livemallwebsocket.model;

public final class GuestNameGenerator {

    private static final String[] PREFIXES = {
            "游客", "路人", "观众", "访客", "吃瓜", "围观"
    };

    private GuestNameGenerator() {
    }

    public static String generate(String seed) {
        int idx = Math.abs(seed.hashCode()) % PREFIXES.length;
        String suffix = seed.replace("-", "").substring(0, 4).toUpperCase();
        return PREFIXES[idx] + "_" + suffix;
    }
}