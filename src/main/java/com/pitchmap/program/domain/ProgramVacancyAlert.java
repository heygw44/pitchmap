package com.pitchmap.program.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 행사의 빈자리 알림을 신청한 기록이다. 회원이 해제할 때까지 남고, 자리가 돌아올 때마다 알림 대상이 된다.
 * 행은 중복 무시 INSERT로 만들고 지우거나 알림 시각을 갱신하는 일도 리포지토리의 쿼리가 맡아서, 이 클래스에는 상태를 바꾸는 메서드가 없다.
 */
@Entity
@Table(name = "program_vacancy_alert")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramVacancyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_id")
    private long programId;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "created_at")
    private Instant createdAt;
}
