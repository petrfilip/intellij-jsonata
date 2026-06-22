package cz.tix.jsonata.lang

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.InheritanceUtil
import cz.tix.jsonata.settings.JSONATA_SETTINGS_TOPIC
import cz.tix.jsonata.settings.JsonataProjectSettings
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getUParentForIdentifier
import java.util.function.Supplier

/**
 * Gutter icon on classes (Java/Kotlin via UAST) that implement [cz.tix.jsonata.api.JsonataFunctionProvider]:
 *  - **green** when the class is registered with the playground,
 *  - a "+" otherwise.
 * Clicking toggles the registration in the project settings.
 */
class JsonataProviderLineMarkerProvider : LineMarkerProvider {

    /**
     * Returns a gutter marker for a UAST class identifier only when it is a loadable (non-interface,
     * concrete, public, no-arg-constructible) implementation of
     * [cz.tix.jsonata.api.JsonataFunctionProvider]: an "OK"
     * icon when already registered, a "+" otherwise. The navigation handler toggles registration via
     * [toggle].
     */
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val uClass = getUParentForIdentifier(element) as? UClass ?: return null
        val psiClass = uClass.javaPsi
        if (!isLoadableProviderClass(psiClass)) return null
        val fqn = psiClass.qualifiedName ?: return null
        if (!InheritanceUtil.isInheritor(psiClass, PROVIDER_FQN)) return null

        val project = element.project
        val registered = fqn in JsonataProjectSettings.getInstance(project).state.providerClassNames
        val icon = if (registered) AllIcons.General.InspectionsOK else AllIcons.General.Add
        val tooltip =
            if (registered) "Registered with JSONata Playground — click to unregister"
            else "Register with JSONata Playground"

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltip },
            GutterIconNavigationHandler<PsiElement> { _, _ -> toggle(project, fqn, registered) },
            GutterIconRenderer.Alignment.LEFT,
            Supplier { tooltip },
        )
    }

    private fun isLoadableProviderClass(psiClass: PsiClass): Boolean {
        if (psiClass.isInterface) return false
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false
        if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) return false
        val constructors = psiClass.constructors
        return constructors.isEmpty() ||
            constructors.any {
                it.hasModifierProperty(PsiModifier.PUBLIC) && it.parameterList.parametersCount == 0
            }
    }

    // Adds/removes the class from the registered providers; registering also enables the feature.
    // Either way it republishes the settings topic and restarts the analyzer to refresh the gutter.
    private fun toggle(project: Project, fqn: String, currentlyRegistered: Boolean) {
        val settings = JsonataProjectSettings.getInstance(project).state
        if (currentlyRegistered) {
            settings.providerClassNames.remove(fqn)
        } else {
            if (!settings.providerClassNames.contains(fqn)) settings.providerClassNames.add(fqn)
            settings.customFunctionsEnabled = true // registering implies enabling the feature
        }
        project.messageBus.syncPublisher(JSONATA_SETTINGS_TOPIC).jsonataSettingsChanged()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    companion object {
        private const val PROVIDER_FQN = "cz.tix.jsonata.api.JsonataFunctionProvider"
    }
}
