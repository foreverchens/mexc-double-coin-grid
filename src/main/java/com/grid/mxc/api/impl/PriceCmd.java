package com.grid.mxc.api.impl;

import com.grid.mxc.api.BotCmdHandleFunc;
import com.grid.mxc.common.MxcClient;

import org.apache.commons.lang.StringUtils;

import java.util.Locale;

/**
 * @author yyy
 * @tg t.me/ychen5325
 */
public class PriceCmd implements BotCmdHandleFunc {


	@Override
	public String handle(String cmd) {
		try {
			String token = StringUtils.split(cmd, ":")[1].toUpperCase(Locale.ROOT);
			return token + " price --> " + MxcClient.getPrice(token.concat("USDT")).toPlainString();
		} catch (Exception e) {
			return "err:"+e.getMessage();
		}
	}
}
