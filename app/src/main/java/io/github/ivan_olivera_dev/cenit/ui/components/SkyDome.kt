package io.github.ivan_olivera_dev.cenit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.sky.SkyDomeProjection
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import java.time.ZoneId
import kotlin.math.hypot

/**
 * Seasonal sun reference arcs displayed thinly on the sky dome (June/December solstices
 * and March equinox).
 */
data class DomeReferencePaths(
    val juneSolstice: List<PathPoint>,
    val decemberSolstice: List<PathPoint>,
    val equinox: List<PathPoint>,
)

/**
 * Horizon tick (sunrise or sunset) drawn on the dome's horizon ring.
 */
data class DomeHorizonTick(
    val azimuthDeg: Double,
    val label: String,
)

/**
 * Flat-compass sky dome (`sky-dome` token, Artboard 02): zenith at the centre, radial elevation,
 * horizon circle (`skyDay`), twilight rings at −6° (`skyCivil`) and −12° (`skyNautical`),
 * 30°/60° altitude rings, solstice & equinox reference paths, today's sun path solid with hour
 * dots and labels every 3 h, night path dotted outside the horizon, moon path dashed, and
 * selected-time sun & moon markers. North up by default (`headingDeg = 0.0`).
 */
@Composable
fun SkyDome(
    sunPath: List<PathPoint>,
    moonPath: List<PathPoint>,
    referencePaths: DomeReferencePaths,
    sunPosition: SkyPosition,
    moonPosition: SkyPosition,
    sunriseTick: DomeHorizonTick?,
    sunsetTick: DomeHorizonTick?,
    zone: ZoneId,
    contentDescription: String,
    modifier: Modifier = Modifier,
    headingDeg: Double = 0.0,
) {
    val palette = LocalCenitPalette.current
    val textMeasurer = rememberTextMeasurer()

    val cardinalN = stringResource(R.string.cardinal_n)
    val cardinalE = stringResource(R.string.cardinal_e)
    val cardinalS = stringResource(R.string.cardinal_s)
    val cardinalW = stringResource(R.string.cardinal_w)
    val junLabel = stringResource(R.string.dome_solstice_jun)
    val decLabel = stringResource(R.string.dome_solstice_dec)

    val baseLabelStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = "tnum",
    )

    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
            this.role = Role.Image
        },
    ) {
        val diameter = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = (diameter / 2f) - 4.dp.toPx()
        if (outerR <= 0f) return@Canvas

        val horizonR = (outerR * SkyDomeProjection.HORIZON_RADIUS).toFloat()
        val civilR = (outerR * SkyDomeProjection.CIVIL_TWILIGHT_RADIUS).toFloat()
        val hairline = 1.dp.toPx()

        fun toCanvas(elevationDeg: Double, azimuthDeg: Double): Offset {
            val pt = SkyDomeProjection.project(elevationDeg, azimuthDeg, headingDeg)
            return Offset(cx + (pt.x * outerR).toFloat(), cy + (pt.y * outerR).toFloat())
        }

        fun onRing(radiusPx: Float, azimuthDeg: Double): Offset {
            val pt = SkyDomeProjection.pointOnRing(1.0, azimuthDeg, headingDeg)
            return Offset(cx + (pt.x * radiusPx).toFloat(), cy + (pt.y * radiusPx).toFloat())
        }

        // 1. Twilight rings (−12° nautical, −6° civil) & horizon circle (0° day)
        drawCircle(color = palette.skyNautical, radius = outerR, center = Offset(cx, cy))
        drawCircle(
            color = palette.borderColor,
            radius = outerR,
            center = Offset(cx, cy),
            style = Stroke(width = hairline),
        )
        drawCircle(color = palette.skyCivil, radius = civilR, center = Offset(cx, cy))
        drawCircle(color = palette.skyDay, radius = horizonR, center = Offset(cx, cy))
        drawCircle(
            color = palette.controlBorder,
            radius = horizonR,
            center = Offset(cx, cy),
            style = Stroke(width = hairline),
        )

        // 2. 30° and 60° altitude rings
        drawCircle(
            color = palette.borderColor,
            radius = horizonR * 2f / 3f,
            center = Offset(cx, cy),
            style = Stroke(width = hairline),
        )
        drawCircle(
            color = palette.borderColor,
            radius = horizonR / 3f,
            center = Offset(cx, cy),
            style = Stroke(width = hairline),
        )

        // 3. Cardinal letters inside the horizon circle
        val cardinalOffset = horizonR - 11.dp.toPx()
        listOf(
            0.0 to cardinalN,
            90.0 to cardinalE,
            180.0 to cardinalS,
            270.0 to cardinalW,
        ).forEach { (az, label) ->
            val pos = onRing(cardinalOffset, az)
            val isNorth = az == 0.0
            drawCenteredText(
                textMeasurer = textMeasurer,
                text = label,
                center = pos,
                style = baseLabelStyle.copy(
                    color = if (isNorth) palette.textPrimary else palette.textSecondary,
                    fontWeight = if (isNorth) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }

        // 4. Solstice and equinox reference paths (above horizon)
        val refSolid = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
        val refDashed = Stroke(
            width = 1.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
        )
        drawThresholdPath(referencePaths.juneSolstice, threshold = 0.0, above = true, ::toCanvas, palette.textSecondary, refSolid)
        drawThresholdPath(referencePaths.decemberSolstice, threshold = 0.0, above = true, ::toCanvas, palette.textSecondary, refSolid)
        drawThresholdPath(referencePaths.equinox, threshold = 0.0, above = true, ::toCanvas, palette.textSecondary, refDashed)

        referencePaths.juneSolstice.maxByOrNull { it.position.elevation }
            ?.takeIf { it.position.elevation > 0.0 }
            ?.let { apex ->
                val p = toCanvas(apex.position.elevation, apex.position.azimuth)
                drawCenteredText(
                    textMeasurer = textMeasurer,
                    text = junLabel,
                    center = Offset(p.x, p.y - 8.dp.toPx()),
                    style = baseLabelStyle.copy(color = palette.textSecondary),
                )
            }
        referencePaths.decemberSolstice.maxByOrNull { it.position.elevation }
            ?.takeIf { it.position.elevation > 0.0 }
            ?.let { apex ->
                val p = toCanvas(apex.position.elevation, apex.position.azimuth)
                drawCenteredText(
                    textMeasurer = textMeasurer,
                    text = decLabel,
                    center = Offset(p.x, p.y + 10.dp.toPx()),
                    style = baseLabelStyle.copy(color = palette.textSecondary),
                )
            }

        // 5. Night sun path (dotted outside the horizon)
        val nightStroke = Stroke(
            width = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 4.dp.toPx())),
        )
        drawThresholdPath(sunPath, threshold = 0.0, above = false, ::toCanvas, palette.textSecondary, nightStroke)

        // 6. Moon path (dashed)
        val moonStroke = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
        drawThresholdPath(moonPath, threshold = 0.0, above = true, ::toCanvas, palette.moon, moonStroke)

        // 7. Today's sun path (solid)
        val sunStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawThresholdPath(sunPath, threshold = 0.0, above = true, ::toCanvas, palette.sun, sunStroke)

        // 8. Hour dots on today's sun path + labels every 3 h
        val dotRadius = 3.dp.toPx()
        val dotStroke = Stroke(width = 1.5.dp.toPx())
        val labelPullPx = 13.dp.toPx()
        val seenHours = HashSet<Int>()
        sunPath.forEach { pt ->
            val zoned = pt.instant.atZone(zone)
            if (zoned.minute != 0 || zoned.second != 0) return@forEach
            if (pt.position.elevation <= 0.5) return@forEach
            if (!seenHours.add(zoned.hour)) return@forEach

            val pos = toCanvas(pt.position.elevation, pt.position.azimuth)
            drawCircle(color = palette.bgColor, radius = dotRadius, center = pos)
            drawCircle(color = palette.sun, radius = dotRadius, center = pos, style = dotStroke)

            if (zoned.hour % 3 == 0) {
                val dx = cx - pos.x
                val dy = cy - pos.y
                val norm = hypot(dx, dy).coerceAtLeast(1f)
                val labelPos = Offset(
                    pos.x + (dx / norm) * labelPullPx,
                    pos.y + (dy / norm) * labelPullPx,
                )
                drawCenteredText(
                    textMeasurer = textMeasurer,
                    text = zoned.hour.toString(),
                    center = labelPos,
                    style = baseLabelStyle.copy(color = palette.textPrimary),
                )
            }
        }

        // 9. Sunrise & sunset horizon ticks + time labels
        listOfNotNull(sunriseTick, sunsetTick).forEach { tick ->
            val p1 = onRing(horizonR, tick.azimuthDeg)
            val p2 = onRing(horizonR - 9.dp.toPx(), tick.azimuthDeg)
            drawLine(
                color = palette.sun,
                start = p1,
                end = p2,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val labelPos = onRing(horizonR - 25.dp.toPx(), tick.azimuthDeg)
            drawCenteredText(
                textMeasurer = textMeasurer,
                text = tick.label,
                center = labelPos,
                style = baseLabelStyle.copy(color = palette.textPrimary),
            )
        }

        // 10. Moon marker at selected time (when above horizon)
        if (moonPosition.elevation > 0.0) {
            val mp = toCanvas(moonPosition.elevation, moonPosition.azimuth)
            drawCircle(color = palette.bgColor, radius = 6.dp.toPx(), center = mp)
            drawCircle(
                color = palette.moon,
                radius = 6.dp.toPx(),
                center = mp,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // 11. Sun marker at selected time
        val sp = toCanvas(sunPosition.elevation, sunPosition.azimuth)
        if (sunPosition.elevation > 0.0) {
            drawCircle(color = palette.sun, radius = 7.5.dp.toPx(), center = sp)
            drawCircle(
                color = palette.bgColor,
                radius = 7.5.dp.toPx(),
                center = sp,
                style = Stroke(width = 2.dp.toPx()),
            )
        } else {
            drawCircle(color = palette.bgColor, radius = 6.dp.toPx(), center = sp)
            drawCircle(
                color = palette.sun,
                radius = 6.dp.toPx(),
                center = sp,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // 12. Zenith dot
        drawCircle(color = palette.textSecondary, radius = 2.dp.toPx(), center = Offset(cx, cy))
    }
}

/**
 * Draws the contiguous segments of [points] where elevation is on the requested side of
 * [threshold], linearly interpolating crossings so arcs meet the horizon ring without gaps.
 */
private fun DrawScope.drawThresholdPath(
    points: List<PathPoint>,
    threshold: Double,
    above: Boolean,
    project: (Double, Double) -> Offset,
    color: Color,
    stroke: Stroke,
) {
    if (points.size < 2) return
    fun inside(elev: Double): Boolean = if (above) elev >= threshold else elev <= threshold
    fun interpolateCrossing(a: SkyPosition, b: SkyPosition): Offset {
        val denom = (b.elevation - a.elevation)
        val t = if (denom == 0.0) 0.5 else ((threshold - a.elevation) / denom).coerceIn(0.0, 1.0)
        var dAz = ((b.azimuth - a.azimuth + 540.0) % 360.0) - 180.0
        if (dAz < -180.0) dAz += 360.0
        val az = ((a.azimuth + dAz * t) % 360.0 + 360.0) % 360.0
        return project(threshold, az)
    }

    var currentPath: Path? = null
    var count = 0

    fun append(offset: Offset) {
        if (currentPath == null) {
            currentPath = Path().apply { moveTo(offset.x, offset.y) }
        } else {
            currentPath!!.lineTo(offset.x, offset.y)
        }
        count++
    }

    fun flush() {
        val p = currentPath
        if (p != null && count >= 2) {
            drawPath(path = p, color = color, style = stroke)
        }
        currentPath = null
        count = 0
    }

    var prev = points.first().position
    var prevIn = inside(prev.elevation)
    if (prevIn) append(project(prev.elevation, prev.azimuth))

    for (i in 1 until points.size) {
        val cur = points[i].position
        val curIn = inside(cur.elevation)
        when {
            prevIn && curIn -> append(project(cur.elevation, cur.azimuth))
            prevIn && !curIn -> {
                append(interpolateCrossing(prev, cur))
                flush()
            }
            !prevIn && curIn -> {
                append(interpolateCrossing(prev, cur))
                append(project(cur.elevation, cur.azimuth))
            }
        }
        prev = cur
        prevIn = curIn
    }
    flush()
}

private fun DrawScope.drawCenteredText(
    textMeasurer: TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
) {
    val result = textMeasurer.measure(text = text, style = style)
    drawText(
        textLayoutResult = result,
        topLeft = Offset(
            x = center.x - result.size.width / 2f,
            y = center.y - result.size.height / 2f,
        ),
    )
}
