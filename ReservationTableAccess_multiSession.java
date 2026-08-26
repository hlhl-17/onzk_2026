package reservation;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Map;


public class ReservationTableAccess_multiSession extends Simulation {

    // 一定の負荷強度を維持したまま、試験時間だけを変えて劣化傾向を確認する

    private static final String baseUrl = "http://qbbngrmtsuptool.bpfdev-awspri1.imhds.net";
    private static final String cookieHeaderSessionKey = "cookieHeaderValue";
    // Cookie は 1 レコードを 1 セットとして定義し、グループ実行ごとに順番に使い回す
    private static final List<Map<String, Object>> cookieHeaderFeedRecords = List.of(
            Map.of(cookieHeaderSessionKey, "ここにクッキー値1を入れる"),
            Map.of(cookieHeaderSessionKey, "ここにクッキー値2を入れる"),
            Map.of(cookieHeaderSessionKey, "ここにクッキー値3を入れる"),
            Map.of(cookieHeaderSessionKey, "ここにクッキー値4を入れる"));

    private static final String pageUrl = "/reservations";

    // session
    private static final String getUrl1 = "/api/auth/session";
    // ショップ情報取得
    private static final String postUrl1 = "/api/util/db/support/select-selected-shop-id";
    private static final String postUrl1Body = "{\"retoolUserId\":\"onozuka_harumi@ims-sol.co.jp\"}";
    // 店舗情報取得
    private static final String postUrl2 = "/api/util/db/remote/get-store-name-and-shop-name";
    private static final String postUrl2Body = "{\"shopId\":\"509999\"}";
    //イベント/サービス一覧取得
    private static final String postUrl4 = "/api/RE0300/db/reserve/get-reservation-targets";
    private static final String postUrl4Body = "{\"shopId\":\"509999\"}";
    //予約データ取得
    private static final String postUrl5 = "/api/RE0300/db/reserve/get-reservation-data";
    private static final String postUrl5Body = "{\"shopId\":\"509999\", \"limit\":\"___\", \"offset\":\"___\"}";
    //予約データ件数取得
    private static final String postUrl5 = "/api/RE0300/db/reserve/get-reservation-data-count";
    private static final String postUrl5Body = "{\"shopId\":\"509999\"}";

    private static final String openPageStatusKey = "openReservationTableStatus";
    private static final String sessionStatusKey = "sessionStatus";
    private static final String selectShopStatusKey = "selectSelectedShopIdStatus";
    private static final String storeNameStatusKey = "getStoreNameAndShopNameStatus";
    private static final String reservationTargetsStatusKey= "getReservationTargetsStatus";
    private static final String reservationDataStatusKey = "getReservationData";
    private static final String reservationCountStatusKey= "getReservationDataCount";

    private static final Map<String, String> commonHeaders = Map.of("Cookie", "#{" + cookieHeaderSessionKey + "}");
    private static final Map<String, String> jsonPostHeaders = Map.of(
            "Cookie", "#{" + cookieHeaderSessionKey + "}",
            "Content-Type", "application/json");
    private static final HttpProtocolBuilder httpProtocol = http.baseUrl(baseUrl);

    private static HttpRequestActionBuilder getRequest(String requestName, String url) {
        return http(requestName)
                .get(url)
                .disableFollowRedirect()
                .headers(commonHeaders)
                .check(responseTimeInMillis().lte(10000));
    }

    private static HttpRequestActionBuilder postRequest(String requestName, String url, String body) {
        return http(requestName)
                .post(url)
                .disableFollowRedirect()
                .headers(jsonPostHeaders)
                .body(StringBody(body))
                .check(responseTimeInMillis().lte(10000));
    }

    private static ChainBuilder recordStatusMetric(String requestName, String statusSessionKey) {
        return exec(dummy(session -> requestName + "-status-" + session.getInt(statusSessionKey), 0)
                .withSuccess(true));
    }

    private static ChainBuilder execGetWithStatusMetric(
            String requestName,
            String url,
            String statusSessionKey,
            Integer... expectedStatuses) {
        return exec(getRequest(requestName, url)
                .check(status().saveAs(statusSessionKey))
                .check(status().in(expectedStatuses)))
                .exec(recordStatusMetric(requestName, statusSessionKey));
    }

    private static ChainBuilder execPostWithStatusMetric(
            String requestName,
            String url,
            String body,
            String statusSessionKey,
            Integer... expectedStatuses) {
        return exec(postRequest(requestName, url, body)
                .check(status().saveAs(statusSessionKey))
                .check(status().in(expectedStatuses)))
                .exec(recordStatusMetric(requestName, statusSessionKey));
    }

    // check で保存したステータスコードをもとに、リクエストごとの成功/失敗を判定してカスタムメトリクスとして記録する
    // これにより、Gatling のレポート上でリクエストごとの成功率を確認できるようになる
    // 例えば、open-enquetes-answers-status-200 というメトリクスは、open-enquetes-answers
    // リクエストのうちステータスコード 200 のものをカウントする。
    private static final ChainBuilder sequentialRequestGroup = execGetWithStatusMetric(
            "open-reservation-table",
            pageUrl,
            openPageStatusKey,
            200, 204, 302, 304, 307, 308)
            .exec(execGetWithStatusMetric("session", getUrl1, sessionStatusKey, 200, 204, 302, 304, 307, 308))
            .exec(execPostWithStatusMetric(
                    "select-selected-shop-id",
                    postUrl1,
                    postUrl1Body,
                    selectShopStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-store-name-and-shop-name",
                    postUrl2,
                    postUrl2Body,
                    storeNameStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-reservation-targets",
                    postUrl3,
                    postUrl3Body,
                    reservationTargetsStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-reservation-data",
                    postUrl4,
                    postUrl4Body,
                    reservationDataStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-reservation-data-count",
                    postUrl5,
                    postUrl5Body,
                    reservationCountStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308));


    private static final ChainBuilder requestGroup = feed(listFeeder(cookieHeaderFeedRecords).circular())
            .exec(group("reservation-load-request-group")
                    .on(sequentialRequestGroup));

    private static final ScenarioBuilder reservationScenario = scenario("ReservationTotalAccess_multiSession")
            .exec(requestGroup);

    private static OpenInjectionStep[] setupDefinitions() {
        return new OpenInjectionStep[] {
                // 性能試験前の開発環境の動作確認用
                // constantUsersPerSec(1.0 / 60.0).during(Duration.ofMinutes(1)),

                // オンピーク: 180 req/min 相当 (9 API/1シナリオ -> 約 4 scn/min)
                constantUsersPerSec(4.0 / 60.0).during(Duration.ofMinutes(5)),

                // オフピーク: 495 req/min 相当 (9 API/1シナリオ -> 約 1 scn/min)
                constantUsersPerSec(1.0 / 60.0).during(Duration.ofMinutes(55))
        };
    }

    {
        setUp(reservationScenario.injectOpen(setupDefinitions())).protocols(httpProtocol);
    }
}