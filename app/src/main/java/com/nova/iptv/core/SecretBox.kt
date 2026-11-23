package com.nova.iptv.core

/** Encrypts / decrypts small secrets (profile passwords). */
interface SecretBox {
    fun encrypt(plain: String): String
    fun decrypt(encoded: String): String
}
