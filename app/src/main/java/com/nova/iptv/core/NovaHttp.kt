package com.nova.iptv.core

import okhttp3.OkHttpClient
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object NovaHttp {
    const val USER_AGENT = "NOVA IPTV/1.0 (Android)"

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(
                if (request.header("User-Agent") == null) {
                    request.newBuilder().header("User-Agent", USER_AGENT).build()
                } else {
                    request
                }
            )
        }
        .build()
}

/** User-facing (French) message; never includes URLs, which may contain credentials. */
fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException -> "Serveur introuvable. Vérifiez l'adresse et la connexion Internet."
    is SocketTimeoutException -> "Le serveur met trop de temps à répondre."
    is ConnectException -> "Connexion au serveur impossible."
    is SSLException -> "Erreur de connexion sécurisée (SSL)."
    is IOException, is IllegalStateException -> message?.takeIf { it.isNotBlank() } ?: "Erreur réseau."
    else -> "Erreur inattendue (${this::class.java.simpleName})."
}
