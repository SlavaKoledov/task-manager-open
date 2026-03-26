package com.taskmanager.android.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TaskManagerApi {
    @GET("lists")
    suspend fun getLists(): List<ApiListItem>

    @POST("lists")
    suspend fun createList(@Body payload: ApiListPayload): ApiListItem

    @PATCH("lists/{listId}")
    suspend fun updateList(
        @Path("listId") listId: Int,
        @Body payload: ApiListPayload,
    ): ApiListItem

    @DELETE("lists/{listId}")
    suspend fun deleteList(@Path("listId") listId: Int)

    @POST("lists/reorder")
    suspend fun reorderLists(@Body payload: ApiListReorderPayload): List<ApiListItem>

    @GET("tasks")
    suspend fun getTasks(): List<ApiTask>

    @GET("tasks/today")
    suspend fun getTodayTasks(@Query("today") today: String): List<ApiTask>

    @GET("tasks/tomorrow")
    suspend fun getTomorrowTasks(@Query("tomorrow") tomorrow: String): List<ApiTask>

    @GET("tasks/inbox")
    suspend fun getInboxTasks(): List<ApiTask>

    @GET("lists/{listId}/tasks")
    suspend fun getListTasks(@Path("listId") listId: Int): List<ApiTask>

    @POST("tasks")
    suspend fun createTask(@Body payload: ApiTaskCreatePayload): ApiTask

    @PATCH("tasks/{taskId}")
    suspend fun updateTask(
        @Path("taskId") taskId: Int,
        @Body payload: ApiTaskUpdatePayload,
    ): ApiTask

    @DELETE("tasks/{taskId}")
    suspend fun deleteTask(@Path("taskId") taskId: Int)

    @POST("tasks/{taskId}/toggle")
    suspend fun toggleTask(@Path("taskId") taskId: Int): ApiTask

    @POST("tasks/reorder")
    suspend fun reorderTopLevelTasks(@Body payload: ApiTopLevelTaskReorderPayload): List<ApiTask>

    @POST("tasks/{taskId}/move")
    suspend fun moveTask(
        @Path("taskId") taskId: Int,
        @Body payload: ApiTaskMovePayload,
    ): ApiTaskMoveResult

    @POST("tasks/{taskId}/subtasks/reorder")
    suspend fun reorderSubtasks(
        @Path("taskId") taskId: Int,
        @Body payload: ApiSubtaskReorderPayload,
    ): ApiTask
}
