package com.rotiropi.pos_erpnext.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.ui.navigation.PosLayoutMode
import com.rotiropi.pos_erpnext.ui.products.ProductCategory
import com.rotiropi.pos_erpnext.ui.products.ProductItem
import com.rotiropi.pos_erpnext.ui.products.ProductsContent
import com.rotiropi.pos_erpnext.ui.products.ProductsScreen
import com.rotiropi.pos_erpnext.ui.products.ProductsUiState
import com.rotiropi.pos_erpnext.ui.theme.PosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun products_loading_empty_offline_unavailable_and_error_states_are_explicit() {
        val state = mutableStateOf<ProductsUiState>(ProductsUiState.Loading)
        composeRule.setContent {
            PosTheme {
                ProductsScreen(
                    state = state.value,
                    layoutMode = PosLayoutMode.COMPACT,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Loading products").assertIsDisplayed()
        composeRule.runOnIdle { state.value = ProductsUiState.Empty }
        composeRule.onNodeWithText("No products found").assertIsDisplayed()
        composeRule.runOnIdle { state.value = ProductsUiState.Offline }
        composeRule.onNodeWithText("Products are offline").assertIsDisplayed()
        composeRule.runOnIdle { state.value = ProductsUiState.Unavailable }
        composeRule.onNodeWithText("Products unavailable").assertIsDisplayed()
        composeRule.runOnIdle { state.value = ProductsUiState.Error("Products could not load") }
        composeRule.onNodeWithText("Products could not load").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertHasClickAction()
    }

    @Test
    fun compact_product_detail_remains_scrollable_at_font_scale_1_5() {
        val product = ProductItem(
            itemCode = "CROISSANT-PACK",
            itemName = "Croissant Pack",
            itemGroup = "Pastry",
            description = "Six butter croissants with a deliberately long description that wraps across several lines.",
            priceList = "Outlet Retail",
            price = "25000",
            currency = "IDR",
            warehouse = "Outlet 01 - RR",
            availableQuantity = "18",
            uom = "Pack",
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f)
            ) {
                PosTheme {
                    ProductsScreen(
                        state = ProductsUiState.Populated(
                            ProductsContent(
                                query = "",
                                categories = emptyList(),
                                selectedCategoryId = null,
                                products = listOf(product),
                                selectedProduct = product,
                                demoData = true,
                            )
                        ),
                        layoutMode = PosLayoutMode.COMPACT,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Warehouse")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun expanded_products_keep_product_cards_at_least_touch_target_width() {
        val product = productFixture()
        composeRule.setContent {
            PosTheme {
                Box(Modifier.width(600.dp).height(800.dp)) {
                    ProductsScreen(
                        state = ProductsUiState.Populated(
                            ProductsContent(
                                query = "",
                                categories = emptyList(),
                                selectedCategoryId = null,
                                products = listOf(product),
                                selectedProduct = null,
                                demoData = true,
                            )
                        ),
                        layoutMode = PosLayoutMode.EXPANDED,
                    )
                }
            }
        }

        composeRule.onNode(
            hasContentDescription("Product ${product.itemName}") and hasClickAction(),
        ).assertWidthIsAtLeast(48.dp)
    }

    /**
     * A landscape phone is wide enough that the width-only layout mode reports
     * EXPANDED, but too short for a full-height detail pane beside the grid. Such a
     * window stacks instead of clipping both columns, so the detail pane is only
     * rendered once a product is selected.
     */
    @Test
    fun expanded_width_with_a_short_window_stacks_products_instead_of_splitting() {
        val product = productFixture()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalPosWindow provides PosWindow(width = 914.dp, height = 411.dp),
            ) {
                PosTheme {
                    Box(Modifier.width(914.dp).height(411.dp)) {
                        ProductsScreen(
                            state = ProductsUiState.Populated(
                                ProductsContent(
                                    query = "",
                                    categories = emptyList(),
                                    selectedCategoryId = null,
                                    products = listOf(product),
                                    selectedProduct = null,
                                    demoData = true,
                                )
                            ),
                            layoutMode = PosLayoutMode.EXPANDED,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Item details").assertDoesNotExist()
        composeRule.onNode(
            hasContentDescription("Product ${product.itemName}") and hasClickAction(),
        ).assertIsDisplayed()
    }

    @Test
    fun product_cards_expose_button_role() {
        val product = productFixture()
        composeRule.setContent {
            PosTheme {
                ProductsScreen(
                    state = ProductsUiState.Populated(
                        ProductsContent(
                            query = "",
                            categories = emptyList(),
                            selectedCategoryId = null,
                            products = listOf(product),
                            selectedProduct = null,
                            demoData = true,
                        )
                    ),
                    layoutMode = PosLayoutMode.COMPACT,
                )
            }
        }

        composeRule.onNode(
            hasContentDescription("Product ${product.itemName}") and hasClickAction(),
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun products_errors_are_announced() {
        val productsState = mutableStateOf<ProductsUiState>(ProductsUiState.Loading)
        composeRule.setContent {
            PosTheme {
                ProductsScreen(productsState.value, PosLayoutMode.COMPACT)
            }
        }

        composeRule.runOnIdle {
            productsState.value = ProductsUiState.Error("Products could not load")
        }
        composeRule.onNodeWithText("Products could not load")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
    }

    @Test
    fun products_content_exposes_search_categories_snapshots_and_detail() {
        val product = ProductItem(
            itemCode = "CROISSANT-PACK",
            itemName = "Croissant Pack",
            itemGroup = "Pastry",
            description = "Six butter croissants",
            priceList = "Outlet Retail",
            price = "25000",
            currency = "IDR",
            warehouse = "Outlet 01 - RR",
            availableQuantity = "18",
            uom = "Pack",
        )
        composeRule.setContent {
            PosTheme {
                ProductsScreen(
                    state = ProductsUiState.Populated(
                        ProductsContent(
                            query = "croissant",
                            categories = listOf(
                                ProductCategory("all", "All"),
                                ProductCategory("pastry", "Pastry"),
                            ),
                            selectedCategoryId = "pastry",
                            products = listOf(product),
                            selectedProduct = product,
                            demoData = true,
                        )
                    ),
                    layoutMode = PosLayoutMode.COMPACT,
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Filter category Pastry").assertIsDisplayed()
        composeRule.onAllNodesWithText("IDR 25000 · Outlet Retail snapshot")
            .assertCountEquals(2)
        composeRule.onAllNodesWithText("18 Pack · Outlet 01 - RR stock snapshot")
            .assertCountEquals(2)
        composeRule.onNodeWithText("Item details").assertIsDisplayed()
        composeRule.onNodeWithText("Demo data").assertIsDisplayed()
    }

    private fun productFixture() = ProductItem(
        itemCode = "CROISSANT-PACK",
        itemName = "Croissant Pack",
        itemGroup = "Pastry",
        description = "Six butter croissants",
        priceList = "Outlet Retail",
        price = "25000",
        currency = "IDR",
        warehouse = "Outlet 01 - RR",
        availableQuantity = "18",
        uom = "Pack",
    )
}
