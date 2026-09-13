package com.aura.music.core.network

import com.aura.music.core.license.LicenseRepository
import okhttp3.Interceptor
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkTimeInterceptor @Inject constructor(
    private val licenseRepository: LicenseRepository
) : Interceptor {

    private val httpDateFormat by lazy {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        try {
            val dateHeader = response.header("Date")
            if (!dateHeader.isNullOrBlank()) {
                val parsedDate = httpDateFormat.parse(dateHeader)
                if (parsedDate != null && parsedDate.time > 0) {
                    licenseRepository.updateNetworkTime(parsedDate.time)
                }
            }
        } catch (_: Exception) {
            // Ignorar errores de parseo para no interrumpir el flujo de red principal
        }

        return response
    }
}
