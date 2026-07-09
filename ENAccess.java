package example;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class CustomerAccess extends Simulation {

    // 一定の負荷強度を維持したまま、試験時間だけを変えて劣化傾向を確認する

    private static final String baseUrl = "http://qbbngrmtsuptool.bpfdev-awspri1.imhds.net";
    // ピーク時の同時操作人数を VU として定義
    private static final int userCount = 280;
    // 試験継続時間（分）
    private static final int targetDurationMinutes = 1;
    // 1 分あたりの目標グループ実行回数
    // 瞬間集中を見る場合は 2000、1 時間あたり 30k を見る場合は 500 を目安にする
    private static final int targetGroupExecutionsPerMinute = 5600;
    private static final String cookieHeaderSessionKey = "cookieHeaderValue";
    // Cookie は 1 レコードを 1 セットとして定義し、グループ実行ごとに順番に使い回す
    private static final List<Map<String, Object>> cookieHeaderFeedRecords = List.of(
            Map.of(cookieHeaderSessionKey, "ここにクッキー値1を入れる"),
            Map.of(cookieHeaderSessionKey, "ここにクッキー値2を入れる"),
            Map.of(cookieHeaderSessionKey, "ここにクッキー値3を入れる"),
            Map.of(cookieHeaderSessionKey, "ここにクッキー値4を入れる"));

    private static final long intervalMillis = Math.max(
            1L,
            Duration.ofMinutes(1).toMillis() * userCount / targetGroupExecutionsPerMinute);

    private static final String pageUrl = "/customers";

    // session
    private static final String getUrl1 = "/api/auth/session";
    // ショップ情報取得
    private static final String postUrl1 = "/api/util/db/support/select-selected-shop-id";
    private static final String postUrl1Body = "{\"retoolUserId\":\"hirota_ayaka@ims-sol.oc.jp\"}";
    // 店舗情報取得
    private static final String postUrl2 = "/api/util/db/remote/get-store-name-and-shop-name";
    private static final String postUrl2Body = "{\"shopId\":\"509999\"}";
    // アンケート名データ取得（ドロップダウン）
    private static final String postUrl3 = "/api/EN0100/db/remote/get-enquete-name-data";
    private static final String postUrl3Body = "{\"shopId\":\"509999\"}";
    // アンケート回答件数取得API
    private static final String postUrl4 = "/api/EN0100/db/remote/get-enquete-answer-data-count";
    private static final String postUrl4Body = "{\"shopId\":\"509999\", \"unconfirmedOnly\":false}";
    // アンケート回答データ取得API
    private static final String postUrl5 = "/api/EN0100/db/remote/get-enquete-answer-data";
    private static final String postUrl5Body = "{\"shopId\":\"509999\", \"unconfirmedOnly\":false, \"limit\":18, \"offset\":0, \"sortColumn\":\"create_date_time\", \"sortDesc\":true}";

    private static final String openPageStatusKey = "openEnquetesAnswersStatus";
    private static final String sessionStatusKey = "sessionStatus";
    private static final String selectShopStatusKey = "selectSelectedShopIdStatus";
    private static final String storeNameStatusKey = "getStoreNameAndShopNameStatus";
    private static final String enqueteNameStatusKey = "getEnqueteNameDataStatus";
    private static final String answerCountStatusKey = "getEnqueteAnswerDataCountStatus";
    private static final String answerDataStatusKey = "getEnqueteAnswerDataStatus";

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

    // check で保存したステータスコードをもとに、リクエストごとの成功/失敗を判定してカスタムメトリクスとして記録する
    // これにより、Gatling のレポート上でリクエストごとの成功率を確認できるようになる
    // 例えば、open-enquetes-answers-status-200 というメトリクスは、open-enquetes-answers
    // リクエストのうちステータスコード 200 のものをカウントする
    private static final ChainBuilder sequentialRequestGroup = exec(getRequest("open-enquetes-answers", pageUrl)
            .check(status().in(200, 204, 302, 304, 307, 308).saveAs(openPageStatusKey)))
            .exec(recordStatusMetric("open-enquetes-answers", openPageStatusKey))
            .exec(getRequest("session", getUrl1)
                    .check(status().in(200, 204, 302, 304, 307, 308).saveAs(sessionStatusKey)))
            .exec(recordStatusMetric("session", sessionStatusKey))
            .exec(postRequest("select-selected-shop-id", postUrl1, postUrl1Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(selectShopStatusKey)))
            .exec(recordStatusMetric("select-selected-shop-id", selectShopStatusKey))
            .exec(postRequest("get-store-name-and-shop-name", postUrl2, postUrl2Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(storeNameStatusKey)))
            .exec(recordStatusMetric("get-store-name-and-shop-name", storeNameStatusKey))
            .exec(postRequest("get-enquete-name-data", postUrl3, postUrl3Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(enqueteNameStatusKey)))
            .exec(recordStatusMetric("get-enquete-name-data", enqueteNameStatusKey))
            .exec(postRequest("get-enquete-answer-data-count", postUrl4, postUrl4Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(answerCountStatusKey)))
            .exec(recordStatusMetric("get-enquete-answer-data-count", answerCountStatusKey))
            .exec(postRequest("get-enquete-answer-data", postUrl5, postUrl5Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(answerDataStatusKey)))
            .exec(recordStatusMetric("get-enquete-answer-data", answerDataStatusKey));

    private static final ChainBuilder requestGroup = feed(listFeeder(cookieHeaderFeedRecords).circular())
            .exec(group("enquete-fixed-load-request-group")
                    .on(sequentialRequestGroup));

    private static final ScenarioBuilder enqueteScenario = scenario("EnqueteFixedLoadDuration")
            .during(Duration.ofMinutes(targetDurationMinutes))
            .on(
                    pace(Duration.ofMillis(intervalMillis))
                            .exec(requestGroup));

    {
        setUp(enqueteScenario.injectOpen(atOnceUsers(userCount))).protocols(httpProtocol);
    }
}