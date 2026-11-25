package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.remote.openExternalUrl
import com.example.legal.AppLegalConfig

private val LegalBtnTextDark = Color(0xFF0F172A)
private val LegalBtnBorder = Color(0xFFCBD5E1)
private val LegalAccent = Color(0xFF4F46E5)
private val LegalDestructive = Color(0xFFDC2626)

@Composable
fun PrivacyPolicyDialog(
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color.White,
    titleContentColor = LegalBtnTextDark,
    textContentColor = Color(0xFF334155),
    modifier = Modifier.testTag("privacy_policy_dialog"),
    icon = {
      Icon(
        imageVector = Icons.Rounded.PrivacyTip,
        contentDescription = null,
        tint = LegalAccent,
        modifier = Modifier.size(28.dp)
      )
    },
    title = {
      Text(
        text = "CubedPDF Privacy Policy",
        fontWeight = FontWeight.Bold,
        color = LegalBtnTextDark
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 320.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = AppLegalConfig.PRIVACY_SUMMARY_BULLETS,
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF334155)
        )
        Text(
          text = "Public Policy URL: ${AppLegalConfig.PRIVACY_POLICY_URL}",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = LegalAccent
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (AppLegalConfig.isValidHttpsUrl(AppLegalConfig.PRIVACY_POLICY_URL)) {
            openExternalUrl(context, AppLegalConfig.PRIVACY_POLICY_URL)
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = Color.White,
          contentColor = LegalAccent
        ),
        border = BorderStroke(1.5.dp, LegalAccent.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("open_privacy_policy_web_button")
      ) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Open Web Policy", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
      }
    },
    dismissButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(
          containerColor = Color.White,
          contentColor = LegalBtnTextDark
        ),
        border = BorderStroke(1.dp, LegalBtnBorder),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("close_privacy_policy_dialog")
      ) {
        Text("Close", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
      }
    }
  )
}

@Composable
fun WebAccountDeletionDialog(
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color.White,
    titleContentColor = LegalBtnTextDark,
    textContentColor = Color(0xFF334155),
    modifier = Modifier.testTag("web_account_deletion_dialog"),
    icon = {
      Icon(
        imageVector = Icons.Rounded.DeleteForever,
        contentDescription = null,
        tint = LegalDestructive,
        modifier = Modifier.size(28.dp)
      )
    },
    title = {
      Text(
        text = "Account & Data Deletion",
        fontWeight = FontWeight.Bold,
        color = LegalBtnTextDark
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 320.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = AppLegalConfig.WEB_DELETION_SUMMARY,
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF334155)
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = "Web Deletion URL: ${AppLegalConfig.ACCOUNT_DELETION_WEB_URL}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = LegalDestructive
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (AppLegalConfig.isValidHttpsUrl(AppLegalConfig.ACCOUNT_DELETION_WEB_URL)) {
            openExternalUrl(context, AppLegalConfig.ACCOUNT_DELETION_WEB_URL)
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = Color.White,
          contentColor = LegalDestructive
        ),
        border = BorderStroke(1.5.dp, LegalDestructive.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("open_web_deletion_url_button")
      ) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Open Web Page", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
      }
    },
    dismissButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(
          containerColor = Color.White,
          contentColor = LegalBtnTextDark
        ),
        border = BorderStroke(1.dp, LegalBtnBorder),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("close_web_deletion_dialog")
      ) {
        Text("Close", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
      }
    }
  )
}
