package com.smithware.notepilot.data

import kotlinx.coroutines.flow.Flow

class NotePilotRepository(private val dao: NotePilotDao) {
    val captures: Flow<List<CaptureEntity>> = dao.observeAll()
    val recent: Flow<List<CaptureEntity>> = dao.observeRecent()

    suspend fun save(capture: CaptureEntity) = dao.save(capture)
    suspend fun delete(capture: CaptureEntity) = dao.delete(capture)
    suspend fun archive(capture: CaptureEntity) = dao.move(capture.id, Section.Archive, true)
    suspend fun move(capture: CaptureEntity, section: Section) = dao.move(capture.id, section, section == Section.Archive)
    suspend fun pin(capture: CaptureEntity) = dao.pin(capture.id, !capture.pinned)
    suspend fun complete(capture: CaptureEntity) = dao.complete(capture.id, !capture.completed)
    suspend fun markReminderScheduled(id: String) = dao.markReminderScheduled(id)
    suspend fun update(capture: CaptureEntity, title: String, content: String, type: CaptureType, items: List<String>) =
        dao.updateContent(capture.id, title, content, type, items.joinToString("|"))
}
