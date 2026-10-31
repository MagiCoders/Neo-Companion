package com.example

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.CadenceViewModel
import com.example.ui.character.CharacterController
import com.example.ui.character.CharacterRuntimeState
import com.example.ui.character.CharacterSpec
import com.example.ui.character.CharacterStageView
import com.example.ui.character.GlobalFloatingCompanionModule
import com.example.ui.components.VoiceUndoCancelOverlay
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CharacterPickerScreen
import com.example.ui.screens.ComponentGalleryScreen
import com.example.ui.screens.DrivingModeScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.HelpAndFeedbackScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MochiBottomNavBar
import com.example.ui.screens.MochiMainTab
import com.example.ui.screens.MochiToolTile
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.PrivacyAndDataScreen
import com.example.ui.screens.ProfileAndSettingsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SingleToolScreen
import com.example.ui.screens.StageScreen
import com.example.ui.screens.ToolResultScreen
import com.example.ui.screens.ToolsGridScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.screens.WhenYouAreBusyScreen
import com.example.ui.theme.AuroraBackground
import com.example.ui.theme.LocalVisualStyle
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiTheme
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType
import com.example.ui.theme.MochiVisualStyle
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.example.ai.OmnichannelMediaBridge
import com.example.data.MochiHashRoutes
import com.example.ui.screens.QuestsScreen

fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    throw IllegalStateException("Credentials should be called in the context of an Activity")
}

enum class MochiScreenRoute(val hashRoute: String) {
    STAGE(MochiHashRoutes.STAGE),
    CHARACTER_PICKER(MochiHashRoutes.CHARACTER_PICKER),
    TOOLS_GRID(MochiHashRoutes.TOOLS),
    SINGLE_TOOL(MochiHashRoutes.SINGLE_TOOL),
    TOOL_RESULT(MochiHashRoutes.TOOL_RESULT),
    COMPONENT_GALLERY(MochiHashRoutes.COMPONENT_GALLERY),
    LIBRARY(MochiHashRoutes.LIBRARY),
    SEARCH(MochiHashRoutes.SEARCH),
    PROFILE_SETTINGS(MochiHashRoutes.PROFILE),
    EDIT_PROFILE(MochiHashRoutes.EDIT_PROFILE),
    WALLET(MochiHashRoutes.WALLET),
    PERMISSIONS(MochiHashRoutes.PERMISSIONS),
    PRIVACY_DATA(MochiHashRoutes.PRIVACY),
    HELP_FEEDBACK(MochiHashRoutes.HELP),
    WHEN_BUSY(MochiHashRoutes.BUSY),
    DRIVING_MODE(MochiHashRoutes.DRIVING),
    PANTRY(MochiHashRoutes.PANTRY),
    QUESTS(MochiHashRoutes.QUESTS)
}

class MainActivity : ComponentActivity() {

    private var isInSystemPiPState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        configureAutoPictureInPictureParams()

