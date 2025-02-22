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
 *  Created by mykhailo.nester on 4/24/21 2:54 PM
 */

package si.vakcin.pct.android.verification

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import si.vakcin.pct.android.data.VerifierRepository
import si.vakcin.pct.android.model.CertificateModel
import si.vakcin.pct.android.model.toCertificateModel
import dgca.verifier.app.decoder.base45.Base45Service
import dgca.verifier.app.decoder.cbor.CborService
import dgca.verifier.app.decoder.cbor.GreenCertificateData
import dgca.verifier.app.decoder.compression.CompressorService
import dgca.verifier.app.decoder.cose.CoseService
import dgca.verifier.app.decoder.cose.CryptoService
import dgca.verifier.app.decoder.model.GreenCertificate
import dgca.verifier.app.decoder.model.RecoveryVerificationResult
import dgca.verifier.app.decoder.model.TestVerificationResult
import dgca.verifier.app.decoder.model.VerificationResult
import dgca.verifier.app.decoder.prefixvalidation.PrefixValidationService
import dgca.verifier.app.decoder.schema.SchemaValidator
import dgca.verifier.app.decoder.toBase64
import dgca.verifier.app.engine.*
import dgca.verifier.app.engine.data.CertificateType
import dgca.verifier.app.engine.data.ExternalParameter
import dgca.verifier.app.engine.data.source.valuesets.ValueSetsRepository
import dgca.verifier.app.engine.domain.rules.GetRulesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.cert.X509Certificate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*
import javax.inject.Inject

enum class GeneralVerificationResult {
    SUCCESS, FAILED, RULES_VALIDATION_FAILED
}

fun VerificationData.getGeneralResult(): GeneralVerificationResult {
    return when {
        verificationResult?.isValid() == true && innerVerificationResult.isValid() -> GeneralVerificationResult.SUCCESS
        verificationResult?.isTestWithPositiveResult() == true -> GeneralVerificationResult.FAILED
        verificationResult?.rulesValidationFailed == true -> GeneralVerificationResult.RULES_VALIDATION_FAILED
        else -> GeneralVerificationResult.FAILED
    }
}

data class VerificationData(
    val verificationResult: VerificationResult?,
    val innerVerificationResult: InnerVerificationResult,
    val certificateModel: CertificateModel?
)

