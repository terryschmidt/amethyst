package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.tagSearch
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.service.nip19.Nip19.nip19regex

open class BaseTextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun mentions() = taggedUsers()
    open fun replyTos() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }

    private var citedUsersCache: Set<HexKey>? = null

    fun citedUsers(): Set<HexKey> {
        citedUsersCache?.let { return it }

        val matcher = tagSearch.matcher(content)
        val returningList = mutableSetOf<String>()
        while (matcher.find()) {
            try {
                val tag = matcher.group(1)?.let { tags[it.toInt()] }
                if (tag != null && tag.size > 1 && tag[0] == "p") {
                    returningList.add(tag[1])
                }
            } catch (e: Exception) {
            }
        }

        val matcher2 = nip19regex.matcher(content)
        while (matcher2.find()) {
            val uriScheme = matcher2.group(1) // nostr:
            val type = matcher2.group(2) // npub1
            val key = matcher2.group(3) // bech32
            val additionalChars = matcher2.group(4) // additional chars

            val parsed = Nip19.parseComponents(uriScheme, type, key, additionalChars)

            if (parsed != null) {
                try {
                    val tag = tags.firstOrNull { it.size > 1 && it[1] == parsed.hex }

                    if (tag != null && tag[0] == "p") {
                        returningList.add(tag[1])
                    }
                } catch (e: Exception) {
                }
            }
        }

        citedUsersCache = returningList
        return returningList
    }

    fun findCitations(): Set<String> {
        var citations = mutableSetOf<String>()
        // Removes citations from replies:
        val matcher = tagSearch.matcher(content)
        while (matcher.find()) {
            try {
                val tag = matcher.group(1)?.let { tags[it.toInt()] }
                if (tag != null && tag.size > 1 && tag[0] == "e") {
                    citations.add(tag[1])
                }
                if (tag != null && tag.size > 1 && tag[0] == "a") {
                    citations.add(tag[1])
                }
            } catch (e: Exception) {
            }
        }

        val matcher2 = nip19regex.matcher(content)
        while (matcher2.find()) {
            val uriScheme = matcher2.group(1) // nostr:
            val type = matcher2.group(2) // npub1
            val key = matcher2.group(3) // bech32
            val additionalChars = matcher2.group(4) // additional chars

            val parsed = Nip19.parseComponents(uriScheme, type, key, additionalChars)

            if (parsed != null) {
                if (content.contains("Testing event")) {
                    println("AAAA $key")
                }

                try {
                    val tag = tags.firstOrNull { it.size > 1 && it[1] == parsed.hex }

                    if (tag != null && tag[0] == "e") {
                        citations.add(tag[1])
                    }
                    if (tag != null && tag[0] == "a") {
                        citations.add(tag[1])
                    }
                } catch (e: Exception) {
                }
            }
        }

        return citations
    }

    fun tagsWithoutCitations(): List<String> {
        val repliesTo = replyTos()
        val tagAddresses = taggedAddresses().map { it.toTag() }
        if (repliesTo.isEmpty() && tagAddresses.isEmpty()) return emptyList()

        val citations = findCitations()

        return if (citations.isEmpty()) {
            repliesTo + tagAddresses
        } else {
            repliesTo.filter { it !in citations }
        }
    }
}
