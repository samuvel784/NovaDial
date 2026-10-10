package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.BlockAction
import com.example.data.model.CallType
import com.example.data.model.SpamCallerCategory

class Converters {
    @TypeConverter
    fun fromSpamCallerCategory(value: SpamCallerCategory?): String? = value?.name

    @TypeConverter
    fun toSpamCallerCategory(value: String?): SpamCallerCategory? = value?.let {
        try {
            SpamCallerCategory.valueOf(it)
        } catch (e: Exception) {
            SpamCallerCategory.SPAM_OTHER
        }
    }

    @TypeConverter
    fun fromBlockAction(value: BlockAction?): String? = value?.name

    @TypeConverter
    fun toBlockAction(value: String?): BlockAction? = value?.let {
        try {
            BlockAction.valueOf(it)
        } catch (e: Exception) {
            BlockAction.AUTO_BLOCK
        }
    }

    @TypeConverter
    fun fromCallType(value: CallType?): String? = value?.name

    @TypeConverter
    fun toCallType(value: String?): CallType? = value?.let {
        try {
            CallType.valueOf(it)
        } catch (e: Exception) {
            CallType.INCOMING
        }
    }
}
