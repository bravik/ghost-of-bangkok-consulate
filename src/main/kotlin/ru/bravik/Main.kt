package ru.bravik

import io.ktor.util.escapeHTML
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main() = runBlocking {
    val telegramBotToken = System.getenv("TELEGRAM_BOT_TOKEN")
    val antiCaptchaToken = System.getenv("ANTICAPTCHA_TOKEN")

    requireNotNull(telegramBotToken)
    requireNotNull(antiCaptchaToken)

    println("Boooooo... Weird sounds are starting to come from the consular attic...")

    val bot = TelegramBot(telegramBotToken)

    val telegramJob = launch {
        bot.start()
    }

    delay(3000)

    bot.sendMessage("Starting flying over the consular window...");

    val antiCaptcha = AntiCaptcha(antiCaptchaToken)

    try {
        // For Debug
//        while (true) {
//            println("Idle...")
//            delay(3000)
//        }

        ConsularChecker(
            antiCaptcha,
            foundSlotsCallback = {bot.sendMessage("Found available slot!")},
            noSlotsCallback = {bot.sendMessage("FUCK! No slots again", true)},
        ).start(
            intervalInSeconds = 60,
            intervalInSecondsOutsideWorkingHours = 60 * 30
        )
    }  catch (e: Throwable) {
        bot.sendMarkdownMessage("""
            *Checker failed with exception:*
            ```java
            ${e.stackTraceToString().escapeHTML()}
            ```
            Stopping\.\.\.
        """.trimIndent()
        )

        // Exit
        delay(1000)
        bot.stop()
        telegramJob.cancel()
        coroutineContext.cancel()
        exitProcess(1)
    }
}

