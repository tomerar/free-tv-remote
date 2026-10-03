package io.github.tomerar.freetvremote.data

import kotlinx.serialization.json.Json

internal val AppJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
