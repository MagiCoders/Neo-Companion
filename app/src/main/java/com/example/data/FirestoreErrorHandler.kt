package com.example.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

/** A Firestore call failed. Carries the error code only — never names, emails or document contents. */
class DataAccessException(val code: String, val operation: OperationType, cause: Throwable) :
    RuntimeException("Firestore ${operation.name.lowercase()} failed: $code", cause)

/**
 * Logs and rethrows a Firestore failure without personal data. User IDs inside the path are
 * masked, so logcat and crash reports can't be used to identify anyone.
 */
fun handleFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String?
): Nothing {
    val code = (error as? FirebaseFirestoreException)?.code?.name ?: error.javaClass.simpleName
    val maskedPath = path?.split('/')?.mapIndexed { i, part -> if (i % 2 == 1) "*" else part }?.joinToString("/")
    Log.w("FirestoreError", "op=${operationType.name.lowercase()} code=$code path=$maskedPath")
    throw DataAccessException(code, operationType, error)
}
