<div style="text-align: center;">
  <h2>
    mexc-double-coin-grid
  </h2>
</div>

一个对接MXC交易所的简易网格程序(基于MXC V3 API、现在现货0手续费)

如果你已经物色好了你认为的潜力币、并计划持有到牛市、但又不想死拿、希望在熊市的漫长波动中让持有的币慢慢变多的话、那么你可能会对此项目感兴趣

众所周知、网格策略是最擅长在波动行情中套利的

与传统的xxxUSDT网格相比、双币网格可能更适合在熊市中套利、原因如下：

1. 传统网格的资金利用率较低、而双币网格满手都是币
2. 传统网格的参数设置求稳的话、利润低、激进的话、容易错过单边行情。而双币网格的买入和卖出仅与两个币种之间的汇率相关、在熊市中、汇率受两个币种影响、波动更加无序、因此可能可以获取到更多的利润。并且在进入牛市时、不管是同时飞、还是先后飞，都能获得大部分的上涨利润，不容易错过单边行情。

使用的API端点如下

|            URI            | 类型 |       描述        |
| :-----------------------: | :--: | :---------------: |
|       /api/v3/time        | GET  |  获取服务器时间   |
|   /api/v3/ticker/price    | GET  |   获取最新价格    |
| /api/v3/ticker/bookTicker | GET  |   获取最优挂单    |
|       /api/v3/order       | GET  |     获取订单      |
|       /api/v3/order       | POST |     创建订单      |
|  /api/v3/defaultSymbols   | GET  | 获取API支持交易对 |

涉及到的外部配置如下

|    key     |                          描述                           | 必填 |
| :--------: | :-----------------------------------------------------: | :--: |
|     ak     |                       mexc api ak                       |  Y   |
|     sk     |                       mexc api sk                       |  Y   |
|  symbolA   |                         XXXUSDT                         |  Y   |
|  symbolB   |                         XXXUSDT                         |  Y   |
| swapQtyOfA |             每次买入/卖出时、币A的交换数量              |  Y   |
|  gridRate  |                 等比网格、值范围(0,100)                 |  Y   |
| slippage | 交易滑点 |  Y   |
| enableTgBot | 是否启用tgBot、默认false | N |
| chatId | tgBot交互的频道Id | N |
| token | tgBot token | N |
现存问题：

1. 在低流动性场景中、 可能会出现卖A获得的USD无法全部进入买B的订单中、导致USD结余、该情况可通过提高slippage交易滑点的值来解决。

