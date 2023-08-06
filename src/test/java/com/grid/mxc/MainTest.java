package com.grid.mxc;

import com.grid.mxc.common.MxcClient;
import com.grid.mxc.entity.Order;
import com.grid.mxc.entity.PriceBook;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * @author yyy
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(MxcClient.class)
@PowerMockIgnore("javax.net.ssl.*")
public class MainTest {

	MxcClient mxcClient;

	Main main;

	@Before
	public void before() {
		mxcClient = EasyMock.createMock(MxcClient.class);
		main = new Main();
	}

	@Test
	public void priceTest() throws Exception {

		PowerMock.mockStatic(MxcClient.class);

		BigDecimal prePrice = BigDecimal.valueOf(111);
		EasyMock.expect(MxcClient.getPrice(EasyMock.anyString())).andReturn(prePrice);

		PowerMock.replayAll();

		BigDecimal price = MxcClient.getPrice("price");

		PowerMock.verifyAll();
		Assert.assertEquals(price, prePrice);
	}


	/**
	 * 汇率上涨、执行一次卖A买B
	 * symbolA: KASUSDT
	 * symbolB: DNXUSDT
	 * swapQtyOfA: 5
	 * gridRate: 1
	 * tradeDepth: 1
	 */
	@Test
	public void ratioUpTest() throws Exception {
		PowerMock.mockStatic(MxcClient.class);

		EasyMock.expect(MxcClient.getSupSymbols()).andReturn(Arrays.asList("DNXUSDT", "KASUSDT"));
		// init()
		BigDecimal kasP1 = BigDecimal.TEN;
		BigDecimal dnxP1 = BigDecimal.valueOf(5);
		EasyMock.expect(MxcClient.getPrice(EasyMock.anyString())).andReturn(kasP1);
		EasyMock.expect(MxcClient.getPrice(EasyMock.anyString())).andReturn(dnxP1);

		// loop()
		BigDecimal kasP2 = new BigDecimal("10.2");
		BigDecimal kasQ1 = new BigDecimal(4);

		PriceBook kasPrBook1 = PriceBook.builder().bidPrice(kasP2).bidQty(kasQ1).build();

		PriceBook dnxPrBook1 = PriceBook.builder().askPrice(new BigDecimal("5")).askQty(new BigDecimal("8")).build();
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(kasPrBook1);
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(dnxPrBook1);
		String orderId01 = "orderId01";
		EasyMock.expect(MxcClient.createOrder(EasyMock.anyObject())).andReturn(orderId01);
		Order order1 =
				Order.builder().cummulativeQuoteQty(kasP2.multiply(kasQ1).toPlainString()).executedQty(kasQ1.toPlainString()).price(kasP2.toPlainString()).build();
		EasyMock.expect(MxcClient.getOrder(EasyMock.anyString(), EasyMock.anyString())).andReturn(order1);
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(kasPrBook1);
		String orderId02 = "orderId02";
		EasyMock.expect(MxcClient.createOrder(EasyMock.anyObject())).andReturn(orderId02);
		Order order2 = Order.builder().cummulativeQuoteQty("40").executedQty("8").price("5").build();
		EasyMock.expect(MxcClient.getOrder(EasyMock.anyString(), EasyMock.anyString())).andReturn(order2);
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(dnxPrBook1);

		// 预设空指针退出循环
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(null).times(2);
		PowerMock.replayAll();
		Main.init();
		try {
			Main.loop();
		} catch (Exception ex) {}
	}


	/**
	 * 汇率下跌、执行一次卖B买A
	 * symbolA: KASUSDT
	 * symbolB: DNXUSDT
	 * swapQtyOfA: 5
	 * gridRate: 1
	 * tradeDepth: 1
	 */
	@Test
	public void ratioDownTest() throws Exception {
		PowerMock.mockStatic(MxcClient.class);

		EasyMock.expect(MxcClient.getSupSymbols()).andReturn(Arrays.asList("DNXUSDT", "KASUSDT"));
		// init()
		BigDecimal kasP1 = BigDecimal.TEN;
		BigDecimal dnxP1 = BigDecimal.valueOf(5);
		EasyMock.expect(MxcClient.getPrice(EasyMock.anyString())).andReturn(kasP1);
		EasyMock.expect(MxcClient.getPrice(EasyMock.anyString())).andReturn(dnxP1);

		// loop()
		BigDecimal kasP2 = new BigDecimal("9.8");
		BigDecimal kasQ1 = new BigDecimal(4);

		PriceBook kasPrBook1 = PriceBook.builder().bidPrice(kasP2).askPrice(kasP2).askQty(kasQ1).build();

		PriceBook dnxPrBook1 =
				PriceBook.builder().bidPrice(new BigDecimal("5")).bidQty(new BigDecimal("8")).askPrice(BigDecimal.valueOf(5)).build();
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(kasPrBook1);
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(dnxPrBook1);
		String orderId01 = "orderId01";
		EasyMock.expect(MxcClient.createOrder(EasyMock.anyObject())).andReturn(orderId01);
		Order dnxOrder = Order.builder().cummulativeQuoteQty("40").executedQty("8").price("5").build();
		EasyMock.expect(MxcClient.getOrder(EasyMock.anyString(), EasyMock.anyString())).andReturn(dnxOrder);
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(dnxPrBook1);
		String orderId02 = "orderId02";
		EasyMock.expect(MxcClient.createOrder(EasyMock.anyObject())).andReturn(orderId02);
		Order kasOrder =
				Order.builder().cummulativeQuoteQty(kasP2.multiply(kasQ1).toPlainString()).executedQty(kasQ1.toPlainString()).price(kasP2.toPlainString()).build();
		EasyMock.expect(MxcClient.getOrder(EasyMock.anyString(), EasyMock.anyString())).andReturn(kasOrder);
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(kasPrBook1);

		// 预设空指针退出循环
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(null).times(2);
		PowerMock.replayAll();
		Main.init();
		try {
			Main.loop();
		} catch (Exception ex) {}
	}


