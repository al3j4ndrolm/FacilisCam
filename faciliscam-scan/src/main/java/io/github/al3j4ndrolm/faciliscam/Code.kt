package io.github.al3j4ndrolm.faciliscam

import java.net.URLDecoder

/** The kinds of codes the scanner can read. */
enum class Format {
    QR, DataMatrix, Aztec, PDF417,
    EAN13, EAN8, UPCA, UPCE,
    Code128, Code39, Code93, Codabar, ITF;

    /** Whether this is a one-line barcode (stripes) rather than a square/2D code. */
    val isBarcode: Boolean get() = this in Barcodes

    companion object {
        /** Every format. */
        @JvmField
        val All: Set<Format> = entries.toSet()

        /** Barcodes printed on products in stores (EAN and UPC). */
        @JvmField
        val Products: Set<Format> = setOf(EAN13, EAN8, UPCA, UPCE)

        /** One-line barcodes (stripes). */
        @JvmField
        val Barcodes: Set<Format> = setOf(EAN13, EAN8, UPCA, UPCE, Code128, Code39, Code93, Codabar, ITF)
    }
}

/**
 * A scanned code, already understood: a link, a Wi-Fi network, a contact... or plain [Text].
 *
 * ```
 * when (code) {
 *     is Code.Url -> openBrowser(code.url)
 *     is Code.Wifi -> connect(code.ssid, code.password)
 *     else -> show(code.value)
 * }
 * ```
 */
sealed class Code {
    /** Exactly what the code contains. */
    abstract val value: String

    /** What kind of code it was printed as. */
    abstract val format: Format

    /** A web link. */
    data class Url(override val value: String, override val format: Format, val url: String) : Code()

    /** A Wi-Fi network to join. [password] is null for open networks. */
    data class Wifi(
        override val value: String,
        override val format: Format,
        val ssid: String,
        val password: String?,
        /** "WPA", "WEP", or null for an open network. */
        val security: String?,
    ) : Code()

    /** An email to write. */
    data class Email(
        override val value: String,
        override val format: Format,
        val address: String,
        val subject: String?,
        val body: String?,
    ) : Code()

    /** A phone number to call. */
    data class Phone(override val value: String, override val format: Format, val number: String) : Code()

    /** A text message to send. */
    data class Sms(override val value: String, override val format: Format, val number: String, val message: String?) : Code()

    /** A place on a map. */
    data class Location(override val value: String, override val format: Format, val latitude: Double, val longitude: Double) : Code()

    /** A contact card (vCard or MeCard). */
    data class Contact(
        override val value: String,
        override val format: Format,
        val name: String?,
        val phones: List<String>,
        val emails: List<String>,
    ) : Code()

    /** A product barcode (EAN/UPC). [value] is the product number, e.g. "0123456789012". */
    data class Product(override val value: String, override val format: Format) : Code()

    /** Anything else. */
    data class Text(override val value: String, override val format: Format) : Code()

    internal companion object {

        /** Understands the common QR conventions (links, WIFI:, mailto:, tel:, SMSTO:, geo:, vCard, MeCard). */
        fun parse(value: String, format: Format): Code {
            val text = value.trim()
            if (format in Format.Products) return Product(text, format)
            return when {
                text.startsWith("WIFI:", ignoreCase = true) -> wifi(value, format, text)
                text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) ->
                    Url(value, format, text)
                text.startsWith("www.", ignoreCase = true) && ' ' !in text -> Url(value, format, "https://$text")
                text.startsWith("URLTO:", ignoreCase = true) -> Url(value, format, text.substring(6))
                text.startsWith("MEBKM:", ignoreCase = true) ->
                    fields(text.substring(6))["URL"]?.let { Url(value, format, it) } ?: Text(value, format)
                text.startsWith("mailto:", ignoreCase = true) -> mailto(value, format, text)
                text.startsWith("MATMSG:", ignoreCase = true) -> fields(text.substring(7)).let {
                    Email(value, format, it["TO"].orEmpty(), it["SUB"], it["BODY"])
                }
                text.startsWith("tel:", ignoreCase = true) -> Phone(value, format, text.substring(4))
                text.startsWith("smsto:", ignoreCase = true) || text.startsWith("sms:", ignoreCase = true) -> sms(value, format, text)
                text.startsWith("geo:", ignoreCase = true) -> geo(value, format, text) ?: Text(value, format)
                text.startsWith("BEGIN:VCARD", ignoreCase = true) -> vcard(value, format, text)
                text.startsWith("MECARD:", ignoreCase = true) -> mecard(value, format, text)
                else -> Text(value, format)
            }
        }

