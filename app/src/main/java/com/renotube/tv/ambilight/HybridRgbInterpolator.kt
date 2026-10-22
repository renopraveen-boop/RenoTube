package com.renotube.tv.ambilight

import kotlin.math.abs

/**
 * HyperHDR HybridRgbInterpolator Spring-Damper Engine:
 * - Semi-implicit Euler integration for 2nd order harmonic oscillator
 * - Dynamic fast-attack stiffness and damping
 * - Anti-flicker deadband
 * - Fast zero-snap for low target (< 1f) and low current (< 4.0f) to eliminate dark scene lingering
 */
class HybridRgbInterpolator(
    var totalLeds: Int = 228
) {
    var currentR = FloatArray(totalLeds)
    var currentG = FloatArray(totalLeds)
    var currentB = FloatArray(totalLeds)
    var velocityR = FloatArray(totalLeds)
    var velocityG = FloatArray(totalLeds)
    var velocityB = FloatArray(totalLeds)

    fun ensureCapacity(count: Int) {
        if (currentR.size < count) {
            currentR = FloatArray(count)
            currentG = FloatArray(count)
            currentB = FloatArray(count)
            velocityR = FloatArray(count)
            velocityG = FloatArray(count)
            velocityB = FloatArray(count)
            totalLeds = count
        }
    }

    /**
     * Executes single channel physics for HybridRgbInterpolator.
     * Snaps any channel to 0f when target < 1f && current < 4.0f so the spring-damper
     * filter never lingers at R = 1..3 or G = 1..3 during dark scenes.
     */
    fun updateChannel(
        target: Float,
        cur: FloatArray,
        vel: FloatArray,
        index: Int,
        baseK: Float,
        baseD: Float,
        dt: Float
    ): Int {
        val c = cur[index]
        val v = vel[index]
        val delta = target - c

        // Anti-flickering deadband: if delta is tiny (< 2.0f) and speed is low (< 3.0f), snap
        if (abs(delta) < 2.0f && abs(v) < 3.0f) {
            cur[index] = target
            vel[index] = 0f
            val rounded = Math.round(target).coerceIn(0, 255)
            return if ((target < 1.0f && rounded < 4) || (target == 0f && rounded < 3)) 0 else rounded
        }

        // Attack dynamics:
        val k = when {
            target == 0f -> 450f
            abs(delta) > 35f -> 420f
            else -> baseK
        }
        val d = when {
            target == 0f -> 42f
            abs(delta) > 35f -> 38f
            else -> baseD
        }

        // 2 sub-steps of semi-implicit Euler integration
        val subDt = dt * 0.5f
        var newV = v
        var newC = c
        for (step in 0 until 2) {
            val accel = k * (target - newC) - d * newV
            newV += accel * subDt
            newC += newV * subDt
        }

        cur[index] = newC
        vel[index] = newV

        var out = Math.round(newC).coerceIn(0, 255)

        // Absolute dark scene snap: snap any channel to 0f when target < 1f && current < 4.0f
        // so the spring-damper filter never lingers at R = 1..3 or G = 1..3 during dark scenes!
        if (target < 1.0f && (newC < 4.0f || out < 4)) {
            out = 0
            cur[index] = 0f
            vel[index] = 0f
        }
        return out
    }
}