	/**
	 * 汇率上涨、执行一次卖A买B、吃两层最优挂单。
	 * 先卖A、买单簿如下
	 * p：10.2  qty：3
	 * p: 10.19 qty:2
	 * 在买B、买单簿如下
	 * p: 5    qty：6
	 * p：5.1  qty:3
	 * <p>
	 * symbolA: KASUSDT
	 * symbolB: DNXUSDT
	 * swapQtyOfA: 5
	 * gridRate: 1
	 * tradeDepth: 2
	 */
	@Test
	public void tradeDepthTest() throws Exception {
		PowerMock.mockStatic(MxcClient.class);

		EasyMock.expect(MxcClient.getSupSymbols()).andReturn(Arrays.asList("DNXUSDT", "KASUSDT"));
		// init()
		BigDecimal kasP1 = BigDecimal.TEN;
		BigDecimal dnxP1 = BigDecimal.valueOf(5);
		EasyMock.expect(MxcClient.getPrice(EasyMock.anyString())).andReturn(kasP1);
		EasyMock.expect(MxcClient.getPrice(EasyMock.anyString())).andReturn(dnxP1);

		// loop()
		BigDecimal kasP2 = new BigDecimal("10.2");
		BigDecimal kasQ1 = new BigDecimal(3);
		BigDecimal kasP3 = new BigDecimal("10.19");
		BigDecimal kasQ2 = new BigDecimal(2);

		PriceBook kasPrBook1 = PriceBook.builder().bidPrice(kasP2).bidQty(kasQ1).build();
		PriceBook dnxPrBook1 = PriceBook.builder().askPrice(new BigDecimal("5")).askQty(new BigDecimal("6")).build();

		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(kasPrBook1);
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(dnxPrBook1);

		String orderId01 = "orderId01";
		EasyMock.expect(MxcClient.createOrder(EasyMock.anyObject())).andReturn(orderId01);
		Order kasOrder1 =
				Order.builder().cummulativeQuoteQty(kasPrBook1.getBidPrice().multiply(kasPrBook1.getBidQty()).toPlainString()).executedQty(kasPrBook1.getBidQty().toPlainString()).price(kasPrBook1.getBidPrice().toPlainString()).build();
		EasyMock.expect(MxcClient.getOrder(EasyMock.anyString(), EasyMock.anyString())).andReturn(kasOrder1);

		PriceBook kasPrBook2 = PriceBook.builder().bidPrice(kasP3).bidQty(kasQ2).build();
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(kasPrBook2);
		String orderId02 = "orderId02";
		EasyMock.expect(MxcClient.createOrder(EasyMock.anyObject())).andReturn(orderId02);
		Order kasOrder2 =
				Order.builder().cummulativeQuoteQty(kasPrBook2.getBidPrice().multiply(kasPrBook2.getBidQty()).toPlainString()).executedQty(kasPrBook2.getBidQty().toPlainString()).price(kasPrBook2.getBidPrice().toPlainString()).build();
		EasyMock.expect(MxcClient.getOrder(EasyMock.anyString(), EasyMock.anyString())).andReturn(kasOrder2);

		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(kasPrBook2);


		String orderId03 = "orderId03";
		EasyMock.expect(MxcClient.createOrder(EasyMock.anyObject())).andReturn(orderId03).times(2);
		Order dnxOrder1 = Order.builder().cummulativeQuoteQty("30").executedQty("6").price("5").build();
		EasyMock.expect(MxcClient.getOrder(EasyMock.anyString(), EasyMock.anyString())).andReturn(dnxOrder1);

		PriceBook dnxPrBook2 = PriceBook.builder().askPrice(new BigDecimal("5.1")).askQty(new BigDecimal("3")).build();

		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(dnxPrBook2);

		Order dnxOrder2 = Order.builder().cummulativeQuoteQty("15.3").executedQty("3").price("5.1").build();
		EasyMock.expect(MxcClient.getOrder(EasyMock.anyString(), EasyMock.anyString())).andReturn(dnxOrder2);


		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(dnxPrBook1);


		// 预设空指针退出循环
		EasyMock.expect(MxcClient.getPriceBook(EasyMock.anyString())).andReturn(null).times(2);
		PowerMock.replayAll();
		Main.init();
		try {
			Main.loop();
		} catch (Exception ex) {}
	}
}
