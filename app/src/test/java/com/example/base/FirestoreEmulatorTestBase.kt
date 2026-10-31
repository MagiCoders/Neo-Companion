package com.example.base

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
abstract class FirestoreEmulatorTestBase {

  protected lateinit var context: Context
  protected lateinit var firestore: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected lateinit var dbId: String

  private val projectId = "demo-test"
  private val firestoreHost = "127.0.0.1"
  private val firestorePort = 8085
  private val authHost = "127.0.0.1"
  private val authPort = 9099

  @Before
  open fun setUpEmulator() {
    context = ApplicationProvider.getApplicationContext()
    dbId = context.getString(R.string.firestore_database_id)

    FirebaseApp.clearInstancesForTest()
    val options =
      FirebaseOptions.Builder()
        .setProjectId(projectId)
        .setApplicationId("1:1234567890:android:abcdef")
        .setApiKey("fake-api-key-for-emulator")
        .build()
    FirebaseApp.initializeApp(context, options)

    auth = FirebaseAuth.getInstance()
    auth.useEmulator(authHost, authPort)

    firestore = FirebaseFirestore.getInstance(dbId)
    firestore.useEmulator(firestoreHost, firestorePort)

    val settings =
      FirebaseFirestoreSettings.Builder()
        .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
        .build()
    firestore.firestoreSettings = settings

    clearEmulatorData()
  }

  @After
  open fun tearDownEmulator() {
    try {
      awaitTask(firestore.terminate())
    } catch (_: Exception) {}
    FirebaseApp.clearInstancesForTest()
  }

  protected fun signInTestUser(
    email: String = "test@example.com",
    password: String = "password123",
  ): String {
    val result =
      try {
        awaitTask(auth.createUserWithEmailAndPassword(email, password))
      } catch (e: Exception) {
        awaitTask(auth.signInWithEmailAndPassword(email, password))
      }
    return result.user!!.uid
  }

  protected fun <T> awaitTask(task: Task<T>, timeoutSeconds: Long = 10): T {
    val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds)
    while (!task.isComplete) {
      if (System.currentTimeMillis() > deadline) {
        throw TimeoutException("Task timed out after $timeoutSeconds seconds")
      }
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(10)
    }
    if (task.isCanceled) throw RuntimeException("Task was canceled")
    task.exception?.let { throw it }
    return task.result
  }

  protected fun clearEmulatorData() {
    val encodedDbId = java.net.URLEncoder.encode(dbId, "UTF-8")
    deleteUrl(
      "http://$firestoreHost:$firestorePort/emulator/v1/projects/$projectId/databases/$encodedDbId/documents"
    )
    deleteUrl("http://$authHost:$authPort/emulator/v1/projects/$projectId/accounts")
  }

  private fun deleteUrl(urlString: String) {
    val conn = URL(urlString).openConnection() as HttpURLConnection
    conn.requestMethod = "DELETE"
    conn.connectTimeout = 5000
    conn.readTimeout = 30000
    try {
      conn.responseCode
    } finally {
      conn.disconnect()
    }
  }
}
