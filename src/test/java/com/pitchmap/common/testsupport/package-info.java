/**
 * 단위·통합 테스트가 함께 쓰는 지원 코드.
 *
 * <ul>
 *   <li>{@link com.pitchmap.common.testsupport.IntegrationTest}: 통합 테스트는 이 어노테이션만 쓴다. 실행 전체가 나눠 쓰는 MySQL
 *       컨테이너, 이동 가능한 시계, 테스트가 끝날 때마다 테이블 비우기가 함께 붙는다.
 *   <li>{@link com.pitchmap.common.testsupport.MutableClock}: 고정해 두거나 원하는 만큼 옮길 수 있는 시계.
 *   <li>{@link com.pitchmap.common.testsupport.TestSequence}: 빌더가 UNIQUE 값의 기본값으로 쓰는 순번.
 * </ul>
 *
 * <h2>테스트 데이터 빌더</h2>
 *
 * 빌더는 도메인 클래스와 같은 패키지의 테스트 소스에 둔다(예: {@code com.pitchmap.member.domain.MemberBuilder}).
 *
 * <ul>
 *   <li>필수 값마다 유효한 기본값을 두고, 테스트가 관심 있는 값만 덮어쓴다.
 *   <li>불변식 검증을 우회하지 않도록 운영 코드의 생성자나 정적 팩터리로 만든다. 엔티티에 {@code @Builder}를 달지 않는다.
 *   <li>UNIQUE 값의 기본값은 {@code build()}를 부를 때 {@link com.pitchmap.common.testsupport.TestSequence}에서 꺼낸다. 그래야 빌더
 *       하나로 서로 다른 행을 여러 개 만들 수 있다.
 *   <li>시각의 기본값은 {@link com.pitchmap.common.testsupport.MutableClock#DEFAULT_INSTANT}에서 얻는다.
 * </ul>
 */
package com.pitchmap.common.testsupport;
