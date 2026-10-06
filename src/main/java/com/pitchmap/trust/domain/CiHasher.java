package com.pitchmap.trust.domain;

/** CI 원값을 비밀 키로 해시하는 포트다. */
public interface CiHasher {

    /** 호출하면 CI 원값의 HMAC 해시를 돌려준다. 같은 CI와 같은 키면 항상 같은 값이다. */
    CiHash hash(String ci);
}
