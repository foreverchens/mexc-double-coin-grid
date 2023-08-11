package com.grid.mxc.api;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 统计A/B两币的买入卖出情况
 * @author yyy
 * @tg t.me/ychen5325
 */
@Getter
public class TradeStat {

	/**
	 * 买A的总数量&买入平均价格&买入交易量
	 */
	private BigDecimal buyTotalQtyA = BigDecimal.ZERO;
	private BigDecimal buyAvgPriceA = BigDecimal.ZERO;
	private BigDecimal buyTradeVolumeA = BigDecimal.ZERO;

	/**
	 * 卖A的总数量&卖出平均价格&卖出交易量
	 */
	private BigDecimal sellTotalQtyA = BigDecimal.ZERO;
	private BigDecimal sellAvgPriceA = BigDecimal.ZERO;
	private BigDecimal sellTradeVolumeA = BigDecimal.ZERO;

	/**
	 * 买A的总数量&买入平均价格&买入交易量
	 */
	private BigDecimal buyTotalQtyB = BigDecimal.ZERO;
	private BigDecimal buyAvgPriceB = BigDecimal.ZERO;
	private BigDecimal buyTradeVolumeB = BigDecimal.ZERO;

	/**
	 * 卖B的总数量&卖出平均价格&卖出交易量
	 */
	private BigDecimal sellTotalQtyB = BigDecimal.ZERO;
	private BigDecimal sellAvgPriceB = BigDecimal.ZERO;
	private BigDecimal sellTradeVolumeB = BigDecimal.ZERO;

	/**
	 * 非盈利usd、
	 * 因A/B流动性深度差异造成的USD未进入订单导致的留存余额
	 */
	private BigDecimal usdBalance = BigDecimal.ZERO;

	private static final TradeStat instance = new TradeStat();


	public void buyA(BigDecimal qty, BigDecimal volume, BigDecimal usd) {
		buyTotalQtyA = buyTotalQtyA.add(qty);
		buyTradeVolumeA = buyTradeVolumeA.add(volume);
		buyAvgPriceA = buyTradeVolumeA.divide(buyTotalQtyA, 8, RoundingMode.DOWN);
		usdBalance = usdBalance.add(usd);
	}

	public void buyB(BigDecimal qty, BigDecimal volume, BigDecimal usd) {
		buyTotalQtyB = buyTotalQtyB.add(qty);
		buyTradeVolumeB = buyTradeVolumeB.add(volume);
		buyAvgPriceB = buyTradeVolumeB.divide(buyTotalQtyB, 8, 1);
		usdBalance = usdBalance.add(usd);
	}


	public void sellA(BigDecimal qty, BigDecimal volume) {
		sellTotalQtyA = sellTotalQtyA.add(qty);
		sellTradeVolumeA = sellTradeVolumeA.add(volume);
		sellAvgPriceA = sellTradeVolumeA.divide(sellTotalQtyA, 8, 1);
	}

	public void sellB(BigDecimal qty, BigDecimal volume) {
		sellTotalQtyB = sellTotalQtyB.add(qty);
		sellTradeVolumeB = sellTradeVolumeB.add(volume);
		sellAvgPriceB = sellTradeVolumeB.divide(sellTotalQtyB, 8, 1);
	}

	public static TradeStat getInstance() {
		return instance;
	}
}
