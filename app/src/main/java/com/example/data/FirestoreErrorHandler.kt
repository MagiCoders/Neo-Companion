package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

fun handleFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String?
): Nothing {
    val auth = try {
        FirebaseAuth.getInstance()
    } catch (_: Throwable) {
        null
    }
    val user = auth?.currentUser

    val providerInfoArray = JSONArray()
    user?.providerData?.forEach { profile ->
        providerInfoArray.put(
            JSONObject().apply {
                put("providerId", profile.providerId)
                put("displayName", profile.displayName ?: JSONObject.NULL)
                put("email", profile.email ?: JSONObject.NULL)
                put("photoUrl", profile.photoUrl?.toString() ?: JSONObject.NULL)
            }
        )
    }

    val authInfo = JSONObject().apply {
        put("userId", user?.uid ?: JSONObject.NULL)
        put("email", user?.email ?: JSONObject.NULL)
        put("emailVerified", user?.isEmailVerified ?: JSONObject.NULL)
        put("isAnonymous", user?.isAnonymous ?: JSONObject.NULL)
        put("tenantId", user?.tenantId ?: JSONObject.NULL)
        put("providerInfo", providerInfoArray)
    }

    val errInfo = JSONObject().apply {
        put("error", error.message ?: error.toString())
        put("operationType", operationType.name.lowercase())
        put("path", path ?: JSONObject.NULL)
        put("authInfo", authInfo)
    }

    val jsonString = errInfo.toString()
    Log.e("FirestoreError", jsonString, error)
    throw RuntimeException(jsonString, error)
}
