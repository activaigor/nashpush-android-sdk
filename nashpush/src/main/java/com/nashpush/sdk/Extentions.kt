package com.nashpush.sdk

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject


fun JSONObject.getStringSafe(key: String): String? {
    return try {
        this.getString(key)
    } catch (e: JSONException) {
        null
    }
}

fun JSONObject.getIntSafe(key: String): Int? {
    return try {
        this.getInt(key)
    } catch (e: JSONException) {
        null
    }
}

fun JSONObject.getJSONObjectSafe(key: String): JSONObject? {
    return try {
        this.getJSONObject(key)
    } catch (e: JSONException) {
        null
    }
}

fun JSONArray.getJSONObjectSafe(pos: Int): JSONObject? {
    return try {
        this.getJSONObject(pos)
    } catch (e: JSONException) {
        null
    }
}