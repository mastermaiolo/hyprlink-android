// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui.kit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * Vetores próprios para substituir a dependência de 35 MB de material-icons-extended.
 * Apenas 3 ícones eram consumidos em toda a aplicação.
 */
object HyprIcons {
    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "HyprIcons.Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                fill = SolidColor(Color.Black),
                pathData = PathBuilder().apply {
                    moveTo(6.0f, 19.0f)
                    horizontalLineToRelative(4.0f)
                    lineTo(10.0f, 5.0f)
                    lineTo(6.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    close()
                    moveTo(14.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    horizontalLineToRelative(4.0f)
                    lineTo(18.0f, 5.0f)
                    horizontalLineToRelative(-4.0f)
                    close()
                }.nodes
            )
        }.build()
    }

    val SkipNext: ImageVector by lazy {
        ImageVector.Builder(
            name = "HyprIcons.SkipNext",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                fill = SolidColor(Color.Black),
                pathData = PathBuilder().apply {
                    moveTo(6.0f, 18.0f)
                    lineToRelative(8.5f, -6.0f)
                    lineTo(6.0f, 6.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(16.0f, 6.0f)
                    verticalLineToRelative(12.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(6.0f)
                    horizontalLineToRelative(-2.0f)
                    close()
                }.nodes
            )
        }.build()
    }

    val SkipPrevious: ImageVector by lazy {
        ImageVector.Builder(
            name = "HyprIcons.SkipPrevious",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                fill = SolidColor(Color.Black),
                pathData = PathBuilder().apply {
                    moveTo(6.0f, 6.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(12.0f)
                    lineTo(6.0f, 18.0f)
                    close()
                    moveTo(9.5f, 12.0f)
                    lineToRelative(8.5f, 6.0f)
                    lineTo(18.0f, 6.0f)
                    close()
                }.nodes
            )
        }.build()
    }
}
