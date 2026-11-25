package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.RecentFileEntity
import com.example.data.repository.RecentFileRepository
import com.example.model.PdfTool
import com.example.pdf.PdfEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecentFilesViewModel(
  private val repository: RecentFileRepository
) : ViewModel() {

  val recentFiles: StateFlow<List<RecentFileEntity>> = repository.recentFiles
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  fun recordRecentDocument(
    fileName: String,
    fileUriString: String,
    mimeType: String = "application/pdf",
    sizeBytes: Long,
    pageCount: Int,
    tool: PdfTool,
    summaryNote: String
  ) {
    viewModelScope.launch {
      repository.recordRecentFile(
        fileName = fileName,
        fileUriString = fileUriString,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        lastToolId = tool.id,
        lastToolName = tool.name,
        summaryNote = summaryNote
      )
    }
  }

  fun deleteRecentFile(id: Long) {
    viewModelScope.launch {
      repository.deleteById(id)
    }
  }

  fun clearAllRecentFiles() {
    viewModelScope.launch {
      repository.clearAll()
    }
  }

  fun createQuickSampleInRecents(
    context: Context,
    tool: PdfTool,
    onReady: ((PdfTool, Uri) -> Unit)? = null
  ) {
    viewModelScope.launch {
      PdfEngine.createSamplePdf(context, title = "CubedPDF Workspace Draft", pages = 3)
        .onSuccess { uri ->
          val meta = PdfEngine.inspectPdf(context, uri).getOrNull()
          val fileName = meta?.displayName ?: "CubedPDF_Draft.pdf"
          val sizeBytes = meta?.sizeBytes ?: PdfEngine.querySizeBytes(context, uri)
          val pageCount = meta?.pageCount ?: 3
          repository.recordRecentFile(
            fileName = fileName,
            fileUriString = uri.toString(),
            mimeType = "application/pdf",
            sizeBytes = sizeBytes,
            pageCount = pageCount,
            lastToolId = tool.id,
            lastToolName = tool.name,
            summaryNote = "Ready to resume in ${tool.name}"
          )
          onReady?.invoke(tool, uri)
        }
    }
  }

  class Factory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      val dao = AppDatabase.getInstance(context.applicationContext).recentFileDao()
      val repository = RecentFileRepository(dao)
      return RecentFilesViewModel(repository) as T
    }
  }
}
