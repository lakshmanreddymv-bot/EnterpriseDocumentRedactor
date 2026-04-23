package com.example.enterprisedocumentredactor.domain.model

import android.graphics.RectF

data class RedactionItem(
    val id: String,
    val piiType: PiiType,
    val text: String,
    val boundingBox: RectF,
    val pageIndex: Int,
    val isSelected: Boolean = true
)
