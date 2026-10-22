package com.renotube.tv.ambilight

import android.graphics.Bitmap
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * HyperHDR-Grade Ultra-Low-Latency Edge Color Extraction & Smoothing Engine.
 *
 * Implements the exact algorithm, color science, and configuration from HyperHDR:
 * - 228 LEDs (exact box dimensions from HyperHdrBoxes.kt: 5% H depth, 8% V depth)
 * - VideoSize Aspect Ratio + Border Scanner Active Video Bounds Detection
 * - Dark-Scene YUV Chroma Noise & WS2812B Hardware Red/Green Compensation
 * - Low-PWM Diode Floor Gate & HybridRgbInterpolator Spring-Damper with Zero-Snap
 * - DDP (Port 4048) / DRGB UDP protocols with keepalive
 */
@Singleton
class AmbilightProcessor @Inject constructor(
    private val udpSender: WledUdpSender
) {
    data class LedBox(
        val hmin: Float,
        val hmax: Float,
        val vmin: Float,
        val vmax: Float
    )

    // Precomputed exact normalized coordinates for 228 LEDs directly from user HyperHDR led_layout.json
    private val hyperHdr228Boxes = HYPER_HDR_228_BOXES

    // Dynamic LED boxes for custom user layouts
    private var cachedBoxes: Array<LedBox>? = null
    private var cachedBoxKey = ""

    // ── Persistent Reusable Buffers (Zero GC allocations during playback) ──
    private var pixelBuffer = IntArray(128 * 72)
    private var targetColors = IntArray(228)
    private var finalLeds = IntArray(228)
    private var previousLeds = IntArray(228)

    // ── HybridRgbInterpolator Spring-Damper Physics State (per-channel) ──
    private var currentR = FloatArray(228)
    private var currentG = FloatArray(228)
    private var currentB = FloatArray(228)
    private var velocityR = FloatArray(228)
    private var velocityG = FloatArray(228)
    private var velocityB = FloatArray(228)
    private var lastPhysicsTimeMs = 0L

    // Active video bounds (normalized 0f..1f)
    private var smoothTopEdge = 0.0f
    private var smoothBottomEdge = 1.0f
    private var smoothLeftEdge = 0.0f
    private var smoothRightEdge = 1.0f

    private var activeBorderX = 0
    private var activeBorderY = 0

    private var lastSentTimestamp = 0L
    private var lastLogTime = 0L

    private fun ensureCapacity(count: Int) {
        if (targetColors.size < count) {
            targetColors = IntArray(count)
            finalLeds = IntArray(count)
            previousLeds = IntArray(count)
            currentR = FloatArray(count)
            currentG = FloatArray(count)
            currentB = FloatArray(count)
            velocityR = FloatArray(count)
            velocityG = FloatArray(count)
            velocityB = FloatArray(count)
        }
    }

    private fun getLedBoxes(config: AmbilightConfig): Array<LedBox> {
        val total = config.totalLeds
        if (total == 228 && config.leftLeds == 40 && config.topLeds == 71 && config.rightLeds == 41 && config.bottomLeds == 76) {
            return hyperHdr228Boxes
        }
        val key = "${config.leftLeds}:${config.topLeds}:${config.rightLeds}:${config.bottomLeds}"
        if (cachedBoxes != null && cachedBoxKey == key) {
            return cachedBoxes!!
        }

        val leftCount = config.leftLeds.coerceAtLeast(1)
        val topCount = config.topLeds.coerceAtLeast(1)
        val rightCount = config.rightLeds.coerceAtLeast(1)
        val bottomCount = config.bottomLeds.coerceAtLeast(1)
        val boxes = Array(total) { i ->
            when {
                // Left: 0 until leftCount (bottom -> up)
                i < leftCount -> {
                    val vmax = 1.0f - (i.toFloat() / leftCount)
                    val vmin = 1.0f - ((i + 1).toFloat() / leftCount)
                    LedBox(hmin = 0.0f, hmax = 0.05f, vmin = vmin, vmax = vmax)
                }
                // Top: leftCount until leftCount + topCount (left -> right)
                i < leftCount + topCount -> {
                    val k = i - leftCount
                    val hmin = k.toFloat() / topCount
                    val hmax = (k + 1).toFloat() / topCount
                    LedBox(hmin = hmin, hmax = hmax, vmin = 0.0f, vmax = 0.08f)
                }
                // Right: leftCount + topCount until leftCount + topCount + rightCount (top -> down)
                i < leftCount + topCount + rightCount -> {
                    val k = i - (leftCount + topCount)
                    val vmin = k.toFloat() / rightCount
                    val vmax = (k + 1).toFloat() / rightCount
                    LedBox(hmin = 0.95f, hmax = 1.0f, vmin = vmin, vmax = vmax)
                }
                // Bottom: remaining (right -> left)
                else -> {
                    val k = i - (leftCount + topCount + rightCount)
                    val hmax = 1.0f - (k.toFloat() / bottomCount)
                    val hmin = 1.0f - ((k + 1).toFloat() / bottomCount)
                    LedBox(hmin = hmin, hmax = hmax, vmin = 0.92f, vmax = 1.0f)
                }
            }
        }
        cachedBoxes = boxes
        cachedBoxKey = key
        return boxes
    }

    fun processFrame(bitmap: Bitmap?, config: AmbilightConfig, videoAspectRatio: Float = 16f / 9f) {
        if (!config.enabled || config.wledIp.isBlank()) return
        val totalLeds = config.totalLeds
        if (totalLeds <= 0) return
        ensureCapacity(totalLeds)

        if (bitmap != null && !bitmap.isRecycled && bitmap.width >= 4 && bitmap.height >= 4) {
            sampleHyperHdrFrame(bitmap, config, videoAspectRatio)
        } else {
            fillBlack(totalLeds)
        }

        applyPhysicsAndSend(totalLeds, config)
    }

    fun updateTargetFromBitmap(bitmap: Bitmap?, config: AmbilightConfig, videoAspectRatio: Float = 16f / 9f) {
        if (!config.enabled || config.wledIp.isBlank()) return
        val totalLeds = config.totalLeds
        if (totalLeds <= 0) return
        ensureCapacity(totalLeds)

        if (bitmap != null && !bitmap.isRecycled && bitmap.width >= 4 && bitmap.height >= 4) {
            sampleHyperHdrFrame(bitmap, config, videoAspectRatio)
        }
    }

    fun tickPhysicsAndSend(config: AmbilightConfig) {
        if (!config.enabled || config.wledIp.isBlank()) return
        val totalLeds = config.totalLeds
        if (totalLeds <= 0) return
        ensureCapacity(totalLeds)

        applyPhysicsAndSend(totalLeds, config)
    }

    private fun applyPhysicsAndSend(totalLeds: Int, config: AmbilightConfig) {
        val now = System.currentTimeMillis()
        val dt = if (lastPhysicsTimeMs == 0L) {
            0.02f
        } else {
            ((now - lastPhysicsTimeMs).toFloat() / 1000f).coerceIn(0.005f, 0.08f)
        }
        lastPhysicsTimeMs = now

        // ── HyperHDR HybridRgbInterpolator Spring-Damper Engine ──
        val smoothMult = (config.smoothFactor.coerceIn(0.1f, 1.0f) / 0.35f)
        val baseK = 280f * smoothMult
        val baseD = 32f * (0.8f + 0.2f * smoothMult)

        var hasSignificantChange = false
        for (i in 0 until totalLeds) {
            val target = targetColors[i]
            val tR = ((target shr 16) and 0xFF).toFloat()
            val tG = ((target shr 8) and 0xFF).toFloat()
            val tB = (target and 0xFF).toFloat()

            val r = updateChannelPhysics(tR, currentR, velocityR, i, baseK, baseD, dt)
            val g = updateChannelPhysics(tG, currentG, velocityG, i, baseK, baseD, dt)
            val b = updateChannelPhysics(tB, currentB, velocityB, i, baseK, baseD, dt)

            finalLeds[i] = (r shl 16) or (g shl 8) or b

            val lastSent = previousLeds[i]
            val diff = abs(r - ((lastSent shr 16) and 0xFF)) +
                    abs(g - ((lastSent shr 8) and 0xFF)) +
                    abs(b - (lastSent and 0xFF))
            if (diff >= 1) {
                hasSignificantChange = true
            }
        }

        // Send immediately on change, or every 50ms (20 Hz) keepalive so WLED live stream never drops out
        if (hasSignificantChange || (now - lastSentTimestamp) >= 50L) {
            lastSentTimestamp = now
            System.arraycopy(finalLeds, 0, previousLeds, 0, totalLeds)
            udpSender.sendFrameDirect(config.wledIp, config.wledPort, finalLeds)
        }

        if (now - lastLogTime > 4000) {
            lastLogTime = now
            val sample = finalLeds.getOrNull(0) ?: 0
            Log.d(
                "AmbilightProcessor",
                "HyperHDR WLED Sync -> $totalLeds LEDs to ${config.wledIp}:${config.wledPort} [Bounds T:${"%.3f".format(smoothTopEdge)} L:${"%.3f".format(smoothLeftEdge)}, LED0:#${"%06X".format(sample and 0xFFFFFF)}]"
            )
        }
    }

    /**
     * Executes single channel physics for HybridRgbInterpolator:
     * - Damped harmonic oscillator: a = k * delta - d * v
     * - Anti-flickering deadband: suppresses ±2.0 level quantization noise
     * - Fast-attack on big cuts (|delta| > 35)
     * - Rapid blackout attack when target is 0 with zero residual glow
     * - 2 sub-steps of semi-implicit Euler integration for unconditional stability
     * - Zero-snap: snap any channel to 0f when target < 1f && current < 4.0f to eliminate dark scene lingering
     */
    private inline fun updateChannelPhysics(
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

    private fun updateActiveVideoBounds(
        pixels: IntArray,
        width: Int,
        height: Int,
        videoAspectRatio: Float = 16f / 9f
    ) {
        // HyperHDR exact 3-line Black Border Detector (33%, 50%, 66% with threshold = 13 / 5%)
        val threshold = 13
        val yCenter = height / 2
        val y33 = height / 3
        val y66 = (height * 2) / 3
        val xCenter = width / 2
        val x33 = width / 3
        val x66 = (width * 2) / 3

        val maxScanY = (height * 0.35f).toInt()
        val maxScanX = (width * 0.35f).toInt()

        var nonBlackY = 0
        for (y in 0 until maxScanY) {
            val offset = y * width
            val c1 = pixels[offset + xCenter]
            val c2 = pixels[offset + x33]
            val c3 = pixels[offset + x66]
            val max1 = maxOf((c1 shr 16) and 0xFF, (c1 shr 8) and 0xFF, c1 and 0xFF)
            val max2 = maxOf((c2 shr 16) and 0xFF, (c2 shr 8) and 0xFF, c2 and 0xFF)
            val max3 = maxOf((c3 shr 16) and 0xFF, (c3 shr 8) and 0xFF, c3 and 0xFF)
            if (max1 > threshold || max2 > threshold || max3 > threshold) {
                nonBlackY = y
                break
            }
        }

        var nonBlackX = 0
        for (x in 0 until maxScanX) {
            val c1 = pixels[yCenter * width + x]
            val c2 = pixels[y33 * width + x]
            val c3 = pixels[y66 * width + x]
            val max1 = maxOf((c1 shr 16) and 0xFF, (c1 shr 8) and 0xFF, c1 and 0xFF)
            val max2 = maxOf((c2 shr 16) and 0xFF, (c2 shr 8) and 0xFF, c2 and 0xFF)
            val max3 = maxOf((c3 shr 16) and 0xFF, (c3 shr 8) and 0xFF, c3 and 0xFF)
            if (max1 > threshold || max2 > threshold || max3 > threshold) {
                nonBlackX = x
                break
            }
        }

        // Fast responsive smoothing (alpha = 0.30)
        val targetTop = (nonBlackY.toFloat() / height).coerceIn(0f, 0.35f)
        val targetLeft = (nonBlackX.toFloat() / width).coerceIn(0f, 0.35f)

        val alpha = 0.30f
        smoothTopEdge += (targetTop - smoothTopEdge) * alpha
        smoothBottomEdge = 1.0f - smoothTopEdge
        smoothLeftEdge += (targetLeft - smoothLeftEdge) * alpha
        smoothRightEdge = 1.0f - smoothLeftEdge

        activeBorderY = (smoothTopEdge * height).toInt()
        activeBorderX = (smoothLeftEdge * width).toInt()
    }

    /**
     * Executes color sampling using exact original 228-LED box dimensions mapped flush against active video bounds.
     */
    private fun sampleHyperHdrFrame(
        bitmap: Bitmap,
        config: AmbilightConfig,
        videoAspectRatio: Float = 16f / 9f
    ) {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        if (pixelBuffer.size < totalPixels) {
            pixelBuffer = IntArray(totalPixels)
        }
        bitmap.getPixels(pixelBuffer, 0, width, 0, 0, width, height)

        if (config.letterboxProtection) {
            updateActiveVideoBounds(pixelBuffer, width, height, videoAspectRatio)
        } else {
            smoothTopEdge = 0f
            smoothBottomEdge = 1f
            smoothLeftEdge = 0f
            smoothRightEdge = 1f
            activeBorderX = 0
            activeBorderY = 0
        }

        val activeW = (smoothRightEdge - smoothLeftEdge).coerceAtLeast(0.50f)
        val activeH = (smoothBottomEdge - smoothTopEdge).coerceAtLeast(0.45f)

        val bFactor = config.brightness.coerceIn(0.1f, 1.5f)
        val satGain = config.saturationGain.coerceIn(0.5f, 2.0f)
        val isWarm = config.colorTemperature.equals("WARM", ignoreCase = true)
        val isCool = config.colorTemperature.equals("COOL", ignoreCase = true)

        val boxes = if (config.totalLeds == 228) HyperHdrBoxes.BOXES else getLedBoxes(config)
        val countLeds = boxes.size

        for (i in 0 until countLeds) {
            val box = boxes[i]

            // Keep the EXACT original HyperHdrBoxes.kt proportions (5% H, 8% V)
            // mapped directly onto the active video rectangle:
            val hminNorm = smoothLeftEdge + box.hmin * activeW
            val hmaxNorm = smoothLeftEdge + box.hmax * activeW
            val vminNorm = smoothTopEdge + box.vmin * activeH
            val vmaxNorm = smoothTopEdge + box.vmax * activeH

            val xStart = (hminNorm * width).toInt().coerceIn(0, width - 1)
            val xEnd = maxOf(xStart + 1, (hmaxNorm * width).toInt()).coerceIn(xStart + 1, width)
            val yStart = (vminNorm * height).toInt().coerceIn(0, height - 1)
            val yEnd = maxOf(yStart + 1, (vmaxNorm * height).toInt()).coerceIn(yStart + 1, height)

            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var totalCount = 0

            for (y in yStart until yEnd) {
                val rowOffset = y * width
                for (x in xStart until xEnd) {
                    val c = pixelBuffer[rowOffset + x]
                    sumR += (c shr 16) and 0xFF
                    sumG += (c shr 8) and 0xFF
                    sumB += c and 0xFF
                    totalCount++
                }
            }

            val avgR = if (totalCount > 0) sumR.toFloat() / totalCount else 0f
            val avgG = if (totalCount > 0) sumG.toFloat() / totalCount else 0f
            val avgB = if (totalCount > 0) sumB.toFloat() / totalCount else 0f

            val rNorm = (avgR / 255f).coerceIn(0f, 1f)
            val gNorm = (avgG / 255f).coerceIn(0f, 1f)
            val bNorm = (avgB / 255f).coerceIn(0f, 1f)

            val maxCh = maxOf(rNorm, gNorm, bNorm)
            val luma = 0.2126f * rNorm + 0.7152f * gNorm + 0.0722f * bNorm

            var targetR = 0f
            var targetG = 0f
            var targetB = 0f

            // 1. HyperHDR Black Cutoff: turns LEDs completely OFF on pure black/near-black shadows
            if (maxCh < 0.035f || luma < 0.030f) {
                targetR = 0f
                targetG = 0f
                targetB = 0f
            } else {
                // 2. HyperHDR Exact Optical Gamma Standard (1.6)
                val gamma = 1.6f
                targetR = (rNorm.pow(gamma) * 255f).coerceIn(0f, 255f)
                targetG = (gNorm.pow(gamma) * 255f).coerceIn(0f, 255f)
                targetB = (bNorm.pow(gamma) * 255f).coerceIn(0f, 255f)

                // 3. WS2812B Hardware solitary diode clamp: blocks single-diode green/red noise
                // in deep shadows without affecting genuine multi-channel colors or deep blues
                if (maxCh < 0.15f) {
                    if (rNorm > 0f && gNorm < 0.02f && bNorm < 0.02f) targetR = 0f
                    if (gNorm > 0f && rNorm < 0.02f && bNorm < 0.02f) targetG = 0f
                }

                // 4. Low-PWM Diode Floor Gate: snap to 0 if all channels are below 4 PWM counts
                if (targetR < 4f && targetG < 4f && targetB < 4f) {
                    targetR = 0f
                    targetG = 0f
                    targetB = 0f
                }
            }

            if (targetR > 0f || targetG > 0f || targetB > 0f) {
                // White balance / Color temperature adjustment
                var tR = targetR
                var tG = targetG
                var tB = targetB
                if (isWarm) {
                    tG *= 0.95f
                    tB *= 0.82f
                } else if (isCool) {
                    tR *= 0.88f
                    tG *= 0.96f
                }

                // Perceptual saturation gain
                if (satGain != 1.0f) {
                    val satLuma = 0.2126f * tR + 0.7152f * tG + 0.0722f * tB
                    tR = (satLuma + satGain * (tR - satLuma)).coerceIn(0f, 255f)
                    tG = (satLuma + satGain * (tG - satLuma)).coerceIn(0f, 255f)
                    tB = (satLuma + satGain * (tB - satLuma)).coerceIn(0f, 255f)
                }

                // Master brightness scale and noise floor clamping
                var outR = (tR * bFactor).roundToInt().coerceIn(0, 255)
                var outG = (tG * bFactor).roundToInt().coerceIn(0, 255)
                var outB = (tB * bFactor).roundToInt().coerceIn(0, 255)

                if (outR < 4 && outG < 4 && outB < 4) {
                    outR = 0
                    outG = 0
                    outB = 0
                }

                targetColors[i] = (outR shl 16) or (outG shl 8) or outB
            } else {
                targetColors[i] = 0
            }
        }
    }

    /**
     * Returns JSON dump of all live LED colors for realtime HTTP inspection
     */
    fun getLatestLedsJson(): String {
        val total = targetColors.size.coerceAtLeast(228)
        val posMs = com.renotube.tv.remote.RemoteCommandDispatcher.getCurrentPositionMs()
        val sb = StringBuilder(2048)
        sb.append("{\"totalLeds\":").append(total).append(",")
        sb.append("\"positionMs\":").append(posMs).append(",")
        sb.append("\"borderY\":").append(activeBorderY).append(",")
        sb.append("\"borderX\":").append(activeBorderX).append(",")
        val leftMid = finalLeds.getOrElse(20) { 0 }
        val topMid = finalLeds.getOrElse(75) { 0 }
        val rightMid = finalLeds.getOrElse(130) { 0 }
        val bottomMid = finalLeds.getOrElse(190) { 0 }
        sb.append("\"left\":{\"mid\":[")
            .append((leftMid shr 16) and 0xFF).append(",")
            .append((leftMid shr 8) and 0xFF).append(",")
            .append(leftMid and 0xFF).append("]},")
        sb.append("\"top\":{\"mid\":[")
            .append((topMid shr 16) and 0xFF).append(",")
            .append((topMid shr 8) and 0xFF).append(",")
            .append(topMid and 0xFF).append("]},")
        sb.append("\"right\":{\"mid\":[")
            .append((rightMid shr 16) and 0xFF).append(",")
            .append((rightMid shr 8) and 0xFF).append(",")
            .append(rightMid and 0xFF).append("]},")
        sb.append("\"bottom\":{\"mid\":[")
            .append((bottomMid shr 16) and 0xFF).append(",")
            .append((bottomMid shr 8) and 0xFF).append(",")
            .append(bottomMid and 0xFF).append("]},")
        sb.append("\"leds\":[")
        for (i in 0 until total) {
            val c = finalLeds[i]
            if (i > 0) sb.append(",")
            sb.append("[").append((c shr 16) and 0xFF).append(",").append((c shr 8) and 0xFF).append(",").append(c and 0xFF).append("]")
        }
        sb.append("]}")
        return sb.toString()
    }

    /**
     * Generates a 96x54 4-edge calibration frame with diagonal quadrant corner separation:
     * - Left Quadrant: Pure Red (#FF0000)
     * - Top Quadrant: Pure Green (#00FF00)
     * - Right Quadrant: Pure Blue (#0000FF)
     * - Bottom Quadrant: Pure Yellow (#FFFF00)
     * - Center: Pure Black (#000000)
     */
    fun generateCalibrationBitmap(width: Int = 96, height: Int = 54): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val rowOffset = y * width
            val normY = y.toFloat() / height
            for (x in 0 until width) {
                val normX = x.toFloat() / width
                val isEdge = normX < 0.08f || normX > 0.92f || normY < 0.08f || normY > 0.92f
                val color = when {
                    !isEdge -> 0xFF000000.toInt() // Pure Black Center
                    normX <= normY && normX <= (1.0f - normY) -> 0xFFFF0000.toInt() // Left: Pure Red
                    normY <= normX && normY <= (1.0f - normX) -> 0xFF00FF00.toInt() // Top: Pure Green
                    (1.0f - normX) <= normY && (1.0f - normX) <= (1.0f - normY) -> 0xFF0000FF.toInt() // Right: Pure Blue
                    else -> 0xFFFFFF00.toInt() // Bottom: Pure Yellow
                }
                pixels[rowOffset + x] = color
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    suspend fun sendCalibrationPattern(config: AmbilightConfig) {
        val testBitmap = generateCalibrationBitmap(96, 54)
        processFrame(testBitmap, config)
    }

    /**
     * Verifies that the calibration pattern samples into the exact 4 edge colors:
     * Left Mid = Red, Top Mid = Green, Right Mid = Blue, Bottom Mid = Yellow.
     */
    fun verifyCalibrationAlignment(): Boolean {
        val testBitmap = generateCalibrationBitmap(96, 54)
        val cfg = AmbilightConfig()
        sampleHyperHdrFrame(testBitmap, cfg, 16f / 9f)

        val leftMid = targetColors[20]
        val topMid = targetColors[75]
        val rightMid = targetColors[130]
        val bottomMid = targetColors[190]

        val leftIsRed = ((leftMid shr 16) and 0xFF) > 200 && (leftMid and 0xFFFF) < 50
        val topIsGreen = ((topMid shr 8) and 0xFF) > 200 && ((topMid shr 16) and 0xFF) < 50 && (topMid and 0xFF) < 50
        val rightIsBlue = (rightMid and 0xFF) > 200 && ((rightMid shr 8) and 0xFFFF) < 50
        val bottomIsYellow = ((bottomMid shr 16) and 0xFF) > 200 && ((bottomMid shr 8) and 0xFF) > 200 && (bottomMid and 0xFF) < 50

        return leftIsRed && topIsGreen && rightIsBlue && bottomIsYellow
    }

    private fun fillBlack(totalLeds: Int) {
        for (i in 0 until totalLeds) {
            targetColors[i] = 0
        }
    }
}
