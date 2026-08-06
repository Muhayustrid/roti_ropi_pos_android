package com.rotiropi.pos_erpnext.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.ui.cashier.CartContent
import com.rotiropi.pos_erpnext.ui.cashier.CartLine
import com.rotiropi.pos_erpnext.ui.cashier.CartSnapshot
import com.rotiropi.pos_erpnext.ui.cashier.CashierCategory
import com.rotiropi.pos_erpnext.ui.cashier.CashierContent
import com.rotiropi.pos_erpnext.ui.cashier.CashierProduct
import com.rotiropi.pos_erpnext.ui.cashier.CashierScreen
import com.rotiropi.pos_erpnext.ui.cashier.CashierUiState
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.payment.CheckoutPanel
import com.rotiropi.pos_erpnext.ui.payment.CheckoutUiState
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptContent
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CashierScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun release_cashier_is_honest_and_has_no_demo_or_input() {
        composeRule.setContent {
            PosTheme {
                CashierScreen(CashierUiState.Unavailable, PosLayoutMode.COMPACT)
            }
        }

        composeRule.onNodeWithText("Cashier unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Demo data").assertDoesNotExist()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun active_cashier_exposes_manual_input_categories_and_server_snapshots() {
        composeRule.setContent {
            PosTheme {
                CashierScreen(activeState(), PosLayoutMode.COMPACT)
            }
        }

        composeRule.onNodeWithTag("cashier-search").assertIsDisplayed()
        composeRule.onNodeWithTag("cashier-barcode").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Cashier category Pastry").assertIsSelected()
        composeRule.onNode(
            hasContentDescription("Add Croissant Pack to cart") and hasClickAction(),
        ).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("IDR 25,000 · Outlet Retail server snapshot").assertIsDisplayed()
        composeRule.onNodeWithText("18 Pack · Outlet 01 - RR server stock snapshot").assertIsDisplayed()
        composeRule.onNodeWithText("Demo data").assertIsDisplayed()
    }

    @Test
    fun manual_or_hid_barcode_input_emits_text_and_submit_events() {
        val barcode = androidx.compose.runtime.mutableStateOf("")
        var submitted = false
        composeRule.setContent {
            PosTheme {
                CashierScreen(
                    state = activeState(barcode = barcode.value),
                    layoutMode = PosLayoutMode.COMPACT,
                    onBarcodeChange = { barcode.value = it },
                    onBarcodeSubmit = { submitted = true },
                )
            }
        }

        val barcodeField = composeRule.onNodeWithTag("cashier-barcode")
        barcodeField.performTextInput("899100")
        barcodeField.performImeAction()
        composeRule.runOnIdle {
            assertEquals("899100", barcode.value)
            assertTrue(submitted)
        }
        composeRule.onNodeWithContentDescription("Camera scanner").assertDoesNotExist()
    }

    @Test
    fun external_keyboard_moves_from_search_to_barcode() {
        composeRule.setContent {
            PosTheme {
                CashierScreen(activeState(), PosLayoutMode.COMPACT)
            }
        }

        composeRule.onNodeWithTag("cashier-search").requestFocus().assertIsFocused()
        composeRule.onNodeWithTag("cashier-search").performKeyInput { pressKey(Key.Tab) }
        composeRule.onNodeWithTag("cashier-barcode").assertIsFocused()
    }

    @Test
    fun compact_and_expanded_cashier_use_expected_cart_surfaces() {
        var openInvoked = false
        val layoutMode = androidx.compose.runtime.mutableStateOf(PosLayoutMode.COMPACT)
        val cartVisible = androidx.compose.runtime.mutableStateOf(false)
        composeRule.setContent {
            PosTheme {
                CashierScreen(
                    state = activeState(),
                    layoutMode = layoutMode.value,
                    cartVisible = cartVisible.value,
                    onOpenCart = { openInvoked = true },
                )
            }
        }

        composeRule.onNodeWithTag("cashier-cart-summary")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle {
            assertTrue(openInvoked)
            cartVisible.value = true
        }
        composeRule.onNodeWithTag("cashier-cart-sheet").assertIsDisplayed()

        composeRule.runOnIdle {
            cartVisible.value = false
            layoutMode.value = PosLayoutMode.EXPANDED
        }
        composeRule.onNodeWithTag("cashier-cart-pane").assertIsDisplayed()
        composeRule.onNodeWithTag("cashier-cart-summary").assertDoesNotExist()
    }

    @Test
    fun cart_quantity_actions_are_accessible() {
        composeRule.setContent {
            PosTheme {
                CartContent(
                    cart = cartFixture(),
                    checkoutState = CheckoutUiState.Unavailable,
                )
            }
        }

        composeRule.onNodeWithTag("cart-decrease-CROISSANT-PACK")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("cart-increase-CROISSANT-PACK")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun checkout_states_never_enable_confirmation_or_claim_unsupported_controls() {
        val state = androidx.compose.runtime.mutableStateOf<CheckoutUiState>(CheckoutUiState.Unavailable)
        composeRule.setContent {
            PosTheme { CheckoutPanel(state.value) }
        }

        composeRule.onNodeWithText("Authoritative payable and payment modes unavailable").assertIsDisplayed()
        composeRule.onNodeWithTag("checkout-confirm").assertIsNotEnabled()

        composeRule.runOnIdle { state.value = CheckoutUiState.OfflineNotSubmitted }
        composeRule.onNodeWithText("Offline — sale not submitted").assertIsDisplayed()
        composeRule.onNodeWithTag("checkout-confirm").assertIsNotEnabled()

        composeRule.runOnIdle { state.value = CheckoutUiState.PriceChanged("Server price changed", emptyMap()) }
        composeRule.onNodeWithText("Server price changed").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertHasClickAction()

        composeRule.runOnIdle { state.value = CheckoutUiState.Submitting }
        composeRule.onNodeWithText("Submitting sale").assertIsDisplayed()
        composeRule.onNodeWithTag("checkout-confirm").assertIsNotEnabled()

        listOf("Overpayment", "Change due", "Discount", "Camera").forEach { unsupported ->
            composeRule.onAllNodesWithText(unsupported, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun checkout_errors_are_announced() {
        composeRule.setContent {
            PosTheme { CheckoutPanel(CheckoutUiState.Error("Sale was not submitted")) }
        }

        composeRule.onNodeWithText("Sale was not submitted")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
        composeRule.onNodeWithTag("checkout-confirm").assertIsNotEnabled()
    }

    @Test
    fun receipt_displays_terminal_server_change() {
        composeRule.setContent {
            PosTheme {
                CashierScreen(
                    state = CashierUiState.Receipt(receiptFixture()),
                    layoutMode = PosLayoutMode.COMPACT,
                )
            }
        }

        composeRule.onNodeWithText("Receipt").assertIsDisplayed()
        composeRule.onNodeWithText("SINV-0001").assertIsDisplayed()
        composeRule.onNodeWithText("Server change").assertIsDisplayed()
        composeRule.onNodeWithText("IDR 0").assertIsDisplayed()
        composeRule.onNodeWithText("Demo data").assertIsDisplayed()
        composeRule.onNodeWithTag("receipt-close")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun compact_cart_remains_scrollable_at_font_scale_1_5() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                PosTheme {
                    Box(Modifier.width(400.dp).height(600.dp)) {
                        CartContent(
                            cart = cartFixture(),
                            checkoutState = CheckoutUiState.Unavailable,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("checkout-confirm")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun activeState(barcode: String = "") = CashierUiState.Active(
        CashierContent(
            query = "",
            barcode = barcode,
            categories = listOf(
                CashierCategory("all", "All"),
                CashierCategory("pastry", "Pastry"),
            ),
            selectedCategoryId = "pastry",
            products = listOf(productFixture()),
            cart = cartFixture(),
            checkoutState = CheckoutUiState.Unavailable,
            demoData = true,
        )
    )

    private fun productFixture() = CashierProduct(
        itemCode = "CROISSANT-PACK",
        itemName = "Croissant Pack",
        categoryId = "pastry",
        price = "25,000",
        currency = "IDR",
        priceList = "Outlet Retail",
        availableQuantity = "18",
        uom = "Pack",
        warehouse = "Outlet 01 - RR",
    )

    private fun cartFixture() = CartSnapshot(
        lines = listOf(
            CartLine(
                id = "line-croissant",
                itemCode = "CROISSANT-PACK",
                itemName = "Croissant Pack",
                quantity = "2",
                priceLabel = "Demo line IDR 50,000",
                uom = "Pack",
            )
        ),
        itemCountLabel = "2 items",
        payableLabel = "Demo total IDR 50,000",
    )

    private fun receiptFixture() = ReceiptContent(
        saleId = "SINV-0001",
        customerLabel = "Walk-in Customer",
        total = "IDR 55,000",
        paid = "IDR 55,000",
        changeAmount = "IDR 0",
        status = "Paid",
        demoData = true,
    )
}
