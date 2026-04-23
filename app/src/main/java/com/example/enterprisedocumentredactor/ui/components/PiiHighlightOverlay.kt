package com.example.enterprisedocumentredactor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.example.enterprisedocumentredactor.domain.model.RedactionItem

@Composable
fun PiiHighlightOverlay(
    items: List<RedactionItem>,
    imageWidth: Int,
    imageHeight: Int,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(items) {
            detectTapGestures { tapOffset ->
                val scaleX = size.width.toFloat() / imageWidth
                val scaleY = size.height.toFloat() / imageHeight
                items.forEach { item ->
                    val left = item.boundingBox.left * scaleX
                    val top = item.boundingBox.top * scaleY
                    val right = item.boundingBox.right * scaleX
                    val bottom = item.boundingBox.bottom * scaleY
                    if (tapOffset.x in left..right && tapOffset.y in top..bottom) {
                        onToggle(item.id)
                        return@detectTapGestures
                    }
                }
            }
        }
    ) {
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight

        items.forEach { item ->
            val left = item.boundingBox.left * scaleX
            val top = item.boundingBox.top * scaleY
            val width = (item.boundingBox.right - item.boundingBox.left) * scaleX
            val height = (item.boundingBox.bottom - item.boundingBox.top) * scaleY

            val fillColor = if (item.isSelected)
                Color.Red.copy(alpha = 0.4f)
            else
                Color.Gray.copy(alpha = 0.4f)

            val strokeColor = if (item.isSelected) Color.Red else Color.Gray

            drawRect(
                color = fillColor,
                topLeft = Offset(left, top),
                size = Size(width, height)
            )
            drawRect(
                color = strokeColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
    }
}
