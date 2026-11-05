package com.example.androidapp

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object CalendarStorage {
    private const val TAG = "CalendarStorage"
    private const val PREFS_NAME = "weekly_calendar_storage"
    private const val KEY_EVENTS = "calendar_events_json"
    private const val COLLECTION_NAME = "calendar_events"

    private fun getUserId(onUidReady: (String) -> Unit, onError: () -> Unit = {}) {
        try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            if (user != null) {
                onUidReady(user.uid)
            } else {
                auth.signInAnonymously()
                    .addOnCompleteListener { task ->
                        val uid = auth.currentUser?.uid
                        if (task.isSuccessful && uid != null) {
                            Log.d(TAG, "Signed in anonymously: $uid")
                            onUidReady(uid)
                        } else {
                            Log.e(TAG, "Anonymous sign-in failed: ${task.exception?.message}", task.exception)
                            onError()
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseAuth exception: ${e.message}", e)
            onError()
        }
    }

    fun listenToEvents(
        context: Context,
        onEventsUpdated: (List<CalendarEvent>) -> Unit
    ) {
        getUserId(
            onUidReady = { uid ->
                try {
                    val db = FirebaseFirestore.getInstance()
                    db.collection(COLLECTION_NAME)
                        .whereEqualTo("userId", uid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e(TAG, "Firestore listen error: ${error.message}", error)
                                onEventsUpdated(loadLocalEvents(context))
                                return@addSnapshotListener
                            }

                            if (snapshot != null) {
                                val events = snapshot.documents.mapNotNull { doc ->
                                    try {
                                        val title = doc.getString("title") ?: return@mapNotNull null
                                        val dateStr = doc.getString("date") ?: return@mapNotNull null
                                        val hour = doc.getLong("hour")?.toInt() ?: return@mapNotNull null
                                        val colorInt = doc.getLong("color")?.toInt() ?: return@mapNotNull null

                                        CalendarEvent(
                                            id = doc.id,
                                            title = title,
                                            date = LocalDate.parse(dateStr),
                                            hour = hour,
                                            color = Color(colorInt)
                                        )
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                saveLocalEvents(context, events)
                                onEventsUpdated(events)
                            }
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore not available, loading local events", e)
                    onEventsUpdated(loadLocalEvents(context))
                }
            },
            onError = {
                Log.w(TAG, "Auth unavailable, loading local events")
                onEventsUpdated(loadLocalEvents(context))
            }
        )
    }

    fun saveEvent(context: Context, event: CalendarEvent) {
        saveLocalEvent(context, event)
        getUserId(
            onUidReady = { uid ->
                try {
                    val db = FirebaseFirestore.getInstance()
                    val data = hashMapOf(
                        "userId" to uid,
                        "title" to event.title,
                        "date" to event.date.toString(),
                        "hour" to event.hour,
                        "color" to event.color.toArgb()
                    )
                    db.collection(COLLECTION_NAME)
                        .document(event.id)
                        .set(data)
                        .addOnSuccessListener {
                            Log.d(TAG, "Successfully synced event to Firestore: ${event.title}")
                        }
                        .addOnFailureListener { error ->
                            Log.e(TAG, "Failed to sync event to Firestore: ${error.message}", error)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore save error", e)
                }
            }
        )
    }

    fun deleteEvent(context: Context, eventId: String) {
        deleteLocalEvent(context, eventId)
        getUserId(
            onUidReady = { _ ->
                try {
                    val db = FirebaseFirestore.getInstance()
                    db.collection(COLLECTION_NAME)
                        .document(eventId)
                        .delete()
                        .addOnSuccessListener {
                            Log.d(TAG, "Successfully deleted event from Firestore: $eventId")
                        }
                        .addOnFailureListener { error ->
                            Log.e(TAG, "Failed to delete event from Firestore: ${error.message}", error)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore delete error", e)
                }
            }
        )
    }

    private fun saveLocalEvent(context: Context, event: CalendarEvent) {
        val current = loadLocalEvents(context).toMutableList()
        val index = current.indexOfFirst { it.id == event.id }
        if (index != -1) {
            current[index] = event
        } else {
            current.add(event)
        }
        saveLocalEvents(context, current)
    }

    private fun deleteLocalEvent(context: Context, eventId: String) {
        val current = loadLocalEvents(context).toMutableList()
        current.removeAll { it.id == eventId }
        saveLocalEvents(context, current)
    }

    private fun saveLocalEvents(context: Context, events: List<CalendarEvent>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            events.forEach { event ->
                val jsonObject = JSONObject().apply {
                    put("id", event.id)
                    put("title", event.title)
                    put("date", event.date.toString())
                    put("hour", event.hour)
                    put("color", event.color.toArgb())
                }
                jsonArray.put(jsonObject)
            }
            prefs?.edit()?.putString(KEY_EVENTS, jsonArray.toString())?.apply()
        } catch (_: Exception) {}
    }

    fun loadLocalEvents(context: Context): List<CalendarEvent> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs?.getString(KEY_EVENTS, null) ?: return emptyList()

            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<CalendarEvent>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    CalendarEvent(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        date = LocalDate.parse(obj.getString("date")),
                        hour = obj.getInt("hour"),
                        color = Color(obj.getInt("color"))
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
