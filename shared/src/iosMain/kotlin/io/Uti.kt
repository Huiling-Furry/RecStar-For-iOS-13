package io

object Uti {
    fun mapExtensions(extensions: List<String>): List<String> {
        return extensions.map { extension ->
            when (extension) {
                "txt" -> "public.text"
                "wav" -> "public.audio"
                else -> "public.data"
            }
        }
    }
}