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
 *  Created by osarapulov on 7/26/21 1:47 PM
 */

package si.vakcin.pct.android.data.remote.countries

import dgca.verifier.app.engine.data.source.remote.countries.CountriesRemoteDataSrouce
import retrofit2.Response

class DefaultCountriesRemoteDataSource(private val countriesApiService: CountriesApiService) :
    CountriesRemoteDataSrouce {
    override suspend fun getCountries(countriesUrl: String): List<String> {
        val countriesResponse: Response<List<String>> =
            countriesApiService.getCountries(countriesUrl)
        return countriesResponse.body() ?: listOf()
    }
}