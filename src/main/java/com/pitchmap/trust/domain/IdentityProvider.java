package com.pitchmap.trust.domain;

/** 본인확인을 해 주는 제공자다. 지금은 가짜 제공자뿐이고, 실제 기관을 붙일 때 이 인터페이스의 구현체를 바꾼다. */
public interface IdentityProvider {

    /** 호출하면 회원이 낸 값을 확인해 신원을 돌려준다. */
    VerifiedIdentity verify(IdentityClaim claim);

    /** 이 제공자의 종류다. 본인확인 기록에 함께 저장한다. */
    IdentityProviderType type();
}
