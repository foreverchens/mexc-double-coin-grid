package com.grid.mxc.api;

import com.grid.mxc.common.MxcClient;

import org.apache.commons.lang.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author yyy
 * @tg t.me/ychen5325
 */
@Slf4j
public class ApiService {


	private String symbolA;
	private String symbolB;
	private String dbPath;

	private TradeStat tradeStat;

	public void start() {
		CompletableFuture.runAsync(() -> {
			while (true) {
				InputStream resourceAsStream =
						MxcClient.class.getResourceAsStream("/application-grid.yaml");
				Properties properties = new Properties();
				try {
					properties.load(resourceAsStream);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				symbolA = properties.getProperty("symbolA");
				symbolB = properties.getProperty("symbolB");
				dbPath = properties.getProperty("dbPath");
				tradeStat = TradeStat.getInstance();

				report();
				log.info("~~~~~");
				try {
					TimeUnit.MINUTES.sleep(5);
				} catch (InterruptedException e) {
					log.error(e.getMessage(), e);
				}
			}
		});
	}

	public void report() {
		try (FileOutputStream fos = new FileOutputStream(dbPath)) {
			fos.write("symbol,side,totalQty,tradeVolume,avgPrice\n".getBytes());
			fos.write(StringUtils.join(Arrays.asList(symbolA, "BUY", tradeStat.getBuyTotalQtyA(),
					tradeStat.getBuyTradeVolumeA(), tradeStat.getBuyAvgPriceA()), ",").concat("\n").getBytes());
			fos.write(StringUtils.join(Arrays.asList(symbolA, "SELL", tradeStat.getSellTotalQtyA()
					, tradeStat.getSellTradeVolumeA(), tradeStat.getSellAvgPriceA()), ",").concat(
							"\n").getBytes());
			fos.write(StringUtils.join(Arrays.asList(symbolB, "BUY", tradeStat.getBuyTotalQtyB(),
					tradeStat.getBuyTradeVolumeB(), tradeStat.getBuyAvgPriceB()), ",").concat("\n").getBytes());
			fos.write(StringUtils.join(Arrays.asList(symbolB, "SELL", tradeStat.getSellTotalQtyB()
					, tradeStat.getSellTradeVolumeB(), tradeStat.getSellAvgPriceB()), ",").concat(
							"\n").getBytes());
			fos.write(tradeStat.getUsdBalance().toPlainString().getBytes());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
