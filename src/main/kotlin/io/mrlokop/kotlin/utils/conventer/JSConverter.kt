package io.mrlokop.kotlin.utils.conventer

import com.google.gson.Gson
import io.mrlokop.kotlin.utils.conventer.enities.DeclarationEntity
import io.mrlokop.kotlin.utils.conventer.enities.EntryEntity
import io.mrlokop.kotlin.utils.conventer.enities.IntPrimitiveEntity
import io.mrlokop.kotlin.utils.conventer.enities.TypeEntity
import io.mrlokop.kotlin.utils.conventer.enities.expression.*
import io.mrlokop.kotlin.utils.conventer.exceptions.UnresolvedReferenceException
import io.mrlokop.kotlin.utils.conventer.scopes.ConverterScope
import io.mrlokop.kotlin.utils.conventer.scopes.EntryScope
import io.mrlokop.kotlin.utils.conventer.utils.ScriptBuilder
import io.mrlokop.kotlin.utils.conventer.utils.debug
import io.mrlokop.kotlin.utils.conventer.utils.trace

class JSConverter(val entries: List<EntryEntity>) {
    fun convert(): String {
        var script = ScriptBuilder()
        var entryId = 0
        val scope = ConverterScope()

        script + "exports = () => {"
        script++
        script + "const root = {};"
        +script
        includeConverterAPI(script)
        +script
        entries.forEach { entry ->
            val entryScope = scope.makeEntryScope(entry)
        }
        entries.forEach { entry ->
            val entryScope = scope.makeEntryScope(entry)
            entryScope.compile()
        }
        entries.forEach { entry ->
            val entryScope = scope.makeEntryScope(entry)
            entryScope.processImports()
        }
        entries.forEach { entry ->
            entryId++

            val entryScope = scope.makeEntryScope(entry)


            script + "// Entry #$entryId"
            script + "// -> File: ${entry.fileName}"
            script + "// -> Package: ${entry.packageName}"
            +script
            script + "(() => {"

            script.wrap {
                +"// Script..."
                +"const " + escapePackage(entry.packageName) + " = root.converter.getPackage('" + entry.packageName + "'); // Get getPackage fun for get package"

                entry.topLevels.forEach {
                    it.declarations.forEach {
                        serializeDeclaration(entryScope, it)
                    }
                }


            }

            script + "})();"
            +script
        }
        +script
        includeBootstrap(script)
        +script
        script + "return root;"
        script--
        script + "}"
        if (enableAutoRun) {
            +script
            +script
            script + "// Autorun"
            script + "try {"
            script.wrap {
                script + "const data = exports();"

                script + "console.log();"
                script + "console.log(\"  __ __ __ __ __ __ __ __ __ __ __ __ __ __\");"
                script + "console.log();"
                script + "console.log(\"Data:\", data);"
            }
            script + "} catch(error) {"
            script.wrap {
                script + "console.error(\"Failed to run\")"
                script + "console.error(error)"
            }
            script + "}"
        }
        return script.toString()
    }

