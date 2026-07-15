package cz.tix.jsonata.settings

import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import cz.tix.jsonata.api.JsonataFn
import cz.tix.jsonata.api.JsonataFunctionProvider
import cz.tix.jsonata.api.JsonataFunctions
import cz.tix.jsonata.completion.JsonataBuiltins
import cz.tix.jsonata.engine.CustomFunction
import java.io.File
import java.net.URL
import java.net.URLClassLoader

/**
 * Loads custom functions from the user's compiled project output. Gated by an explicit per-project
 * opt-in AND project trust, because the loaded code runs unsandboxed inside the IDE JVM. The
 * `URLClassLoader` parent is the plugin classloader so the shared `JsonataFunctionProvider` interface
 * resolves to the same class on both sides (avoids ClassCastException). Results are cached; only a
 * settings change auto-invalidates the cache. A recompile of the user's project is NOT detected, so
 * after rebuilding call [invalidate] (or re-open the playground) to pick up the new bytecode.
 */
@Service(Service.Level.PROJECT)
class JsonataFunctionLoader(private val project: Project) : Disposable {

    data class LoadResult(val functions: List<CustomFunction>, val errors: List<String>, val active: Boolean)

    @Volatile
    private var cache: LoadResult? = null

    @Volatile
    private var classLoader: URLClassLoader? = null

    init {
        project.messageBus.connect(this).subscribe(JSONATA_SETTINGS_TOPIC, JsonataSettingsListener { invalidate() })
        project.messageBus.connect(this).subscribe(TrustedProjectsListener.TOPIC, object : TrustedProjectsListener {
            override fun onProjectTrusted(project: Project) = invalidate()
            override fun onProjectUntrusted(project: Project) = invalidate()
        })
    }

    fun load(): LoadResult = cache ?: doLoad()

    @Synchronized
    fun invalidate() {
        cache = null
        classLoader?.let { runCatching { it.close() } }
        classLoader = null
    }

    @Synchronized
    private fun doLoad(): LoadResult {
        cache?.let { return it }
        val result = compute()
        cache = result
        return result
    }

    private fun compute(): LoadResult {
        val settings = JsonataProjectSettings.getInstance(project).state
        if (!settings.customFunctionsEnabled) return LoadResult(emptyList(), emptyList(), active = false)
        if (!isProjectTrusted()) {
            return LoadResult(
                emptyList(),
                listOf("Custom functions are disabled because project trust could not be confirmed"),
                active = false,
            )
        }

        val fqns = JsonataFunctionNames.normalizeProviderClassNames(settings.providerClassNames)
        if (fqns.isEmpty()) return LoadResult(emptyList(), emptyList(), active = true)

        val functions = ArrayList<CustomFunction>()
        val errors = ArrayList<String>()
        val sink = object : JsonataFunctions {
            override fun add(name: String, fn: JsonataFn) {
                val normalized = JsonataFunctionNames.normalizeFunctionName(name)
                when {
                    normalized == null -> errors.add("Invalid custom function name '$name' ignored")
                    JsonataFunctionNames.isBuiltInFunctionName(normalized) ->
                        errors.add("Custom function '\$$normalized' conflicts with a built-in function and was ignored")
                    functions.any { it.name == normalized } ->
                        errors.add("Duplicate custom function '\$$normalized' ignored")
                    else -> functions.add(CustomFunction(normalized, fn))
                }
            }
        }

        val loader = URLClassLoader(classpathUrls(), javaClass.classLoader)
        classLoader = loader
        for (fqn in fqns) {
            val name = fqn.trim()
            if (name.isEmpty()) continue
            try {
                val instance = Class.forName(name, true, loader).getDeclaredConstructor().newInstance()
                (instance as JsonataFunctionProvider).register(sink)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: LinkageError) {
                errors.add("$name — ${e.message ?: e.javaClass.simpleName}")
            } catch (e: Exception) {
                errors.add("$name — ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return LoadResult(functions, errors, active = true)
    }

    private fun classpathUrls(): Array<URL> {
        val paths = ReadAction.compute<List<String>, RuntimeException> {
            OrderEnumerator.orderEntries(project).withoutSdk().runtimeOnly().classes().pathsList.pathList
        }
        return paths.mapNotNull { runCatching { File(it).toURI().toURL() }.getOrNull() }.toTypedArray()
    }

    /**
     * Trust is a security boundary. Any API mismatch, reflection failure or unexpected return value
     * must therefore fail closed and keep unsandboxed project code disabled.
     */
    private fun isProjectTrusted(): Boolean =
        runCatching {
            val trustedProjects = Class.forName("com.intellij.ide.impl.TrustedProjects")
            trustedProjects.getMethod("isTrusted", Project::class.java).invoke(null, project) as? Boolean ?: false
        }.getOrDefault(false)

    override fun dispose() = invalidate()

    companion object {
        fun getInstance(project: Project): JsonataFunctionLoader = project.service()
    }
}

internal object JsonataFunctionNames {
    private val FUNCTION_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun normalizeProviderClassNames(raw: Iterable<String>): List<String> =
        raw.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun normalizeFunctionName(raw: String): String? {
        val name = raw.trim().removePrefix("$")
        return name.takeIf { FUNCTION_NAME.matches(it) }
    }

    fun isBuiltInFunctionName(normalizedName: String): Boolean =
        JsonataBuiltins.byName("\$$normalizedName") != null
}
