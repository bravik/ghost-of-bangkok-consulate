package ru.bravik

import com.twocaptcha.TwoCaptcha
import com.twocaptcha.captcha.Normal

class AntiCaptcha(
    private val apiKey: String
) {
    fun solve(pathToImage: String): String {
        println("Solving captcha: $pathToImage")

        val solver = TwoCaptcha(apiKey)
        val captcha = Normal(pathToImage)

        solver.solve(captcha)

        println("Captcha solved: " + captcha.code)

        return captcha.code
    }
}
