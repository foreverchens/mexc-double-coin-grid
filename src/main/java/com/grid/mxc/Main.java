package com.grid.mxc;


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
		// 合理价格及相关汇率
		BigDecimal priceA = MxcClient.getPrice(symbolA);
		BigDecimal priceB = MxcClient.getPrice(symbolB);
		BigDecimal ratio = priceA.divide(priceB, 8, RoundingMode.DOWN);
		sellRatio = ratio.add(ratio.multiply(gridRate));
		buyRatio = ratio.subtract(ratio.multiply(gridRate));
		eqQtyOfB = priceA.multiply(swapQtyOfA).divide(priceB, 8, RoundingMode.DOWN);
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
				BigDecimal curSellRatio = priceBookA.getBidPrice().divide(priceBookB.getAskPrice(), 8,
						RoundingMode.DOWN);
				log.info("[{} < {} < {}]", buyRatio, curSellRatio, sellRatio);
				if (curSellRatio.compareTo(sellRatio) > 0) {
					// 卖A买B、
					exeSellA(priceBookA, priceBookB);
					// 更新买/卖汇率
					sellRatio = curSellRatio.add(curSellRatio.multiply(gridRate));
					buyRatio = curSellRatio.subtract(curSellRatio.multiply(gridRate));
					return;
				}

				// 检查是否满足汇率买入条件、你可能需要卖出B、然后买入A、
				// 所以你的汇率应该用B的最优买单价格和A的最优卖单价格计算。
				BigDecimal curBuyRatio = priceBookA.getAskPrice().divide(priceBookB.getBidPrice(), 8,
						RoundingMode.DOWN);
				log.info("[{} < {} < {}]", buyRatio, curBuyRatio, sellRatio);
				if (curBuyRatio.compareTo(buyRatio) < 0) {
					// 卖B买A
					exeSellB(priceBookA, priceBookB);
					// 更新买/卖汇率
					sellRatio = curBuyRatio.add(curBuyRatio.multiply(gridRate));
					buyRatio = curBuyRatio.subtract(curBuyRatio.multiply(gridRate));
				}
				TimeUnit.MINUTES.sleep(1);
			} catch (Exception ex) {
				log.error(ex.getMessage(), ex);
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
			sellACumQuoteQty = sellACumQuoteQty.add(new BigDecimal(order.getCummulativeQuoteQty()));
			// 剩余待卖数量
			sellAQty = sellAQty.subtract(new BigDecimal(order.getExecutedQty()));
			// 更新最优挂单
			priceBookA = MxcClient.getPriceBook(symbolA);
		}
		// 买B的资金来源于卖A的盈利/USD
		BigDecimal buyBOrigQuoteQty = sellACumQuoteQty;
		x = 0;
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
			// 更新最优挂单
			priceBookB = MxcClient.getPriceBook(symbolB);
		}
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
			OrderParam param =
					OrderParam.builder().symbol(symbolA).side(SideTypeEnum.SELL).type("IMMEDIATE_OR_CANCEL").quantity(exeQty.toString()).price(bidPrice.toString()).build();
			String orderId = MxcClient.createOrder(param);
			Order order = MxcClient.getOrder(symbolB, orderId);
			// 累计总盈利
			sellBCumQuoteQty = sellBCumQuoteQty.add(new BigDecimal(order.getCummulativeQuoteQty()));
			// 剩余待卖数量
			sellBQty = sellBQty.subtract(new BigDecimal(order.getExecutedQty()));
			// 更新最优挂单
			priceBookB = MxcClient.getPriceBook(symbolB);
		}
		// 买B的资金来源于卖A的盈利/USD
		BigDecimal buyAOrigQuoteQty = sellBCumQuoteQty;
		x = 0;
		while (x++ < tradeDepth && buyAOrigQuoteQty.compareTo(BigDecimal.TEN) > 0) {
			// 获取最优一格卖单
			BigDecimal askPrice = priceBookA.getAskPrice();
			BigDecimal askQty = priceBookA.getAskQty();
			// 最优卖价的最大可买数量
			BigDecimal maxExeQty = buyAOrigQuoteQty.divide(askPrice, 8, RoundingMode.DOWN);
			BigDecimal orderQty = maxExeQty.compareTo(askQty) > 0 ? askQty : maxExeQty;
			OrderParam param =
					OrderParam.builder().symbol(symbolA).side(SideTypeEnum.BUY).type("IMMEDIATE_OR_CANCEL").quantity(orderQty.toString()).price(askPrice.toString()).build();

			String orderId = MxcClient.createOrder(param);
			Order order = MxcClient.getOrder(symbolA, orderId);
			// 订单成交金额
			String quoteQty = order.getCummulativeQuoteQty();
			// 更新可用余额
			buyAOrigQuoteQty = buyAOrigQuoteQty.subtract(new BigDecimal(quoteQty));
			// 更新最优挂单
			priceBookA = MxcClient.getPriceBook(symbolA);
		}
	}


	public static void main(String[] args) throws Exception {
		init();
		loop();
	}
}
