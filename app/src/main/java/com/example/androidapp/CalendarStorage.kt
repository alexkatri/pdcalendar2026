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

    private fun getUserId(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }
    }

    fun initAuth(onAuthComplete: () -> Unit) {
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Anonymous auth successful: ${auth.currentUser?.uid}")
                        }
                        onAuthComplete()
                    }
            } else {
                onAuthComplete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auth init exception", e)
            onAuthComplete()
        }
    }

    fun listenToEvents(
        context: Context,
        onEventsUpdated: (List<CalendarEvent>) -> Unit
    ) {
        val uid = getUserId()
        if (uid != null) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users")
                    .document(uid)
                    .collection("events")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Firestore listen error", error)
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
                return
            } catch (e: Exception) {
                Log.e(TAG, "Firestore not initialized or available", e)
            }
        }
        onEventsUpdated(loadLocalEvents(context))
    }

    fun saveEvent(context: Context, event: CalendarEvent) {
        saveLocalEvent(context, event)
        val uid = getUserId()
        if (uid != null) {
            try {
                val db = FirebaseFirestore.getInstance()
                val data = hashMapOf(
                    "title" to event.title,
                    "date" to event.date.toString(),
                    "hour" to event.hour,
                    "color" to event.color.toArgb()
                )
                db.collection("users")
                    .document(uid)
                    .collection("events")
                    .document(event.id)
                    .set(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully synced event to Firestore: ${event.title}")
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Failed to sync event to Firestore", error)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore save error", e)
            }
        } else {
            Log.w(TAG, "User ID is null; auth may not be complete yet.")
        }
    }

    fun deleteEvent(context: Context, eventId: String) {
        deleteLocalEvent(context, eventId)
        val uid = getUserId()
        if (uid != null) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users")
                    .document(uid)
                    .collection("events")
                    .document(eventId)
                    .delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully deleted event from Firestore: $eventId")
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Failed to delete event from Firestore", error)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore delete error", e)
            }
        }
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
