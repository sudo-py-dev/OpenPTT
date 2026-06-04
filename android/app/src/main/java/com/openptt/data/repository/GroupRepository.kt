package com.openptt.data.repository

import com.openptt.data.remote.api.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for group and channel operations.
 */
@Singleton
class GroupRepository @Inject constructor(
    private val groupApi: GroupApi
) {
    suspend fun listGroups(): Result<List<GroupDto>> {
        return try {
            val response = groupApi.listGroups()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to load groups: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroup(id: String): Result<GroupDetailDto> {
        return try {
            val response = groupApi.getGroup(id)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load group: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGroup(name: String, description: String?, isPublic: Boolean): Result<GroupDto> {
        return try {
            val response = groupApi.createGroup(
                CreateGroupRequest(name, description, isPublic)
            )
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to create group: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinGroup(id: String, inviteCode: String? = null): Result<Unit> {
        return try {
            val response = groupApi.joinGroup(id, JoinGroupRequest(inviteCode))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to join group: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(id: String): Result<Unit> {
        return try {
            val response = groupApi.leaveGroup(id)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to leave group: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listChannels(groupId: String): Result<List<ChannelDto>> {
        return try {
            val response = groupApi.listChannels(groupId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to load channels: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createChannel(groupId: String, name: String, description: String?, channelType: String, maxUsers: Int): Result<ChannelDto> {
        return try {
            val response = groupApi.createChannel(
                groupId,
                CreateChannelRequest(name, description, channelType, maxUsers)
            )
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to create channel: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
