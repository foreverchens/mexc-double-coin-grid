package com.grid.mxc.api.impl;


import com.grid.mxc.api.BotCmdHandleFunc;
import com.grid.mxc.api.TradeStat;
import com.grid.mxc.common.MxcClient;

import cn.hutool.core.date.DateTime;

import org.apache.commons.lang.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

/**
 * @author yyy
 * @tg t.me/ychen5325
 */
@Slf4j
public class GridCmd implements BotCmdHandleFunc {

	private String symbolA;
	private String symbolB;
	private String dbPath;
	private TradeStat tradeStat;


	public GridCmd() {
		InputStream resourceAsStream = MxcClient.class.getResourceAsStream("/application-grid.yaml");
		Properties properties = new Properties();
		try {
			properties.load(resourceAsStream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		symbolA = properties.getProperty("symbolA");
		symbolB = properties.getProperty("symbolB");
		dbPath = System.getProperty("user.dir") + File.separator + properties.getProperty("dbPath");
		tradeStat = TradeStat.getInstance();
		try {
			File file = new File(dbPath);
			if (!file.exists() && !file.createNewFile()) {
				throw new RuntimeException("tableFile create fail");
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String handle(String cmd) {
		StringBuffer sb = new StringBuffer();
		sb.append("` | symbol|side|totalQty|tradeVolume|avgPrice|`\n");
		sb.append(StringUtils.join(Arrays.asList("`", symbolA, "BUY", tradeStat.getBuyTotalQtyA(), tradeStat.getBuyTradeVolumeA(), tradeStat.getBuyAvgPriceA()), " | ").concat(" |`\n"));
		sb.append(StringUtils.join(Arrays.asList("`", symbolA, "SELL", tradeStat.getSellTotalQtyA(), tradeStat.getSellTradeVolumeA(), tradeStat.getSellAvgPriceA()), " | ").concat(" |`\n"));
		sb.append(StringUtils.join(Arrays.asList("`", symbolB, "BUY", tradeStat.getBuyTotalQtyB(), tradeStat.getBuyTradeVolumeB(), tradeStat.getBuyAvgPriceB()), " | ").concat(" |`\n"));
		sb.append(StringUtils.join(Arrays.asList("`", symbolB, "SELL", tradeStat.getSellTotalQtyB(), tradeStat.getSellTradeVolumeB(), tradeStat.getSellAvgPriceB()), " | ").concat(" |`\n"));
		sb.append(tradeStat.getUsdBalance().toPlainString().concat("  |  ").concat(DateTime.now().toString()));
		return sb.toString();
	}
}
