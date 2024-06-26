package io.gitlab.arturbosch.detekt.sample.extensions.reports

import io.gitlab.arturbosch.detekt.api.Detektion
import io.gitlab.arturbosch.detekt.api.OutputReport

class QualifiedNamesOutputReport : OutputReport() {

    override val id: String = "QualifiedNamesOutputReport"
    override val ending: String = "txt"

    override fun render(detektion: Detektion): String? = qualifiedNamesReport(detektion)
}
