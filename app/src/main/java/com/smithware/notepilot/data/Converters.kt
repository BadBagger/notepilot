package com.smithware.notepilot.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun toCaptureType(value: String): CaptureType = CaptureType.valueOf(value)
    @TypeConverter fun fromCaptureType(value: CaptureType): String = value.name
    @TypeConverter fun toCaptureSource(value: String): CaptureSource = CaptureSource.valueOf(value)
    @TypeConverter fun fromCaptureSource(value: CaptureSource): String = value.name
    @TypeConverter fun toSection(value: String): Section = Section.valueOf(value)
    @TypeConverter fun fromSection(value: Section): String = value.name
}
