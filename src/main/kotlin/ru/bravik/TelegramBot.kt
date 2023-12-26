package ru.bravik

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.telegramError
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode.MARKDOWN_V2
import com.github.kotlintelegrambot.logging.LogLevel
import org.apache.commons.io.input.ReversedLinesFileReader
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class TelegramBot(
    private val apiKey: String,
) {
    private var bot: Bot? = null;

    fun start() {
        if (bot !== null) {
            throw RuntimeException("Bot is already started")
        }

        bot = bot {
            token = apiKey
            timeout = 30
            logLevel = LogLevel.Network.Body

            dispatch {
                /**
                 * Send latest N entries from checks log
                 */
                command("log") {
                    var linesCount = 20;
                    if (args.isNotEmpty()) {
                        val linesCountArg = args[0].toIntOrNull()
                        if (linesCountArg !== null) {
                            linesCount = linesCountArg
                        }
                    }

                    println("Received log command")
                    val tempDir = Paths.get("./tmp") // Replace with your directory path
                    val log = File(tempDir.toAbsolutePath().toString(), "checks.log")

                    val lines = mutableListOf<String>()

                    val reader = ReversedLinesFileReader(log, Charset.defaultCharset())

                    var line: String?

                    while (true) {
                        line = try {
                            reader.readLine()
                        } catch (e: IOException) {
                            null
                        }

                        if (line === null || lines.size >= linesCount) {
                            break
                        }

                        lines.add(line)
                    }

                    // Format dates in log line, convert to Asia/Bangkok timezone
                    lines.reverse()
                    val logLines = lines.joinToString("\n") {
                        val logParts = it.split(" - ", limit = 2)
                        val logDateTime = logParts.firstOrNull() // Extracting date and time

                        val localDateTime = LocalDateTime.parse(
                            logDateTime,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS")
                        )

                        val zonedDateTimeBangkok = localDateTime.atZone(ZoneId.systemDefault())
                            .withZoneSameInstant(ZoneId.of("Asia/Bangkok"))

                        val formatterBangkok = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        val formattedBangkok = formatterBangkok.format(zonedDateTimeBangkok)

                        "`$formattedBangkok - ${logParts[1]}`"
                    }

                    val markdown = "**Here is a log of latest checks \\(Asia\\Bangkok time\\):**\n$logLines"

                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = markdown,
                        parseMode = MARKDOWN_V2,
                    )
                }

                command("start") {
                    val result = bot.sendMessage(chatId = ChatId.fromId(update.message!!.chat.id), text = "Bot started")

                    result.fold(
                        {
                            // do something here with the response
                        },
                        {
                            // do something with the error
                        },
                    )
                }

                text("ping") {
                    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Pong")
                }

                telegramError {
                    println(error.getErrorMessage())
                }
            }
        }

        bot!!.startPolling()
    }

    fun stop() {
        bot?.stopPolling()
    }

    fun sendMessage(message: String, isSilent: Boolean = false) {
        bot?.sendMessage(
            ChatId.fromId(129324712), // TODO Take from somewhere else
            text = message,
            disableNotification = isSilent
        )
    }

    fun sendMarkdownMessage(message: String, isSilent: Boolean = false) {
        bot?.sendMessage(
            ChatId.fromId(129324712), // TODO Take from somewhere else
            text = message,
            disableNotification = isSilent,
            parseMode = MARKDOWN_V2,
        )
    }
}