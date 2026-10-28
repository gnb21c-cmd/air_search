package kr.flightalert.core

import java.time.LocalDate

/** 지켜볼 항공권 한 건. 오는 날이 없으면 편도. */
data class Watch(
    val id: String,
    val from: String,
    val to: String,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val adults: Int = 1,
    val active: Boolean = true,
) {
    val title: String
        get() = buildString {
            append(from.uppercase()).append(" → ").append(to.uppercase())
            append("  ").append(departDate)
            if (returnDate != null) append(" ~ ").append(returnDate)
        }

    /** 가는 날이 지났으면 더 볼 필요가 없다. */
    fun expired(today: LocalDate): Boolean = departDate.isBefore(today)
}

/** 한 번 확인한 가격. */
data class PricePoint(val watchId: String, val at: Long, val price: Long)

/** 입력한 공항·도시 코드를 다듬는다 (ICN, sela 등 영문 3~4자). 틀리면 null. */
fun normalizePlace(raw: String): String? {
    val s = raw.trim().lowercase()
    return if (Regex("^[a-z]{3,4}$").matches(s)) s else null
}
