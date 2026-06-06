package com.example.data.model

data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val avatarUri: String? = null
)
