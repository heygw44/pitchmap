package com.pitchmap.notification.application;

/**
 * 알림함에 넣을 알림의 내용이다. 처리기가 이벤트에서 받는 사람마다 하나씩 만들고, {@link NotificationWriter}가 저장한다.
 *
 * @param memberId 받는 회원 ID
 * @param type 알림 종류. 알림을 만든 이벤트 종류 이름과 같다
 * @param link 화면 안의 경로. 연결할 화면이 없으면 null이다
 */
record NotificationDraft(long memberId, String type, String title, String body, String link) {}
