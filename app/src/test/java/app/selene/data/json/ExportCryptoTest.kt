package app.selene.data.json

import app.selene.domain.model.DayLog
import app.selene.domain.model.Mood
import app.selene.domain.model.PeriodLog
import app.selene.domain.model.SymptomLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.security.SecureRandom
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExportCryptoTest {
    private val d1 = LocalDate.of(2026, 11, 9)
    private val sample = listOf(
        DayLog(d1, period = PeriodLog(d1, 2, 2), symptoms = SymptomLog(d1, moods = setOf(Mood.SAD), note = "Mal au genou")),
    )
    private val plain = JsonExport.encode(sample, OffsetDateTime.of(2026, 11, 15, 13, 47, 0, 0, ZoneOffset.ofHours(1)))

    /** Fast iterations: the real count is exercised by the sample file test. */
    private fun encrypt(text: String = plain, password: String = "lune rousse") =
        ExportCrypto.encrypt(text, password, SecureRandom(), iterations = ExportCrypto.ITERATIONS_RANGE.first)

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun pbkdf2MatchesReferenceVectors() {
        // RFC 7914 §11 and values from Python's hashlib.pbkdf2_hmac.
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            hex(ExportCrypto.deriveKey("passwd", "salt".toByteArray(), 1, 64)),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            hex(ExportCrypto.deriveKey("password", "salt".toByteArray(), 4096)),
        )
        // Non-ASCII, NFD input: normalised to NFC before UTF-8 encoding.
        assertEquals(
            "bd1671184cee7e227057f009bafce27c4ce2b296d9c8f17616399c52f85abebc",
            hex(ExportCrypto.deriveKey("Lune rousse é", "saltsaltsaltsalt".toByteArray(), 1000)),
        )
    }

    @Test
    fun encryptThenDecryptThenImportIsIdentical() {
        val encrypted = encrypt()
        assertTrue(ExportCrypto.isEncrypted(encrypted))
        assertFalse("Mal au genou" in encrypted, "no plaintext in the file")
        assertFalse("2026-11-09" in encrypted, "no dates in the file")
        val result = ExportCrypto.decrypt(encrypted, "lune rousse")
        assertIs<ExportCrypto.DecryptResult.Success>(result)
        assertEquals(plain, result.plaintext)
        assertEquals(sample, (JsonImport.parse(result.plaintext) as ImportResult.Success).days)
    }

    @Test
    fun saltAndIvAreRandom() {
        assertTrue(encrypt() != encrypt())
    }

    @Test
    fun wrongPasswordIsReported() {
        assertEquals(ExportCrypto.DecryptResult.WrongPassword, ExportCrypto.decrypt(encrypt(), "lune rouge"))
        assertEquals(ExportCrypto.DecryptResult.WrongPassword, ExportCrypto.decrypt(encrypt(), ""))
    }

    @Test
    fun alteredDataIsRejected() {
        val root = Json.parseToJsonElement(encrypt()).jsonObject
        val data = Base64.getDecoder().decode((root["data"] as JsonPrimitive).content)
        data[0] = (data[0].toInt() xor 1).toByte()
        val altered = JsonObject(root + ("data" to JsonPrimitive(Base64.getEncoder().encodeToString(data))))
        assertEquals(ExportCrypto.DecryptResult.WrongPassword, ExportCrypto.decrypt(altered.toString(), "lune rousse"))
    }

    @Test
    fun plainExportsAreNotEncrypted() {
        assertFalse(ExportCrypto.isEncrypted(plain))
        assertFalse(ExportCrypto.isEncrypted("not json"))
        assertFalse(ExportCrypto.isEncrypted("[]"))
    }

    @Test
    fun invalidEnvelopesAreRefused() {
        fun withField(key: String, value: kotlinx.serialization.json.JsonElement): String {
            val root = Json.parseToJsonElement(encrypt()).jsonObject
            return JsonObject(root + (key to value)).toString()
        }
        fun decrypt(text: String) = ExportCrypto.decrypt(text, "lune rousse")

        assertEquals(ExportCrypto.DecryptResult.Invalid(ImportError.NotJson), decrypt("{"))
        assertEquals(ExportCrypto.DecryptResult.Invalid(ImportError.NotSeleneExport), decrypt(plain))
        assertEquals(
            ExportCrypto.DecryptResult.Invalid(ImportError.UnsupportedVersion(2, 1)),
            decrypt(withField("version", JsonPrimitive(2))),
        )
        assertEquals(ExportCrypto.DecryptResult.Invalid(ImportError.MissingVersion), decrypt(withField("version", JsonPrimitive("x"))))

        val root = Json.parseToJsonElement(encrypt()).jsonObject
        val kdf = root.getValue("kdf").jsonObject
        val tooMany = JsonObject(root + ("kdf" to JsonObject(kdf + ("iterations" to JsonPrimitive(100_000_000)))))
        assertIs<ExportCrypto.DecryptResult.Invalid>(decrypt(tooMany.toString()))
        val otherKdf = JsonObject(root + ("kdf" to JsonObject(kdf + ("name" to JsonPrimitive("scrypt")))))
        assertIs<ExportCrypto.DecryptResult.Invalid>(decrypt(otherKdf.toString()))
        assertIs<ExportCrypto.DecryptResult.Invalid>(decrypt(withField("data", JsonPrimitive("%%%"))))
        assertIs<ExportCrypto.DecryptResult.Invalid>(decrypt(JsonObject(root - "cipher").toString()))
    }

    @Test
    fun passwordLength() {
        assertFalse(ExportCrypto.isAcceptablePassword("1234567"))
        assertTrue(ExportCrypto.isAcceptablePassword("12345678"))
        assertFalse(ExportCrypto.isAcceptablePassword("🌕🌕🌕🌕🌕🌕🌕"), "counted in code points")
    }

    /** docs/exemple-export-chiffre.json, password « lune rousse 2026 », real iteration count. */
    @Test
    fun theDocumentedSampleFileDecrypts() {
        val text = javaClass.getResource("/exemple-export-chiffre.json")!!.readText()
        val result = ExportCrypto.decrypt(text, "lune rousse 2026")
        assertIs<ExportCrypto.DecryptResult.Success>(result)
        assertIs<ImportResult.Success>(JsonImport.parse(result.plaintext))
        assertEquals(ExportCrypto.ITERATIONS, Json.parseToJsonElement(text).jsonObject["kdf"]!!.jsonObject["iterations"].toString().toInt())
    }
}
