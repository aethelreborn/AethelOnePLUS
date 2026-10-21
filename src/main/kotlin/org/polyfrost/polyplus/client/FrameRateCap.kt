package org.polyfrost.polyplus.client

/**
 * The frame rates the game is allowed to reach.
 *
 * Unlimited is the fastest on a strong machine, but on a laptop it keeps the
 * processor and the graphics card busy enough to start throttling, which drags
 * the frame rate down again a few minutes into the session.
 */
enum class FrameRateCap {
    UNLIMITED,
    REFRESH_RATE,
    FPS_120,
    FPS_60,
    FPS_30,
}