    fun ScriptBuilder.serializeDeclaration(
        entryScope: EntryScope,
        declarationEntity: DeclarationEntity,
        classWrap: Int = 0
    ) {
        var script = this
        val isFromClazz = classWrap != 0
        val className = "\$\$class${classWrap}"
        declarationEntity.fields.forEach {
            expr(
                +(if (it.decType == "var") "var" else "const") + " " + it.name + " = ",
                it.expression!!, scope = entryScope
            )

            if (!isFromClazz) {
                +escapePackage(entryScope.entry.packageName) + "['" + it.name + "'] = " + it.name
            } else {
                +className + "['" + it.name + "'] = " + it.name
            }
            //+escapePackage(entryScope.entry.packageName) + "['" + it.name + "'] = " + it.name
            +""
        }

        declarationEntity.functions.forEach {
            val entry = entryScope.entry

            +"/**"
            +" * Function converted from ${entry.packageName}.${it.name}"
            +" *"
            if (it.params.isNotEmpty()) {
                it.params.forEach {
                    val line = (+" * @param " + it.name)
                    if (it.type != null) {
                        line + " " + serialize(it.type!!)
                    }
                }
            }
            +" */"
            val line = (+"function " + it.name + "(")
            if (it.params.isNotEmpty()) {
                it.params.forEach {
                    line + it.name + ", "
                }
                line.sub(2)
            }
            line + ") {"
            line.script++
            line.script + "// body"
            it.body.block.statements.forEach {
                it.expressions.forEach {
                    expr(line.script.ln(), it, scope = entryScope)
                }
            }
            line.script--
            line.script + "}"
            if (!isFromClazz) {
                +escapePackage(entry.packageName) + "['" + it.name + "'] = " + it.name
            } else {
                +className + "['" + it.name + "'] = " + it.name
            }

            if (enableMeta) {
                +(it.name + "['\$'] = " + it.name + "['\$'] || {}; " + it.name + "['\$']['\$_meta'] ={")
                script++
                script + ("name: '" + it.name + "',")
                script + ("modifiers: " + Gson().toJson(it.mods) + ",")
                script--

                +"}"
            }
            +""
        }

        declarationEntity.classes.forEach {
            val parentClassName = className
            val className = "\$\$class${classWrap + 1}"
            val name = "\$\$class_${it.name}"
            script + "const $name = function () {"
            script.wrap {
                +"const ${className} = this;"
                +""
                it.declarations.forEach {
                    serializeDeclaration(entryScope, it, classWrap + 1)
                }
                +"return this;"
            }
            script + "}"

            if (!isFromClazz) {
                +escapePackage(entryScope.entry.packageName) + "['" + it.name + "'] = " + name
            } else {
                +parentClassName + "['" + it.name + "'] = " + name
            }

        }
    }
}

/** SCRIPT API **/
private fun includeConverterAPI(script_: ScriptBuilder) {
    var script = script_
    script + "(() => {"
    script++ // 1
    script + "function escapePackage(pck) {"
    script++ // 2
    script + "return \"\$_\" + pck.replace(/\\./gm, \"_\")"
    script-- // 1
    script + "}"
    +script
    script + "function getPackage(key) {"
    script++ // 2
    script + "let a = root;"
    script + "for (const k of key.split(\".\")) {"
    script++ // 3
    script + "const p = a;"
    script + "a = a[k] || {};"
    script + "p[k] = a;"
    script-- // 2
    script + "}"
    script + "return a;"
    script-- // 1
    script + "}"
    +script
    script + "const converterPackage = getPackage(\"converter\")"
    script + "converterPackage.getPackage = getPackage;"
    script + "converterPackage.escapePackage = escapePackage;"
    script-- // 0
    script + "})();"
}

private fun includeBootstrap(script_: ScriptBuilder) {
    var script = script_
    script + "function recursive(data) {"
    script++
    script + "for (const key of Object.keys(data)) {"
    script++
    script + "const v = data[key];"
    script + "if (typeof v === 'object') {"
    script++
    script + "recursive(v)"
    script--
    script + "}"
    script + "if (typeof v === 'function') {"
    script++
    script + "if (key === \"main\") {"
    script++
    script + "v()"
    script--
    script + "}"
    script--
    script + "}"
    script--
    script + "}"
    script--
    script + "}"

    +script
    script + "recursive(root)"
}

/** UTILS **/
fun escapePackage(pck: String): String {
    if (pck.isEmpty())
        return "\$"
    return "\$_" + pck.replace(".", "_")
}

private fun serialize(data: Any): String {
    when (data) {
        is TypeEntity -> {
            var a = data.name
            if (data.subTypes.isNotEmpty()) {
                a += "<"
                data.subTypes.forEach {
                    a += serialize(it) + ","
                }
                a = a.substring(0, a.length - 1) + ">"
            }
            return a
        }
    }
    return "/* Fail to serialize (${data.javaClass.simpleName}) */"
}

var enableExpressionsShow = false
var enableAutoRun = true
var enableMeta = false
var allowUnresolvedReference = true

