package kr.flightalert.core

/**
 * 갑자기 떨어졌는지 판단한다.
 * 기준 = 바로 앞 확인 가격. 그보다 [percent]% 이상 싸지면 알린다.
 * (천천히 내려가는 것은 '갑자기'가 아니므로 알리지 않는다.)
 */
object DropRule {
    data class Drop(val previous: Long, val current: Long, val percent: Double)

    fun check(history: List<PricePoint>, current: Long, percent: Double = 10.0): Drop? {
        val previous = history.maxByOrNull { it.at }?.price ?: return null
        if (previous <= 0) return null
        val dropped = (previous - current) * 100.0 / previous
        return if (dropped >= percent) Drop(previous, current, dropped) else null
    }
}
