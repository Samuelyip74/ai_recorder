package com.example.airecorder.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.airecorder.ui.meetings.MeetingDetailScreen
import com.example.airecorder.ui.meetings.MeetingDetailViewModel
import com.example.airecorder.ui.meetings.MeetingsScreen
import com.example.airecorder.ui.meetings.MeetingsViewModel
import com.example.airecorder.ui.recorder.RecorderScreen
import com.example.airecorder.ui.recorder.RecorderViewModel
import com.example.airecorder.ui.rainbow.RainbowAuthViewModel
import com.example.airecorder.ui.rainbow.RainbowLoginScreen
import com.example.airecorder.ui.settings.SettingsScreen
import com.example.airecorder.ui.settings.SettingsViewModel

@Composable
fun AiRecorderAppRoot() {
    val authViewModel: RainbowAuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        authViewModel.onPhonePermissionChanged(granted)
    }
    LaunchedEffect(Unit) {
        authViewModel.onPhonePermissionChanged(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    if (!authState.isSignedIn) {
        RainbowLoginScreen(
            uiState = authState,
            onLoginChanged = authViewModel::updateLogin,
            onPasswordChanged = authViewModel::updatePassword,
            onSignIn = authViewModel::signIn,
            onRequestPhonePermission = {
                permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            },
            onDismissError = authViewModel::clearError,
        )
        return
    }

    val navController = rememberNavController()
    val items = listOf(
        Triple(NavRoutes.Recorder.route, "Recorder", Icons.Outlined.Mic),
        Triple(NavRoutes.Meetings.route, "Recordings", Icons.AutoMirrored.Outlined.Article),
        Triple(NavRoutes.Settings.route, "Settings", Icons.Outlined.Settings),
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = destination?.route != NavRoutes.MeetingDetail.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    items.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == route } == true,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        NavHost(navController = navController, startDestination = NavRoutes.Recorder.route) {
            composable(NavRoutes.Recorder.route) {
                val viewModel: RecorderViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                RecorderScreen(
                    paddingValues = paddingValues,
                    uiState = state,
                    onStart = viewModel::startRecording,
                    onPause = viewModel::pauseRecording,
                    onResume = viewModel::resumeRecording,
                    onStop = viewModel::stopRecording,
                    onDismissDraft = viewModel::discardPendingRecording,
                    onConfirmSave = viewModel::saveMeeting,
                    onModeSelected = viewModel::selectRecordingMode,
                    onBeginPlaybackConsentRequest = viewModel::beginPlaybackConsentRequest,
                    onPlaybackCaptureGranted = viewModel::setPlaybackCaptureConsent,
                    onPermissionDenied = viewModel::onPermissionDenied,
                    onProjectionConsentDenied = viewModel::onProjectionConsentDenied,
                    onProjectionLaunchUnavailable = viewModel::onProjectionLaunchUnavailable,
                    onMessageShown = viewModel::clearMessage,
                )
            }
            composable(NavRoutes.Meetings.route) {
                val viewModel: MeetingsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.importedMeetingId) {
                    state.importedMeetingId?.let { meetingId ->
                        navController.navigate(NavRoutes.MeetingDetail.createRoute(meetingId))
                        viewModel.consumeImportedMeetingNavigation()
                    }
                }
                MeetingsScreen(
                    paddingValues = paddingValues,
                    uiState = state,
                    onQueryChange = viewModel::updateSearchQuery,
                    onDeleteMeeting = viewModel::deleteMeeting,
                    onDeleteAllMeetings = viewModel::deleteAllMeetings,
                    onRefreshRainbowBubbles = viewModel::refreshRainbowBubbles,
                    onRainbowBubbleClick = viewModel::importRainbowRecording,
                    onMeetingClick = { navController.navigate(NavRoutes.MeetingDetail.createRoute(it)) },
                )
            }
            composable(
                route = NavRoutes.MeetingDetail.route,
                arguments = listOf(navArgument("meetingId") { type = NavType.LongType }),
            ) {
                val viewModel: MeetingDetailViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                MeetingDetailScreen(
                    uiState = state,
                    onBack = { navController.popBackStack() },
                    onRename = viewModel::renameMeeting,
                    onDelete = {
                        viewModel.deleteMeeting()
                        navController.popBackStack()
                    },
                    onPlayPause = viewModel::togglePlayback,
                    onSeek = viewModel::seekTo,
                    onShareRecording = viewModel::shareRecording,
                    onGenerateTranscript = viewModel::generateTranscript,
                    onTranslateTranscript = viewModel::translateTranscript,
                    onConsumeShareAudioPath = viewModel::consumeShareAudioPath,
                    onClearMessage = viewModel::clearMessage,
                )
            }
            composable(NavRoutes.Settings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    paddingValues = paddingValues,
                    uiState = state,
                    onAutoTranscribeChanged = viewModel::setAutoTranscribe,
                    onLanguageChanged = viewModel::setLanguage,
                    onTranslationTargetLanguageChanged = viewModel::setTranslationTargetLanguage,
                    onDeleteAll = viewModel::deleteAllData,
                    onMessageShown = viewModel::clearMessage,
                )
            }
        }
    }
}
