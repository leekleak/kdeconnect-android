/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2018 Simon Redman <simon@ergotech.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import android.util.Base64
import android.util.Base64OutputStream
import android.util.Log
import org.apache.commons.io.IOUtils
import java.io.ByteArrayOutputStream
import kotlin.text.Charsets.UTF_8
import androidx.core.net.toUri

object ContactsHelper {
    const val LOG_TAG: String = "ContactsHelper"

    /**
     * Lookup the name and photoID of a contact given a phone number
     */
    fun phoneNumberLookup(context: Context, number: String): Map<String, String> {
        val contactInfo: MutableMap<String, String> = HashMap()

        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val columns: Array<String> = arrayOf(
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.PHOTO_URI /*, PhoneLookup.TYPE
                  , PhoneLookup.LABEL
                  , PhoneLookup.ID */
        )
        try {
            context.contentResolver.query(uri, columns, null, null, null).use { cursor ->
                // Take the first match only
                if (cursor != null && cursor.moveToFirst()) {
                    var nameIndex = cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        contactInfo["name"] = cursor.getString(nameIndex)
                    }

                    nameIndex = cursor.getColumnIndex(PhoneLookup.PHOTO_URI)
                    if (nameIndex != -1) {
                        contactInfo["photoID"] = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return contactInfo
    }

    fun photoId64Encoded(context: Context, photoId: String): String {
        val photoUri = photoId.toUri()

        val encodedPhoto = ByteArrayOutputStream()
        try {
            context.contentResolver.openInputStream(photoUri).use { input ->
                Base64OutputStream(encodedPhoto, Base64.DEFAULT).use { output ->
                    IOUtils.copy(input, output, 1024)
                    return encodedPhoto.toString()
                }
            }
        } catch (ex: Exception) {
            Log.e(LOG_TAG, ex.toString())
            return ""
        }
    }

    /**
     * Return all the NAME_RAW_CONTACT_IDS which contribute an entry to a Contact in the database
     * 
     * 
     * If the user has, for example, joined several contacts, on the phone, the IDs returned will
     * be representative of the joined contact
     * 
     * 
     * See here: https://developer.android.com/reference/android/provider/ContactsContract.Contacts.html
     * for more information about the connection between contacts and raw contacts
     * 
     * @param context android.content.Context running the request
     * @return List of each NAME_RAW_CONTACT_ID in the Contacts database
     */
    fun getAllContactContactIDs(context: Context): MutableList<UID?> {
        val toReturn = ArrayList<UID?>()

        // Define the columns we want to read from the Contacts database
        val columns: Array<String> = arrayOf(
            ContactsContract.Contacts.LOOKUP_KEY
        )

        val contactsUri = ContactsContract.Contacts.CONTENT_URI
        context.contentResolver.query(contactsUri, columns, null, null, null)
            .use { contactsCursor ->
                if (contactsCursor != null && contactsCursor.moveToFirst()) {
                    do {
                        val contactID: UID?

                        val idIndex =
                            contactsCursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                        if (idIndex != -1) {
                            contactID = UID(contactsCursor.getString(idIndex))
                        } else {
                            // Something went wrong with this contact
                            // If you are experiencing this, please open a bug report indicating how you got here
                            Log.e(LOG_TAG, "Got a contact which does not have a LOOKUP_KEY")
                            continue
                        }

                        if (!toReturn.contains(contactID)) {
                            toReturn.add(contactID)
                        }
                    } while (contactsCursor.moveToNext())
                }
            }
        return toReturn
    }

    /**
     * Get VCards using serial database lookups. This is tragically slow, so call only when needed.
     * 
     * There is a faster API specified using ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI,
     * but there does not seem to be a way to figure out which ID resulted in which VCard using that API
     * 
     * @param context    android.content.Context running the request
     * @param ids        collection of uIDs to look up
     * @return Mapping of uIDs to the corresponding VCard
     */
    private fun getVCardsSlow(
        context: Context,
        ids: List<UID>
    ): MutableMap<UID, VCardBuilder> {
        val toReturn: MutableMap<UID, VCardBuilder> = HashMap()

        for (id in ids) {
            val lookupKey = id.toString()
            val vcardURI =
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)

            try {
                context.contentResolver.openInputStream(vcardURI).use { input ->
                    if (input == null) {
                        Log.w(
                            "Contacts",
                            "ContentResolver did not give us a stream for the VCard for uID $id"
                        )
                        continue
                    }
                    toReturn.put(id, VCardBuilder(IOUtils.toString(input, UTF_8)))
                }
            } catch (e: Exception) {
                // If you are experiencing this, please open a bug report indicating how you got here
                Log.e("Contacts", "Exception while fetching vcards", e)
            }
        }

        return toReturn
    }

    /**
     * Get the VCard for every specified raw contact ID
     * 
     * @param context android.content.Context running the request
     * @param ids     collection of raw contact IDs to look up
     * @return Mapping of raw contact IDs to the corresponding VCard
     */
    fun getVCardsForContactIDs(
        context: Context,
        ids: List<UID>
    ): MutableMap<UID, VCardBuilder> {
        return getVCardsSlow(context, ids)
    }

    /**
     * Get the last-modified timestamp for every contact in the database
     * 
     * @param context android.content.Context running the request
     * @return Mapping of contact uID to last-modified timestamp
     */
    fun getAllContactTimestamps(context: Context): MutableMap<UID, Long?> {
        val projection = arrayOf(UID.COLUMN, ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)

        val databaseValues = accessContactsDatabase(context, projection, null, null)

        val timestamps: MutableMap<UID, Long?> = HashMap()
        for (contactID in databaseValues.keys) {
            val data = databaseValues[contactID] ?: continue
            val timestamp = data[ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP]
            timestamps[contactID] = timestamp?.toLong()
        }

        return timestamps
    }

    /**
     * Get the last-modified timestamp for the specified contact
     * 
     * @param context   android.content.Context running the request
     * @param contactID Contact uID to read
     * @throws ContactNotFoundException If the given ID for some reason does not match a contact
     * @return          Last-modified timestamp of the contact
     */
    @Throws(ContactNotFoundException::class)
    fun getContactTimestamp(context: Context, contactID: UID): Long? {
        val projection = arrayOf(UID.COLUMN, ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)
        val selection = UID.COLUMN + " = ?"
        val selectionArgs = arrayOf(contactID.toString())

        val databaseValue = accessContactsDatabase(context, projection, selection, selectionArgs)

        if (databaseValue.isEmpty()) {
            throw ContactNotFoundException("Querying for contact with id $contactID returned no results.")
        }

        if (databaseValue.size != 1) {
            Log.w(
                LOG_TAG,
                "Received an improper number of return values from the database in getContactTimestamp: " + databaseValue.size
            )
        }

        val timestamp = databaseValue[contactID]?.get(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)?.toLong()

        return timestamp
    }

    /**
     * Return a mapping of contact IDs to a map of the requested data from the Contacts database.
     * 
     * @param context    android.content.Context running the request
     * @param projection List of column names to extract, defined in ContactsContract.Contacts. Must contain uID.COLUMN
     * @param selection  Parameterizable filter to use with the ContentResolver query. May be null.
     * @param selectionArgs Parameters for selection. May be null.
     * @return mapping of contact uIDs to desired values, which are a mapping of column names to the data contained there
     */
    private fun accessContactsDatabase(
        context: Context,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
    ): MutableMap<UID, MutableMap<String, String>> {
        val contactsUri = ContactsContract.Contacts.CONTENT_URI

        val toReturn = HashMap<UID, MutableMap<String, String>>()

        context.contentResolver.query(
            contactsUri,
            projection,
            selection,
            selectionArgs,
            null
        ).use { contactsCursor ->
            if (contactsCursor != null && contactsCursor.moveToFirst()) {
                do {
                    val requestedData: MutableMap<String, String> = HashMap()

                    val uIDIndex = contactsCursor.getColumnIndexOrThrow(UID.COLUMN)
                    val uID = UID(contactsCursor.getString(uIDIndex))

                    // For each column, collect the data from that column
                    for (column in projection) {
                        val index = contactsCursor.getColumnIndex(column)
                        if (index == -1) {
                            // This contact didn't have the requested column? Something is very wrong.
                            // If you are experiencing this, please open a bug report indicating how you got here
                            Log.e(LOG_TAG, "Got a contact which does not have a requested column")
                            continue
                        }
                        // Since we might be getting various kinds of data, Object is the best we can do
                        val data: String = contactsCursor.getString(index)

                        requestedData[column] = data
                    }

                    toReturn[uID] = requestedData
                } while (contactsCursor.moveToNext())
            }
        }
        return toReturn
    }

    /**
     * This is a cheap ripoff of com.android.vcard.VCardBuilder
     * 
     * 
     * Maybe in the future that library will be made public and we can switch to using that!
     * 
     * 
     * The main similarity is the usage of .toString() to produce the finalized VCard and the
     * usage of .appendLine(String, String) to add stuff to the vcard
     */
    class VCardBuilder internal constructor(vcard: String) {
        val vcardBody: StringBuilder

        /**
         * Take a partial vcard as a string and make a VCardBuilder
         */
        init {
            // Remove the end tag. We will add it back on in .toString()
            var vcard = vcard
            vcard = vcard.substring(0, vcard.indexOf(VCARD_END))

            vcardBody = StringBuilder(vcard)
        }

        /**
         * Appends one line with a given property name and value.
         */
        fun appendLine(propertyName: String?, rawValue: String?) {
            vcardBody.append(propertyName)
                .append(VCARD_DATA_SEPARATOR)
                .append(rawValue)
                .append("\n")
        }

        override fun toString(): String {
            return vcardBody.toString() + VCARD_END
        }

        companion object {
            const val VCARD_END: String = "END:VCARD" // Written to terminate the vcard
            const val VCARD_DATA_SEPARATOR: String = ":"
        }
    }

    /**
     * Essentially a typedef of the type used for a unique identifier
     */
    class UID(lookupKey: String) {
        /**
         * We use the LOOKUP_KEY column of the Contacts table as a unique ID, since that's what it's
         * for
         */
        val contactLookupKey: String = lookupKey

        override fun toString(): String {
            return this.contactLookupKey
        }

        override fun hashCode(): Int {
            return contactLookupKey.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (other is UID) {
                return contactLookupKey == other.contactLookupKey
            }
            return contactLookupKey == other
        }

        companion object {
            /**
             * Which Contacts column this uID is pulled from
             */
            const val COLUMN: String = ContactsContract.Contacts.LOOKUP_KEY
        }
    }

    /**
     * Exception to indicate that a specified contact was not found
     */
    class ContactNotFoundException : Exception {

        constructor(message: String?) : super(message)
    }
}
