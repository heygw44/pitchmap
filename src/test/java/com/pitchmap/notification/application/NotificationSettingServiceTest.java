package com.pitchmap.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.notification.domain.NotificationSetting;
import com.pitchmap.notification.domain.NotificationSettingRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationSettingServiceTest {

    private static final long MEMBER_ID = 7L;

    private final NotificationSettingRepository repository = mock(NotificationSettingRepository.class);
    private final NotificationSettingService service = new NotificationSettingService(repository);

    @Test
    @DisplayName("[F-20] 요청 길이의 상한은 설정할 수 있는 알림 종류의 수와 같다")
    void typeCountMatchesConfigurableTypes() {
        assertThat(NotificationSettingService.TYPE_COUNT).isEqualTo(NotificationEventTypes.EMAIL_CONFIGURABLE.size());
    }

    @Test
    @DisplayName("[F-20] 저장한 설정이 없으면 모든 종류를 정해진 순서로 기본값(이메일 받기)으로 돌려준다")
    void listReturnsAllTypesWithDefault() {
        when(repository.findByMemberId(MEMBER_ID)).thenReturn(List.of());

        List<NotificationSettingItem> items = service.list(MEMBER_ID);

        assertThat(items)
                .extracting(NotificationSettingItem::type)
                .containsExactlyElementsOf(NotificationEventTypes.EMAIL_CONFIGURABLE);
        assertThat(items).allMatch(NotificationSettingItem::emailEnabled);
    }

    @Test
    @DisplayName("[F-20] 저장한 설정은 해당 종류에 덮어쓰고 나머지는 기본값이다")
    void listMergesStoredSettings() {
        when(repository.findByMemberId(MEMBER_ID))
                .thenReturn(List.of(NotificationSetting.of(MEMBER_ID, NotificationEventTypes.BASECAMP_KICKED, false)));

        List<NotificationSettingItem> items = service.list(MEMBER_ID);

        assertThat(items)
                .filteredOn(item -> !item.emailEnabled())
                .extracting(NotificationSettingItem::type)
                .containsExactly(NotificationEventTypes.BASECAMP_KICKED);
        assertThat(items).hasSize(NotificationEventTypes.EMAIL_CONFIGURABLE.size());
    }

    @Test
    @DisplayName("[F-20] 설정을 바꾸면 기존 설정을 지우고 요청한 항목을 저장하며, 빠진 종류는 기본값으로 돌려준다")
    void replaceDeletesThenSavesAndRevertsOmitted() {
        List<NotificationSettingItem> requested =
                List.of(new NotificationSettingItem(NotificationEventTypes.BASECAMP_APPLIED, false));

        List<NotificationSettingItem> result = service.replace(MEMBER_ID, requested);

        verify(repository).deleteByMemberId(MEMBER_ID);
        verify(repository).saveAll(any());
        assertThat(result.get(0))
                .isEqualTo(new NotificationSettingItem(NotificationEventTypes.BASECAMP_APPLIED, false));
        assertThat(result.subList(1, result.size())).allMatch(NotificationSettingItem::emailEnabled);
    }

    @Test
    @DisplayName("[F-20] 모르는 알림 종류면 INVALID_INPUT이고 아무것도 지우거나 저장하지 않는다")
    void replaceRejectsUnknownType() {
        List<NotificationSettingItem> requested = List.of(new NotificationSettingItem("NO_SUCH_TYPE", false));

        assertThatThrownBy(() -> service.replace(MEMBER_ID, requested))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        verify(repository, never()).deleteByMemberId(MEMBER_ID);
    }

    @Test
    @DisplayName("[F-20] 같은 알림 종류가 두 번 있으면 INVALID_INPUT이고 아무것도 지우거나 저장하지 않는다")
    void replaceRejectsDuplicateType() {
        List<NotificationSettingItem> requested = List.of(
                new NotificationSettingItem(NotificationEventTypes.BASECAMP_APPLIED, false),
                new NotificationSettingItem(NotificationEventTypes.BASECAMP_APPLIED, true));

        assertThatThrownBy(() -> service.replace(MEMBER_ID, requested))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        verify(repository, never()).deleteByMemberId(MEMBER_ID);
    }
}
