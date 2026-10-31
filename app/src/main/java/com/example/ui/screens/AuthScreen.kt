package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.character.CharacterController
import com.example.ui.character.CharacterSpec
import com.example.ui.character.CharacterStageView
import com.example.ui.theme.AuroraBackground
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LocalCharacterAccent
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiSpacing
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType

@Composable
fun AuthScreen(
    isSigningIn: Boolean,
    errorMessage: String?,
    onGoogleSignInClick: () -> Unit
) {
    val accent = LocalCharacterAccent.current
    val scope = androidx.compose.runtime.remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default) }
    val previewController = androidx.compose.runtime.remember { CharacterController(scope) }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackground(accentColor = accent)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.58f),
                contentAlignment = Alignment.Center
            ) {
                CharacterStageView(
                    character = CharacterSpec.ELENA,
                    controller = previewController,
                    modifier = Modifier.fillMaxSize()
                )
            }

            GlassSurface(
                shape = MochiShapes.sheet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MochiSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
                ) {
                    Text(
                        text = "MochiLife",
                        style = MochiType.Display,
                        color = MochiTokens.textPrimary
                    )
                    Text(
                        text = "Your quiet 3D companion for calm conversations and everyday tools.",
                        style = MochiType.Body,
                        color = MochiTokens.textSecondary
                    )

                    if (isSigningIn) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = accent,
                            trackColor = MochiTokens.hairline
                        )
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage,
                            style = MochiType.Label,
                            color = MochiTokens.danger,
                            modifier = Modifier.testTag("auth_error_banner")
                        )
                    }

                    Spacer(modifier = Modifier.height(MochiSpacing.unit))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MochiSpacing.minTouchTarget)
                            .clip(MochiShapes.pill)
                            .background(accent)
                            .clickable(enabled = !isSigningIn) { onGoogleSignInClick() }
                            .testTag("google_sign_in_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSigningIn) "Connecting..." else "Continue with Google",
                            style = MochiType.Body,
                            color = MochiTokens.bg
                        )
                    }
                }
            }
        }
    }
}
