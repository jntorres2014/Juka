package com.example.juka.data.models

data class PezCapturado @JvmOverloads constructor(
    val especie: String = "",
    val cantidad: Int = 0,
    val observaciones: String? = null
)
