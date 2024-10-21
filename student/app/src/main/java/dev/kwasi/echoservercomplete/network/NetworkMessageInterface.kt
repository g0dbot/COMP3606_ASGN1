package dev.kwasi.echoservercomplete.network

import dev.kwasi.echoservercomplete.models.ContentModel

interface NetworkMessageInterface {
    fun onContent(content: ContentModel)
}