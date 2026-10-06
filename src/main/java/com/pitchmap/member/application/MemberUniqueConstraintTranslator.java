package com.pitchmap.member.application;

import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

// 서비스가 중복 여부를 조회한 뒤 DB에 쓰기 전에, 다른 요청이 같은 이메일이나 닉네임을 먼저 쓸 수 있다.
// 이때 DB가 유니크 제약 위반 예외를 던지므로, 서비스는 이 클래스로 제약 이름을 보고 어느 값이 중복인지 가려낸다.
// 가입과 내 정보 수정이 같은 제약을 쓰므로 제약 이름과 오류 코드의 짝을 한 곳에 둔다.
final class MemberUniqueConstraintTranslator {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_member_email";
    private static final String NICKNAME_UNIQUE_CONSTRAINT = "uk_member_nickname";
    private static final String KEY_MARKER = " for key '";

    private MemberUniqueConstraintTranslator() {}

    /** 호출하면 회원 유니크 제약 위반을 중복 오류 코드 예외로 바꿔 돌려준다. 다른 제약 위반이면 받은 예외를 그대로 돌려준다. */
    static RuntimeException translate(DataIntegrityViolationException e) {
        String violatedKey =
                violatedKey(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        if (violatedKey.contains(EMAIL_UNIQUE_CONSTRAINT)) {
            return new MemberException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        }
        if (violatedKey.contains(NICKNAME_UNIQUE_CONSTRAINT)) {
            return new MemberException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
        return e;
    }

    // MySQL 메시지는 "Duplicate entry '값' for key '제약'" 형태라서 값에도 제약 이름이 들어갈 수 있다.
    // 예를 들어 닉네임이 uk_member_email이면 닉네임 중복인데 이메일 중복으로 읽는다. 그래서 값은 빼고 마지막 "for key" 뒤만 본다.
    private static String violatedKey(String message) {
        if (message == null) {
            return "";
        }
        int marker = message.lastIndexOf(KEY_MARKER);
        return marker < 0 ? message : message.substring(marker + KEY_MARKER.length());
    }
}
