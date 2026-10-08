package com.pitchmap.program.application;

import com.pitchmap.program.domain.Program;

/**
 * 행사 입력값의 범위다. 다른 모듈의 요청 DTO는 도메인 패키지를 참조할 수 없어서, 입력 검증에 쓸 상수를 여기서 다시 내보낸다.
 * 값은 {@link Program}이 정하고, 서비스가 도메인에서 한 번 더 검사한다.
 */
public final class ProgramLimits {

    public static final int TITLE_MAX_LENGTH = Program.TITLE_MAX_LENGTH;
    public static final int DESCRIPTION_MAX_LENGTH = Program.DESCRIPTION_MAX_LENGTH;
    public static final int LOCATION_TEXT_MAX_LENGTH = Program.LOCATION_TEXT_MAX_LENGTH;
    public static final int CAPACITY_MIN = Program.CAPACITY_MIN;
    public static final int CAPACITY_MAX = Program.CAPACITY_MAX;
    public static final int FEE_MIN = Program.FEE_MIN;
    public static final int FEE_MAX = Program.FEE_MAX;
    public static final int PAYMENT_DEADLINE_MINUTES_MIN = Program.PAYMENT_DEADLINE_MINUTES_MIN;
    public static final int PAYMENT_DEADLINE_MINUTES_MAX = Program.PAYMENT_DEADLINE_MINUTES_MAX;

    private ProgramLimits() {}
}
