package com.stremio.mobile.server

fun formatServerErrorMessage(message: String): String {
    return if (message.contains("failed to bind", ignoreCase = true) || message.contains("11470")) {
        "Port 11470 is already in use. Please close any other Stremio or streaming apps running in the background and try again."
    } else {
        message
    }
}
