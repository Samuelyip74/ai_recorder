package com.example.airecorder.navigation

sealed class NavRoutes(val route: String) {
    data object Recorder : NavRoutes("recorder")
    data object Meetings : NavRoutes("meetings")
    data object Settings : NavRoutes("settings")
    data object MeetingDetail : NavRoutes("meeting_detail/{meetingId}") {
        fun createRoute(meetingId: Long) = "meeting_detail/$meetingId"
    }
}
