package com.openptt.data.remote.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

// ---------- Group DTOs ----------

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val avatar_url: String? = null,
    val owner_id: String? = null,
    val is_public: Boolean,
    val invite_code: String? = null,
    val max_members: Int,
    val created_at: String
)

@Serializable
data class ChannelDto(
    val id: String,
    val group_id: String,
    val name: String,
    val description: String? = null,
    val channel_type: String,
    val max_users: Int,
    val created_at: String
)

@Serializable
data class GroupDetailDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val avatar_url: String? = null,
    val owner_id: String? = null,
    val is_public: Boolean,
    val invite_code: String? = null,
    val max_members: Int,
    val created_at: String,
    val channels: List<ChannelDto>,
    val member_count: Long
)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val is_public: Boolean = true
)

@Serializable
data class CreateChannelRequest(
    val name: String,
    val description: String? = null,
    val channel_type: String = "voice",
    val max_users: Int = 50
)

@Serializable
data class JoinGroupRequest(val invite_code: String? = null)

// ---------- API Interface ----------

interface GroupApi {
    @GET("/api/v1/groups")
    suspend fun listGroups(): Response<List<GroupDto>>

    @POST("/api/v1/groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): Response<GroupDto>

    @GET("/api/v1/groups/{id}")
    suspend fun getGroup(@Path("id") id: String): Response<GroupDetailDto>

    @DELETE("/api/v1/groups/{id}")
    suspend fun deleteGroup(@Path("id") id: String): Response<Unit>

    @POST("/api/v1/groups/{id}/join")
    suspend fun joinGroup(@Path("id") id: String, @Body request: JoinGroupRequest): Response<Unit>

    @POST("/api/v1/groups/{id}/leave")
    suspend fun leaveGroup(@Path("id") id: String): Response<Unit>

    @GET("/api/v1/groups/{gid}/channels")
    suspend fun listChannels(@Path("gid") groupId: String): Response<List<ChannelDto>>

    @POST("/api/v1/groups/{gid}/channels")
    suspend fun createChannel(
        @Path("gid") groupId: String,
        @Body request: CreateChannelRequest
    ): Response<ChannelDto>

    @DELETE("/api/v1/groups/{gid}/channels/{id}")
    suspend fun deleteChannel(
        @Path("gid") groupId: String,
        @Path("id") channelId: String
    ): Response<Unit>
}
