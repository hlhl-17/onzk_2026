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

public class CustomerDetailAccess extends Simulation {

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

    private static final String pageUrl = "/customers/[room_id]";

    // session
    private static final String getUrl1 = "/api/auth/session";
    // ショップ情報取得
    private static final String postUrl1 = "/api/util/db/support/select-selected-shop-id";
    private static final String postUrl1Body = "{\"retoolUserId\":\"onozuka_harumi@ims-sol.oc.jp\"}";
    // 店舗情報取得
    private static final String postUrl2 = "/api/util/db/remote/get-store-name-and-shop-name";
    private static final String postUrl2Body = "{\"shopId\":\"509999\"}";
    //リモート会員詳細取得
    private static final String postUrl3 = "/api/CU0200/TopTab/api/remote/get-member-details";
    private static final String postUrl3Body = "{\"did\":\"_________\", \"shopId\":\"509999\", \"deviceId\":\"________\", \"callGf\":true}";
    // ショップ代表取組先コード取得
    private static final String postUrl4 = "/api/CU0200/TopTab/db/remote/get-shop-representative-partner-code";
    private static final String postUrl4Body = "{\"shopId\":\"509999\"}";
    // BAアイテム属性取得 - キャッシュ作成日時確認
    private static final String postUrl5 = "/api/CU0200/TopTab/db/remote/get-ba-item-attributes-create-date-time";
    private static final String postUrl5Body = "{\"did\":\"\"}";
    // BAアイテム属性取得 - キャッシュ取得（DB）
    private static final String postUrl6 = "/api/CU0200/TopTab/db/remote/get-ba-item-attributes";
    private static final String postUrl6Body = "{\"did\":\"\"}";
    // BAアイテム属性取得 - キャッシュ削除
    private static final String postUrl7 = "/api/CU0200/TopTab/db/remote/delete-ba-item-attributes";
    private static final String postUrl7Body = "{\"did\":\"\"}";
    // BAアイテム属性取得 - BigQueryから名称データ取得
    private static final String postUrl8 = "/api/CU0200/TopTab/ba/get-kbi-j-did-meisyo-bb-{0|1|2}";
    private static final String postUrl8Body = "{\"did\":\"\"}";
    // BAアイテム属性取得 - キャッシュ書き込み
    private static final String postUrl9 = "/api/CU0200/TopTab/db/remote/insert-into-ba-item-attributes";
    private static final String postUrl9Body = "{\"did\":\"\"}";
    //年間購買金額・回数取得（全体）
    private static final String postUrl10 = "/api/CU0200/TopTab/ba/get-top-tab-info-by-did-{0|1|2}";
    private static final String postUrl10Body = "{\"did\":\"\"}";
    // 利用頻度Top3取得
    private static final String postUrl11 = "/api/CU0200/TopTab/ba/get-usage-frequency-top3-by-did-{0|1|2}";
    private static final String postUrl11Body = "{\"did\":\"\"}";
    // ショップ別年間購買金額取得
    private static final String postUrl12 = "/api/CU0200/TopTab/ba/get-total-amount-one-year-by-did-{0|1|2}";
    private static final String postUrl12Body = "{\"did\":\"\", \"representativePartnerCodes\":\"______\"}";
    //ショップ別前回購買日取得
    private static final String postUrl13 = "/api/CU0200/TopTab/ba/get-latest-purchase-date-by-did-{0|1|2}";
    private static final String postUrl13Body = "{\"did\":\"\", \"representativePartnerCodes\":\"______\"}";
    // 顧客名更新
    private static final String postUrl14 = "/api/CU0200/api/remote/update-customer-name";
    private static final String postUrl14Body = "{\"roomId\":\"\", \"shopId\":\"509999\"}";

