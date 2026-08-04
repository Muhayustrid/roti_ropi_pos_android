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

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerSearchViewModelTest {
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
        viewModel.selectRegistered(CustomerRecord("CUST-1", "Ayu Bakery", "0812", false))

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
}
