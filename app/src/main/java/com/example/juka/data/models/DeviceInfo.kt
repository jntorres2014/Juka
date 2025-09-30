package com.example.juka.data.models

data class DeviceInfo @JvmOverloads constructor(
    val modelo: String = "",
    val marca: String = "",
    val versionAndroid: String = "",
    val versionApp: String = "1.0"
)