private fun expr(
    line_: ScriptBuilder.ScriptLine,
    expression: ExpressionEntity,
    fromRoot: Boolean = false,
    scope: EntryScope
): ScriptBuilder.ScriptLine {
    var line = line_
    if (enableExpressionsShow)
        line + "/* <" + expression.javaClass.simpleName + "> */"
    when (expression) {
        is StringExpression -> {
            line + "'" + expression.get() + "'"
        }
        is PropertyDeclarationExpression -> {
            expr(
                line + (if (expression.field.decType == "var") "var" else "const") + " " + expression.field.name + " = ",
                expression.field.expression!!, scope = scope
            ) + ";"
        }
        is FunctionDeclarationExpression -> {
            line + "function " + expression.function.name + "("

            if (expression.function.params.isNotEmpty()) {
                expression.function.params.forEach {
                    line + it.name + ", "
                }
                line.sub(2)
            } else {
                line + "/* empty */"
            }
            line + ") {"
            var builder = line.script
            builder++
            builder + "// function "
            expression.function.body.block.statements.forEach {
                it.expressions.forEach {
                    expr(builder.ln(), it, true, scope)
                }
            }
            builder--
            builder + "}"
        }
        is IdentifierExpression -> {
            line + expression.identifier
        }
        is FunctionInvokeExpression -> {
            if (expression.parent != null) {
                line + "("
                expr(line, expression.parent!!, false, scope)
                line + ")"
                if (expression.isDotAccessor)
                    line + "."
            }
            // resolving function
            if (expression.isMember) {
                val resolve = scope.resolveLocalFunctionByName(expression.name)
                if (resolve.isEmpty()) {
                    trace("-> Function '${expression.name}' not resolved: trying resolve by class")
                    val resolveClass = scope.resolveLocalClassByName(expression.name)
                    if (resolveClass.isEmpty()) {

                        if (!allowUnresolvedReference) {
                            throw UnresolvedReferenceException(expression.name)
                        } else {
                            line + "/* Unresolved */"
                        }

                    } else {
                        trace("--> Resolved!")
                        resolveClass.forEach {
                            line + "root.${it.scope.entry.packageName}."
                        }
                    }
                } else {

                    resolve.forEach {
                        line + "root.${it.scope.entry.packageName}."
                    }
                }
            }

            line + expression.name

            if (expression.isMember) {
                line + "("

                if (expression.args.isNotEmpty()) {

                    expression.args.forEach {
                        expr(line, it, false, scope) + ", "
                    }

                    line.sub(2)


                }
                line + ")"



                if (fromRoot)
                    line + ";"
            }

        }

        is MultiplicativeExpression -> {

            expression.operations.forEach {
                if (it is MultiplicativeData) {
                    expr(line + " ( ", it.data, scope = scope) + " ) "
                }
                if (it is MultiplicativeOperator) {
                    line + it.data
                }
            }
        }
        is JumpExpression -> {
            expr(line + expression.jumpType + " ", expression.expression, scope = scope)
        }
        is ConstantExpression -> {
            when (expression.const) {
                is IntPrimitiveEntity -> {
                    line + (expression.const as IntPrimitiveEntity).get().toString()
                }
                else -> {
                    line + "/* Failed to unwrap ConstantExpression (${expression.const!!.javaClass.simpleName}) */"
                }
            }
        }
        is AdditiveExpression -> {
            expression.operations.forEach {
                if (it is AdditiveData) {
                    expr(line + " ( ", it.data, scope = scope) + " ) "
                }
                if (it is AdditiveOperator) {
                    line + it.data
                }
            }
        }
        is LambdaExpression -> {
            (line + "(")
            if (expression.parameters.isNotEmpty()) {
                expression.parameters.forEach {
                    line + it.name + ", "
                }
                line.sub(2)
            } else {
                line + "/* empty */"
            }
            var script = (line + ") => {").script
            script++
            script + "// lambda"
            expression.statements.forEach {
                it.expressions.forEach {
                    expr(script.ln(), it, true, scope)
                }
            }
            script--
            script + "}"

        }
        else -> {
            if (expression.javaClass.name != "io.mrlokop.kotlin.utils.conventer.enities.expression.ExpressionEntity") {
                println()
                debug()
                debug("Failed serialize")
                debug("-> ${expression.javaClass.name}")
                debug()
                println()
                line + " /* Failed to serialize expression ${expression.javaClass.simpleName} */ "
            } else {
                line.remove()
            }
        }
    }
    if (enableExpressionsShow)
        line + "/* </" + expression.javaClass.simpleName + "> */"
    return line
}