    private static final String openPageStatusKey = "openCustomerDetailStatus";
    private static final String sessionStatusKey = "sessionStatus";
    private static final String selectShopStatusKey = "selectSelectedShopIdStatus";
    private static final String storeNameStatusKey = "getStoreNameAndShopNameStatus";
    private static final String memberDetailsStatusKey = "getMemberDetailsStatus";
    private static final String shopRepresentativePartnerCodeStatusKey = "getShopRepresentativePartnerCodeStatus";
    private static final String baItemAttributesCreateDateTimeStatusKey = "getBaItemAttributesCreateDateTimeStatus";
    private static final String getBaItemAttributesStatusKey = "getBaItemAttributesStatus";
    private static final String deleteBaItemAttributesStatusKey = "deleteBaItemAttributesStatus";
    private static final String KbiJDidMeisyoBbStatusKey = "getKbiJDidMeisyoBbStatus";
    private static final String insertIntoBaItemAttributesStatusKey = "insertIntoBaItemAttributesStatus";
    private static final String topTabInfoStatusKey = "geTopTabInfoByDidStatus";
    private static final String usageFrequencyTop3StatusKey = "getUsageFrequencyTop3ByDidStatus";
    private static final String totalAmountOneYearStatusKey = "getTotalAmountOneYearByDidStatus";
    private static final String latestPurchaseDateStatusKey = "getLatestPurchaseDateByDidStatus";
    private static final String customerNameStatusKey = "updateCustomerNameStatus";

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
    private static final ChainBuilder sequentialRequestGroup = exec(getRequest("open-customer-detail", pageUrl)
                     .check(status().in(200, 204, 302, 304, 307, 308).saveAs(openPageStatusKey)))
            .exec(recordStatusMetric("open-customer-detail", openPageStatusKey))
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
            .exec(postRequest("get-member-details", postUrl3, postUrl3Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(memberDetailsStatusKey)))
            .exec(recordStatusMetric("get-member-details", memberDetailsStatusKey))
            .exec(postRequest("get-shop-representative-partner-code", postUrl4, postUrl4Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(shopRepresentativePartnerCodeStatusKey)))
            .exec(recordStatusMetric("get-shop-representative-partner-code", shopRepresentativePartnerCodeStatusKey))
            .exec(postRequest("get-ba-item-attributes-create-date-time", postUrl4, postUrl4Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(baItemAttributesCreateDateTimeStatusKey)))
            .exec(recordStatusMetric("get-ba-item-attributes-create-date-time", baItemAttributesCreateDateTimeStatusKey))
            .exec(postRequest("get-ba-item-attributes", postUrl5, postUrl5Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(getBaItemAttributesStatusKey)))
            .exec(recordStatusMetric("get-ba-item-attributes", getBaItemAttributesStatusKey))
            .exec(postRequest("get-ba-item-attributes", postUrl6, postUrl6Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(getBaItemAttributesStatusKey)))
            .exec(recordStatusMetric("get-ba-item-attributes", getBaItemAttributesStatusKey))
            .exec(postRequest("delete-ba-item-attributes", postUrl7, postUrl7Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(deleteBaItemAttributesStatusKey)))
            .exec(recordStatusMetric("delete-ba-item-attributes", deleteBaItemAttributesStatusKey))
            .exec(postRequest("get-kbi-j-did-meisyo-bb", postUrl8, postUrl8Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(KbiJDidMeisyoBbStatusKey)))
            .exec(recordStatusMetric("get-kbi-j-did-meisyo-bb", KbiJDidMeisyoBbStatusKey))
            .exec(postRequest("insert-into-ba-item-attributes", postUrl9, postUrl9Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(insertIntoBaItemAttributesStatusKey)))
            .exec(recordStatusMetric("insert-into-ba-item-attributes", insertIntoBaItemAttributesStatusKey))
            .exec(postRequest("get-top-tab-info-by-did", postUrl10, postUrl10Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(topTabInfoStatusKey)))
            .exec(recordStatusMetric("get-top-tab-info-by-did", topTabInfoStatusKey))
            .exec(postRequest("get-usage-frequency-top3-by-did", postUrl11, postUrl11Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(usageFrequencyTop3StatusKey)))
            .exec(recordStatusMetric("get-usage-frequency-top3-by-did", usageFrequencyTop3StatusKey))
            .exec(postRequest("get-total-amount-one-year-by-did", postUrl12, postUrl12Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(totalAmountOneYearStatusKey)))
            .exec(recordStatusMetric("get-total-amount-one-year-by-did", totalAmountOneYearStatusKey))
            .exec(postRequest("get-latest-purchase-date-by-did", postUrl13, postUrl13Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(latestPurchaseDateStatusKey)))
            .exec(recordStatusMetric("get-latest-purchase-date-by-did", latestPurchaseDateStatusKey))
            .exec(postRequest("update-customer-name", postUrl14, postUrl14Body)
                    .check(status().in(200, 201, 202, 204, 301, 302, 303, 307, 308)
                            .saveAs(customerNameStatusKey)))
            .exec(recordStatusMetric("update-customer-name", customerNameStatusKey));

    private static final ChainBuilder requestGroup = feed(listFeeder(cookieHeaderFeedRecords).circular())
            .exec(group("customer-load-request-group")
                    .on(sequentialRequestGroup));

    private static final ScenarioBuilder customerScenario = scenario("CustomerTotalAccess")
            .during(Duration.ofMinutes(targetDurationMinutes))
            .on(
                    pace(Duration.ofMillis(intervalMillis))
                            .exec(requestGroup));

    {
        setUp(customerScenario.injectOpen(atOnceUsers(userCount))).protocols(httpProtocol);
    }
}