package com.wzy.paymentcenter;


import com.wzy.paymentcenter.ruler.PayCallBack;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest
class PaymentCenterApplicationTests {

    @Resource
    private PayCallBack payCallBack;

    @Test
    void PayCallBackTest() {
        Map<String, String> params = new HashMap<>();
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("out_trade_no", "1234");
        params.put("seller_id", "20230401-0001");
        params.put("total_amount", "100");
        params.put("app_id","UmeAirport");
        params.put("trade_no", "20260120-0001");
        System.out.println(payCallBack.alipayNotify(params));
    }

}
