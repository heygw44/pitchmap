package com.pitchmap.notification.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.notification.domain.NotificationSetting;
import com.pitchmap.notification.domain.NotificationSettingRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 회원이 알림 종류별 이메일 수신 여부를 읽고 바꾼다.
 *
 * <p>저장하지 않은 종류는 기본값(이메일 받기)을 따른다. 그래서 조회는 항상 모든 종류를 정해진 순서로 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    /** 설정할 수 있는 알림 종류의 수다. 요청 목록 길이의 상한으로 쓴다. */
    public static final int TYPE_COUNT = 13;

    private final NotificationSettingRepository notificationSettingRepository;

    /** 호출하면 memberId인 회원의 모든 알림 종류별 이메일 수신 여부를 정해진 순서로 돌려준다. 저장하지 않은 종류는 기본값이다. */
    @Transactional(readOnly = true)
    public List<NotificationSettingItem> list(long memberId) {
        return merge(storedByType(memberId));
    }

    /**
     * 호출하면 memberId인 회원의 설정을 requested로 통째로 바꾸고, 바뀐 뒤의 모든 종류별 설정을 돌려준다.
     * requested에 없는 종류는 기본값으로 돌아간다. 모르는 종류나 같은 종류가 두 번 있으면 아무것도 바꾸지 않고
     * INVALID_INPUT 예외를 던진다.
     */
    @Transactional
    public List<NotificationSettingItem> replace(long memberId, List<NotificationSettingItem> requested) {
        validate(requested);
        notificationSettingRepository.deleteByMemberId(memberId);
        List<NotificationSetting> settings = requested.stream()
                .map(item -> NotificationSetting.of(memberId, item.type(), item.emailEnabled()))
                .toList();
        notificationSettingRepository.saveAll(settings);
        return merge(requested.stream().collect(Collectors.toMap(NotificationSettingItem::type, Function.identity())));
    }

    private Map<String, NotificationSettingItem> storedByType(long memberId) {
        return notificationSettingRepository.findByMemberId(memberId).stream()
                .collect(Collectors.toMap(
                        NotificationSetting::getType,
                        setting -> new NotificationSettingItem(setting.getType(), setting.isEmailEnabled())));
    }

    private static List<NotificationSettingItem> merge(Map<String, NotificationSettingItem> storedByType) {
        return NotificationEventTypes.EMAIL_CONFIGURABLE.stream()
                .map(type -> storedByType.getOrDefault(
                        type, new NotificationSettingItem(type, NotificationEventTypes.DEFAULT_EMAIL_ENABLED)))
                .toList();
    }

    private static void validate(List<NotificationSettingItem> requested) {
        Set<String> seen = new HashSet<>();
        for (NotificationSettingItem item : requested) {
            if (!NotificationEventTypes.EMAIL_CONFIGURABLE.contains(item.type())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT, "알 수 없는 알림 종류입니다.");
            }
            if (!seen.add(item.type())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT, "같은 알림 종류를 두 번 보낼 수 없습니다.");
            }
        }
    }
}
