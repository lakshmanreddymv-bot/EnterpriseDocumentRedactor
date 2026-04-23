package com.example.enterprisedocumentredactor.domain.model

data class RedactionResult(
    val documentId: String,
    val outputPath: String,
    val redactedItems: List<RedactionItem>,
    val completedAt: Long = System.currentTimeMillis()
) {
    val countByType: Map<PiiType, Int>
        get() = redactedItems.groupingBy { it.piiType }.eachCount()

    val totalCount: Int get() = redactedItems.size
}
