/*
 *  ---license-start
 *  eu-digital-green-certificates / dgca-verifier-app-android
 *  ---
 *  Copyright (C) 2021 T-Systems International GmbH and all other contributors
 *  ---
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  ---license-end
 *
 *  Created by osarapulov on 7/22/21 11:44 AM
 */

package si.vakcin.pct.android.verification

import dgca.verifier.app.decoder.cbor.GreenCertificateData

data class InnerVerificationResult(
    val noPublicKeysFound: Boolean = true,
    val certificateExpired: Boolean = false,
    val greenCertificateData: GreenCertificateData? = null,
    val isApplicableCode: Boolean = false,
    val base64EncodedKid: String? = null
) {
    fun isValid() =
        !noPublicKeysFound && !certificateExpired && greenCertificateData != null && isApplicableCode && base64EncodedKid?.isNotBlank() == true
}