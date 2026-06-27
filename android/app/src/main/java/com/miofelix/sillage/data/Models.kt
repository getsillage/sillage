package com.miofelix.sillage.data

data class Account(
    val id: String,
    val username: String,
    val displayName: String,
)

data class AuthSession(
    val account: Account,
    val accessToken: String,
    val expiresAt: String,
)

data class Memo(
    val id: String,
    val content: String,
    val entryDate: String,
    val version: Long,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

class ApiException(message: String) : Exception(message)
