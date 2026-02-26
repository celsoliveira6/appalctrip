package com.appalctrip.notifications

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.CookieManager
import java.net.CookiePolicy
import java.security.MessageDigest

class SiteNotificationsScraper {

    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(okhttp3.JavaNetCookieJar(cookieManager))
        .followRedirects(true)
        .build()

    fun fetchNotificationsFingerprint(username: String, password: String): String {
        val loginPage = executeGet(LOGIN_URL)
        val loginDoc = Jsoup.parse(loginPage)

        val form = loginDoc.selectFirst("form")
            ?: throw IllegalStateException("Não foi possível encontrar o formulário de login.")

        val action = form.absUrl("action").ifBlank { LOGIN_URL }
        val inputs = form.select("input")

        val bodyBuilder = FormBody.Builder()
        inputs.forEach { input ->
            val name = input.attr("name")
            if (name.isNotBlank()) {
                bodyBuilder.add(name, input.attr("value"))
            }
        }

        val userField = findUserFieldName(inputs)
        val passField = findPasswordFieldName(inputs)

        bodyBuilder.add(userField, username)
        bodyBuilder.add(passField, password)

        executePost(action, bodyBuilder.build())

        val notificationsHtml = executeGet(NOTIFICATIONS_URL)
        val document = Jsoup.parse(notificationsHtml)

        val normalizedText = document.select("main, .content, body").text().replace(Regex("\\s+"), " ").trim()

        return sha256(normalizedText)
    }

    private fun findUserFieldName(inputs: List<org.jsoup.nodes.Element>): String {
        return inputs
            .firstOrNull {
                val name = it.attr("name").lowercase()
                name.contains("user") || name.contains("email") || name.contains("login")
            }
            ?.attr("name")
            ?: "username"
    }

    private fun findPasswordFieldName(inputs: List<org.jsoup.nodes.Element>): String {
        return inputs
            .firstOrNull {
                val type = it.attr("type").lowercase()
                val name = it.attr("name").lowercase()
                type == "password" || name.contains("pass")
            }
            ?.attr("name")
            ?: "password"
    }

    private fun executeGet(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            ensureSuccess(response)
            return response.body?.string().orEmpty()
        }
    }

    private fun executePost(url: String, body: FormBody): String {
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { response ->
            ensureSuccess(response)
            return response.body?.string().orEmpty()
        }
    }

    private fun ensureSuccess(response: Response) {
        if (!response.isSuccessful) {
            throw IllegalStateException("Erro HTTP ${response.code}")
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val LOGIN_URL = "https://myoffice.icligo.com/account/login".toHttpUrl().toString()
        private val NOTIFICATIONS_URL = "https://myoffice.icligo.com/account/notifications".toHttpUrl().toString()
    }
}
