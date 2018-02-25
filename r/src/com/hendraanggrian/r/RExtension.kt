package com.hendraanggrian.r

import org.gradle.api.Project
import java.io.File
import kotlin.DeprecationLevel.ERROR

/** Extension to customize r generation, note that all customizations are optional. */
open class RExtension(private val project: Project) {

    private var _packageName: String? = null
    private var _className: String = "R"
    private var _resourcesDirectory: File = project.projectDir.resolve("src/main/resources")

    /**
     * Package name of which `r` class will be generated to.
     * Default is project group.
     */
    var packageName: String
        @Deprecated(NO_GETTER, level = ERROR) get() = noGetter()
        set(value) {
            _packageName = value
        }

    /**
     * `r` generated class name.
     * Default is `R`.
     */
    var className: String
        @Deprecated(NO_GETTER, level = ERROR) get() = noGetter()
        set(value) {
            _className = value
        }

    /**
     * Path of resources that will be read.
     * Default is resources folder in main module.
     */
    var resourcesDirectory: String
        @Deprecated(NO_GETTER, level = ERROR) get() = noGetter()
        set(value) {
            _resourcesDirectory = project.projectDir.resolve(value)
        }

    /** Value of project's group and version might change upon completion of project evaluation. */
    internal fun applyDefault(project: Project) {
        if (_packageName == null) packageName = project.group.toString()
    }

    /** Make task name based on current class name. */
    internal fun getTaskName(prefix: String) = "$prefix$_className"

    /** Convert this extension to class writer. */
    internal fun toWriter() = RClassWriter(_packageName!!, _className, _resourcesDirectory.listFiles())

    companion object {
        private const val NO_GETTER: String = "Property does not have a getter"

        private fun noGetter(): Nothing = throw UnsupportedOperationException(NO_GETTER)
    }
}