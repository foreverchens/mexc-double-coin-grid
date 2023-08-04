package com.grid.mxc.entity;/**
 *
 */

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yyy
 */
@Data
@Slf4j
@ToString
public class Order {

	private String symbol;
	private String orderId;
	/**
	 * 市价模式下返回的价格不准
	 */
	private String price;
	private String origOty;
	/**
	 * 订单累计成交数量
	 */
	private String executedQty;
	/**
	 * 订单累计成交金额
	 */
	private String cummulativeQuoteQty;
	private String status;
	private String timeInForce;
	private String type;
	private String side;
	private String time;
	private String updateTime;

	/**
	 * 订单预计成交总额
	 */
	private String origQuoteOrderQty;


}
