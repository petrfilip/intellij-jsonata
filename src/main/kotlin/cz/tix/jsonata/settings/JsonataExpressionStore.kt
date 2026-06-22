package cz.tix.jsonata.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Remembers the JSONata expression last used for each JSON file, keyed by the file's URL, so the
 * playground restores it when the file is reopened — and across IDE restarts.
 *
 * Stored in the project's `workspace.xml` (personal working state, not shared in VCS), matching how
 * the IDE persists other per-file editor state. An empty expression drops the entry rather than
 * storing a blank, so files the user never queried leave no trace.
 */
@Service(Service.Level.PROJECT)
@State(name = "JsonataExpressions", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class JsonataExpressionStore : SimplePersistentStateComponent<JsonataExpressionStore.State>(State()) {

    class State : BaseState() {
        var expressions by map<String, String>()
    }

    /** The last expression remembered for [fileUrl], or null if none. */
    fun get(fileUrl: String): String? = state.expressions[fileUrl]

    /** Remembers [expression] for [fileUrl]; an empty/blank expression removes the entry. */
    fun put(fileUrl: String, expression: String) {
        val current = state.expressions[fileUrl]
        if (expression.isEmpty()) {
            if (current != null) {
                state.expressions.remove(fileUrl)
                state.intIncrementModificationCount()
            }
        } else if (current != expression) {
            state.expressions[fileUrl] = expression
            state.intIncrementModificationCount()
        }
    }

    companion object {
        fun getInstance(project: Project): JsonataExpressionStore = project.service()
    }
}
