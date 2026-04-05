package com.example.airecorder.rainbow

import android.util.Log
import com.ale.infra.manager.files.RainbowFileDescriptor
import com.ale.infra.manager.recordingfile.ConferenceRecord
import com.ale.infra.manager.recordingfile.RecordFile
import com.ale.infra.rest.listeners.onFailure
import com.ale.infra.rest.listeners.onSuccess
import com.ale.rainbowsdk.FileStorage
import com.ale.rainbowsdk.Infrastructure
import com.ale.rainbowsdk.RainbowSdk
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RainbowBubbleConversation(
    val id: String,
    val name: String,
    val topic: String,
    val lastActivityAt: Long,
    val lastMessagePreview: String?,
    val unreadCount: Int,
    val recordingCount: Int,
    val latestRecordingId: String?,
)

data class RainbowResolvedRecording(
    val conferenceRecordId: String,
    val descriptor: RainbowFileDescriptor,
)

@Singleton
class RainbowBubbleRepository @Inject constructor() {

    companion object {
        private const val TAG = "RainbowBubbleRepo"
        private val SUPPORTED_MEDIA_EXTENSIONS = setOf("wav", "mp3", "m4a", "mp4", "aac", "ogg", "opus", "webm")
    }

    private val sdk by lazy { RainbowSdk.instance() }
    private val infrastructure by lazy { Infrastructure.instance() }
    private var listenerRegistered = false
    private val recordingLock = Any()
    private val recordingIdsByRoomId = linkedMapOf<String, LinkedHashSet<String>>()

    private val _recordedRooms = MutableStateFlow<List<RainbowBubbleConversation>>(emptyList())
    val recordedRooms: StateFlow<List<RainbowBubbleConversation>> = _recordedRooms.asStateFlow()

    private val fileStorageListener = object : FileStorage.IFileStorageListener {
        override fun notifyConferenceRecordingFileAvailable(
            roomName: String,
            roomId: String,
            recordingId: String,
            ownerId: String?,
        ) {
            upsertRecording(roomId = roomId, recordingId = recordingId)
            publishRoom(roomId = roomId, fallbackRoomName = roomName)
        }
    }

    fun registerListenerIfNeeded() {
        if (listenerRegistered) return
        sdk.fileStorage().registerListener(fileStorageListener)
        listenerRegistered = true
    }

    fun refreshTrackedRooms() {
        trackedRoomIds().forEach { roomId ->
            publishRoom(roomId = roomId, fallbackRoomName = recordedRooms.value.firstOrNull { it.id == roomId }?.name)
        }
    }

    suspend fun refreshRecordedRooms() {
        sdk.bubbles().refreshActiveBubbles(offset = 0, limit = 500)
        Log.d(TAG, "Refreshing conference records. activeRooms=${sdk.bubbles().getAllList().size}")

        val discoveredRecordingsByRoomId = linkedMapOf<String, LinkedHashSet<String>>()
        val records = fetchConferenceRecords(roomId = null)
        records.forEach { record ->
            discoveredRecordingsByRoomId.getOrPut(record.roomId) { linkedSetOf() }.add(record.id)
        }

        val mergedRoomIds = synchronized(recordingLock) {
            discoveredRecordingsByRoomId.forEach { (roomId, recordingIds) ->
                val existingIds = recordingIdsByRoomId.getOrPut(roomId) { linkedSetOf() }
                existingIds += recordingIds
            }
            recordingIdsByRoomId.keys.toList()
        }

        Log.d(
            TAG,
            "Recorded rooms discovered=${discoveredRecordingsByRoomId.size} ids=${discoveredRecordingsByRoomId.keys}; mergedTrackedRooms=$mergedRoomIds",
        )
        mergedRoomIds.forEach { roomId ->
            publishRoom(roomId = roomId, fallbackRoomName = null)
        }
    }

