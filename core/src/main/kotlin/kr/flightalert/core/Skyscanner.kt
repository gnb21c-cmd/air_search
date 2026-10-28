package kr.flightalert.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Skyscanner {
    private val YYMMDD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")

    /** 스카이스캐너 한국 사이트 검색 주소 (원화, 성인 n명, 일반석). */
    fun searchUrl(w: Watch): String {
        val path = buildString {
            append("https://www.skyscanner.co.kr/transport/flights/")
            append(w.from.lowercase()).append('/').append(w.to.lowercase()).append('/')
            append(w.departDate.format(YYMMDD)).append('/')
            if (w.returnDate != null) append(w.returnDate.format(YYMMDD)).append('/')
        }
        val rtn = if (w.returnDate != null) 1 else 0
        return "$path?adultsv2=${w.adults}&cabinclass=economy&childrenv2=&rtn=$rtn" +
            "&preferdirects=false&currency=KRW&locale=ko-KR&market=KR"
    }

    private val PRICE = Regex("""(?:₩\s*([0-9][0-9,]{3,}))|(?:([0-9][0-9,]{3,})\s*원)""")

    /** 글자에서 첫 원화 가격. */
    fun firstPrice(text: String): Long? {
        val m = PRICE.find(text) ?: return null
        val digits = (m.groupValues[1].ifEmpty { m.groupValues[2] }).replace(",", "")
        return digits.toLongOrNull()
    }

    /**
     * 결과 화면 글에서 '최저가' 칸의 가격을 찾는다 (가로채기가 안 될 때 쓰는 대비책).
     * 화면의 아무 가격이나 집으면 광고·달력 가격이 섞이므로 '최저가' 바로 뒤만 본다.
     */
    fun cheapestFromPageText(text: String): Long? {
        var idx = text.indexOf("최저가")
        while (idx >= 0) {
            val p = firstPrice(text.substring(idx, minOf(text.length, idx + 60)))
            if (p != null && plausible(p)) return p
            idx = text.indexOf("최저가", idx + 3)
        }
        return null
    }

    /** 항공권이라 보기 어려운 수(1만 원 미만, 1억 원 넘음)는 버린다. */
    fun plausible(price: Long): Boolean = price in 10_000..100_000_000
}
