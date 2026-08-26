package com.apoorvdarshan.verceltics.data.vercel

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import com.apoorvdarshan.verceltics.data.network.HttpResponse
import com.apoorvdarshan.verceltics.data.network.ProviderHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class VercelApiTest {
    @Test
    fun validateUsesExactEndpointAndBearerCredential() {
        val client = RecordingHttpClient(HttpResponse(200, byteArrayOf(1), emptyMap()))
        val parser = FakeParser()
        val api = VercelApi(client, parser)
        val token = SecretValue.of("vercel-secret")

        val user = api.newValidatePersonalTokenCall(token).execute()

        assertEquals("/v2/user", client.relativePath)
        assertEquals(token, client.bearerToken)
        assertEquals("user_123", user.id)
    }

    @Test
    fun projectsUsesV9EndpointBoundedLimitAndCursor() {
        val client = RecordingHttpClient(HttpResponse(200, byteArrayOf(1), emptyMap()))
        val api = VercelApi(client, FakeParser())

        val page = api.newListProjectsCall(
            token = SecretValue.of("vercel-secret"),
            limit = 50,
            until = "cursor_123",
        ).execute()

        assertEquals("/v9/projects", client.relativePath)
        assertEquals(listOf("limit" to "50", "until" to "cursor_123"), client.query)
        assertEquals("project_123", page.projects.single().id)
        assertThrows(IllegalArgumentException::class.java) {
            api.newListProjectsCall(SecretValue.of("token"), limit = 101)
        }
    }

    @Test
    fun authenticationFailureDoesNotExposeTokenOrResponseBody() {
        val tokenText = "never-leak-this-token"
        val client = RecordingHttpClient(
            HttpResponse(401, tokenText.encodeToByteArray(), emptyMap()),
        )
        val error = assertThrows(VercelApiException::class.java) {
            VercelApi(client, FakeParser(errorCode = "forbidden"))
                .newValidatePersonalTokenCall(SecretValue.of(tokenText))
                .execute()
        }

        assertEquals(401, error.statusCode)
        assertEquals("forbidden", error.errorCode)
        assertFalse(error.toString().contains(tokenText))
    }

    @Test
    fun validatedUserCreatesRawVercelAccount() {
        val api = VercelApi(RecordingHttpClient(HttpResponse(200, byteArrayOf(1), emptyMap())), FakeParser())
        val account = api.accountForValidatedUser(
            user = FakeParser.USER,
            token = SecretValue.of("token"),
            nowMillis = 42L,
        )

        assertEquals("vercel", account.providerId)
        assertEquals("user_123", account.id)
        assertEquals("Apoorv", account.displayName)
        assertEquals(42L, account.createdAtMillis)
    }

    private class RecordingHttpClient(
        private val response: HttpResponse,
    ) : ProviderHttpClient {
        var relativePath: String? = null
        var query: List<Pair<String, String>> = emptyList()
        var bearerToken: SecretValue? = null

        override fun newGetCall(
            relativePath: String,
            queryParameters: List<Pair<String, String>>,
            bearerToken: SecretValue?,
            headers: Map<String, String>,
        ): CancelableCall<HttpResponse> {
            this.relativePath = relativePath
            this.query = queryParameters
            this.bearerToken = bearerToken
            return object : CancelableCall<HttpResponse> {
                override fun execute(): HttpResponse = response
                override fun cancel() = Unit
            }
        }
    }

    private class FakeParser(
        private val errorCode: String? = null,
    ) : VercelJsonParser {
        override fun parseUser(bytes: ByteArray): VercelUser = USER

        override fun parseProjects(bytes: ByteArray): VercelProjectsPage = VercelProjectsPage(
            projects = listOf(
                VercelProject(
                    id = "project_123",
                    name = "verceltics",
                    framework = "nextjs",
                    createdAtMillis = 1L,
                    updatedAtMillis = 2L,
                ),
            ),
            nextCursor = null,
        )

        override fun parseErrorCode(bytes: ByteArray): String? = errorCode

        companion object {
            val USER = VercelUser(
                id = "user_123",
                username = "apoorvdarshan",
                email = "apoorv@example.com",
                name = "Apoorv",
                avatarUrl = null,
            )
        }
    }
}
