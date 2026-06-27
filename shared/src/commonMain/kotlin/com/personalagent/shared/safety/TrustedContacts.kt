// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.shared.safety

import com.personalagent.shared.model.Ids
import kotlinx.serialization.Serializable

/**
 * 🔒 CRISIS-CRITICAL (Step 7). 🔒
 *
 * A person the user has **chosen in advance** as someone they might want help
 * reaching during a hard moment. The user adds these themselves with explicit
 * consent captured up front — the app never derives them from contacts, messages,
 * or anywhere else, and never reaches out to them on its own.
 *
 * [consentAcknowledgedAtMillis] records that the user ticked the up-front consent
 * box when adding this person; it is proof-of-consent, not a tracking field.
 */
@Serializable
data class TrustedContact(
    val id: String,
    val name: String,
    val phone: String? = null,
    val relationship: String? = null,
    val consentAcknowledgedAtMillis: Long,
) {
    companion object {
        fun create(
            name: String,
            phone: String?,
            relationship: String?,
            nowMillis: Long,
        ): TrustedContact = TrustedContact(
            id = Ids.next(nowMillis),
            name = name.trim(),
            phone = phone?.trim()?.ifBlank { null },
            relationship = relationship?.trim()?.ifBlank { null },
            consentAcknowledgedAtMillis = nowMillis,
        )
    }
}

/**
 * Persistence contract for the user's trusted contacts. Owned by the sibling; this
 * is the agreed contract the UIs depend on (reconcile with the authoritative
 * `feat/step7-shared` version at merge).
 *
 * Implementations persist through the **encrypted** [com.personalagent.shared.store.KeyValueStorage]
 * like every other piece of user data.
 */
interface TrustedContactsStore {
    fun all(): List<TrustedContact>
    fun add(contact: TrustedContact)
    fun remove(id: String)
}
