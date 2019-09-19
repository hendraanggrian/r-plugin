package com.hendraanggrian.r

import com.helger.css.ECSSVersion
import com.helger.css.reader.CSSReader
import com.hendraanggrian.javapoet.TypeSpecBuilder
import com.hendraanggrian.javapoet.final
import com.hendraanggrian.javapoet.private
import com.hendraanggrian.javapoet.public
import com.hendraanggrian.javapoet.static
import java.io.File
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets
import java.util.Properties
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

internal sealed class Adapter(
    private val isUppercase: Boolean,
    private val isFix: Boolean
) {

    abstract fun TypeSpecBuilder.adapt(file: File): Boolean

    protected fun TypeSpecBuilder.addStringField(name: String, value: String) {
        var fieldName: String? = name
        if (isUppercase) {
            fieldName = fieldName!!.toUpperCase()
        }
        if (!fieldName!!.isFieldName()) {
            fieldName = when {
                isFix -> fieldName.toFieldName()
                else -> null
            }
        }
        // checks for duplicate
        if (fieldName != null && fieldName !in build().fieldSpecs.map { it.name }) {
            fields.add<String>(fieldName) {
                addModifiers(public, static, final)
                initializer("%S", value)
            }
        }
    }
}

internal class DefaultAdapter(
    isUppercase: Boolean,
    isFix: Boolean,
    private val resourcesDir: String,
    private val usePrefix: Boolean = false
) : Adapter(isUppercase, isFix) {

    override fun TypeSpecBuilder.adapt(file: File): Boolean {
        addStringField(
            when {
                usePrefix -> "${file.extension}_${file.nameWithoutExtension}"
                else -> file.nameWithoutExtension
            },
            file.path.substringAfter(resourcesDir).replace('\\', '/')
        )
        return true
    }
}

internal class CssAdapter(
    isUppercase: Boolean,
    isFix: Boolean,
    private val options: CssOptions
) : Adapter(isUppercase, isFix) {

    override fun TypeSpecBuilder.adapt(file: File): Boolean {
        if (file.extension == "css") {
            val css = checkNotNull(CSSReader.readFromFile(file, StandardCharsets.UTF_8, ECSSVersion.CSS30)) {
                "Error while reading css, please report"
            }
            css.allStyleRules.forEach { rule ->
                rule.allSelectors.forEach { selector ->
                    val member = selector.getMemberAtIndex(0) ?: return false
                    var styleName = member.asCSSString
                    if (options.isJavaFx) {
                        styleName = styleName.toFxCssName()
                    }
                    addStringField(styleName, styleName)
                }
            }
            return true
        }
        return false
    }

    private fun String.toFxCssName(): String = when {
        startsWith('.') -> substringAfter('.')
        else -> this
    }
}

internal class PropertiesAdapter(
    isUppercase: Boolean,
    isFix: Boolean,
    private val options: PropertiesOptions
) : Adapter(isUppercase, isFix) {

    override fun TypeSpecBuilder.adapt(file: File): Boolean {
        if (file.extension == "properties") {
            when {
                options.readResourceBundle && file.isResourceBundle() -> {
                    val className = file.resourceBundleName
                    if (className !in build().typeSpecs.map { it.name }) {
                        types.addClass(className) {
                            addModifiers(public, static, final)
                            methods.addConstructor {
                                addModifiers(private)
                            }
                            process(file)
                        }
                    }
                }
                else -> {
                    process(file)
                    return true
                }
            }
        }
        return false
    }

    private fun TypeSpecBuilder.process(file: File) =
        file.forEachProperties { key, _ -> addStringField(key, key) }

    private fun File.forEachProperties(action: (key: String, value: String) -> Unit) =
        inputStream().use { stream ->
            Properties().run {
                load(stream)
                keys.map { it as? String ?: it.toString() }.forEach { key -> action(key, getProperty(key)) }
            }
        }

    private inline val File.resourceBundleName: String
        get() = nameWithoutExtension.substringBeforeLast("_")

    private fun File.isResourceBundle(): Boolean = isValid() &&
        extension == "properties" &&
        nameWithoutExtension.let { name -> '_' in name && name.substringAfterLast("_").length == 2 }
}

internal class JsonAdapter(
    isUppercase: Boolean,
    isFix: Boolean,
    private val options: JsonOptions
) : Adapter(isUppercase, isFix) {

    private var parserRef = WeakReference<JSONParser>(null)

    override fun TypeSpecBuilder.adapt(file: File): Boolean {
        if (file.extension == "json") {
            file.reader().use { reader ->
                var parser = parserRef.get()
                if (parser == null) {
                    parser = JSONParser()
                    parserRef = WeakReference(parser)
                }
                (parser.parse(reader) as JSONObject).forEachKey { key ->
                    addStringField(key, key)
                }
                return true
            }
        }
        return false
    }

    private fun JSONObject.forEachKey(action: (String) -> Unit): Unit =
        forEach { key, value ->
            action(key.toString())
            if (value is JSONArray && options.readArray) {
                value.forEachKey(action)
            }
        }

    private fun JSONArray.forEachKey(action: (String) -> Unit): Unit =
        forEach { json ->
            when {
                options.isRecursive && json is JSONObject -> json.forEachKey(action)
                options.readArray && json is JSONArray -> json.forEachKey(action)
            }
        }
}