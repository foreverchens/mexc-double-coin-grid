package com.grid.mxc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * @author yyy
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceBook {

	private String symbol;
	private BigDecimal bidPrice;
	private BigDecimal bidQty;
	private BigDecimal askPrice;
	private BigDecimal askQty;
}
