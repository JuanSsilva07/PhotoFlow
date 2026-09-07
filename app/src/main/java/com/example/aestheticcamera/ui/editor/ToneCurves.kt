package com.example.aestheticcamera.ui.editor

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.math.sqrt

enum class CurveChannel {
    RGB, RED, GREEN, BLUE
}

data class CurvesLuts(
    val r: IntArray = IntArray(256) { it },
    val g: IntArray = IntArray(256) { it },
    val b: IntArray = IntArray(256) { it }
)

object CubicSpline {
    fun calculateLut(points: List<PointF>): IntArray {
        val lut = IntArray(256)
        val sorted = points.sortedBy { it.x }
        if (sorted.isEmpty()) {
            for (i in 0..255) lut[i] = i
            return lut
        }
        if (sorted.size < 2) {
            val y = (sorted[0].y * 255f).toInt().coerceIn(0, 255)
            for (i in 0..255) lut[i] = y
            return lut
        }

        val n = sorted.size
        val x = DoubleArray(n) { sorted[it].x.toDouble() }
        val y = DoubleArray(n) { sorted[it].y.toDouble() }

        val h = DoubleArray(n - 1) { x[it + 1] - x[it] }

        val a = DoubleArray(n)
        val b = DoubleArray(n)
        val c = DoubleArray(n)
        val d = DoubleArray(n)

        b[0] = 1.0
        b[n - 1] = 1.0

        for (i in 1 until n - 1) {
            a[i] = h[i - 1] / 6.0
            b[i] = (h[i - 1] + h[i]) / 3.0
            c[i] = h[i] / 6.0
            d[i] = (y[i + 1] - y[i]) / h[i] - (y[i] - y[i - 1]) / h[i - 1]
        }

        val m = DoubleArray(n)
        val cPrime = DoubleArray(n)
        val dPrime = DoubleArray(n)

        cPrime[0] = c[0] / b[0]
        dPrime[0] = d[0] / b[0]

        for (i in 1 until n) {
            val denom = b[i] - a[i] * cPrime[i - 1]
            cPrime[i] = c[i] / denom
            dPrime[i] = (d[i] - a[i] * dPrime[i - 1]) / denom
        }

        m[n - 1] = dPrime[n - 1]
        for (i in n - 2 downTo 0) {
            m[i] = dPrime[i] - cPrime[i] * m[i + 1]
        }

        for (i in 0..255) {
            val t = i / 255f
            var k = 0
            for (j in 0 until n - 1) {
                if (t >= x[j] && t <= x[j + 1]) {
                    k = j
                    break
                }
            }
            if (t < x[0]) {
                lut[i] = (y[0] * 255f).toInt().coerceIn(0, 255)
            } else if (t > x[n - 1]) {
                lut[i] = (y[n - 1] * 255f).toInt().coerceIn(0, 255)
            } else {
                val hk = h[k]
                val xk = x[k]
                val xk1 = x[k + 1]
                val mk = m[k]
                val mk1 = m[k + 1]
                val yk = y[k]
                val yk1 = y[k + 1]

                // Prevent divisions by zero or extremely small values if control points are too close
                val valY = if (hk > 0.0001) {
                    mk * (xk1 - t).pow(3.0) / (6.0 * hk) +
                            mk1 * (t - xk).pow(3.0) / (6.0 * hk) +
                            (yk - mk * hk * hk / 6.0) * (xk1 - t) / hk +
                            (yk1 - mk1 * hk * hk / 6.0) * (t - xk) / hk
                } else {
                    yk
                }

                lut[i] = (valY * 255.0).toInt().coerceIn(0, 255)
            }
        }

        return lut
    }
}

