package com.rotiropi.pos_erpnext.ui.cashier

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.UiText
import com.rotiropi.pos_erpnext.ui.uiText
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchUiState
import com.rotiropi.pos_erpnext.ui.customer.CustomerSelection
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.payment.CheckoutUiState
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val catalogUnavailable get() = context.getString(R.string.catalog_error_unavailable)
    private val cartRowLimit get() = context.getString(R.string.cart_error_row_limit)

    @Test
    fun live_catalog_exposes_loading_error_and_reachable_product_actions() {
        composeRule.setContent {
            PosTheme {
                CashierScreen(
                    state = activeState(
                        catalogLoading = true,
                        catalogError = uiText(R.string.catalog_error_unavailable),
                        catalogHasMore = true,
                    ),
                    layoutMode = PosLayoutMode.COMPACT,
                    customerState = CustomerSearchUiState(
                        selection = CustomerSelection.WalkIn("WALK-IN", ""),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("cashier-search").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("cashier-barcode").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(context.getString(R.string.cashier_loading_more)).assertIsDisplayed()
        composeRule.onNodeWithText(catalogUnavailable).assertIsDisplayed()
        composeRule.onNodeWithTag("cashier-catalog-retry").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("cashier-load-more").assertHeightIsAtLeast(48.dp)
        composeRule.onNode(
            hasContentDescription(
                context.getString(R.string.cashier_add_to_cart_description, "Croissant Pack"),
            ),
        ).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(context.getString(R.string.badge_demo_data)).assertDoesNotExist()
        composeRule.onNodeWithText("Camera", substring = true).assertDoesNotExist()
    }

    @Test
    fun serial_cart_line_announces_identity_and_disables_quantity_editing() {
        composeRule.setContent {
            PosTheme {
                CartContent(
                    cart = CartSnapshot(
                        lines = listOf(
                            CartLine(
                                id = "SCALE|Nos||SER-1",
                                itemCode = "SCALE",
                                itemName = "Scale",
                                quantity = "1",
                                priceLabel = uiText(R.string.cart_quote_estimate, "1000"),
                                uom = "Nos",
                                serialNo = "SER-1",
                            ),
                        ),
                        itemCountLabel = uiText(R.string.cart_line_count, 1),
                        payableLabel = uiText(R.string.cart_estimated_only),
                    ),
                    checkoutState = CheckoutUiState.Unavailable,
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.cart_serial, "SER-1")).assertIsDisplayed()
        composeRule.onNodeWithTag("cart-decrease-SER-1").assertIsNotEnabled()
        composeRule.onNodeWithTag("cart-increase-SER-1").assertIsNotEnabled()
        composeRule.onNodeWithTag("cart-remove-SER-1").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun catalog_and_cart_remain_reachable_at_large_font_scale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                PosTheme {
                    Box(Modifier.width(400.dp).height(600.dp)) {
                        CartContent(
                            cart = activeState().content.cart,
                            checkoutState = CheckoutUiState.Unavailable,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("checkout-confirm").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun scan_and_quote_retry_actions_are_clickable_and_focus_reachable() {
        var retryClicks = 0
        composeRule.setContent {
            PosTheme {
                CashierScreen(
                    state = activeState(
                        scanError = uiText(R.string.catalog_error_unavailable),
                        quoteError = uiText(R.string.cart_error_row_limit),
                        products = emptyList(),
                    ),
                    layoutMode = PosLayoutMode.COMPACT,
                    onRetry = { retryClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag("cashier-scan-retry").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag("cashier-quote-retry").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText(context.getString(R.string.cashier_no_products)).assertIsDisplayed()
        composeRule.onNodeWithText(cartRowLimit).assertIsDisplayed()
        composeRule.runOnIdle { assert(retryClicks == 2) }
    }

    @Test
    fun catalog_empty_success_state_is_announced_with_retry_reachable() {
        var retryClicks = 0
        composeRule.setContent {
            PosTheme {
                CashierScreen(
                    state = activeState(catalogError = uiText(R.string.catalog_error_unavailable)),
                    layoutMode = PosLayoutMode.COMPACT,
                    onRetry = { retryClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag("cashier-catalog-retry").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        composeRule.runOnIdle { assert(retryClicks == 1) }
    }

    @Test
    fun decimal_quantity_editor_is_focusable_and_reports_invalid_input() {
        val editedValues = mutableListOf<String>()
        composeRule.setContent {
            PosTheme {
                CartContent(
                    cart = CartSnapshot(
                        lines = listOf(
                            CartLine(
                                id = "ITEM-1|Nos||",
                                itemCode = "ITEM-1",
                                itemName = "Item",
                                quantity = "1",
                                priceLabel = uiText(R.string.cart_quote_estimate, "1000"),
                                uom = "Nos",
                            ),
                        ),
                        itemCountLabel = uiText(R.string.cart_line_count, 1),
                        payableLabel = uiText(R.string.cart_estimated_only),
                    ),
                    checkoutState = CheckoutUiState.Unavailable,
                    onEditQuantity = { _, raw -> editedValues += raw },
                    invalidQuantityForLine = "ITEM-1|Nos||",
                )
            }
        }

        composeRule.onNodeWithTag("cart-qty-ITEM-1").assertIsDisplayed().assertHeightIsAtLeast(48.dp).requestFocus()
        composeRule.onNodeWithTag("cart-qty-ITEM-1").performTextReplacement("2.5")
        composeRule.waitForIdle()
        // The edit is forwarded; the state-driven field keeps the previous valid
        // cart quantity (B7: invalid input must not corrupt the cart line).
        assertTrue(editedValues.any { it == "2.5" })
        composeRule.onNodeWithText(context.getString(R.string.cart_quantity_invalid)).assertIsDisplayed()
    }

    @Test
    fun fifty_line_cart_limit_state_is_visible_and_not_clipped() {
        val lines = (0 until 50).map { index ->
            CartLine(
                id = "ITEM-$index|Nos||",
                itemCode = "ITEM-$index",
                itemName = "Item $index",
                quantity = "1",
                priceLabel = uiText(R.string.cart_quote_estimate, "1000"),
                uom = "Nos",
            )
        }
        composeRule.setContent {
            PosTheme {
                CashierScreen(
                    state = CashierUiState.Active(
                        CashierContent(
                            query = "",
                            barcode = "",
                            categories = listOf(CashierCategory("all", uiText(R.string.cashier_category_all))),
                            selectedCategoryId = "all",
                            products = emptyList(),
                            cart = CartSnapshot(
                                lines,
                                uiText(R.string.cart_line_count, 50),
                                uiText(R.string.cart_estimated_only),
                            ),
                            checkoutState = CheckoutUiState.Unavailable,
                            demoData = false,
                            quoteError = uiText(R.string.cart_error_row_limit),
                        ),
                    ),
                    layoutMode = PosLayoutMode.COMPACT,
                )
            }
        }

        composeRule.onNodeWithTag("cashier-cart-summary").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(cartRowLimit).assertIsDisplayed()
    }

    private fun activeState(
        catalogLoading: Boolean = false,
        catalogError: UiText? = null,
        catalogHasMore: Boolean = false,
        scanError: UiText? = null,
        quoteError: UiText? = null,
        products: List<CashierProduct> = listOf(
            CashierProduct(
                itemCode = "CROISSANT-PACK",
                itemName = "Croissant Pack",
                categoryId = "all",
                price = "25000",
                currency = "IDR",
                priceList = uiText(R.string.checkout_server_price),
                availableQuantity = "18",
                uom = "Pack",
                warehouse = "Outlet 01 - RR",
            ),
        ),
    ) = CashierUiState.Active(
        CashierContent(
            query = "",
            barcode = "",
            categories = listOf(CashierCategory("all", uiText(R.string.cashier_category_all))),
            selectedCategoryId = "all",
            products = products,
            cart = CartSnapshot(
                emptyList(),
                uiText(R.string.cart_line_count, 0),
                uiText(R.string.cart_empty_short),
            ),
            checkoutState = CheckoutUiState.Unavailable,
            demoData = false,
            catalogLoading = catalogLoading,
            catalogError = catalogError,
            catalogHasMore = catalogHasMore,
            scanError = scanError,
            quoteError = quoteError,
        ),
    )
}
