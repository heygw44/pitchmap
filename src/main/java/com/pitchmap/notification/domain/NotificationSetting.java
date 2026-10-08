package com.pitchmap.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 회원이 알림 종류 하나에 정한 이메일 수신 여부. 회원은 다른 모듈의 엔티티라서 ID로만 가리킨다.
 * 한 회원은 종류마다 설정을 하나만 가지므로 (회원, 종류)를 기본 키로 쓴다.
 *
 * <p>기본 키를 호출하는 쪽이 직접 정하기 때문에, Spring Data의 {@code save()}는 이 엔티티가 새 것인지 알 수 없다. 그래서 키가 있는 객체를
 * 기존 행으로 보고 먼저 SELECT를 한 뒤 병합(merge)한다. 이 엔티티는 {@link Persistable}을 구현하고, 저장하거나 읽기 전에는 새 것이라고
 * 답해서 곧바로 INSERT를 보낸다.
 */
@Entity
@Table(name = "notification_setting")
@IdClass(NotificationSettingId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting implements Persistable<NotificationSettingId> {

    @Id
    @Column(name = "member_id")
    private long memberId;

    @Id
    private String type;

    @Column(name = "email_enabled")
    private boolean emailEnabled;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    private NotificationSetting(long memberId, String type, boolean emailEnabled) {
        this.memberId = memberId;
        this.type = type;
        this.emailEnabled = emailEnabled;
    }

    /** 호출하면 memberId인 회원이 type 종류의 알림을 이메일로 받을지 정한 설정을 만든다. type이 null이면 {@link IllegalArgumentException}을 던진다. */
    public static NotificationSetting of(long memberId, String type, boolean emailEnabled) {
        if (type == null) {
            throw new IllegalArgumentException("알림 종류가 null입니다.");
        }
        return new NotificationSetting(memberId, type, emailEnabled);
    }

    @Override
    public NotificationSettingId getId() {
        return new NotificationSettingId(memberId, type);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
