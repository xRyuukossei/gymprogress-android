package br.com.ryuukossei.gymprogress.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Erros de comunicação com a API, já com mensagens para mostrar na tela. */
sealed class ApiException(message: String) : Exception(message) {
    class Unauthorized : ApiException("Token inválido ou revogado. Confira o token informado.")
    class Network(cause: IOException) : ApiException("Não foi possível conectar ao servidor. Verifique o endereço e a internet.") {
        init { initCause(cause) }
    }
    class Http(val code: Int) : ApiException("O servidor respondeu com erro $code.")
    class InvalidResponse(cause: Throwable) : ApiException("Resposta inesperada do servidor.") {
        init { initCause(cause) }
    }
}

/** Cliente da API do GymProgress (endpoints de sincronização). */
class GymApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Baixa todos os dados do servidor (usado na importação/restauração). */
    suspend fun export(baseUrl: String, token: String): SyncExportDto = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(normalizeBaseUrl(baseUrl) + "/api/sync/export")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> throw ApiException.Unauthorized()
                    !response.isSuccessful -> throw ApiException.Http(response.code)
                }
                val body = response.body?.string() ?: throw ApiException.InvalidResponse(IllegalStateException("Corpo vazio"))
                json.decodeFromString(SyncExportDto.serializer(), body)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: SerializationException) {
            throw ApiException.InvalidResponse(e)
        } catch (e: IllegalArgumentException) {
            // URL mal formada ou JSON inválido
            throw ApiException.InvalidResponse(e)
        } catch (e: IOException) {
            throw ApiException.Network(e)
        }
    }

    /** Envia um lote de alterações do celular. */
    suspend fun push(baseUrl: String, token: String, batch: SyncPushDto): SyncPushResultDto = withContext(Dispatchers.IO) {
        val body = json.encodeToString(SyncPushDto.serializer(), batch)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(normalizeBaseUrl(baseUrl) + "/api/sync/push")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> throw ApiException.Unauthorized()
                    !response.isSuccessful -> throw ApiException.Http(response.code)
                }
                val text = response.body?.string().orEmpty()
                if (text.isBlank()) SyncPushResultDto() else json.decodeFromString(SyncPushResultDto.serializer(), text)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: SerializationException) {
            throw ApiException.InvalidResponse(e)
        } catch (e: IllegalArgumentException) {
            throw ApiException.InvalidResponse(e)
        } catch (e: IOException) {
            throw ApiException.Network(e)
        }
    }

    companion object {
        fun normalizeBaseUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }
}
