package com.example.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Exact Shape Tokens from DESIGN.md:
 * - Sheets: 28dp
 * - Cards: 20dp
 * - Buttons and chips: fully rounded (CircleShape)
 */
object MochiShapes {
    val sheet = RoundedCornerShape(28.dp)
    val sheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val card = RoundedCornerShape(20.dp)
    val pill = CircleShape
}

val MochiMaterialShapes = Shapes(
    extraSmall = CircleShape,
    small = CircleShape,
    medium = MochiShapes.card,
    large = MochiShapes.sheet,
    extraLarge = MochiShapes.sheet
)
