/*
 * SPDX-FileCopyrightText: 2019 Simon Redman <simon@ergotech.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.helpers

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat

object TelephonyHelper {
    const val LOGGING_TAG: String = "TelephonyHelper"

    /**
     * Try to get the phone number currently active on the phone
     * 
     * Make sure that you have the READ_PHONE_STATE permission!
     * 
     * Note that entries of the returned list might return null if the phone number is not known by the device
     */
    @Throws(SecurityException::class)
    fun getAllPhoneNumbers(
        context: Context
    ): MutableList<LocalPhoneNumber> {
        val sm = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
        // Each subscription is a different SIM card
        val subscriptionManager = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
        if (subscriptionManager == null) {
            // I don't know why or when this happens...
            Log.w(LOGGING_TAG, "Could not get SubscriptionManager")
            return mutableListOf()
        }
        val subscriptionInfos = subscriptionManager.activeSubscriptionInfoList
        if (subscriptionInfos == null) {
            // This happens when there is no SIM card inserted
            Log.w(LOGGING_TAG, "Could not get SubscriptionInfos")
            return mutableListOf()
        }
        val phoneNumbers: MutableList<LocalPhoneNumber> = mutableListOf()
        for (info in subscriptionInfos) {
            val thisPhoneNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val number = sm?.getPhoneNumber(info.subscriptionId) ?: continue
                LocalPhoneNumber(number, info.subscriptionId)
            } else {
                LocalPhoneNumber(info.number, info.subscriptionId)
            }
            phoneNumbers.add(thisPhoneNumber)
        }
        return phoneNumbers
    }

    /**
     * Get the APN settings of the current APN for the given subscription ID
     * 
     * Note that this method is broken after Android 4.2 but starts working again "at some point"
     * After Android 4.2, *reading* APN permissions requires a system permission (WRITE_APN_SETTINGS)
     * Before this, no permission is required
     * At some point after, the permission is not required to read non-sensitive columns (which are the
     * only ones we need)
     * If anyone has a solution to this (which doesn't involve a vendor-sepecific XML), feel free to share!
     * 
     * Cobbled together from the [Android sources](https://android.googlesource.com/platform/packages/services/Mms/+/refs/heads/master/src/com/android/mms/service/ApnSettings.java)
     * and some StackOverflow Posts
     * [post 1](https://stackoverflow.com/a/18897139/3723163)
     * [post 2[(https://stackoverflow.com/a/7928751/3723163)
     * 
     * @param context Context of the requester
     * @param subscriptionId Subscription ID for which to get the preferred APN. Ignored for devices older than Lollypop
     * @return Null if the preferred APN can't be found or doesn't support MMS, otherwise an ApnSetting object
     */
    fun getPreferredApn(context: Context, subscriptionId: Int): ApnSetting? {
        val apnProjection = arrayOf<String?>(
            Telephony.Carriers.TYPE,
            Telephony.Carriers.MMSC,
            Telephony.Carriers.MMSPROXY,
            Telephony.Carriers.MMSPORT,
        )

        val telephonyCarriersUri = Telephony.Carriers.CONTENT_URI

        val telephonyCarriersPreferredApnUri =
            Uri.withAppendedPath(telephonyCarriersUri, "/preferapn/subId/$subscriptionId")

        try {
            context.contentResolver.query(
                telephonyCarriersPreferredApnUri,
                apnProjection,
                null,
                null,
                Telephony.Carriers.DEFAULT_SORT_ORDER
            ).use { cursor ->
                while (cursor != null && cursor.moveToNext()) {
                    val typeColumn = cursor.getColumnIndex(Telephony.Carriers.TYPE)
                    if (typeColumn == -1) continue
                    val type = cursor.getString(typeColumn)
                    if (!isValidApnType(type, APN_TYPE_MMS)) continue

                    val mmscColumn = cursor.getColumnIndex(Telephony.Carriers.MMSC)
                    val mmsProxyColumn = cursor.getColumnIndex(Telephony.Carriers.MMSPROXY)
                    if (mmscColumn == -1 || mmsProxyColumn == -1) continue
                    val apnBuilder = ApnSetting.Builder()
                        .setMmsc(Uri.parse(cursor.getString(mmscColumn)))
                        .setMmsProxyAddress(cursor.getString(mmsProxyColumn))

                    val maybeMmsProxyPortColumn = cursor.getColumnIndex(Telephony.Carriers.MMSPORT)
                    if (maybeMmsProxyPortColumn == -1) continue
                    val maybeMmsProxyPort = cursor.getString(maybeMmsProxyPortColumn)
                    try {
                        val mmsProxyPort = maybeMmsProxyPort.toInt()
                        apnBuilder.setMmsProxyPort(mmsProxyPort)
                    } catch (_: Exception) {
                        // Lots of APN settings have other values, very commonly something like "Not set"
                        // just cross your fingers and hope that the default in ApnSetting works...
                        // If someone finds some documentation which says what the default value should be,
                        // please share
                    }

                    return apnBuilder.build()
                }
            }
        } catch (e: Exception) {
            Log.e(LOGGING_TAG, "Error encountered while trying to read APNs", e)
        }

        return null
    }

    /**
     * APN types for data connections.  These are usage categories for an APN
     * entry.  One APN entry may support multiple APN types, eg, a single APN
     * may service regular internet traffic ("default") as well as MMS-specific
     * connections.
     * APN_TYPE_ALL is a special type to indicate that this APN entry can
     * service all data connections.
     * Copied from Android's internal source: https://android.googlesource.com/platform/frameworks/base/+/cd92588/telephony/java/com/android/internal/telephony/PhoneConstants.java
     */
    private const val APN_TYPE_ALL = "*"

    /** APN type for MMS traffic  */
    private const val APN_TYPE_MMS = "mms"

    /**
     * Copied directly from Android's source: https://android.googlesource.com/platform/packages/services/Mms/+/refs/heads/master/src/com/android/mms/service/ApnSettings.java
     * @param types Value of Telephony.Carriers.TYPE for the APN being interrogated
     * @param requestType Value which we would like to find in types
     * @return True if the APN supports the requested type, false otherwise
     */
    fun isValidApnType(types: String, requestType: String?): Boolean {
        // If APN type is unspecified, assume APN_TYPE_ALL.
        if (types.isEmpty()) {
            return true
        }
        for (type in types.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            var type = type
            type = type.trim { it <= ' ' }
            if (type == requestType || type == APN_TYPE_ALL) {
                return true
            }
        }
        return false
    }

    /**
     * Canonicalize a phone number by removing all (valid) non-digit characters
     * 
     * Should be equivalent to SmsHelper::canonicalizePhoneNumber in the C++ implementation
     * 
     * @param phoneNumber The phone number to canonicalize
     * @return The canonicalized version of the input phone number
     */
    fun canonicalizePhoneNumber(phoneNumber: String): String {
        var toReturn = phoneNumber
        toReturn = toReturn.replace(" ", "")
        toReturn = toReturn.replace("-", "")
        toReturn = toReturn.replace("(", "")
        toReturn = toReturn.replace(")", "")
        toReturn = toReturn.replace("+", "")
        toReturn = toReturn.replaceFirst("^0*".toRegex(), "")

        if (toReturn.isEmpty()) {
            // If we have stripped away everything, assume this is a special number (and already canonicalized)
            return phoneNumber
        }
        return toReturn
    }

    /**
     * Light copy of https://developer.android.com/reference/android/telephony/data/ApnSetting so
     * that we can support older API versions. Delete this when API 28 becomes our supported version.
     */
    class ApnSetting
    private constructor() {
        var mmsc: Uri? = null
            private set
        var mmsProxyAddressAsString: String? = null
            private set
        var mmsProxyPort: Int =
            80 // Default port should be 80 according to code comment in Android's ApnSettings.java
            private set

        class Builder {
            private val internalApnSetting: ApnSetting = ApnSetting()

            fun setMmsc(mmscUri: Uri?): Builder {
                internalApnSetting.mmsc = mmscUri
                return this
            }

            fun setMmsProxyAddress(mmsProxy: String?): Builder {
                internalApnSetting.mmsProxyAddressAsString = mmsProxy
                return this
            }

            fun setMmsProxyPort(mmsPort: Int): Builder {
                internalApnSetting.mmsProxyPort = mmsPort
                return this
            }

            fun build(): ApnSetting {
                return internalApnSetting
            }
        }
    }

    /**
     * Class representing a phone number which is assigned to the current device
     */
    class LocalPhoneNumber(
        /**
         * The phone number
         */
        val number: String,
        /**
         * The subscription ID to which this phone number belongs
         */
        val subscriptionID: Int
    ) {
        override fun toString(): String {
            return number
        }

        /**
         * Do some basic fuzzy matching on two phone numbers to determine whether they match
         * 
         * This is roughly equivalent to SmsHelper::isPhoneNumberMatch, but might produce more false negatives
         * 
         * @param potentialMatchingPhoneNumber The phone number to compare to this phone number
         * @return True if the phone numbers appear to be the same, false otherwise
         */
        fun isMatchingPhoneNumber(potentialMatchingPhoneNumber: String): Boolean {
            val mPhoneNumber = canonicalizePhoneNumber(number)
            val oPhoneNumber = canonicalizePhoneNumber(potentialMatchingPhoneNumber)

            if (mPhoneNumber.isEmpty() || oPhoneNumber.isEmpty()) {
                // The empty string is not a valid phone number so does not match anything
                return false
            }

            // To decide if a phone number matches:
            // 1. Are they similar lengths? If two numbers are very different, probably one is junk data and should be ignored
            // 2. Is one a superset of the other? Phone number digits get more specific the further towards the end of the string,
            //    so if one phone number ends with the other, it is probably just a more-complete version of the same thing
            val longerNumber =
                if (mPhoneNumber.length >= oPhoneNumber.length) mPhoneNumber else oPhoneNumber
            val shorterNumber =
                if (mPhoneNumber.length < oPhoneNumber.length) mPhoneNumber else oPhoneNumber

            // If the numbers are vastly different in length, assume they are not the same
            if (shorterNumber.length < 0.75 * longerNumber.length) {
                return false
            }

            val matchingPhoneNumber = longerNumber.endsWith(shorterNumber)

            return matchingPhoneNumber
        }
    }
}
