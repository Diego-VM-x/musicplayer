package com.larkplayer.core.common

/** Lightweight result type for repository operations. */
sealed interface Result<out T> {
    data class Success<out T>(val data: T) : Result<T>
    data class Error(val exception: Throwable, val message: String? = null) : Result<Nothing>
    data object Loading : Result<Nothing>
}

inline fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success)?.data
