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

package si.vakcin.pct.android.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import si.vakcin.pct.android.BuildConfig
import si.vakcin.pct.android.data.ConfigRepository
import si.vakcin.pct.android.data.VerifierRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val verifierRepository: VerifierRepository
) : ViewModel() {

    private val _inProgress = MutableLiveData<Boolean>()
    val inProgress: LiveData<Boolean> = _inProgress
    val lastSyncLiveData: LiveData<Long> = verifierRepository.getLastSyncTimeMillis()

    fun syncPublicKeys() {
        viewModelScope.launch {
            _inProgress.value = true
            withContext(Dispatchers.IO) {
                try {
                    val config = configRepository.local().getConfig()
                    val versionName = BuildConfig.VERSION_NAME
                    verifierRepository.fetchCertificates(
                        config.getStatusUrl(versionName),
                        config.getUpdateUrl(versionName)
                    )
                } catch (error: Throwable) {
                    Timber.e(error, "error refreshing keys")
                }
            }
            _inProgress.value = false
        }
    }
}