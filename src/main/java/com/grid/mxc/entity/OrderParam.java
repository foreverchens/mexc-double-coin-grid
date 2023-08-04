package com.grid.mxc.entity;/**
 *
 */

import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yyy
 */
@Data
@Slf4j
@Builder
@ToString
public class OrderParam {

	private String symbol;
	private String side;
	private String type;
	private String quantity;
	private String quoteOrderQty;
	private String price;

}
