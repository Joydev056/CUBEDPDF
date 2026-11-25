package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.data.local.RecentFileEntity
import com.example.data.remote.AccountDeletionOutcome
import com.example.data.remote.CloudRecentDoc
import com.example.data.remote.UserProfile
import com.example.data.remote.WorkspaceRole
import com.example.data.remote.openExternalUrl
import com.example.pdf.PdfEngine
import com.example.ui.components.GlassSurface
import com.example.ui.components.PrivacyPolicyDialog
import com.example.ui.components.WebAccountDeletionDialog
import com.example.ui.theme.LocalGlassColors
import com.example.ui.viewmodel.CloudUiState

private const val GOOGLE_ACCOUNT_SECURITY_URL = "https://myaccount.google.com/security"
private val PopupButtonTextDark = Color(0xFF0F172A)
private val PopupSubtitleMuted = Color(0xFF475569)
private val PopupSoftSurface = Color(0xFFF8FAFC)
private val PopupButtonBorderLight = Color(0xFFCBD5E1)
private val PopupDividerLight = Color(0xFFE2E8F0)
private val PopupPrimaryAccent = Color(0xFF4F46E5)
private val PopupDestructiveText = Color(0xFFDC2626)
private val PopupDestructiveBorder = Color(0xFFFCA5A5)

