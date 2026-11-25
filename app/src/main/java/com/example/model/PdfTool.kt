package com.example.model

import androidx.compose.ui.graphics.Color

enum class ToolCategory(val label: String) {
  ALL("All Tools"),
  ORGANIZE("Organize"),
  CONVERT("Convert"),
  EDIT_SIGN("Edit & Sign"),
  SECURITY("Security"),
  OPTIMIZE("Optimize")
}

enum class PdfToolIconType {
  MERGE,
  SPLIT,
  COMPRESS,
  EDIT,
  SIGN,
  CONVERTER,
  IMAGES_TO_PDF,
  PDF_TO_IMAGES,
  EXTRACT_IMAGES,
  PROTECT,
  UNLOCK,
  ROTATE,
  REMOVE_PAGES,
  EXTRACT_PAGES,
  REARRANGE,
  WEBPAGE_TO_PDF,
  OCR,
  WATERMARK,
  PAGE_NUMBERS,
  OVERLAY,
  COMPARE,
  WEB_OPTIMIZE,
  REDACT,
  CREATE
}

data class ToolPalette3D(
  val highlight: Color,
  val base: Color,
  val depth: Color,
  val glow: Color
)

data class PdfTool(
  val id: String,
  val name: String,
  val shortTag: String,
  val description: String,
  val category: ToolCategory,
  val iconType: PdfToolIconType,
  val palette: ToolPalette3D,
  val isFeatured: Boolean = false
)

private val Crimson3D = ToolPalette3D(
  highlight = Color(0xFFFF6B81),
  base = Color(0xFFE11D48),
  depth = Color(0xFF881337),
  glow = Color(0xFFFF3B5C)
)

private val Indigo3D = ToolPalette3D(
  highlight = Color(0xFF818CF8),
  base = Color(0xFF4F46E5),
  depth = Color(0xFF312E81),
  glow = Color(0xFF6366F1)
)

private val Cyan3D = ToolPalette3D(
  highlight = Color(0xFF38BDF8),
  base = Color(0xFF0284C7),
  depth = Color(0xFF0C4A6E),
  glow = Color(0xFF0EA5E9)
)

private val Emerald3D = ToolPalette3D(
  highlight = Color(0xFF34D399),
  base = Color(0xFF059669),
  depth = Color(0xFF064E3B),
  glow = Color(0xFF10B981)
)

private val Violet3D = ToolPalette3D(
  highlight = Color(0xFFC084FC),
  base = Color(0xFF9333EA),
  depth = Color(0xFF581C87),
  glow = Color(0xFFA855F7)
)

private val Amber3D = ToolPalette3D(
  highlight = Color(0xFFFBBF24),
  base = Color(0xFFD97706),
  depth = Color(0xFF78350F),
  glow = Color(0xFFF59E0B)
)

private val Teal3D = ToolPalette3D(
  highlight = Color(0xFF2DD4BF),
  base = Color(0xFF0D9488),
  depth = Color(0xFF134E4A),
  glow = Color(0xFF14B8A6)
)

private val RoseCoral3D = ToolPalette3D(
  highlight = Color(0xFFFB7185),
  base = Color(0xFFF43F5E),
  depth = Color(0xFF9F1239),
  glow = Color(0xFFFB7185)
)