@Composable
fun CurvesCanvas(
    points: List<PointF>,
    channelColor: Color,
    onPointsChange: (List<PointF>) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activePointIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val touchRadiusPx = with(density) { 40.dp.toPx() }
    val currentPoints by rememberUpdatedState(points)

    val canvasBgColor = remember(channelColor) {
        if (channelColor == Color.White) Color(0xFF121212)
        else channelColor.copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(canvasBgColor, RoundedCornerShape(12.dp))
            .border(1.dp, channelColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val currentList = currentPoints
                            val nx = offset.x / size.width
                            val ny = 1f - (offset.y / size.height)

                            // Try to select an existing point
                            var minDistance = Float.MAX_VALUE
                            var selectedIdx: Int? = null

                            for (i in currentList.indices) {
                                val p = currentList[i]
                                val px = p.x * size.width
                                val py = (1f - p.y) * size.height
                                val dist = sqrt((px - offset.x).pow(2) + (py - offset.y).pow(2))
                                if (dist < touchRadiusPx && dist < minDistance) {
                                    minDistance = dist
                                    selectedIdx = i
                                }
                            }

                            if (selectedIdx != null) {
                                activePointIndex = selectedIdx
                            } else {
                                // Add a new point if space permits
                                if (currentList.size < 7) {
                                    val newPoint = PointF(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
                                    val mutablePoints = currentList.toMutableList()
                                    mutablePoints.add(newPoint)
                                    mutablePoints.sortBy { it.x }
                                    val newIndex = mutablePoints.indexOf(newPoint)
                                    onPointsChange(mutablePoints)
                                    activePointIndex = newIndex
                                    onDragFinished()
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val currentList = currentPoints
                            // Find closest point to drag
                            var minDistance = Float.MAX_VALUE
                            var selectedIdx: Int? = null

                            for (i in currentList.indices) {
                                val p = currentList[i]
                                val px = p.x * size.width
                                val py = (1f - p.y) * size.height
                                val dist = sqrt((px - offset.x).pow(2) + (py - offset.y).pow(2))
                                if (dist < touchRadiusPx && dist < minDistance) {
                                    minDistance = dist
                                    selectedIdx = i
                                }
                            }
                            activePointIndex = selectedIdx
                        },
                        onDragEnd = {
                            // Touch lift/gesture completed
                            onDragFinished()
                        },
                        onDrag = { change, dragAmount ->
                            val currentList = currentPoints
                            val index = activePointIndex ?: return@detectDragGestures
                            change.consume()

                            val currentPoint = currentList[index]
                            val px = currentPoint.x * size.width + dragAmount.x
                            val py = (1f - currentPoint.y) * size.height + dragAmount.y

                            var nx = px / size.width
                            var ny = 1f - (py / size.height)

                            nx = nx.coerceIn(0f, 1f)
                            ny = ny.coerceIn(0f, 1f)

                            val mutablePoints = currentList.toMutableList()

                            if (index == 0) {
                                // First point is locked to X=0
                                mutablePoints[0] = PointF(0f, ny)
                            } else if (index == currentList.size - 1) {
                                // Last point is locked to X=1
                                mutablePoints[currentList.size - 1] = PointF(1f, ny)
                            } else {
                                // Intermediate point, clamp between neighbors X
                                val minX = currentList[index - 1].x + 0.03f
                                val maxX = currentList[index + 1].x - 0.03f
                                nx = nx.coerceIn(minX, maxX)
                                mutablePoints[index] = PointF(nx, ny)
                            }

                            onPointsChange(mutablePoints)
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height

            // 1. Draw Grid Lines (4x4 squares)
            val gridColor = Color.White.copy(alpha = 0.07f)
            for (j in 1..3) {
                val frac = j * 0.25f
                // Vertical
                drawLine(
                    color = gridColor,
                    start = Offset(width * frac, 0f),
                    end = Offset(width * frac, height),
                    strokeWidth = 1.dp.toPx()
                )
                // Horizontal
                drawLine(
                    color = gridColor,
                    start = Offset(0f, height * frac),
                    end = Offset(width, height * frac),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 2. Draw Baseline (diagonal guideline)
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(0f, height),
                end = Offset(width, 0f),
                strokeWidth = 1.dp.toPx()
            )

            // 3. Generate and draw spline curve path
            val lut = CubicSpline.calculateLut(points)
            val path = Path().apply {
                moveTo(0f, (1f - lut[0] / 255f) * height)
                for (i in 1..255) {
                    lineTo((i / 255f) * width, (1f - lut[i] / 255f) * height)
                }
            }

            drawPath(
                path = path,
                color = channelColor,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // 4. Draw control point nodes
            points.forEachIndexed { idx, point ->
                val px = point.x * width
                val py = (1f - point.y) * height
                val isSelected = (idx == activePointIndex)

                // Outer highlight for active node
                if (isSelected) {
                    drawCircle(
                        color = Color(0xFFFFD54F),
                        radius = 8.dp.toPx(),
                        center = Offset(px, py)
                    )
                }

                // Inner filled circle
                drawCircle(
                    color = if (isSelected) Color.White else channelColor,
                    radius = 5.dp.toPx(),
                    center = Offset(px, py)
                )

                // Border for all points to make them pop
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = 5.dp.toPx(),
                    center = Offset(px, py),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }

    // Controls for deleting/resetting the selected curve point
    // Controls for deleting/resetting the selected curve point
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val selectedIndex = activePointIndex
        val canDelete = selectedIndex != null && selectedIndex != 0 && selectedIndex != points.size - 1

        if (canDelete) {
            Surface(
                onClick = {
                    if (selectedIndex != null) {
                        val mutablePoints = points.toMutableList()
                        mutablePoints.removeAt(selectedIndex)
                        onPointsChange(mutablePoints)
                        activePointIndex = null
                    }
                },
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFF5252).copy(alpha = 0.15f),
                border = BorderStroke(0.5.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
            ) {
                Text(
                    text = "Remover Ponto",
                    color = Color(0xFFFF5252),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        Surface(
            onClick = {
                onPointsChange(listOf(PointF(0f, 0f), PointF(1f, 1f)))
                activePointIndex = null
            },
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Text(
                text = "Resetar Canal",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun CurvesControlTabRow(
    selectedChannel: CurveChannel,
    onChannelChange: (CurveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = selectedChannel.ordinal,
        containerColor = Color.Transparent,
        contentColor = Color.White,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedChannel.ordinal]),
                color = when (selectedChannel) {
                    CurveChannel.RGB -> Color.White
                    CurveChannel.RED -> Color(0xFFFF3B30)
                    CurveChannel.GREEN -> Color(0xFF34C759)
                    CurveChannel.BLUE -> Color(0xFF007AFF)
                }
            )
        },
        modifier = modifier.fillMaxWidth()
    ) {
        CurveChannel.values().forEach { channel ->
            Tab(
                selected = selectedChannel == channel,
                onClick = { onChannelChange(channel) },
                modifier = Modifier.height(34.dp),
                text = {
                    Text(
                        text = channel.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedChannel == channel) {
                            when (channel) {
                                CurveChannel.RGB -> Color.White
                                CurveChannel.RED -> Color(0xFFFF3B30)
                                CurveChannel.GREEN -> Color(0xFF34C759)
                                CurveChannel.BLUE -> Color(0xFF007AFF)
                            }
                        } else {
                            Color.White.copy(alpha = 0.4f)
                        }
                    )
                }
            )
        }
    }
}
