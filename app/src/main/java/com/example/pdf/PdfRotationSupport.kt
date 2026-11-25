package com.example.pdf

import kotlin.math.max
import kotlin.math.min

/**
 * Supported orthogonal PDF page rotation angles (0, 90, 180, and 270 degrees clockwise).
 */
enum class RotationAngle(
  val degrees: Int,
  val label: String,
  val shortLabel: String
) {
  NONE(0, "0° Original", "0°"),
  CW_90(90, "90° Clockwise", "90°"),
  HALF_180(180, "180° Flip", "180°"),
  CCW_90(270, "270° (90° CCW)", "270°");

  companion object {
    val selectableNonZero: List<RotationAngle> = listOf(CW_90, HALF_180, CCW_90)

    fun fromDegrees(degrees: Int): RotationAngle {
      val norm = PdfRotationSupport.normalizeDegrees(degrees)
      return entries.firstOrNull { it.degrees == norm } ?: NONE
    }
  }
}

/**
 * Pure, deterministic helpers for per-page PDF rotation math, 1-based page range parsing,
 * output page dimension swapping, and human-readable rotation summaries.
 */
object PdfRotationSupport {

  /**
   * Normalizes any integer degree input to the nearest valid orthogonal PDF rotation:
   * 0, 90, 180, or 270 degrees clockwise.
   */
  fun normalizeDegrees(degrees: Int): Int {
    val mod = ((degrees % 360) + 360) % 360
    return when {
      mod in 45..134 -> 90
      mod in 135..224 -> 180
      mod in 225..314 -> 270
      else -> 0
    }
  }

  /**
   * Advances a page's current rotation by [deltaDegrees] (e.g. +90 or -90) modulo 360.
   */
  fun nextRotation(currentDegrees: Int, deltaDegrees: Int = 90): Int {
    return normalizeDegrees(normalizeDegrees(currentDegrees) + deltaDegrees)
  }

  /**
   * Parses a user-entered 1-based page expression (such as `"1, 3-5, 8"`) into a sorted set of
   * valid 0-based page indices within `[0, totalPages - 1]`.
   */
  fun parseSpecificPages(expression: String, totalPages: Int): Set<Int> {
    if (totalPages <= 0 || expression.isBlank()) return emptySet()
    val result = linkedSetOf<Int>()
    expression.split(",", ";", " ")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .forEach { token ->
        if (token.contains("-")) {
          val parts = token.split("-").map { it.trim().toIntOrNull() }
          if (parts.size == 2 && parts[0] != null && parts[1] != null) {
            val start = min(parts[0]!!, parts[1]!!).coerceIn(1, totalPages)
            val end = max(parts[0]!!, parts[1]!!).coerceIn(1, totalPages)
            for (p in start..end) {
              result.add(p - 1)
            }
          }
        } else {
          val single = token.toIntOrNull()
          if (single != null && single in 1..totalPages) {
            result.add(single - 1)
          }
        }
      }
    return result.toSortedSet()
  }

  /**
   * Formats 0-based page indices into a compact 1-based string (e.g. `{0, 1, 2, 4}` -> `"1-3, 5"`).
   */
  fun formatPageSelection(zeroBasedPages: Set<Int>): String {
    if (zeroBasedPages.isEmpty()) return ""
    val sorted = zeroBasedPages.filter { it >= 0 }.sorted()
    if (sorted.isEmpty()) return ""
    val parts = mutableListOf<String>()
    var rangeStart = sorted.first()
    var prev = rangeStart
    for (i in 1 until sorted.size) {
      val cur = sorted[i]
      if (cur == prev + 1) {
        prev = cur
      } else {
        parts.add(if (rangeStart == prev) "${rangeStart + 1}" else "${rangeStart + 1}-${prev + 1}")
        rangeStart = cur
        prev = cur
      }
    }
    parts.add(if (rangeStart == prev) "${rangeStart + 1}" else "${rangeStart + 1}-${prev + 1}")
    return parts.joinToString(", ")
  }

  /**
   * Builds the effective map of `0-based pageIndex -> normalized non-zero degrees (90, 180, or 270)`.
   *
   * If [perPageRotations] contains at least one valid non-zero entry, those exact per-page angles
   * are used. Otherwise, [fallbackDegrees] is applied to [fallbackPages] (or every page when
   * [fallbackPages] is empty).
   */
  fun buildPageRotationsMap(
    totalPages: Int,
    perPageRotations: Map<Int, Int>,
    fallbackPages: Set<Int> = emptySet(),
    fallbackDegrees: Int = 90
  ): Map<Int, Int> {
    if (totalPages <= 0) return emptyMap()

    val cleanedExplicit = perPageRotations.entries
      .mapNotNull { (pageIdx, deg) ->
        val norm = normalizeDegrees(deg)
        if (pageIdx in 0 until totalPages && norm != 0) pageIdx to norm else null
      }
      .toMap()

    if (cleanedExplicit.isNotEmpty()) {
      return cleanedExplicit.toSortedMap()
    }

    val normFallback = normalizeDegrees(fallbackDegrees)
    if (normFallback == 0) return emptyMap()

    val targets = if (fallbackPages.isEmpty()) {
      (0 until totalPages).toList()
    } else {
      fallbackPages.filter { it in 0 until totalPages }.sorted()
    }
    return targets.associateWith { normFallback }
  }

  /**
   * Computes output PDF page dimensions after rotating a page by [degrees].
   * Swaps width and height for 90° and 270°; preserves dimensions for 0° and 180°.
   */
  fun computeRotatedDimensions(srcWidth: Int, srcHeight: Int, degrees: Int): Pair<Int, Int> {
    val norm = normalizeDegrees(degrees)
    val safeW = max(1, srcWidth)
    val safeH = max(1, srcHeight)
    return if (norm == 90 || norm == 270) {
      safeH to safeW
    } else {
      safeW to safeH
    }
  }

  /**
   * Generates a clear human-readable summary of applied page rotations.
   */
  fun summarizeRotations(rotations: Map<Int, Int>, totalPages: Int): String {
    val active = rotations.entries
      .mapNotNull { (idx, deg) ->
        val norm = normalizeDegrees(deg)
        if (idx in 0 until totalPages && norm != 0) idx to norm else null
      }
      .sortedBy { it.first }

    if (active.isEmpty()) return "No pages rotated."

    val uniqueAngles = active.map { it.second }.toSet()
    if (active.size == totalPages && uniqueAngles.size == 1) {
      return "Rotated all $totalPages page(s) by ${uniqueAngles.first()}°."
    }
    if (uniqueAngles.size == 1) {
      val formattedPages = formatPageSelection(active.map { it.first }.toSet())
      return "Rotated page(s) $formattedPages by ${uniqueAngles.first()}°."
    }
    val breakdown = active.take(4).joinToString(", ") { (idx, deg) -> "P${idx + 1} (${deg}°)" }
    val suffix = if (active.size > 4) " +${active.size - 4} more" else ""
    return "Rotated ${active.size} of $totalPages page(s): $breakdown$suffix."
  }
}
