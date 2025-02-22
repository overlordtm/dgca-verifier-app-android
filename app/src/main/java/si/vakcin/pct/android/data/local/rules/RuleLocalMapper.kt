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
 *  Created by osarapulov on 7/26/21 12:35 PM
 */

package si.vakcin.pct.android.data.local.rules

import dgca.verifier.app.engine.UTC_ZONE_ID
import dgca.verifier.app.engine.data.Description
import dgca.verifier.app.engine.data.Rule
import java.util.*

fun Rule.toRuleWithDescriptionLocal(): RuleWithDescriptionsLocal =
    RuleWithDescriptionsLocal(this.toRuleLocal(), descriptions.toDescriptionsLocal())

fun List<Rule>.toRulesWithDescriptionLocal(): List<RuleWithDescriptionsLocal> {
    val rulesWithDescriptionLocal = mutableListOf<RuleWithDescriptionsLocal>()
    forEach {
        rulesWithDescriptionLocal.add(it.toRuleWithDescriptionLocal())
    }
    return rulesWithDescriptionLocal
}

fun Rule.toRuleLocal(): RuleLocal = RuleLocal(
    identifier = this.identifier,
    type = this.type,
    version = this.version,
    schemaVersion = this.schemaVersion,
    engine = this.engine,
    engineVersion = this.engineVersion,
    ruleCertificateType = this.ruleCertificateType,
    validFrom = this.validFrom.withZoneSameInstant(UTC_ZONE_ID),
    validTo = this.validTo.withZoneSameInstant(UTC_ZONE_ID),
    affectedString = this.affectedString,
    logic = this.logic,
    countryCode = this.countryCode,
    region = this.region
)

fun Description.toDescriptionLocal(): DescriptionLocal =
    DescriptionLocal(lang = this.lang, desc = this.desc)

fun Map<String, String>.toDescriptionsLocal(): List<DescriptionLocal> {
    val descriptionsLocal = mutableListOf<DescriptionLocal>()
    forEach { descriptionsLocal.add(DescriptionLocal(lang = it.key, desc = it.value)) }
    return descriptionsLocal
}

fun DescriptionLocal.toDescription(): Description = Description(lang = this.lang, desc = this.desc)

fun List<DescriptionLocal>.toDescriptions(): Map<String, String> {
    val descriptions = mutableMapOf<String, String>()
    forEach { descriptions[it.lang.toLowerCase(Locale.ROOT)] = it.desc }
    return descriptions
}

fun RuleWithDescriptionsLocal.toRule(): Rule = Rule(
    identifier = this.rule.identifier,
    type = this.rule.type,
    version = this.rule.version,
    schemaVersion = this.rule.schemaVersion,
    engine = this.rule.engine,
    engineVersion = this.rule.engineVersion,
    ruleCertificateType = this.rule.ruleCertificateType,
    validFrom = this.rule.validFrom.withZoneSameInstant(UTC_ZONE_ID),
    validTo = this.rule.validTo.withZoneSameInstant(UTC_ZONE_ID),
    affectedString = this.rule.affectedString,
    logic = this.rule.logic,
    countryCode = this.rule.countryCode,
    descriptions = this.descriptions.toDescriptions(),
    region = this.rule.region
)

fun List<RuleWithDescriptionsLocal>.toRules(): List<Rule> {
    val rules = mutableListOf<Rule>()
    forEach {
        rules.add(it.toRule())
    }
    return rules
}