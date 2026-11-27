package com.hackerdjakodjo.mypdfwriter.data

/** Three explicit vertices keep triangle orientation through later transforms. */
fun triangleVertices(points: List<NormalizedPoint>): List<NormalizedPoint> {
    if (points.size >= 3) return points.take(3)
    if (points.isEmpty()) return emptyList()
    val start = points.first()
    val end = points.last()
    val left = minOf(start.x, end.x)
    val right = maxOf(start.x, end.x)
    val top = minOf(start.y, end.y)
    val bottom = maxOf(start.y, end.y)
    return listOf(
        NormalizedPoint((left + right) * 0.5f, top, start.pressure, start.timeMs),
        NormalizedPoint(left, bottom, end.pressure, end.timeMs),
        NormalizedPoint(right, bottom, end.pressure, end.timeMs),
    )
}
