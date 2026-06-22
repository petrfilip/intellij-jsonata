package cz.tix.jsonata.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import cz.tix.jsonata.lang.JsonataLanguage
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings ▸ Tools ▸ JSONata Playground. Edits the per-project custom-function configuration:
 * the opt-in, the provider class FQNs, and the JSONata prelude. Applying publishes
 * [JSONATA_SETTINGS_TOPIC] so open playgrounds reload and re-evaluate.
 */
class JsonataConfigurable(private val project: Project) : Configurable {

    private val enableCheckbox = JBCheckBox(
        "Load custom functions from this project's compiled output (runs project code in the IDE)"
    )
    private val fqnArea = JBTextArea(5, 50)
    private val preludeField = LanguageTextField(JsonataLanguage, project, "", false)

    override fun getDisplayName(): String = "JSONata Playground"

    override fun createComponent(): JComponent {
        preludeField.preferredSize = Dimension(500, 140)
        val panel = FormBuilder.createFormBuilder()
            .addComponent(enableCheckbox)
            .addComponent(JBLabel("Function provider classes (one fully-qualified class name per line):"))
            .addComponent(JBScrollPane(fqnArea))
            .addComponent(JBLabel("Each class must implement cz.tix.jsonata.api.JsonataFunctionProvider and have a no-arg constructor."))
            .addComponent(JBLabel("Prelude — JSONata function definitions prepended to every expression (e.g. \$f := function(\$x){ … };):"))
            .addComponent(preludeField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val s = state()
        return enableCheckbox.isSelected != s.customFunctionsEnabled ||
            currentFqns() != s.providerClassNames.toList() ||
            preludeField.text != (s.prelude ?: "")
    }

    override fun apply() {
        val s = state()
        s.customFunctionsEnabled = enableCheckbox.isSelected
        s.providerClassNames.clear()
        s.providerClassNames.addAll(currentFqns())
        s.prelude = preludeField.text
        project.messageBus.syncPublisher(JSONATA_SETTINGS_TOPIC).jsonataSettingsChanged()
    }

    override fun reset() {
        val s = state()
        enableCheckbox.isSelected = s.customFunctionsEnabled
        fqnArea.text = s.providerClassNames.joinToString("\n")
        preludeField.text = s.prelude ?: ""
    }

    private fun state() = JsonataProjectSettings.getInstance(project).state

    private fun currentFqns(): List<String> =
        JsonataFunctionNames.normalizeProviderClassNames(fqnArea.text.lines())
}
