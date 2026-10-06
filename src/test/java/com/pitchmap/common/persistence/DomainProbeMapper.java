package com.pitchmap.common.persistence;

import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DomainProbeMapper {

    int insert(@Param("domain") String domain, @Param("source") String source, @Param("createdAt") Instant createdAt);

    Optional<DomainProbeView> findByDomain(@Param("domain") String domain);
}
