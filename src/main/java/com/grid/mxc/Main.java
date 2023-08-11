package com.grid.mxc;


import com.grid.mxc.common.MxcClient;
import com.grid.mxc.common.OrderTypeEnum;
import com.grid.mxc.common.SideTypeEnum;
import com.grid.mxc.entity.Order;
import com.grid.mxc.entity.OrderParam;
import com.grid.mxc.entity.PriceBook;


import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author yyy
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


	static {
		InputStream resourceAsStream = MxcClient.class.getResourceAsStream("/application-grid" +
				".yaml");
		Properties properties = new Properties();
		try {
			properties.load(resourceAsStream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		symbolA = properties.getProperty("symbolA");
		symbolB = properties.getProperty("symbolB");
		swapQtyOfA = new BigDecimal(properties.getProperty("swapQtyOfA"));
		gridRate =
				new BigDecimal(properties.getProperty("gridRate")).multiply(new BigDecimal("0" +
						".01"));
		tradeDepth = Integer.parseInt(properties.getProperty("tradeDepth", "1"));
	}

	public static void init() throws Exception {
		List<String> supSymbols = MxcClient.getSupSymbols();
		if (!supSymbols.contains(symbolA) || !supSymbols.contains(symbolB)) {
			throw new RuntimeException("该交易对不支持api交易,code=10007,自检地址\\https://www.mexc" + ".com" + "/zh" + "-CN/user/openapi");
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
				BigDecimal curSellRatio = priceBookA.getBidPrice().divide(priceBookB.getAskPrice()
						, 8, RoundingMode.DOWN);
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
				BigDecimal curBuyRatio = priceBookA.getAskPrice().divide(priceBookB.getBidPrice(),
						8, RoundingMode.DOWN);
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
				// TODO: 2023/8/6  区分故障和偶发性异常
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
		BigDecimal sellAQty = swapQtyOfA;
		// 卖A获取的总盈利/USD
		BigDecimal sellACumQuoteQty = BigDecimal.ZERO;
		while (x++ < tradeDepth && sellAQty.multiply(priceBookA.getBidPrice()).compareTo(BigDecimal.TEN) > 0) {
			// 获取最优一格挂单
			BigDecimal bidPrice = priceBookA.getBidPrice();
			BigDecimal bidQty = priceBookA.getBidQty();
			// 最优买价的可卖数量
			BigDecimal exeQty = sellAQty.compareTo(bidQty) > 0 ? bidQty : sellAQty;
			OrderParam param =
					OrderParam.builder().symbol(symbolA).side(SideTypeEnum.SELL).type(OrderTypeEnum.IMMEDIATE_OR_CANCEL).quantity(exeQty.toString()).price(bidPrice.toString()).build();
			String orderId = MxcClient.createOrder(param);
			Order order = MxcClient.getOrder(symbolA, orderId);
			// 累计总盈利
			sellACumQuoteQty =
					sellACumQuoteQty.add(new BigDecimal(order.getCummulativeQuoteQty()));
			// 剩余待卖数量
			sellAQty = sellAQty.subtract(new BigDecimal(order.getExecutedQty()));
			// 更新最优挂单
			priceBookA = MxcClient.getPriceBook(symbolA);
			log.info("	本次A待卖数量:{},卖出数量:{},卖出价格:{},卖出金额:{},卖出总盈利:{},剩余待卖:{}", swapQtyOfA,
					order.getExecutedQty(), order.getPrice(), order.getCummulativeQuoteQty(),
					sellACumQuoteQty, sellAQty);
		}
		if (BigDecimal.ZERO.equals(sellACumQuoteQty)) {
			// 小于10U、无法进入订单
			log.warn("	卖A总结: 预设订单金额小于10U、无法进入订单");
			return;
		}
		BigDecimal sellAPrice = sellACumQuoteQty.divide(swapQtyOfA.subtract(sellAQty), 1, 1);
		log.warn("	卖A总结: 以{}的均价卖出{}个、总金额为:{}", sellAPrice.toPlainString(),
				swapQtyOfA.subtract(sellAQty), sellACumQuoteQty);
		// 买B的资金来源于卖A的盈利/USD
		BigDecimal buyBOrigQuoteQty = sellACumQuoteQty;
		x = 0;
		// B的累计买入量
		BigDecimal buyBCumQty = BigDecimal.ZERO;
		while (x++ < tradeDepth && buyBOrigQuoteQty.compareTo(BigDecimal.TEN) > 0) {
			// 获取最优一格卖单
			BigDecimal askPrice = priceBookB.getAskPrice();
			BigDecimal askQty = priceBookB.getAskQty();
			// 最优卖价的最大可买数量
			BigDecimal maxExeQty = buyBOrigQuoteQty.divide(askPrice, 8, RoundingMode.DOWN);
			BigDecimal orderQty = maxExeQty.compareTo(askQty) > 0 ? askQty : maxExeQty;
			OrderParam param =
					OrderParam.builder().symbol(symbolB).side(SideTypeEnum.BUY).type(OrderTypeEnum.IMMEDIATE_OR_CANCEL).quantity(orderQty.toString()).price(askPrice.toString()).build();
			String orderId = MxcClient.createOrder(param);
			Order order = MxcClient.getOrder(symbolB, orderId);
			// 订单成交金额
			String quoteQty = order.getCummulativeQuoteQty();
			// 更新可用余额
			buyBOrigQuoteQty = buyBOrigQuoteQty.subtract(new BigDecimal(quoteQty));
			buyBCumQty = buyBCumQty.add(new BigDecimal(order.getExecutedQty()));
			// 更新最优挂单
			priceBookB = MxcClient.getPriceBook(symbolB);
			log.info("	本次B买入花费:{},买入数量:{},买入价格:{},剩余可用余额:{}", order.getCummulativeQuoteQty(),
					order.getExecutedQty(), order.getPrice(), buyBOrigQuoteQty);
		}
		if (BigDecimal.ZERO.equals(buyBCumQty)) {
			log.warn("	买B总结: 本次卖A总金额小于10U、无法继续买B");
			return;
		}
		log.warn("	买B总结: 以{}的均价买入{}个、总金额为:{},留存USD为:{}",
				(sellACumQuoteQty.subtract(buyBOrigQuoteQty)).divide(buyBCumQty, 8, 1), buyBCumQty
				, sellACumQuoteQty.subtract(buyBOrigQuoteQty), buyBOrigQuoteQty);
	}

	private static void exeSellB(PriceBook priceBookA, PriceBook priceBookB) throws Exception {
		int x = 0;
		BigDecimal sellBQty = eqQtyOfB;
		// 卖B获取的总盈利/USD
		BigDecimal sellBCumQuoteQty = BigDecimal.ZERO;
		while (x++ < tradeDepth && sellBQty.multiply(priceBookB.getBidPrice()).compareTo(BigDecimal.TEN) > 0) {
			// 获取最优一格挂单
			BigDecimal bidPrice = priceBookB.getBidPrice();
			BigDecimal bidQty = priceBookB.getBidQty();
			// 最优买价的可卖数量
			BigDecimal exeQty = sellBQty.compareTo(bidQty) > 0 ? bidQty : sellBQty;
			OrderParam param = OrderParam.builder().symbol(symbolB).side(SideTypeEnum.SELL).type(
					"IMMEDIATE_OR_CANCEL").quantity(exeQty.toString()).price(bidPrice.toString()).build();
			String orderId = MxcClient.createOrder(param);
			Order order = MxcClient.getOrder(symbolB, orderId);
			// 累计总盈利
			sellBCumQuoteQty =
					sellBCumQuoteQty.add(new BigDecimal(order.getCummulativeQuoteQty()));
			// 剩余待卖数量
			sellBQty = sellBQty.subtract(new BigDecimal(order.getExecutedQty()));
			// 更新最优挂单
			priceBookB = MxcClient.getPriceBook(symbolB);
			log.info("  本次B待卖数量:{},卖出数量:{},卖出价格:{},卖出金额:{},卖出总盈利:{},剩余待卖:{}", eqQtyOfB,
					order.getExecutedQty(), order.getPrice(), order.getCummulativeQuoteQty(),
					sellBCumQuoteQty, sellBQty);
		}
		if (BigDecimal.ZERO.equals(sellBCumQuoteQty)) {
			// 小于10U、无法进入订单
			log.warn("	卖B总结: 预设订单金额小于10U、无法进入订单");
			return;
		}
		BigDecimal sellBPrice = sellBCumQuoteQty.divide(eqQtyOfB.subtract(sellBQty), 1, 1);
		log.warn("	卖B总结: 以{}的均价卖出{}个、总金额为:{}", sellBPrice.toPlainString(),
				eqQtyOfB.subtract(sellBQty), sellBCumQuoteQty);
		// 买B的资金来源于卖A的盈利/USD
		BigDecimal buyAOrigQuoteQty = sellBCumQuoteQty;
		x = 0;
		// A的累计买入量
		BigDecimal buyACumQty = BigDecimal.ZERO;
		while (x++ < tradeDepth && buyAOrigQuoteQty.compareTo(BigDecimal.TEN) > 0) {
			// 获取最优一格卖单
			BigDecimal askPrice = priceBookA.getAskPrice();
			BigDecimal askQty = priceBookA.getAskQty();
			// 最优卖价的最大可买数量
			BigDecimal maxExeQty = buyAOrigQuoteQty.divide(askPrice, 8, RoundingMode.DOWN);
			BigDecimal orderQty = maxExeQty.compareTo(askQty) > 0 ? askQty : maxExeQty;
			OrderParam param = OrderParam.builder().symbol(symbolA).side(SideTypeEnum.BUY).type(
					"IMMEDIATE_OR_CANCEL").quantity(orderQty.toString()).price(askPrice.toString()).build();
			String orderId = MxcClient.createOrder(param);
			Order order = MxcClient.getOrder(symbolA, orderId);
			// 订单成交金额
			String quoteQty = order.getCummulativeQuoteQty();
			// 更新可用余额
			buyAOrigQuoteQty = buyAOrigQuoteQty.subtract(new BigDecimal(quoteQty));

			buyACumQty = buyACumQty.add(new BigDecimal(order.getExecutedQty()));
			// 更新最优挂单
			priceBookA = MxcClient.getPriceBook(symbolA);
			log.info("  本次A买入花费:{},买入数量:{},买入价格:{},剩余可用余额:{}", order.getCummulativeQuoteQty(),
					order.getExecutedQty(), order.getPrice(), buyAOrigQuoteQty);
		}
		if (BigDecimal.ZERO.equals(buyACumQty)) {
			log.info("	买A总结: 本次卖B总金额小于10U、无法继续买A");
			return;
		}
		log.warn("	买A总结: 以{}的均价买入{}个、总金额为:{},留存USD为:{}",
				(sellBCumQuoteQty.subtract(buyAOrigQuoteQty)).divide(buyACumQty, 8, 1), buyACumQty
				, sellBCumQuoteQty.subtract(buyAOrigQuoteQty), buyAOrigQuoteQty);
	}


	public static void main(String[] args) throws Exception {
		init();
		loop();
	}
}
