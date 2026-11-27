package com.wusper.zeyao.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Pure Kotlin monochrome minimalist stroke icons (24x24 viewport, 1.8dp stroke).
 * Zero reflection, zero R8 risk, cohesive modern flat streamlined aesthetic.
 */
object ZeyaoIcons {
    private val stroke = SolidColor(Color.Black)

    private inline fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private inline fun ImageVector.Builder.strokePath(block: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = stroke,
            strokeLineWidth = 1.85f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }

    val ArrowLeft: ImageVector by lazy {
        icon("ArrowLeft") {
            strokePath {
                moveTo(19f, 12f); lineTo(5f, 12f)
                moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
            }
        }
    }

    val ArrowRight: ImageVector by lazy {
        icon("ArrowRight") {
            strokePath {
                moveTo(5f, 12f); lineTo(19f, 12f)
                moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
            }
        }
    }

    val ArrowUpRight: ImageVector by lazy {
        icon("ArrowUpRight") {
            strokePath {
                moveTo(7f, 17f); lineTo(17f, 7f)
                moveTo(8f, 7f); lineTo(17f, 7f); lineTo(17f, 16f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            strokePath {
                moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            strokePath {
                moveTo(18f, 6f); lineTo(6f, 18f)
                moveTo(6f, 6f); lineTo(18f, 18f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            strokePath {
                moveTo(5f, 13f); lineTo(10f, 18f); lineTo(19f, 7f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            strokePath {
                // Outer gear circle + inner circle
                moveTo(12f, 15f)
                arcTo(3f, 3f, 0f, true, true, 12f, 9f)
                arcTo(3f, 3f, 0f, true, true, 12f, 15f)
                moveTo(12f, 3f); lineTo(12f, 5.2f)
                moveTo(12f, 18.8f); lineTo(12f, 21f)
                moveTo(3f, 12f); lineTo(5.2f, 12f)
                moveTo(18.8f, 12f); lineTo(21f, 12f)
                moveTo(5.6f, 5.6f); lineTo(7.2f, 7.2f)
                moveTo(16.8f, 16.8f); lineTo(18.4f, 18.4f)
                moveTo(18.4f, 5.6f); lineTo(16.8f, 7.2f)
                moveTo(7.2f, 16.8f); lineTo(5.6f, 18.4f)
            }
        }
    }

    val Grid: ImageVector by lazy {
        icon("Grid") {
            strokePath {
                moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
                moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
                moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
                moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
            }
        }
    }

    val Shield: ImageVector by lazy {
        icon("Shield") {
            strokePath {
                moveTo(12f, 3f)
                lineTo(19f, 6f)
                lineTo(19f, 11.5f)
                curveTo(19f, 16.2f, 15.8f, 19.6f, 12f, 21f)
                curveTo(8.2f, 19.6f, 5f, 16.2f, 5f, 11.5f)
                lineTo(5f, 6f)
                close()
            }
        }
    }

    val ShieldCheck: ImageVector by lazy {
        icon("ShieldCheck") {
            strokePath {
                moveTo(12f, 3f)
                lineTo(19f, 6f)
                lineTo(19f, 11.5f)
                curveTo(19f, 16.2f, 15.8f, 19.6f, 12f, 21f)
                curveTo(8.2f, 19.6f, 5f, 16.2f, 5f, 11.5f)
                lineTo(5f, 6f)
                close()
                moveTo(9f, 12f); lineTo(11f, 14f); lineTo(15.5f, 9.5f)
            }
        }
    }

    val Globe: ImageVector by lazy {
        icon("Globe") {
            strokePath {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                moveTo(3.5f, 12f); lineTo(20.5f, 12f)
                moveTo(12f, 3f)
                curveTo(14.8f, 5.8f, 16f, 8.8f, 16f, 12f)
                curveTo(16f, 15.2f, 14.8f, 18.2f, 12f, 21f)
                curveTo(9.2f, 18.2f, 8f, 15.2f, 8f, 12f)
                curveTo(8f, 8.8f, 9.2f, 5.8f, 12f, 3f)
            }
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            strokePath {
                moveTo(11f, 18f)
                arcTo(7f, 7f, 0f, true, true, 11f, 4f)
                arcTo(7f, 7f, 0f, true, true, 11f, 18f)
                moveTo(16.2f, 16.2f); lineTo(20.5f, 20.5f)
            }
        }
    }

    val ListIcon: ImageVector by lazy {
        icon("ListIcon") {
            strokePath {
                moveTo(8f, 6f); lineTo(20f, 6f)
                moveTo(8f, 12f); lineTo(20f, 12f)
                moveTo(8f, 18f); lineTo(20f, 18f)
                moveTo(4f, 6f); lineTo(4.01f, 6f)
                moveTo(4f, 12f); lineTo(4.01f, 12f)
                moveTo(4f, 18f); lineTo(4.01f, 18f)
            }
        }
    }

    val Pin: ImageVector by lazy {
        icon("Pin") {
            strokePath {
                moveTo(14.5f, 4.5f); lineTo(19.5f, 9.5f)
                moveTo(15.5f, 5.5f); lineTo(11.5f, 9.5f)
                moveTo(18.5f, 8.5f); lineTo(14.5f, 12.5f)
                moveTo(9f, 9f); lineTo(15f, 15f)
                moveTo(10.5f, 13.5f); lineTo(5f, 19f)
            }
        }
    }

    val Alert: ImageVector by lazy {
        icon("Alert") {
            strokePath {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                moveTo(12f, 8f); lineTo(12f, 13f)
                moveTo(12f, 16.5f); lineTo(12.01f, 16.5f)
            }
        }
    }

    val Share: ImageVector by lazy {
        icon("Share") {
            strokePath {
                moveTo(12f, 15f); lineTo(12f, 4f)
                moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f)
                moveTo(5f, 13f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 13f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        icon("Copy") {
            strokePath {
                moveTo(9f, 9f); lineTo(19f, 9f); lineTo(19f, 20f); lineTo(9f, 20f); close()
                moveTo(15f, 9f); lineTo(15f, 5f); lineTo(5f, 5f); lineTo(5f, 16f); lineTo(9f, 16f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            strokePath {
                moveTo(4f, 7f); lineTo(20f, 7f)
                moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
                moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f)
                moveTo(10f, 11f); lineTo(10f, 16f)
                moveTo(14f, 11f); lineTo(14f, 16f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            strokePath {
                moveTo(19f, 12f)
                arcTo(7f, 7f, 0f, true, true, 16.5f, 6.6f)
                moveTo(17f, 4f); lineTo(17f, 7.5f); lineTo(13.5f, 7.5f)
            }
        }
    }

    val Compress: ImageVector by lazy {
        icon("Compress") {
            strokePath {
                moveTo(4f, 12f); lineTo(9f, 12f)
                moveTo(7f, 9f); lineTo(10f, 12f); lineTo(7f, 15f)
                moveTo(20f, 12f); lineTo(15f, 12f)
                moveTo(17f, 9f); lineTo(14f, 12f); lineTo(17f, 15f)
            }
        }
    }

    val Expand: ImageVector by lazy {
        icon("Expand") {
            strokePath {
                moveTo(9f, 12f); lineTo(4f, 12f)
                moveTo(7f, 9f); lineTo(4f, 12f); lineTo(7f, 15f)
                moveTo(15f, 12f); lineTo(20f, 12f)
                moveTo(17f, 9f); lineTo(20f, 12f); lineTo(17f, 15f)
            }
        }
    }

    val TextSmaller: ImageVector by lazy {
        icon("TextSmaller") {
            strokePath {
                moveTo(4f, 18f); lineTo(8.5f, 6f); lineTo(13f, 18f)
                moveTo(5.5f, 14f); lineTo(11.5f, 14f)
                moveTo(15f, 12f); lineTo(21f, 12f)
            }
        }
    }

    val TextLarger: ImageVector by lazy {
        icon("TextLarger") {
            strokePath {
                moveTo(4f, 18f); lineTo(8.5f, 6f); lineTo(13f, 18f)
                moveTo(5.5f, 14f); lineTo(11.5f, 14f)
                moveTo(15f, 12f); lineTo(21f, 12f)
                moveTo(18f, 9f); lineTo(18f, 15f)
            }
        }
    }

    val Link: ImageVector by lazy {
        icon("Link") {
            strokePath {
                moveTo(10f, 14f); lineTo(14f, 10f)
                moveTo(13f, 7f); lineTo(15f, 5f)
                arcTo(3.5f, 3.5f, 0f, false, true, 20f, 10f)
                lineTo(18f, 12f)
                moveTo(11f, 17f); lineTo(9f, 19f)
                arcTo(3.5f, 3.5f, 0f, false, true, 4f, 14f)
                lineTo(6f, 12f)
            }
        }
    }

    val Block: ImageVector by lazy {
        icon("Block") {
            strokePath {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                moveTo(6f, 6f); lineTo(18f, 18f)
            }
        }
    }

    val Bolt: ImageVector by lazy {
        icon("Bolt") {
            strokePath {
                moveTo(13f, 3f); lineTo(5f, 14f); lineTo(12f, 14f); lineTo(11f, 21f); lineTo(19f, 10f); lineTo(12f, 10f); close()
            }
        }
    }

    val Clock: ImageVector by lazy {
        icon("Clock") {
            strokePath {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        icon("Lock") {
            strokePath {
                moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 20f); lineTo(6f, 20f); close()
                moveTo(8.5f, 11f); lineTo(8.5f, 7.5f)
                arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 7.5f)
                lineTo(15.5f, 11f)
            }
        }
    }

    val Key: ImageVector by lazy {
        icon("Key") {
            strokePath {
                moveTo(8f, 16f)
                arcTo(4f, 4f, 0f, true, true, 8f, 8f)
                arcTo(4f, 4f, 0f, true, true, 8f, 16f)
                moveTo(12f, 12f); lineTo(21f, 12f)
                moveTo(18f, 12f); lineTo(18f, 15.5f)
                moveTo(21f, 12f); lineTo(21f, 15f)
            }
        }
    }

    val Sliders: ImageVector by lazy {
        icon("Sliders") {
            strokePath {
                moveTo(4f, 7f); lineTo(20f, 7f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 17f); lineTo(20f, 17f)
                moveTo(9f, 5f); lineTo(9f, 9f)
                moveTo(15f, 10f); lineTo(15f, 14f)
                moveTo(10f, 15f); lineTo(10f, 19f)
            }
        }
    }

    val Sparkle: ImageVector by lazy {
        icon("Sparkle") {
            strokePath {
                moveTo(12f, 3f); lineTo(14.2f, 9.8f); lineTo(21f, 12f)
                lineTo(14.2f, 14.2f); lineTo(12f, 21f); lineTo(9.8f, 14.2f)
                lineTo(3f, 12f); lineTo(9.8f, 9.8f); close()
            }
        }
    }

    val Eye: ImageVector by lazy {
        icon("Eye") {
            strokePath {
                moveTo(2.5f, 12f)
                curveTo(5f, 7.5f, 8.5f, 5.5f, 12f, 5.5f)
                curveTo(15.5f, 5.5f, 19f, 7.5f, 21.5f, 12f)
                curveTo(19f, 16.5f, 15.5f, 18.5f, 12f, 18.5f)
                curveTo(8.5f, 18.5f, 5f, 16.5f, 2.5f, 12f)
                moveTo(12f, 14.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 12f, 9.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 12f, 14.5f)
            }
        }
    }

    val EyeOff: ImageVector by lazy {
        icon("EyeOff") {
            strokePath {
                moveTo(3f, 3f); lineTo(21f, 21f)
                moveTo(10.2f, 5.7f)
                curveTo(10.8f, 5.6f, 11.4f, 5.5f, 12f, 5.5f)
                curveTo(15.5f, 5.5f, 19f, 7.5f, 21.5f, 12f)
                curveTo(20.6f, 13.6f, 19.5f, 15f, 18.2f, 16f)
                moveTo(6.2f, 7.8f)
                curveTo(4.7f, 8.9f, 3.4f, 10.3f, 2.5f, 12f)
                curveTo(5f, 16.5f, 8.5f, 18.5f, 12f, 18.5f)
                curveTo(13.2f, 18.5f, 14.4f, 18.2f, 15.5f, 17.7f)
            }
        }
    }

    val Document: ImageVector by lazy {
        icon("Document") {
            strokePath {
                moveTo(6f, 3f); lineTo(14f, 3f); lineTo(18f, 7f); lineTo(18f, 21f); lineTo(6f, 21f); close()
                moveTo(14f, 3f); lineTo(14f, 7f); lineTo(18f, 7f)
                moveTo(9f, 12f); lineTo(15f, 12f)
                moveTo(9f, 16f); lineTo(13f, 16f)
            }
        }
    }

    val Clipboard: ImageVector by lazy {
        icon("Clipboard") {
            strokePath {
                moveTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f); lineTo(9f, 7f); close()
                moveTo(9f, 5.5f); lineTo(6f, 5.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 5.5f); lineTo(15f, 5.5f)
            }
        }
    }

    val Compass: ImageVector by lazy {
        icon("Compass") {
            strokePath {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                moveTo(15.5f, 8.5f); lineTo(13.5f, 13.5f); lineTo(8.5f, 15.5f); lineTo(10.5f, 10.5f); close()
            }
        }
    }

    val Pointer: ImageVector by lazy {
        icon("Pointer") {
            strokePath {
                moveTo(5f, 4f); lineTo(11f, 20f); lineTo(13.5f, 13.5f); lineTo(20f, 11f); close()
            }
        }
    }

    val Contrast: ImageVector by lazy {
        icon("Contrast") {
            strokePath {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                moveTo(12f, 3f); lineTo(12f, 21f)
            }
        }
    }

    val Layout: ImageVector by lazy {
        icon("Layout") {
            strokePath {
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
                moveTo(4f, 13f); lineTo(20f, 13f)
                moveTo(12f, 13f); lineTo(12f, 19f)
            }
        }
    }

    val Image: ImageVector by lazy {
        icon("Image") {
            strokePath {
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
                moveTo(4f, 16f); lineTo(9f, 11f); lineTo(13.5f, 15.5f); lineTo(16.5f, 12.5f); lineTo(20f, 16f)
                moveTo(8.5f, 9.5f); lineTo(8.51f, 9.5f)
            }
        }
    }

    val BarChart: ImageVector by lazy {
        icon("BarChart") {
            strokePath {
                moveTo(6f, 19f); lineTo(6f, 13f)
                moveTo(12f, 19f); lineTo(12f, 7f)
                moveTo(18f, 19f); lineTo(18f, 10f)
                moveTo(4f, 19f); lineTo(20f, 19f)
            }
        }
    }

    val Code: ImageVector by lazy {
        icon("Code") {
            strokePath {
                moveTo(8f, 7f); lineTo(3f, 12f); lineTo(8f, 17f)
                moveTo(16f, 7f); lineTo(21f, 12f); lineTo(16f, 17f)
                moveTo(14f, 5f); lineTo(10f, 19f)
            }
        }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
            strokePath {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                moveTo(12f, 11f); lineTo(12f, 16f)
                moveTo(12f, 8f); lineTo(12.01f, 8f)
            }
        }
    }

    val Play: ImageVector by lazy {
        icon("Play") {
            strokePath {
                moveTo(7f, 5f); lineTo(19f, 12f); lineTo(7f, 19f); close()
            }
        }
    }

    val Award: ImageVector by lazy {
        icon("Award") {
            strokePath {
                moveTo(12f, 15f)
                arcTo(5f, 5f, 0f, true, true, 12f, 5f)
                arcTo(5f, 5f, 0f, true, true, 12f, 15f)
                moveTo(9f, 14.2f); lineTo(8f, 21f); lineTo(12f, 19f); lineTo(16f, 21f); lineTo(15f, 14.2f)
            }
        }
    }
}
