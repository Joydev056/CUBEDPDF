package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.pdf.PasswordStrength
import com.example.pdf.PdfCryptoService
import com.example.pdf.PdfPasswordActionMode
import com.example.pdf.PdfProtectionStatus
import com.example.ui.theme.LocalGlassColors

/**
 * Interactive PDF Password Security panel supporting both setting a new password (AES-256-GCM
 * encryption) and removing an existing password (decryption), with show/hide toggles, live
 * password strength metering, confirmation validation, and detected protection status badges.
 */
@Composable
fun PdfPasswordControlCard(
  mode: PdfPasswordActionMode,
  onModeChange: (PdfPasswordActionMode) -> Unit,
  password: String,
  onPasswordChange: (String) -> Unit,
  confirmPassword: String,
  onConfirmPasswordChange: (String) -> Unit,
  protectionStatus: PdfProtectionStatus?,
  accentColor: Color,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  var passwordVisible by remember { mutableStateOf(false) }
  var confirmVisible by remember { mutableStateOf(false) }

  val strength = remember(password) { PdfCryptoService.evaluatePasswordStrength(password) }
  val animatedStrength by animateFloatAsState(
    targetValue = strength.score,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "passwordStrengthProgress"
  )
  val strengthColor by animateColorAsState(
    targetValue = when (strength) {
      PasswordStrength.EMPTY -> MaterialTheme.colorScheme.outline
      PasswordStrength.WEAK -> Color(0xFFE11D48)
      PasswordStrength.FAIR -> Color(0xFFF59E0B)
      PasswordStrength.STRONG -> Color(0xFF10B981)
    },
    label = "passwordStrengthColor"
  )

  val hasConfirmMismatch = mode == PdfPasswordActionMode.SET_PASSWORD &&
    confirmPassword.isNotEmpty() &&
    password != confirmPassword
  val hasConfirmMatch = mode == PdfPasswordActionMode.SET_PASSWORD &&
    password.length >= PdfCryptoService.MIN_PASSWORD_LENGTH &&
    confirmPassword == password

  GlassSurface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("pdf_password_control_card"),
    shape = RoundedCornerShape(22.dp),
    tintGlow = accentColor
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header row
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = if (glass.isDark) 0.24f else 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (mode == PdfPasswordActionMode.SET_PASSWORD) {
              Icons.Rounded.EnhancedEncryption
            } else {
              Icons.Rounded.LockOpen
            },
            contentDescription = null,
            tint = if (glass.isDark) Color.White else accentColor,
            modifier = Modifier.size(20.dp)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (mode == PdfPasswordActionMode.SET_PASSWORD) {
              "Encrypt PDF with Password"
            } else {
              "Remove Password & Decrypt PDF"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = if (mode == PdfPasswordActionMode.SET_PASSWORD) {
              "Lock your document into an AES-256-GCM encrypted vault"
            } else {
              "Unlock an AES-256 vault or password-protected PDF"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Segmented Set / Remove Mode Toggle
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(glass.pillFill)
          .border(1.dp, glass.cardBorderStart.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        PasswordModeSegment(
          label = PdfPasswordActionMode.SET_PASSWORD.title,
          icon = Icons.Rounded.Lock,
          selected = mode == PdfPasswordActionMode.SET_PASSWORD,
          accentColor = accentColor,
          onClick = { onModeChange(PdfPasswordActionMode.SET_PASSWORD) },
          modifier = Modifier
            .weight(1f)
            .testTag("pdf_password_mode_set")
        )
        PasswordModeSegment(
          label = PdfPasswordActionMode.REMOVE_PASSWORD.title,
          icon = Icons.Rounded.LockOpen,
          selected = mode == PdfPasswordActionMode.REMOVE_PASSWORD,
          accentColor = accentColor,
          onClick = { onModeChange(PdfPasswordActionMode.REMOVE_PASSWORD) },
          modifier = Modifier
            .weight(1f)
            .testTag("pdf_password_mode_remove")
        )
      }

      // Auto-detected Document Protection Status Badge + Smart Mode Switch Suggestion
      if (protectionStatus != null) {
        val statusColor = when (protectionStatus) {
          PdfProtectionStatus.AES_VAULT_ENCRYPTED -> Color(0xFF8B5CF6)
          PdfProtectionStatus.NATIVE_PDF_PASSWORD_PROTECTED -> Color(0xFFF59E0B)
          PdfProtectionStatus.UNPROTECTED -> Color(0xFF10B981)
          PdfProtectionStatus.UNKNOWN -> MaterialTheme.colorScheme.primary
        }
        val statusIcon = when (protectionStatus) {
          PdfProtectionStatus.AES_VAULT_ENCRYPTED,
          PdfProtectionStatus.NATIVE_PDF_PASSWORD_PROTECTED -> Icons.Rounded.Shield
          PdfProtectionStatus.UNPROTECTED -> Icons.Rounded.VerifiedUser
          PdfProtectionStatus.UNKNOWN -> Icons.Rounded.Key
        }
        val suggestedMode = when {
          mode == PdfPasswordActionMode.SET_PASSWORD &&
            (protectionStatus == PdfProtectionStatus.AES_VAULT_ENCRYPTED ||
              protectionStatus == PdfProtectionStatus.NATIVE_PDF_PASSWORD_PROTECTED) -> {
            PdfPasswordActionMode.REMOVE_PASSWORD
          }
          mode == PdfPasswordActionMode.REMOVE_PASSWORD &&
            protectionStatus == PdfProtectionStatus.UNPROTECTED -> {
            PdfPasswordActionMode.SET_PASSWORD
          }
          else -> null
        }

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(statusColor.copy(alpha = if (glass.isDark) 0.16f else 0.10f))
            .border(1.dp, statusColor.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("pdf_protection_status_badge"),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = statusIcon,
              contentDescription = protectionStatus.title,
              tint = statusColor,
              modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = protectionStatus.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = protectionStatus.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          if (suggestedMode != null) {
            OutlinedButton(
              onClick = { onModeChange(suggestedMode) },
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("pdf_switch_mode_suggestion")
            ) {
              Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "Switch to ${suggestedMode.title}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }

      // Primary Password Input Field
      OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = {
          Text(
            if (mode == PdfPasswordActionMode.SET_PASSWORD) {
              "Create PDF Password (min ${PdfCryptoService.MIN_PASSWORD_LENGTH} chars)"
            } else {
              "Enter Current PDF Password"
            }
          )
        },
        placeholder = {
          Text(
            if (mode == PdfPasswordActionMode.SET_PASSWORD) {
              "Use letters, numbers & symbols"
            } else {
              "Enter password to unlock file"
            }
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Rounded.Key,
            contentDescription = null,
            tint = accentColor
          )
        },
        trailingIcon = {
          IconButton(
            onClick = { passwordVisible = !passwordVisible },
            modifier = Modifier
              .size(48.dp)
              .testTag("pdf_password_visibility_toggle")
          ) {
            Icon(
              imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
              contentDescription = if (passwordVisible) "Hide password" else "Show password"
            )
          }
        },
        singleLine = true,
        visualTransformation = if (passwordVisible) {
          VisualTransformation.None
        } else {
          PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Password,
          autoCorrectEnabled = false,
          imeAction = if (mode == PdfPasswordActionMode.SET_PASSWORD) ImeAction.Next else ImeAction.Done
        ),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = accentColor,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("pdf_password_input")
      )

      // Live Strength Meter + Confirm Password (Set Password mode only)
      AnimatedVisibility(
        visible = mode == PdfPasswordActionMode.SET_PASSWORD,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          // Strength meter bar & label
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Password Strength",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = strength.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = strengthColor,
                modifier = Modifier.testTag("pdf_password_strength_label")
              )
            }
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(glass.pillFill)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth(fraction = animatedStrength.coerceIn(0f, 1f))
                  .fillMaxHeight()
                  .clip(CircleShape)
                  .background(strengthColor)
              )
            }
          }

          // Confirm Password Input Field
          OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm PDF Password") },
            placeholder = { Text("Re-enter password to verify") },
            leadingIcon = {
              Icon(
                imageVector = if (hasConfirmMatch) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                contentDescription = null,
                tint = when {
                  hasConfirmMismatch -> Color(0xFFE11D48)
                  hasConfirmMatch -> Color(0xFF10B981)
                  else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
              )
            },
            trailingIcon = {
              IconButton(
                onClick = { confirmVisible = !confirmVisible },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("pdf_confirm_password_visibility_toggle")
              ) {
                Icon(
                  imageVector = if (confirmVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                  contentDescription = if (confirmVisible) "Hide confirmation password" else "Show confirmation password"
                )
              }
            },
            isError = hasConfirmMismatch,
            supportingText = {
              when {
                hasConfirmMismatch -> {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.ErrorOutline,
                      contentDescription = null,
                      tint = Color(0xFFE11D48),
                      modifier = Modifier.size(14.dp)
                    )
                    Text(
                      text = "Passwords do not match.",
                      color = Color(0xFFE11D48),
                      modifier = Modifier.testTag("pdf_password_mismatch_error")
                    )
                  }
                }
                hasConfirmMatch -> {
                  Text(
                    text = "Passwords match and ready to encrypt.",
                    color = Color(0xFF10B981),
                    modifier = Modifier.testTag("pdf_password_match_ok")
                  )
                }
              }
            },
            singleLine = true,
            visualTransformation = if (confirmVisible) {
              VisualTransformation.None
            } else {
              PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Password,
              autoCorrectEnabled = false,
              imeAction = ImeAction.Done
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = if (hasConfirmMatch) Color(0xFF10B981) else accentColor,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
              errorBorderColor = Color(0xFFE11D48)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("pdf_confirm_password_input")
          )
        }
      }

      // Local-only privacy microcopy
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(glass.pillFill)
          .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Rounded.VerifiedUser,
          contentDescription = null,
          tint = Color(0xFF10B981),
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = "AES-256-GCM encryption runs 100% on-device. CubedPDF never stores or uploads your password.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun PasswordModeSegment(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  selected: Boolean,
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val glass = LocalGlassColors.current
  val shape = RoundedCornerShape(12.dp)
  val bg = if (selected) {
    accentColor.copy(alpha = if (glass.isDark) 0.28f else 0.16f)
  } else {
    Color.Transparent
  }
  val borderColor = if (selected) {
    accentColor.copy(alpha = 0.72f)
  } else {
    Color.Transparent
  }
  val contentColor = if (selected) {
    if (glass.isDark) Color.White else accentColor
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

  Row(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .heightIn(min = 48.dp)
      .clip(shape)
      .background(bg)
      .border(1.dp, borderColor, shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = contentColor,
      modifier = Modifier.size(17.dp)
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = contentColor,
      maxLines = 1,
      softWrap = false
    )
  }
}
