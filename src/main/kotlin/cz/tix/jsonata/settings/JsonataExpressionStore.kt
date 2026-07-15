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
 * playground restores it when the file is reopened and across IDE restarts.
 *
 * Stored in the project's `workspace.xml` as personal working state. Blank expressions are removed
 * instead of persisted, so files with no meaningful query leave no state behind.
 */
@Service(Service.Level.PROJECT)
@State(name = "JsonataExpressions", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class JsonataExpressionStore : SimplePersistentStateComponent<JsonataExpressionStore.State>(State()) {

    class State : BaseState() {
        var expressions by map<String, String>()
    }

    fun get(fileUrl: String): String? = state.expressions[fileUrl]

    fun put(fileUrl: String, expression: String) {
        val current = state.expressions[fileUrl]
        if (expression.isBlank()) {
            if (current != null) state.expressions.remove(fileUrl)
        } else if (current != expression) {
            state.expressions[fileUrl] = expression
        }
    }

    companion object {
        fun getInstance(project: Project): JsonataExpressionStore = project.service()
    }
}
