package com.zeticai.tencenthymt

data class Language(val name: String, val abbr: String) {
    override fun toString(): String = name
}

object Languages {
    val list = listOf(
        Language("Chinese", "zh"),
        Language("English", "en"),
        Language("French", "fr"),
        Language("Portuguese", "pt"),
        Language("Spanish", "es"),
        Language("Japanese", "ja"),
        Language("Turkish", "tr"),
        Language("Russian", "ru"),
        Language("Arabic", "ar"),
        Language("Korean", "ko"),
        Language("Thai", "th"),
        Language("Italian", "it"),
        Language("German", "de"),
        Language("Vietnamese", "vi"),
        Language("Malay", "ms"),
        Language("Indonesian", "id"),
        Language("Filipino", "tl"),
        Language("Hindi", "hi"),
        Language("Traditional Chinese", "zh-Hant"),
        Language("Polish", "pl"),
        Language("Czech", "cs"),
        Language("Dutch", "nl"),
        Language("Khmer", "km"),
        Language("Burmese", "my"),
        Language("Persian", "fa"),
        Language("Gujarati", "gu"),
        Language("Urdu", "ur"),
        Language("Telugu", "te"),
        Language("Marathi", "mr"),
        Language("Hebrew", "he"),
        Language("Bengali", "bn"),
        Language("Tamil", "ta"),
        Language("Ukrainian", "uk"),
        Language("Tibetan", "bo"),
        Language("Kazakh", "kk"),
        Language("Mongolian", "mn"),
        Language("Uyghur", "ug"),
        Language("Cantonese", "yue")
    )
}
