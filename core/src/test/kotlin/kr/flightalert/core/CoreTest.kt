package kr.flightalert.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CoreTest {
    private fun p(at: Long, price: Long) = PricePoint("w", at, price)

    @Test fun 처음_확인은_비교할_것이_없어_알리지_않음() {
        assertNull(DropRule.check(emptyList(), 100_000))
    }

    @Test fun 바로_앞보다_10퍼센트_이상_떨어지면_알림() {
        val d = DropRule.check(listOf(p(1, 300_000), p(2, 200_000)), 180_000)
        assertNotNull(d)
        assertEquals(200_000, d.previous)
        assertEquals(10.0, d.percent, 0.001)
    }

    @Test fun 덜_떨어지면_알리지_않음() {
        assertNull(DropRule.check(listOf(p(1, 200_000)), 181_000))
    }

    @Test fun 순서가_섞여도_가장_최근_가격과_비교() {
        assertNull(DropRule.check(listOf(p(5, 150_000), p(1, 300_000)), 140_000))
        assertNotNull(DropRule.check(listOf(p(5, 300_000), p(1, 150_000)), 260_000))
    }

    @Test fun 왕복_주소() {
        val w = Watch("w", "ICN", "nrt", LocalDate.of(2026, 12, 15), LocalDate.of(2026, 12, 20), 2)
        assertEquals(
            "https://www.skyscanner.co.kr/transport/flights/icn/nrt/261215/261220/" +
                "?adultsv2=2&cabinclass=economy&childrenv2=&rtn=1&preferdirects=false&currency=KRW&locale=ko-KR&market=KR",
            Skyscanner.searchUrl(w),
        )
    }

    @Test fun 편도_주소() {
        val w = Watch("w", "sela", "osaa", LocalDate.of(2027, 1, 3), null)
        val url = Skyscanner.searchUrl(w)
        assertEquals(true, url.startsWith("https://www.skyscanner.co.kr/transport/flights/sela/osaa/270103/?"))
        assertEquals(true, url.contains("rtn=0"))
    }

    @Test fun 최저가_칸_읽기() {
        val text = "광고 ₩9,900 특가\n추천순\n₩312,400\n최저가\n₩245,300\n평균 2시간 20분\n최단 시간\n₩401,000"
        assertEquals(245_300, Skyscanner.cheapestFromPageText(text))
        assertEquals(245_300, Skyscanner.cheapestFromPageText("최저가 245,300원 부터"))
        assertNull(Skyscanner.cheapestFromPageText("₩245,300 만 있음"))
    }

    @Test fun 코드_다듬기() {
        assertEquals("icn", normalizePlace(" ICN "))
        assertEquals("sela", normalizePlace("SELA"))
        assertNull(normalizePlace("서울"))
        assertNull(normalizePlace("IC"))
    }

    @Test fun 지난_여행은_만료() {
        val w = Watch("w", "icn", "nrt", LocalDate.of(2026, 10, 1), null)
        assertEquals(true, w.expired(LocalDate.of(2026, 10, 28)))
        assertEquals(false, w.expired(LocalDate.of(2026, 10, 1)))
    }
}
