package com.chorequest.utils

/**
 * A generic sealed class to represent the result of an operation
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    data object Loading : Result<Nothing>()
    
    val isLoading: Boolean
        get() = this is Loading
        
    val isSuccess: Boolean
        get() = this is Success
        
    val isError: Boolean
        get() = this is Error
}

/**
 * Helper function to handle result
 */
inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        action(data)
    }
    return this
}

inline fun <T> Result<T>.onError(action: (String) -> Unit): Result<T> {
    if (this is Result.Error) {
        action(message)
    }
    return this
}

inline fun <T> Result<T>.onLoading(action: () -> Unit): Result<T> {
    if (this is Result.Loading) {
        action()
    }
    return this
}
