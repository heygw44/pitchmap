package com.pitchmap.spot.infra;

import lombok.Getter;

/**
 * 새로 추가할 공공데이터 장소 하나.
 *
 * <p>{@link PublicSpotMapper#insertSpots}를 호출하면 MyBatis가 DB가 만든 spot.id를 spotId 필드에 채운다. 그 뒤
 * {@link PublicSpotMapper#insertDetails}가 이 값을 public_spot_detail.spot_id로 쓴다. MyBatis는 setter가 없으면 필드에 직접 값을 넣는다. 그래서
 * spotId만 final이 아니고 setter도 두지 않는다. record는 필드를 바꿀 수 없어서 클래스로 만들었다.
 */
@Getter
public class PublicSpotInsertRow {

    private Long spotId;
    private final PublicSpotColumns columns;

    public PublicSpotInsertRow(PublicSpotColumns columns) {
        this.columns = columns;
    }
}
