package com.pitchmap.publicdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.pitchmap.publicdata.infra.GoCampingItem;
import com.pitchmap.spot.application.PublicSpotCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 항목 값은 고캠핑 basedList 실제 응답(fixtures/publicdata/gocamping/based-list.json)에서 가져왔다.
class GoCampingItemConverterTest {

    @Test
    @DisplayName("[F-06] 고캠핑 항목을 mapY는 위도, mapX는 경도로 읽어 장소 적재 명령으로 바꾼다")
    void convertsItemToCommandWithMapYAsLatitude() {
        // given
        GoCampingItem item = gangbyeonSari("", "127.190085215523", "35.4952105728394");

        // when
        PublicSpotCommand command = GoCampingItemConverter.convert(item).orElseThrow();

        // then
        assertThat(command.externalId()).isEqualTo("146");
        assertThat(command.name()).isEqualTo("강변사리 캠핑장");
        assertThat(command.latitude()).isEqualTo(35.4952105728394);
        assertThat(command.longitude()).isEqualTo(127.190085215523);
        assertThat(command.address()).isEqualTo("전북특별자치도 임실군 덕치면 강동로 865-20");
        assertThat(command.category()).isEqualTo("일반야영장");
        assertThat(command.phone()).isEqualTo("063-642-5351");
        assertThat(command.homepage()).isEqualTo("http://www.gang42.com");
    }

    @Test
    @DisplayName("[F-06] 상세 주소가 있으면 기본 주소 뒤에 공백 하나를 두고 붙인다")
    void joinsDetailAddressWithSingleSpace() {
        // given
        GoCampingItem item = gangbyeonSari(" 2층 관리동 ", "127.190085215523", "35.4952105728394");

        // when
        PublicSpotCommand command = GoCampingItemConverter.convert(item).orElseThrow();

        // then
        assertThat(command.address()).isEqualTo("전북특별자치도 임실군 덕치면 강동로 865-20 2층 관리동");
    }

    @Test
    @DisplayName("[F-06] 값이 비어 있는 원천 필드는 null로 바꾸고, 시설 정보에는 값이 있는 항목만 원천 이름 그대로 담는다")
    void convertsBlankValuesToNullAndKeepsOnlyFilledFacilities() {
        // given: 실제 옥화자연휴양림 항목은 주소 끝에 공백이 있고 화로대·부대시설 기타 값이 비어 있다.
        // 여기에 더해 테스트는 전화번호를 빈 문자열로, 홈페이지를 공백으로, 가능 시설 기타를 응답에 없는 값(null)으로 바꿨다.
        GoCampingItem item = new GoCampingItem(
                "2329",
                "옥화자연휴양림 국민여가오토캠핑장",
                "충청북도 청주시 상당구 미원면 운암옥화길 140 ",
                "",
                "127.694371568372",
                "36.5989501183999",
                "자동차야영장",
                "",
                " ",
                "0",
                "0",
                "0",
                "",
                "전기,물놀이장,놀이터",
                "",
                "",
                null,
                "불가능");

        // when
        PublicSpotCommand command = GoCampingItemConverter.convert(item).orElseThrow();

        // then
        assertThat(command.address()).isEqualTo("충청북도 청주시 상당구 미원면 운암옥화길 140");
        assertThat(command.phone()).isNull();
        assertThat(command.homepage()).isNull();
        assertThat(command.facilities())
                .containsExactly(
                        entry("toiletCo", "0"),
                        entry("swrmCo", "0"),
                        entry("wtrplCo", "0"),
                        entry("sbrsCl", "전기,물놀이장,놀이터"),
                        entry("animalCmgCl", "불가능"));
    }

    @Test
    @DisplayName("[F-06] 좌표를 숫자로 읽을 수 없거나 비어 있으면 그 항목을 건너뛴다")
    void skipsItemWithUnparseableCoordinate() {
        // when, then
        assertThat(GoCampingItemConverter.convert(gangbyeonSari("", "127.19x", "35.4952105728394")))
                .isEmpty();
        assertThat(GoCampingItemConverter.convert(gangbyeonSari("", "127.190085215523", "")))
                .isEmpty();
        assertThat(GoCampingItemConverter.convert(gangbyeonSari("", null, "35.4952105728394")))
                .isEmpty();
    }

    private static GoCampingItem gangbyeonSari(String addr2, String mapX, String mapY) {
        return new GoCampingItem(
                "146",
                "강변사리 캠핑장",
                "전북특별자치도 임실군 덕치면 강동로 865-20",
                addr2,
                mapX,
                mapY,
                "일반야영장",
                "063-642-5351",
                "http://www.gang42.com",
                "1",
                "1",
                "1",
                "개별",
                "전기,장작판매,온수,물놀이장,놀이터,운동장,운동시설",
                "",
                "계곡 물놀이,청소년체험시설",
                "",
                "불가능");
    }
}
