package com.pitchmap.member.domain;

import java.util.Collection;
import java.util.Optional;

public interface DisposableEmailDomainRepository {

    boolean existsByDomainIn(Collection<String> domains);

    DisposableEmailDomain save(DisposableEmailDomain domain);

    Optional<DisposableEmailDomain> findById(String domain);

    long count();
}
