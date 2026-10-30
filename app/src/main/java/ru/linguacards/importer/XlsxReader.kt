package ru.linguacards.importer

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipInputStream

data class WorkbookSheet(val name: String, val rows: List<List<String>>)
interface SpreadsheetReader { fun read(resolver: ContentResolver, uri: Uri): List<WorkbookSheet> }

/** Lightweight OOXML reader: uses Android ZIP/XML APIs and is replaceable through SpreadsheetReader. */
class XlsxReader : SpreadsheetReader {
    override fun read(resolver: ContentResolver, uri: Uri): List<WorkbookSheet> {
        val files = mutableMapOf<String, ByteArray>()
        resolver.openInputStream(uri)?.use { stream -> ZipInputStream(stream).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; if (!entry.isDirectory) files[entry.name] = zip.readBytes() }
        } } ?: error("Не удалось открыть файл")
        val workbook = files["xl/workbook.xml"] ?: error("Файл не похож на книгу XLSX")
        val rels = files["xl/_rels/workbook.xml.rels"] ?: error("В книге не найдена таблица листов")
        val targets = mutableMapOf<String, String>()
        parse(rels) { p -> if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") targets[p.getAttributeValue(null, "Id")] = p.getAttributeValue(null, "Target") }
        val sheetRefs = mutableListOf<Pair<String, String>>()
        parse(workbook) { p -> if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
            val name = p.getAttributeValue(null, "name") ?: "Лист"
            val id = p.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                ?: p.getAttributeValue("r", "id") ?: p.getAttributeValue(null, "r:id")
            val target = targets[id]
            if (target != null) sheetRefs += name to normalizeTarget(target)
        } }
        val strings = files["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        return sheetRefs.mapNotNull { (name, path) -> files[path]?.let { WorkbookSheet(name, sheetRows(it, strings)) } }
            .ifEmpty { error("В книге нет доступных листов") }
    }

    private fun normalizeTarget(target: String): String {
        val path = if (target.startsWith("/")) target.removePrefix("/") else if (target.startsWith("xl/")) target else "xl/$target"
        val parts = mutableListOf<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> { if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex) }
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }
    private fun sharedStrings(xml: ByteArray): List<String> {
        val result = mutableListOf<String>(); var inSi = false; val text = StringBuilder()
        parse(xml) { p ->
            if (p.eventType == XmlPullParser.START_TAG && p.name == "si") { inSi = true; text.clear() }
            else if (inSi && p.eventType == XmlPullParser.START_TAG && p.name == "t") text.append(p.nextText())
            else if (p.eventType == XmlPullParser.END_TAG && p.name == "si") { result += text.toString(); inSi = false }
        }
        return result
    }
    private fun sheetRows(xml: ByteArray, strings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>(); var row: MutableMap<Int, String>? = null
        parse(xml) { p ->
            if (p.eventType == XmlPullParser.START_TAG && p.name == "row") row = mutableMapOf()
            else if (p.eventType == XmlPullParser.START_TAG && p.name == "c" && row != null) {
                val index = columnIndex(p.getAttributeValue(null, "r") ?: "A1")
                val type = p.getAttributeValue(null, "t")
                var value = ""; var inline = ""
                val depth = p.depth
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    if (p.eventType == XmlPullParser.START_TAG && p.name == "v") value = p.nextText()
                    else if (p.eventType == XmlPullParser.START_TAG && p.name == "t" && type == "inlineStr") inline += p.nextText()
                    else if (p.eventType == XmlPullParser.END_TAG && p.name == "c" && p.depth == depth) break
                }
                row?.set(index, when (type) { "s" -> strings.getOrNull(value.toIntOrNull() ?: -1).orEmpty(); "inlineStr" -> inline; "b" -> if (value == "1") "TRUE" else "FALSE"; else -> value })
            } else if (p.eventType == XmlPullParser.END_TAG && p.name == "row" && row != null) {
                val current = row!!; val max = current.keys.maxOrNull() ?: -1
                rows += (0..max).map { current[it].orEmpty() }; row = null
            }
        }
        return rows
    }
    private fun columnIndex(ref: String): Int {
        var result = 0
        for (char in ref.takeWhile { it.isLetter() }.uppercase()) result = result * 26 + (char - 'A' + 1)
        return (result - 1).coerceAtLeast(0)
    }
    private fun parse(bytes: ByteArray, block: (XmlPullParser) -> Unit) {
        val parser = Xml.newPullParser(); parser.setInput(bytes.inputStream(), "UTF-8")
        while (parser.next() != XmlPullParser.END_DOCUMENT) block(parser)
    }
}
