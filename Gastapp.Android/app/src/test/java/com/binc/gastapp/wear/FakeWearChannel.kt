package com.binc.gastapp.wear

/** Canal del reloj en memoria: anota lo que el telefono le mandaria por la Data Layer. */
class FakeWearChannel : WearChannel {
    val revoked = mutableListOf<String>()
    val data = mutableListOf<Pair<String, String>>()
    val replies = mutableListOf<Triple<String, String, String>>()

    override suspend fun notifyRevoked(deviceId: String): Boolean {
        revoked += deviceId
        return true
    }

    override suspend fun putData(path: String, json: String): Boolean {
        data += path to json
        return true
    }

    override suspend fun reply(nodeId: String, path: String, body: String) {
        replies += Triple(nodeId, path, body)
    }
}
