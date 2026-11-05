package br.com.ryuukossei.gymprogress.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

/** Erros de comunicação com a API, já com mensagens para mostrar na tela. */
sealed class ApiException(message: String) : Exception(message) {
    class Unauthorized : ApiException("Token inválido ou revogado. Confira o token informado.")
    class Network(cause: IOException) : ApiException("Não foi possível conectar ao servidor. Verifique o endereço e a internet.") {
        init { initCause(cause) }
    }
    class Http(val code: Int) : ApiException("O servidor respondeu com erro $code.")
    class InvalidResponse(cause: Throwable, detail: String? = null) : ApiException(
        "Resposta inesperada do servidor." + (detail?.let { " $it" } ?: "")
    ) {
        init { initCause(cause) }
    }
}

/** Cliente da API do GymProgress (endpoints de sincronização). */
class GymApi {

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
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
            .header("Authorization", "Bearer ${validatedToken(token)}")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> throw ApiException.Unauthorized()
                    !response.isSuccessful -> throw ApiException.Http(response.code)
                }
                val body = response.body?.readLimitedString() ?: throw ApiException.InvalidResponse(IllegalStateException("Corpo vazio"), "O corpo da exportação está vazio.")
                json.decodeFromString(SyncExportDto.serializer(), body)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: SerializationException) {
            throw ApiException.InvalidResponse(e, serializationDetail(e))
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
            .header("Authorization", "Bearer ${validatedToken(token)}")
            .header("Accept", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> throw ApiException.Unauthorized()
                    !response.isSuccessful -> throw ApiException.Http(response.code)
                }
                val text = response.body?.readLimitedString().orEmpty()
                if (text.isBlank() && response.code == 204) SyncPushResultDto()
                else if (text.isBlank()) throw ApiException.InvalidResponse(IllegalStateException("Resposta de envio vazia"))
                else json.decodeFromString(SyncPushResultDto.serializer(), text)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: SerializationException) {
            throw ApiException.InvalidResponse(e, serializationDetail(e))
        } catch (e: IllegalArgumentException) {
            throw ApiException.InvalidResponse(e)
        } catch (e: IOException) {
            throw ApiException.Network(e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun serializationDetail(error: SerializationException): String =
        if (error is MissingFieldException) {
            // Apenas nomes conhecidos do contrato; nunca inclui o JSON ou o token.
            val allowed = setOf("exportedAt", "targetMuscles", "exercises", "workouts", "workoutExercises", "sessions", "sets",
                "publicId", "name", "id", "category", "workoutPublicId", "exercisePublicId", "repetitions", "weight",
                "restTimeSeconds", "order", "startedAt", "sessionPublicId", "workoutExercisePublicId", "setNumber",
                "performedRepetitions", "performedWeight")
            val fields = error.missingFields.filter { it in allowed }
            if (fields.isEmpty()) "Há campos obrigatórios ausentes na exportação."
            else "Campos obrigatórios ausentes: ${fields.joinToString(", ")}."
        } else {
            val path = Regex("at path: ([^\\s]+)").find(error.message.orEmpty())?.groupValues?.get(1)
                ?.takeIf { it.length <= 160 && it.startsWith("$") && it.all { c -> c.isLetterOrDigit() || c in "_.$[]" } }
            if (path != null) "Formato JSON incompatível no campo $path."
            else "O JSON recebido tem um formato incompatível com o contrato de sincronização."
        }

    private fun validatedToken(token: String): String {
        val value = token.trim()
        require(value.isNotEmpty() && value.length <= 4096 && value.all { it.code in 33..126 }) { "Formato do token inválido." }
        return value
    }

    /** Limita também respostas comprimidas, cujo tamanho anunciado pode ser desconhecido. */
    private fun ResponseBody.readLimitedString(): String {
        val limit = 32L * 1024 * 1024
        if (contentLength() > limit) throw ApiException.InvalidResponse(IllegalStateException("Resposta excede 32 MiB"), "A resposta excede o limite de 32 MiB.")
        val source = source()
        source.request(limit + 1)
        if (source.buffer.size > limit) throw ApiException.InvalidResponse(IllegalStateException("Resposta excede 32 MiB"), "A resposta excede o limite de 32 MiB.")
        return source.readUtf8()
    }

    companion object {
        /** O token só pode trafegar via HTTPS, sem credenciais, consulta ou fragmento na URL. */
        fun normalizeBaseUrl(baseUrl: String): String {
            val input = baseUrl.trim()
            require(input.isNotEmpty()) { "Informe o endereço HTTPS do servidor." }
            val candidate = if ("://" in input) input else "https://$input"
            val url = requireNotNull(candidate.toHttpUrlOrNull()) { "Endereço do servidor inválido." }
            require(url.isHttps) { "Use HTTPS para proteger o token de acesso." }
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
                "O endereço do servidor não pode conter credenciais, parâmetros ou fragmentos."
            }
            return url.toString().trimEnd('/')
        }
    }
}
