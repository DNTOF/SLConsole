// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole

import com.dntof.slconsole.util.AppText
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * 纯 JVM 测试拿不到 Android 资源。这里直接读 values-zh/strings.xml,
 * 让断言里的中文提示和应用里显示的保持一致。
 */
object TestStrings {
    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, String>()

    @Synchronized
    fun install() {
        if (AppText.testResolver != null) return
        val file = listOf("src/main/res/values-zh/strings.xml", "app/src/main/res/values-zh/strings.xml")
            .map(::File).first { it.isFile }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strNodes = doc.getElementsByTagName("string")
        for (i in 0 until strNodes.length) {
            val e = strNodes.item(i) as Element
            strings[e.getAttribute("name")] = unescape(e.textContent)
        }
        val plNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until plNodes.length) {
            val e = plNodes.item(i) as Element
            val item = e.getElementsByTagName("item").item(0) as Element
            plurals[e.getAttribute("name")] = unescape(item.textContent)
        }
        val stringNames = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        val pluralNames = R.plurals::class.java.fields.associate { it.getInt(null) to it.name }
        AppText.testResolver = { id, quantity, args ->
            val template = if (quantity == null) strings.getValue(stringNames.getValue(id))
            else plurals.getValue(pluralNames.getValue(id))
            if (args.isEmpty() && quantity == null) template else String.format(template, *args)
        }
    }

    private fun unescape(raw: String): String {
        var s = raw
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1)
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                out.append(
                    when (val n = s[i + 1]) {
                        'n' -> '\n'
                        't' -> '\t'
                        else -> n
                    },
                )
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
