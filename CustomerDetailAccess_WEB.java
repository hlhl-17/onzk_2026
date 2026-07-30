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

public class CustomerDetailAccess_WEB extends Simulation {

    // 一定の負荷強度を維持したまま、試験時間だけを変えて劣化傾向を確認する

    private static final String baseUrl = "http://dbbngrmtsuptool.bpfdev-awspri1.imhds.net";
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

    /*
    TODO did, deviceId, representativePartnerCodes, roomIdをダミーの値からテストで使う値に置き換える
    */
    private static final String pageUrl = "/customers/[room_id____________________]";

    // session
    private static final String getUrl1 = "/api/auth/session";
    //顧客データ読み込み
    private static final String postUrl1 = "/api/CU0200/db/remote/get-user-info-from-room";
    private static final String postUrl1Body = "{\"roomId\":\"____________________\"}";
    // ショップ情報取得
    private static final String postUrl2 = "/api/util/db/support/select-selected-shop-id";
    private static final String postUrl2Body = "{\"retoolUserId\":\"onozuka_harumi@ims-sol.oc.jp\"}";
    // 店舗情報取得
    private static final String postUrl3 = "/api/util/db/remote/get-store-name-and-shop-name";
    private static final String postUrl3Body = "{\"shopId\":\"509999\"}";
    //ショップデータ取得
    private static final String postUrl4 = "/api/CU0200/db/remote/get-shop-data";
    private static final String postUrl4Body = "{\"shopId\":\"509999\"}";
    //顧客名更新
    private static final String postUrl5 = "/api/CU0200/api/remote/update-customer-name";
    private static final String postUrl5Body = "{\"roomId\":\"____________________\", \"shopId\":\"509999\"}";
    //リモート会員詳細取得
    private static final String postUrl6 = "/api/CU0200/TopTab/api/remote/get-member-details";
    private static final String postUrl6Body = "{\"did\":\"___________\", \"shopId\":\"509999\", \"deviceId\":\"________\", \"callGf\":true}";
    //ショップ利用状況・決済設定の取得
    private static final String postUrl7 = "/api/CU0200/WebPaymentTab/db/remote/get-shop-data";
    private static final String postUrl7Body = "{\"shopId\":\"509999\"}";
    //WEB決済履歴の取得
    private static final String postUrl8 = "/api/CU0200/WebPaymentTab/db/remote/get-cart-data";
    private static final String postUrl8Body = "{\"roomId\":\"____________________\", \"shopId\":\"509999\"}";

    private static final String openPageStatusKey = "openCustomerDetailStatus";
    private static final String sessionStatusKey = "sessionStatus";
    private static final String userInfoFromRoomKey = "getUserInfoFromRoom";
    private static final String selectShopStatusKey = "selectSelectedShopIdStatus";
    private static final String storeNameStatusKey = "getStoreNameAndShopNameStatus";
    private static final String shopDataStatusKey = "getShopDataStatus";
    private static final String updateCustomerNameStatusKey = "updateCustomerNameStatus";
    private static final String memberDetailsStatusKey = "getMemberDetailsStatus";
    private static final String shopUtilAndPaymentStatusKey = "getShopUtilAndPaymentStatus";
    private static final String cartDataStatusKey = "getCartDataStatus";

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
    // リクエストのうちステータスコード 200 のものをカウントする。
    private static final ChainBuilder sequentialRequestGroup = exec(getRequest("open-customer-detail", pageUrl)
                     .check(status().in(200, 204, 302, 304, 307, 308).saveAs(openPageStatusKey)))
            .exec(recordStatusMetric("open-customer-detail", openPageStatusKey))
            .exec(getRequest("session", getUrl1)
                    .check(status().in(200, 204, 302, 304, 307, 308).saveAs(sessionStatusKey)))
            .exec(recordStatusMetric("session", sessionStatusKey))
            .exec(postRequest("get-user-info-from-room", postUrl1, postUrl1Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(userInfoFromRoomKey)))
            .exec(recordStatusMetric("get-user-info-from-room", userInfoFromRoomKey))
            .exec(postRequest("select-selected-shop-id", postUrl2, postUrl2Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(selectShopStatusKey)))
            .exec(recordStatusMetric("select-selected-shop-id", selectShopStatusKey))
            .exec(postRequest("get-store-name-and-shop-name", postUrl3, postUrl3Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(storeNameStatusKey)))
            .exec(recordStatusMetric("get-store-name-and-shop-name", storeNameStatusKey))
            .exec(postRequest("get-shop-data", postUrl4, postUrl4Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(shopDataStatusKey)))
            .exec(recordStatusMetric("get-shop-data", shopDataStatusKey))
            .exec(postRequest("update-customer-name", postUrl5, postUrl5Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(updateCustomerNameStatusKey)))
            .exec(recordStatusMetric("update-customer-name", updateCustomerNameStatusKey))
            .exec(postRequest("get-member-details", postUrl6, postUrl6Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(memberDetailsStatusKey)))
            .exec(recordStatusMetric("get-member-details", memberDetailsStatusKey))
            .exec(postRequest("get-shop-util-and-payment-data", postUrl7, postUrl7Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(shopUtilAndPaymentStatusKey)))
            .exec(recordStatusMetric("get-shop-util-and-payment-data", shopUtilAndPaymentStatusKey))
            .exec(postRequest("get-cart-data", postUrl8, postUrl8Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(cartDataStatusKey)))
            .exec(recordStatusMetric("get-cart-data", cartDataStatusKey));

    private static final ChainBuilder requestGroup = feed(listFeeder(cookieHeaderFeedRecords).circular())
            .exec(group("customer-load-request-group")
                    .on(sequentialRequestGroup));

    private static final ScenarioBuilder customerScenario = scenario("CustomerTotalAccess_WEB")
            .during(Duration.ofMinutes(targetDurationMinutes))
            .on(
                    pace(Duration.ofMillis(intervalMillis))
                            .exec(requestGroup));

    {
        setUp(customerScenario.injectOpen(atOnceUsers(userCount))).protocols(httpProtocol);
    }
}