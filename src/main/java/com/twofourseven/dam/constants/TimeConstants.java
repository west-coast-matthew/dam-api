package com.twofourseven.dam.constants;

/**
 * Common time duration constants expressed in milliseconds.
 * Covers intervals from 500ms to 1 minute.
 */
public final class TimeConstants {

    private TimeConstants() {
        // Utility class — do not instantiate
    }

    // -------------------------------------------------------------------------
    // Sub-second / half-second intervals (0.5s – 5s)
    // -------------------------------------------------------------------------

    /** 500 milliseconds (0.5 seconds) */
    public static final long MS_500 = 500L;

    /** 1,000 milliseconds (1 second) */
    public static final long MS_1_SEC = 1_000L;

    /** 1,500 milliseconds (1.5 seconds) */
    public static final long MS_1_5_SEC = 1_500L;

    /** 2,000 milliseconds (2 seconds) */
    public static final long MS_2_SEC = 2_000L;

    /** 2,500 milliseconds (2.5 seconds) */
    public static final long MS_2_5_SEC = 2_500L;

    /** 3,000 milliseconds (3 seconds) */
    public static final long MS_3_SEC = 3_000L;

    /** 3,500 milliseconds (3.5 seconds) */
    public static final long MS_3_5_SEC = 3_500L;

    /** 4,000 milliseconds (4 seconds) */
    public static final long MS_4_SEC = 4_000L;

    /** 4,500 milliseconds (4.5 seconds) */
    public static final long MS_4_5_SEC = 4_500L;

    /** 5,000 milliseconds (5 seconds) */
    public static final long MS_5_SEC = 5_000L;

    // -------------------------------------------------------------------------
    // Larger intervals
    // -------------------------------------------------------------------------

    /** 10,000 milliseconds (10 seconds) */
    public static final long MS_10_SEC = 10_000L;

    /** 30,000 milliseconds (30 seconds) */
    public static final long MS_30_SEC = 30_000L;

    /** 60,000 milliseconds (1 minute) */
    public static final long MS_1_MIN = 60_000L;
}