        private fun wifi(value: String, format: Format, text: String): Code {
            val f = fields(text.substring(5))
            val security = f["T"]?.takeUnless { it.isBlank() || it.equals("nopass", ignoreCase = true) }
            return Wifi(value, format, ssid = f["S"].orEmpty(), password = f["P"]?.takeIf { it.isNotEmpty() }, security = security)
        }

        private fun mailto(value: String, format: Format, text: String): Code {
            val rest = text.substring(7)
            val address = decode(rest.substringBefore('?'))
            val query = query(rest.substringAfter('?', ""))
            return Email(value, format, address, query["subject"], query["body"])
        }

        private fun sms(value: String, format: Format, text: String): Code {
            val rest = text.substringAfter(':')
            // SMSTO:number:message, or sms:number?body=message
            return if ('?' in rest) {
                Sms(value, format, rest.substringBefore('?'), query(rest.substringAfter('?'))["body"])
            } else {
                Sms(value, format, rest.substringBefore(':'), rest.substringAfter(':', "").ifEmpty { null })
            }
        }

        private fun geo(value: String, format: Format, text: String): Code? {
            val parts = text.substring(4).substringBefore('?').substringBefore(';').split(',')
            val latitude = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
            val longitude = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return null
            return Location(value, format, latitude, longitude)
        }

        private fun vcard(value: String, format: Format, text: String): Code {
            var name: String? = null
            var structuredName: String? = null
            val phones = mutableListOf<String>()
            val emails = mutableListOf<String>()
            for (line in text.lines()) {
                val key = line.substringBefore(':').substringBefore(';').uppercase()
                val content = line.substringAfter(':', "").trim()
                when (key) {
                    "FN" -> name = content
                    "N" -> structuredName = content.split(';').let { listOf(it.getOrNull(1), it.getOrNull(0)) }
                        .filterNot { it.isNullOrBlank() }.joinToString(" ")
                    "TEL" -> if (content.isNotEmpty()) phones += content
                    "EMAIL" -> if (content.isNotEmpty()) emails += content
                }
            }
            return Contact(value, format, name ?: structuredName?.ifBlank { null }, phones, emails)
        }

        private fun mecard(value: String, format: Format, text: String): Code {
            val entries = entries(text.substring(7))
            val name = entries.firstOrNull { it.first == "N" }?.second
                ?.split(',')?.let { listOf(it.getOrNull(1), it.getOrNull(0)) }
                ?.filterNot { it.isNullOrBlank() }?.joinToString(" ")
            return Contact(
                value, format,
                name = name?.ifBlank { null },
                phones = entries.filter { it.first == "TEL" }.map { it.second },
                emails = entries.filter { it.first == "EMAIL" }.map { it.second },
            )
        }

        /** `KEY:value;KEY:value;;` with `\` escapes, as used by WIFI:, MECARD:, MATMSG:. */
        private fun fields(text: String): Map<String, String> = entries(text).toMap()

        private fun entries(text: String): List<Pair<String, String>> {
            val result = mutableListOf<Pair<String, String>>()
            val current = StringBuilder()
            var escaped = false
            fun flush() {
                val entry = current.toString()
                current.clear()
                val colon = entry.indexOf(':')
                if (colon > 0) result += entry.substring(0, colon).uppercase() to entry.substring(colon + 1)
            }
            for (c in text) {
                when {
                    escaped -> { current.append(c); escaped = false }
                    c == '\\' -> escaped = true
                    c == ';' -> flush()
                    else -> current.append(c)
                }
            }
            flush()
            return result
        }

        private fun query(text: String): Map<String, String> = text.split('&')
            .filter { '=' in it }
            .associate { it.substringBefore('=').lowercase() to decode(it.substringAfter('=')) }

        private fun decode(text: String): String = try {
            URLDecoder.decode(text, "UTF-8")
        } catch (e: IllegalArgumentException) {
            text
        }
    }
}
