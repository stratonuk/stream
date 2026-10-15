package com.strata.tv.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.strata.tv.ui.theme.StrataColors

/**
 * Xtream Codes login — functional equivalent of IPTV Smarters Pro
 * "Xtreme Codes", with STREAM monochrome styling.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onFinished: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StrataColors.SurfaceVoid)
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "STREAM",
                color = StrataColors.Silver,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Xtream Codes login",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Enter the same server URL, username and password you use in IPTV Smarters Pro (Xtreme Codes).",
                color = StrataColors.TextSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(28.dp))

            FieldRow(
                label = "Playlist name",
                value = state.playlistName,
                onValueChange = { viewModel.updateField(SetupViewModel.Field.PlaylistName, it) },
            )
            Spacer(Modifier.height(14.dp))
            FieldRow(
                label = "Server URL",
                value = state.providerHost,
                onValueChange = { viewModel.updateField(SetupViewModel.Field.Host, it) },
                keyboardType = KeyboardType.Uri,
                placeholder = "http://example.com:8080",
            )
            Spacer(Modifier.height(14.dp))
            FieldRow(
                label = "Username",
                value = state.username,
                onValueChange = { viewModel.updateField(SetupViewModel.Field.Username, it) },
            )
            Spacer(Modifier.height(14.dp))
            FieldRow(
                label = "Password",
                value = state.password,
                onValueChange = { viewModel.updateField(SetupViewModel.Field.Password, it) },
                isPassword = true,
            )

            if (state.httpWarning != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.httpWarning!!, color = StrataColors.Silver, fontSize = 13.sp)
            }
            if (state.errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.errorMessage!!, color = StrataColors.TextPrimary, fontSize = 13.sp)
            }

            if (state.diagnostics.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.diagnostics.forEach { step ->
                        Text(
                            text = "${if (step.ok) "✓" else "✗"} ${step.name}: ${step.detail}",
                            color = if (step.ok) StrataColors.TextSecondary else StrataColors.TextPrimary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            ConnectButton(
                label = if (state.testing) "Connecting…" else "Connect",
                enabled = !state.testing,
                onClick = { viewModel.connect() },
            )
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    placeholder: String = "",
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = StrataColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(StrataColors.SurfaceRaised, RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = if (focused) StrataColors.Silver else StrataColors.Border,
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = StrataColors.TextTertiary, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = StrataColors.TextPrimary,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(StrataColors.TextPrimary),
                visualTransformation = if (isPassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConnectButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = { if (enabled) onClick() },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (enabled) StrataColors.TextPrimary else StrataColors.SurfaceFloat,
            focusedContainerColor = StrataColors.Silver,
            disabledContainerColor = StrataColors.SurfaceFloat,
        ),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (enabled) StrataColors.SurfaceVoid else StrataColors.TextTertiary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
