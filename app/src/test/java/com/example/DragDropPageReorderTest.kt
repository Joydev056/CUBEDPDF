package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.RecentFileRepository
import com.example.ui.components.DragDropPageReorderGrid
import com.example.ui.components.findDropTargetIndex
import com.example.ui.components.reorderPageList
import com.example.ui.components.resetPageOrder
import com.example.ui.components.reversePageOrder
import com.example.ui.screens.PdfToolsDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.RecentFilesViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class DragDropPageReorderTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `reorder helper functions and 2D grid drop target hit-testing handle all edge cases`() {
    val initial = listOf(0, 1, 2, 3)

    assertEquals(listOf(1, 2, 0, 3), reorderPageList(initial, 0, 2))
    assertEquals(listOf(3, 0, 1, 2), reorderPageList(initial, 3, 0))
    assertEquals(initial, reorderPageList(initial, 1, 1))
    assertEquals(initial, reorderPageList(initial, -1, 2))
    assertEquals(initial, reorderPageList(initial, 0, 99))
    assertEquals(emptyList<Int>(), reorderPageList(emptyList(), 0, 1))

    assertEquals(listOf(3, 2, 1, 0), reversePageOrder(initial))
    assertEquals(listOf(0, 1, 2, 3), resetPageOrder(4))
    assertEquals(emptyList<Int>(), resetPageOrder(0))

    // 2x2 grid hit-testing
    val slots = mapOf(
      0 to Rect(0f, 0f, 100f, 140f),
      1 to Rect(110f, 0f, 210f, 140f),
      2 to Rect(0f, 150f, 100f, 290f),
      3 to Rect(110f, 150f, 210f, 290f)
    )
    assertEquals(1, findDropTargetIndex(Offset(150f, 70f), slots, currentIndex = 0))
    assertEquals(3, findDropTargetIndex(Offset(160f, 220f), slots, currentIndex = 0))
    assertEquals(2, findDropTargetIndex(Offset(40f, 200f), slots, currentIndex = 1))
    // Far outside grid keeps currentIndex unchanged
    assertEquals(0, findDropTargetIndex(Offset(900f, 900f), slots, currentIndex = 0))
  }

  @Test
  fun `dragDropPageReorderGrid displays empty state when no PDF pages are loaded`() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        DragDropPageReorderGrid(
          thumbnails = emptyList(),
          pageOrder = emptyList(),
          totalPageCount = 0,
          onOrderChange = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drag_drop_page_reorder_container").assertIsDisplayed()
    composeTestRule.onNodeWithTag("reorder_empty_state").assertIsDisplayed()
    composeTestRule.onNodeWithText("No PDF Pages Loaded").assertIsDisplayed()
  }

  @Test
  fun `dragDropPageReorderGrid supports touch drag gesture reverse reset and accessible shift buttons`() {
    val thumbs = List(4) { idx ->
      Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888).apply {
        eraseColor(
          when (idx) {
            0 -> Color.rgb(225, 29, 72)
            1 -> Color.rgb(79, 70, 229)
            2 -> Color.rgb(13, 148, 136)
            else -> Color.rgb(217, 119, 6)
          }
        )
      }
    }
    var currentOrder by mutableStateOf(listOf(0, 1, 2, 3))

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        DragDropPageReorderGrid(
          thumbnails = thumbs,
          pageOrder = currentOrder,
          totalPageCount = 4,
          onOrderChange = { currentOrder = it }
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drag_drop_pages_grid").assertIsDisplayed()
    composeTestRule.onNodeWithTag("reorder_reset_button").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("reorder_sequence_summary").assertIsDisplayed()

    // 1. Perform real drag gesture on Page 1's grip handle to the right (into Slot 2)
    composeTestRule.onNodeWithTag("reorder_drag_handle_0").performTouchInput {
      swipeRight(startX = center.x, endX = center.x + 520f, durationMillis = 300)
    }
    composeTestRule.waitForIdle()
    assertTrue("Expected drag gesture to reorder Page 1 out of index 0, order=$currentOrder", currentOrder != listOf(0, 1, 2, 3))
    composeTestRule.onNodeWithTag("reorder_modified_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("reorder_reset_button").assertIsEnabled()

    // 2. Tap Reset Order -> restores [0, 1, 2, 3]
    composeTestRule.onNodeWithTag("reorder_reset_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(listOf(0, 1, 2, 3), currentOrder)
    composeTestRule.onNodeWithTag("reorder_reset_button").assertIsNotEnabled()

    // 3. Tap Reverse Order -> [3, 2, 1, 0]
    composeTestRule.onNodeWithTag("reorder_reverse_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(listOf(3, 2, 1, 0), currentOrder)

    // 4. Accessible move-right button on Page 4 (currently at index 0) -> [2, 3, 1, 0]
    composeTestRule.onNodeWithTag("reorder_move_right_3").performClick()
    composeTestRule.waitForIdle()
    assertEquals(listOf(2, 3, 1, 0), currentOrder)

    // 5. Accessible move-left button on Page 4 (now at index 1) -> [3, 2, 1, 0]
    composeTestRule.onNodeWithTag("reorder_move_left_3").performClick()
    composeTestRule.waitForIdle()
    assertEquals(listOf(3, 2, 1, 0), currentOrder)

    thumbs.forEach { it.recycle() }
  }

  @Test
  fun `rearrange pages workspace opens from dashboard with drag and drop component`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val viewModel = RecentFilesViewModel(RecentFileRepository(db.recentFileDao()))
      composeTestRule.setContent {
        MyApplicationTheme(darkTheme = true) {
          PdfToolsDashboard(
            isDarkTheme = true,
            onToggleTheme = {},
            recentFilesViewModel = viewModel
          )
        }
      }

      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("search_tools_input").performScrollTo().performTextInput("Rearrange")
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("tool_card_rearrange").performScrollTo().performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("drag_drop_page_reorder_container").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithText("Drag & Drop Pages").assertIsDisplayed()
    } finally {
      db.close()
    }
  }
}
