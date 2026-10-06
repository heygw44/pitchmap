package com.pitchmap.spot.domain;

/**
 * 기상청 단기예보의 5km 격자 좌표다. 기상청 단기예보 API는 위도·경도가 아니라 이 격자 번호(nx, ny)로 예보 지점을 받는다. 그래서 서버는
 * 장소를 저장할 때 격자 좌표를 한 번 계산해 두고, 날씨를 조회할 때마다 다시 계산하지 않는다.
 *
 * <p>변환식은 기상청 단기예보 오픈 API 활용가이드에 실린 람베르트 정각원추도법(Lambert Conformal Conic) C 예제를 그대로 옮겼다.
 *
 * <p>격자 번호는 X가 1~149, Y가 1~253이다. 이 범위 밖 번호로는 기상청 API에서 예보를 조회할 수 없다. 그래서 범위 밖 번호를 넘기면 생성자가
 * {@link IllegalArgumentException}을 던진다. 한국에서 멀리 떨어진 좌표를 {@link #from}에 넣으면 결과 번호가 범위를 벗어나므로 같은 예외가 난다.
 */
public record WeatherGrid(int nx, int ny) {

    private static final double EARTH_RADIUS_KM = 6371.00877;
    private static final double GRID_SPACING_KM = 5.0;
    private static final double FIRST_STANDARD_PARALLEL_RADIANS = Math.toRadians(30.0);
    private static final double SECOND_STANDARD_PARALLEL_RADIANS = Math.toRadians(60.0);
    private static final double ORIGIN_LONGITUDE_RADIANS = Math.toRadians(126.0);
    private static final double ORIGIN_LATITUDE_RADIANS = Math.toRadians(38.0);

    // 기준점(북위 38도, 동경 126도)은 격자 지도의 왼쪽 아래 모서리에서 동쪽으로 210km, 북쪽으로 675km 떨어져 있다.
    private static final double ORIGIN_X_GRID = 210.0 / GRID_SPACING_KM;
    private static final double ORIGIN_Y_GRID = 675.0 / GRID_SPACING_KM;

    // 격자 번호는 0이 아니라 1부터 센다. 그래서 기상청 예제는 투영한 좌표에 1을 더하고, 가장 가까운 격자로 반올림하려고 0.5를 더 더한다.
    private static final double GRID_INDEX_OFFSET = 1.5;

    // 기상청 격자는 가로(X) 149칸, 세로(Y) 253칸이다. 번호는 1부터 센다.
    private static final int GRID_WIDTH = 149;
    private static final int GRID_HEIGHT = 253;

    private static final double GRID_RADIUS = EARTH_RADIUS_KM / GRID_SPACING_KM;
    private static final double CONE_CONSTANT = coneConstant();
    private static final double SCALE_FACTOR = scaleFactor();
    private static final double ORIGIN_RADIUS = radiusAt(ORIGIN_LATITUDE_RADIANS);

    public WeatherGrid {
        if (!isInRange(nx, ny)) {
            throw new IllegalArgumentException(
                    "기상청 격자 범위(X 1~" + GRID_WIDTH + ", Y 1~" + GRID_HEIGHT + ")를 벗어났습니다. nx=" + nx + ", ny=" + ny);
        }
    }

    /** 호출하면 위도·경도를 기상청 단기예보 5km 격자 좌표로 바꾼다. 결과가 기상청 격자 범위 밖이면 {@link IllegalArgumentException}을 던진다. */
    public static WeatherGrid from(GeoPoint point) {
        ProjectedIndex index = project(point);
        return new WeatherGrid(index.nx(), index.ny());
    }

    /**
     * 호출하면 좌표를 격자로 바꾼 번호가 기상청 격자 범위 안인지 돌려준다. {@link #from}과 같은 계산을 쓰지만 범위 밖이어도 예외를 던지지 않는다.
     * 그래서 호출하는 쪽은 예외를 잡지 않고 범위 밖 좌표를 미리 걸러 낼 수 있다.
     */
    public static boolean covers(GeoPoint point) {
        ProjectedIndex index = project(point);
        return isInRange(index.nx(), index.ny());
    }

    private static boolean isInRange(int nx, int ny) {
        return nx >= 1 && nx <= GRID_WIDTH && ny >= 1 && ny <= GRID_HEIGHT;
    }

    private static ProjectedIndex project(GeoPoint point) {
        double radius = radiusAt(Math.toRadians(point.latitude()));
        double theta = longitudeOffsetFromOrigin(point.longitude()) * CONE_CONSTANT;
        double x = radius * Math.sin(theta) + ORIGIN_X_GRID;
        double y = ORIGIN_RADIUS - radius * Math.cos(theta) + ORIGIN_Y_GRID;
        return new ProjectedIndex(toGridIndex(x), toGridIndex(y));
    }

    // 범위를 검사하기 전의 격자 번호다. 레코드 생성자는 범위 밖 번호를 거부하므로, 범위를 먼저 확인하려고 따로 둔다.
    private record ProjectedIndex(int nx, int ny) {}

    private static double longitudeOffsetFromOrigin(double longitude) {
        double offset = Math.toRadians(longitude) - ORIGIN_LONGITUDE_RADIANS;
        if (offset > Math.PI) {
            return offset - 2 * Math.PI;
        }
        if (offset < -Math.PI) {
            return offset + 2 * Math.PI;
        }
        return offset;
    }

    private static int toGridIndex(double projected) {
        return (int) Math.floor(projected + GRID_INDEX_OFFSET);
    }

    private static double coneConstant() {
        double cosineRatio = Math.cos(FIRST_STANDARD_PARALLEL_RADIANS) / Math.cos(SECOND_STANDARD_PARALLEL_RADIANS);
        double tangentRatio =
                conformalTangent(SECOND_STANDARD_PARALLEL_RADIANS) / conformalTangent(FIRST_STANDARD_PARALLEL_RADIANS);
        return Math.log(cosineRatio) / Math.log(tangentRatio);
    }

    private static double scaleFactor() {
        return Math.pow(conformalTangent(FIRST_STANDARD_PARALLEL_RADIANS), CONE_CONSTANT)
                * Math.cos(FIRST_STANDARD_PARALLEL_RADIANS)
                / CONE_CONSTANT;
    }

    private static double radiusAt(double latitudeRadians) {
        return GRID_RADIUS * SCALE_FACTOR / Math.pow(conformalTangent(latitudeRadians), CONE_CONSTANT);
    }

    private static double conformalTangent(double latitudeRadians) {
        return Math.tan(Math.PI / 4 + latitudeRadians / 2);
    }
}
