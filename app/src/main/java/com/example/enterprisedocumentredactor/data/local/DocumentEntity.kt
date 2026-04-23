package com.example.enterprisedocumentredactor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val fileName: String,
    val pageCount: Int,
    val redactedItemCount: Int,
    val redactedPath: String,
    val createdAt: Long
)
