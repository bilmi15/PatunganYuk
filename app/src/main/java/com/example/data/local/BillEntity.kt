package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

data class Participant(
    val name: String,
    val amountToPay: Double,
    val isPaid: Boolean = false
)

data class BillMenuItem(
    val name: String,
    val price: Double,
    val qty: Int = 1,
    val orderedBy: String = "" // Under whose account or name is this menu item
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val totalAmount: Double,
    val date: Long = System.currentTimeMillis(),
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageUri: String? = null,
    val participants: List<Participant> = emptyList(),
    val menuItems: List<BillMenuItem> = emptyList(),
    val userId: Int = 0
)
