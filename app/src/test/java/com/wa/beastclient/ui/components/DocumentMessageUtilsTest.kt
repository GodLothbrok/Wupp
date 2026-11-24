package com.wa.beastclient.ui.components

import com.wa.beastclient.data.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocumentMessageUtilsTest {

    private fun sampleMsg(
        msgType: String = "DOCUMENT",
        mimeType: String = "application/pdf",
        body: String = "ALPs Notification (14.11.2026) 1258.pdf",
        fileSize: Long = 7_235_174L,
        localMediaPath: String? = null
    ) = MessageEntity(
        id = "msg_doc_1",
        chatJid = "12345@g.us",
        senderJid = "923060507370@s.whatsapp.net",
        senderName = "Lecturer",
        isFromMe = false,
        timestamp = 1_700_000_000_000L,
        body = body,
        msgType = msgType,
        mimeType = mimeType,
        fileSize = fileSize,
        localMediaPath = localMediaPath
    )

    @Test
    fun isDocumentMessage_detectsDocumentsAndSkipsPlainTextOrMedia() {
        assertTrue(isDocumentMessage(sampleMsg()))
        assertTrue(isDocumentMessage(sampleMsg(msgType = "UNKNOWN", mimeType = "application/pdf", fileSize = 0L)))
        assertFalse(isDocumentMessage(sampleMsg(msgType = "TEXT", mimeType = "text/plain", body = "Check notes.pdf", fileSize = 0L)))
        assertFalse(isDocumentMessage(sampleMsg(msgType = "IMAGE", mimeType = "image/jpeg", body = "photo.pdf")))
        assertFalse(isDocumentMessage(sampleMsg(msgType = "CONTACT", mimeType = "text/vcard", body = "John Doe.vcf")))
    }

    @Test
    fun isContactMessage_identifiesContactsCorrectly() {
        val contactMsg = sampleMsg(msgType = "CONTACT", mimeType = "text/vcard", body = "Jane Doe")
        assertTrue(isContactMessage(contactMsg))

        val vcardMimeMsg = sampleMsg(msgType = "DOCUMENT", mimeType = "text/vcard", body = "Jane Doe.vcf")
        assertTrue(isContactMessage(vcardMimeMsg))
        assertFalse(isDocumentMessage(vcardMimeMsg))

        val vcfExtMsg = sampleMsg(msgType = "DOCUMENT", mimeType = "application/octet-stream", body = "John.vcf")
        assertTrue(isContactMessage(vcfExtMsg))
        assertFalse(isDocumentMessage(vcfExtMsg))

        val deletedContactMsg = contactMsg.copy(isDeletedBySender = true)
        assertFalse(isContactMessage(deletedContactMsg))

        val regularMsg = sampleMsg(msgType = "TEXT", mimeType = "text/plain", body = "Hello")
        assertFalse(isContactMessage(regularMsg))
    }

    @Test
    fun extractPhoneFromVCardOrMessage_parsesTelField() {
        val tempVcf = File.createTempFile("contact_", ".vcf").apply {
            writeText("BEGIN:VCARD\nVERSION:3.0\nFN:Alice\nTEL;TYPE=CELL:+1234567890\nEND:VCARD\n")
            deleteOnExit()
        }
        val msgWithVcf = sampleMsg(msgType = "CONTACT", localMediaPath = tempVcf.absolutePath)
        assertEquals("+1234567890", extractPhoneFromVCardOrMessage(msgWithVcf))

        val msgWithoutVcf = sampleMsg(msgType = "CONTACT", localMediaPath = null)
        assertEquals(null, extractPhoneFromVCardOrMessage(msgWithoutVcf))
    }

    @Test
    fun resolveDocumentMimeType_infersPdfFromBodyAndMagicBytes() {
        val noMimeMsg = sampleMsg(mimeType = "application/octet-stream", body = "report.pdf")
        assertEquals("application/pdf", resolveDocumentMimeType(noMimeMsg))

        val tempPdf = File.createTempFile("wupp_doc_", ".bin").apply {
            writeBytes("%PDF-1.7\n".toByteArray(Charsets.US_ASCII))
            deleteOnExit()
        }
        val magicOnlyMsg = sampleMsg(mimeType = "", body = "Unnamed file")
        assertEquals("application/pdf", resolveDocumentMimeType(magicOnlyMsg, tempPdf))
        assertTrue(isPdfDocument(magicOnlyMsg, tempPdf))
    }

    @Test
    fun formatDocumentMetaLine_matchesNativeLayouts() {
        assertEquals(
            "65 pages · 6.9 MB · PDF",
            formatDocumentMetaLine(
                pageCount = 65,
                fileSize = (6.9 * 1024 * 1024).toLong(),
                extLabel = "PDF",
                isDownloading = false,
                errorMsg = null,
                hasLocalFile = true
            )
        )
        assertEquals(
            "Downloading... · 6.9 MB · PDF",
            formatDocumentMetaLine(
                pageCount = null,
                fileSize = (6.9 * 1024 * 1024).toLong(),
                extLabel = "PDF",
                isDownloading = true,
                errorMsg = null,
                hasLocalFile = false
            )
        )
    }

    @Test
    fun buildDocumentExportFileName_preservesSafeDocumentTitleAndExtension() {
        val tempFile = File("/data/local/tmp/msg_1.bin")
        assertEquals(
            "ALPs Notification (14.11.2026) 1258.pdf",
            buildDocumentExportFileName(sampleMsg(), tempFile)
        )
    }
}
