package com.grid.mxc;


import com.grid.mxc.common.MxcClient;
import com.grid.mxc.common.OrderTypeEnum;
import com.grid.mxc.common.SideTypeEnum;
import com.grid.mxc.entity.Order;
import com.grid.mxc.entity.OrderParam;
import com.grid.mxc.entity.PriceBook;

import cn.hutool.http.HttpException;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
	private static String symbolA;
	private static String symbolB;
	private static Double slippage;
	private static BigDecimal swapQtyOfA;
	private static BigDecimal eqQtyOfB;
	private static BigDecimal gridRate;
	private static BigDecimal sellRatio;
	private static BigDecimal buyRatio;
	private static BigDecimal lowQuoteQty = BigDecimal.valueOf(6);
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
		slippage = Double.valueOf(properties.getProperty("slippage"));
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
	@SneakyThrows
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
			} catch (HttpException ex) {
				throw ex;
			}
		}
	}

	/**
	 * 卖A买B
	 * 不断的吃掉A的最优买单、直至达到最大可卖出数量
	 * 然后用卖出A获取的盈利、不断吃B的最优卖单、
	 * 这不是市价操作、你可以根据流动性深度来设置slippage的值。
	 * 卖B买A则相反
	 */
	private static void exeSellA(PriceBook priceBookA, PriceBook priceBookB) throws Exception {
		BigDecimal sellQtyA = swapQtyOfA;
		// 卖A获取的总盈利/USD
		BigDecimal sellCumQuoteQtyA = BigDecimal.ZERO;
		// 卖A、以当前最优买价计算最低可接受买价
		BigDecimal lowBuyPriceA = priceBookA.getBidPrice().multiply(BigDecimal.valueOf(1 - slippage));

		while (priceBookA.getBidPrice().compareTo(lowBuyPriceA) > 0 && sellQtyA.multiply(priceBookA.getBidPrice()).compareTo(lowQuoteQty) > 0) {
			// 获取最优一格挂单
			BigDecimal bidPrice = priceBookA.getBidPrice();
			BigDecimal bidQty = priceBookA.getBidQty();
			// 检查这层挂单的总金额是否大于5U、抹茶的最低开单限制
			if (bidPrice.multiply(bidQty).compareTo(lowQuoteQty) < 0) {
				// 最优流动性过低时、手工填满5U流动性、因IOC机制、只会成交实际拥有的流动性。
				bidQty = lowQuoteQty.divide(bidPrice, 8, RoundingMode.UP);
			}
			// 最优买价的可卖数量
			BigDecimal exeQty = sellQtyA.compareTo(bidQty) > 0 ? bidQty : sellQtyA;
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
		BigDecimal sellPriceA = sellCumQuoteQtyA.divide(swapQtyOfA.subtract(sellQtyA), 8, RoundingMode.DOWN);
		log.warn("	卖A总结: 以{}的均价卖出{}个、总金额为:{}", sellPriceA.toPlainString(), swapQtyOfA.subtract(sellQtyA), sellCumQuoteQtyA);

		// 统计
		tradeStat.sellA(swapQtyOfA.subtract(sellQtyA), sellCumQuoteQtyA);

		// 买B的资金来源于卖A的盈利/USD
		BigDecimal buyOrigQuoteQtyB = sellCumQuoteQtyA;
		// B的累计买入量
		BigDecimal buyCumQtyB = BigDecimal.ZERO;
		// 买B、以当前最优卖价计算最高可接受卖价
		BigDecimal highSellPriceB = priceBookB.getAskPrice().multiply(BigDecimal.valueOf(1 + slippage));
		while (priceBookB.getAskPrice().compareTo(highSellPriceB) < 0 && buyOrigQuoteQtyB.compareTo(lowQuoteQty) > 0) {
			// 获取最优一格卖单
			BigDecimal askPrice = priceBookB.getAskPrice();
			BigDecimal askQty = priceBookB.getAskQty();

			// 检查这层挂单的总金额是否大于5U、抹茶的最低开单限制
			if (askPrice.multiply(askQty).compareTo(lowQuoteQty) < 0) {
				// 最优流动性过低时、手工填满5U流动性、因IOC机制、只会成交实际拥有的流动性。
				askQty = lowQuoteQty.divide(askPrice, 8, RoundingMode.UP);
			}
			// 最优卖价的最大可买数量
			BigDecimal maxExeQty = buyOrigQuoteQtyB.divide(askPrice, 8, RoundingMode.DOWN);
			// 预进入订单数量
			BigDecimal orderQty = maxExeQty.compareTo(askQty) > 0 ? askQty : maxExeQty;
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
		log.warn("	买B总结: 以{}的均价买入{}个、总金额为:{},留存USD为:{}", (sellCumQuoteQtyA.subtract(buyOrigQuoteQtyB)).divide(buyCumQtyB, 8, RoundingMode.DOWN), buyCumQtyB,
				 sellCumQuoteQtyA.subtract(buyOrigQuoteQtyB), buyOrigQuoteQtyB);

		tradeStat.buyB(buyCumQtyB, sellCumQuoteQtyA.subtract(buyOrigQuoteQtyB), buyOrigQuoteQtyB);
	}

	private static void exeSellB(PriceBook priceBookA, PriceBook priceBookB) throws Exception {
		BigDecimal sellQtyB = eqQtyOfB;
		// 卖B获取的总盈利/USD
		BigDecimal sellCumQuoteQtyB = BigDecimal.ZERO;
		// 卖B、以当前最优买价计算最低可接受买价
		BigDecimal lowBuyPriceB = priceBookB.getBidPrice().multiply(BigDecimal.valueOf(1 - slippage));
		while (priceBookB.getBidPrice().compareTo(lowBuyPriceB) > 0 && sellQtyB.multiply(priceBookB.getBidPrice()).compareTo(lowQuoteQty) > 0) {
			// 获取最优一格挂单
			BigDecimal bidPrice = priceBookB.getBidPrice();
			BigDecimal bidQty = priceBookB.getBidQty();
			// 检查这层挂单的总金额是否大于5U、抹茶的最低开单限制
			if (bidPrice.multiply(bidQty).compareTo(lowQuoteQty) < 0) {
				// 最优流动性过低时、手工填满5U流动性、因IOC机制、只会成交实际拥有的流动性。
				bidQty = lowQuoteQty.divide(bidPrice, 8, RoundingMode.UP);
			}
			// 最优买价的可卖数量
			BigDecimal exeQty = sellQtyB.compareTo(bidQty) > 0 ? bidQty : sellQtyB;

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
		BigDecimal sellPriceB = sellCumQuoteQtyB.divide(eqQtyOfB.subtract(sellQtyB), 8, RoundingMode.DOWN);
		log.warn("	卖B总结: 以{}的均价卖出{}个、总金额为:{}", sellPriceB.toPlainString(), eqQtyOfB.subtract(sellQtyB), sellCumQuoteQtyB);
		// 统计
		tradeStat.sellB(eqQtyOfB.subtract(sellQtyB), sellCumQuoteQtyB);

		// 买B的资金来源于卖A的盈利/USD
		BigDecimal buyOrigQuoteQtyA = sellCumQuoteQtyB;
		// A的累计买入量
		BigDecimal buyCumQtyA = BigDecimal.ZERO;
		// 买A、以当前最优卖价计算最高可接受卖价
		BigDecimal highSellPriceA = priceBookA.getAskPrice().multiply(BigDecimal.valueOf(1 + slippage));
		while (priceBookA.getAskPrice().compareTo(highSellPriceA) < 0 && buyOrigQuoteQtyA.compareTo(lowQuoteQty) > 0) {
			// 获取最优一格卖单
			BigDecimal askPrice = priceBookA.getAskPrice();
			BigDecimal askQty = priceBookA.getAskQty();
			// 检查这层挂单的总金额是否大于5U、抹茶的最低开单限制
			if (askPrice.multiply(askQty).compareTo(lowQuoteQty) < 0) {
				// 最优流动性过低时、手工填满5U流动性、因IOC机制、只会成交实际拥有的流动性。
				askQty = lowQuoteQty.divide(askPrice, 8, RoundingMode.UP);
			}
			// 最优卖价的最大可买数量
			BigDecimal maxExeQty = buyOrigQuoteQtyA.divide(askPrice, 8, RoundingMode.DOWN);
			BigDecimal orderQty = maxExeQty.compareTo(askQty) > 0 ? askQty : maxExeQty;

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
		log.warn("	买A总结: 以{}的均价买入{}个、总金额为:{},留存USD为:{}", (sellCumQuoteQtyB.subtract(buyOrigQuoteQtyA)).divide(buyCumQtyA, 8, RoundingMode.DOWN), buyCumQtyA,
				 sellCumQuoteQtyB.subtract(buyOrigQuoteQtyA), buyOrigQuoteQtyA);
		// 统计
		tradeStat.buyA(buyCumQtyA, sellCumQuoteQtyB.subtract(buyOrigQuoteQtyA), buyOrigQuoteQtyA);
	}


	public static void main(String[] args) throws Exception {
		init();
		loop();
	}
}
