package com.jolumn.vtslkgs.constant;

public final class KgsConstants {

    private KgsConstants() {}

    public static final String REDIS_QUEUE_NAME = "shortly-kgs-redis-queue";
    public static final long QUEUE_THRESHOLD = 200;
    public static final int BATCH_SIZE = 1000;
    public static final int KEY_LENGTH = 6;
    public static final int MAX_GENERATE_ATTEMPTS = 3;
    public static final String STATUS_AVAILABLE = "available";
    public static final String STATUS_USED = "used";
}