@Composable
fun AccountProfileDialog(
  userDisplayName: String,
  userEmail: String?,
  isEmailVerified: Boolean = false,
  profileState: CloudUiState<UserProfile?>,
  cloudDocsState: CloudUiState<List<CloudRecentDoc>>,
  localRecentFiles: List<RecentFileEntity>,
  isBusy: Boolean,
  statusMessage: String?,
  onDismiss: () -> Unit,
  onSaveProfile: (firstName: String, lastName: String, role: WorkspaceRole) -> Unit,
  onSendVerificationEmail: () -> Unit = {},
  onRefreshVerification: () -> Unit = {},
  onSyncLocalFiles: () -> Unit,
  onDeleteCloudDoc: (String) -> Unit,
  onSwitchAccount: () -> Unit,
  onSignOut: () -> Unit,
  onDeleteCloudData: () -> Unit,
  reauthRequiredForDelete: AccountDeletionOutcome.RequiresReauth? = null,
  onDismissReauthPrompt: () -> Unit = {},
  onReauthWithGoogleAndDelete: () -> Unit = {},
  onReauthWithPasswordAndDelete: (String) -> Unit = {}
) {
  val context = LocalContext.current
  val glass = LocalGlassColors.current
  val profile = (profileState as? CloudUiState.Success)?.data
  val cloudDocs = (cloudDocsState as? CloudUiState.Success)?.data.orEmpty()

  val resolvedInitialName = profile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName
  var editedFirstName by remember(profile?.firstName, resolvedInitialName) {
    mutableStateOf(
      profile?.firstName?.takeIf { it.isNotBlank() }
        ?: resolvedInitialName.trim().substringBefore(" ")
    )
  }
  var editedLastName by remember(profile?.lastName, resolvedInitialName) {
    mutableStateOf(
      profile?.lastName?.takeIf { it.isNotBlank() }
        ?: resolvedInitialName.trim().let { full ->
          if (full.contains(" ")) full.substringAfter(" ").trim() else ""
        }
    )
  }
  var editedRole by remember(profile?.workspaceRole) {
    mutableStateOf(WorkspaceRole.fromLabel(profile?.workspaceRole))
  }
  var showDeleteConfirm by remember { mutableStateOf(false) }
  var showPrivacyDialog by remember { mutableStateOf(false) }
  var showWebDeletionDialog by remember { mutableStateOf(false) }
  var reauthPasswordInput by remember(reauthRequiredForDelete) { mutableStateOf("") }

  LaunchedEffect(profile?.firstName, profile?.lastName, profile?.displayName, profile?.workspaceRole) {
    if (profile != null) {
      if (profile.firstName.isNotBlank() || profile.lastName.isNotBlank()) {
        editedFirstName = profile.firstName
        editedLastName = profile.lastName
      } else if (profile.displayName.isNotBlank()) {
        val full = profile.displayName.trim()
        editedFirstName = full.substringBefore(" ")
        editedLastName = if (full.contains(" ")) full.substringAfter(" ").trim() else ""
      }
      editedRole = WorkspaceRole.fromLabel(profile.workspaceRole)
    }
  }

  val editedName = remember(editedFirstName, editedLastName) {
    listOf(editedFirstName.trim(), editedLastName.trim())
      .filter { it.isNotBlank() }
      .joinToString(" ")
      .take(80)
  }

  val initials = remember(editedName) {
    editedName.trim().split(" ").filter { it.isNotBlank() }.take(2)
      .joinToString("") { it.first().uppercase() }
      .ifBlank { "U" }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 24.dp)
        .testTag("account_profile_dialog"),
      shape = RoundedCornerShape(28.dp),
      color = Color.White,
      contentColor = PopupButtonTextDark,
      shadowElevation = 16.dp,
      border = BorderStroke(1.dp, PopupButtonBorderLight)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White)
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Header Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF6366F1))
                  )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
              )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = editedName.ifBlank { "CubedPDF Account" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PopupButtonTextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (!userEmail.isNullOrBlank()) {
                Text(
                  text = userEmail,
                  style = MaterialTheme.typography.bodySmall,
                  color = PopupSubtitleMuted,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }

          Spacer(Modifier.width(8.dp))

          // White popup close button
          Box(
            modifier = Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(Color.White)
              .border(1.dp, PopupButtonBorderLight, CircleShape),
            contentAlignment = Alignment.Center
          ) {
            IconButton(
              onClick = onDismiss,
              modifier = Modifier
                .size(42.dp)
                .testTag("close_account_dialog_button")
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close account settings",
                tint = PopupButtonTextDark
              )
            }
          }
        }

        // Authentication Provider & Verification Status Banner
        val isPasswordProvider = profile?.provider == "password"
        val effectiveVerified = profile?.emailVerified ?: isEmailVerified
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFECFDF5))
            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(18.dp)
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = if (isPasswordProvider) {
                  if (effectiveVerified) "Email & Password • Verified" else "Email & Password • Unverified"
                } else {
                  "Signed in with Google"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF047857),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            Spacer(Modifier.width(8.dp))
            Text(
              text = editedRole.label,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = PopupButtonTextDark
            )
          }

          if (!effectiveVerified) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = onSendVerificationEmail,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = Color.White,
                  contentColor = PopupButtonTextDark
                ),
                border = BorderStroke(1.dp, PopupButtonBorderLight),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 38.dp)
                  .testTag("resend_verification_email_button")
              ) {
                Text("Verify Email", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
              }
              OutlinedButton(
                onClick = onRefreshVerification,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = Color.White,
                  contentColor = PopupPrimaryAccent
                ),
                border = BorderStroke(1.dp, PopupPrimaryAccent),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 38.dp)
                  .testTag("refresh_verification_status_button")
              ) {
                Text("Refresh Status", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
              }
            }
          }
        }

        // Status Feedback
        if (!statusMessage.isNullOrBlank()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(PopupSoftSurface)
              .border(1.dp, PopupButtonBorderLight, RoundedCornerShape(12.dp))
              .padding(10.dp)
          ) {
            Text(
              text = statusMessage,
              style = MaterialTheme.typography.bodySmall,
              color = PopupButtonTextDark
            )
          }
        }

        // Editable First Name & Last Name Fields
        val whiteFieldColors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = PopupButtonTextDark,
          unfocusedTextColor = PopupButtonTextDark,
          focusedLabelColor = PopupPrimaryAccent,
          unfocusedLabelColor = PopupSubtitleMuted,
          focusedBorderColor = PopupPrimaryAccent,
          unfocusedBorderColor = PopupButtonBorderLight,
          cursorColor = PopupPrimaryAccent,
          focusedContainerColor = Color.White,
          unfocusedContainerColor = Color.White
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedTextField(
            value = editedFirstName,
            onValueChange = { if (it.length <= 40) editedFirstName = it },
            label = { Text("First Name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = whiteFieldColors,
            modifier = Modifier
              .weight(1f)
              .testTag("profile_first_name_input")
          )
          OutlinedTextField(
            value = editedLastName,
            onValueChange = { if (it.length <= 40) editedLastName = it },
            label = { Text("Last Name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = whiteFieldColors,
            modifier = Modifier
              .weight(1f)
              .testTag("profile_last_name_input")
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Workspace Role",
            style = MaterialTheme.typography.labelLarge,
            color = PopupButtonTextDark
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(WorkspaceRole.entries) { role ->
              val isSelected = editedRole == role
              val chipShape = RoundedCornerShape(50)
              Box(
                modifier = Modifier
                  .heightIn(min = 40.dp)
                  .clip(chipShape)
                  .background(Color.White)
                  .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) PopupPrimaryAccent else PopupButtonBorderLight,
                    shape = chipShape
                  )
                  .clickable { editedRole = role }
                  .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = role.label,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                  color = if (isSelected) PopupPrimaryAccent else PopupButtonTextDark,
                  maxLines = 1,
                  softWrap = false
                )
              }
            }
          }
        }

        // White popup primary button
        Button(
          onClick = { onSaveProfile(editedFirstName, editedLastName, editedRole) },
          enabled = !isBusy && editedName.trim().isNotEmpty(),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = PopupPrimaryAccent,
            disabledContainerColor = Color.White.copy(alpha = 0.72f),
            disabledContentColor = Color(0xFF64748B)
          ),
          border = BorderStroke(1.5.dp, PopupPrimaryAccent),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("save_profile_button")
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = PopupPrimaryAccent
            )
          } else {
            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Save Profile Preferences",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center
            )
          }
        }

        HorizontalDivider(color = PopupDividerLight)

        // Cloud Sync Section (Firestore users/{uid}/recent_docs)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = Icons.Rounded.CloudDone,
              contentDescription = null,
              tint = Color(0xFF0284C7),
              modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Cloud History (${cloudDocs.size})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = PopupButtonTextDark,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Spacer(Modifier.width(8.dp))
          OutlinedButton(
            onClick = onSyncLocalFiles,
            enabled = !isBusy && localRecentFiles.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = Color.White,
              contentColor = PopupButtonTextDark,
              disabledContainerColor = Color.White.copy(alpha = 0.72f),
              disabledContentColor = Color(0xFF64748B)
            ),
            border = BorderStroke(1.dp, PopupButtonBorderLight),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .heightIn(min = 40.dp)
              .testTag("sync_recent_files_button")
          ) {
            Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(
              text = "Sync Local (${localRecentFiles.size})",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false
            )
          }
        }

        when (cloudDocsState) {
          is CloudUiState.Loading -> {
            Text(
              text = "Connecting to your private cloud history...",
              style = MaterialTheme.typography.bodySmall,
              color = PopupSubtitleMuted
            )
          }
          is CloudUiState.Error -> {
            Text(
              text = cloudDocsState.message,
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFFE11D48)
            )
          }
          is CloudUiState.Success -> {
            if (cloudDocs.isEmpty()) {
              Text(
                text = "No documents synced yet. Tap Sync Local or edit a PDF to keep your history backed up across sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = PopupSubtitleMuted
              )
            } else {
              cloudDocs.take(5).forEach { doc ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PopupSoftSurface)
                    .border(1.dp, PopupDividerLight, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = doc.fileName,
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Bold,
                      color = PopupButtonTextDark,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "${doc.lastToolName} • ${doc.pageCount} pg • ${PdfEngine.formatFileSize(doc.sizeBytes)}",
                      style = MaterialTheme.typography.labelSmall,
                      color = PopupSubtitleMuted,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  Spacer(Modifier.width(8.dp))
                  Box(
                    modifier = Modifier
                      .size(36.dp)
                      .clip(CircleShape)
                      .background(Color.White)
                      .border(1.dp, PopupButtonBorderLight, CircleShape),
                    contentAlignment = Alignment.Center
                  ) {
                    IconButton(
                      onClick = { onDeleteCloudDoc(doc.id) },
                      modifier = Modifier.size(36.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete synced item",
                        tint = PopupButtonTextDark,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(color = PopupDividerLight)

        // Account Security & Session Controls — all white buttons for maximum legibility in popups
        OutlinedButton(
          onClick = { openExternalUrl(context, GOOGLE_ACCOUNT_SECURITY_URL) },
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = PopupButtonTextDark
          ),
          border = BorderStroke(1.dp, PopupButtonBorderLight),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("manage_google_security_button")
        ) {
          Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Manage Google Security & Password",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
          )
          Spacer(Modifier.width(6.dp))
          Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onSwitchAccount,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = Color.White,
              contentColor = PopupButtonTextDark
            ),
            border = BorderStroke(1.dp, PopupButtonBorderLight),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("switch_account_button")
          ) {
            Icon(Icons.Rounded.SwitchAccount, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Switch",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false
            )
          }

          Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.White,
              contentColor = PopupDestructiveText
            ),
            border = BorderStroke(1.5.dp, PopupDestructiveBorder),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("sign_out_button")
          ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Sign Out",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false
            )
          }
        }

        OutlinedButton(
          onClick = { showDeleteConfirm = true },
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = PopupDestructiveText
          ),
          border = BorderStroke(1.dp, PopupDestructiveBorder),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .testTag("delete_cloud_profile_button")
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteForever,
            contentDescription = null,
            tint = PopupDestructiveText,
            modifier = Modifier.size(17.dp)
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = "Delete Account & Cloud Data",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = PopupDestructiveText
          )
        }

        // Play Store Compliance: Privacy Policy & Public Web Account Deletion Info links
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = { showPrivacyDialog = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = Color.White,
              contentColor = PopupButtonTextDark
            ),
            border = BorderStroke(1.dp, PopupButtonBorderLight),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("profile_privacy_policy_button")
          ) {
            Icon(Icons.Rounded.PrivacyTip, contentDescription = null, tint = PopupPrimaryAccent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(
              text = "Privacy Policy",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis
            )
          }

          OutlinedButton(
            onClick = { showWebDeletionDialog = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = Color.White,
              contentColor = PopupButtonTextDark
            ),
            border = BorderStroke(1.dp, PopupButtonBorderLight),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("profile_web_deletion_button")
          ) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = PopupDestructiveText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(
              text = "Web Deletion",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      containerColor = Color.White,
      titleContentColor = PopupButtonTextDark,
      textContentColor = PopupSubtitleMuted,
      title = { Text("Delete Account & Cloud Data?", fontWeight = FontWeight.Bold, color = PopupButtonTextDark) },
      text = {
        Text(
          text = "This permanently deletes your CubedPDF account, profile preferences, and synced document history from Firebase. Your local PDF files on this device will remain untouched.",
          color = PopupSubtitleMuted
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirm = false
            onDeleteCloudData()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = PopupDestructiveText
          ),
          border = BorderStroke(1.5.dp, PopupDestructiveBorder),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Text("Delete", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
      },
      dismissButton = {
        Button(
          onClick = { showDeleteConfirm = false },
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = PopupButtonTextDark
          ),
          border = BorderStroke(1.dp, PopupButtonBorderLight),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Text("Cancel", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
      }
    )
  }

  // Re-authentication prompt when Firebase requires recent sign-in to complete user.delete()
  val reauthInfo = reauthRequiredForDelete
  if (reauthInfo != null) {
    val isPasswordAccount = reauthInfo.providerId == "password"
    AlertDialog(
      onDismissRequest = onDismissReauthPrompt,
      containerColor = Color.White,
      titleContentColor = PopupButtonTextDark,
      textContentColor = PopupSubtitleMuted,
      modifier = Modifier.testTag("reauth_delete_account_dialog"),
      title = { Text("Confirm Identity to Delete Account", fontWeight = FontWeight.Bold, color = PopupButtonTextDark) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = if (isPasswordAccount) {
              "For your security, enter your account password to permanently delete ${reauthInfo.email ?: "your account"} from Firebase Authentication."
            } else {
              "For your security, confirm your Google sign-in once more to permanently delete ${reauthInfo.email ?: "your account"} from Firebase Authentication."
            },
            style = MaterialTheme.typography.bodySmall,
            color = PopupSubtitleMuted
          )
          if (isPasswordAccount) {
            OutlinedTextField(
              value = reauthPasswordInput,
              onValueChange = { reauthPasswordInput = it },
              label = { Text("Account Password") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PopupButtonTextDark,
                unfocusedTextColor = PopupButtonTextDark,
                focusedLabelColor = PopupPrimaryAccent,
                unfocusedLabelColor = PopupSubtitleMuted,
                focusedBorderColor = PopupPrimaryAccent,
                unfocusedBorderColor = PopupButtonBorderLight,
                cursorColor = PopupPrimaryAccent,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("reauth_delete_password_input")
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (isPasswordAccount) {
              onReauthWithPasswordAndDelete(reauthPasswordInput)
            } else {
              onReauthWithGoogleAndDelete()
            }
          },
          enabled = !isBusy && (!isPasswordAccount || reauthPasswordInput.isNotBlank()),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = PopupDestructiveText
          ),
          border = BorderStroke(1.5.dp, PopupDestructiveBorder),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("reauth_confirm_delete_button")
        ) {
          Text(
            text = if (isPasswordAccount) "Verify & Delete" else "Verify with Google",
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
          )
        }
      },
      dismissButton = {
        Button(
          onClick = onDismissReauthPrompt,
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = PopupButtonTextDark
          ),
          border = BorderStroke(1.dp, PopupButtonBorderLight),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Text("Cancel", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
      }
    )
  }

  if (showPrivacyDialog) {
    PrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
  }

  if (showWebDeletionDialog) {
    WebAccountDeletionDialog(onDismiss = { showWebDeletionDialog = false })
  }
}