@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val prefixValidationService: PrefixValidationService,
    private val base45Service: Base45Service,
    private val compressorService: CompressorService,
    private val cryptoService: CryptoService,
    private val coseService: CoseService,
    private val schemaValidator: SchemaValidator,
    private val cborService: CborService,
    private val verifierRepository: VerifierRepository,
    private val engine: CertLogicEngine,
    private val getRulesUseCase: GetRulesUseCase,
    private val valueSetsRepository: ValueSetsRepository
) : ViewModel() {

    private val _verificationData = MutableLiveData<VerificationData>()
    val verificationData: LiveData<VerificationData> = _verificationData

    private val _verificationError = MutableLiveData<VerificationError>()
    val verificationError: LiveData<VerificationError> = _verificationError

    private val _validationResults = MutableLiveData<List<ValidationResult>>()
    val validationResults: LiveData<List<ValidationResult>> = _validationResults

    private val _inProgress = MutableLiveData<Boolean>()
    val inProgress: LiveData<Boolean> = _inProgress

    fun init(qrCodeText: String, countryIsoCode: String) {
        decode(qrCodeText, countryIsoCode)
    }

    private fun decode(code: String, countryIsoCode: String) {
        viewModelScope.launch {
            _inProgress.value = true

            val verificationResult = VerificationResult()
            var innerVerificationResult: InnerVerificationResult

            withContext(Dispatchers.IO) {

                innerVerificationResult = validateCertificate(code, verificationResult)

                if (verificationResult.isValid() && innerVerificationResult.base64EncodedKid?.isNotBlank() == true) {
                    innerVerificationResult.greenCertificateData?.validateRules(
                        verificationResult,
                        countryIsoCode,
                        innerVerificationResult.base64EncodedKid!!
                    )
                }

            }

            verificationResult.fetchError(innerVerificationResult)
                ?.apply { _verificationError.value = this }

            _inProgress.value = false
            val certificateModel: CertificateModel? =
                innerVerificationResult.greenCertificateData?.greenCertificate?.toCertificateModel()
            _verificationData.value = VerificationData(
                if (innerVerificationResult.isApplicableCode) verificationResult else null,
                innerVerificationResult,
                certificateModel
            )
        }
    }

    private suspend fun validateCertificate(
        code: String,
        verificationResult: VerificationResult
    ): InnerVerificationResult {
        var greenCertificateData: GreenCertificateData? = null
        var isApplicableCode = false

        val plainInput = prefixValidationService.decode(code, verificationResult)
        val compressedCose = base45Service.decode(plainInput, verificationResult)
        val cose: ByteArray? = compressorService.decode(compressedCose, verificationResult)

        if (cose == null) {
            Timber.d("Verification failed: Too many bytes read")
            return InnerVerificationResult(
                greenCertificateData = greenCertificateData,
                isApplicableCode = isApplicableCode
            )
        }

        val coseData = coseService.decode(cose, verificationResult)
        if (coseData == null) {
            Timber.d("Verification failed: COSE not decoded")
            return InnerVerificationResult(
                greenCertificateData = greenCertificateData,
                isApplicableCode = isApplicableCode
            )
        }

        val kid = coseData.kid
        if (kid == null) {
            Timber.d("Verification failed: cannot extract kid from COSE")
            return InnerVerificationResult(
                greenCertificateData = greenCertificateData,
                isApplicableCode = isApplicableCode
            )
        }

        isApplicableCode = true

        schemaValidator.validate(coseData.cbor, verificationResult)
        greenCertificateData = cborService.decodeData(coseData.cbor, verificationResult)
        validateCertData(greenCertificateData?.greenCertificate, verificationResult)

        val base64EncodedKid = kid.toBase64()
        val certificates = verifierRepository.getCertificatesBy(base64EncodedKid)
        if (certificates.isEmpty()) {
            Timber.d("Verification failed: failed to load certificate")
            return InnerVerificationResult(
                greenCertificateData = greenCertificateData,
                isApplicableCode = isApplicableCode,
                base64EncodedKid = base64EncodedKid
            )
        }
        val noPublicKeysFound = false
        var certificateExpired = false
        certificates.forEach { innerCertificate ->
            cryptoService.validate(
                cose,
                innerCertificate,
                verificationResult,
                greenCertificateData?.greenCertificate?.getType()
                    ?: dgca.verifier.app.decoder.model.CertificateType.UNKNOWN
            )
            if (verificationResult.coseVerified) {
                val expirationTime: ZonedDateTime? =
                    if (innerCertificate is X509Certificate) innerCertificate.notAfter.toInstant()
                        .atZone(UTC_ZONE_ID) else null
                val currentTime: ZonedDateTime =
                    ZonedDateTime.now().withZoneSameInstant(UTC_ZONE_ID)
                if (expirationTime != null && currentTime.isAfter(expirationTime)) {
                    certificateExpired = true
                }
                return@forEach
            }
        }
        return InnerVerificationResult(
            noPublicKeysFound = noPublicKeysFound,
            certificateExpired = certificateExpired,
            greenCertificateData = greenCertificateData,
            isApplicableCode = isApplicableCode,
            base64EncodedKid = base64EncodedKid
        )
    }

    private suspend fun GreenCertificateData.validateRules(
        verificationResult: VerificationResult,
        countryIsoCode: String,
        base64EncodedKid: String
    ) {
        this.apply {
            val engineCertificateType = this.greenCertificate.getEngineCertificateType()
            if (countryIsoCode.isNotBlank()) {
                val issuingCountry: String =
                    (if (this.issuingCountry?.isNotBlank() == true && this.issuingCountry != null) this.issuingCountry!! else this.greenCertificate.getIssuingCountry()).toLowerCase(
                        Locale.ROOT
                    )
                val rules = getRulesUseCase.invoke(
                    ZonedDateTime.now().withZoneSameInstant(UTC_ZONE_ID),
                    countryIsoCode,
                    issuingCountry,
                    engineCertificateType
                )
                val valueSetsMap = mutableMapOf<String, List<String>>()
                valueSetsRepository.getValueSets().forEach { valueSet ->
                    val ids = mutableListOf<String>()
                    valueSet.valueSetValues.fieldNames().forEach { id ->
                        ids.add(id)
                    }
                    valueSetsMap[valueSet.valueSetId] = ids
                }

                val externalParameter = ExternalParameter(
                    validationClock = ZonedDateTime.now(ZoneId.of(ZoneOffset.UTC.id)),
                    valueSets = valueSetsMap,
                    countryCode = countryIsoCode,
                    exp = this.expirationTime,
                    iat = this.issuedAt,
                    issuerCountryCode = issuingCountry,
                    kid = base64EncodedKid,
                    region = "",
                )
                val validationResults = engine.validate(
                    engineCertificateType,
                    this.greenCertificate.schemaVersion,
                    rules,
                    externalParameter,
                    this.hcertJson
                )

                _validationResults.postValue(validationResults)

                validationResults.forEach {
                    if (it.result != Result.PASSED) {
                        verificationResult.rulesValidationFailed = true
                        return@forEach
                    }
                }
            }
        }
    }

    private fun GreenCertificate.getEngineCertificateType(): CertificateType {
        return when {
            this.recoveryStatements?.isNotEmpty() == true -> CertificateType.RECOVERY
            this.vaccinations?.isNotEmpty() == true -> CertificateType.VACCINATION
            this.tests?.isNotEmpty() == true -> CertificateType.TEST
            else -> CertificateType.TEST
        }
    }

    companion object {
        private const val ENGINE_VERSION = "1.0.0"

        fun validateCertData(
            certificate: GreenCertificate?,
            verificationResult: VerificationResult
        ) {
            certificate?.tests?.let {
                if (it.isNotEmpty()) {
                    val test = it.first()
                    verificationResult.testVerification =
                        TestVerificationResult(test.isResultNegative(), test.isDateInThePast())
                }
            }
            certificate?.recoveryStatements?.let {
                if (it.isNotEmpty()) {
                    val recovery = it.first()
                    verificationResult.recoveryVerification =
                        RecoveryVerificationResult(
                            recovery.isCertificateNotValidSoFar() == true,
                            recovery.isCertificateNotValidAnymore() == true
                        )
                }
            }
        }
    }
}