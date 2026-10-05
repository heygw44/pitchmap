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

    private MemberUniqueConstraintTranslator() {}

    /** 호출하면 회원 유니크 제약 위반을 중복 오류 코드 예외로 바꿔 돌려준다. 다른 제약 위반이면 받은 예외를 그대로 돌려준다. */
    static RuntimeException translate(DataIntegrityViolationException e) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        if (message != null && message.contains(EMAIL_UNIQUE_CONSTRAINT)) {
            return new MemberException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        }
        if (message != null && message.contains(NICKNAME_UNIQUE_CONSTRAINT)) {
            return new MemberException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
        return e;
    }
}
