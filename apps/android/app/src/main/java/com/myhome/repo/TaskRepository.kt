package com.myhome.repo

import com.myhome.net.ApiService
import com.myhome.net.dto.TaskDto
import com.myhome.net.dto.TaskRecordDto
import com.myhome.net.dto.TaskRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun list(includeInactive: Boolean = false): List<TaskDto> = api.listTasks(includeInactive)
    suspend fun create(req: TaskRequest): TaskDto = api.createTask(req)
    suspend fun get(id: String): TaskDto = api.getTask(id)
    suspend fun update(id: String, req: TaskRequest): TaskDto = api.updateTask(id, req)
    suspend fun delete(id: String) = api.deleteTask(id)
    suspend fun complete(id: String): TaskRecordDto = api.completeTask(id)
    suspend fun listRecords(userId: String? = null): List<TaskRecordDto> = api.listTaskRecords(userId)
    suspend fun deleteRecord(id: String) = api.deleteTaskRecord(id)
}
