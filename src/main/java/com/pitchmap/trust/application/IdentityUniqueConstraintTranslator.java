package com.pitchmap.trust.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.trust.domain.TrustErrorCode;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

// 서비스가 중복 여부를 조회한 뒤 DB에 쓰기 전에, 다른 요청이 같은 회원이나 같은 CI로 먼저 본인확인을 저장할 수 있다.
// 이때 DB가 유니크 제약 위반 예외를 던지므로, 서비스는 이 클래스로 제약 이름을 보고 어느 쪽이 중복인지 가려낸다.
final class IdentityUniqueConstraintTranslator {

    private static final String MEMBER_UNIQUE_CONSTRAINT = "uk_identity_verification_member";
    private static final String CI_HASH_UNIQUE_CONSTRAINT = "uk_identity_verification_ci_hash";
    private static final String KEY_MARKER = " for key '";

    private IdentityUniqueConstraintTranslator() {}

    /** 호출하면 본인확인 유니크 제약 위반을 오류 코드 예외로 바꿔 돌려준다. 다른 제약 위반이면 받은 예외를 그대로 돌려준다. */
    static RuntimeException translate(DataIntegrityViolationException e) {
        String violatedKey =
                violatedKey(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        if (violatedKey.contains(MEMBER_UNIQUE_CONSTRAINT)) {
            return new BusinessException(TrustErrorCode.IDENTITY_ALREADY_VERIFIED);
        }
        if (violatedKey.contains(CI_HASH_UNIQUE_CONSTRAINT)) {
            return new BusinessException(TrustErrorCode.IDENTITY_CI_DUPLICATED);
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
