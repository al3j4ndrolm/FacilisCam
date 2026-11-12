package io.github.al3j4ndrolm.faciliscam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeTest {

    private fun qr(value: String) = Code.parse(value, Format.QR)

    @Test
    fun `links`() {
        assertEquals("https://sjsu.edu", (qr("https://sjsu.edu") as Code.Url).url)
        assertEquals("HTTP://EXAMPLE.COM/A", (qr("HTTP://EXAMPLE.COM/A") as Code.Url).url)
        assertEquals("https://www.sjsu.edu", (qr("www.sjsu.edu") as Code.Url).url)
        assertEquals("https://a.com", (qr("URLTO:https://a.com") as Code.Url).url)
        assertEquals("https://a.com", (qr("MEBKM:TITLE:A;URL:https://a.com;;") as Code.Url).url)
        // A sentence that starts with www. isn't a link.
        assertTrue(qr("www. is a prefix") is Code.Text)
    }

    @Test
    fun `wifi with escapes and open networks`() {
        assertEquals(
            Code.Wifi("WIFI:T:WPA;S:SJSU Guest;P:p\\;ss\\:word;;", Format.QR, "SJSU Guest", "p;ss:word", "WPA"),
            qr("WIFI:T:WPA;S:SJSU Guest;P:p\\;ss\\:word;;"),
        )
        val open = qr("WIFI:S:Cafe;T:nopass;P:;;") as Code.Wifi
        assertEquals("Cafe", open.ssid)
        assertEquals(null, open.password)
        assertEquals(null, open.security)
    }

    @Test
    fun `email in both styles`() {
        val mailto = qr("mailto:prof@sjsu.edu?subject=Office%20hours&body=Hi") as Code.Email
        assertEquals("prof@sjsu.edu", mailto.address)
        assertEquals("Office hours", mailto.subject)
        assertEquals("Hi", mailto.body)

        val matmsg = qr("MATMSG:TO:a@b.com;SUB:Hello;BODY:See you;;") as Code.Email
        assertEquals(Triple("a@b.com", "Hello", "See you"), Triple(matmsg.address, matmsg.subject, matmsg.body))
    }

    @Test
    fun `phone, sms and location`() {
        assertEquals("+14085551234", (qr("tel:+14085551234") as Code.Phone).number)

        val smsto = qr("SMSTO:4085551234:On my way") as Code.Sms
        assertEquals("4085551234" to "On my way", smsto.number to smsto.message)
        val sms = qr("sms:4085551234?body=Hi%20there") as Code.Sms
        assertEquals("4085551234" to "Hi there", sms.number to sms.message)

        val geo = qr("geo:37.3352,-121.8811?q=SJSU") as Code.Location
        assertEquals(37.3352, geo.latitude, 1e-9)
        assertEquals(-121.8811, geo.longitude, 1e-9)
        assertTrue(qr("geo:nowhere") is Code.Text)
    }

    @Test
    fun `contacts from vCard and MeCard`() {
        val vcard = qr(
            "BEGIN:VCARD\nVERSION:3.0\nN:Lopez;Alejandro\nFN:Alejandro Lopez\nTEL;TYPE=CELL:+14085551234\nEMAIL:ale@sjsu.edu\nEND:VCARD"
        ) as Code.Contact
        assertEquals("Alejandro Lopez", vcard.name)
        assertEquals(listOf("+14085551234"), vcard.phones)
        assertEquals(listOf("ale@sjsu.edu"), vcard.emails)

        // Without FN the structured name is used.
        assertEquals("Ada Lovelace", (qr("BEGIN:VCARD\nN:Lovelace;Ada\nEND:VCARD") as Code.Contact).name)

        val mecard = qr("MECARD:N:Lopez,Alejandro;TEL:111;TEL:222;EMAIL:a@b.com;;") as Code.Contact
        assertEquals("Alejandro Lopez", mecard.name)
        assertEquals(listOf("111", "222"), mecard.phones)
        assertEquals(listOf("a@b.com"), mecard.emails)
    }

    @Test
    fun `product barcodes and plain text`() {
        assertEquals(Code.Product("0123456789012", Format.EAN13), Code.parse("0123456789012", Format.EAN13))
        assertEquals(Code.Product("036000291452", Format.UPCA), Code.parse("036000291452", Format.UPCA))
        // Other barcodes keep their text, even if it looks like a number.
        assertEquals(Code.Text("A-1234", Format.Code128), Code.parse("A-1234", Format.Code128))
        assertEquals(Code.Text("Hello class", Format.QR), qr("Hello class"))
    }

    @Test
    fun `format groups`() {
        assertTrue(Format.EAN13.isBarcode)
        assertFalse(Format.QR.isBarcode)
        assertEquals(13, Format.All.size)
        assertTrue(Format.Products.all { it.isBarcode })
    }

    @Test
    fun `barcodes need two matching frames, 2D codes one`() {
        val confirmer = Confirmer()
        assertTrue(confirmer.offer("https://sjsu.edu", Format.QR))
        assertFalse("not again while it stays in view", confirmer.offer("https://sjsu.edu", Format.QR))

        confirmer.reset()
        assertFalse(confirmer.offer("0123456789012", Format.EAN13))
        assertTrue(confirmer.offer("0123456789012", Format.EAN13))
        assertFalse(confirmer.offer("0123456789012", Format.EAN13))

        // A misread in between starts over.
        confirmer.reset()
        assertFalse(confirmer.offer("0123456789012", Format.EAN13))
        assertFalse(confirmer.offer("0123456789018", Format.EAN13))
        assertTrue(confirmer.offer("0123456789018", Format.EAN13))
    }
}