val pdfToolsList = listOf(
  PdfTool(
    id = "merge",
    name = "Merge PDF",
    shortTag = "COMBINE",
    description = "Combine multiple PDF documents into a single unified file in your preferred sequence.",
    category = ToolCategory.ORGANIZE,
    iconType = PdfToolIconType.MERGE,
    palette = Crimson3D,
    isFeatured = true
  ),
  PdfTool(
    id = "split",
    name = "Split PDF",
    shortTag = "DIVIDE",
    description = "Divide a large PDF into separate documents or extract custom page ranges instantly.",
    category = ToolCategory.ORGANIZE,
    iconType = PdfToolIconType.SPLIT,
    palette = RoseCoral3D,
    isFeatured = true
  ),
  PdfTool(
    id = "compress",
    name = "Compress PDF",
    shortTag = "SHRINK",
    description = "Reduce PDF file size dramatically while preserving crisp typography and image clarity.",
    category = ToolCategory.OPTIMIZE,
    iconType = PdfToolIconType.COMPRESS,
    palette = Emerald3D,
    isFeatured = true
  ),
  PdfTool(
    id = "edit",
    name = "Edit PDF",
    shortTag = "MODIFY",
    description = "Edit text, shapes, highlights, and annotations directly inside your document canvas.",
    category = ToolCategory.EDIT_SIGN,
    iconType = PdfToolIconType.EDIT,
    palette = Indigo3D,
    isFeatured = true
  ),
  PdfTool(
    id = "sign",
    name = "Sign PDF",
    shortTag = "E-SIGN",
    description = "Draw, type, or upload your electronic signature and place it securely on any page.",
    category = ToolCategory.EDIT_SIGN,
    iconType = PdfToolIconType.SIGN,
    palette = Violet3D
  ),
  PdfTool(
    id = "converter",
    name = "PDF Converter",
    shortTag = "FORMATS",
    description = "Convert PDFs seamlessly between Word, Excel, PowerPoint, text, and archive formats.",
    category = ToolCategory.CONVERT,
    iconType = PdfToolIconType.CONVERTER,
    palette = Cyan3D
  ),
  PdfTool(
    id = "images_to_pdf",
    name = "Images to PDF",
    shortTag = "IMG → PDF",
    description = "Turn JPG, PNG, and WebP images into a clean, print-ready multi-page PDF document.",
    category = ToolCategory.CONVERT,
    iconType = PdfToolIconType.IMAGES_TO_PDF,
    palette = Amber3D
  ),
  PdfTool(
    id = "pdf_to_images",
    name = "PDF to Images",
    shortTag = "PDF → IMG",
    description = "Export individual or all PDF pages into sharp high-resolution PNG or JPEG images.",
    category = ToolCategory.CONVERT,
    iconType = PdfToolIconType.PDF_TO_IMAGES,
    palette = Teal3D
  ),
  PdfTool(
    id = "extract_images",
    name = "Extract Images",
    shortTag = "ASSETS",
    description = "Pull original embedded graphics and photos directly out of any PDF container.",
    category = ToolCategory.CONVERT,
    iconType = PdfToolIconType.EXTRACT_IMAGES,
    palette = Indigo3D
  ),
  PdfTool(
    id = "protect",
    name = "Protect PDF",
    shortTag = "ENCRYPT",
    description = "Lock sensitive files with AES encryption, open passwords, and permission controls.",
    category = ToolCategory.SECURITY,
    iconType = PdfToolIconType.PROTECT,
    palette = Crimson3D
  ),
  PdfTool(
    id = "unlock",
    name = "Unlock PDF",
    shortTag = "ACCESS",
    description = "Remove permitted owner restrictions or password locks from authorized PDF files.",
    category = ToolCategory.SECURITY,
    iconType = PdfToolIconType.UNLOCK,
    palette = Emerald3D
  ),
  PdfTool(
    id = "rotate",
    name = "Rotate Pages",
    shortTag = "ORIENT",
    description = "Fix sideways or upside-down scans by rotating single or selected PDF pages.",
    category = ToolCategory.ORGANIZE,
    iconType = PdfToolIconType.ROTATE,
    palette = Cyan3D
  ),
  PdfTool(
    id = "remove",
    name = "Remove Pages",
    shortTag = "DELETE",
    description = "Quickly trim blank, duplicate, or unwanted pages out of your PDF document.",
    category = ToolCategory.ORGANIZE,
    iconType = PdfToolIconType.REMOVE_PAGES,
    palette = RoseCoral3D
  ),
  PdfTool(
    id = "extract_pages",
    name = "Extract Pages",
    shortTag = "EXPORT",
    description = "Save selected pages from an existing document into a brand-new standalone PDF.",
    category = ToolCategory.ORGANIZE,
    iconType = PdfToolIconType.EXTRACT_PAGES,
    palette = Violet3D
  ),
  PdfTool(
    id = "rearrange",
    name = "Rearrange Pages",
    shortTag = "REORDER",
    description = "Drag and reorder page thumbnails into the exact sequence your document needs.",
    category = ToolCategory.ORGANIZE,
    iconType = PdfToolIconType.REARRANGE,
    palette = Amber3D
  ),
  PdfTool(
    id = "webpage",
    name = "Webpage to PDF",
    shortTag = "HTML → PDF",
    description = "Capture articles and live webpages into clean, archivable PDF documents.",
    category = ToolCategory.CONVERT,
    iconType = PdfToolIconType.WEBPAGE_TO_PDF,
    palette = Cyan3D
  ),
  PdfTool(
    id = "ocr",
    name = "Extract Text (OCR)",
    shortTag = "ML KIT OCR",
    description = "Convert scanned PDF documents or images into searchable, editable text using on-device ML Kit.",
    category = ToolCategory.OPTIMIZE,
    iconType = PdfToolIconType.OCR,
    palette = Teal3D
  ),
  PdfTool(
    id = "watermark",
    name = "Add Watermark",
    shortTag = "STAMP",
    description = "Overlay custom text or brand watermarks with adjustable opacity and angle.",
    category = ToolCategory.EDIT_SIGN,
    iconType = PdfToolIconType.WATERMARK,
    palette = Indigo3D
  ),
  PdfTool(
    id = "page_numbers",
    name = "Add Page Numbers",
    shortTag = "INDEX",
    description = "Automatically stamp formatted page numbers into headers or footers across pages.",
    category = ToolCategory.EDIT_SIGN,
    iconType = PdfToolIconType.PAGE_NUMBERS,
    palette = Amber3D
  ),
  PdfTool(
    id = "overlay",
    name = "PDF Overlay",
    shortTag = "LAYER",
    description = "Blend letterheads, templates, or another PDF directly over existing document pages.",
    category = ToolCategory.EDIT_SIGN,
    iconType = PdfToolIconType.OVERLAY,
    palette = Violet3D
  ),
  PdfTool(
    id = "compare",
    name = "Compare PDFs",
    shortTag = "DIFF",
    description = "Compare two PDF revisions side-by-side and highlight textual or layout changes.",
    category = ToolCategory.OPTIMIZE,
    iconType = PdfToolIconType.COMPARE,
    palette = Cyan3D
  ),
  PdfTool(
    id = "web_optimize",
    name = "Web Optimize PDF",
    shortTag = "FAST VIEW",
    description = "Linearize PDFs for ultra-fast streaming, byte-range loading, and web delivery.",
    category = ToolCategory.OPTIMIZE,
    iconType = PdfToolIconType.WEB_OPTIMIZE,
    palette = Emerald3D
  ),
  PdfTool(
    id = "redact",
    name = "Redact PDF",
    shortTag = "PRIVACY",
    description = "Permanently scrub and blackout confidential text or numbers before sharing.",
    category = ToolCategory.SECURITY,
    iconType = PdfToolIconType.REDACT,
    palette = Crimson3D
  ),
  PdfTool(
    id = "create",
    name = "Create PDF",
    shortTag = "BUILD",
    description = "Compose new structured PDF documents from scratch using built-in layout tools.",
    category = ToolCategory.EDIT_SIGN,
    iconType = PdfToolIconType.CREATE,
    palette = Teal3D,
    isFeatured = true
  )
)
