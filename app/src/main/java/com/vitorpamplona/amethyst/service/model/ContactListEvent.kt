package com.vitorpamplona.amethyst.service.model

import android.util.Log
import com.google.gson.reflect.TypeToken
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.decodePublicKey
import com.vitorpamplona.amethyst.model.toHexKey
import java.util.Date
import nostr.postr.Utils

data class Contact(val pubKeyHex: String, val relayUri: String?)

class ContactListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
): Event(id, pubKey, createdAt, kind, tags, content, sig) {
    // This function is only used by the user logged in
    // But it is used all the time.
    val verifiedFollowKeySet: Set<HexKey> by lazy {
        tags.filter { it[0] == "p" }.mapNotNull {
            it.getOrNull(1)?.let { unverifiedHex: String ->
                decodePublicKey(unverifiedHex).toHexKey()
            }
        }.toSet()
    }

    val verifiedFollowKeySetAndMe: Set<HexKey> by lazy {
        verifiedFollowKeySet + pubKey
    }

    fun unverifiedFollowKeySet() = tags.filter { it[0] == "p" }.mapNotNull { it.getOrNull(1) }

    fun follows() = tags.filter { it[0] == "p" }.mapNotNull {
        try {
            Contact(decodePublicKey(it[1]).toHexKey(), it.getOrNull(2))
        } catch (e: Exception) {
            Log.w("ContactListEvent", "Can't parse tags as a follows: ${it[1]}", e)
            null
        }
    }

    fun relays(): Map<String, ReadWrite>? = try {
        if (content.isNotEmpty())
            gson.fromJson(content, object: TypeToken<Map<String, ReadWrite>>() {}.type) as Map<String, ReadWrite>
        else
            null
    } catch (e: Exception) {
        Log.w("ContactListEvent", "Can't parse content as relay lists: $content", e)
        null
    }

    companion object {
        const val kind = 3

        fun create(follows: List<Contact>, relayUse: Map<String, ReadWrite>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ContactListEvent {
            val content = if (relayUse != null)
                gson.toJson(relayUse)
            else
                ""
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = follows.map {
                if (it.relayUri != null)
                    listOf("p", it.pubKeyHex, it.relayUri)
                else
                    listOf("p", it.pubKeyHex)
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return ContactListEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }

    data class ReadWrite(val read: Boolean, val write: Boolean)
}