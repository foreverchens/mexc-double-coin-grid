package com.grid.mxc.entity;/**
 *
 */

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * @author yyy
 */
@Slf4j
@Data
public class PriceBook {

	private String symbol;
	private BigDecimal bidPrice;
	private BigDecimal bidQty;
	private BigDecimal askPrice;
	private BigDecimal askQty;
}
