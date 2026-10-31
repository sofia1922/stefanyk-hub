package com.example.stefanykhub.data


internal fun materialContentType(fileType: String): String? = when (fileType.trim().uppercase()) {
    "PDF" -> "application/pdf"
    "DOC" -> "application/msword"
    "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "ZIP" -> "application/zip"
    "MP4" -> "video/mp4"
    "PNG" -> "image/png"
    "JPG", "JPEG" -> "image/jpeg"
    "WEBP" -> "image/webp"
    "GIF" -> "image/gif"
    "BMP" -> "image/bmp"
    "HEIC" -> "image/heic"
    "HEIF" -> "image/heif"
    else -> null
}
