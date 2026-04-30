package ai.fluiz.fluidrecorder

/**
 * 전역 변수를 관리하는 싱글톤 객체
 */
object AppGlobals {
    /**
     * 현재 세션 ID (MainActivity에서 설정)
     */
    var sessionId: String = ""

    var baseUrl: String = BuildConfig.BASE_URL;
}

