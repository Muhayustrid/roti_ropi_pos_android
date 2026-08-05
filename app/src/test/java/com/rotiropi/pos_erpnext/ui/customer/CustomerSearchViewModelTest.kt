package com.rotiropi.pos_erpnext.ui.customer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.rotiropi.pos_erpnext.data.CustomerSearchResult
import com.rotiropi.pos_erpnext.data.CustomerSearchPage as RepositoryPage
import com.rotiropi.pos_erpnext.data.CustomerSearchFailure
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerSearchViewModelTest {
    @Test
    fun `selector first open schedules one debounced blank search and completed reopen is no op`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = viewModel(dispatcher, requests)
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))

        viewModel.onSelectorOpened()
        advanceTimeBy(299); runCurrent()
        assertTrue(requests.isEmpty())
        advanceTimeBy(1); runCurrent()

        assertEquals(listOf(CustomerSearchRequest("", "PROFILE", 0, 20)), requests)
        viewModel.onSelectorOpened()
        advanceTimeBy(300); runCurrent()
        assertEquals(1, requests.size)
    }

    @Test
    fun `selector open after profile rebind dispatches blank search for new profile only`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = viewModel(dispatcher, requests)
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE-A", "WALK-A"))

        viewModel.onQueryChanged("old")
        advanceTimeBy(300); runCurrent()
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE-B", "WALK-B"))
        viewModel.onSelectorOpened()
        advanceTimeBy(300); runCurrent()

        assertEquals("", viewModel.state.value.query)
        assertTrue(
            "previous identity query must never be sent under the rebound profile",
            requests.none { it.posProfile == "PROFILE-B" && it.query == "old" },
        )
        assertEquals(
            listOf(
                CustomerSearchRequest("old", "PROFILE-A", 0, 20),
                CustomerSearchRequest("", "PROFILE-B", 0, 20),
            ),
            requests,
        )
    }

    @Test
    fun `selector open scheduled before profile rebind is invalidated by rebind`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = viewModel(dispatcher, requests)
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE-A", "WALK-A"))

        viewModel.onSelectorOpened()
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE-B", "WALK-B"))
        advanceTimeBy(300); runCurrent()
        assertTrue(requests.isEmpty())

        viewModel.onSelectorOpened()
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf(CustomerSearchRequest("", "PROFILE-B", 0, 20)), requests)
    }

    @Test
    fun `selector open racing profile rebind never reuses previous identity query`() {
        val executor = Executors.newFixedThreadPool(3)
        val dispatcher = executor.asCoroutineDispatcher()
        val requests = mutableListOf<CustomerSearchRequest>()
        val oldSearchEntered = CountDownLatch(1)
        val releaseOldSearch = CountDownLatch(1)
        try {
            val viewModel = CustomerSearchViewModel(
                dispatcher = dispatcher,
                search = { request, _ ->
                    synchronized(requests) { requests += request }
                    if (request.query == "old") {
                        oldSearchEntered.countDown()
                        check(releaseOldSearch.await(2, TimeUnit.SECONDS))
                    }
                    CustomerSearchResult.Success(RepositoryPage(emptyList(), 0, 20, false))
                },
                debounceMillis = 0,
            )
            viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE-A", "WALK-A"))
            viewModel.onQueryChanged("old")
            assertTrue("old search did not enter repository", oldSearchEntered.await(2, TimeUnit.SECONDS))

            val ready = CountDownLatch(2)
            val release = CountDownLatch(1)
            val done = CountDownLatch(2)
            Thread {
                ready.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                viewModel.onSelectorOpened()
                done.countDown()
            }.start()
            Thread {
                ready.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE-B", "WALK-B"))
                done.countDown()
            }.start()
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            release.countDown()
            assertTrue("selector open or rebind thread did not finish", done.await(2, TimeUnit.SECONDS))

            releaseOldSearch.countDown()
            awaitState("old completion not settled") { !viewModel.state.value.loading }

            val dispatched = synchronized(requests) { requests.toList() }
            assertTrue(
                "previous identity query must never be sent under the rebound profile",
                dispatched.none { it.posProfile == "PROFILE-B" && it.query == "old" },
            )
            assertEquals("", viewModel.state.value.query)
        } finally {
            releaseOldSearch.countDown()
            dispatcher.close()
            executor.terminateGracefully()
        }
    }

    @Test
    fun `profile default row marked by backend selects walk in and preserves display name`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler), mutableListOf())
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
        viewModel.onWalkInDisplayNameChanged("Ayu")

        viewModel.selectCustomer(CustomerRecord("WALK", "Walk In", null, true))

        assertEquals(CustomerSelection.WalkIn("WALK", "Ayu"), viewModel.state.value.selection)
    }

    @Test
    fun `another row incorrectly marked default selects registered`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler), mutableListOf())
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))

        viewModel.selectCustomer(CustomerRecord("OTHER", "Other", "0812", true))

        assertEquals(CustomerSelection.Registered("OTHER", "Other", "0812"), viewModel.state.value.selection)
    }

    @Test
    fun `registered to profile default row transitions to walk in`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler), mutableListOf())
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
        viewModel.selectCustomer(CustomerRecord("REGISTERED", "Registered", null, false))

        viewModel.selectCustomer(CustomerRecord("WALK", "Walk In", null, true))

        assertEquals(CustomerSelection.WalkIn("WALK", ""), viewModel.state.value.selection)
    }

    @Test
    fun `walk in to registered transition clears display name`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler), mutableListOf())
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
        viewModel.onWalkInDisplayNameChanged("Must clear")

        viewModel.selectCustomer(CustomerRecord("REGISTERED", "Registered", null, false))

        assertEquals(CustomerSelection.Registered("REGISTERED", "Registered", null), viewModel.state.value.selection)
    }

    @Test
    fun `old success paused after authority evaluation cannot publish after clear`() {
        val state = completionPausedThenInvalidate(
            result = success("OLD"),
            invalidate = { it.clear() },
        )

        assertEquals(CustomerSearchUiState(), state)
    }

    @Test
    fun `old failure paused after authority evaluation cannot publish after clear`() {
        val state = completionPausedThenInvalidate(
            result = CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable),
            invalidate = { it.clear() },
        )

        assertEquals(CustomerSearchUiState(), state)
    }

    @Test
    fun `logout interleaving invalidates cancels and clears before old completion`() {
        val state = completionPausedThenInvalidate(
            result = success("OLD"),
            invalidate = {
                it.invalidateAuthority()
                it.cancelActiveRequest()
                it.clearUi()
            },
        )

        assertEquals(CustomerSearchUiState(), state)
    }

    @Test
    fun `profile change interleaving rejects old completion`() {
        val replacement = CustomerSearchIdentity("cashier-a", "PROFILE-B", "WALK-B")
        val state = completionPausedThenInvalidate(
            result = success("OLD"),
            invalidate = { it.bind(replacement) },
        )

        assertEquals(CustomerSelection.WalkIn("WALK-B", ""), state.selection)
        assertTrue(state.customers.isEmpty())
        assertEquals(null, state.error)
    }

    @Test
    fun `cashier change interleaving rejects old completion`() {
        val replacement = CustomerSearchIdentity("cashier-b", "PROFILE-A", "WALK-B")
        val state = completionPausedThenInvalidate(
            result = CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable),
            invalidate = { it.bind(replacement) },
        )

        assertEquals(CustomerSelection.WalkIn("WALK-B", ""), state.selection)
        assertTrue(state.customers.isEmpty())
        assertEquals(null, state.error)
    }

    @Test
    fun `retry completion wins over older non cooperative request completion`() {
        val executor = Executors.newFixedThreadPool(2)
        val dispatcher = executor.asCoroutineDispatcher()
        val oldEntered = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        val retryEntered = CountDownLatch(1)
        val retryEvaluated = CountDownLatch(1)
        val releaseRetry = CountDownLatch(1)
        val identity = CustomerSearchIdentity("cashier", "PROFILE", "WALK")
        val requests = mutableListOf<CustomerSearchRequest>()
        try {
            val viewModel = CustomerSearchViewModel(
                dispatcher = dispatcher,
                search = { request, _ ->
                    val invocation = synchronized(requests) { requests += request; requests.size }
                    if (invocation == 1) {
                        oldEntered.countDown()
                        while (true) {
                            try {
                                if (releaseOld.await(2, TimeUnit.SECONDS)) break
                            } catch (_: InterruptedException) {
                                // Deliberately non-cooperative transport simulation.
                            }
                        }
                        success("OLD")
                    } else {
                        retryEntered.countDown()
                        success("RETRY")
                    }
                },
                debounceMillis = 0,
                completionCheckpoint = { authority ->
                    if (authority.requestId == 2L) {
                        retryEvaluated.countDown()
                        check(releaseRetry.await(2, TimeUnit.SECONDS))
                    }
                },
            )
            viewModel.bind(identity)
            viewModel.onQueryChanged("ayu")
            assertTrue("old request did not enter repository", oldEntered.await(2, TimeUnit.SECONDS))
            viewModel.publishForAuthority(
                CustomerSearchAuthority(identity, viewModel.activeGeneration, "ayu", 0, 1),
                CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable),
            )

            viewModel.retry()
            assertTrue("retry did not enter repository", retryEntered.await(2, TimeUnit.SECONDS))
            assertTrue("retry completion did not evaluate authority", retryEvaluated.await(2, TimeUnit.SECONDS))
            releaseOld.countDown()
            releaseRetry.countDown()
            awaitState("retry completion not published") { !viewModel.state.value.loading }

            assertEquals(listOf("RETRY"), viewModel.state.value.customers.map(CustomerRecord::id))
            assertEquals(null, viewModel.state.value.error)
        } finally {
            releaseOld.countDown()
            releaseRetry.countDown()
            dispatcher.close()
            executor.terminateGracefully()
        }
    }

    @Test
    fun `default selection uses profile customer and waits 300 ms`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val calls = mutableListOf<CustomerSearchRequest>()
        val viewModel = viewModel(dispatcher, calls)

        viewModel.bind(CustomerSearchIdentity("cashier-a", "OUTLET-01", "WALK-IN"))
        viewModel.onQueryChanged("")
        advanceTimeBy(299)

        assertEquals(CustomerSelection.WalkIn("WALK-IN", ""), viewModel.state.value.selection)
        assertTrue(calls.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(CustomerSearchRequest("", "OUTLET-01", 0, 20)), calls)
    }

    @Test
    fun `changed query resets records and stale generation cannot publish`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val calls = mutableListOf<CustomerSearchRequest>()
        val viewModel = viewModel(dispatcher, calls)
        viewModel.bind(CustomerSearchIdentity("cashier-a", "OUTLET-01", "WALK-IN"))

        viewModel.onQueryChanged("ayu")
        advanceTimeBy(300)
        runCurrent()
        viewModel.onQueryChanged("bima")
        advanceTimeBy(300)
        runCurrent()

        assertEquals("bima", viewModel.state.value.query)
        assertTrue(viewModel.state.value.customers.isEmpty())
        assertEquals(listOf("ayu", "bima"), calls.map { it.query })
    }

    @Test
    fun `registered selection clears walk-in name and logout clears memory state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(dispatcher, mutableListOf())
        viewModel.bind(CustomerSearchIdentity("cashier-a", "OUTLET-01", "WALK-IN"))
        viewModel.onWalkInDisplayNameChanged("Ayu")
        viewModel.selectCustomer(CustomerRecord("CUST-1", "Ayu Bakery", "0812", false))

        assertEquals(CustomerSelection.Registered("CUST-1", "Ayu Bakery", "0812"), viewModel.state.value.selection)
        viewModel.selectWalkIn()
        assertEquals(CustomerSelection.WalkIn("WALK-IN", ""), viewModel.state.value.selection)

        viewModel.onQueryChanged("query")
        viewModel.clear()
        assertEquals("", viewModel.state.value.query)
        assertTrue(viewModel.state.value.customers.isEmpty())
        assertEquals(null, viewModel.state.value.selection)
    }

    @Test
    fun `query change and clear cancel active request`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cancellations = mutableListOf<ApiCallCancellation>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { _, cancellation ->
            cancellations += cancellation
            CustomerSearchResult.Success(RepositoryPage(emptyList(), 0, 20, false))
        })
        viewModel.bind(CustomerSearchIdentity("cashier-a", "OUTLET-01", "WALK"))
        viewModel.onQueryChanged("ayu")
        advanceTimeBy(300); runCurrent()
        viewModel.onQueryChanged("bima")
        assertTrue(cancellations.single().isCancelled)
        advanceTimeBy(300); runCurrent()
        viewModel.clear()
        assertTrue(cancellations.last().isCancelled)
    }

    @Test
    fun `profile and cashier bind cancel old request and reset default walk in`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cancellations = mutableListOf<ApiCallCancellation>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { _, cancellation ->
            cancellations += cancellation
            CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable)
        })
        viewModel.bind(CustomerSearchIdentity("cashier-a", "OUTLET-01", "WALK-A"))
        viewModel.onQueryChanged("ayu"); advanceTimeBy(300); runCurrent()
        viewModel.bind(CustomerSearchIdentity("cashier-a", "OUTLET-02", "WALK-B"))
        assertTrue(cancellations.single().isCancelled)
        assertEquals(CustomerSelection.WalkIn("WALK-B", ""), viewModel.state.value.selection)
        viewModel.onQueryChanged("bima"); advanceTimeBy(300); runCurrent()
        viewModel.bind(CustomerSearchIdentity("cashier-b", "OUTLET-02", "WALK-C"))
        assertTrue(cancellations.last().isCancelled)
        assertEquals(CustomerSelection.WalkIn("WALK-C", ""), viewModel.state.value.selection)
    }

    @Test
    fun `server metadata drives next offset and page failure keeps records`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        var failPage = false
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request
            if (failPage) CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable) else CustomerSearchResult.Success(
                RepositoryPage(listOf(com.rotiropi.pos_erpnext.data.Customer("CUST-${request.start}", "Customer", null, false)), request.start + 5, 7, true)
            )
        })
        viewModel.bind(CustomerSearchIdentity("cashier-a", "OUTLET-01", "WALK"))
        viewModel.onQueryChanged(""); advanceTimeBy(300); runCurrent()
        failPage = true
        viewModel.loadMore(); runCurrent()
        assertEquals(12, requests.last().start)
        assertEquals(1, viewModel.state.value.customers.size)
        assertEquals(CustomerSearchError.Unavailable, viewModel.state.value.pageError)
        viewModel.retry(); runCurrent()
        assertEquals(12, requests.last().start)
    }

    @Test
    fun `initial limit zero publishes recoverable protocol error without records`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request
            CustomerSearchResult.Success(RepositoryPage(
                listOf(com.rotiropi.pos_erpnext.data.Customer("INVALID", "Invalid", null, false)),
                0,
                0,
                true,
            ))
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))

        viewModel.onQueryChanged(""); advanceTimeBy(300); runCurrent()

        assertEquals(listOf(0), requests.map(CustomerSearchRequest::start))
        assertFalse(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.customers.isEmpty())
        assertEquals(CustomerSearchError.Protocol, viewModel.state.value.error)
        assertFalse(viewModel.state.value.hasMore)
        viewModel.retry(); runCurrent()
        assertEquals(listOf(0, 0), requests.map(CustomerSearchRequest::start))
    }

    @Test
    fun `negative initial start publishes recoverable protocol error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = CustomerSearchViewModel(dispatcher, search = { _, _ ->
            CustomerSearchResult.Success(RepositoryPage(
                listOf(com.rotiropi.pos_erpnext.data.Customer("INVALID", "Invalid", null, false)),
                -1,
                20,
                false,
            ))
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))

        viewModel.onQueryChanged(""); advanceTimeBy(300); runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.customers.isEmpty())
        assertEquals(CustomerSearchError.Protocol, viewModel.state.value.error)
    }

    @Test
    fun `page limit zero retains records failed offset and explicit page retry`() = runTest {
        invalidPageMetadataScenario { request -> RepositoryPage(emptyList(), request.start, 0, true) }
    }

    @Test
    fun `repeated page offset is recoverable protocol failure`() = runTest {
        invalidPageMetadataScenario { RepositoryPage(emptyList(), 0, 20, true) }
    }

    @Test
    fun `regressive page offset is recoverable protocol failure`() = runTest {
        invalidPageMetadataScenario { request -> RepositoryPage(emptyList(), request.start - 10, 5, true) }
    }

    @Test
    fun `page metadata that does not advance is recoverable protocol failure`() = runTest {
        invalidPageMetadataScenario { request -> RepositoryPage(emptyList(), request.start - 1, 1, true) }
    }

    @Test
    fun `page retry after invalid metadata consumes corrected response at exact failed offset`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        var invocation = 0
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request
            invocation++
            when (invocation) {
                1 -> CustomerSearchResult.Success(RepositoryPage(
                    listOf(com.rotiropi.pos_erpnext.data.Customer("FIRST", "First", null, false)),
                    0,
                    20,
                    true,
                ))
                2 -> CustomerSearchResult.Success(RepositoryPage(emptyList(), request.start, 0, true))
                else -> CustomerSearchResult.Success(RepositoryPage(
                    listOf(com.rotiropi.pos_erpnext.data.Customer("SECOND", "Second", null, false)),
                    request.start,
                    20,
                    false,
                ))
            }
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
        viewModel.onQueryChanged(""); advanceTimeBy(300); runCurrent()
        viewModel.loadMore(); runCurrent()

        assertEquals(CustomerSearchError.Protocol, viewModel.state.value.pageError)
        assertEquals(listOf("FIRST"), viewModel.state.value.customers.map(CustomerRecord::id))
        viewModel.retry(); runCurrent()

        assertEquals(listOf(0, 20, 20), requests.map(CustomerSearchRequest::start))
        assertEquals(listOf("FIRST", "SECOND"), viewModel.state.value.customers.map(CustomerRecord::id))
        assertEquals(null, viewModel.state.value.pageError)
    }

    @Test
    fun `pagination offset overflow is recoverable protocol failure`() = runTest {
        invalidPageMetadataScenario {
            RepositoryPage(emptyList(), Int.MAX_VALUE - 5, 20, true)
        }
    }

    @Test
    fun `has more false and repeated successful query do not dispatch load more`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request
            CustomerSearchResult.Success(RepositoryPage(listOf(com.rotiropi.pos_erpnext.data.Customer("A", "A", null, false)), 0, 20, false))
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
        viewModel.onQueryChanged("ayu"); advanceTimeBy(300); runCurrent()
        viewModel.loadMore()
        viewModel.onQueryChanged(" ayu "); runCurrent()
        assertEquals(1, requests.size)
    }

    @Test
    fun `repeated successful normalized empty query is a no op`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request
            CustomerSearchResult.Success(RepositoryPage(emptyList(), 0, 20, false))
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))

        viewModel.onQueryChanged("ayu"); advanceTimeBy(300); runCurrent()
        viewModel.onQueryChanged(" ayu "); advanceTimeBy(300); runCurrent()

        assertEquals(1, requests.size)
        assertEquals("ayu", viewModel.state.value.query)
        assertTrue(viewModel.state.value.customers.isEmpty())
        assertFalse(viewModel.state.value.loading)
        assertEquals(null, viewModel.state.value.error)
        assertEquals(null, viewModel.state.value.pageError)
        assertFalse(viewModel.state.value.hasMore)
    }

    @Test
    fun `successful empty query dispatches again after query changes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request
            CustomerSearchResult.Success(RepositoryPage(emptyList(), 0, 20, false))
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))

        viewModel.onQueryChanged("ayu"); advanceTimeBy(300); runCurrent()
        viewModel.onQueryChanged("bima"); advanceTimeBy(300); runCurrent()
        viewModel.onQueryChanged("ayu"); advanceTimeBy(300); runCurrent()

        assertEquals(listOf("ayu", "bima", "ayu"), requests.map(CustomerSearchRequest::query))
    }

    @Test
    fun `initial failure clears old records and retry is explicit`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var fail = false
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request
            if (fail) CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable) else CustomerSearchResult.Success(RepositoryPage(listOf(com.rotiropi.pos_erpnext.data.Customer("A", "A", null, false)), 0, 20, false))
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
        viewModel.onQueryChanged("a"); advanceTimeBy(300); runCurrent()
        fail = true
        viewModel.onQueryChanged("b"); advanceTimeBy(300); runCurrent()
        assertTrue(viewModel.state.value.customers.isEmpty())
        assertEquals(CustomerSearchError.Unavailable, viewModel.state.value.error)
        assertEquals(2, requests.size)
        viewModel.retry(); runCurrent()
        assertEquals(CustomerSearchRequest("b", "PROFILE", 0, 20), requests.last())
    }

    @Test
    fun `repeating failed normalized query waits for explicit retry`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request
            CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable)
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
        viewModel.onQueryChanged("ayu"); advanceTimeBy(300); runCurrent()
        viewModel.onQueryChanged(" ayu "); advanceTimeBy(300); runCurrent()

        assertEquals(1, requests.size)
        assertEquals(CustomerSearchError.Unavailable, viewModel.state.value.error)
    }

    @Test
    fun `ignored cancelled response cannot publish after authority transitions`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(dispatcher, mutableListOf())
        val old = CustomerSearchIdentity("cashier-a", "PROFILE-A", "WALK-A")
        viewModel.bind(old)
        viewModel.onQueryChanged("old"); advanceTimeBy(300); runCurrent()
        val authority = CustomerSearchAuthority(old, viewModel.activeGeneration, "old", 0)
        viewModel.bind(CustomerSearchIdentity("cashier-b", "PROFILE-B", "WALK-B"))
        viewModel.publishForAuthority(authority, CustomerSearchResult.Success(RepositoryPage(listOf(com.rotiropi.pos_erpnext.data.Customer("OLD", "Old", null, false)), 0, 20, false)))
        viewModel.publishForAuthority(authority, CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable))
        assertTrue(viewModel.state.value.customers.isEmpty())
        assertEquals(null, viewModel.state.value.error)
        assertEquals(CustomerSelection.WalkIn("WALK-B", ""), viewModel.state.value.selection)
    }

    @Test
    fun `old completion after query clear and logout cannot republish`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = viewModel(dispatcher, mutableListOf())
        val identity = CustomerSearchIdentity("cashier", "PROFILE", "WALK")
        viewModel.bind(identity)
        viewModel.onQueryChanged("one"); advanceTimeBy(300); runCurrent()
        val old = CustomerSearchAuthority(identity, viewModel.activeGeneration, "one", 0)
        viewModel.onQueryChanged("two")
        viewModel.publishForAuthority(old, CustomerSearchResult.Success(RepositoryPage(listOf(com.rotiropi.pos_erpnext.data.Customer("OLD", "Old", null, false)), 0, 20, false)))
        assertTrue(viewModel.state.value.customers.isEmpty())
        viewModel.clear()
        viewModel.publishForAuthority(old, CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable))
        assertEquals(null, viewModel.state.value.error)
        assertEquals(null, viewModel.state.value.selection)
    }

    @Test
    fun `invalid repeated metadata and bound stop pagination safely`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        var invocation = 0
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request; invocation++
            when (invocation) {
                1 -> CustomerSearchResult.Success(RepositoryPage((1..100).map { com.rotiropi.pos_erpnext.data.Customer("$it", "$it", null, false) }, 0, 20, true))
                else -> CustomerSearchResult.Success(RepositoryPage(emptyList(), 0, 0, true))
            }
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
        viewModel.onQueryChanged(""); advanceTimeBy(300); runCurrent()
        assertEquals(100, viewModel.state.value.customers.size)
        assertFalse(viewModel.state.value.hasMore)
        viewModel.loadMore()
        assertEquals(1, requests.size)
    }

    @Test
    fun `non cooperative old completion cannot replace newer query state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cancellations = mutableListOf<ApiCallCancellation>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, cancellation ->
            cancellations += cancellation
            CustomerSearchResult.Success(RepositoryPage(listOf(com.rotiropi.pos_erpnext.data.Customer(request.query, request.query, null, false)), 0, 20, false))
        })
        val identity = CustomerSearchIdentity("cashier", "PROFILE", "WALK")
        viewModel.bind(identity)
        viewModel.onQueryChanged("old"); advanceTimeBy(300); runCurrent()
        viewModel.onQueryChanged("new"); advanceTimeBy(300); runCurrent()
        val oldAuthority = CustomerSearchAuthority(identity, viewModel.activeGeneration - 1, "old", 0)
        viewModel.publishForAuthority(oldAuthority, CustomerSearchResult.Success(RepositoryPage(listOf(com.rotiropi.pos_erpnext.data.Customer("OLD", "Old", null, false)), 0, 20, false)))
        viewModel.publishForAuthority(oldAuthority, CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable))

        assertTrue(cancellations.first().isCancelled)
        assertEquals("new", viewModel.state.value.query)
        assertEquals(listOf("new"), viewModel.state.value.customers.map(CustomerRecord::id))
        assertFalse(viewModel.state.value.loading)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `regressive non initial metadata stops pagination`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request
            if (request.start == 0) {
                CustomerSearchResult.Success(RepositoryPage(listOf(com.rotiropi.pos_erpnext.data.Customer("ONE", "One", null, false)), 0, 20, true))
            } else {
                CustomerSearchResult.Success(RepositoryPage(listOf(com.rotiropi.pos_erpnext.data.Customer("TWO", "Two", null, false)), 0, 40, true))
            }
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
        viewModel.onQueryChanged(""); advanceTimeBy(300); runCurrent()
        viewModel.loadMore(); runCurrent()
        viewModel.loadMore(); runCurrent()

        assertEquals(listOf(0, 20), requests.map(CustomerSearchRequest::start))
        assertEquals(listOf("ONE"), viewModel.state.value.customers.map(CustomerRecord::id))
        assertFalse(viewModel.state.value.hasMore)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `old retry completion cannot publish over replacement request`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = CustomerSearchViewModel(dispatcher, search = { _, _ ->
            CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable)
        })
        val identity = CustomerSearchIdentity("cashier", "PROFILE", "WALK")
        viewModel.bind(identity)
        viewModel.onQueryChanged("ayu"); advanceTimeBy(300); runCurrent()

        val old = CustomerSearchAuthority(identity, viewModel.activeGeneration, "ayu", 0, 1)
        viewModel.retry(); runCurrent()
        viewModel.publishForAuthority(old, CustomerSearchResult.Success(RepositoryPage(
            listOf(com.rotiropi.pos_erpnext.data.Customer("OLD", "Old", null, false)), 0, 20, false,
        )))

        assertTrue(viewModel.state.value.customers.isEmpty())
        assertEquals(CustomerSearchError.Unavailable, viewModel.state.value.error)
    }

    // -------------------------------------------------------------------------
    // toUiMessage mapping — verifies every error type maps to a user-facing
    // string that contains no technical details (endpoints, stack traces, etc.)
    // -------------------------------------------------------------------------

    @Test
    fun `toUiMessage AuthenticationRequired returns user facing string without technical details`() {
        val message = CustomerSearchError.AuthenticationRequired.toUiMessage()
        assertFalse("must not be empty", message.isBlank())
        assertFalse("must not contain class name", message.contains("AuthenticationRequired"))
        assertFalse("must not contain endpoint", message.contains("http"))
        assertFalse("must not contain stack trace marker", message.contains("at com."))
        assertFalse("must not contain token", message.contains("token", ignoreCase = true))
    }

    @Test
    fun `toUiMessage AuthorizationDenied returns user facing string without technical details`() {
        val message = CustomerSearchError.AuthorizationDenied.toUiMessage()
        assertFalse("must not be empty", message.isBlank())
        assertFalse("must not contain class name", message.contains("AuthorizationDenied"))
        assertFalse("must not contain endpoint", message.contains("http"))
    }

    @Test
    fun `toUiMessage Unavailable returns user facing string without technical details`() {
        val message = CustomerSearchError.Unavailable.toUiMessage()
        assertFalse("must not be empty", message.isBlank())
        assertFalse("must not contain class name", message.contains("Unavailable"))
        assertFalse("must not contain endpoint", message.contains("http"))
    }

    @Test
    fun `toUiMessage Stable does not leak server error code to user`() {
        val message = CustomerSearchError.Stable(code = "ERR_INTERNAL_SERVER_ERROR").toUiMessage()
        assertFalse("must not be empty", message.isBlank())
        // The raw server code must not appear verbatim in the UI message
        assertFalse("must not contain raw server code", message.contains("ERR_INTERNAL_SERVER_ERROR"))
        assertFalse("must not contain class name", message.contains("Stable("))
    }

    @Test
    fun `toUiMessage Protocol returns user facing string without technical details`() {
        val message = CustomerSearchError.Protocol.toUiMessage()
        assertFalse("must not be empty", message.isBlank())
        assertFalse("must not contain class name", message.contains("Protocol"))
        assertFalse("must not contain endpoint", message.contains("http"))
    }

    @Test
    fun `toUiMessage mapping is consistent — all five error types produce distinct non blank messages`() {
        val messages = listOf(
            CustomerSearchError.AuthenticationRequired.toUiMessage(),
            CustomerSearchError.AuthorizationDenied.toUiMessage(),
            CustomerSearchError.Unavailable.toUiMessage(),
            CustomerSearchError.Stable(code = "CODE").toUiMessage(),
            CustomerSearchError.Protocol.toUiMessage(),
        )
        messages.forEach { assertFalse("message must not be blank", it.isBlank()) }
        // AuthenticationRequired and AuthorizationDenied should be distinct from the
        // generic fallback messages used for Stable / Protocol
        val generic = CustomerSearchError.Stable(code = "X").toUiMessage()
        assertFalse(
            "auth errors must be distinct from generic error message",
            CustomerSearchError.AuthenticationRequired.toUiMessage() == generic &&
                CustomerSearchError.AuthorizationDenied.toUiMessage() == generic
        )
    }

    @Test
    fun `same normalized query during debounce is a strict no op without deadline restart`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request
            CustomerSearchResult.Success(RepositoryPage(emptyList(), 0, 20, false))
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))

        viewModel.onQueryChanged("ayu")
        advanceTimeBy(100)
        viewModel.onQueryChanged(" ayu ")
        advanceTimeBy(200)
        runCurrent()

        assertEquals("original debounce deadline should dispatch", 1, requests.size)
        assertEquals("ayu", requests.single().query)
    }

    @Test
    fun `ayu active then ayu with trailing space is strict no op`() {
        assertIdenticalActiveQueryIsNoOp("ayu", "ayu ")
    }

    @Test
    fun `repeated blank query while active is strict no op`() {
        assertIdenticalActiveQueryIsNoOp("", "   ")
    }

    @Test
    fun `non cooperative active request is not duplicated by identical normalized input`() {
        assertIdenticalActiveQueryIsNoOp("ayu", " ayu ")
    }

    @Test
    fun `explicit retry enters repository once after failed identical query remains no op`() {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val requests = mutableListOf<CustomerSearchRequest>()
        try {
            val viewModel = CustomerSearchViewModel(
                dispatcher = dispatcher,
                search = { request, _ ->
                    synchronized(requests) { requests += request }
                    CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable)
                },
                debounceMillis = 0,
            )
            viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
            viewModel.onQueryChanged("ayu")
            awaitState("initial failure not published") { viewModel.state.value.error != null }

            viewModel.onQueryChanged(" ayu ")
            executor.submit {}.get(2, TimeUnit.SECONDS)
            assertEquals(1, synchronized(requests) { requests.size })

            viewModel.retry()
            awaitState("retry did not enter repository") { synchronized(requests) { requests.size == 2 } }
        } finally {
            dispatcher.close()
            executor.terminateGracefully()
        }
    }

    @Test
    fun `concurrent retry admission dispatches only one replacement request`() {
        val dispatched = runConcurrentRetryAdmissionScenario()
        assertEquals(2, dispatched.size)
    }

    @Test
    fun `concurrent retry admission stays deterministic across repeated runs`() {
        repeat(20) {
            val dispatched = runConcurrentRetryAdmissionScenario()
            assertEquals("iteration $it must dispatch exactly the initial and one replacement request", 2, dispatched.size)
        }
    }

    private fun runConcurrentRetryAdmissionScenario(): List<CustomerSearchRequest> {
        val executor = Executors.newFixedThreadPool(3)
        val dispatcher = executor.asCoroutineDispatcher()
        val retryEntered = CountDownLatch(1)
        val releaseRetry = CountDownLatch(1)
        val admissionsReady = CountDownLatch(2)
        val releaseAdmissions = CountDownLatch(1)
        val requests = mutableListOf<CustomerSearchRequest>()
        try {
            val viewModel = CustomerSearchViewModel(
                dispatcher = dispatcher,
                search = { request, _ ->
                    val invocation = synchronized(requests) { requests += request; requests.size }
                    if (invocation == 1) {
                        CustomerSearchResult.Failure(CustomerSearchFailure.Unavailable)
                    } else {
                        retryEntered.countDown()
                        check(releaseRetry.await(2, TimeUnit.SECONDS))
                        success("RETRY")
                    }
                },
                debounceMillis = 0,
                requestAdmissionCheckpoint = {
                    admissionsReady.countDown()
                    check(releaseAdmissions.await(2, TimeUnit.SECONDS))
                },
            )
            viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
            viewModel.onQueryChanged("ayu")
            awaitState("initial failure not published") { viewModel.state.value.error != null }

            val ready = CountDownLatch(2)
            val release = CountDownLatch(1)
            val done = CountDownLatch(2)
            repeat(2) {
                Thread {
                    ready.countDown()
                    check(release.await(2, TimeUnit.SECONDS))
                    viewModel.retry()
                    done.countDown()
                }.start()
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            release.countDown()
            assertTrue(admissionsReady.await(2, TimeUnit.SECONDS))
            releaseAdmissions.countDown()
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertTrue(retryEntered.await(2, TimeUnit.SECONDS))

            releaseRetry.countDown()
            awaitState("retry completion not published") { viewModel.state.value.customers.isNotEmpty() }
            assertEquals(listOf("RETRY"), viewModel.state.value.customers.map(CustomerRecord::id))
            assertEquals(null, viewModel.state.value.error)
            return synchronized(requests) { requests.toList() }
        } finally {
            releaseAdmissions.countDown()
            releaseRetry.countDown()
            dispatcher.close()
            executor.terminateGracefully()
        }
    }

    @Test
    fun `concurrent load more admission dispatches one page request`() {
        val executor = Executors.newFixedThreadPool(3)
        val dispatcher = executor.asCoroutineDispatcher()
        val pageEntered = CountDownLatch(1)
        val releasePage = CountDownLatch(1)
        val admissionsReady = CountDownLatch(2)
        val releaseAdmissions = CountDownLatch(1)
        val requests = mutableListOf<CustomerSearchRequest>()
        try {
            val viewModel = CustomerSearchViewModel(
                dispatcher = dispatcher,
                search = { request, _ ->
                    synchronized(requests) { requests += request }
                    if (request.start == 0) {
                        CustomerSearchResult.Success(RepositoryPage(
                            listOf(com.rotiropi.pos_erpnext.data.Customer("FIRST", "First", null, false)),
                            0,
                            20,
                            true,
                        ))
                    } else {
                        pageEntered.countDown()
                        check(releasePage.await(2, TimeUnit.SECONDS))
                        CustomerSearchResult.Success(RepositoryPage(emptyList(), request.start, 20, false))
                    }
                },
                debounceMillis = 0,
                requestAdmissionCheckpoint = {
                    admissionsReady.countDown()
                    check(releaseAdmissions.await(2, TimeUnit.SECONDS))
                },
            )
            viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
            viewModel.onQueryChanged("")
            awaitState("initial page not published") { viewModel.state.value.hasMore }

            val ready = CountDownLatch(2)
            val release = CountDownLatch(1)
            val done = CountDownLatch(2)
            repeat(2) {
                Thread {
                    ready.countDown()
                    release.await(2, TimeUnit.SECONDS)
                    viewModel.loadMore()
                    done.countDown()
                }.start()
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            release.countDown()
            assertTrue(admissionsReady.await(2, TimeUnit.SECONDS))
            releaseAdmissions.countDown()
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertTrue(pageEntered.await(2, TimeUnit.SECONDS))

            releasePage.countDown()
            awaitState("page completion not published") { !viewModel.state.value.loading }
            assertEquals(listOf(0, 20), synchronized(requests) { requests.map(CustomerSearchRequest::start) })
        } finally {
            releaseAdmissions.countDown()
            releasePage.countDown()
            dispatcher.close()
            executor.terminateGracefully()
        }
    }

    private fun TestScope.viewModel(
        dispatcher: TestDispatcher,
        calls: MutableList<CustomerSearchRequest>,
    ) = CustomerSearchViewModel(
        dispatcher = dispatcher,
        search = { request, _ ->
            calls += request
            CustomerSearchResult.Success(RepositoryPage(emptyList(), request.start, request.limit, false))
        },
    )

    private suspend fun TestScope.invalidPageMetadataScenario(
        invalidPage: (CustomerSearchRequest) -> RepositoryPage,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requests = mutableListOf<CustomerSearchRequest>()
        val viewModel = CustomerSearchViewModel(dispatcher, search = { request, _ ->
            requests += request
            if (request.start == 0) {
                CustomerSearchResult.Success(RepositoryPage(
                    listOf(com.rotiropi.pos_erpnext.data.Customer("FIRST", "First", null, false)),
                    0,
                    20,
                    true,
                ))
            } else {
                CustomerSearchResult.Success(invalidPage(request))
            }
        })
        viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
        viewModel.onQueryChanged(""); advanceTimeBy(300); runCurrent()
        viewModel.loadMore(); runCurrent()

        assertEquals(listOf(0, 20), requests.map(CustomerSearchRequest::start))
        assertFalse(viewModel.state.value.loading)
        assertEquals(listOf("FIRST"), viewModel.state.value.customers.map(CustomerRecord::id))
        assertEquals(CustomerSearchError.Protocol, viewModel.state.value.pageError)
        assertFalse(viewModel.state.value.hasMore)
        viewModel.retry(); runCurrent()
        assertEquals(listOf(0, 20, 20), requests.map(CustomerSearchRequest::start))
    }

    private fun completionPausedThenInvalidate(
        result: CustomerSearchResult,
        invalidate: (CustomerSearchViewModel) -> Unit,
    ): CustomerSearchUiState {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val requestEntered = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        val authorityEvaluated = CountDownLatch(1)
        val releaseCompletion = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        try {
            val viewModel = CustomerSearchViewModel(
                dispatcher = dispatcher,
                search = { _, _ ->
                    requestEntered.countDown()
                    check(releaseResponse.await(2, TimeUnit.SECONDS))
                    result
                },
                debounceMillis = 0,
                completionCheckpoint = {
                    authorityEvaluated.countDown()
                    check(releaseCompletion.await(2, TimeUnit.SECONDS))
                },
            )
            viewModel.bind(CustomerSearchIdentity("cashier-a", "PROFILE-A", "WALK-A"))
            viewModel.onQueryChanged("old")
            assertTrue("request did not enter repository", requestEntered.await(2, TimeUnit.SECONDS))
            releaseResponse.countDown()
            assertTrue("completion did not evaluate authority", authorityEvaluated.await(2, TimeUnit.SECONDS))

            val invalidationDone = CountDownLatch(1)
            Thread {
                try {
                    invalidate(viewModel)
                } catch (throwable: Throwable) {
                    failure.set(throwable)
                } finally {
                    invalidationDone.countDown()
                }
            }.start()
            assertTrue("invalidation blocked behind completion", invalidationDone.await(2, TimeUnit.SECONDS))
            failure.get()?.let { throw it }
            releaseCompletion.countDown()
            return viewModel.state.value
        } finally {
            releaseResponse.countDown()
            releaseCompletion.countDown()
            dispatcher.close()
            executor.terminateGracefully()
        }
    }

    private fun success(id: String) = CustomerSearchResult.Success(
        RepositoryPage(listOf(com.rotiropi.pos_erpnext.data.Customer(id, id, null, false)), 0, 20, false),
    )

    private fun assertIdenticalActiveQueryIsNoOp(initial: String, duplicate: String) {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val requestEntered = CountDownLatch(1)
        val releaseRequest = CountDownLatch(1)
        val requests = mutableListOf<CustomerSearchRequest>()
        val cancellations = mutableListOf<ApiCallCancellation>()
        try {
            val viewModel = CustomerSearchViewModel(
                dispatcher = dispatcher,
                search = { request, cancellation ->
                    synchronized(requests) { requests += request }
                    synchronized(cancellations) { cancellations += cancellation }
                    requestEntered.countDown()
                    while (true) {
                        try {
                            if (releaseRequest.await(2, TimeUnit.SECONDS)) break
                        } catch (_: InterruptedException) {
                            // Deliberately non-cooperative transport simulation.
                        }
                    }
                    CustomerSearchResult.Success(RepositoryPage(emptyList(), 0, 20, false))
                },
                debounceMillis = 0,
            )
            viewModel.bind(CustomerSearchIdentity("cashier", "PROFILE", "WALK"))
            viewModel.onQueryChanged(initial)
            assertTrue("request did not enter repository", requestEntered.await(2, TimeUnit.SECONDS))

            viewModel.onQueryChanged(duplicate)

            assertEquals(1, synchronized(requests) { requests.size })
            assertFalse(synchronized(cancellations) { cancellations.single().isCancelled })
            releaseRequest.countDown()
            awaitState("search completion not published") { !viewModel.state.value.loading }
            assertEquals(1, synchronized(requests) { requests.size })
        } finally {
            releaseRequest.countDown()
            dispatcher.close()
            executor.terminateGracefully()
        }
    }

    private fun awaitState(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!condition() && System.nanoTime() < deadline) Thread.yield()
        assertTrue(message, condition())
    }

    /**
     * Graceful, interrupt-free executor termination for concurrency tests. Every
     * latch is released before this runs, so blocked tasks wake, complete, and
     * terminate without interruption on the normal success path. Forced
     * [shutdownNow] is a last resort only for genuinely stuck tasks.
     */
    private fun ExecutorService.terminateGracefully() {
        shutdown()
        if (!awaitTermination(2, TimeUnit.SECONDS)) shutdownNow()
    }

}
