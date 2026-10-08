package com.pitchmap.trust.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.trust.domain.TrustErrorCode;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

// 서비스가 이미 신고했는지 조회한 뒤 저장하기 전에, 같은 신고를 하는 다른 요청이 먼저 저장할 수 있다.
// 이때 DB가 유니크 제약 위반 예외를 던지므로, 서비스는 이 클래스로 제약 이름을 보고 같은 신고의 중복인지 가려낸다.
final class MemberReportUniqueConstraintTranslator {

    private static final String UNIQUE_CONSTRAINT = "uk_member_report_reporter_target_basecamp_kind";
    private static final String KEY_MARKER = " for key '";

    private MemberReportUniqueConstraintTranslator() {}

    /** 호출하면 같은 신고의 유니크 제약 위반을 오류 코드 예외로 바꿔 돌려준다. 다른 제약 위반이면 받은 예외를 그대로 돌려준다. */
    static RuntimeException translate(DataIntegrityViolationException e) {
        String violatedKey =
                violatedKey(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        if (violatedKey.contains(UNIQUE_CONSTRAINT)) {
            return new BusinessException(TrustErrorCode.REPORT_DUPLICATED);
        }
        return e;
    }

    // MySQL 메시지는 "Duplicate entry '값' for key '제약'" 형태다. 값 자리에 제약 이름이 들어가도 오판하지 않게 마지막 "for key" 뒤만 본다.
    private static String violatedKey(String message) {
        if (message == null) {
            return "";
        }
        int marker = message.lastIndexOf(KEY_MARKER);
        return marker < 0 ? message : message.substring(marker + KEY_MARKER.length());
    }
}
