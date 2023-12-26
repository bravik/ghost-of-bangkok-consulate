package ru.bravik

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.headers
import io.ktor.http.parameters
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.cert.X509Certificate
import java.time.LocalTime
import java.time.ZoneId
import javax.net.ssl.X509TrustManager
import kotlin.jvm.Throws

class ConsularChecker(
    private val antiCaptcha: AntiCaptcha,
    private val foundSlotsCallback: () -> Unit,
    private val noSlotsCallback: () -> Unit,
) {
    private val logger = LoggerFactory.getLogger("checks")
    private val dumper = LoggerFactory.getLogger("page-dumps")

    companion object {
        const val BASE_URL = "https://bangkok.kdmid.ru"
    }

    suspend fun start(
        intervalInSeconds: Int,
        intervalInSecondsOutsideWorkingHours: Int,
    ) {
        println("-----------------------------------")
        println("Starting to fly over consulate...")
        println("-----------------------------------")

        val client = createClient()

        var session = startSession(client, antiCaptcha);

        while (true) {
            println("-----------------------------------")
            println("Checking...")
            println("-----------------------------------")
            try {
                checkSlots(client, session.url, session.id)
                //  Sleep
                if (isWithinWorkingHours()) {
                    delay((intervalInSeconds * 1000).toLong())
                } else {
                    delay((intervalInSecondsOutsideWorkingHours * 1000).toLong())
                }
            } catch (e: SessionBrokenException) {
                println("Session is lost. Restarting...")
                session = startSession(client, antiCaptcha)
            }
        }
    }

    private fun createClient(): HttpClient {
        return HttpClient(CIO) {
            install(Logging)

            engine {
                https {
                    // This will not check SSL certificate at all, but that is actually not necessary
                    trustManager = object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate?> = arrayOf()
                        override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                        override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                    }
                }
            }
            // Configure the User-Agent header
            defaultRequest {
                headers {
                    append(
                        HttpHeaders.Accept,
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                    )
                    append(HttpHeaders.AcceptEncoding, "gzip, deflate, br")
                    append(HttpHeaders.AcceptLanguage, "ru,ru-RU;q=0.9,en-US;q=0.8,en;q=0.7,zh;q=0.6")
                    append(HttpHeaders.CacheControl, "max-age=0")
                    append(HttpHeaders.Connection, "keep-alive")
                    append(HttpHeaders.Host, "bangkok.kdmid.ru")
                    append(
                        HttpHeaders.UserAgent,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    append("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                    append("sec-ch-ua-mobile", "?0")
                    append("sec-ch-ua-platform", "\"Windows\"")
                }
            }
        }
    }

    data class Session(
        val id: String,
        val url: String,
    )

    /**
     * TODO Do this for each user when /starting the bot
     * TODO Or group by service to reduce queries for similar services?
     */
    private suspend fun startSession(client: HttpClient, antiCaptcha: AntiCaptcha): Session {
        println("Starting new session...")
        // Get initial page with captcha
        val httpResponse = client.get("$BASE_URL/queue/orderinfo.aspx?id=69780&cd=ee44fdc5&ems=C6A14E31")
        val responseBody = httpResponse.bodyAsText()

        println("STATUS: ${httpResponse.status.value}")
        println("BODY: ${responseBody}")

        // Parse
        var document = Jsoup.parse(responseBody)

        // Parse some form values
        var element = document.select("input#__EVENTVALIDATION").first()
        var eventValidationField = element?.attr("value") ?: ""
        element = document.select("input#__VIEWSTATE").first()
        var viewStateField = element?.attr("value") ?: ""
        print("__EVENTVALIDATION: $eventValidationField")
        print("__VIEWSTATE: $viewStateField")

        // Parse captcha image src
        println("Parsing captcha image src...")
        val imageElement = document.select("div.inp img").firstOrNull()
        val captchaSrc = imageElement?.attr("src") ?: throw RuntimeException("Captcha image not found")
        val captchaUrl = "$BASE_URL/queue/$captchaSrc"
        println("Captcha url: $captchaUrl")

        // Create directory for captcha image files
        val tempDir = Paths.get("./tmp") // Replace with your directory path
        if (!Files.exists(tempDir)) {
            val createdDir = Files.createDirectory(tempDir)
            println("Directory created: ${createdDir.toAbsolutePath()}")
        } else {
            println("Directory already exists")
        }

        // Fetch captcha image
        println("Fetching captcha image...")

        val outputFile = File(tempDir.toAbsolutePath().toString(), "captcha.jpg")

        if (outputFile.exists()) {
            outputFile.delete()
        }

        var sessionId: String? = null
        client.prepareGet(captchaUrl).execute { response ->
            // Retrieve session id
            val aSessionId = response.headers[HttpHeaders.SetCookie]
                ?.split(";")
                ?.find {
                    it.startsWith("ASP.NET_SessionId")
                }

            if (aSessionId != null) {
                sessionId = aSessionId.split("=")[1]
                println("Got session ID: $sessionId")
            } else {
                throw RuntimeException("ASP.NET_SessionId header not found")
            }

            val channel: ByteReadChannel = response.body()
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.isEmpty) {
                    val bytes = packet.readBytes()
                    outputFile.appendBytes(bytes)
                    println("Received ${outputFile.length()} bytes from ${response.contentLength()}")
                }
            }
            println("A file saved to ${outputFile.path}")
        }
        require(sessionId != null)

        println("Captcha image downloaded to: ${outputFile.absolutePath}")

        // Solve captcha:
        val captcha = antiCaptcha.solve(outputFile.absolutePath)

        // Submit form
        println("Submitting form...")
        val response = client.submitForm(
            url = "$BASE_URL/queue/orderinfo.aspx?id=69780&cd=ee44fdc5&ems=C6A14E31",
            formParameters = parameters {
                append("__EVENTTARGET", "")
                append("__EVENTARGUMENT", "")
                append("__VIEWSTATE", viewStateField)
                append("__EVENTVALIDATION", eventValidationField)
                append("ctl00\$MainContent\$txtID", "69780")
                append("ctl00\$MainContent\$txtUniqueID", "EE44FDC5")
                append("ctl00\$MainContent\$txtCode", captcha)
                append("ctl00\$MainContent\$ButtonA", "%D0%94%D0%B0%D0%BB%D0%B5%D0%B5")
            },
            block = {
                header(HttpHeaders.Cookie, "ASP.NET_SessionId=$sessionId")
            }
        )

        val submitResponse = response.bodyAsText()
        println(submitResponse)
        require(response.status == HttpStatusCode.OK)

        // Reparse some params
        // Parse
        document = Jsoup.parse(submitResponse)

        // Parse some form values again
        element = document.select("input#__EVENTVALIDATION").first()
        eventValidationField = element?.attr("value") ?: ""
        element = document.select("input#__VIEWSTATE").first()
        viewStateField = element?.attr("value") ?: ""

        // Submit again:
        println("Submitting form again...")
        val response2 = client.submitForm(
            url = "$BASE_URL/queue/orderinfo.aspx?id=69780&cd=ee44fdc5&ems=C6A14E31",
            formParameters = parameters {
                append("__EVENTTARGET", "")
                append("__EVENTARGUMENT", "")
                append("__VIEWSTATE", viewStateField)
                append("__EVENTVALIDATION", eventValidationField)
                append("ctl00\$MainContent\$ButtonB.x", "133")
                append("ctl00\$MainContent\$ButtonB.y", "30")
            },
            block = {
                header(HttpHeaders.Cookie, "ASP.NET_SessionId=$sessionId")
            }
        )
        val submit2Response = response2.bodyAsText()
        println(submit2Response)
        require(response2.status == HttpStatusCode.Found)

        // Get redirect page
        val redirectUrl = response2.headers[HttpHeaders.Location]
        println("Check URL: $redirectUrl")

        return Session(
            "$sessionId",
            "$BASE_URL/" + redirectUrl
        )
    }

    class SessionBrokenException() : RuntimeException()

    @Throws(SessionBrokenException::class)
    suspend fun checkSlots(client: HttpClient, url: String, sessionId: String) {
        val httpResponse = client.get(url) {
            header(HttpHeaders.Cookie, "ASP.NET_SessionId=$sessionId")
        }
        val response = httpResponse.bodyAsText()
        println(response)
        // Check session is not broken

        // This is an email field from first page. If session was broken than it will redirect to that page
        if (response.contains("<input name=\"ctl00\$MainContent\$txtEmail\" type=\"text\"")) {
            throw SessionBrokenException()
        }

        // Calendar widget should be on our target page
        if (!response.contains("id=\"ctl00_MainContent_Calendar\"")) {
            throw SessionBrokenException()
        }

        // Ok, seems like we're there.
        if (response.contains("<p>Извините, но в настоящий момент на интересующее Вас консульское действие в системе предварительной записи нет свободного времени.</p>")) {
            println("FUCK! No slots again")
            logger.info("No slots")
//            noSlotsCallback()
        } else {
            println("HAS AVAILABLE SLOTS!")
            logger.info("HAS AVAILABLE SLOTS")
            foundSlotsCallback()
            // Dump page to analyze contents in case of success
            dumper.info("Has slot page dump...")
            dumper.info(response)
        }
    }

    private fun isWithinWorkingHours(): Boolean {
        val bangkokZoneId = ZoneId.of("Asia/Bangkok")
        val currentTime = LocalTime.now(bangkokZoneId)

        val startWorkingHour = LocalTime.of(6, 0)
        val endWorkingHour = LocalTime.of(20, 0)

        return currentTime in startWorkingHour..endWorkingHour
    }
}