    suspend fun resolveRecording(
        roomId: String,
        conferenceRecordId: String? = null,
    ): RainbowResolvedRecording {
        sdk.bubbles().refreshActiveBubbles(offset = 0, limit = 500)
        val room = sdk.bubbles().findBubbleById(roomId)
            ?: error("Rainbow bubble $roomId was not found.")
        val resolvedRecord = if (conferenceRecordId.isNullOrBlank()) {
            fetchConferenceRecords(roomId = roomId)
                .maxWithOrNull(
                    compareBy<ConferenceRecord> { it.startDate.time }
                        .thenBy { it.endDate.time }
                        .thenBy { it.id },
                )
        } else {
            fetchConferenceRecordById(conferenceRecordId)
        } ?: error("No conference recording record was found for ${room.name}.")

        val selectedFile = selectPreferredMediaFile(resolvedRecord)
            ?: error("No downloadable audio or video file was found for ${room.name}.")

        var descriptor: RainbowFileDescriptor? = null
        infrastructure.conferenceRecordMgr.resolveFileDescriptor(resolvedRecord, selectedFile.fileId)
            .onSuccess { descriptor = it }
            .onFailure { failure ->
                throw IllegalStateException(failure.message.ifBlank { "Unable to resolve Rainbow recording file." })
            }

        return RainbowResolvedRecording(
            conferenceRecordId = resolvedRecord.id,
            descriptor = descriptor ?: error("Rainbow recording resolution completed without a file descriptor."),
        )
    }

    fun clear() {
        synchronized(recordingLock) {
            recordingIdsByRoomId.clear()
        }
        _recordedRooms.value = emptyList()
    }

    private fun publishRoom(roomId: String, fallbackRoomName: String?) {
        val room = sdk.bubbles().findBubbleById(roomId)
        val conversation = room?.let { sdk.im().getConversationFromRoom(it) }
        val recordingIds = synchronized(recordingLock) { recordingIdsByRoomId[roomId]?.toList().orEmpty() }
        val recordingCount = recordingIds.size
        val updatedRoom = RainbowBubbleConversation(
            id = roomId,
            name = room?.name.orEmpty().ifBlank {
                fallbackRoomName.orEmpty().ifBlank { room?.topic.orEmpty().ifBlank { "Unnamed bubble" } }
            },
            topic = room?.topic.orEmpty(),
            lastActivityAt = room?.lastActivityDate?.time ?: room?.creationDate?.time ?: 0L,
            lastMessagePreview = conversation?.lastMessage?.messageContent?.takeIf { it.isNotBlank() }
                ?: "Conference recording available",
            unreadCount = conversation?.unreadMessageNumber ?: 0,
            recordingCount = recordingCount,
            latestRecordingId = recordingIds.lastOrNull(),
        )
        _recordedRooms.update { current ->
            current.filterNot { it.id == roomId }
                .plus(updatedRoom)
                .sortedByDescending { it.lastActivityAt }
        }
    }

    private fun upsertRecording(roomId: String, recordingId: String) {
        synchronized(recordingLock) {
            val recordingIds = recordingIdsByRoomId.getOrPut(roomId) { linkedSetOf() }
            recordingIds += recordingId
        }
    }

    private fun trackedRoomIds(): List<String> = synchronized(recordingLock) {
        recordingIdsByRoomId.keys.toList()
    }

    private suspend fun fetchConferenceRecords(roomId: String?): List<ConferenceRecord> {
        var records: List<ConferenceRecord> = emptyList()
        infrastructure.conferenceRecordMgr.fetchConferenceRecords(
            roomId = roomId,
            status = listOf("release_complete"),
            limit = 100,
            offset = 0,
            sortField = "recordingStartDate",
            sortOrder = -1,
        ).onSuccess {
            records = it
            Log.d(TAG, "Conference records fetched roomId=$roomId count=${it.size}")
        }.onFailure { failure ->
            Log.e(TAG, "Conference record fetch failed roomId=$roomId message=${failure.message}")
            throw IllegalStateException(failure.message.ifBlank { "Unable to load Rainbow conference recordings." })
        }
        return records
    }

    private suspend fun fetchConferenceRecordById(conferenceRecordId: String): ConferenceRecord? {
        var record: ConferenceRecord? = null
        infrastructure.conferenceRecordMgr.fetchConferenceRecordById(conferenceRecordId)
            .onSuccess {
                record = it
                Log.d(TAG, "Conference record fetched id=$conferenceRecordId roomId=${it.roomId}")
            }
            .onFailure { failure ->
                Log.e(TAG, "Conference record fetch failed id=$conferenceRecordId message=${failure.message}")
            }
        return record
    }

    private fun selectPreferredMediaFile(record: ConferenceRecord): RecordFile? {
        return record.getAudioFile()
            ?: record.getVideoFile()
            ?: record.files.firstOrNull(::isSupportedMediaFile)
    }

    private fun isSupportedMediaFile(file: RecordFile): Boolean {
        val extension = file.fileName.substringAfterLast('.', "").lowercase()
        return file.typeMIME.startsWith("audio/")
            || file.typeMIME.startsWith("video/")
            || extension in SUPPORTED_MEDIA_EXTENSIONS
    }
}
