package com.grid.mxc;


import com.grid.mxc.api.ApiService;
import com.grid.mxc.api.TradeStat;
import com.grid.mxc.common.MxcClient;
import com.grid.mxc.common.OrderTypeEnum;
import com.grid.mxc.common.SideTypeEnum;
import com.grid.mxc.entity.Order;
import com.grid.mxc.entity.OrderParam;
import com.grid.mxc.entity.PriceBook;

import org.apache.commons.lang.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author yyy
 * @tg t.me/ychen5325
 */
@Slf4j
public class Main {

	private static int tradeDepth;
	private static String symbolA;
	private static String symbolB;
	/**
	 * 卖A买B时、A的卖出量
	 */
	private static BigDecimal swapQtyOfA;
	/**
	 * 买A卖B时、B的等价卖出量、
	 */
	private static BigDecimal eqQtyOfB;
	private static BigDecimal gridRate;
	private static BigDecimal sellRatio;
	private static BigDecimal buyRatio;
	private static BigDecimal lowQuoteQty = BigDecimal.valueOf(5);
	private static TradeStat tradeStat = TradeStat.getInstance();

	static {
		InputStream resourceAsStream = MxcClient.class.getResourceAsStream("/application-grid.yaml");
		Properties properties = new Properties();
		try {
			properties.load(resourceAsStream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		symbolA = properties.getProperty("symbolA");
		symbolB = properties.getProperty("symbolB");
		swapQtyOfA = new BigDecimal(properties.getProperty("swapQtyOfA"));
		gridRate = new BigDecimal(properties.getProperty("gridRate")).multiply(new BigDecimal("0.01"));
		tradeDepth = Integer.parseInt(properties.getProperty("tradeDepth", "1"));
	}

	public static void init() throws Exception {
		List<String> supSymbols = MxcClient.getSupSymbols();
		if (!supSymbols.contains(symbolA) || !supSymbols.contains(symbolB)) {
			String msg = "该交易对不支持api交易,code=10007,自检地址\\https://www.mexc.com/zh-CN/user/openapi";
			throw new RuntimeException(msg);
		}
		// 合理价格及相关汇率
		BigDecimal priceA = MxcClient.getPrice(symbolA);
		BigDecimal priceB = MxcClient.getPrice(symbolB);
		BigDecimal ratio = priceA.divide(priceB, 8, RoundingMode.DOWN);
		sellRatio = ratio.add(ratio.multiply(gridRate));
		buyRatio = ratio.subtract(ratio.multiply(gridRate));
		eqQtyOfB = priceA.multiply(swapQtyOfA).divide(priceB, 8, RoundingMode.DOWN);
		log.info("参数列表:");
		log.info("{}-{},{}-{}", symbolA, priceA, symbolB, priceB);
		log.info("{}/{}现汇率:{},下一卖出汇率:{}，下一买入汇率:{}", symbolA, symbolB, ratio, sellRatio, buyRatio);
		log.info("每次交易A卖出数量:{},B卖出数量:{}\n", swapQtyOfA, eqQtyOfB);
	}


	/**
	 * 低市值币往往存在流动性问题、所以在实际运行中、应该根据最新买/卖单价格判断是否进行套利
	 */
	public static void loop() {
		while (true) {
			try {
				PriceBook priceBookA = MxcClient.getPriceBook(symbolA);
				PriceBook priceBookB = MxcClient.getPriceBook(symbolB);
				// 检查是否满足汇率卖出条件、你可能需要卖出A、然后买入B、
				// 所以你的汇率应该用A的最优买单价格和B的最优卖单价格计算。
				BigDecimal curSellRatio = priceBookA.getBidPrice().divide(priceBookB.getAskPrice(), 8, RoundingMode.DOWN);
				log.info("  [{} < {} < {}]", buyRatio, curSellRatio, sellRatio);
				if (curSellRatio.compareTo(sellRatio) > 0) {
					log.info("	当前汇率高于卖出汇率、卖A买B");
					// 卖A买B、
					exeSellA(priceBookA, priceBookB);
					// 更新买/卖汇率
					sellRatio = curSellRatio.add(curSellRatio.multiply(gridRate));
					buyRatio = curSellRatio.subtract(curSellRatio.multiply(gridRate));
					log.info("	完成卖A买B:最新买入汇率:{},卖出汇率:{}", buyRatio, sellRatio);
					continue;
				}

				// 检查是否满足汇率买入条件、你可能需要卖出B、然后买入A、
				// 所以你的汇率应该用B的最优买单价格和A的最优卖单价格计算。
				BigDecimal curBuyRatio = priceBookA.getAskPrice().divide(priceBookB.getBidPrice(), 8, RoundingMode.DOWN);
				log.info("	[{} < {} < {}]", buyRatio, curBuyRatio, sellRatio);
				if (curBuyRatio.compareTo(buyRatio) < 0) {
					log.info("	当前汇率低于买入汇率、卖B买A");
					// 卖B买A
					exeSellB(priceBookA, priceBookB);
					// 更新买/卖汇率
					sellRatio = curBuyRatio.add(curBuyRatio.multiply(gridRate));
					buyRatio = curBuyRatio.subtract(curBuyRatio.multiply(gridRate));
					log.info("	完成卖B买A:最新买入汇率:{},卖出汇率:{}", buyRatio, sellRatio);
				}
				TimeUnit.SECONDS.sleep(30);
			} catch (Exception ex) {
				log.error(ex.getMessage(), ex);
				if (StringUtils.containsIgnoreCase(ex.getMessage(), "time") || ex instanceof SocketException) {
					try {
						TimeUnit.MINUTES.sleep(3);
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
					continue;
				}
				throw new RuntimeException(ex);
			}
		}
	}

	/**
	 * 卖A买B
	 * 不断的吃掉A的最优买单、直至达到最大可卖出数量
	 * 然后用卖出A获取的盈利、不断吃B的最优卖单、
	 * 这不是市价操作、你可以根据流动性深度来设置tradeDepth的值。
	 * 卖B买A则相反
	 */
	private static void exeSellA(PriceBook priceBookA, PriceBook priceBookB) throws Exception {
		int x = 0;
		BigDecimal sellQtyA = swapQtyOfA;
		// 卖A获取的总盈利/USD
		BigDecimal sellCumQuoteQtyA = BigDecimal.ZERO;
		while (x++ < tradeDepth && sellQtyA.multiply(priceBookA.getBidPrice()).compareTo(lowQuoteQty) > 0) {
			// 获取最优一格挂单
			BigDecimal bidPrice = priceBookA.getBidPrice();
			BigDecimal bidQty = priceBookA.getBidQty();
			// 最优买价的可卖数量
			BigDecimal exeQty = sellQtyA.compareTo(bidQty) > 0 ? bidQty : sellQtyA;
			// 检查这层挂单的总金额是否大于5U、抹茶的最低开单限制
			if (bidPrice.multiply(exeQty).compareTo(lowQuoteQty) < 0) {
				// 最优流动性过低时、手工填满5U流动性、因IOC机制、只会成交实际拥有的流动性。
				exeQty = lowQuoteQty.divide(bidPrice, 8, RoundingMode.UP);
			}
			OrderParam param = OrderParam.builder().symbol(symbolA).side(SideTypeEnum.SELL).type(OrderTypeEnum.IMMEDIATE_OR_CANCEL).quantity(exeQty.toString()).price(bidPrice.toString()).build();
			Order order = MxcClient.getOrder(symbolA, MxcClient.createOrder(param));
			// 累计总盈利
			sellCumQuoteQtyA = sellCumQuoteQtyA.add(new BigDecimal(order.getCummulativeQuoteQty()));
			// 剩余待卖数量
			sellQtyA = sellQtyA.subtract(new BigDecimal(order.getExecutedQty()));
			// 更新最优挂单
			priceBookA = MxcClient.getPriceBook(symbolA);
			log.info("	本次A待卖数量:{},卖出数量:{},卖出价格:{},卖出金额:{},卖出总盈利:{},剩余待卖:{}", swapQtyOfA, order.getExecutedQty(), order.getPrice(), order.getCummulativeQuoteQty(), sellCumQuoteQtyA, sellQtyA);
		}
		if (BigDecimal.ZERO.equals(sellCumQuoteQtyA)) {
			// 小于10U、无法进入订单
			log.warn("	卖A总结: 预设订单金额小于5U、无法进入订单");
			return;
		}
		BigDecimal sellPriceA = sellCumQuoteQtyA.divide(swapQtyOfA.subtract(sellQtyA), 8, 1);
		log.warn("	卖A总结: 以{}的均价卖出{}个、总金额为:{}", sellPriceA.toPlainString(), swapQtyOfA.subtract(sellQtyA), sellCumQuoteQtyA);

		// 统计
		tradeStat.sellA(swapQtyOfA.subtract(sellQtyA), sellCumQuoteQtyA);

		// 买B的资金来源于卖A的盈利/USD
		BigDecimal buyOrigQuoteQtyB = sellCumQuoteQtyA;
		x = 0;
		// B的累计买入量
		BigDecimal buyCumQtyB = BigDecimal.ZERO;
		while (x++ < tradeDepth && buyOrigQuoteQtyB.compareTo(lowQuoteQty) > 0) {
			// 获取最优一格卖单
			BigDecimal askPrice = priceBookB.getAskPrice();
			BigDecimal askQty = priceBookB.getAskQty();
			// 最优卖价的最大可买数量
			BigDecimal maxExeQty = buyOrigQuoteQtyB.divide(askPrice, 8, RoundingMode.DOWN);
			// 预进入订单数量
			BigDecimal orderQty = maxExeQty.compareTo(askQty) > 0 ? askQty : maxExeQty;

			// 检查这层挂单的总金额是否大于5U、抹茶的最低开单限制
			if (askPrice.multiply(orderQty).compareTo(lowQuoteQty) < 0) {
				// 最优流动性过低时、手工填满5U流动性、因IOC机制、只会成交实际拥有的流动性。
				orderQty = lowQuoteQty.divide(askPrice, 8, RoundingMode.UP);
			}

			OrderParam param = OrderParam.builder().symbol(symbolB).side(SideTypeEnum.BUY).type(OrderTypeEnum.IMMEDIATE_OR_CANCEL).quantity(orderQty.toString()).price(askPrice.toString()).build();
			Order order = MxcClient.getOrder(symbolB, MxcClient.createOrder(param));
			// 订单成交金额
			String quoteQty = order.getCummulativeQuoteQty();
			// 更新可用余额
			buyOrigQuoteQtyB = buyOrigQuoteQtyB.subtract(new BigDecimal(quoteQty));
			buyCumQtyB = buyCumQtyB.add(new BigDecimal(order.getExecutedQty()));
			// 更新最优挂单
			priceBookB = MxcClient.getPriceBook(symbolB);
			log.info("	本次B买入花费:{},买入数量:{},买入价格:{},剩余可用余额:{}", order.getCummulativeQuoteQty(), order.getExecutedQty(), order.getPrice(), buyOrigQuoteQtyB);
		}
		if (BigDecimal.ZERO.equals(buyCumQtyB)) {
			log.warn("	买B总结: 本次卖A总金额小于5U、无法继续买B");
			tradeStat.buyB(BigDecimal.ZERO, BigDecimal.ZERO, buyOrigQuoteQtyB);
			return;
		}
		log.warn("	买B总结: 以{}的均价买入{}个、总金额为:{},留存USD为:{}", (sellCumQuoteQtyA.subtract(buyOrigQuoteQtyB)).divide(buyCumQtyB, 8, 1), buyCumQtyB, sellCumQuoteQtyA.subtract(buyOrigQuoteQtyB),
				 buyOrigQuoteQtyB);

		tradeStat.buyB(buyCumQtyB, sellCumQuoteQtyA.subtract(buyOrigQuoteQtyB), buyOrigQuoteQtyB);
	}

	private static void exeSellB(PriceBook priceBookA, PriceBook priceBookB) throws Exception {
		int x = 0;
		BigDecimal sellQtyB = eqQtyOfB;
		// 卖B获取的总盈利/USD
		BigDecimal sellCumQuoteQtyB = BigDecimal.ZERO;
		while (x++ < tradeDepth && sellQtyB.multiply(priceBookB.getBidPrice()).compareTo(lowQuoteQty) > 0) {
			// 获取最优一格挂单
			BigDecimal bidPrice = priceBookB.getBidPrice();
			BigDecimal bidQty = priceBookB.getBidQty();
			// 最优买价的可卖数量
			BigDecimal exeQty = sellQtyB.compareTo(bidQty) > 0 ? bidQty : sellQtyB;
			// 检查这层挂单的总金额是否大于5U、抹茶的最低开单限制
			if (bidPrice.multiply(exeQty).compareTo(lowQuoteQty) < 0) {
				// 最优流动性过低时、手工填满5U流动性、因IOC机制、只会成交实际拥有的流动性。
				exeQty = lowQuoteQty.divide(bidPrice, 8, RoundingMode.UP);
			}
			OrderParam param = OrderParam.builder().symbol(symbolB).side(SideTypeEnum.SELL).type("IMMEDIATE_OR_CANCEL").quantity(exeQty.toString()).price(bidPrice.toString()).build();
			String orderId = MxcClient.createOrder(param);
			Order order = MxcClient.getOrder(symbolB, orderId);
			// 累计总盈利
			sellCumQuoteQtyB = sellCumQuoteQtyB.add(new BigDecimal(order.getCummulativeQuoteQty()));
			// 剩余待卖数量
			sellQtyB = sellQtyB.subtract(new BigDecimal(order.getExecutedQty()));
			// 更新最优挂单
			priceBookB = MxcClient.getPriceBook(symbolB);
			log.info("  本次B待卖数量:{},卖出数量:{},卖出价格:{},卖出金额:{},卖出总盈利:{},剩余待卖:{}", eqQtyOfB, order.getExecutedQty(), order.getPrice(), order.getCummulativeQuoteQty(), sellCumQuoteQtyB, sellQtyB);
		}
		if (BigDecimal.ZERO.equals(sellCumQuoteQtyB)) {
			// 小于10U、无法进入订单
			log.warn("	卖B总结: 预设订单金额小于5U、无法进入订单");
			return;
		}
		BigDecimal sellBPrice = sellCumQuoteQtyB.divide(eqQtyOfB.subtract(sellQtyB), 8, 1);
		log.warn("	卖B总结: 以{}的均价卖出{}个、总金额为:{}", sellBPrice.toPlainString(), eqQtyOfB.subtract(sellQtyB), sellCumQuoteQtyB);
		// 统计
		tradeStat.sellB(eqQtyOfB.subtract(sellQtyB), sellCumQuoteQtyB);

		// 买B的资金来源于卖A的盈利/USD
		BigDecimal buyOrigQuoteQtyA = sellCumQuoteQtyB;
		x = 0;
		// A的累计买入量
		BigDecimal buyCumQtyA = BigDecimal.ZERO;
		while (x++ < tradeDepth && buyOrigQuoteQtyA.compareTo(lowQuoteQty) > 0) {
			// 获取最优一格卖单
			BigDecimal askPrice = priceBookA.getAskPrice();
			BigDecimal askQty = priceBookA.getAskQty();
			// 最优卖价的最大可买数量
			BigDecimal maxExeQty = buyOrigQuoteQtyA.divide(askPrice, 8, RoundingMode.DOWN);
			BigDecimal orderQty = maxExeQty.compareTo(askQty) > 0 ? askQty : maxExeQty;
			// 检查这层挂单的总金额是否大于5U、抹茶的最低开单限制
			if (askPrice.multiply(orderQty).compareTo(lowQuoteQty) < 0) {
				// 最优流动性过低时、手工填满5U流动性、因IOC机制、只会成交实际拥有的流动性。
				orderQty = lowQuoteQty.divide(askPrice, 8, RoundingMode.UP);
			}
			OrderParam param = OrderParam.builder().symbol(symbolA).side(SideTypeEnum.BUY).type("IMMEDIATE_OR_CANCEL").quantity(orderQty.toString()).price(askPrice.toString()).build();
			Order order = MxcClient.getOrder(symbolA, MxcClient.createOrder(param));
			// 订单成交金额
			String quoteQty = order.getCummulativeQuoteQty();
			// 更新可用余额
			buyOrigQuoteQtyA = buyOrigQuoteQtyA.subtract(new BigDecimal(quoteQty));
			buyCumQtyA = buyCumQtyA.add(new BigDecimal(order.getExecutedQty()));
			// 更新最优挂单
			priceBookA = MxcClient.getPriceBook(symbolA);
			log.info("  本次A买入花费:{},买入数量:{},买入价格:{},剩余可用余额:{}", order.getCummulativeQuoteQty(), order.getExecutedQty(), order.getPrice(), buyOrigQuoteQtyA);
		}
		if (BigDecimal.ZERO.equals(buyCumQtyA)) {
			log.info("	买A总结: 本次卖B总金额小于10U、无法继续买A");
			tradeStat.buyA(BigDecimal.ZERO, BigDecimal.ZERO, buyOrigQuoteQtyA);
			return;
		}
		log.warn("	买A总结: 以{}的均价买入{}个、总金额为:{},留存USD为:{}", (sellCumQuoteQtyB.subtract(buyOrigQuoteQtyA)).divide(buyCumQtyA, 8, 1), buyCumQtyA, sellCumQuoteQtyB.subtract(buyOrigQuoteQtyA),
				 buyOrigQuoteQtyA);
		// 统计
		tradeStat.buyA(buyCumQtyA, sellCumQuoteQtyB.subtract(buyOrigQuoteQtyA), buyOrigQuoteQtyA);
	}


	public static void main(String[] args) throws Exception {
		init();
		new ApiService().start();
		loop();
	}
}
