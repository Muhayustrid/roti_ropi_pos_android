package com.rotiropi.pos_erpnext.ui.cashier

import com.rotiropi.pos_erpnext.data.CatalogQuote
import com.rotiropi.pos_erpnext.data.CatalogWarning
import java.math.BigDecimal

data class QuoteAuthority(
    val cashier: String,
    val sessionName: String?,
    val posProfile: String,
    val customer: String?,
    val itemCode: String,
    val quantity: String,
    val uom: String,
    val batchNo: String?,
    val serialNo: String?,
    val generation: Long,
    val requestId: Long,
)

data class CartLineDraft(
    val itemCode: String,
    val itemName: String,
    val quantity: String,
    val uom: String,
    val batchNo: String?,
    val serialNo: String?,
    val warehouse: String,
    val conversionFactor: String?,
    val scanWarnings: List<CatalogWarning> = emptyList(),
)

data class CartEntry(
    val id: String,
    val itemCode: String,
    val itemName: String,
    val quantity: String,
    val uom: String,
    val batchNo: String?,
    val serialNo: String?,
    val warehouse: String,
    val conversionFactor: String,
    val scanWarnings: List<CatalogWarning>,
    val quote: CatalogQuote?,
    val quoteAuthority: QuoteAuthority?,
)

data class CartState(
    val lines: List<CartEntry> = emptyList(),
) {
    fun applyQuote(
        draft: CartLineDraft,
        quote: CatalogQuote,
        authority: QuoteAuthority,
    ): CartMutation {
        val quantity = QuantitySyntax.parse(draft.quantity) ?: return CartMutation.InvalidQuantity
        if (quote.itemCode != draft.itemCode || QuantitySyntax.parse(quote.quantity) != quantity) {
            return CartMutation.QuoteMismatch
        }
        if (draft.serialNo != null && quantity != "1") return CartMutation.InvalidSerialQuantity

        // Exact stable line identity B5
        val exactId = lineId(draft, quote.uom)
        val existingIndex = lines.indexOfFirst { it.id == exactId }

        if (draft.serialNo != null) {
            val duplicateIndex = lines.indexOfFirst { it.serialNo == draft.serialNo }
            if (duplicateIndex >= 0) {
                val duplicateEntry = lines[duplicateIndex]
                if (duplicateEntry.id != exactId || (duplicateEntry.quoteAuthority != null && duplicateEntry.quoteAuthority.requestId < authority.requestId && duplicateEntry.quoteAuthority.generation == authority.generation)) {
                    return CartMutation.DuplicateSerial
                }
            }
        }

        val entry = CartEntry(
            id = exactId,
            itemCode = draft.itemCode,
            itemName = draft.itemName,
            quantity = quantity,
            uom = quote.uom,
            batchNo = draft.batchNo,
            serialNo = draft.serialNo,
            warehouse = quote.warehouse,
            conversionFactor = quote.conversionFactor,
            scanWarnings = draft.scanWarnings,
            quote = quote,
            quoteAuthority = authority,
        )

        if (existingIndex >= 0) {
            val existing = lines[existingIndex]
            if (existing.quoteAuthority != null && existing.quoteAuthority.generation == authority.generation && existing.quoteAuthority.requestId > authority.requestId) {
                // Out-of-order completion: skip
                return CartMutation.Applied(this)
            }
            return CartMutation.Applied(copy(lines = lines.toMutableList().also { it[existingIndex] = entry }))
        }
        if (lines.size >= MAX_CART_ROWS) return CartMutation.RowLimit
        return CartMutation.Applied(copy(lines = lines + entry))
    }

    fun invalidateQuotes(): CartState = copy(
        lines = lines.map { it.copy(quote = null, quoteAuthority = null) },
    )

    fun invalidateLine(lineId: String): CartState = copy(
        lines = lines.map {
            if (it.id == lineId) it.copy(quote = null, quoteAuthority = null) else it
        },
    )

    fun removeLine(lineId: String): CartState = copy(lines = lines.filterNot { it.id == lineId })

    fun line(lineId: String): CartEntry? = lines.firstOrNull { it.id == lineId }

    fun snapshot(): CartSnapshot = CartSnapshot(
        lines = lines.map { line ->
            CartLine(
                id = line.id,
                itemCode = line.itemCode,
                itemName = line.itemName,
                quantity = line.quantity,
                priceLabel = line.quote?.let { "${it.rate} server quote estimate" } ?: "Quote pending",
                uom = line.uom,
                batchNo = line.batchNo,
                serialNo = line.serialNo,
                warningLabel = (line.scanWarnings + (line.quote?.warnings ?: emptyList()))
                    .firstOrNull()
                    ?.message,
            )
        },
        itemCountLabel = "${lines.size} lines",
        payableLabel = if (lines.isEmpty()) "Cart empty" else "Estimated values only",
    )

    private fun lineId(draft: CartLineDraft, resolvedUom: String): String = listOf(
        draft.itemCode,
        resolvedUom,
        draft.batchNo.orEmpty(),
        draft.serialNo.orEmpty(),
    ).joinToString("|")
}

sealed interface CartMutation {
    data class Applied(val state: CartState) : CartMutation
    data object DuplicateSerial : CartMutation
    data object InvalidQuantity : CartMutation
    data object InvalidSerialQuantity : CartMutation
    data object QuoteMismatch : CartMutation
    data object RowLimit : CartMutation
}

object QuantitySyntax {
    private val syntax = Regex("^[0-9]+(?:\\.[0-9]{1,6})?$")
    private val maximum = BigDecimal("999999.999999")
    private val zero = BigDecimal.ZERO

    fun parse(raw: String): String? {
        if (!syntax.matches(raw)) return null
        val value = raw.toBigDecimalOrNull() ?: return null
        if (value <= zero || value > maximum) return null
        return value.stripTrailingZeros().toPlainString()
    }

    fun addUnit(raw: String): String? = adjust(raw, BigDecimal.ONE)

    fun subtractUnit(raw: String): String? = adjust(raw, BigDecimal.ONE.negate())

    private fun adjust(raw: String, delta: BigDecimal): String? {
        val parsed = parse(raw) ?: return null
        return parse(BigDecimal(parsed).add(delta).toPlainString())
    }
}
