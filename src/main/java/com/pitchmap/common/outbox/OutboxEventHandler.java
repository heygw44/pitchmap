package com.pitchmap.common.outbox;

/**
 * 한 종류의 이벤트를 처리하는 구현체다. 처리기는 어느 모듈에든 둘 수 있고, 발행기가 스프링 빈 목록으로 찾아서
 * {@link #eventType()}이 같은 메시지를 넘긴다. 같은 이벤트 종류를 처리하는 구현체는 하나만 둔다.
 *
 * <p>발행기는 트랜잭션 밖에서 {@link #handle(OutboxMessage)}를 호출한다. 그래서 처리기는 DB를 바꿔야 하면
 * 자기 트랜잭션을 스스로 연다.
 *
 * <p>전달은 적어도 한 번이다. 처리는 끝났는데 발행 완료 표시 전에 서버가 죽거나 임대 시간이 지나면
 * 같은 메시지가 다시 올 수 있다. 따라서 구현체는 멱등이어야 한다. 예를 들어 {@link OutboxMessage#eventId()}로
 * 만든 유니크 키로 중복 저장을 막는다.
 *
 * <p>처리에 실패하면 예외를 던진다. 발행기가 시도 횟수를 올리고 나중에 다시 호출한다.
 */
public interface OutboxEventHandler {

    /** 이 처리기가 맡는 이벤트 종류. {@code outbox_event.event_type} 값과 같아야 한다. */
    String eventType();

    void handle(OutboxMessage message);
}
