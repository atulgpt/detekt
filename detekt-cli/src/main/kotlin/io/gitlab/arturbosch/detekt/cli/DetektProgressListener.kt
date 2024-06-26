package io.gitlab.arturbosch.detekt.cli

import io.gitlab.arturbosch.detekt.api.Detektion
import io.gitlab.arturbosch.detekt.api.FileProcessListener
import io.gitlab.arturbosch.detekt.api.SetupContext
import org.jetbrains.kotlin.psi.KtFile

class DetektProgressListener : FileProcessListener {

    private lateinit var outPrinter: Appendable

    override val id: String = "DetektProgressListener"

    override fun init(context: SetupContext) {
        this.outPrinter = context.outputChannel
    }

    override fun onProcess(file: KtFile) {
        outPrinter.append('.')
    }

    override fun onFinish(files: List<KtFile>, result: Detektion) {
        val middlePart = if (files.size == 1) "file was" else "files were"
        outPrinter.appendLine("\n\n${files.size} kotlin $middlePart analyzed.")
    }
}
