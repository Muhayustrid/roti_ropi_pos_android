package com.rotiropi.pos_erpnext.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.tooling.preview.Preview
import com.rotiropi.pos_erpnext.ui.customer.CustomerRecord
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchError
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchSheet
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchUiState
import com.rotiropi.pos_erpnext.ui.customer.CustomerSelection
import com.rotiropi.pos_erpnext.ui.theme.PosTheme

private val customer = CustomerRecord("CUST-DEMO-1", "Demo Bakery", "081234567890", false)

@Preview(name = "Customer walk-in", widthDp = 360, heightDp = 800)
@Composable fun CustomerWalkInPreview() = CustomerPreview(CustomerSearchUiState(selection = CustomerSelection.WalkIn("WALK-DEMO", "Demo name")))

@Preview(name = "Customer results", widthDp = 360, heightDp = 800)
@Composable fun CustomerResultsPreview() = CustomerPreview(CustomerSearchUiState(customers = listOf(customer), selection = CustomerSelection.Registered(customer.id, customer.displayLabel, customer.mobile), hasMore = true))

@Preview(name = "Customer error large font", widthDp = 800, heightDp = 360, fontScale = 1.5f)
@Composable fun CustomerErrorPreview() = CustomerPreview(CustomerSearchUiState(error = CustomerSearchError.Unavailable))

@Composable private fun CustomerPreview(state: CustomerSearchUiState) {
    PosTheme {
        Column {
            Text("Demo data")
            CustomerSearchSheet(state, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
