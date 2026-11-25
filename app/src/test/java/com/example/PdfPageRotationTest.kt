package com.example

import com.example.pdf.PdfRotationSupport
import com.example.pdf.RotationAngle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests verifying per-page PDF rotation math (90°, 180°, 270°), custom page-range
 * parsing, dimension swapping, batch fallback rules, summaries, and workspace integration.
 */
class PdfPageRotationTest {

  private fun projectRoot(): File {
    val cwd = File(System.getProperty("user.dir") ?: ".")
    return if (File(cwd, "settings.gradle.kts").exists()) cwd else cwd.parentFile ?: cwd
  }

  @Test
  fun `normalizeDegrees and nextRotation handle 0 90 180 270 negative and wraparound values`() {
    assertEquals(0, PdfRotationSupport.normalizeDegrees(0))
    assertEquals(90, PdfRotationSupport.normalizeDegrees(90))
    assertEquals(180, PdfRotationSupport.normalizeDegrees(180))
    assertEquals(270, PdfRotationSupport.normalizeDegrees(270))
    assertEquals(0, PdfRotationSupport.normalizeDegrees(360))
    assertEquals(270, PdfRotationSupport.normalizeDegrees(-90))
    assertEquals(180, PdfRotationSupport.normalizeDegrees(-180))
    assertEquals(90, PdfRotationSupport.normalizeDegrees(450))

    assertEquals(90, PdfRotationSupport.nextRotation(0, 90))
    assertEquals(180, PdfRotationSupport.nextRotation(90, 90))
    assertEquals(270, PdfRotationSupport.nextRotation(180, 90))
    assertEquals(0, PdfRotationSupport.nextRotation(270, 90))
    assertEquals(270, PdfRotationSupport.nextRotation(0, -90))
    assertEquals(RotationAngle.CCW_90, RotationAngle.fromDegrees(270))
  }

  @Test
  fun `parseSpecificPages and formatPageSelection handle ranges singles reversed bounds and invalid tokens`() {
    assertEquals(setOf(0, 2, 3, 4), PdfRotationSupport.parseSpecificPages("1, 3-5", 6))
    assertEquals(setOf(1, 2, 3), PdfRotationSupport.parseSpecificPages("4-2", 6))
    assertEquals(setOf(0, 4, 5), PdfRotationSupport.parseSpecificPages("1, 5-99, abc, -2", 6))
    assertEquals(emptySet<Int>(), PdfRotationSupport.parseSpecificPages("   ", 6))
    assertEquals(emptySet<Int>(), PdfRotationSupport.parseSpecificPages("10, 12", 5))

    assertEquals("1, 3-5", PdfRotationSupport.formatPageSelection(setOf(0, 2, 3, 4)))
    assertEquals("2", PdfRotationSupport.formatPageSelection(setOf(1)))
    assertEquals("", PdfRotationSupport.formatPageSelection(emptySet()))
  }

  @Test
  fun `buildPageRotationsMap supports independent per-page 90 180 270 angles and batch fallback`() {
    // Independent per-page angles take priority, ignoring 0° no-ops and out-of-range pages
    val explicit = PdfRotationSupport.buildPageRotationsMap(
      totalPages = 5,
      perPageRotations = mapOf(0 to 90, 1 to 0, 2 to 180, 4 to 270, 9 to 90),
      fallbackPages = emptySet(),
      fallbackDegrees = 90
    )
    assertEquals(mapOf(0 to 90, 2 to 180, 4 to 270), explicit)

    // When no per-page rotations are set, applies fallback angle to specific fallbackPages
    val specificFallback = PdfRotationSupport.buildPageRotationsMap(
      totalPages = 4,
      perPageRotations = emptyMap(),
      fallbackPages = setOf(1, 3),
      fallbackDegrees = 270
    )
    assertEquals(mapOf(1 to 270, 3 to 270), specificFallback)

    // When both per-page and fallbackPages are empty, rotates all pages by fallback angle
    val allFallback = PdfRotationSupport.buildPageRotationsMap(
      totalPages = 3,
      perPageRotations = emptyMap(),
      fallbackPages = emptySet(),
      fallbackDegrees = 180
    )
    assertEquals(mapOf(0 to 180, 1 to 180, 2 to 180), allFallback)
  }

  @Test
  fun `computeRotatedDimensions swaps width and height for 90 and 270 and preserves for 0 and 180`() {
    assertEquals(595 to 842, PdfRotationSupport.computeRotatedDimensions(595, 842, 0))
    assertEquals(842 to 595, PdfRotationSupport.computeRotatedDimensions(595, 842, 90))
    assertEquals(595 to 842, PdfRotationSupport.computeRotatedDimensions(595, 842, 180))
    assertEquals(842 to 595, PdfRotationSupport.computeRotatedDimensions(595, 842, 270))
    assertEquals(1 to 1, PdfRotationSupport.computeRotatedDimensions(0, -5, 90))
  }

  @Test
  fun `summarizeRotations formats all-page uniform and mixed per-page summaries clearly`() {
    assertEquals(
      "Rotated all 3 page(s) by 90°.",
      PdfRotationSupport.summarizeRotations(mapOf(0 to 90, 1 to 90, 2 to 90), 3)
    )
    assertEquals(
      "Rotated page(s) 1, 3 by 180°.",
      PdfRotationSupport.summarizeRotations(mapOf(0 to 180, 2 to 180), 4)
    )
    val mixed = PdfRotationSupport.summarizeRotations(mapOf(0 to 90, 2 to 180, 3 to 270), 5)
    assertTrue(mixed.contains("3 of 5"))
    assertTrue(mixed.contains("P1 (90°)"))
    assertTrue(mixed.contains("P3 (180°)"))
    assertTrue(mixed.contains("P4 (270°)"))
  }

  @Test
  fun `pdfEngine and workspace wire per-page 90 180 270 rotation studio cleanly`() {
    val root = projectRoot()
    val engineSrc = File(root, "app/src/main/java/com/example/pdf/PdfEngine.kt").readText(Charsets.UTF_8)
    val studioSrc = File(root, "app/src/main/java/com/example/ui/components/PageRotationStudioCard.kt").readText(Charsets.UTF_8)
    val workspaceSrc = File(root, "app/src/main/java/com/example/ui/screens/ToolWorkspaceScreen.kt").readText(Charsets.UTF_8)

    assertTrue(engineSrc.contains("suspend fun rotateSpecificPages("))
    assertTrue(studioSrc.contains("rotate_angle_90"))
    assertTrue(studioSrc.contains("rotate_angle_180"))
    assertTrue(studioSrc.contains("rotate_angle_270"))
    assertTrue(studioSrc.contains("rotate_page_range_input"))
    assertTrue(workspaceSrc.contains("PageRotationStudioCard("))
    assertTrue(workspaceSrc.contains("PdfEngine.rotateSpecificPages("))
  }
}