        setContent {
            MochiLifeRootContainer(
                isInPictureInPictureMode = isInSystemPiPState
            )
        }
    }

    private fun buildPiPParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(1, 1))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun configureAutoPictureInPictureParams() {
        try {
            setPictureInPictureParams(buildPiPParams())
        } catch (e: Exception) {
            Log.w("MochiPiP", "PiP params setup skipped: ${e.message}")
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (FirebaseAuth.getInstance().currentUser != null) {
            try {
                enterPictureInPictureMode(buildPiPParams())
            } catch (e: Exception) {
                Log.w("MochiPiP", "Could not enter Picture-in-Picture mode: ${e.message}")
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInSystemPiPState = isInPictureInPictureMode
    }
}

@Composable
fun MochiLifeRootContainer(
    isInPictureInPictureMode: Boolean = false
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(activity) { CredentialManager.create(activity) }
    val auth = remember { FirebaseAuth.getInstance() }

    val viewModel: CadenceViewModel = viewModel(
        factory = CadenceViewModel.provideFactory(context)
    )

    val selectedCharacter by viewModel.selectedCharacter.collectAsStateWithLifecycle()
    val previewAccentCharacter by viewModel.previewAccentCharacter.collectAsStateWithLifecycle()
    val selectedVisualStyle by viewModel.selectedVisualStyle.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isCompanionThinking by viewModel.isCompanionThinking.collectAsStateWithLifecycle()
    val activeAccent = (previewAccentCharacter ?: selectedCharacter).accent

    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            currentUser = user
            viewModel.onAuthenticatedUserChanged(
                userId = user?.uid,
                displayName = user?.displayName,
                email = user?.email
            )
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    suspend fun handleGoogleSignInResponse(result: GetCredentialResponse) {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential =
                GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            currentUser = authResult.user
            authError = null
        } else {
            authError = "Unexpected credential type returned."
        }
    }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(activity.getString(R.string.default_web_client_id))
                    .setAutoSelectEnabled(true)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(activity, request)
                handleGoogleSignInResponse(result)
            } catch (_: NoCredentialException) {
            } catch (e: GetCredentialException) {
                Log.w("MochiAuth", "Silent auto sign-in skipped: ${e.message}")
            } catch (e: Exception) {
                Log.w("MochiAuth", "Silent auto sign-in error: ${e.message}")
            }
        }
    }

    MochiTheme(
        characterAccent = activeAccent,
        visualStyle = selectedVisualStyle
    ) {
        if (isInPictureInPictureMode) {
            PiPCharacterWindow(
                character = selectedCharacter,
                controller = viewModel.characterController,
                latestMessage = chatHistory.lastOrNull { !it.senderIsUser }?.message,
                isThinking = isCompanionThinking
            )
            return@MochiTheme
        }

        if (currentUser == null) {
            AuthScreen(
                isSigningIn = isSigningIn,
                errorMessage = authError,
                onGoogleSignInClick = {
                    coroutineScope.launch {
                        isSigningIn = true
                        authError = null
                        try {
                            val signInWithGoogleOption =
                                GetSignInWithGoogleOption.Builder(
                                    activity.getString(R.string.default_web_client_id)
                                ).build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(signInWithGoogleOption)
                                .build()
                            val result = credentialManager.getCredential(activity, request)
                            handleGoogleSignInResponse(result)
                        } catch (_: GetCredentialCancellationException) {
                            authError = "Sign-in was canceled."
                        } catch (e: GetCredentialException) {
                            Log.e("MochiAuth", "Google Sign-In failed", e)
                            authError = e.localizedMessage ?: "Check your Google account on this device."
                        } catch (e: Exception) {
                            Log.e("MochiAuth", "Unexpected Sign-In error", e)
                            authError = e.localizedMessage ?: "Please try again."
                        } finally {
                            isSigningIn = false
                        }
                    }
                }
            )
        } else {
            MochiAppHost(viewModel = viewModel)
        }
    }
}

