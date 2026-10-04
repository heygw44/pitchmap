package com.pitchmap.member.application;

import com.pitchmap.member.domain.DisposableEmailDomainRepository;
import com.pitchmap.member.domain.Email;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import com.pitchmap.member.domain.MemberRepository;
import com.pitchmap.member.domain.Password;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberSignupService {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_member_email";
    private static final String NICKNAME_UNIQUE_CONSTRAINT = "uk_member_nickname";

    private final MemberRepository memberRepository;
    private final DisposableEmailDomainRepository disposableEmailDomainRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public SignupResult signUp(SignupCommand command) {
        Password password = Password.of(command.password());
        Email email = Email.of(command.email());
        rejectDisposableDomain(email);
        rejectDuplicates(email, command.nickname());

        String passwordHash = passwordEncoder.encode(password.value());
        Member member = Member.register(email, passwordHash, command.nickname(), Instant.now(clock));
        Member saved = saveOrTranslate(member);
        return new SignupResult(saved.getId(), saved.getStatus());
    }

    private void rejectDisposableDomain(Email email) {
        if (disposableEmailDomainRepository.existsByDomainIn(email.domainCandidates())) {
            throw new MemberException(MemberErrorCode.MEMBER_DISPOSABLE_EMAIL);
        }
    }

    private void rejectDuplicates(Email email, String nickname) {
        if (memberRepository.existsByEmail(email.value())) {
            throw new MemberException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new MemberException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
    }

    // 사전 조회와 저장 사이에 다른 요청이 같은 값을 먼저 넣을 수 있다.
    // 이때는 서비스가 DB 유니크 제약 이름을 보고 이메일 중복인지 닉네임 중복인지 가려낸다.
    private Member saveOrTranslate(Member member) {
        try {
            return memberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
            if (message != null && message.contains(EMAIL_UNIQUE_CONSTRAINT)) {
                throw new MemberException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
            }
            if (message != null && message.contains(NICKNAME_UNIQUE_CONSTRAINT)) {
                throw new MemberException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
            }
            throw e;
        }
    }
}
