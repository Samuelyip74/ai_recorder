package com.example.airecorder.rainbow

import android.util.Log
import com.ale.infra.manager.files.RainbowFileDescriptor
import com.ale.infra.manager.room.Room
import com.ale.infra.rest.listeners.onFailure
import com.ale.infra.rest.listeners.onSuccess
import com.ale.rainbowsdk.FileStorage
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
)

@Singleton
class RainbowBubbleRepository @Inject constructor() {

    companion object {
        private const val TAG = "RainbowBubbleRepo"
        private val RECORDING_EXTENSIONS = setOf("wav", "mp3", "m4a", "mp4", "aac", "ogg", "opus", "webm")
    }

    private val sdk by lazy { RainbowSdk.instance() }
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
        Log.d(TAG, "Refreshing recorded rooms. activeRooms=${sdk.bubbles().getAllList().size}")
        val discoveredRecordingsByRoomId = linkedMapOf<String, LinkedHashSet<String>>()
        sdk.bubbles().getAllList().forEach { room ->
            Log.d(TAG, "Scanning room id=${room.id} name=${room.name}")
            collectBubbleRecordingIds(room).forEach { recordingId ->
                discoveredRecordingsByRoomId.getOrPut(room.id) { linkedSetOf() }.add(recordingId)
            }
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

    fun clear() {
        synchronized(recordingLock) {
            recordingIdsByRoomId.clear()
        }
        _recordedRooms.value = emptyList()
    }

    private fun publishRoom(roomId: String, fallbackRoomName: String?) {
        val room = sdk.bubbles().findBubbleById(roomId)
        val conversation = room?.let { sdk.im().getConversationFromRoom(it) }
        val recordingCount = synchronized(recordingLock) { recordingIdsByRoomId[roomId]?.size ?: 0 }
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

    private suspend fun collectBubbleRecordingIds(room: Room): Set<String> {
        val recordingIds = linkedSetOf<String>()
        var offset = 0
        val pageSize = 100
        while (true) {
            val page = buildList {
                addAll(fetchBubbleFiles(room = room, limit = pageSize, offset = offset) { targetRoom, targetLimit, targetOffset ->
                    sdk.fileStorage().fetchFileInBubble(room = targetRoom, limit = targetLimit, offset = targetOffset)
                })
                addAll(fetchBubbleFiles(room = room, limit = pageSize, offset = offset) { targetRoom, targetLimit, targetOffset ->
                    sdk.fileStorage().fetchFileReceivedInBubble(room = targetRoom, limit = targetLimit, offset = targetOffset)
                })
                addAll(fetchBubbleFiles(room = room, limit = pageSize, offset = offset) { targetRoom, targetLimit, targetOffset ->
                    sdk.fileStorage().fetchFileSentInBubble(room = targetRoom, limit = targetLimit, offset = targetOffset)
                })
            }.distinctBy { it.id }

            page.forEach { file ->
                Log.d(
                    TAG,
                    "File candidate roomId=${room.id} fileId=${file.id} name=${file.fileName} mime=${file.typeMIME} ext=${file.extension} confRoomId=${file.filesConfRecordingRoomId}",
                )
            }
            page.filter { isRecordingFile(it, room.id) }
                .mapNotNull { it.id }
                .forEach(recordingIds::add)

            Log.d(TAG, "Room id=${room.id} pageOffset=$offset pageSize=${page.size} matchedRecordings=${recordingIds.size}")

            if (page.size < pageSize) break
            offset += pageSize
        }
        return recordingIds
    }

    private suspend fun fetchBubbleFiles(
        room: Room,
        limit: Int,
        offset: Int,
        fetcher: suspend (Room, Int, Int) -> com.ale.infra.rest.listeners.RainbowResult<List<RainbowFileDescriptor>>,
    ): List<RainbowFileDescriptor> {
        var page: List<RainbowFileDescriptor> = emptyList()
        fetcher(room, limit, offset)
            .onSuccess { page = it }
            .onFailure { failure ->
                Log.e(TAG, "Bubble file fetch failed for roomId=${room.id} roomName=${room.name} offset=$offset limit=$limit message=${failure.message}")
                throw IllegalStateException(failure.message.ifBlank { "Unable to load Rainbow files for ${room.name}." })
            }
        Log.d(TAG, "Bubble file fetch success roomId=${room.id} roomName=${room.name} offset=$offset limit=$limit count=${page.size}")
        return page
    }

    private fun isRecordingFile(file: RainbowFileDescriptor, roomId: String): Boolean {
        if (file.filesConfRecordingRoomId == roomId) return true
        if (file.isAudioType() || file.isVideoType()) return true

        val extension = file.extension
            ?.lowercase()
            ?.removePrefix(".")
            ?: file.fileName
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase()
                ?.removePrefix(".")
                .orEmpty()

        return extension in RECORDING_EXTENSIONS
    }
}
