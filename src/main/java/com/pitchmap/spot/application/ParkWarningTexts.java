package com.pitchmap.spot.application;

/** 공원 경계 경고에 붙이는 안내 문구. 박지 제보 응답과 장소 상세 응답이 같은 문구를 쓴다. */
final class ParkWarningTexts {

    /**
     * 경고 박지에 붙이는 안내. 공원 안에서 야영해도 뒷정리만 잘하면 된다고 읽히지 않게, 흔적 남기지 않기가 아니라 야영하지 말라는 안내를 준다.
     * 경고가 아닌 박지에는 {@link #NORMAL_GUIDE}를 쓴다.
     */
    static final String WARNED_GUIDE =
            "자연공원 안에서는 지정된 야영장 밖의 야영과 취사가 금지돼 있고, 어기면 과태료 대상입니다. 이곳에서 야영하지 말고 가까운 지정 야영장을 이용하세요.";

    static final String NORMAL_GUIDE = "머문 자리에 흔적을 남기지 마세요. 쓰레기는 모두 되가져가세요.";

    /** 경계 데이터는 참고용이라서, 장소 상세와 박지 제보 응답은 경고와 함께 이 고지를 준다. */
    static final String NOTICE = "참고용 데이터입니다. 공식 경계는 고시 도면을 확인하세요.";

    private ParkWarningTexts() {}
}
