package com.apoorvdarshan.verceltics.ui.netlify

import com.apoorvdarshan.verceltics.data.account.AccountCipher
import com.apoorvdarshan.verceltics.data.account.AtomicBytesStore
import com.apoorvdarshan.verceltics.data.account.SealedPayload
import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.netlify.NetlifyBuild
import com.apoorvdarshan.verceltics.data.netlify.NetlifyBuildControls
import com.apoorvdarshan.verceltics.data.netlify.NetlifyConnectionRepository
import com.apoorvdarshan.verceltics.data.netlify.NetlifyConnectionStore
import com.apoorvdarshan.verceltics.data.netlify.NetlifyDataSource
import com.apoorvdarshan.verceltics.data.netlify.NetlifyDeployment
import com.apoorvdarshan.verceltics.data.netlify.NetlifyDomain
import com.apoorvdarshan.verceltics.data.netlify.NetlifyDomainKind
import com.apoorvdarshan.verceltics.data.netlify.NetlifyProfile
import com.apoorvdarshan.verceltics.data.netlify.NetlifyReadApi
import com.apoorvdarshan.verceltics.data.netlify.NetlifySite
import com.apoorvdarshan.verceltics.data.netlify.NetlifySiteDetails
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNetlifyUiGatewayTest {
    @Test
    fun cancellationDuringFirstEncryptedSaveRollsBackEmptySlot() = runBlocking {
        val fixture = Fixture(FakeApi(siteCount = 1))
        try {
            fixture.store.blockNextWrite()
            val job = async(Dispatchers.Default) {
                fixture.gateway.connect(SecretValue.of("cancelled-token"))
            }
            assertTrue(fixture.store.awaitWriteStarted())

            job.cancel()
            fixture.store.releaseBlockedWrite()
            job.cancelAndJoin()

            assertNull(fixture.repository.load())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancelledReplacementRestoresExactPriorEncryptedRecord() = runBlocking {
        val fixture = Fixture(FakeApi(siteCount = 1))
        try {
            fixture.gateway.connect(SecretValue.of("original-token")).getOrThrow()
            val originalEnvelope = checkNotNull(fixture.store.snapshotBytes())
            val original = checkNotNull(fixture.repository.load())

            fixture.store.blockNextWrite()
            val job = async(Dispatchers.Default) {
                fixture.gateway.connect(SecretValue.of("replacement-token"))
            }
            assertTrue(fixture.store.awaitWriteStarted())
            job.cancel()
            fixture.store.releaseBlockedWrite()
            job.cancelAndJoin()

            assertArrayEquals(originalEnvelope, fixture.store.snapshotBytes())
            val restored = checkNotNull(fixture.repository.load())
            assertEquals(original.account.id, restored.account.id)
            assertEquals(original.account.personalToken, restored.account.personalToken)
            assertEquals(original.account.createdAtMillis, restored.account.createdAtMillis)
            assertEquals(original.cachedSnapshot, restored.cachedSnapshot)
            originalEnvelope.fill(0)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancellationAfterSaveButBeforeAcceptRestoresExactPriorRecord() = runBlocking {
        var blockBeforeAccept = false
        val acceptStarted = CompletableDeferred<Unit>()
        val acceptRelease = CompletableDeferred<Unit>()
        val fixture = Fixture(
            api = FakeApi(siteCount = 1),
            beforeAccept = {
                if (blockBeforeAccept) {
                    acceptStarted.complete(Unit)
                    acceptRelease.await()
                }
            },
        )
        try {
            fixture.gateway.connect(SecretValue.of("original-token")).getOrThrow()
            val originalEnvelope = checkNotNull(fixture.store.snapshotBytes())
            blockBeforeAccept = true

            val job = async(Dispatchers.Default) {
                fixture.gateway.connect(SecretValue.of("replacement-token"))
            }
            acceptStarted.await()
            job.cancelAndJoin()

            assertArrayEquals(originalEnvelope, fixture.store.snapshotBytes())
            assertEquals(
                SecretValue.of("original-token"),
                checkNotNull(fixture.repository.load()).account.personalToken,
            )
            originalEnvelope.fill(0)
        } finally {
            acceptRelease.complete(Unit)
            fixture.close()
        }
    }

    @Test
    fun cancellationAfterAcceptKeepsCommittedConnection() = runBlocking {
        val accepted = CompletableDeferred<Unit>()
        val releaseAfterAccept = CompletableDeferred<Unit>()
        val fixture = Fixture(
            api = FakeApi(siteCount = 1),
            afterAccept = {
                accepted.complete(Unit)
                releaseAfterAccept.await()
            },
        )
        try {
            val job = async(Dispatchers.Default) {
                fixture.gateway.connect(SecretValue.of("committed-token"))
            }
            accepted.await()
            job.cancel()
            releaseAfterAccept.complete(Unit)
            job.cancelAndJoin()

            assertEquals(
                SecretValue.of("committed-token"),
                checkNotNull(fixture.repository.load()).account.personalToken,
            )
        } finally {
            releaseAfterAccept.complete(Unit)
            fixture.close()
        }
    }

    @Test
    fun completeLargeInventoryIsBoundedWithoutClaimingProviderPartialFailure() = runBlocking {
        val fixture = Fixture(FakeApi(siteCount = 150))
        try {
            val dashboard = fixture.gateway.connect(SecretValue.of("token")).getOrThrow()

            assertEquals(100, dashboard.sites.size)
            assertEquals(150, dashboard.loadedSiteCount)
            assertTrue(dashboard.providerInventoryComplete)
            assertTrue(dashboard.inventoryTruncatedForDisplay)
            assertTrue(dashboard.isPartial)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun siteWorkspaceKeepsIndependentDetailsPartialDeployAndFailedBuildTruth() = runBlocking {
        val fixture = Fixture(
            FakeApi(
                siteCount = 1,
                partialDeployments = true,
                failBuilds = true,
            ),
        )
        try {
            fixture.gateway.connect(SecretValue.of("token")).getOrThrow()

            val workspace = fixture.gateway.loadSite("site-0").getOrThrow()

            assertTrue(workspace.details is NetlifyResourceUi.Available)
            val details = (workspace.details as NetlifyResourceUi.Available).value
            assertEquals("https://github.com/example/site", details.buildControls?.repositoryUrl)
            assertEquals(listOf("main"), details.buildControls?.allowedBranches)
            assertEquals("published-deploy", details.publishedDeployment?.id)
            assertEquals(100, workspace.deployments.items.size)
            assertEquals(100, workspace.deployments.loadedItemCount)
            assertFalse(workspace.deployments.providerCollectionComplete)
            assertTrue(workspace.deployments.warning!!.contains("reached", ignoreCase = true))
            assertTrue(workspace.builds.items.isEmpty())
            assertFalse(workspace.builds.providerCollectionComplete)
            assertTrue(workspace.builds.warning!!.contains("reached", ignoreCase = true))
        } finally {
            fixture.close()
        }
    }

    private class Fixture(
        api: FakeApi,
        beforeAccept: suspend () -> Unit = {},
        afterAccept: suspend () -> Unit = {},
    ) {
        val store = BlockingAtomicBytesStore()
        val repository = NetlifyConnectionRepository(store, TestAccountCipher())
        private val networkExecutor = Executors.newFixedThreadPool(4)
        private val storageExecutor = Executors.newSingleThreadExecutor()
        val gateway = NativeNetlifyUiGateway(
            connectionStore = NetlifyConnectionStore(repository, nowMillis = { 42L }),
            dataSource = NetlifyDataSource(api, nowMillis = { 42L }),
            networkExecutor = networkExecutor,
            storageExecutor = storageExecutor,
            beforeAcceptValidatedConnection = beforeAccept,
            afterAcceptValidatedConnection = afterAccept,
        )

        fun close() {
            store.releaseBlockedWrite()
            networkExecutor.shutdownNow()
            storageExecutor.shutdownNow()
        }
    }

    private class FakeApi(
        siteCount: Int,
        private val partialDeployments: Boolean = false,
        private val failBuilds: Boolean = false,
    ) : NetlifyReadApi {
        private val sites = List(siteCount) { index -> site(index) }

        override fun newValidatePersonalTokenCall(token: SecretValue): CancelableCall<NetlifyProfile> =
            FixedCall { NetlifyProfile("account", "Netlify Account", "owner@example.com", null) }

        override fun newListSitesPageCall(
            token: SecretValue,
            page: Int,
            perPage: Int,
        ): CancelableCall<List<NetlifySite>> = FixedCall {
            val from = ((page - 1) * perPage).coerceAtMost(sites.size)
            val to = (from + perPage).coerceAtMost(sites.size)
            sites.subList(from, to)
        }

        override fun newSiteDetailsCall(
            token: SecretValue,
            siteId: String,
        ): CancelableCall<NetlifySiteDetails> = FixedCall {
            NetlifySiteDetails(
                site = sites.first { it.id == siteId },
                domains = listOf(NetlifyDomain("example.com", NetlifyDomainKind.CUSTOM)),
                buildControls = NetlifyBuildControls(
                    buildsStopped = false,
                    repositoryUrl = "https://github.com/example/site",
                    repositoryPath = null,
                    repositoryBranch = "main",
                    baseDirectory = null,
                    publishDirectory = "dist",
                    functionsDirectory = null,
                    buildCommand = "npm run build",
                    allowedBranches = listOf("main"),
                    provider = "github",
                ),
                publishedDeployment = NetlifyDeployment(
                    id = "published-deploy",
                    title = "Published deploy",
                    status = "ready",
                    createdAtMillis = 42L,
                    url = "https://example.netlify.app",
                    branch = "main",
                    commitMessage = "Publish",
                ),
            )
        }

        override fun newListDeploymentsPageCall(
            token: SecretValue,
            siteId: String,
            page: Int,
            perPage: Int,
        ): CancelableCall<List<NetlifyDeployment>> = FixedCall {
            if (partialDeployments && page > 1) throw IOException("provider cannot be reached")
            if (partialDeployments) {
                List(perPage) { index -> deployment(index) }
            } else if (page == 1) {
                listOf(deployment(0))
            } else {
                emptyList()
            }
        }

        override fun newListBuildsPageCall(
            token: SecretValue,
            siteId: String,
            page: Int,
            perPage: Int,
        ): CancelableCall<List<NetlifyBuild>> = FixedCall {
            if (failBuilds) throw IOException("provider cannot be reached")
            if (page == 1) listOf(build(0)) else emptyList()
        }

        override fun newBuildCall(
            token: SecretValue,
            buildId: String,
        ): CancelableCall<NetlifyBuild> = FixedCall { build(0).copy(id = buildId) }

        private fun site(index: Int) = NetlifySite(
            id = "site-$index",
            name = "Site $index",
            subtitle = "site-$index.netlify.app",
            url = "https://site-$index.netlify.app",
            status = "current",
            updatedAtMillis = 42L,
            adminUrl = null,
        )

        private fun deployment(index: Int) = NetlifyDeployment(
            id = "deploy-$index",
            title = "Deploy $index",
            status = "ready",
            createdAtMillis = 42L,
            url = null,
            branch = "main",
            commitMessage = null,
        )

        private fun build(index: Int) = NetlifyBuild(
            id = "build-$index",
            deploymentId = "deploy-$index",
            commitSha = "abc$index",
            isDone = true,
            error = null,
            createdAtMillis = 42L,
        )
    }

    private class FixedCall<T>(private val block: () -> T) : CancelableCall<T> {
        @Volatile
        private var cancelled = false

        override fun execute(): T {
            if (cancelled) throw java.util.concurrent.CancellationException()
            return block()
        }

        override fun cancel() {
            cancelled = true
        }
    }

    private class BlockingAtomicBytesStore : AtomicBytesStore {
        private val blockLock = Any()
        private var shouldBlockNextWrite = false
        private var writeStarted = CountDownLatch(0)
        private var allowWriteToFinish = CountDownLatch(0)

        @Volatile
        private var bytes: ByteArray? = null

        override fun read(): ByteArray? = bytes?.copyOf()

        override fun write(bytes: ByteArray) {
            val blockedWrite = synchronized(blockLock) {
                if (!shouldBlockNextWrite) null else {
                    shouldBlockNextWrite = false
                    writeStarted to allowWriteToFinish
                }
            }
            blockedWrite?.let { (started, finish) ->
                started.countDown()
                while (true) {
                    try {
                        finish.await()
                        break
                    } catch (_: InterruptedException) {
                        // Model an atomic replacement already past its cancellable point.
                    }
                }
            }
            this.bytes = bytes.copyOf()
        }

        override fun delete() {
            bytes = null
        }

        fun blockNextWrite() = synchronized(blockLock) {
            writeStarted = CountDownLatch(1)
            allowWriteToFinish = CountDownLatch(1)
            shouldBlockNextWrite = true
        }

        fun awaitWriteStarted(): Boolean = writeStarted.await(5, TimeUnit.SECONDS)

        fun releaseBlockedWrite() = allowWriteToFinish.countDown()

        fun snapshotBytes(): ByteArray? = bytes?.copyOf()
    }

    private class TestAccountCipher : AccountCipher {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): SealedPayload =
            SealedPayload(ByteArray(12) { 7 }, transform(plaintext, associatedData))

        override fun decrypt(payload: SealedPayload, associatedData: ByteArray): ByteArray =
            transform(payload.ciphertext(), associatedData)

        private fun transform(bytes: ByteArray, associatedData: ByteArray): ByteArray =
            ByteArray(bytes.size) { index ->
                (bytes[index].toInt() xor associatedData[index % associatedData.size].toInt()).toByte()
            }
    }
}
