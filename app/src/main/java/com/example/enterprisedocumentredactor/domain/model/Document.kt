package com.example.enterprisedocumentredactor.domain.model

data class Document(
    val id: String,
    val filePath: String,
    val fileName: String,
    val pageCount: Int,
    val redactedItemCount: Int = 0,
    val redactedPath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
