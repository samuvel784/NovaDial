package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merged_contact_mappings")
data class MergedContactMappingEntity(
    @PrimaryKey val suppressedId: String,
    val primaryId: String,
    val mergedAt: Long = System.currentTimeMillis()
)
