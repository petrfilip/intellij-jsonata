package cz.tix.jsonata.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

/**
 * Per-project configuration for the JSONata playground, persisted to `.idea/jsonata.xml`.
 *  - [State.customFunctionsEnabled]: master opt-in for loading compiled custom functions (default off).
 *  - [State.providerClassNames]: FQNs of classes implementing `cz.tix.jsonata.api.JsonataFunctionProvider`.
 *  - [State.prelude]: JSONata-defined helper functions prepended to every expression.
 */
@Service(Service.Level.PROJECT)
@State(name = "JsonataPlayground", storages = [Storage("jsonata.xml")])
class JsonataProjectSettings : SimplePersistentStateComponent<JsonataProjectSettings.State>(State()) {

    class State : BaseState() {
        var customFunctionsEnabled by property(false)
        var providerClassNames by list<String>()
        var prelude by string()
    }

    companion object {
        fun getInstance(project: Project): JsonataProjectSettings = project.service()
    }
}

/** Notifies the playground when custom-function settings change so it can reload and re-evaluate. */
fun interface JsonataSettingsListener {
    fun jsonataSettingsChanged()
}

val JSONATA_SETTINGS_TOPIC: Topic<JsonataSettingsListener> =
    Topic.create("JSONata Playground settings", JsonataSettingsListener::class.java)
