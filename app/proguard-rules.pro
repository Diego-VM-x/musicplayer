# Keep Media3 / Room generated classes used via reflection
-keep class androidx.room.** { *; }
-keep class com.larkplayer.core.database.** { *; }
-dontwarn androidx.media3.**
# Gson deserializes these models by reflection; keep names and constructors.
-keep class com.larkplayer.feature.backup.** { *; }
