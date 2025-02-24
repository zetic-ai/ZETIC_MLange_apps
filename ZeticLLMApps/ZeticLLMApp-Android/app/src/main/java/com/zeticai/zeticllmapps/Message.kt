package com.zeticai.zeticllmapps

data class Message(
    val content: StringBuilder = StringBuilder(),
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)