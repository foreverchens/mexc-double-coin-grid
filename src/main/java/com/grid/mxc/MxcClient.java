package com.grid.mxc;

import com.alibaba.fastjson.JSON;
import com.grid.mxc.common.OrderTypeEnum;
import com.grid.mxc.common.SideTypeEnum;
import com.grid.mxc.common.SignTool;
import com.grid.mxc.entity.Order;
import com.grid.mxc.entity.OrderParam;
import com.grid.mxc.entity.PriceBook;

import org.apache.commons.lang.StringUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 抹茶 V3 APi的部分实现
 *
 * @author yyy
 */
@Slf4j
public class MxcClient {


	private static final String HEADER_API_KEY = "X-MEXC-APIKEY";

	private static final String AK;
	private static final String SK;
	private static final OkHttpClient HTTP_CLIENT;

	static {
		InputStream resourceAsStream = MxcClient.class.getResourceAsStream("/application-grid.yaml");
		Properties properties = new Properties();
		try {
			properties.load(resourceAsStream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		AK = properties.getProperty("ak");
		SK = properties.getProperty("sk");
		HTTP_CLIENT = new OkHttpClient();
		if (StringUtils.isBlank(AK) || StringUtils.isBlank(SK)) {
			throw new RuntimeException("as || sk is blank");
		}
	}


	public static String getServerTime() throws Exception {
		Request request = new Request.Builder().url("https://api.mexc.com/api/v3/time").build();
		try (Response response = HTTP_CLIENT.newCall(request).execute()) {
			assert response.body() != null;
			return JSON.parseObject(response.body().string()).getString("serverTime");
		}
	}


	public static BigDecimal getPrice(String symbol) throws Exception {
		Request request =
				new Request.Builder().url("https://api.mexc.com/api/v3/ticker/price?symbol=" + symbol).build();
		try (Response response = HTTP_CLIENT.newCall(request).execute()) {
			assert response.body() != null;
			return JSON.parseObject(response.body().string()).getBigDecimal("price");
		}
	}

	public static PriceBook getPriceBook(String symbol) throws IOException {
		Request request =
				new Request.Builder().url("https://api.mexc.com/api/v3/ticker/bookTicker?symbol=" + symbol).build();
		try (Response response = HTTP_CLIENT.newCall(request).execute()) {
			assert response.body() != null;
			return JSON.parseObject(response.body().string(), PriceBook.class);
		}
	}

	public static String createOrder(OrderParam param) throws Exception {
		Map<String, String> params = new HashMap<>(16);
		params.put("symbol", param.getSymbol());
		params.put("side", param.getSide());
		params.put("recvWindow", "60000");
		params.put("timestamp", getServerTime());

		String type = param.getType();
		params.put("type", type);
		if (OrderTypeEnum.LIMIT.equals(type) || OrderTypeEnum.IMMEDIATE_OR_CANCEL.equals(type)) {
			params.put("quantity", param.getQuantity());
			params.put("price", param.getPrice());
		} else if (OrderTypeEnum.MARKET.equals(type)) {
			String side = param.getSide();
			if (SideTypeEnum.BUY.equals(side)) {
				params.put("quoteOrderQty", param.getQuoteOrderQty());
			} else {
				params.put("quantity", param.getQuantity());
			}
		}
		String signature = SignTool.sign(SignTool.toQueryStr(params), SK);
		params.put("signature", signature);

		Request request =
				new Request.Builder().url("https://api.mexc.com/api/v3/order?" + SignTool.toQueryStr(params)).header(HEADER_API_KEY, AK).post(RequestBody.create("", MediaType.parse("application/json"))).build();

		try (Response response = HTTP_CLIENT.newCall(request).execute()) {
			assert response.body() != null;
			return JSON.parseObject(response.body().string()).getString("orderId");
		}
	}

	public static Order getOrder(String symbol, String orderId) throws Exception {
		Map<String, String> params = new HashMap<>(16);
		params.put("symbol", symbol);
		params.put("recvWindow", "60000");
		params.put("timestamp", getServerTime());
		params.put("orderId", orderId);
		String signature = SignTool.sign(SignTool.toQueryStr(params), SK);
		params.put("signature", signature);

		Request request =
				new Request.Builder().url("https://api.mexc.com/api/v3/order?" + SignTool.toQueryStr(params)).header(HEADER_API_KEY, AK).get().build();
		try (Response response = HTTP_CLIENT.newCall(request).execute()) {
			assert response.body() != null;
			return JSON.parseObject(response.body().string(), Order.class);
		}
	}
}
