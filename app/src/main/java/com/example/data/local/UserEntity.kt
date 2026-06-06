package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val email: String,
    val passwordPlain: String,
    val joinedDate: Long = System.currentTimeMillis(),
    val profileImageUri: String? = null,
    val status: String = "User Terverifikasi",
    val phone: String = "",
    val address: String = ""
)
