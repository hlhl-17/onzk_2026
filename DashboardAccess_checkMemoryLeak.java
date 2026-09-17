package dashboard;
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


public class DashboardAccess_checkMemoryLeak extends Simulation {

    // 一定の負荷強度を維持したまま、試験時間だけを変えて劣化傾向を確認する

    private static final String baseUrl = "http://dbbngrmtsuptool.bpfdev-awspri1.imhds.net";
    private static final String cookieHeaderSessionKey = "cookieHeaderValue";
    // Cookie は 1 レコードを 1 セットとして定義し、グループ実行ごとに順番に使い回す
    private static final List<Map<String, Object>> cookieHeaderFeedRecords = List.of(
            Map.of(cookieHeaderSessionKey, "ここにクッキー値1を入れる"));

    private static final String pageUrl = "/dashboard";

    // session
    private static final String getUrl1 = "/api/auth/session";
    // getNotice お知らせ文言取得
    private static final String postUrl1 = "/api/DA0100/db/admin/get-notice";
    // getShopPermission ログインユーザーのショップ権限情報取得
    private static final String postUrl2 = "/api/DA0100/db/admin/get-shop-permission";
    private static final String postUrl2Body = "{\"retoolUserId\":\"onozuka_harumi@ims-sol.co.jp\"}";
    // getCountSalesRecordError 売上計上エラー合計取得
    private static final String postUrl3 = "/api/DA0100/db/remote/get-count-sales-record-error";
    private static final String postUrl3Body = "{\"shopId\":\"111152\", \"currentDateTime\":\"2026-09-24 11:00:00\"}";
    // getAmountForShopId 売上金額・取引件数エリアの値取得
    private static final String postUrl4 = "/api/DA0100/db/remote/get-amount-for-shop-id";
    private static final String postUrl4Body = "{\"shopId\":\"111152\", \"currentDateTime\":\"2026-09-24 11:00:00\"}";
    // getCountSmsMailError SMS/メール送信エラー合計取得 shopIdがpayloadにないが・・・
    private static final String postUrl5 = "/api/DA0100/db/remote/get-count-sms-mail-error";
    private static final String postUrl5Body = "{\"shopId\":\"111152\"}";
    // getWeekSalesAmount 棒グラフエリア（合計値・連携手段・売上日付）取得
    private static final String postUrl6 = "/api/DA0100/db/remote/get-week-sales-amount";
    private static final String postUrl6Body = "{\"shopId\":\"111152\", \"currentDateTime\":\"2026-09-24 11:00:00\"}";
    // getUncheckedEnqueteAlertMessage 未確認のアンケート回答件数取得
    private static final String postUrl7 = "/api/DA0100/db/remote/get-unchecked-enquete-alert-message";
    private static final String postUrl7Body = "{\"shopId\":\"111152\"}";
    // getUnconfirmedPurchaseRequestAlertMessage 未対応の購入リクエスト件数取得
    private static final String postUrl8 = "/api/DA0100/db/remote/get-unconfirmed-purchase-request-alert-message";
    private static final String postUrl8Body = "{\"shopId\":\"111152\"}";
    // getCountCartStatus カートステータス(決済待ち(期限切れ),発送/お渡し待ち,売上確定保留,前受発生完了)件数取得
    private static final String postUrl9 = "/api/DA0100/db/remote/get-count-cart-status";
    private static final String postUrl9Body = "{\"shopId\":\"111152\", \"currentDateTime\":\"2026-09-24 11:00:00\"}";
    // 店舗情報取得
    private static final String postUrl10 = "/api/util/db/remote/get-store-name-and-shop-name";
    private static final String postUrl10Body = "{\"shopId\":\"111152\"}";

    private static final String openPageStatusKey = "openDashboardStatus";
    private static final String sessionStatusKey = "sessionStatus";
    private static final String noticeStatusKey = "getNoticeStatus";
    private static final String shopPermissionStatusKey = "getShopPermissionStatus";
    private static final String countSalesRecordErrorStatusKey = "getCountSalesRecordErrorStatus";
    private static final String amountForShopIdStatusKey = "getAmountForShopIdStatus";
    private static final String countSmsMailErrorStatusKey = "getCountSmsMilErrorStatus";
    private static final String weekSalesAmountStatusKey = "getWeekSalesAmountStatus";
    private static final String uncheckedEnqueteAlertStatusKey = "getUncheckedEnqueteAlertMessageStatus";
    private static final String unconfirmedPurchaseRequestAlertStatusKey = "getUnconfirmedPurchaseRequestAlertMessageStatus";
    private static final String countCartStatusKey = "getCountCartStatus";
    private static final String storeNameStatusKey = "getStoreNameAndShopNameStatus";

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
            "open-dashboard",
            pageUrl,
            openPageStatusKey,
            200, 204, 302, 304, 307, 308)
            .exec(execGetWithStatusMetric("session", getUrl1, sessionStatusKey, 200, 204, 302, 304, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-notice",
                    postUrl1,
                    postUrl1Body,
                    noticeStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-shop-permission",
                    postUrl2,
                    postUrl2Body,
                    shopPermissionStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-count-sales-record-error",
                    postUrl3,
                    postUrl3Body,
                    countSalesRecordErrorStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-amount-for-shop-id",
                    postUrl4,
                    postUrl4Body,
                    amountForShopIdStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-count-sms-mail-error",
                    postUrl5,
                    postUrl5Body,
                    countSmsMailErrorStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-week-sales-amount",
                    postUrl6,
                    postUrl6Body,
                    weekSalesAmountStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-unchecked-enquete-alert-message",
                    postUrl7,
                    postUrl7Body,
                    uncheckedEnqueteAlertStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-unconfirmed-purchase-request-alert-message",
                    postUrl8,
                    postUrl8Body,
                    unconfirmedPurchaseRequestAlertStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-count-cart-status",
                    postUrl9,
                    postUrl9Body,
                    countCartStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308))
            .exec(execPostWithStatusMetric(
                    "get-store-name-and-shop-name",
                    postUrl10,
                    postUrl10Body,
                    storeNameStatusKey,
                    200, 201, 202, 204, 301, 302, 303, 307, 308));

    private static final ChainBuilder requestGroup = feed(listFeeder(cookieHeaderFeedRecords).circular())
            .exec(group("dashboard-load-request-group")
                    .on(sequentialRequestGroup));

    private static final ScenarioBuilder dashboardScenario = scenario("DashboardAccess_checkMemoryLeak")
            .exec(requestGroup);

    private static OpenInjectionStep[] setupDefinitions() {
        return new OpenInjectionStep[] {
                // 性能試験前の開発環境の動作確認用
                 constantUsersPerSec(1.0 / 60.0).during(Duration.ofMinutes(1)),

                // オンピーク: 1470 req数 (12 API/1シナリオ -> 約 21 scn/min)
//                constantUsersPerSec(21.0 / 60.0).during(Duration.ofMinutes(10)),

                // オフピーク: 4550 req数 (12 API/1シナリオ -> 約 13 scn/min)
//                constantUsersPerSec(13.0 / 60.0).during(Duration.ofMinutes(50))
        };
    }

    {
        setUp(dashboardScenario.injectOpen(setupDefinitions())).protocols(httpProtocol);
    }
}