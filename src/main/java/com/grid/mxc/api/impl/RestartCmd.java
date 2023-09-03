package com.grid.mxc.api.impl;

import com.grid.mxc.Main;
import com.grid.mxc.api.BotCmdHandleFunc;

/**
 * @author yyy
 * @tg t.me/ychen5325
 */
public class RestartCmd implements BotCmdHandleFunc {
	@Override
	public String handle(String cmd) {
		return Main.stop.compareAndSet(true, false) ? "restarted." : "跑的好好的";
	}
}
