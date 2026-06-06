package com.example.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromParticipantList(list: List<Participant>?): String? {
        if (list == null) return null
        // Encode each participant as 'name:amount:isPaid' and join them with '|'
        // Escape special chars if name contains it
        return list.joinToString("###") { participant ->
            val escapedName = participant.name.replace(":", "\\:").replace("#", "\\#")
            "$escapedName:::${participant.amountToPay}:::${participant.isPaid}"
        }
    }

    @TypeConverter
    fun toParticipantList(value: String?): List<Participant>? {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split("###").mapNotNull {
            val parts = it.split(":::")
            if (parts.size >= 3) {
                val unescapedName = parts[0].replace("\\:", ":").replace("\\#", "#")
                Participant(
                    name = unescapedName,
                    amountToPay = parts[1].toDoubleOrNull() ?: 0.0,
                    isPaid = parts[2].toBoolean()
                )
            } else {
                null
            }
        }
    }

    @TypeConverter
    fun fromMenuItemList(list: List<BillMenuItem>?): String? {
        if (list == null) return null
        return list.joinToString("###") { item ->
            val escapedName = item.name.replace(":", "\\:").replace("#", "\\#")
            val escapedOrderedBy = item.orderedBy.replace(":", "\\:").replace("#", "\\#")
            "$escapedName:::${item.price}:::${item.qty}:::$escapedOrderedBy"
        }
    }

    @TypeConverter
    fun toMenuItemList(value: String?): List<BillMenuItem>? {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split("###").mapNotNull {
            val parts = it.split(":::")
            if (parts.size >= 4) {
                val unescapedName = parts[0].replace("\\:", ":").replace("\\#", "#")
                val unescapedOrderedBy = parts[3].replace("\\:", ":").replace("\\#", "#")
                BillMenuItem(
                    name = unescapedName,
                    price = parts[1].toDoubleOrNull() ?: 0.0,
                    qty = parts[2].toIntOrNull() ?: 1,
                    orderedBy = unescapedOrderedBy
                )
            } else {
                null
            }
        }
    }
}
