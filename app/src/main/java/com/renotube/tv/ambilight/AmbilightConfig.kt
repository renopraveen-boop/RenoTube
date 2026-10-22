package com.renotube.tv.ambilight

data class AmbilightConfig(
    val enabled: Boolean = true,
    val wledIp: String = "192.168.1.6",
    val wledPort: Int = 4048, // 4048: DDP (Default), 21324: WLED DRGB protocol
    val wledDeviceName: String = "WLED Lightstrip",
    val topLeds: Int = 71,
    val rightLeds: Int = 41,
    val bottomLeds: Int = 76,
    val leftLeds: Int = 40,
    val bottomGapLeds: Int = 0, // Stand cutout where no LEDs are mounted
    val startCorner: String = "BOTTOM_LEFT", // "BOTTOM_LEFT", "BOTTOM_RIGHT", "TOP_LEFT", "TOP_RIGHT"
    val direction: String = "CLOCKWISE", // "CLOCKWISE", "COUNTER_CLOCKWISE"
    val brightness: Float = 0.75f,
    val saturationGain: Float = 1.0f,
    val colorTemperature: String = "NEUTRAL", // "NEUTRAL", "WARM", "COOL"
    val smoothFactor: Float = 0.35f,
    val keepAmbientGlow: Boolean = true,
    val letterboxProtection: Boolean = true,
    val autoTurnOffOnDisconnect: Boolean = true,
    val disconnectTimeoutMinutes: Int = 3
) {
    val totalLeds: Int get() = (topLeds + rightLeds + bottomLeds + leftLeds - bottomGapLeds).coerceAtLeast(1)
}