@Composable
private fun PiPCharacterWindow(
    character: CharacterSpec,
    controller: CharacterController,
    latestMessage: String? = null,
    isThinking: Boolean = false
) {
    val visualStyle = LocalVisualStyle.current
    val runtimeState by controller.state.collectAsStateWithLifecycle()

    val statusText = when {
        isThinking || runtimeState is CharacterRuntimeState.Thinking -> "Thinking..."
        runtimeState is CharacterRuntimeState.Listening -> "Listening..."
        runtimeState is CharacterRuntimeState.Talking -> "Speaking"
        else -> latestMessage ?: character.displayName
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(listOf(character.accent, visualStyle.secondaryGlow)),
                shape = MochiShapes.card
            ),
        contentAlignment = Alignment.Center
    ) {
        AuroraBackground(
            accentColor = character.accent,
            visualStyle = visualStyle
        )
        CharacterStageView(
            character = character,
            controller = controller,
            halfResolutionMode = true,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(6.dp)
                .clip(MochiShapes.pill)
                .background(visualStyle.bgRaised.copy(alpha = 0.88f))
                .border(1.dp, character.accent.copy(alpha = 0.75f), MochiShapes.pill)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(character.accent)
                )
                Text(
                    text = statusText,
                    style = MochiType.Label,
                    color = MochiTokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MochiAppHost(
    viewModel: CadenceViewModel,
    initialRoute: MochiScreenRoute = MochiScreenRoute.STAGE
) {
    val context = LocalContext.current
    val currentHashRoute by viewModel.currentHashRoute.collectAsStateWithLifecycle()
    var activeTool by rememberSaveable { mutableStateOf(MochiToolTile.IMAGE_TO_PDF) }
    var showConversationSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialRoute) {
        if (initialRoute != MochiScreenRoute.STAGE) {
            viewModel.navigateHash(initialRoute.hashRoute)
        }
    }

    val selectedCharacter by viewModel.selectedCharacter.collectAsStateWithLifecycle()
    val previewAccentCharacter by viewModel.previewAccentCharacter.collectAsStateWithLifecycle()
    val selectedVisualStyle by viewModel.selectedVisualStyle.collectAsStateWithLifecycle()
    val isFloatingCompanionMode by viewModel.isFloatingCompanionMode.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isCompanionThinking by viewModel.isCompanionThinking.collectAsStateWithLifecycle()
    val isConverting by viewModel.isConverting.collectAsStateWithLifecycle()
    val isChefCooking by viewModel.isChefCooking.collectAsStateWithLifecycle()
    val conversionResult by viewModel.conversionResult.collectAsStateWithLifecycle()
    val selectedIngredients by viewModel.selectedIngredients.collectAsStateWithLifecycle()
    val meals by viewModel.meals.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val quests by viewModel.quests.collectAsStateWithLifecycle()
    val executedOrCancelledTurnIds by viewModel.executedOrCancelledTurnIds.collectAsStateWithLifecycle()
    val currentMoodTone by viewModel.currentMoodTone.collectAsStateWithLifecycle()
    val pendingVoiceAction by viewModel.pendingVoiceAction.collectAsStateWithLifecycle()
    val isWakeWordListeningActive by viewModel.isWakeWordListeningActive.collectAsStateWithLifecycle()

    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.let { tts ->
                    OmnichannelMediaBridge.configureHumanLikeNeuralVoice(tts, selectedCharacter)
                }
            }
        }
        ttsInstance = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    LaunchedEffect(selectedCharacter, ttsInstance) {
        ttsInstance?.let { tts ->
            OmnichannelMediaBridge.configureHumanLikeNeuralVoice(tts, selectedCharacter)
        }
    }

    // Keep global 'Hi Mochi' wake-word listening loop active whenever wakeWordEnabled & RECORD_AUDIO granted
    DisposableEffect(settings.wakeWordEnabled, selectedCharacter) {
        viewModel.voiceCommandService.bindCallbacks(
            onCommandRecognized = { recognizedCommand ->
                viewModel.sendMessageToMochi(context, ttsInstance, recognizedCommand)
            },
            onExecutePendingAction = { confirmedAction ->
                viewModel.executeTurnActionNow(context, ttsInstance, confirmedAction.turn)
            }
        )
        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.voiceCommandService.setWakeWordListeningEnabled(
            enabled = settings.wakeWordEnabled,
            hasRecordPermission = hasAudioPerm
        )
        onDispose {
            // Keep service alive across internal route transitions; only stop on host disposal
        }
    }

    // Keep Pantry route synced with Fridge Chef activeTool
    LaunchedEffect(currentHashRoute) {
        if (currentHashRoute == MochiHashRoutes.PANTRY) {
            activeTool = MochiToolTile.FRIDGE_CHEF
        }
    }

    BackHandler(enabled = currentHashRoute != MochiHashRoutes.STAGE || showConversationSheet || isFloatingCompanionMode) {
        when {
            isFloatingCompanionMode -> {
                viewModel.setFloatingCompanionMode(false)
            }
            showConversationSheet && currentHashRoute == MochiHashRoutes.STAGE -> {
                showConversationSheet = false
            }
            else -> {
                viewModel.setPreviewAccentCharacter(null)
                viewModel.popHashBack()
            }
        }
    }

    val auroraAccent = (previewAccentCharacter ?: selectedCharacter).accent
    val showBottomBar = !isFloatingCompanionMode && !showConversationSheet && currentHashRoute in setOf(
        MochiHashRoutes.STAGE,
        MochiHashRoutes.TOOLS,
        MochiHashRoutes.LIBRARY,
        MochiHashRoutes.QUESTS
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AuroraBackground(
                accentColor = auroraAccent,
                visualStyle = selectedVisualStyle
            )

            AnimatedContent(
                targetState = currentHashRoute,
                transitionSpec = {
                    fadeIn(animationSpec = tween(210)) togetherWith fadeOut(animationSpec = tween(160))
                },
                label = "MochiHashRouterTransition"
            ) { route ->
                when (route) {
                    MochiHashRoutes.STAGE -> {
                        StageScreen(
                            character = selectedCharacter,
                            controller = viewModel.characterController,
                            chatHistory = chatHistory,
                            isThinking = isCompanionThinking,
                            showConversationSheet = showConversationSheet,
                            onToggleConversationSheet = { showConversationSheet = it },
                            onSendMessage = { message ->
                                viewModel.sendMessageToMochi(context, ttsInstance, message)
                            },
                            onOpenCharacterPicker = {
                                viewModel.navigateHash(MochiHashRoutes.CHARACTER_PICKER)
                            },
                            onOpenTools = {
                                viewModel.navigateHash(MochiHashRoutes.TOOLS)
                            },
                            onOpenFridgeChef = {
                                activeTool = MochiToolTile.FRIDGE_CHEF
                                viewModel.navigateHash(MochiHashRoutes.PANTRY)
                            },
                            onOpenConverter = {
                                activeTool = MochiToolTile.IMAGE_TO_PDF
                                viewModel.navigateHash(MochiHashRoutes.SINGLE_TOOL)
                            },
                            onOpenProfile = {
                                viewModel.navigateHash(MochiHashRoutes.PROFILE)
                            },
                            onOpenQuests = {
                                viewModel.navigateHash(MochiHashRoutes.QUESTS)
                            },
                            moodToneLabel = currentMoodTone,
                            wakeWordEnabled = settings.wakeWordEnabled,
                            executedOrCancelledTurnIds = executedOrCancelledTurnIds,
                            onCancelTurnAction = viewModel::markTurnCancelled,
                            onExecuteTurnActionNow = { turn ->
                                viewModel.executeTurnActionNow(context, ttsInstance, turn)
                            },
                            visualStyle = selectedVisualStyle,
                            onSelectVisualStyle = viewModel::selectVisualStyle,
                            isFloatingCompanionMode = isFloatingCompanionMode,
                            onToggleFloatingCompanionMode = viewModel::setFloatingCompanionMode
                        )
                    }

                    MochiHashRoutes.CHARACTER_PICKER -> {
                        CharacterPickerScreen(
                            activeCharacter = selectedCharacter,
                            controller = viewModel.characterController,
                            onPreviewAccentChanged = { spec ->
                                viewModel.setPreviewAccentCharacter(spec)
                            },
                            onChooseCharacter = { spec ->
                                viewModel.chooseCharacter(spec)
                                viewModel.navigateHash(MochiHashRoutes.STAGE)
                            },
                            onBack = {
                                viewModel.setPreviewAccentCharacter(null)
                                viewModel.navigateHash(MochiHashRoutes.STAGE)
                            }
                        )
                    }

                    MochiHashRoutes.TOOLS -> {
                        ToolsGridScreen(
                            onSelectTool = { tile ->
                                if (tile == MochiToolTile.COMPONENT_GALLERY) {
                                    viewModel.navigateHash(MochiHashRoutes.COMPONENT_GALLERY)
                                } else if (tile == MochiToolTile.FRIDGE_CHEF) {
                                    activeTool = tile
                                    viewModel.navigateHash(MochiHashRoutes.PANTRY)
                                } else {
                                    activeTool = tile
                                    viewModel.navigateHash(MochiHashRoutes.SINGLE_TOOL)
                                }
                            },
                            onBack = {
                                viewModel.navigateHash(MochiHashRoutes.STAGE)
                            },
                            onOpenSearch = {
                                viewModel.navigateHash(MochiHashRoutes.SEARCH)
                            },
                            onOpenProfile = {
                                viewModel.navigateHash(MochiHashRoutes.PROFILE)
                            }
                        )
                    }

                    MochiHashRoutes.PANTRY, MochiHashRoutes.SINGLE_TOOL -> {
                        val resolvedTool = if (route == MochiHashRoutes.PANTRY) {
                            MochiToolTile.FRIDGE_CHEF
                        } else {
                            activeTool
                        }
                        SingleToolScreen(
                            tool = resolvedTool,
                            isRunning = isConverting || isChefCooking,
                            pantryChips = viewModel.pantryIngredientChips,
                            selectedIngredients = selectedIngredients,
                            meals = meals,
                            onToggleIngredient = viewModel::toggleIngredientChip,
                            onGenerateMeals = viewModel::generateMealsFromFridge,
                            onRunImageToPdf = { bmp ->
                                viewModel.runImageToPdf(context, bmp) {
                                    viewModel.navigateHash(MochiHashRoutes.TOOL_RESULT)
                                }
                            },
                            onRunImageToText = { bmp ->
                                viewModel.runOcrImageToText(context, bmp) {
                                    viewModel.navigateHash(MochiHashRoutes.TOOL_RESULT)
                                }
                            },
                            onRunPdfToImage = { uri ->
                                viewModel.runPdfToImage(context, uri) {
                                    viewModel.navigateHash(MochiHashRoutes.TOOL_RESULT)
                                }
                            },
                            onRunTextToSpeech = { text ->
                                viewModel.characterController.speakWithLipSync(
                                    context = context,
                                    tts = ttsInstance,
                                    text = text,
                                    character = selectedCharacter
                                )
                                viewModel.runTextToVoice(context, ttsInstance, text) {
                                    viewModel.navigateHash(MochiHashRoutes.TOOL_RESULT)
                                }
                            },
                            onRunSpeechToText = { uri ->
                                viewModel.runVoiceToText(context, uri) {
                                    viewModel.navigateHash(MochiHashRoutes.TOOL_RESULT)
                                }
                            },
                            onRunVideoToAudio = { uri ->
                                viewModel.runVideoToAudio(context, uri) {
                                    viewModel.navigateHash(MochiHashRoutes.TOOL_RESULT)
                                }
                            },
                            onRunDocToPdf = { title, body ->
                                viewModel.runDocToPdf(context, title, body) {
                                    viewModel.navigateHash(MochiHashRoutes.TOOL_RESULT)
                                }
                            },
                            onBack = {
                                viewModel.navigateHash(MochiHashRoutes.TOOLS)
                            }
                        )
                    }

                    MochiHashRoutes.TOOL_RESULT -> {
                        val res = conversionResult
                        if (res != null) {
                            ToolResultScreen(
                                result = res,
                                onBack = {
                                    viewModel.clearConversionResult()
                                    viewModel.navigateHash(MochiHashRoutes.SINGLE_TOOL)
                                }
                            )
                        } else {
                            viewModel.navigateHash(MochiHashRoutes.TOOLS)
                        }
                    }

                    MochiHashRoutes.COMPONENT_GALLERY -> {
                        ComponentGalleryScreen(
                            onSelectAccentCharacter = { spec ->
                                viewModel.chooseCharacter(spec)
                            },
                            onBack = {
                                viewModel.navigateHash(MochiHashRoutes.TOOLS)
                            },
                            onSelectVisualStyle = viewModel::selectVisualStyle
                        )
                    }

                    MochiHashRoutes.LIBRARY -> {
                        LibraryScreen(
                            onOpenSearch = { viewModel.navigateHash(MochiHashRoutes.SEARCH) },
                            onOpenProfile = { viewModel.navigateHash(MochiHashRoutes.PROFILE) },
                            onOpenFridgeChef = {
                                activeTool = MochiToolTile.FRIDGE_CHEF
                                viewModel.navigateHash(MochiHashRoutes.PANTRY)
                            },
                            onOpenTools = { viewModel.navigateHash(MochiHashRoutes.TOOLS) }
                        )
                    }

                    MochiHashRoutes.SEARCH -> {
                        SearchScreen(
                            onBack = { viewModel.navigateHash(MochiHashRoutes.LIBRARY) },
                            onOpenFridgeChef = {
                                activeTool = MochiToolTile.FRIDGE_CHEF
                                viewModel.navigateHash(MochiHashRoutes.PANTRY)
                            },
                            onOpenTools = { viewModel.navigateHash(MochiHashRoutes.TOOLS) }
                        )
                    }

                    MochiHashRoutes.QUESTS -> {
                        QuestsScreen(
                            quests = quests,
                            aiEnergyBalance = settings.aiEnergyBalance,
                            onToggleQuest = viewModel::toggleQuestCompleted,
                            onAddQuest = viewModel::addQuest,
                            onBack = { viewModel.navigateHash(MochiHashRoutes.STAGE) }
                        )
                    }

                    MochiHashRoutes.PROFILE -> {
                        ProfileAndSettingsScreen(
                            activeCharacter = selectedCharacter,
                            isDarkTheme = !selectedVisualStyle.isLight,
                            onToggleDarkTheme = { dark ->
                                viewModel.selectVisualStyle(
                                    if (dark) MochiVisualStyle.STUDIO_4K_OPTICAL else MochiVisualStyle.WARM_STUDIO_LUXE
                                )
                            },
                            settings = settings,
                            onUpdateSettings = viewModel::updateSettings,
                            onOpenEditProfile = { viewModel.navigateHash(MochiHashRoutes.EDIT_PROFILE) },
                            onOpenWallet = { viewModel.navigateHash(MochiHashRoutes.WALLET) },
                            onOpenCharacterPicker = { viewModel.navigateHash(MochiHashRoutes.CHARACTER_PICKER) },
                            onOpenWhenBusy = { viewModel.navigateHash(MochiHashRoutes.BUSY) },
                            onOpenDrivingMode = { viewModel.navigateHash(MochiHashRoutes.DRIVING) },
                            onOpenPermissions = { viewModel.navigateHash(MochiHashRoutes.PERMISSIONS) },
                            onOpenPrivacyData = { viewModel.navigateHash(MochiHashRoutes.PRIVACY) },
                            onOpenHelpFeedback = { viewModel.navigateHash(MochiHashRoutes.HELP) },
                            onOpenComponentGallery = { viewModel.navigateHash(MochiHashRoutes.COMPONENT_GALLERY) },
                            onBack = { viewModel.navigateHash(MochiHashRoutes.STAGE) }
                        )
                    }

                    MochiHashRoutes.EDIT_PROFILE -> {
                        EditProfileScreen(
                            settings = settings,
                            onUpdateSettings = viewModel::updateSettings,
                            onBack = { viewModel.navigateHash(MochiHashRoutes.PROFILE) }
                        )
                    }

                    MochiHashRoutes.WALLET -> {
                        WalletScreen(
                            settings = settings,
                            onUpdateSettings = viewModel::updateSettings,
                            onBack = { viewModel.navigateHash(MochiHashRoutes.PROFILE) }
                        )
                    }

                    MochiHashRoutes.PERMISSIONS -> {
                        PermissionsScreen(
                            settings = settings,
                            onTogglePermission = viewModel::togglePermissionAllowed,
                            onBack = { viewModel.navigateHash(MochiHashRoutes.PROFILE) }
                        )
                    }

                    MochiHashRoutes.PRIVACY -> {
                        PrivacyAndDataScreen(
                            settings = settings,
                            onUpdateSettings = viewModel::updateSettings,
                            onClearChatHistory = viewModel::clearChatHistory,
                            onBack = { viewModel.navigateHash(MochiHashRoutes.PROFILE) }
                        )
                    }

                    MochiHashRoutes.HELP -> {
                        HelpAndFeedbackScreen(
                            onAskCompanion = {
                                viewModel.navigateHash(MochiHashRoutes.STAGE)
                                showConversationSheet = true
                            },
                            onBack = { viewModel.navigateHash(MochiHashRoutes.PROFILE) }
                        )
                    }

                    MochiHashRoutes.BUSY -> {
                        WhenYouAreBusyScreen(
                            settings = settings,
                            onUpdateSettings = viewModel::updateSettings,
                            onBack = { viewModel.navigateHash(MochiHashRoutes.PROFILE) }
                        )
                    }

                    MochiHashRoutes.DRIVING -> {
                        DrivingModeScreen(
                            activeCharacter = selectedCharacter,
                            controller = viewModel.characterController,
                            onStartVoicePrompt = { prompt ->
                                viewModel.sendMessageToMochi(context, ttsInstance, prompt)
                            },
                            onExit = { viewModel.navigateHash(MochiHashRoutes.PROFILE) }
                        )
                    }
                }
            }

            // Global Floating 3D Companion Overlay Module across activity screens (or when expanded)
            if (settings.floatingCompanionEnabled &&
                (currentHashRoute != MochiHashRoutes.STAGE || isFloatingCompanionMode) &&
                currentHashRoute != MochiHashRoutes.DRIVING
            ) {
                GlobalFloatingCompanionModule(
                    character = selectedCharacter,
                    controller = viewModel.characterController,
                    latestTurn = chatHistory.lastOrNull { !it.senderIsUser } ?: chatHistory.lastOrNull(),
                    isThinking = isCompanionThinking,
                    isListening = isWakeWordListeningActive,
                    isExpandedFullOverlay = isFloatingCompanionMode,
                    onSetExpandedFullOverlay = { expanded ->
                        viewModel.setFloatingCompanionMode(expanded)
                    },
                    onSendMessage = { message ->
                        viewModel.sendMessageToMochi(context, ttsInstance, message)
                    },
                    onStartVoiceMic = {
                        val hasAudioPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        viewModel.voiceCommandService.triggerManualMicSession(hasAudioPerm)
                    }
                )
            }

            // Global 3-Second 'Undo/Cancel' Voice Action Overlay Pill
            VoiceUndoCancelOverlay(
                pendingAction = pendingVoiceAction,
                onUndoCancel = viewModel::cancelPendingVoiceAction,
                onExecuteNow = {
                    viewModel.executePendingVoiceActionNow(context, ttsInstance)
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        if (showBottomBar) {
            val currentTab = when (currentHashRoute) {
                MochiHashRoutes.TOOLS -> MochiMainTab.TOOLS
                MochiHashRoutes.LIBRARY -> MochiMainTab.LIBRARY
                else -> MochiMainTab.HOME
            }
            MochiBottomNavBar(
                currentTab = currentTab,
                onSelectTab = { tab ->
                    viewModel.navigateHash(
                        when (tab) {
                            MochiMainTab.HOME -> MochiHashRoutes.STAGE
                            MochiMainTab.TOOLS -> MochiHashRoutes.TOOLS
                            MochiMainTab.LIBRARY -> MochiHashRoutes.LIBRARY
                        }
                    )
                }
            )
        }
    }
}
