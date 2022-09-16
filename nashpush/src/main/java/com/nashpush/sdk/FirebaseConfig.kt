package com.nashpush.sdk

data class FirebaseConfig(
    val senderId: String,
    val projectId: String,
    val appId: String,
    val apiKey: String
) {

    override fun equals(other: Any?): Boolean {
        return other is FirebaseConfig
                && this.senderId == other.senderId
                && this.projectId == other.projectId
                && this.appId == other.appId
                && this.apiKey == other.apiKey
    }

    override fun hashCode(): Int {
        return senderId.hashCode() + projectId.hashCode() + appId.hashCode() + apiKey.hashCode()
    }
}
